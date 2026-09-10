package com.milan.game.ui.affinity

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.services.AffinityFormulas
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 好感度页 ViewModel 单测（2026-09-08 P1-6 B 批）。
 *
 * 覆盖：列表只含已拥有角色（owned 过滤下沉 VM）、星尘不足赠送拒绝提示不吞异常、
 * 充值星尘后赠送成功走成功提示。
 */
class AffinityViewModelTest {

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
    fun `列表只含已拥有角色`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val emptyVm = AffinityViewModel(service())
            assertTrue("无拥有角色时列表应为空", emptyVm.uiState.value.rows.isEmpty())

            val svc = service()
            val ownedId = svc.characters.first().characterId
            svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = ownedId))
            val vm = AffinityViewModel(svc)
            val rows = vm.uiState.value.rows
            assertEquals("列表应只含该拥有角色", listOf(ownedId), rows.map { it.characterId })
            assertEquals("初始好感应为 0", 0, rows.single().affinity)
            assertTrue("显示名应来自内容定义", rows.single().displayName.isNotBlank())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `赠送：星尘不足走拒绝提示 充足以后走成功提示`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val svc = service()
            val ownedId = svc.characters.first().characterId
            svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = ownedId))
            val vm = AffinityViewModel(svc)
            val toasts = mutableListOf<String>()
            val collector = launch { vm.toasts.collect { toasts += it } }

            // 星尘不足（置 0）→ 拒绝
            svc.saveData.softCurrency = 0
            vm.gift(ownedId)
            repeat(200) {
                advanceUntilIdle()
                if (toasts.size >= 1) return@repeat
                Thread.sleep(10)
            }
            assertEquals("星尘不足应一条拒绝提示", 1, toasts.size)

            // 充值到够 → 成功（好感未满）。成功路径内部 withContext(IO) 落盘，
            // 需等真实 IO 完成后调度器才推进续体 → 轮询等待再 advance。
            svc.saveData.softCurrency = AffinityFormulas.GIFT_COST_SOFT + 1000
            vm.gift(ownedId)
            repeat(200) {
                advanceUntilIdle()
                if (toasts.size >= 2) return@repeat
                Thread.sleep(10)
            }
            collector.cancel()
            assertEquals("充值后应成功提示", 2, toasts.size)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
