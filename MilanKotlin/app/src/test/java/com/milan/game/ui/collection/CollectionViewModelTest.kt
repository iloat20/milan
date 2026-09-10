package com.milan.game.ui.collection

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
 * 神谱图鉴页 ViewModel 单测（2026-09-09 P1-6 D 批）。
 *
 * 覆盖：内容全表与只读 API 一致、新档拥有视图为空、存档变更随快照刷新后
 * 拥有视图/ownedById 同步（真实写提交触发快照推进，awaitUntil 兜底 IO 时序）。
 */
class CollectionViewModelTest {

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
    fun `初始状态内容全表一致且新档拥有为空`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = CollectionViewModel(svc)
            advanceUntilIdle()
            assertEquals("all 应与内容全表一致", svc.characters, vm.all)
            assertTrue("内容全表不应为空", vm.all.isNotEmpty())
            assertTrue("新档拥有视图应为空", vm.uiState.value.owned.isEmpty())
            assertTrue("新档 ownedById 应为空", vm.uiState.value.ownedById.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `获得角色后拥有视图随快照刷新`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = CollectionViewModel(svc)
            advanceUntilIdle()
            val id = svc.characters.first().characterId

            // 测试搭初始态：直接改内存存档（免走抽卡链），再用一次真实写提交推进快照。
            svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = id))
            svc.grantSoft(1)
            awaitUntil { vm.uiState.value.owned.size == 1 }

            assertEquals("拥有视图应含新角色", listOf(id), vm.uiState.value.owned.map { it.save.characterId })
            assertEquals(
                "ownedById 应可按 id 索引",
                id,
                vm.uiState.value.ownedById[id]?.save?.characterId,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}
