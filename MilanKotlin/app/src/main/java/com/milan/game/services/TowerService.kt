package com.milan.game.services

import com.milan.game.data.BattleRecord
import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveData
import com.milan.game.domain.battle.BattleSimulator
import com.milan.game.domain.battle.StrategicBattleSimulator
import com.milan.game.domain.battle.TeamResonance
import com.milan.game.domain.battle.UnitStats
import com.milan.game.domain.progression.EconomyFormulas
import kotlin.random.Random
import kotlinx.coroutines.sync.withLock

/**
 * 爬塔 + 编队聚合服务（2026-08-28 P1 重构：自 [GameService] 拆出）。
 *
 * 负责：出战编队管理、无尽之塔挑战结算（战斗模拟、奖励、战报、经验）。
 * 边界：**不碰**抽卡/养成加点/商店/成就——那些分别属于其他四个聚合服务。
 *
 * 编队与爬塔放在同一服务：编队是爬塔的唯一入口（没有编队就无法挑战），
 * 二者共享「队伍构建」语义，拆开反而要跨服务调用（会撞上 Mutex 不可重入）。
 */
internal class TowerService(private val core: ServiceCore) {

    private val saveData get() = core.saveData
    
    // 策略战斗模拟器（支持玩家输入）
    private val strategicSimulator = StrategicBattleSimulator(core.rng)

    // ─────────────────────────── 出战编队 ───────────────────────────

    /** 当前编队 characterId 列表（空槽已过滤；顺序即槽位顺序）。 */
    fun getFormation(): List<String> = saveData.getFormationIds()

    /**
     * 设置出战编队。
     * 校验（任一不过返回 [WriteOutcome.Rejected]，不做任何变更）：去重后数量 ≤
     * [SaveData.MAX_FORMATION_SIZE]、全部角色已拥有；允许空列表 = 清空编队。
     * 事务范式：落盘失败回滚本次改动、不广播事件。
     */
    suspend fun setFormation(characterIds: List<String>): WriteOutcome = core.withWriteLock {
        val ids = characterIds.distinct()
        if (ids.size > SaveData.MAX_FORMATION_SIZE) return@withWriteLock WriteOutcome.Rejected
        val ownedIds = saveData.ownedCharacters.filterNotNull().mapTo(HashSet()) { it.characterId }
        if (ids.any { it !in ownedIds }) return@withWriteLock WriteOutcome.Rejected

        val original = saveData.formation
        core.transactionLocked(
            tag = "formation",
            mutate = { saveData.formation = ids },
            rollback = { saveData.formation = original },
            onCommit = { core.refreshSnapshot() },
        )
    }

    /**
     * 切换角色的入队状态（U2，2026-08-28 审查修复）：**读-改-写整体在 writeMutex 内完成**。
     *
     * 此前由 UI 侧分三步（读 formation → 计算 next → 调 setFormation），跨锁执行存在竞态：
     * 快速连点两个角色时，两次都基于同一份过期快照计算，后提交者覆盖前者，
     * 前一次点击被静默丢弃；编队上限校验同样基于过期 size，可能放行超出上限的提交。
     *
     * @return Rejected = 角色未拥有 / 编队已满 / 无变化；SaveFailed = 落盘失败（已回滚）。
     */
    suspend fun toggleFormation(characterId: String): WriteOutcome = core.withWriteLock {
        if (characterId.isEmpty()) return@withWriteLock WriteOutcome.Rejected
        if (saveData.ownedCharacters.none { it?.characterId == characterId }) return@withWriteLock WriteOutcome.Rejected

        val current = saveData.getFormationIds()
        val next = if (characterId in current) {
            current - characterId
        } else {
            if (current.size >= SaveData.MAX_FORMATION_SIZE) return@withWriteLock WriteOutcome.Rejected
            current + characterId
        }
        if (next == current) return@withWriteLock WriteOutcome.Rejected

        val original = saveData.formation
        core.transactionLocked(
            tag = "formation.toggle",
            mutate = { saveData.formation = next },
            rollback = { saveData.formation = original },
            onCommit = { core.refreshSnapshot() },
        )
    }

