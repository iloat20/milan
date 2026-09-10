package com.milan.game.ui.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.services.ChestStatus
import com.milan.game.services.DailyMissionStatus
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 每日任务页 UI 状态（今日任务 + 活跃度宝箱 + 活跃度进度，随快照 revision 重算）。 */
data class DailyMissionUiState(
    val missions: List<DailyMissionStatus>,
    val chestStatuses: List<ChestStatus>,
    val activityPoints: Int,
    val claimedChests: List<Int?>,
)

/**
 * 每日任务页 ViewModel（2026-09-08 P1-6 C 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - 进入页面即触发跨日重置（[ensureDailyMissionReset]，R5-I4：事务 + 落盘，避免只读 API
 *   锁外写）；重置成功推进 revision，派生状态随之刷新——与旧 LaunchedEffect(Unit) 等价。
 * - 三处 remember(snap.revision) 重算收敛为订阅 `service.snapshot`（语义等价）。
 * - 宝箱领取的状态门控与提示下沉进 VM（未解锁/已领取也给出提示，避免"点了没反应"）。
 */
class DailyMissionViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<DailyMissionUiState> = _uiState.asStateFlow()

    init {
        // 进入页面触发跨日重置（等价旧 LaunchedEffect(Unit)），随后订阅快照刷新派生态。
        viewModelScope.launch {
            service.ensureDailyMissionReset()
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): DailyMissionUiState {
        val data = service.getDailyMissionData()
        return DailyMissionUiState(
            missions = service.getTodayMissions(),
            chestStatuses = service.getChestStatuses(),
            activityPoints = data.activityPoints,
            claimedChests = data.claimedChests,
        )
    }

    /** 领取互斥（防连点/防重入）。 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    /**
     * 领取活跃度宝箱。状态门控 + 反馈在此完成：未知里程碑静默忽略、已领取/未解锁
     * 各给出提示，仅可领取时才走写事务（与原 Screen 的 when 门控逐分支等价）。
     */
    fun claimChest(milestone: Int) {
        if (_busy.value) return
        val st = service.getChestStatuses().firstOrNull { it.milestone == milestone }
        when {
            st == null -> Unit
            st.claimed -> viewModelScope.launch { _toasts.send("该宝箱已领取") }
            !st.unlocked -> viewModelScope.launch { _toasts.send("活跃度达到 $milestone 点可领取") }
            else -> viewModelScope.launch {
                _busy.value = true
                try {
                    val msg = when (service.claimActivityChest(milestone)) {
                        WriteOutcome.Success -> "已领取 ${milestone} 点活跃度宝箱"
                        WriteOutcome.Rejected -> "活跃度不足或已领取"
                        WriteOutcome.SaveFailed -> "保存失败，请重试"
                    }
                    _toasts.send(msg)
                } finally {
                    _busy.value = false
                }
            }
        }
    }
}
