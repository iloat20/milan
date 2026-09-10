package com.milan.game.ui.shop

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
 * 商店页 ViewModel 单测（2026-09-08 P1-6 C 批）。
 *
 * 覆盖：派生状态初值（经济四资源与 service.economy 一致 + 每日特惠非空）、
 * 真实写提交后派生状态随快照刷新、余额/库存不足的拒绝路径（提示一次 + busy 复位）。
 *
 * ⚠️ 时序：真实写提交的落盘经 `Dispatchers.IO`（ServiceCore.writeDispatcher），
 * testScheduler 虚拟时间不覆盖 IO —— 成功/写路径断言用 [awaitUntil] 真实时间轮询兜底；
 * 拒绝路径若在事务前置出（不落盘），advanceUntilIdle 即确定。
 */
class ShopViewModelTest {

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
    fun `初始状态派生经济切片与每日特惠`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = ShopViewModel(svc)
            val ui = vm.uiState.value
            val eco = svc.economy.value
            assertEquals("星尘应与经济切片一致", eco.softCurrency, ui.softCurrency)
            assertEquals("钻石应与经济切片一致", eco.hardCurrency, ui.hardCurrency)
            assertEquals("碎片应与经济切片一致", eco.starFragments, ui.starFragments)
            assertEquals("战票应与经济切片一致", eco.battleTickets, ui.battleTickets)
            assertTrue("每日特惠不应为空", ui.dailyOffers.isNotEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `真实写提交后派生状态随快照刷新`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val vm = ShopViewModel(svc)
            advanceUntilIdle()
            // 新档自带初始星尘（GameContent 初始档发放，非 0），以构造态为基线做增量断言。
            val baseline = vm.uiState.value.softCurrency

            // 真实写提交（事务 + 落盘，推进 revision）→ VM 派生状态应刷新
            svc.grantSoft(10_000)
            awaitUntil { vm.uiState.value.softCurrency == baseline + 10_000 }
            assertEquals(
                "发放星尘后 VM 派生应同步",
                baseline + 10_000,
                vm.uiState.value.softCurrency,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `拒绝路径：无碎片兑换提示一次且 busy 复位`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = ShopViewModel(service())
            val toasts = mutableListOf<String>()
            val collector = launch { vm.toasts.collect { toasts += it } }

            assertFalse("初始不应 busy", vm.busy.value)
            vm.exchangeFragmentsForSoft() // 默认碎片 0 → 拒绝
            awaitUntil { toasts.isNotEmpty() }
            advanceUntilIdle()

            assertFalse("结束后 busy 应复位", vm.busy.value)
            collector.cancel()
            assertTrue("应提示碎片不足", toasts.single().contains("碎片不足"))
        } finally {
            Dispatchers.resetMain()
        }
    }
}
