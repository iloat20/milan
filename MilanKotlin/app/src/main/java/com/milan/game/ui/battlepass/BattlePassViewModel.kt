package com.milan.game.ui.battlepass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.BattlePassReward
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 纪行页 UI 状态（派生自 Monetization 数据 + 奖励定义，随快照 revision 重算）。 */
data class BattlePassUiState(
    val level: Int,
    val exp: Int,
    val isPremium: Boolean,
    val claimedLevels: List<Int?>,
    val rewards: List<BattlePassReward>,
)

/**
 * 纪行页 ViewModel（2026-09-08 P1-6 B 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：派生读订阅 `service.snapshot` 每次写提交重算
 * （等价旧的 remember(snapshot.revision)）；购买/领取共用 busy 防重入；三态反馈走
 * [toasts]（Composable 转发 LocalFeedback）。
 */
class BattlePassViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<BattlePassUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): BattlePassUiState {
        val data = service.getMonetizationData()
        return BattlePassUiState(
            level = data.battlePassLevel,
            exp = data.battlePassExp,
            isPremium = data.battlePassPremium,
            claimedLevels = data.claimedBPRewards,
            rewards = service.getBattlePassRewards(),
        )
    }

    /** 领取互斥（防连点/防重入），购买与领取共用（与原实现同一 busy 语义）。 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    /** 领取纪行奖励。 */
    fun claimReward(level: Int) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val msg = when (service.claimBattlePassReward(level)) {
                    WriteOutcome.Success -> "已领取 Lv.$level 奖励"
                    WriteOutcome.Rejected -> "等级不足或已领取"
                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                _toasts.send(msg)
            } finally {
                _busy.value = false
            }
        }
    }

    /** 购买豪华版（[PREMIUM_COST_HARD] 钻石）。 */
    fun purchasePremium() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val msg = when (service.purchaseBattlePass(PREMIUM_COST_HARD)) {
                    WriteOutcome.Success -> "豪华版已激活"
                    WriteOutcome.Rejected -> "钻石不足或已购买"
                    WriteOutcome.SaveFailed -> "保存失败，请重试"
                }
                _toasts.send(msg)
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        /** 豪华版纪行售价（钻石）。UI 确认弹窗与购买共用，禁止就地写死。 */
        const val PREMIUM_COST_HARD = 680
    }
}
