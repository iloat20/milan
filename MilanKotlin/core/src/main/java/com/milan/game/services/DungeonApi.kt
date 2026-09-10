package com.milan.game.services

import com.milan.game.data.DailyDungeonType

/**
 * 日常副本与深渊契约（2026-09-08 P1-5 接口化）。
 *
 * ⚠️ **P2-11 (B12) 死功能**：副本/深渊系统 UI 层零调用，
 * 本接口全部方法无生产调用点。待后续接线或移除。
 */
@Deprecated("P2-11: 副本深渊系统UI零调用，待接线或移除", level = DeprecationLevel.WARNING)
interface DungeonApi {
    /** 今日日常副本挑战状态（剩余次数 / 已扫荡）。 */
    fun getDailyDungeonStatuses(): List<DailyDungeonStatus>

    /** 扫荡日常副本（已通关内容一键完成，多次消耗）。 */
    suspend fun sweepDungeon(type: DailyDungeonType, times: Int): DungeonSweepOutcome

    /** 深渊状态（已通层数 / 星数）。 */
    fun getAbyssStatus(): AbyssStatus

    /** 挑战深渊层（一次性结算 + 星级评价）。 */
    suspend fun challengeAbyss(floor: Int): AbyssChallengeOutcome

    /** 深渊阶段结算（战斗胜利后由编排调用，落盘星级与奖励）。 */
    suspend fun completeAbyssStage(floor: Int, stars: Int): WriteOutcome
}
