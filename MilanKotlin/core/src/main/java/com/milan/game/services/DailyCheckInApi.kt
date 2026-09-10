package com.milan.game.services

/**
 * 每日签到契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（签到页经
 * `GameState.service.xxx` 调用），由 [DailyCheckInService] 实现。周期重置
 * （[DailyCheckInService.ensureTodayReset]）是签到内部前置步骤，不进入本公共契约。
 */
interface DailyCheckInApi {
    /** 执行今日签到（已签到 / 跨周期未重置等情况返回对应结果）。 */
    suspend fun signToday(): WriteOutcome

    /** 今日是否已签到。 */
    @Deprecated("P2-11: UI层零调用（UI通过getCheckInStatus获取状态）", level = DeprecationLevel.WARNING)
    fun isTodaySigned(): Boolean

    /** 今日签到状态（周期进度 + 奖励预览 + 连续天数，供 UI 渲染）。 */
    fun getCheckInStatus(): CheckInDayStatus
}
