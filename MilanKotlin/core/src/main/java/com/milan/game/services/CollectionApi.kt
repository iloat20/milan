package com.milan.game.services

/**
 * 图鉴收集契约（2026-09-08 P1-5 接口化试点）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（CollectionScreen 等经
 * `GameState.service.xxx` 调用），由 [CollectionService] 实现。UI/未来 ViewModel 依赖本
 * 接口即可获得图鉴能力，无需触碰 [GameService] 巨型门面。
 *
 * 契约名与聚合服务内部实现名分离（如内部 `getUnlockedCount` → 契约
 * [getCollectionUnlockedCount]）：聚合服务是 internal 实现细节，契约名才是稳定的公共 API。
 */
interface CollectionApi {
    /** 收集完成率（0.0~1.0）。 */
    fun getCollectionProgress(): Float

    /** 已解锁角色数。 */
    fun getCollectionUnlockedCount(): Int

    /** 收集里程碑状态列表（解锁态 + 领取态）。 */
    fun getCollectionMilestones(): List<CollectionMilestoneStatus>

    /** 领取收集里程碑奖励（未达门槛 / 已领取返回 Rejected）。 */
    suspend fun claimCollectionMilestone(required: Int): WriteOutcome

    /** 全部图鉴条目（含未解锁角色，供图鉴页灰显）。 */
    fun getCollectionEntries(): List<CollectionCharacterEntry>
}
