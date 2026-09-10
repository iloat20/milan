package com.milan.game.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.EventType
import com.milan.game.data.EventShopItem
import com.milan.game.data.EventTask
import com.milan.game.data.GameEvent
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 活动任务展示行：定义 + 存档进度 + 领取态。 */
data class EventTaskUi(
    val task: EventTask,
    val progress: Int,
    val claimed: Boolean,
) {
    /** 是否已达目标（EventTask.isCompleted 字段默认 false，进度以存档 map 为准）。 */
    val reachedTarget: Boolean get() = progress >= task.target

    /** 进度文案：已领取 / 已完成可领 / 进行中。 */
    val progressLabel: String
        get() = when {
            claimed -> "已领取"
            reachedTarget -> "可领取"
            else -> "${progress.coerceIn(0, task.target)} / ${task.target}"
        }

    /** 是否可点击领取。 */
    val canClaim: Boolean get() = reachedTarget && !claimed
}

/** 活动商店商品展示行。 */
data class EventShopUi(
    val item: EventShopItem,
    val redeemed: Int,
)

/** 活动卡片展示态。 */
data class EventCardUi(
    val event: GameEvent,
    val tasks: List<EventTaskUi>,
    val shop: List<EventShopUi>,
    val signInProgress: Int,
    val isSignInType: Boolean,
    /** 签到是否可点（未达上限）。 */
    val canSignIn: Boolean,
)

/** 活动页整页状态（卡片 + 代币余额）。 */
data class EventUiState(
    val cards: List<EventCardUi>,
    val eventBalances: Map<String, Int>,
    val busy: Boolean = false,
)

/**
 * 活动页 ViewModel（2026-09-08 P1-6 C 批；2026-09-10 补全写操作）。
 *
 * - 进入页面懒激活默认活动（[GameService.ensureActiveEvents]）；
 * - 订阅 `service.snapshot` 重算派生状态；
 * - 写操作：签到 / 领取任务奖励 / 商店兑换，均走 [WriteOutcome] 三态反馈。
 */
class EventViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventUiState(emptyList(), emptyMap()))
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    init {
        viewModelScope.launch {
            service.ensureActiveEvents()
            service.snapshot.collect { rebuild() }
        }
    }

    private fun rebuild() {
        val cards = service.getActiveEvents().map { event ->
            val progressMap = service.getEventTaskProgress(event.eventId)
            val claimed = service.getClaimedEventTaskRewards(event.eventId)
            val redemptions = service.getEventShopRedemptions(event.eventId)
            val signInDays = service.getSignInProgress(event.eventId)
            EventCardUi(
                event = event,
                tasks = event.tasks.filterNotNull().map { task ->
                    EventTaskUi(
                        task = task,
                        progress = progressMap[task.taskId] ?: 0,
                        claimed = task.taskId in claimed,
                    )
                },
                shop = event.shopItems.filterNotNull().map { item ->
                    EventShopUi(item = item, redeemed = redemptions[item.itemId] ?: 0)
                },
                signInProgress = signInDays,
                isSignInType = event.eventType == EventType.SIGN_IN.name,
                canSignIn = event.eventType == EventType.SIGN_IN.name &&
                    signInDays < event.signInDays,
            )
        }
        val balances = buildMap {
            for (card in cards) {
                for (task in card.tasks) {
                    val t = task.task.rewardType
                    if (t != "SOFT_CURRENCY" && t != "HARD_CURRENCY") {
                        put(t, service.getEventCurrencyBalance(t))
                    }
                }
                for (s in card.shop) {
                    val t = s.item.currencyType
                    if (t != "SOFT_CURRENCY" && t != "HARD_CURRENCY") {
                        put(t, service.getEventCurrencyBalance(t))
                    }
                }
            }
        }
        _uiState.value = EventUiState(cards = cards, eventBalances = balances)
    }

    /** 签到。 */
    fun signIn(eventId: String) = launchWrite(
        onOk = "签到成功，获得星玉",
        onRejected = "今日已签到或活动不可签到",
    ) {
        service.signIn(eventId)
    }

    /** 领取活动任务奖励。 */
    fun claimTask(eventId: String, taskId: String) = launchWrite(
        onOk = "任务奖励已发放",
        onRejected = "条件不满足或已领取",
    ) {
        service.claimEventTaskReward(eventId, taskId)
    }

    /** 兑换活动商店商品（默认 1 件）。 */
    fun redeem(eventId: String, itemId: String) = launchWrite(
        onOk = "兑换成功",
        onRejected = "代币不足或已达兑换上限",
    ) {
        service.redeemEventShopItem(eventId, itemId, amount = 1)
    }

    private fun launchWrite(
        onOk: String,
        onRejected: String,
        block: suspend () -> WriteOutcome,
    ) {
        viewModelScope.launch {
            if (_busy.value) return@launch
            _busy.value = true
            try {
                val msg = when (block()) {
                    WriteOutcome.Success -> onOk
                    WriteOutcome.Rejected -> onRejected
                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                _toasts.send(msg)
            } catch (_: Exception) {
                _toasts.send("操作异常，请重试")
            } finally {
                _busy.value = false
            }
        }
    }
}
