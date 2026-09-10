package com.milan.game.ui.achievement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.services.AchievementStatus
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 成就页 ViewModel（2026-09-08 P1-6 试点）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - 派生读：成就解锁/领取态 = `service.achievementStatuses()`（解锁态实时推导不落盘）。
 *   订阅 `service.snapshot`（revision 推进即重算）——与旧的 `remember(snapshot.revision)`
 *   刷新语义严格一致；切片化订阅是后续优化，不在此混入。
 * - 写：claim 防重入（busy），结果映射为一次性提示经 [toasts] 流出（Composable 转发
 *   LocalFeedback，VM 不碰 Compose 设施）。
 *
 * 依赖由 [com.milan.game.di.AppGraph] 组合根注入。
 */
class AchievementViewModel(
    private val service: GameService,
) : ViewModel() {

    /** 全部成就状态（定义 + 实时解锁态 + 存档领取态），随快照 revision 重算。 */
    private val _statuses = MutableStateFlow(service.achievementStatuses())
    val statuses: StateFlow<List<AchievementStatus>> = _statuses.asStateFlow()

    init {
        // 构造即订阅快照：每次写提交（revision 推进）重算派生态——与旧的
        // remember(snapshot.revision) 刷新语义严格一致。VM 生命周期 = 导航目的地存活期，
        // 显式 collect（而非 stateIn）让订阅时序完全透明，测试可用 advanceUntilIdle 推进。
        viewModelScope.launch {
            service.snapshot.collect { _statuses.value = service.achievementStatuses() }
        }
    }

    /** 领取互斥（防连点/防重入），与旧的本地 `remember { mutableStateOf(false) }` 等价。 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 一次性提示（领取结果），Screen 用 LaunchedEffect collect 后经 LocalFeedback.show 展示。 */
    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    /** 领取成就奖励。文案/语义照搬 VM 化前（重复领取/未解锁由服务层拒绝）。 */
    fun claim(id: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val msg = when (val outcome = service.claimAchievement(id)) {
                    WriteOutcome.Success -> "奖励已发放"
                    WriteOutcome.Rejected -> "尚未解锁或已领取"
                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                _toasts.send(msg)
            } finally {
                _busy.value = false
            }
        }
    }
}
