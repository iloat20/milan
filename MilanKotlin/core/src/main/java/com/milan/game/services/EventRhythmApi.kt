package com.milan.game.services

import com.milan.game.data.EventDefinition
import com.milan.game.data.EventRhythmSaveData
import com.milan.game.data.EventShopItem
import com.milan.game.data.EventType
import com.milan.game.data.GameEvent

/**
 * 活动运营契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（EventScreen / 活动入口经
 * `GameState.service.xxx` 调用），由 [EventRhythmService] 实现。含活动激活（每日定时）、
 * 活动任务进度、活动商店兑换、活动签到四组。
 */
interface EventRhythmApi {
    /** 活动运营存档数据。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    fun getEventRhythmData(): EventRhythmSaveData

    /** 当前激活中的活动列表。 */
    fun getActiveEvents(): List<GameEvent>

    /** 按类型过滤活动。 */
    fun getEventsByType(type: EventType): List<GameEvent>

    /** 活动当前是否激活（时间窗内）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    fun isEventActive(eventId: String): Boolean

    /** 上报活动任务进度（活动内成就/任务计数）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    suspend fun updateEventTaskProgress(eventId: String, taskId: String, progress: Int): WriteOutcome

    /** 活动任务进度表（taskId → 当前进度）。EventScreen 展示用；进度写在存档 `eventTaskProgress`。 */
    fun getEventTaskProgress(eventId: String): Map<String, Int>

    /** 领取活动任务奖励。 */
    suspend fun claimEventTaskReward(eventId: String, taskId: String): WriteOutcome

    /** 活动商店兑换（活动代币结算；amount 默认 1）。 */
    suspend fun redeemEventShopItem(eventId: String, itemId: String, amount: Int = 1): WriteOutcome

    /** 活动商店在售条目。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    fun getEventShopItems(eventId: String): List<EventShopItem>

    /** 活动签到。 */
    suspend fun signIn(eventId: String): WriteOutcome

    /** 活动签到进度（已签天数）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    fun getSignInProgress(eventId: String): Int

    /** 活动代币余额。 */
    fun getEventCurrencyBalance(currencyType: String): Int

    /** 默认活动定义（无存档时兜底派生）。 */
    fun getDefaultEventDefinitions(): List<EventDefinition>

    /** 确保当前应有活动处于激活态（跨日/启动时调用）。 */
    suspend fun ensureActiveEvents(): WriteOutcome
}
