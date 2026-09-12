package com.milan.game.ui.dungeon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.DailyDungeonType
import com.milan.game.services.AbyssStatus
import com.milan.game.services.DailyDungeonStatus
import com.milan.game.services.DungeonSweepOutcome
import com.milan.game.services.GameService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class DungeonUiState(
    val daily: List<DailyDungeonStatus>,
    val abyss: AbyssStatus?,
)

/** 日常副本 + 深渊 ViewModel（2026-09-12 Dungeon keep 接线）。 */
class DungeonViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<DungeonUiState> = _uiState.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): DungeonUiState = DungeonUiState(
        daily = service.getDailyDungeonStatuses(),
        abyss = runCatching { service.getAbyssStatus() }.getOrNull(),
    )

    /** 战斗层退出后强制重读（snapshot 可能已刷新，双保险）。 */
    fun refresh() {
        _uiState.value = buildState()
    }

    fun sweep(type: DailyDungeonType, times: Int = 1) {
        viewModelScope.launch {
            when (val out = service.sweepDungeon(type, times)) {
                is DungeonSweepOutcome.Success -> {
                    _uiState.value = buildState()
                    _toasts.send(
                        "扫荡成功 ×${out.times} · 星尘 +${out.totalSoft}" +
                            if (out.totalHard > 0) " · 钻石 +${out.totalHard}" else "",
                    )
                }
                DungeonSweepOutcome.Rejected -> _toasts.send("次数不足或参数无效")
            }
        }
    }
}
