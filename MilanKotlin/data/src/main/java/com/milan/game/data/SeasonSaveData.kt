package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 赛季系统数据。
 *
 * 设计：
 * - 竞技场赛季：每 14 天为一个赛季
 * - 赛季结束时根据排名发放奖励（星尘+钻石+专属称号）
 * - 赛季期间的胜利场次/连胜记录用于排名
 * - 赛季结束后重置竞技场数据，开启新赛季
 */
@Serializable
class SeasonSaveData(
    /** 当前赛季编号（从 1 开始）。 */
    @SerialName("SeasonNumber") var seasonNumber: Int = 1,
    /** 当前赛季起始 UTC 日序号。 */
    @SerialName("SeasonStartDay") var seasonStartDay: Long = 0,
    /** 赛季持续天数。 */
    @SerialName("SeasonDurationDays") var seasonDurationDays: Int = 14,
    /** 赛季期间竞技场胜利场次。 */
    @SerialName("SeasonWins") var seasonWins: Int = 0,
    /** 赛季期间竞技场失败场次。 */
    @SerialName("SeasonLosses") var seasonLosses: Int = 0,
    /** 赛季期间最长连胜记录。 */
    @SerialName("MaxWinStreak") var maxWinStreak: Int = 0,
    /** 当前连胜次数。 */
    @SerialName("CurrentWinStreak") var currentWinStreak: Int = 0,
    /** 赛季积分（用于排名）。 */
    @SerialName("SeasonPoints") var seasonPoints: Int = 0,
    /** 已领取的赛季奖励编号列表。 */
    @SerialName("ClaimedSeasonRewards") var claimedSeasonRewards: List<Int?> = emptyList(),
    /** 赛季期间获得的称号ID列表。 */
    @SerialName("EarnedTitles") var earnedTitles: List<String?> = emptyList(),
) {
    companion object {
        /** 每赛季持续天数 */
        const val DEFAULT_SEASON_DAYS = 14

        /** 每场竞技场胜利获得的积分 */
        const val POINTS_PER_WIN = 10

        /** 每场竞技场失败获得的积分 */
        const val POINTS_PER_LOSS = 2

        /** 连胜奖励积分倍率 */
        const val WIN_STREAK_BONUS_MULTIPLIER = 0.5

        /** 赛季奖励里程碑（积分阈值 → 奖励等级） */
        val SEASON_REWARD_MILESTONES = intArrayOf(50, 100, 200, 350, 500)

        /** 赛季奖励星尘（按等级递增） */
        val SEASON_SOFT_REWARDS = intArrayOf(5000, 10000, 15000, 20000, 30000)

        /** 赛季奖励钻石（按等级递增） */
        val SEASON_HARD_REWARDS = intArrayOf(50, 100, 150, 200, 300)
    }
}