    // ─────────────────────────── 无尽之塔 ───────────────────────────

    /**
     * 挑战无尽之塔第 [floor] 层：
     * - 我方 = 当前编队（属性经 StatsCalculator 推导 + TeamResonance 共鸣加成）；
     * - 敌方 = 程序化生成（基础模板 × 层数缩放，seed 由 floor 派生 → 同层可复现、跨端一致）；
     * - 战斗为纯内存模拟（不触存档），胜利后的星尘奖励 / 最高层推进 / 战绩追加在同一事务内落盘，
     *   失败整体回滚且不广播。
     *
     * 门票门槛（2026-08 二期）：入场扣 [EconomyFormulas.towerTicketCost] 张战票；
     * **仅刷新最高层时返 [EconomyFormulas.towerRewardTickets] 张**——首通净消耗 0，
     * 复刷已通层与失败局净消耗 1 张。
     * 票源由每日商店免费补给兜底，零票玩家不会死局。战票不足时在模拟前直接拒绝。
     *
     * 奖励门控（2026-08-28 F1 修复）：星尘与里程碑钻石**一律按 newBest 门控**，
     * 复刷已通层不再产出星尘。此前星尘按 result.victory 无条件发放，与「胜利返票（净耗 0）」
     * 及「敌队 seed 由 floor 派生、已通层必胜」构成闭环，玩家可无限复刷同一层刷星尘。
     */
    suspend fun runTowerFloor(floor: Int): TowerOutcome = core.withWriteLock {
        // M6（2026-08-28 审查修复）：补层数上下界。此前层号只校验 >=1，超大 floor 会让
        // towerRewardSoft / towerEnemyStatScale 脱离设计区间（并存在 Int 溢出敞口）。
        if (floor < 1 || floor > EconomyFormulas.towerMaxFloor()) return@withWriteLock TowerOutcome.Rejected
        val ticketCost = EconomyFormulas.towerTicketCost()
        val ticketsExisted = saveData.items.any { it?.itemId == ServiceCore.BattleTicketItemId }
        val origTickets = core.itemCount(ServiceCore.BattleTicketItemId)
        if (origTickets < ticketCost) return@withWriteLock TowerOutcome.Rejected
        val teamIds = saveData.getFormationIds()
        if (teamIds.isEmpty()) return@withWriteLock TowerOutcome.Rejected
        val myUnits = teamIds.mapNotNull { core.unitStatsFor(it) }
        if (myUnits.isEmpty()) return@withWriteLock TowerOutcome.Rejected
        val team = TeamResonance.apply(myUnits).toTypedArray()
        val enemyTeam = buildTowerEnemies(floor)

        // 战斗 rng 从主 rng 派生：同 seed 注入下整个流程仍确定可复现。
        val result = BattleSimulator(Random(core.rng.nextLong())).simulate(team, enemyTeam, 50)

        val isDraw = result.draw
        val oldBest = saveData.towerBestFloor
        val newBest = if (result.victory && floor > oldBest) floor else null
        // F1（2026-08-28 审查修复）：星尘奖励与里程碑钻石**一律按 newBest 门控**——
        // 只有刷新最高层才发星尘，复刷已通层不再产出。
        // 旧实现按 result.victory 无条件发星尘，与「胜利返票（净耗 0）」+「敌队 seed 由 floor
        // 派生（已通层必胜）」三点构成闭环，玩家可无限复刷同一层刷星尘（经济永动机）。
        val reward = if (newBest != null) EconomyFormulas.towerRewardSoft(floor) else 0
        // P3-7 里程碑钻石：遍历 oldBest+1..newBest 所有里程碑层，跳层不丢奖励
        val rewardHard = if (newBest != null) {
            (oldBest + 1..newBest).sumOf { f -> EconomyFormulas.towerRewardHard(f) }
        } else 0
        // 门票：平局净 0（不消耗不返还）；刷新纪录返票 → 净耗 0（保留「亏损局才是真消耗」设计）；
        // 复刷已通层 / 失败局 → 净耗 ticketCost（F1：让票成为复刷的真实约束）。
        val ticketDelta = if (isDraw) 0 else -ticketCost +
            (if (newBest != null) EconomyFormulas.towerRewardTickets() else 0)

        // P3-7 平局：回合耗尽双方仍存活 → 不消耗门票、不发奖励、不推进纪录；仅记录战报
        if (isDraw) {
            // F5（2026-08-28 审查修复）：补齐战报落盘。此前直接 return，战报从未写入，
            // 与上方注释「仅记录战报」及 TowerOutcome.Draw 的 KDoc 语义自相矛盾。
            val drawRecords = saveData.battleRecords
            core.transactionLocked(
                tag = "tower.draw",
                mutate = {
                    core.appendBattleRecordCapped(
                        BattleRecord(
                            enemyName = "无尽之塔·第${floor}层",
                            enemyElement = "",
                            victory = false,
                            turns = result.turns,
                            remainingHp = result.remainingHp,
                            teamPower = team.sumOf { it.atk },
                        ),
                    )
                },
                rollback = { saveData.battleRecords = drawRecords },
                onCommit = { /* 战绩非经济，无事件广播 */ },
            )
            return@withWriteLock TowerOutcome.Draw(turns = result.turns, log = result.log)
        }

        // 防溢出（对齐 dailyOffers 的 P2-5 保护）：接近 Int 上限时 +reward 翻负 → 闪退/负数货币
        if (reward > 0 && saveData.softCurrency.toLong() + reward > Int.MAX_VALUE) {
            return@withWriteLock TowerOutcome.Rejected
        }
        if (rewardHard > 0 && saveData.hardCurrency.toLong() + rewardHard > Int.MAX_VALUE) {
            return@withWriteLock TowerOutcome.Rejected
        }

        val originalSoft = saveData.softCurrency
        val originalHard = saveData.hardCurrency
        val originalBest = saveData.towerBestFloor
        val originalRecords = saveData.battleRecords
        // 2026-09-02：胜利好感产出的事务前快照（rollback 用）。
        val origAffinity = saveData.characterAffinityData
        // F4（2026-08-28 审查修复）：胜利给出战编队发放经验，让经验条真正能推进。
        // 注意 ProgressionService.addExp 是 suspend 且内部持 writeMutex —— 本函数已在临界区内，
        // Mutex 不可重入，直接调用会死锁，故内联同口径实现（totalExp 驱动 + expToLevel 派生）。
        val expGain = if (result.victory) EconomyFormulas.towerRewardExp(floor) else 0
        val expSnapshots = if (expGain > 0) {
            teamIds.mapNotNull { id ->
                core.getSave(id)?.let { ExpSnapshot(it, it.totalExp, it.level, it.unspentPoints) }
            }
        } else {
            emptyList()
        }

        val outcome = core.transactionLocked(
            tag = "tower",
            mutate = {
                if (newBest != null) saveData.towerBestFloor = newBest
                if (reward > 0) saveData.softCurrency += reward
                if (rewardHard > 0) saveData.hardCurrency += rewardHard
                // F4：经验进 totalExp，等级由 expToLevel 派生并钳到突破阶段上限
                for (snap in expSnapshots) {
                    val s = snap.save
                    s.totalExp += expGain
                    val target = minOf(core.progression.expToLevel(s.totalExp), core.maxLevelForStage(s.stage))
                    if (target > s.level) {
                        s.unspentPoints += target - s.level
                        s.level = target
                    }
                }
                core.addItemDelta(ServiceCore.BattleTicketItemId, ticketDelta)
                core.appendBattleRecordCapped(
                    BattleRecord(
                        enemyName = "无尽之塔·第${floor}层",
                        enemyElement = "",
                        victory = result.victory,
                        turns = result.turns,
                        remainingHp = result.remainingHp,
                        teamPower = team.sumOf { it.atk },
                    ),
                )
                // 2026-09-02：胜利给出战编队全员发好感（数值走 AffinityFormulas，
                // addAffinityDelta 钳位到 MAX_AFFINITY）。失败/平局不发。
                if (result.victory) {
                    for (id in teamIds) {
                        core.addAffinityDelta(id, AffinityFormulas.BATTLE_WIN_AFFINITY)
                    }
                }
            },
            rollback = {
                saveData.softCurrency = originalSoft
                saveData.hardCurrency = originalHard
                saveData.towerBestFloor = originalBest
                saveData.battleRecords = originalRecords
                saveData.characterAffinityData = origAffinity
                // F4：经验与自动升级一并回滚
                for (snap in expSnapshots) {
                    snap.save.totalExp = snap.totalExp
                    snap.save.level = snap.level
                    snap.save.unspentPoints = snap.unspentPoints
                }
                core.restoreItemCount(ServiceCore.BattleTicketItemId, ticketsExisted, origTickets)
            },
            onCommit = {
                if (reward > 0 || rewardHard > 0 || ticketDelta != 0) core.publishCurrencyChanged()
                if (expSnapshots.isNotEmpty() || result.victory) core.publishProgressionChanged()
            },
        )

        when (outcome) {
            WriteOutcome.Success -> TowerOutcome.Completed(
                victory = result.victory,
                turns = result.turns,
                rewardSoft = reward,
                rewardHard = rewardHard,
                bestFloorAfter = newBest ?: saveData.towerBestFloor,
                log = result.log,
                rewardExp = expGain,
                recordAdvanced = newBest != null,
            )
            WriteOutcome.Rejected -> TowerOutcome.Rejected // 防御：前置校验已全部拦截
            WriteOutcome.SaveFailed -> TowerOutcome.SaveFailed
        }
    }

