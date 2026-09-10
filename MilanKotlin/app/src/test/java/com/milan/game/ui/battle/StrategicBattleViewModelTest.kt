package com.milan.game.ui.battle

import com.milan.game.data.SaveProvider
import com.milan.game.domain.battle.BattlePhase
import com.milan.game.services.GameService
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StrategicBattleViewModelTest {

    private class MemoryProvider : SaveProvider {
        var stored: String? = null
        override fun save(json: String): Boolean {
            stored = json
            return true
        }
        override fun load(): String = stored ?: ""
        override fun delete() { stored = null }
        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = null
    }

    private val content = """
        {
          "Characters": [
            { "CharacterId": "char_a", "DisplayName": "甲", "BaseRarity": 3, "TalentTreeId": "t" },
            { "CharacterId": "char_b", "DisplayName": "乙", "BaseRarity": 2, "TalentTreeId": "t" }
          ],
          "Pools": [
            { "PoolId": "p", "RarityWeights": [0,0,1000,0], "HardPity": 90,
              "SingleCost": 160, "TenCost": 1600,
              "Entries": [{ "CharacterId": "char_a", "RarityIndex": 3, "Weight": 1000 }] }
          ],
          "TalentTrees": [{ "TreeId": "t", "Nodes": [{ "NodeId": "n1", "Cost": 1 }] }]
        }
    """.trimIndent()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeService(): GameService {
        val s = GameService(MemoryProvider(), content, { }, Random(1))
        s.saveData.ownedCharacters = listOf(
            com.milan.game.data.CharacterSaveState(characterId = "char_a"),
            com.milan.game.data.CharacterSaveState(characterId = "char_b"),
        )
        return s
    }

    @Test
    fun `start 后进入玩家输入且双方有单位`() = runTest(dispatcher) {
        val service = makeService()
        service.saveData.formation = listOf("char_a", "char_b")
        val vm = StrategicBattleViewModel(service)
        vm.start(1)
        val ui = vm.ui.value
        assertNotNull(ui.state)
        assertEquals(BattlePhase.PLAYER_INPUT, ui.state!!.phase)
        assertTrue(ui.state!!.playerTeam.isNotEmpty())
        assertTrue(ui.state!!.enemyTeam.isNotEmpty())
    }

    @Test
    fun `选普攻并确认后敌方有掉血或战斗推进`() = runTest(dispatcher) {
        val service = makeService()
        service.saveData.formation = listOf("char_a", "char_b")
        val vm = StrategicBattleViewModel(service)
        vm.start(1)
        val before = vm.ui.value.state!!.enemyTeam.map { it.hp }
        vm.selectSkill("normal_attack")
        // 单体默认已预选目标
        vm.confirm()
        val after = vm.ui.value.state!!.enemyTeam.map { it.hp }
        val progressed = after.zip(before).any { (a, b) -> a < b } ||
            vm.ui.value.currentActor != 0 ||
            vm.ui.value.logLines.size > 1
        assertTrue("行动后应有掉血或推进", progressed)
    }
}
