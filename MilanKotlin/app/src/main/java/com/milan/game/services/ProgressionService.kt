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
internal class ProgressionService(private val core: ServiceCore) {

    private val saveData get() = core.saveData

    /** 当前经验条进度（本等级内已积累 / 本级所需）。达到等级上限时返回 (need, need)。 */
    fun expProgress(charId: String): Pair<Int, Int> {
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
    suspend fun levelUp(charId: String, n: Int = 1): WriteOutcome = core.writeMutex.withLock {
        val save = core.getSave(charId) ?: return@withLock WriteOutcome.Rejected
        if (n <= 0) return@withLock WriteOutcome.Rejected

        // 预算规划抽到纯领域（EconomyFormulas.planLevelUp），边界行为由单元测试锁死。
        val (gained, cost) = EconomyFormulas.planLevelUp(
            save.level, core.maxLevelForStage(save.stage), saveData.softCurrency, n,
        )
        if (gained <= 0) return@withLock WriteOutcome.Rejected // 一级都升不了（资源不足 / 已满级）

        val target = save.level + gained
        // 货币升级等价于「买下已完成等级的累计经验」：累加而非覆写，
        // 保持 totalExp 与等级一致，且不抹掉 addExp 已积累的经验（#1 经验条恒 0 的根因）。
        val expGain = EconomyFormulas.cumulativeExp(target) - EconomyFormulas.cumulativeExp(save.level)
        val origSoft = saveData.softCurrency
        // 记录升级前的 totalExp 原值：回滚时直接恢复（而非按等级重算累计经验），
        // 否则 addExp 攒下的经验零头会在「落盘失败回滚」时从内存消失，
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
    suspend fun addExp(charId: String, amount: Int): Int = core.writeMutex.withLock {
        if (amount <= 0) return@withLock 0
        val save = core.getSave(charId) ?: return@withLock 0
        val cap = core.maxLevelForStage(save.stage)

        val origTotal = save.totalExp
        val origLevel = save.level
        val origPoints = save.unspentPoints
        // 目标等级由「累计经验 + 本次奖励」纯推导（不在 mutate 内算，便于回滚只恢复原值）
        val derived = core.progression.expToLevel(save.totalExp + amount)
        val newLevel = if (derived > cap) cap else derived
        val gained = newLevel - origLevel

        val outcome = core.transactionLocked(
            tag = "addExp",
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
    suspend fun ascend(charId: String): WriteOutcome = core.writeMutex.withLock {
        val save = core.getSave(charId) ?: return@withLock WriteOutcome.Rejected
        val def = core.character(charId) ?: return@withLock WriteOutcome.Rejected
        if (save.stage >= def.maxStage) return@withLock WriteOutcome.Rejected

        val frags = core.ascendFragments(save.stage)
        val soft = core.ascendSoft(save.stage)
        val have = core.getStarFragments()
        if (have < frags || saveData.softCurrency < soft) return@withLock WriteOutcome.Rejected

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
                // 差额级数补发天赋点（与 addExp 的升级语义一致）
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
    suspend fun starUp(charId: String): WriteOutcome = core.writeMutex.withLock {
        val save = core.getSave(charId) ?: return@withLock WriteOutcome.Rejected
        val def = core.character(charId) ?: return@withLock WriteOutcome.Rejected
        if (save.stars >= def.maxStars) return@withLock WriteOutcome.Rejected

        val cost = core.starUpFragments(save.stars)
        val have = core.getStarFragments()
        if (have < cost) return@withLock WriteOutcome.Rejected

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
    fun getTalentTree(charId: String): TalentTreeData? {
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
    fun canAllocateTalent(charId: String, nodeId: String): Boolean {
        // C# 在渲染路径上曾有 "TalentPoints": null 覆盖字段初始化器的 NRE（逐节点调用直接闪退），
        // 用 ??= 兜底；Kotlin 类型系统 + coerceInputValues 保证 talentPoints 非空，天然免疫。
        val check = talentCheck(charId, nodeId) ?: return false
        return core.talent.canAllocate(nodeId, check.save.talentPoints.filterNotNull(), core.prereqMap(check.tree))
    }

    /** 点亮天赋节点：校验前置（TalentEngine）与天赋点余额，扣点并落盘。
     * 已点过 / 点不够 / 前置未满足 / 落盘失败分别返回 Rejected / SaveFailed。
     * 整体持锁：talentCheck 读取的存档状态必须在临界区内（防过期校验竞态）。 */
    suspend fun allocateTalent(charId: String, nodeId: String): WriteOutcome = core.writeMutex.withLock {
        val check = talentCheck(charId, nodeId) ?: return@withLock WriteOutcome.Rejected
        val save = check.save
        val node = check.node
        if (!core.talent.canAllocate(nodeId, save.talentPoints.filterNotNull(), core.prereqMap(check.tree))) {
            return@withLock WriteOutcome.Rejected
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

    /** 天赋校验结果（P3-3：替代 Triple 元组，字段具名可读）。 */
    private data class TalentCheck(
        val save: CharacterSaveState,
        val node: TalentNodeData,
        val tree: TalentTreeData,
    )
}