    /**
     * 程序化生成第 [floor] 层敌队：数量/缩放/基础模板全部走 EconomyFormulas（单一事实来源），
     * 元素按 floor 派生的 seed 随机分布——同层完全可复现，克制关系成为爬塔的策略维度。
     */
    private fun buildTowerEnemies(floor: Int): Array<UnitStats> {
        val towerRng = Random(floor * 1_000_003L + 7L)
        val scale = EconomyFormulas.towerEnemyStatScale(floor)
        val base = EconomyFormulas.towerEnemyBaseStats()
        val elements = listOf("Metal", "Wood", "Water", "Flame", "Earth", "Light", "Shadow", "Thunder")
        return Array(EconomyFormulas.towerEnemyCount(floor)) { i ->
            UnitStats(
                atk = (base[0] * scale).toInt(),
                def = (base[1] * scale).toInt(),
                hp = (base[2] * scale).toInt(),
                // P3-7：速度也随层数缩放，否则玩家永远先手、克制定位被架空
                spd = (base[3] * scale).toInt(),
                characterId = "tower_f${floor}_e$i",
                element = elements[towerRng.nextInt(elements.size)],
            )
        }
    }

    /**
     * 爬塔经验发放的事务快照（F4）。
     * 用途：runTowerFloor 已在 writeMutex 临界区内，不能用 suspend 的 addExp（Mutex 不可重入），
     * 故内联经验逻辑并靠本快照实现回滚；语义与 ProgressionService.addExp 完全一致。
     */
    private data class ExpSnapshot(
        val save: CharacterSaveState,
        val totalExp: Int,
        val level: Int,
        val unspentPoints: Int,
    )
    
