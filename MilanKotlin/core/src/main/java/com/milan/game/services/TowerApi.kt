package com.milan.game.services

/**
 * 编队与爬塔契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（DeckScreen / TowerScreen /
 * 战略战斗入口经 `GameState.service.xxx` 调用），由 [TowerService] 实现。GameService 仅对
 * [runTowerFloor] 做显式 override 追加 onProgress 编排，其余方法行为与拆分前等价。
 */
interface TowerApi {
    /** 当前编队 characterId 列表（顺序即槽位顺序）。 */
    fun getFormation(): List<String>

    /** 设置出战编队（整体替换；超编/未拥有由内部规则拒绝）。 */
    suspend fun setFormation(characterIds: List<String>): WriteOutcome

    /** 切换单个角色的入队状态（读-改-写在锁内原子完成，防连点竞态）。 */
    suspend fun toggleFormation(characterId: String): WriteOutcome

    /** 挑战无尽之塔第 [floor] 层（一次性结算 + 奖励发放 + 纪录推进）。 */
    suspend fun runTowerFloor(floor: Int): TowerOutcome

    // ── 战略回合制战斗（交互式）──
    // 2026-09-10：StrategicBattleScreen / StrategicBattleViewModel 已接线，不再是死功能；
    // 移除 P2-11 过期 @Deprecated，避免误导后续重构删 API。

    /** 初始化战略战斗（读档编队 + 楼层敌人）。 */
    fun initializeStrategicBattle(floor: Int): com.milan.game.domain.battle.BattleState

    /** 执行玩家行动（技能/普攻）。 */
    fun executeStrategicAction(
        state: com.milan.game.domain.battle.BattleState,
        action: com.milan.game.domain.battle.PlayerAction,
    ): com.milan.game.domain.battle.BattleState

    /** 执行敌方回合。 */
    fun executeStrategicEnemyTurn(state: com.milan.game.domain.battle.BattleState): com.milan.game.domain.battle.BattleState

    /** 推进回合状态（冷却/持续效果递减）。 */
    fun updateStrategicTurnState(state: com.milan.game.domain.battle.BattleState): com.milan.game.domain.battle.BattleState

    /** 判定战斗结果（进行中/胜利/失败）。 */
    fun checkStrategicBattleResult(state: com.milan.game.domain.battle.BattleState): com.milan.game.domain.battle.BattlePhase

    /** 指定行动角色当前可用行动列表。 */
    fun getStrategicAvailableActions(
        state: com.milan.game.domain.battle.BattleState,
        actorIndex: Int,
    ): List<com.milan.game.domain.battle.PlayerAction>

    /** 结算战略战斗（写盘 + 奖励，与一次性结算共用 TowerOutcome 语义）。 */
    suspend fun settleStrategicBattle(floor: Int, victory: Boolean, turns: Int): TowerOutcome

    // ── 扫荡 ──
    // ⚠️ P2-11 (B12) 死功能：以下 3 个扫荡/统计方法 UI 层零调用。

    /** 扫荡已通关层（快速结算，多次消耗）。 */
    @Deprecated("P2-11: 扫荡功能UI零调用", level = DeprecationLevel.WARNING)
    suspend fun sweepTower(floor: Int, times: Int): TowerSweepOutcome

    /** 扫荡连扫信息（已通关层 + 可扫次数）。 */
    @Deprecated("P2-11: 扫荡功能UI零调用", level = DeprecationLevel.WARNING)
    fun getTowerSweepStreakInfo(times: Int): TowerSweepStreakInfo

    /** 爬塔统计（最高层/总胜场等，供主页/图鉴展示）。 */
    @Deprecated("P2-11: 爬塔统计UI零调用", level = DeprecationLevel.WARNING)
    fun getTowerStats(): TowerStats
}
