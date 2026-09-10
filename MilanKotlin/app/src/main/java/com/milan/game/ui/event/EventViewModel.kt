package com.milan.game.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.EventTask
import com.milan.game.data.GameEvent
import com.milan.game.services.GameService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 活动任务展示行：定义 + 存档进度。 */
data class EventTaskUi(
    val task: EventTask,
    val progress: Int,
) {
    /** 进度文案：已完成显示「已完成」，否则「current / target」（current 钳位到 target）。 */
    val progressLabel: String
        get() = if (task.isCompleted) {
            "已完成"
        } else {
            "${progress.coerceIn(0, task.target)} / ${task.target}"
        }
}

/** 活动卡片展示态。 */
data class EventCardUi(
    val event: GameEvent,
    val tasks: List<EventTaskUi>,
)

/**
 * 活动页 ViewModel（2026-09-08 P1-6 C 批）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - 进入页面懒激活默认活动（[GameService.ensureActiveEvents]，EventRhythmService 设计要求）；
 * - `remember(snapshot.revision) { getActiveEvents() }` 收敛为订阅 `service.snapshot` 重算
 *   （语义等价：激活/进度写提交都会推进 revision）。
 * - 2026-09-10：合并 `getEventTaskProgress`，任务进度不再硬编码 0。
 */
class EventViewModel(
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<List<EventCardUi>>(emptyList())
    val uiState: StateFlow<List<EventCardUi>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            service.ensureActiveEvents()
            service.snapshot.collect { rebuild() }
        }
    }

    private fun rebuild() {
        _uiState.value = service.getActiveEvents().map { event ->
            val progressMap = service.getEventTaskProgress(event.eventId)
            EventCardUi(
                event = event,
                tasks = event.tasks.filterNotNull().map { task ->
                    EventTaskUi(
                        task = task,
                        progress = progressMap[task.taskId] ?: 0,
                    )
                },
            )
        }
    }
}