    // ─────────────────────────── 策略战斗系统 ───────────────────────────
    
    /**
     * 初始化策略战斗状态。
     * 
     * @param floor 无尽之塔层数
     * @return 初始战斗状态
     */
    fun initializeStrategicBattle(floor: Int): com.milan.game.domain.battle.BattleState {
        val teamIds = saveData.getFormationIds()
        val myUnits = teamIds.mapNotNull { core.unitStatsFor(it) }
        val team = TeamResonance.apply(myUnits).toTypedArray()
        val enemyTeam = buildTowerEnemies(floor)
        
        return strategicSimulator.initializeBattle(team.toList(), enemyTeam.toList())
    }
    
    /**
     * 执行玩家行动（策略战斗）。
     * 
     * @param state 当前战斗状态
     * @param action 玩家行动
     * @return 新的战斗状态
     */
    fun executeStrategicAction(
        state: com.milan.game.domain.battle.BattleState,
        action: com.milan.game.domain.battle.PlayerAction,
    ): com.milan.game.domain.battle.BattleState {
        return strategicSimulator.executePlayerAction(state, action)
    }
    
    /**
     * 执行敌方回合（策略战斗）。
     * 
     * @param state 当前战斗状态
     * @return 新的战斗状态
     */
    fun executeStrategicEnemyTurn(
        state: com.milan.game.domain.battle.BattleState,
    ): com.milan.game.domain.battle.BattleState {
        return strategicSimulator.executeEnemyTurn(state)
    }
    
