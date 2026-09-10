package com.milan.game.services

/**
 * 竞技场赛季契约（2026-09-08 P1-5 接口化试点）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（ArenaScreen 经
 * `GameState.service.xxx` 调用），由 [SeasonService] 实现。赛季胜负记录等内部写入
 * （[SeasonService.recordWin] / [SeasonService.recordLoss]）由门面在竞技场结算编排中触发，
 * 属服务内部面，不进入本公共契约。
 */
interface SeasonApi {
    /** 赛季状态（含天数倒计时、胜/负/连胜、积分与里程碑领取态）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    fun getSeasonStatus(): SeasonStatus

    /** 领取赛季奖励（积分不足 / 已领取返回 Rejected）。 */
    suspend fun claimSeasonReward(milestoneIndex: Int): WriteOutcome
}
