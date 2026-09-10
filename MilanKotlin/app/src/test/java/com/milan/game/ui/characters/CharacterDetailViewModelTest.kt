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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 角色详情页 ViewModel 单测（2026-09-09 P1-6 D 批）。
 *
 * 覆盖：未拥有兜底存档（Level/Stage/Stars=1）、全表角色 id（左右切换源）、
 * 存档变更随快照刷新后 owned/save 同步为最新养成态（P2-13/P3-5 语义，
 * 真实写提交触发快照推进，awaitUntil 兜底 IO 时序）。
 */
class CharacterDetailViewModelTest {

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
    fun `未拥有角色渲染兜底存档与全表id`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val id = svc.characters.first().characterId
            val vm = CharacterDetailViewModel(id, svc)
            advanceUntilIdle()
            val ui = vm.uiState.value
            assertEquals("def 应解析成功", id, ui.def?.characterId)
            assertFalse("新档未拥有", ui.owned)
            assertEquals("兜底存档等级应为 1", 1, ui.save.level)
            assertEquals("兜底存档阶段应为 1", 1, ui.save.stage)
            assertEquals("兜底存档星级应为 1", 1, ui.save.stars)
            assertEquals(
                "characterIds 应为全表 id（左右切换源）",
                svc.characters.map { it.characterId },
                ui.characterIds,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `获得角色后owned与save随快照刷新为最新养成态`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val id = svc.characters.first().characterId
            val vm = CharacterDetailViewModel(id, svc)
            advanceUntilIdle()
            assertFalse("前置：新档未拥有", vm.uiState.value.owned)

            // 测试搭初始态：直接改内存存档（养成态 level=5/stage=2/stars=3），
            // 再用一次真实写提交推进快照（ownedSaves 随快照刷新）。
            svc.saveData.ownedCharacters = listOf(
                CharacterSaveState(characterId = id, level = 5, stage = 2, stars = 3),
            )
            svc.grantSoft(1)
            awaitUntil { vm.uiState.value.owned }

            val ui = vm.uiState.value
            assertTrue("获得后 owned 应为真", ui.owned)
            assertEquals("save 应同步为最新养成态等级", 5, ui.save.level)
            assertEquals("save 应同步为最新养成态阶段", 2, ui.save.stage)
            assertEquals("save 应同步为最新养成态星级", 3, ui.save.stars)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
