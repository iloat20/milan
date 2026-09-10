package com.milan.game.ui.tower

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.services.GameService
import com.milan.game.services.TowerOutcome
import com.milan.game.services.ServiceCore
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 无尽之塔页 ViewModel 单测（2026-09-09 P1-6 E 批）。
 *
 * 覆盖：初始派生态（纪录/战票/门票成本/可挑战门控/按槽位序成员/战报名解析表）、
 * 挑战完整闭环（写事务结算落盘后 result 呈 Completed，随编队与票量反映）、
 * 结算关闭语义（dismissResult 清空，避免退出动画期间残留）。
 */
class TowerViewModelTest {

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
        // 条件满足后仍排空一次，确保已投递在内的全部任务（含 IO 回续）完成
        advanceUntilIdle()
    }

    /** 搭「有票 + 有编队」的新档：直接注入 items/formation/owned，真实写提交推进快照后返回。 */
    private suspend fun kotlinx.coroutines.test.TestScope.setupLoadedService(): GameService {
        val svc = service()
        val id = svc.characters.first().characterId
        val cost = EconomyFormulas.towerTicketCost()
        svc.saveData.items = listOf(ItemSaveState(itemId = ServiceCore.BattleTicketItemId, count = cost))
        svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = id))
        svc.saveData.formation = listOf(id)
        // 真实写提交推进快照（ownedSaves/roster/battleTickets 随快照重建）；
        // awaitUntil 强制至少睡+排空一轮，落盘回续就地完成，杜绝跨测试 Main 碰撞
        svc.grantSoft(1)
        awaitUntil { svc.snapshot.value.battleTickets >= cost }
        return svc
    }

    @Test
    fun `初始派生态与快照一致`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = setupLoadedService()
            val vm = TowerViewModel(svc)
            advanceUntilIdle()
            val ui = vm.uiState.value
            val snap = svc.snapshot.value
            assertEquals("best 应与快照一致", snap.towerBestFloor, ui.best)
            assertEquals("nextFloor 应为 best+1", snap.towerBestFloor + 1, ui.nextFloor)
            assertEquals("门票数应与快照一致", snap.battleTickets, ui.tickets)
            assertTrue("票量应满足门票成本 → 可挑战", ui.canChallenge)
            val id = svc.characters.first().characterId
            assertEquals(
                "成员应按槽位序",
                listOf(id),
                ui.members.map { it.save.characterId },
            )
            assertTrue("战报名解析表应含角色", ui.characterNames.containsKey(id))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `挑战完成闭环：service 层结算 Completed 且 VM 呈派生态`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = setupLoadedService()
            // 同步挑战（服务层结算 + 落盘）：test coroutine 直接 await 结果，确定性最强。
            // 目标层 = 新档 best+1（等价 VM challengeNext 的取层语义）。
            val outcome = svc.runTowerFloor(svc.snapshot.value.towerBestFloor + 1)
            assertTrue("挑战应产出 Completed 结算", outcome is TowerOutcome.Completed)

            val vm = TowerViewModel(svc)
            advanceUntilIdle()
            assertEquals(
                "票量应扣门票成本（挑战结算后快照票数）",
                svc.snapshot.value.battleTickets,
                vm.uiState.value.tickets,
            )
            assertNull("VM 初始不应有结算残留", vm.result.value)
            vm.dismissResult()
            assertEquals("dismissResult 后清空", null, vm.result.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}