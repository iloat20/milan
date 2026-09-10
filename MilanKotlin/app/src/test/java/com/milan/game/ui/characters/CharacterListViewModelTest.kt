package com.milan.game.ui.characters

import com.milan.game.data.CharacterSaveState
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 我的角色列表页 ViewModel 单测（2026-09-09 P1-6 D 批）。
 *
 * 覆盖：roster 与内容全表一致、新档计数/拥有 id 集为空、存档变更随快照刷新后
 * owned/ownedCount/ownedIds 同步（真实写提交触发快照推进，awaitUntil 兜底 IO 时序）。
 */
class CharacterListViewModelTest {

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
    fun `初始状态roster一致且新档计数为零`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = CharacterListViewModel(svc)
            advanceUntilIdle()
            assertEquals("roster 应与内容全表一致", svc.characters, vm.roster)
            assertTrue("内容全表不应为空", vm.roster.isNotEmpty())
            val ui = vm.uiState.value
            assertEquals("新档已拥有计数应为 0", 0, ui.ownedCount)
            assertTrue("新档拥有视图应为空", ui.owned.isEmpty())
            assertTrue("新档 ownedIds 应为空", ui.ownedIds.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `获得角色后计数与ownedIds随快照刷新`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = CharacterListViewModel(svc)
            advanceUntilIdle()
            val id = svc.characters.first().characterId

            // 测试搭初始态：直接改内存存档，再用一次真实写提交推进快照。
            svc.saveData.ownedCharacters = listOf(
                CharacterSaveState(characterId = id, level = 3),
            )
            svc.grantSoft(1)
            awaitUntil { vm.uiState.value.ownedCount == 1 }

            val ui = vm.uiState.value
            assertEquals("ownedCount 应为 1", 1, ui.ownedCount)
            assertEquals("owned 应含新角色", listOf(id), ui.owned.map { it.save.characterId })
            assertEquals("ownedIds 应含新角色 id", setOf(id), ui.ownedIds)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
