package com.milan.game.ui.gacha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.PullLogEntry
import com.milan.game.services.GameService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 抽卡历史页派生态（随快照 revision 重算，最新在前）。 */
data class PullHistoryUiState(
    /** 历史记录倒序（最新在最上）；空 = 尚无召唤。 */
    val entries: List<PullLogEntry>,
)

/** 抽卡历史页 ViewModel（2026-09-09 P1-6 F 批）。 */
class PullHistoryViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<PullHistoryUiState> = _uiState.asStateFlow()

    /** 时间格式（MM-dd HH:mm，历史行展示）。 */
    val timeFormatter: SimpleDateFormat = rememberTimeFormatter()

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    private fun buildState(): PullHistoryUiState =
        PullHistoryUiState(entries = service.pullHistory().asReversed())

    private fun rememberTimeFormatter(): SimpleDateFormat =
        SimpleDateFormat("MM-dd HH:mm", Locale.US)

    /** 便捷：由时间戳渲染展示文本（HistoryRow 用，替代 Screen 侧 Date 转换）。 */
    fun formatTime(timestamp: Long): String = timeFormatter.format(Date(timestamp))
}