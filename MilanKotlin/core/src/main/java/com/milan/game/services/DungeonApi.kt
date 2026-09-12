package com.milan.game.services

import com.milan.game.data.DailyDungeonType

/**
 * 日常副本与深渊契约（2026-09-08 P1-5 接口化）。
 *
 * 2026-09-12 Dungeon keep：UI 已接 `ui/dungeon/`（扫荡 + 深渊挑战/手动结算骨架）；
 * 完整策略战斗闭环接入后可去掉手动 complete 入口。
 */
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
