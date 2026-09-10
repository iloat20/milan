package com.milan.game.ui.deck

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
 * 卡组页 ViewModel 单测（2026-09-09 P1-6 E 批）。
 *
 * 覆盖：初始派生态（拥有/编队成员/编队 id 集随快照一致）、服务层 toggleFormation
 * 写提交后派生态随快照刷新（读-改-写事务：快照推进后 formation 即时反映）。
 */
class DeckViewModelTest {

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
    fun `初始派生态与存档一致`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val a = svc.characters[0].characterId
            val b = svc.characters[1].characterId
            // D 批同款范式：先改内存存档，再真实写提交推进快照（roster/ownedSaves 随快照重建）
            svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = a))
            svc.saveData.formation = listOf(a, b)
            svc.grantSoft(1)
            advanceUntilIdle()

            // VM 构造读最新快照；随后 init 订阅快照持续刷新派生状态
            val vm = DeckViewModel(svc)
            advanceUntilIdle()
            assertTrue(
                "成员应只含已拥有且在编队的角色（stranger 被过滤）",
                vm.uiState.value.members.map { it.save.characterId } == listOf(a),
            )
            assertEquals(
                "formation 应为原始编队 id 集",
                listOf(a, b),
                vm.uiState.value.formation,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `toggleFormation后派生态随快照刷新`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val a = svc.characters[0].characterId
            val b = svc.characters[1].characterId
            svc.saveData.ownedCharacters = listOf(
                CharacterSaveState(characterId = a),
                CharacterSaveState(characterId = b),
            )
            svc.saveData.formation = listOf(a, b)
            svc.grantSoft(1)
            advanceUntilIdle()
            assertEquals("初始 formation 应为 [a,b]", listOf(a, b), svc.roster.value.formation)

            // 服务层 toggleFormation 是读-改-写事务：同步 await 返回值，快照/切片立即更新。
            // VM 异步版行为等价（viewModelScope.launch），本测试聚焦断言真实写入结果。
            val outcome = svc.toggleFormation(a)
            advanceUntilIdle()
            assertEquals("入队切换应成功", com.milan.game.services.WriteOutcome.Success, outcome)
            assertEquals(
                "编队已含 a → 切换后应为 [b]",
                listOf(b),
                svc.roster.value.formation,
            )

            // VM 派生态随快照同步（构造时读最新快照；切换后重建验证）
            val vm = DeckViewModel(svc)
            advanceUntilIdle()
            assertEquals(
                "VM 成员应为当前编队",
                listOf(b),
                vm.uiState.value.members.map { it.save.characterId },
            )
            assertEquals("VM 编队 id 集应为当前编队", listOf(b), vm.uiState.value.formation)
        } finally {
            Dispatchers.resetMain()
        }
    }
}