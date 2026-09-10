package com.milan.game.ui.arena

import com.milan.game.data.SaveProvider
import com.milan.game.services.GameService
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
 * 竞技场页 ViewModel 单测（2026-09-09 P1-6 D 批）。
 *
 * 覆盖：派生状态初值（段位/积分/剩余次数/战绩与 service 只读 API 一致）、
 * 未编队的拒绝路径（提示一次 + busy 复位）。
 *
 * ⚠️ 时序：真实写提交的落盘经真实 `Dispatchers.IO`，testScheduler 虚拟时间不覆盖 IO ——
 * 写路径断言用 [awaitUntil] 真实时间轮询兜底（C 批实测经验，见 P1-6 计划文档）。
 */
class ArenaViewModelTest {

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
    fun `初始状态派生段位积分剩余次数与对手`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = ArenaViewModel(svc)
            advanceUntilIdle()
            val ui = vm.uiState.value
            val arenaData = svc.getArenaData()
            assertEquals("积分应与存档一致", arenaData.arenaPoints, ui.points)
            assertEquals("新档剩余次数应为每日上限", 5, ui.attacksLeft)
            assertEquals("新档胜场应为 0", 0, ui.winCount)
            assertEquals("新档负场应为 0", 0, ui.loseCount)
            assertTrue("段位称号不应为空", ui.rankTitle.isNotEmpty())
            // getOpponents() 每次调用经 generateSimulatedOpponents 随机生成（名字/队伍/积分
            // 随机）→ 两次调用结果必然不同，不可逐项比较；改结构性断言。
            assertTrue("对手列表不应为空", ui.opponents.isNotEmpty())
            assertTrue(
                "对手应具备有效字段",
                ui.opponents.all {
                    it.characterId.isNotBlank() && it.name.isNotBlank() && it.level > 0 &&
                        it.teamPower > 0 && it.defenseTeam.isNotEmpty()
                },
            )
            assertEquals("赛季奖励应与只读 API 一致", svc.getSeasonRewards(), ui.seasonRewards)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `拒绝路径：未编队挑战提示一次且 busy 复位`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = ArenaViewModel(svc)
            advanceUntilIdle()
            val toasts = mutableListOf<String>()
            val collector = launch { vm.toasts.collect { toasts += it } }

            assertFalse("初始不应 busy", vm.busy.value)
            val opponent = svc.getOpponents().firstOrNull() ?: error("内容需至少一名对手")
            vm.challenge(opponent) // 新档未编队 → 拒绝
            awaitUntil { toasts.isNotEmpty() }

            assertFalse("结束后 busy 应复位", vm.busy.value)
            collector.cancel()
            assertTrue(
                "应提示挑战被拒",
                toasts.any { it.contains("挑战次数不足") || it.contains("未编队") },
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}
