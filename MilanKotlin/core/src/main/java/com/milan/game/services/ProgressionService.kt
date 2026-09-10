package com.milan.game.services

import com.milan.game.data.CharacterSaveState
import com.milan.game.domain.progression.EconomyFormulas
import kotlinx.coroutines.sync.withLock

/**
 * 养成聚合服务（2026-08-28 P1 重构：自 [GameService] 拆出）。
 *
 * 负责：升级/加经验/突破/升星、天赋树加点与可点预判。
 * 边界：**不碰**抽卡/爬塔/商店/成就——那些分别属于其他四个聚合服务。
 *
 * 未拥有的角色（不在 OwnedCharacters）一律拒绝养成。
 * 所有写操作遵循事务范式：预算校验 → 改内存 → 落盘 → 失败回滚 → 仅成功广播。
 */
class ProgressionService(private val core: ServiceCore) : ProgressionApi {

    private val saveData get() = core.saveData

    /** 当前经验条进度（本等级内已积累 / 本级所需）。达到等级上限时返回 (need, need)。 */
    override fun expProgress(charId: String): Pair<Int, Int> {
        val save = core.getSave(charId) ?: return 0 to 1
        val need = EconomyFormulas.expForLevel(save.level)
        val cur = (save.totalExp - EconomyFormulas.cumulativeExp(save.level)).coerceIn(0, need)
        if (save.level >= core.maxLevelForStage(save.stage)) return need to need
        return cur to need
    }

