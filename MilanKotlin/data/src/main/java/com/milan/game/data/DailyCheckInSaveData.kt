package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 每日签到连续奖励数据。
 *
 * 设计：
 * - 7 天为一个签到周期，每天领取递增奖励
 * - 连续签到天数影响奖励档位（连续 7 天全勤有额外大奖）
 * - 断签后连续天数归零，但累计签到天数不清零（用于成就判定）
 * - 每 7 天为一个周期，周期结束后自动重置到第 1 天
 */
@Serializable
class DailyCheckInSaveData(
    /** 当前签到周期的起始 UTC 日序号。 */
    @SerialName("CycleStartDay") var cycleStartDay: Long = 0,
    /** 当前周期内已签到天数（0~7）。 */
    @SerialName("SignedDaysInCycle") var signedDaysInCycle: Int = 0,
    /** 累计签到天数（永不清零，用于成就判定）。 */
    @SerialName("TotalSignedDays") var totalSignedDays: Int = 0,
    /** 最长连续签到天数（断签归零；用于成就判定）。 */
    @SerialName("MaxStreakDays") var maxStreakDays: Int = 0,
    /** 当前连续签到天数。 */
    @SerialName("CurrentStreakDays") var currentStreakDays: Int = 0,
    /** 已领取的周期序号列表（防重复领取周期大奖）。 */
    @SerialName("ClaimedCycleRewards") var claimedCycleRewards: List<Long?> = emptyList(),
    /**
     * 最近一次成功签到的 UTC 日序号（R7-P0-2：同日不可连签）。
     * -1 = 从未签到 / 旧档缺字段（kotlinx 默认）。旧档迁移后当日可再签一次，可接受。
     */
    @SerialName("LastSignDay") var lastSignDay: Long = -1L,
) {
    companion object {
        /** 每周期天数 */
        const val CYCLE_LENGTH = 7

        /** 每日签到奖励（星尘），按天数递增 */
        val DAILY_SOFT_REWARDS = intArrayOf(
            1000, 1500, 2000, 2500, 3000, 3500, 5000,
        )

        /** 每日签到奖励（钻石），第 7 天额外给钻石 */
        val DAILY_HARD_REWARDS = intArrayOf(
            0, 0, 0, 0, 0, 0, 100,
        )

        /** 周期全勤奖励（星尘） */
        const val CYCLE_FULL_SOFT_REWARD = 20000

        /** 周期全勤奖励（钻石） */
        const val CYCLE_FULL_HARD_REWARD = 300
    }
}
