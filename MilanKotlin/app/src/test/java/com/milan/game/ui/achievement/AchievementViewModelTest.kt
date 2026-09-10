package com.milan.game.ui.achievement

import com.milan.game.data.SaveProvider
import com.milan.game.services.GameService
import com.milan.game.services.WriteOutcome
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成就页 ViewModel 单测（2026-09-08 P1-6 试点）。
 *
 * VM 依赖真实 [GameService]（注入，不碰 GameState 单例）；viewModelScope 用
 * Dispatchers.Main → 测试内 setMain 到 runTest 同一调度器，advanceUntilIdle 驱动。
 * 覆盖：派生状态初值、写提交（revision 推进）后重算、领取拒绝路径提示与 busy 复位。
 */
class AchievementViewModelTest {

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

    /** 固定「今天」：signToday 的跨周期 ensure 路径必然 refreshSnapshot（revision 推进）。 */
    private fun service() = GameService(MemoryProvider(), dataJson, {}, Random(42), today = { 20_000L })

    @Test
    fun `状态随服务派生且随写提交重算`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = AchievementViewModel(svc)
            val initial = vm.statuses.value
            assertTrue("成就列表不应为空", initial.isNotEmpty())
            assertEquals("成就 id 应唯一", initial.size, initial.map { it.def.id }.toSet().size)

            // 解锁翻转前提：现有 ownedCount=0 →「拥有 1 角色」(Achievements L67) 未解锁。
            // ① 直接塞一个 owned 角色 ② 触发一次会 refreshSnapshot 的写提交（签到跨周期
            // ensure）→ revision 推进 → VM 派生流重算 → 该成就翻转为 unlocked。
            val seen = mutableListOf<List<com.milan.game.services.AchievementStatus>>()
            val job = launch { vm.statuses.collect { seen += it } }
            advanceUntilIdle()
            val beforeWrite = seen.size
            assertTrue("订阅建立后派生流应有值", beforeWrite >= 1)
            assertTrue("初始不应有 owned 解锁", initial.none { it.unlocked })

            svc.saveData.ownedCharacters =
                listOf(com.milan.game.data.CharacterSaveState(characterId = svc.characters.first().characterId))
            assertEquals("签到应成功", WriteOutcome.Success, svc.signToday())

            advanceUntilIdle()
            job.cancel()

            assertTrue("写提交后派生流应收到重算发射", seen.size > beforeWrite)
            val post = vm.statuses.value
            assertTrue("拥有 1 角色后应有成就解锁", post.any { it.unlocked })
            assertEquals("重算后成就数量不变", initial.size, post.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `领取拒绝路径：提示一次且 busy 复位`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = AchievementViewModel(service())
            val toasts = mutableListOf<String>()
            val collector = launch { vm.toasts.collect { toasts += it } }

            assertFalse("初始不应 busy", vm.busy.value)
            vm.claim("__no_such_achievement__")
            advanceUntilIdle()
            collector.cancel()

            assertFalse("领取结束后 busy 应复位", vm.busy.value)
            assertEquals("应恰好一条提示", 1, toasts.size)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
