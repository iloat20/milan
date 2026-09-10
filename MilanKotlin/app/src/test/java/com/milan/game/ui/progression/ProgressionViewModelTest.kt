package com.milan.game.ui.progression

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
 * 角色养成页 ViewModel 单测（2026-09-09 P1-6 E 批）。
 *
 * 覆盖：未拥有兜底存档与全表 id、获得后 owned/save 随快照刷新为最新养成态、
 * 升级成功路径（消化星尘、派生态等级随快照 +1）与未拥有守卫拒绝路径（提示一次，
 * 不触发写）。I13 busy 防重入语义依赖真实写耗时，这里只验证守卫与结果透传。
 */
class ProgressionViewModelTest {

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
        // 关键：无论条件是否首轮即真，都先睡一轮再排空——让「前一写操作已挂起的 IO 落盘、
        // 回续被投递到 Main 调度器队列」的连续体有机会被排空；否则测试体结束即 cancel/责任链
        // 触发"Dispatchers.Main 在 resetMain 后被访问"。
        do {
            Thread.sleep(20)
            advanceUntilIdle()
        } while (!cond() && System.currentTimeMillis() < deadline)
        advanceUntilIdle()
    }

    @Test
    fun `未拥有角色渲染兜底存档与全表id`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val id = svc.characters.first().characterId
            val vm = ProgressionViewModel(id, svc)
            advanceUntilIdle()
            val ui = vm.uiState.value
            assertEquals("def 应解析成功", id, ui.def?.characterId)
            assertFalse("新档未拥有", ui.owned)
            assertEquals("兜底等级应为 1", 1, ui.save.level)
            assertEquals("兜底阶段应为 1", 1, ui.save.stage)
            assertEquals("兜底星级应为 1", 1, ui.save.stars)
            assertEquals(
                "characterIds 应为全表 id",
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
            // VM 构造前改内存存档（搭初始态，D 批同款：真实写提交推进快照）
            svc.saveData.ownedCharacters = listOf(
                CharacterSaveState(characterId = id, level = 5, stage = 2, stars = 3),
            )
            svc.grantSoft(1)
            val vm = ProgressionViewModel(id, svc)
            awaitUntil { vm.uiState.value.owned }

            val ui = vm.uiState.value
            assertTrue("获得后 owned 应为真", ui.owned)
            assertEquals("save 应同步等级", 5, ui.save.level)
            assertEquals("save 应同步阶段", 2, ui.save.stage)
            assertEquals("save 应同步星级", 3, ui.save.stars)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `未拥有守卫拒绝升级且提示一次`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val id = svc.characters.first().characterId
            val vm = ProgressionViewModel(id, svc)
            advanceUntilIdle()
            val levelBefore = vm.uiState.value.save.level

            val toasts = mutableListOf<String>()
            val collector = launch { vm.toasts.collect { toasts += it } }
            vm.levelUp(1)
            awaitUntil { toasts.isNotEmpty() }

            assertEquals(listOf("未拥有该角色"), toasts)
            collector.cancel()
            assertEquals("等级不应变化", levelBefore, vm.uiState.value.save.level)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `升级成功路径：同步升级后 VM 随快照重建为最新等级`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val id = svc.characters.first().characterId
            // 搭初始态：拥有角色 + 足量星尘（真实写提交推进快照）
            svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = id, level = 1))
            svc.grantSoft(100_000)
            advanceUntilIdle()

            // 同步升级（服务层事务直接 await，确定性最强）
            val outcome = svc.levelUp(id, 1)
            assertEquals("升级应成功", WriteOutcome.Success, outcome)

            // VM 构造读最新快照：等级应为升级后的值
            val vm = ProgressionViewModel(id, svc)
            advanceUntilIdle()
            assertEquals("升级后等级应 +1", 2, vm.uiState.value.save.level)
            assertTrue("升级后派生态 owned 应为真", vm.uiState.value.owned)
        } finally {
            Dispatchers.resetMain()
        }
    }
}