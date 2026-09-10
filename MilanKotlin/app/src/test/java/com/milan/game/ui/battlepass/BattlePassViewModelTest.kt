package com.milan.game.ui.battlepass

import com.milan.game.data.SaveProvider
import com.milan.game.services.GameService
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
 * 纪行页 ViewModel 单测（2026-09-08 P1-6 B 批）。
 *
 * 覆盖：派生状态初值（奖励定义非空 + 与 Monetization 数据一致）、未达等级领取与
 * 钻石不足购买的拒绝路径（提示一次 + busy 复位）。
 */
class BattlePassViewModelTest {

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

    @Test
    fun `初始状态派生纪行数据且奖励非空`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = BattlePassViewModel(svc)
            val ui = vm.uiState.value
            assertTrue("奖励轨不应为空", ui.rewards.isNotEmpty())
            assertEquals(
                "等级应与 Monetization 数据一致",
                svc.getMonetizationData().battlePassLevel,
                ui.level,
            )
            assertEquals(
                "已领取列表应与 Monetization 数据一致",
                svc.getMonetizationData().claimedBPRewards,
                ui.claimedLevels,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `拒绝路径：未达等级领取与钻石不足购买均提示一次且 busy 复位`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = BattlePassViewModel(service())
            val toasts = mutableListOf<String>()
            val collector = launch { vm.toasts.collect { toasts += it } }

            assertFalse("初始不应 busy", vm.busy.value)
            vm.claimReward(Int.MAX_VALUE) // 等级必然不足
            advanceUntilIdle()
            assertFalse("领取结束后 busy 应复位", vm.busy.value)

            vm.purchasePremium() // 默认钻石 0 < 680 → 拒绝
            advanceUntilIdle()
            collector.cancel()

            assertFalse("购买结束后 busy 应复位", vm.busy.value)
            assertEquals("两次拒绝各应恰一条提示", 2, toasts.size)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