    /**
     * 更新回合状态（策略战斗）。
     * 
     * @param state 当前战斗状态
     * @return 新的战斗状态
     */
    fun updateStrategicTurnState(
        state: com.milan.game.domain.battle.BattleState,
    ): com.milan.game.domain.battle.BattleState {
        return strategicSimulator.updateTurnState(state)
    }
    
    /**
     * 检查战斗结果（策略战斗）。
     * 
     * @param state 当前战斗状态
     * @return 战斗阶段
     */
    fun checkStrategicBattleResult(
        state: com.milan.game.domain.battle.BattleState,
    ): com.milan.game.domain.battle.BattlePhase {
        return strategicSimulator.checkBattleResult(state)
    }
    
    /**
     * 获取可用的玩家行动（策略战斗）。
     * 
     * @param state 当前战斗状态
     * @param actorIndex 行动角色索引
     * @return 可用行动列表
     */
    fun getStrategicAvailableActions(
        state: com.milan.game.domain.battle.BattleState,
        actorIndex: Int,
    ): List<com.milan.game.domain.battle.PlayerAction> {
        return strategicSimulator.getAvailableActions(state, actorIndex)
    }
    
    /**
     * 结算策略战斗奖励。
     * 
     * R5-I1（2026-09-03 审查修复）：补 floor 上下界 + 空编队校验，并统一胜利好感发放
     * （对齐 [runTowerFloor] 口径）——此前三者皆无，floor=巨值可把 towerBestFloor 刷到
     * 存档级损坏（此后 1..N 层「刷新纪录」恒 false、爬塔奖励/返票永久锁死）。
     * 注：`victory` 仍由调用方断言（策略战斗状态机在 UI 侧维护），服务端不重放战斗；
     * 该项的彻底收口依赖策略战斗状态机下沉到领域层，属后续接线项。
     * 
     * @param floor 无尽之塔层数
     * @param victory 是否胜利
     * @param turns 回合数
     * @return 塔挑战结果
     */
    suspend fun settleStrategicBattle(
        floor: Int,
        victory: Boolean,
        turns: Int,
    ): TowerOutcome = core.withWriteLock {
        // R5-I1：补层数上下界（复用 runTowerFloor 的 M6 校验口径）
        if (floor < 1 || floor > EconomyFormulas.towerMaxFloor()) return@withWriteLock TowerOutcome.Rejected
        
        // 复用原有runTowerFloor的结算逻辑
        val ticketCost = EconomyFormulas.towerTicketCost()
        val ticketsExisted = saveData.items.any { it?.itemId == ServiceCore.BattleTicketItemId }
        val origTickets = core.itemCount(ServiceCore.BattleTicketItemId)
        if (origTickets < ticketCost) return@withWriteLock TowerOutcome.Rejected
        
        val teamIds = saveData.getFormationIds()
        if (teamIds.isEmpty()) return@withWriteLock TowerOutcome.Rejected
        val team = teamIds.mapNotNull { core.unitStatsFor(it) }
        if (team.isEmpty()) return@withWriteLock TowerOutcome.Rejected
        
        val oldBest = saveData.towerBestFloor
        val newBest = if (victory && floor > oldBest) floor else null
        val reward = if (newBest != null) EconomyFormulas.towerRewardSoft(floor) else 0
        val rewardHard = if (newBest != null) {
            (oldBest + 1..newBest).sumOf { f -> EconomyFormulas.towerRewardHard(f) }
        } else 0
        val ticketDelta = -ticketCost + (if (newBest != null) EconomyFormulas.towerRewardTickets() else 0)
        
        // 防溢出
        if (reward > 0 && saveData.softCurrency.toLong() + reward > Int.MAX_VALUE) {
            return@withWriteLock TowerOutcome.Rejected
        }
        if (rewardHard > 0 && saveData.hardCurrency.toLong() + rewardHard > Int.MAX_VALUE) {
            return@withWriteLock TowerOutcome.Rejected
        }
        
        val originalSoft = saveData.softCurrency
        val originalHard = saveData.hardCurrency
        val originalBest = saveData.towerBestFloor
        val originalRecords = saveData.battleRecords
        // R5-I1：胜利好感产出的事务前快照（rollback 用）
        val origAffinity = saveData.characterAffinityData
        
        val expGain = if (victory) EconomyFormulas.towerRewardExp(floor) else 0
        val expSnapshots = if (expGain > 0) {
            teamIds.mapNotNull { id ->
                core.getSave(id)?.let { ExpSnapshot(it, it.totalExp, it.level, it.unspentPoints) }
            }
        } else {
            emptyList()
        }
        
        val outcome = core.transactionLocked(
            tag = "tower.strategic",
            mutate = {
                if (newBest != null) saveData.towerBestFloor = newBest
                if (reward > 0) core.addCurrencyDelta(reward, 0)
                if (rewardHard > 0) core.addCurrencyDelta(0, rewardHard)
                
                for (snap in expSnapshots) {
                    val s = snap.save
                    s.totalExp += expGain
                    val target = minOf(core.progression.expToLevel(s.totalExp), core.maxLevelForStage(s.stage))
                    if (target > s.level) {
                        s.unspentPoints += target - s.level
                        s.level = target
                    }
                }
                
                core.addItemDelta(ServiceCore.BattleTicketItemId, ticketDelta)
                core.appendBattleRecordCapped(
                    BattleRecord(
                        enemyName = "无尽之塔·第${floor}层（策略模式）",
                        enemyElement = "",
                        victory = victory,
                        turns = turns,
                        remainingHp = 0,  // 策略战斗需要从状态中获取
                        teamPower = team.sumOf { it.atk },
                    ),
                )
                // R5-I1：胜利给出战编队全员发好感（对齐 runTowerFloor，两结算路径口径统一）
                if (victory) {
                    for (id in teamIds) {
                        core.addAffinityDelta(id, AffinityFormulas.BATTLE_WIN_AFFINITY)
                    }
                }
            },
            rollback = {
                saveData.softCurrency = originalSoft
                saveData.hardCurrency = originalHard
                saveData.towerBestFloor = originalBest
                saveData.battleRecords = originalRecords
                saveData.characterAffinityData = origAffinity
                for (snap in expSnapshots) {
                    snap.save.totalExp = snap.totalExp
                    snap.save.level = snap.level
                    snap.save.unspentPoints = snap.unspentPoints
                }
                core.restoreItemCount(ServiceCore.BattleTicketItemId, ticketsExisted, origTickets)
            },
            onCommit = {
                if (reward > 0 || rewardHard > 0 || ticketDelta != 0) core.publishCurrencyChanged()
                if (expSnapshots.isNotEmpty() || victory) core.publishProgressionChanged()
            },
        )
        
        when (outcome) {
            WriteOutcome.Success -> TowerOutcome.Completed(
                victory = victory,
                turns = turns,
                rewardSoft = reward,
                rewardHard = rewardHard,
                bestFloorAfter = newBest ?: saveData.towerBestFloor,
                log = emptyList(),  // 策略战斗日志在UI中维护
                rewardExp = expGain,
                recordAdvanced = newBest != null,
            )
            WriteOutcome.Rejected -> TowerOutcome.Rejected
            WriteOutcome.SaveFailed -> TowerOutcome.SaveFailed
        }
    }
}