    /**
     * 升级 n 级（默认 1）。星尘不足或已达等级上限时尽可能少升；一级都升不了返回 Rejected。
     * 每升 1 级 +1 天赋点。落盘失败回滚（SaveFailed）。
     * 整体持锁：planLevelUp 依赖的 level/余额必须在临界区内读取（防过期校验竞态）。
     */
    override suspend fun levelUp(charId: String, n: Int): WriteOutcome = core.withWriteLock {
        val save = core.getSave(charId) ?: return@withWriteLock WriteOutcome.Rejected
        if (n <= 0) return@withWriteLock WriteOutcome.Rejected

        // 预算规划抽到纯领域（EconomyFormulas.planLevelUp），边界行为由单元测试锁死。
        val (gained, cost) = EconomyFormulas.planLevelUp(
            save.level, core.maxLevelForStage(save.stage), saveData.softCurrency, n,
        )
        if (gained <= 0) return@withWriteLock WriteOutcome.Rejected // 一级都升不了（资源不足 / 已满级）

        val target = save.level + gained
        // 货币升级等价于「买下已完成等级的累计经验」：累加而非覆写，
        // 保持 totalExp 与等级一致，且不抹掉 grantExp 已积累的经验（#1 经验条恒 0 的根因）。
        val expGain = EconomyFormulas.cumulativeExp(target) - EconomyFormulas.cumulativeExp(save.level)
        val origSoft = saveData.softCurrency
        // 记录升级前的 totalExp 原值：回滚时直接恢复（而非按等级重算累计经验），
        // 否则 grantExp 攒下的经验零头会在「落盘失败回滚」时从内存消失，
        // 与磁盘旧档分叉，下次成功保存即永久丢失（P1-1）。
        val origTotal = save.totalExp
        core.transactionLocked(
            tag = "levelUp",
            mutate = {
                saveData.softCurrency -= cost
                save.totalExp += expGain
                save.level = target
                save.unspentPoints += gained
            },
            rollback = {
                saveData.softCurrency = origSoft
                save.level -= gained
                save.unspentPoints -= gained
                save.totalExp = origTotal
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    /**
     * 增加经验（战斗/活动奖励的统一出口）。经验累计进 [CharacterSaveState.totalExp]，
     * 等级由 ProgressionEngine.expToLevel 派生并钳到本突破阶段上限——totalExp 是经验唯一真值。
     *
     * ⚠️ **调用方注意**：本方法自身持 [ServiceCore.writeMutex]。已在临界区内的调用方
     * （如 TowerService.runTowerFloor）**不可调用**（Mutex 不可重入 → 死锁），
     * 必须内联同口径实现。Kotlin 版 expToLevel 此前长期未被调用，经验条因此恒为 0（#1）。
     *
     * @return 实际升的级数（0 = 仅积累经验、未升级；负数永不返回）
     */
    override suspend fun grantExp(charId: String, amount: Int): Int = core.withWriteLock {
        if (amount <= 0) return@withWriteLock 0
        val save = core.getSave(charId) ?: return@withWriteLock 0
        val cap = core.maxLevelForStage(save.stage)

        val origTotal = save.totalExp
        val origLevel = save.level
        val origPoints = save.unspentPoints
        // 目标等级由「累计经验 + 本次奖励」纯推导（不在 mutate 内算，便于回滚只恢复原值）
        val derived = core.progression.expToLevel(save.totalExp + amount)
        val newLevel = if (derived > cap) cap else derived
        val gained = newLevel - origLevel

        val outcome = core.transactionLocked(
            tag = "grantExp",
            mutate = {
                save.totalExp += amount
                if (gained > 0) save.unspentPoints += gained
                save.level = newLevel
            },
            rollback = {
                // 回滚（不广播）：内存与存档保持一致
                save.totalExp = origTotal
                save.level = origLevel
                if (gained > 0) save.unspentPoints = origPoints
            },
            onCommit = { core.publishProgressionChanged() },
        )
        if (outcome == WriteOutcome.Success) gained else 0
    }

    /** 突破（Stage+1）。需未达 MaxStage 且星魂碎片 + 星尘充足。落盘失败回滚（SaveFailed）。
     *  P2-12：突破后按已积累经验重推导等级（上限随阶段提高）——此前玩家带「银行经验」
     *  突破后等级停留在旧上限，再用星尘 levelUp 会为已经用经验换到的等级再付一次钱。
     *  整体持锁：碎片/星尘余额校验必须在临界区内读取（防过期校验竞态）。 */
    override suspend fun ascend(charId: String): WriteOutcome = core.withWriteLock {
        val save = core.getSave(charId) ?: return@withWriteLock WriteOutcome.Rejected
        val def = core.character(charId) ?: return@withWriteLock WriteOutcome.Rejected
        if (save.stage >= def.maxStage) return@withWriteLock WriteOutcome.Rejected

        val frags = core.ascendFragments(save.stage)
        val soft = core.ascendSoft(save.stage)
        val have = core.getStarFragments()
        if (have < frags || saveData.softCurrency < soft) return@withWriteLock WriteOutcome.Rejected

        val origSoft = saveData.softCurrency
        val origFrags = have
        val origLevel = save.level
        val origPoints = save.unspentPoints
        val itemExisted = saveData.items.any { it?.itemId == ServiceCore.StarFragmentItemId }
        core.transactionLocked(
            tag = "ascend",
            mutate = {
                saveData.softCurrency -= soft
                core.addItemDelta(ServiceCore.StarFragmentItemId, -frags)
                save.stage += 1
                // 突破后重推导：totalExp 是经验唯一真值，等级 = min(expToLevel(totalExp), 新上限)，
                // 差额级数补发天赋点（与 grantExp 的升级语义一致）
                val derived = core.progression.expToLevel(save.totalExp)
                val newCap = core.maxLevelForStage(save.stage)
                val target = minOf(derived, newCap)
                if (target > save.level) {
                    save.unspentPoints += target - save.level
                    save.level = target
                }
            },
            rollback = {
                saveData.softCurrency = origSoft
                core.restoreItemCount(ServiceCore.StarFragmentItemId, itemExisted, origFrags)
                save.level = origLevel
                save.unspentPoints = origPoints
                save.stage -= 1
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    /** 升星（Stars+1）。需未达 MaxStars 且星魂碎片充足。落盘失败回滚（SaveFailed）。每次仅 +1 星。
     *  整体持锁：碎片余额校验必须在临界区内读取（防过期校验竞态）。 */
    override suspend fun starUp(charId: String): WriteOutcome = core.withWriteLock {
        val save = core.getSave(charId) ?: return@withWriteLock WriteOutcome.Rejected
        val def = core.character(charId) ?: return@withWriteLock WriteOutcome.Rejected
        if (save.stars >= def.maxStars) return@withWriteLock WriteOutcome.Rejected

        val cost = core.starUpFragments(save.stars)
        val have = core.getStarFragments()
        if (have < cost) return@withWriteLock WriteOutcome.Rejected

        val origFrags = have
        val itemExisted = saveData.items.any { it?.itemId == ServiceCore.StarFragmentItemId }
        core.transactionLocked(
            tag = "starUp",
            mutate = {
                core.addItemDelta(ServiceCore.StarFragmentItemId, -cost)
                save.stars += 1
            },
            rollback = {
                core.restoreItemCount(ServiceCore.StarFragmentItemId, itemExisted, origFrags)
                save.stars -= 1
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    // ─────────────────────────── 天赋 ───────────────────────────

    /** 取角色天赋树（含节点与前置关系）。无树返回 null。 */
    override fun getTalentTree(charId: String): TalentTreeData? {
        val def = core.character(charId) ?: return null
        return core.talentTree(def.talentTreeId)
    }

    /** 天赋加点前置校验：角色存在 + 树存在 + 节点存在 + 未点亮 + 点数足够；任一失败返回 null。 */
    private fun talentCheck(charId: String, nodeId: String): TalentCheck? {
        val save = core.getSave(charId) ?: return null
        val tree = getTalentTree(charId) ?: return null
        val node = tree.nodes.firstOrNull { it.nodeId == nodeId } ?: return null
        if (save.talentPoints.contains(nodeId)) return null
        if (save.unspentPoints < node.cost) return null
        return TalentCheck(save, node, tree)
    }

    /** 不落盘地预判某天赋节点当前是否可点亮（用于 UI 三态与按钮可用性）。 */
    override fun canAllocateTalent(charId: String, nodeId: String): Boolean {
        // C# 在渲染路径上曾有 "TalentPoints": null 覆盖字段初始化器的 NRE（逐节点调用直接闪退），
        // 用 ??= 兜底；Kotlin 类型系统 + coerceInputValues 保证 talentPoints 非空，天然免疫。
        val check = talentCheck(charId, nodeId) ?: return false
        return core.talent.canAllocate(nodeId, check.save.talentPoints.filterNotNull(), core.prereqMap(check.tree))
    }

    /** 点亮天赋节点：校验前置（TalentEngine）与天赋点余额，扣点并落盘。
     * 已点过 / 点不够 / 前置未满足 / 落盘失败分别返回 Rejected / SaveFailed。
     * 整体持锁：talentCheck 读取的存档状态必须在临界区内（防过期校验竞态）。 */
    override suspend fun allocateTalent(charId: String, nodeId: String): WriteOutcome = core.withWriteLock {
        val check = talentCheck(charId, nodeId) ?: return@withWriteLock WriteOutcome.Rejected
        val save = check.save
        val node = check.node
        if (!core.talent.canAllocate(nodeId, save.talentPoints.filterNotNull(), core.prereqMap(check.tree))) {
            return@withWriteLock WriteOutcome.Rejected
        }

        val origPoints = save.unspentPoints
        core.transactionLocked(
            tag = "talent",
            mutate = {
                save.unspentPoints -= node.cost
                save.talentPoints = save.talentPoints + nodeId
            },
            rollback = {
                save.unspentPoints = origPoints
                save.talentPoints = save.talentPoints.filterNot { it == nodeId }
            },
            onCommit = { core.publishProgressionChanged() },
        )
    }

    // ─────────────────────────── 角色好感度（2026-09-06 S4 自门面下沉）───────────────────────────
    // 原 GameService.grantAffinity / giftAffinity / getCharacterAffinityData 内联实现，
    // 违反"门面不含领域规则"自述。下沉到本服务（养成语义相关），门面改纯转发。
    // 业务口径不变：满级 Rejected、满级 + 钳位兜底、星尘不足 Rejected、事务原子扣减+加好感。

    /** 获取角色好感度数据（null 视为 0；调用方 UI 直接读，无需写事务）。 */
    override fun getCharacterAffinityData(): Map<String, Int> {
        val data = saveData.characterAffinityData ?: return emptyMap()
        return data.mapValues { it.value ?: 0 }
    }

    /**
     * 增加角色好感度（剧情选择 / 其它无消耗产出用）。
     *
     * 满级时明确返回 Rejected（不产生无意义落盘）；内部经 [core.addAffinityDelta]
     * 钳位兜底（数值安全单一事实来源，勿在此就地写数字）。
     */
    override suspend fun grantAffinity(characterId: String, amount: Int): WriteOutcome =
        core.withWriteLock {
            if (amount <= 0) return@withWriteLock WriteOutcome.Rejected
            val origData = core.saveData.characterAffinityData
            if ((origData?.get(characterId) ?: 0) >= AffinityFormulas.MAX_AFFINITY) {
                return@withWriteLock WriteOutcome.Rejected // 已满级
            }
            core.transactionLocked(
                tag = "affinity.add",
                mutate = {
                    core.addAffinityDelta(characterId, amount)
                },
                rollback = {
                    core.saveData.characterAffinityData = origData
                },
                onCommit = {
                    core.publishProgressionChanged()
                },
            )
        }

    /**
     * 赠送礼物（好感度主动培养入口，2026-09-02 产品拍板：100 星尘 → +200 好感）。
     *
     * 事务内原子完成「扣星尘 + 加好感」（单一 transactionLocked，非两次独立写）；
     * 预算在锁内做（Mutex 临界区），避免并发下余额被先到事务扣走造成负数。
     * Rejected 语义：星尘不足 或 已满级（调用方据此给 UI 提示，勿当异常）。
     */
    override suspend fun giftAffinity(characterId: String): WriteOutcome =
        core.withWriteLock {
            val origSoft = core.saveData.softCurrency
            val origData = core.saveData.characterAffinityData
            if (origSoft < AffinityFormulas.GIFT_COST_SOFT) {
                return@withWriteLock WriteOutcome.Rejected // 星尘不足
            }
            if ((origData?.get(characterId) ?: 0) >= AffinityFormulas.MAX_AFFINITY) {
                return@withWriteLock WriteOutcome.Rejected // 已满级
            }
            core.transactionLocked(
                tag = "affinity.gift",
                mutate = {
                    core.saveData.softCurrency -= AffinityFormulas.GIFT_COST_SOFT
                    core.addAffinityDelta(characterId, AffinityFormulas.GIFT_AFFINITY_AMOUNT)
                },
                rollback = {
                    core.saveData.softCurrency = origSoft
                    core.saveData.characterAffinityData = origData
                },
                onCommit = {
                    core.publishCurrencyChanged()
                },
            )
        }

    /** 好感等级奖励领取态（characterId → 已领取档位；null 条目滤掉）。 */
    override fun getClaimedAffinityRewards(): Map<String, List<Int>> {
        val raw = saveData.claimedAffinityRewards ?: return emptyMap()
        return raw.mapNotNull { (id, levels) ->
            if (id.isEmpty()) null else id to (levels?.filterNotNull() ?: emptyList())
        }.toMap()
    }

    /**
     * 领取好感等级奖励（2026-09-10：UI 此前只展示「规划中」，无领取路径）。
     *
     * 事务范式：锁内校验「角色已拥有 + 达到档位 + 未领取」→ 发放 → 标记已领取。
     * Rejected：角色未拥有 / 未达档位 / 已领取 / 档位非法。数值单一事实来源 [AffinityFormulas.LEVEL_REWARDS]。
     * 成功后由门面 [GameService.claimAffinityReward] 上报 CLAIM_AFFINITY 每日任务进度。
     */
    override suspend fun claimAffinityReward(characterId: String, level: Int): WriteOutcome =
        core.withWriteLock {
            val reward = AffinityFormulas.LEVEL_REWARDS.firstOrNull { it.level == level }
                ?: return@withWriteLock WriteOutcome.Rejected
            val owned = saveData.ownedCharacters.any { it?.characterId == characterId }
            if (!owned) return@withWriteLock WriteOutcome.Rejected
            val affinity = saveData.characterAffinityData?.get(characterId) ?: 0
            if (AffinityFormulas.levelOf(affinity) < level) {
                return@withWriteLock WriteOutcome.Rejected
            }
            val claimed = saveData.claimedAffinityRewards?.get(characterId)?.filterNotNull().orEmpty()
            if (level in claimed) return@withWriteLock WriteOutcome.Rejected

            val origClaimed = saveData.claimedAffinityRewards
            val origSoft = saveData.softCurrency
            val origHard = saveData.hardCurrency
            val fragExisted = saveData.items.any { it?.itemId == ServiceCore.StarFragmentItemId }
            val origFrags = core.itemCount(ServiceCore.StarFragmentItemId)

            core.transactionLocked(
                tag = "affinity.claimReward",
                mutate = {
                    when (reward.kind) {
                        AffinityFormulas.RewardKind.SOFT ->
                            core.addCurrencyDelta(softDelta = reward.amount)
                        AffinityFormulas.RewardKind.HARD ->
                            core.addCurrencyDelta(hardDelta = reward.amount)
                        AffinityFormulas.RewardKind.FRAGMENT ->
                            core.addItemDelta(ServiceCore.StarFragmentItemId, reward.amount)
                    }
                    saveData.claimedAffinityRewards =
                        (saveData.claimedAffinityRewards ?: emptyMap()) +
                        (characterId to (claimed + level))
                },
                rollback = {
                    saveData.claimedAffinityRewards = origClaimed
                    saveData.softCurrency = origSoft
                    saveData.hardCurrency = origHard
                    core.restoreItemCount(ServiceCore.StarFragmentItemId, fragExisted, origFrags)
                },
                onCommit = {
                    core.publishCurrencyChanged()
                    core.publishProgressionChanged()
                },
            )
        }

    /** 天赋校验结果（P3-3：替代 Triple 元组，字段具名可读）。 */
    private data class TalentCheck(
        val save: CharacterSaveState,
        val node: TalentNodeData,
        val tree: TalentTreeData,
    )
}
