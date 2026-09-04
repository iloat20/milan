package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 每日任务系统数据。
 *
 * 设计对标原神/崩铁的每日委托：
 * - 每日6个随机任务，完成获得活跃度
 * - 活跃度达到里程碑（20/40/60/80/100）可领取宝箱奖励
 * - 每日UTC 04:00重置
 */
@Serializable
class DailyMissionSaveData(
    /** 当前UTC日序号（跨日检测）。 */
    @SerialName("CurrentDay") var currentDay: Long = 0,
    /** 今日活跃度（0-100）。 */
    @SerialName("ActivityPoints") var activityPoints: Int = 0,
    /** 今日已领取的活跃度宝箱（20/40/60/80/100）。 */
    @SerialName("ClaimedChests") var claimedChests: List<Int?> = emptyList(),
    /** 今日任务进度（任务ID → 当前进度）。 */
    @SerialName("MissionProgress") var missionProgress: Map<String, Int> = emptyMap(),
    /** 今日已完成的任务ID列表。 */
    @SerialName("CompletedMissions") var completedMissions: List<String?> = emptyList(),
    /** 累计完成每日任务总数（成就判定用）。 */
    @SerialName("TotalMissionsCompleted") var totalMissionsCompleted: Int = 0,
    /** 累计获得活跃度（成就判定用）。 */
    @SerialName("TotalActivityPoints") var totalActivityPoints: Int = 0,
) {
    companion object {
        /** 每日任务数量 */
        const val DAILY_MISSION_COUNT = 6
        /** 活跃度宝箱里程碑 */
        val ACTIVITY_MILESTONES = listOf(20, 40, 60, 80, 100)
        /** 各里程碑奖励星尘 */
        val MILESTONE_REWARDS = mapOf(
            20 to 5000,
            40 to 10000,
            60 to 15000,
            80 to 20000,
            100 to 30000,
        )
        /** 各里程碑奖励星琼（仅80和100） */
        val MILESTONE_HARD_REWARDS = mapOf(
            80 to 50,
            100 to 100,
        )
    }
}

/**
 * 每日任务定义。
 */
data class DailyMissionDef(
    val id: String,
    val title: String,
    val description: String,
    val type: DailyMissionType,
    val targetCount: Int,
    val activityReward: Int,
    val icon: String,
)

/**
 * 每日任务类型。
 */
enum class DailyMissionType {
    PULL_GACHA,          // 抽卡N次
    BATTLE_TOWER,        // 挑战无尽之塔N次
    LEVEL_UP_CHARACTER,  // 升级角色N次
    SPEND_SOFT_CURRENCY, // 消耗星尘N
    COMPLETE_STORY,      // 完成剧情关卡N次
    CHECK_IN,            // 签到（登录即完成）
    FRIEND_GIFT,         // 赠送好友体力N次
    CHALLENGE_ARENA,     // 挑战竞技场N次
    ENHANCE_EQUIPMENT,   // 强化装备N次
    CLAIM_AFFINITY,      // 领取好感度奖励N次
}
