package com.milan.game.ui.event

import com.milan.game.data.SaveProvider
import com.milan.game.services.GameService
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 活动页 ViewModel 单测（2026-09-08 P1-6 C 批）。
 *
 * 覆盖：init 懒激活默认活动后派生状态与 service 只读 API 一致（激活写提交
 * 经 Dispatchers.IO 落盘，testScheduler 虚拟时间不覆盖 → awaitUntil 轮询兜底）。
 */
class EventViewModelTest {

    private val dataJson: String =
        File("src/main/assets/data.json").takeIf { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("测试需真实内容文件：app/src/main/assets/data.json")

    private class MemoryProvider : SaveProvider {
        var stored: String? = null
        override fun save(json: String): Boolean {
            stored = json
            return true
        }

        override fun load(): String = stored ?: ""
        override fun delete() {
            stored = null
        }

        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = null
    }

    private fun service() = GameService(MemoryProvider(), dataJson, {}, Random(42))

    /** IO 落盘不在 testScheduler 虚拟时间内：真实时间轮询 + 反复排空调度器直至条件满足。 */
    private fun TestScope.awaitUntil(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cond() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            advanceUntilIdle()
        }
        advanceUntilIdle()
    }

    @Test
    fun `懒激活后派生状态与只读API一致`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = EventViewModel(svc)
            // init：懒激活（真实 IO 落盘）→ collect 重建。收敛条件必须要求"非空"，
            // 否则两侧同为空表时首轮即真，断言会与 init 的 mutate/collect 时序赛跑。
            awaitUntil { vm.uiState.value.isNotEmpty() }
            val cards = vm.uiState.value
            assertEquals(svc.getActiveEvents().map { it.eventId }, cards.map { it.event.eventId })
            // 任务进度来自存档 eventTaskProgress（默认 0），不再硬编码展示层
            cards.forEach { card ->
                val progressMap = svc.getEventTaskProgress(card.event.eventId)
                card.tasks.forEach { taskUi ->
                    assertEquals(progressMap[taskUi.task.taskId] ?: 0, taskUi.progress)
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
