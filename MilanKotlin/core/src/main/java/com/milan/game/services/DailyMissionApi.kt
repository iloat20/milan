package com.milan.game.services

import com.milan.game.data.DailyMissionType
import com.milan.game.data.DailyMissionSaveData

/**
 * 每日任务契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（DailyMissionScreen 经
 * `GameState.service.xxx` 调用；reportDailyMissionProgress 是门面 onProgress 编排的
 * 唯一落点），由 [DailyMissionService] 实现。进度累计查询（getTotalCompleted）供成就
 * 判定使用，不进本公共契约。
 */
interface DailyMissionApi {
    /** 每日任务存档数据（今日任务集合 / 宝箱领取态）。 */
    fun getDailyMissionData(): DailyMissionSaveData

    /** 今日任务列表（含实时进度）。 */
    fun getTodayMissions(): List<DailyMissionStatus>

    /** 领取活跃度宝箱（活跃度不足 / 已领取 → Rejected）。 */
    suspend fun claimActivityChest(milestone: Int): WriteOutcome

    /** 活跃度宝箱状态列表。 */
    fun getChestStatuses(): List<ChestStatus>

    /** 确保今日任务已重置（跨日时重建；门面在 DailyMissionScreen 进入时触发）。 */
    suspend fun ensureDailyMissionReset(): WriteOutcome

    /** 上报某类型任务进度（amount 默认 1；门面 onProgress 编排的唯一落点）。 */
    suspend fun reportDailyMissionProgress(type: DailyMissionType, amount: Int = 1)
}
