package com.milan.game.ui.arena

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.ArenaOpponent
import com.milan.game.data.ArenaSaveData
import com.milan.game.data.SeasonReward
import com.milan.game.services.ArenaChallengeOutcome
import com.milan.game.services.GameService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 竞技场页 UI 状态（段位/积分/剩余次数/战绩/对手/赛季奖励，随快照 revision 重算）。 */
data class ArenaUiState(
    val rankTitle: String,
    val points: Int,
    /** 今日剩余免费挑战次数（跨日重置与 ArenaService.lastRefreshTime 同口径）。 */
    val attacksLeft: Int,
    val winCount: Int,
    val loseCount: Int,
    val opponents: List<ArenaOpponent>,
    val seasonRewards: List<SeasonReward>,
)

/**
 * 竞技场页 ViewModel（2026-09-09 P1-6 D 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - 四处 remember(snap.revision) 派生读收敛为订阅 `service.snapshot` 重算（语义等价）；
 * - 剩余次数推导搬进 [buildState]（epochDay = millis / 86_400_000，与 ArenaService 同口径）；
 * - 挑战动作共用 busy 防重入，三态反馈走 [toasts]；
 * - 2026-09-10：成功挑战写入 [lastResult]，Screen 弹出结算卡（胜负/回合/积分）。
 */
class ArenaViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<ArenaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): ArenaUiState {
        val arenaData = service.getArenaData()
        // 每日剩余挑战次数（跨日重置与 ArenaService.lastRefreshTime 同口径：epochDay = millis / 86_400_000）
        val todayKey = System.currentTimeMillis() / 86_400_000L
        val attacksUsed = if (arenaData.lastRefreshTime == todayKey) arenaData.attackCount else 0
        val (rankNum, rankTitle) = service.getArenaRank() // rankNum 仅用于校验存档一致性，UI 只展示称号
        return ArenaUiState(
            rankTitle = rankTitle,
            points = arenaData.arenaPoints,
            attacksLeft = (ArenaSaveData.DAILY_FREE_ATTACKS - attacksUsed).coerceAtLeast(0),
            winCount = arenaData.winCount,
            loseCount = arenaData.loseCount,
            opponents = service.getOpponents(),
            seasonRewards = service.getSeasonRewards(),
        )
    }

    /** 挑战互斥（防连点/防重入）。 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    /** 最近一次成功挑战的结果（Screen 弹结算卡；dismiss 后清空）。 */
    private val _lastResult = MutableStateFlow<ArenaChallengeOutcome.Completed?>(null)
    val lastResult: StateFlow<ArenaChallengeOutcome.Completed?> = _lastResult.asStateFlow()

    /** 挑战对手（写盘结算 + 奖励发放，ArenaApi.challengeOpponent 事务）。 */
    fun challenge(opponent: ArenaOpponent) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val outcome = service.challengeOpponent(opponent)) {
                    is ArenaChallengeOutcome.Completed -> {
                        _lastResult.value = outcome
                        // 结算卡为主反馈；toast 仅作兜底（无障碍/读屏仍可感知）
                        _toasts.send(
                            if (outcome.victory) "挑战胜利！积分 ${outcome.pointsDelta}" else "挑战失败，积分 ${outcome.pointsDelta}",
                        )
                    }
                    ArenaChallengeOutcome.Rejected -> _toasts.send("挑战次数不足或未编队")
                    ArenaChallengeOutcome.SaveFailed -> _toasts.send("保存失败，请重试")
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun dismissResult() {
        _lastResult.value = null
    }
}
