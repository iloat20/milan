package com.milan.game.ui.missions

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
 * 每日任务页 ViewModel 单测（2026-09-08 P1-6 C 批）。
 *
 * 覆盖：派生状态初值（任务列表/宝箱与 service 只读 API 一致）、未解锁宝箱的
 * 门控提示（不触发写事务）、活跃度达标后的领取成功路径（宝箱入账 + busy 复位）。
 *
 * ⚠️ 时序：真实写提交的落盘经 `Dispatchers.IO`（ServiceCore.writeDispatcher），
 * testScheduler 虚拟时间不覆盖 IO —— 写路径断言用 [awaitUntil] 真实时间轮询兜底。
 */
class DailyMissionViewModelTest {

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
    fun `初始状态派生今日任务与宝箱`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = DailyMissionViewModel(svc)
            // init：跨日重置（真实 IO 落盘）→ collect 重建。等重置落地（任务非空）后再比较，
            // 避免两侧同为空表时提前收敛（新档 dailyMissionData=null → 任务表为空）。
            awaitUntil { vm.uiState.value.missions.isNotEmpty() }
            advanceUntilIdle()
            val ui = vm.uiState.value
            assertEquals(
                "任务列表应与只读 API 一致",
                svc.getTodayMissions(),
                ui.missions,
            )
            assertEquals(
                "宝箱状态应与只读 API 一致",
                svc.getChestStatuses(),
                ui.chestStatuses,
            )
            assertEquals(
                "活跃度应与存档数据一致",
                svc.saveData.dailyMissionData?.activityPoints ?: 0,
                ui.activityPoints,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `门控路径：未解锁宝箱仅提示不触发写`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = DailyMissionViewModel(svc)
            awaitUntil { vm.uiState.value.missions.isNotEmpty() } // 等跨日重置落地
            advanceUntilIdle()
            val toasts = mutableListOf<String>()
            val collector = launch { vm.toasts.collect { toasts += it } }

            // 重置后新档活跃度 0 → 任意里程碑都未解锁
            vm.claimChest(100)
            advanceUntilIdle()
            collector.cancel()

            assertEquals(
                listOf("活跃度达到 100 点可领取"),
                toasts,
            )
            assertFalse(vm.busy.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `成功路径：活跃度达标后可领取宝箱且 busy 复位`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = DailyMissionViewModel(svc)
            // 先等 init 的跨日重置落地（新档 day 序号不匹配会触发重置并清零活跃度），
            // 之后再直接改内存存档把活跃度推到 20（首个里程碑）。
            awaitUntil { vm.uiState.value.missions.isNotEmpty() }
            advanceUntilIdle()
            svc.saveData.dailyMissionData?.activityPoints = 20
            assertTrue(
                "前置：宝箱 20 应已解锁",
                svc.getChestStatuses().first { it.milestone == 20 }.unlocked,
            )

            val toasts = mutableListOf<String>()
            val collector = launch { vm.toasts.collect { toasts += it } }
            vm.claimChest(20)
            awaitUntil { toasts.isNotEmpty() }
            advanceUntilIdle()
            collector.cancel()

            assertEquals(listOf("已领取 20 点活跃度宝箱"), toasts)
            assertFalse("领取结束后 busy 应复位", vm.busy.value)
            assertTrue(
                "领取后活跃度宝箱应标记已领",
                svc.getDailyMissionData().claimedChests.contains(20),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}
