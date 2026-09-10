package com.milan.game.services

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveData
import com.milan.game.data.SaveProvider
import com.milan.game.data.TutorialSteps
import com.milan.game.infrastructure.eventbus.EventBus
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 新手引导服务层（第 8 节）：步骤自动勾选、跳过、旧档兼容。
 * 业务路径（pull / setFormation / levelUp / runTowerFloor）成功后应自动 markDone。
 */
class TutorialServiceTest {

    private val testContent = """
        {
          "Characters": [
            {
              "CharacterId": "char_a",
              "DisplayName": "测试甲",
              "BaseRarity": 3,
              "Element": "Flame",
              "BaseStats": [500, 100, 20000, 30]
            }
          ],
          "Pools": [
            {
              "PoolId": "pool_test",
              "RarityWeights": [0, 0, 1000, 0],
              "HardPity": 90,
              "SingleCost": 160,
              "TenCost": 1600,
              "Entries": [
                { "CharacterId": "char_a", "RarityIndex": 3, "Weight": 1000 }
              ]
            }
          ],
          "TalentTrees": [
            {
              "TreeId": "tree_dummy",
              "Nodes": [ { "NodeId": "n1", "Cost": 1 } ]
            }
          ]
        }
    """.trimIndent()

    private class FakeProvider : SaveProvider {
        var stored: String? = null
        val traces = mutableListOf<String>()
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

    @Before
    fun setUp() {
        EventBus.clear()
    }

    private fun withService(block: suspend (GameService) -> Unit) = runTest {
        val provider = FakeProvider()
        block(GameService(provider, testContent, provider.traces::add, Random(42)))
    }

    private fun GameService.own(vararg ids: Pair<String, Int>) {
        saveData.ownedCharacters = ids.map { (id, lv) ->
            CharacterSaveState(characterId = id, level = lv)
        }
    }

    @Test
    fun newSave_startsAtIntro() = withService { svc ->
        assertEquals(TutorialSteps.INTRO, svc.tutorialCurrentStep())
        assertFalse(svc.tutorialFinished())
    }

    @Test
    fun completeIntro_advancesToFirstPull() = withService { svc ->
        assertTrue(svc.completeTutorialStep(TutorialSteps.INTRO) is WriteOutcome.Success)
        assertEquals(TutorialSteps.FIRST_PULL, svc.tutorialCurrentStep())
        assertFalse(svc.tutorialFinished())
    }

    @Test
    fun pull_marksFirstPull() = withService { svc ->
        // 先过开场，否则 currentStep 仍停在 INTRO（ORDER 取首个未完成）
        assertTrue(svc.completeTutorialStep(TutorialSteps.INTRO) is WriteOutcome.Success)
        svc.grantHard(1600)
        assertTrue(svc.pull("pool_test", tenPull = true) is PullOutcome.Success)
        assertTrue(svc.saveData.tutorialData!!.hasDone(TutorialSteps.FIRST_PULL))
        assertEquals(TutorialSteps.FORM_TEAM, svc.tutorialCurrentStep())
    }

    @Test
    fun setFormation_marksFormTeam() = withService { svc ->
        svc.own("char_a" to 5)
        assertTrue(svc.setFormation(listOf("char_a")) is WriteOutcome.Success)
        assertTrue(svc.saveData.tutorialData!!.hasDone(TutorialSteps.FORM_TEAM))
    }

    @Test
    fun levelUp_marksFirstLevel() = withService { svc ->
        svc.own("char_a" to 1)
        svc.grantSoft(1_000_000)
        assertTrue(svc.levelUp("char_a", 1) is WriteOutcome.Success)
        assertTrue(svc.saveData.tutorialData!!.hasDone(TutorialSteps.FIRST_LEVEL))
    }

    @Test
    fun skip_blocksFurtherAndFinish() = withService { svc ->
        assertTrue(svc.skipTutorial() is WriteOutcome.Success)
        assertTrue(svc.tutorialFinished())
        assertNull(svc.tutorialCurrentStep())
        assertTrue(svc.completeTutorialStep(TutorialSteps.INTRO) is WriteOutcome.Success)
        assertFalse(svc.saveData.tutorialData!!.hasDone(TutorialSteps.INTRO))
    }

    @Test
    fun completeAllSteps_finishes() = withService { svc ->
        TutorialSteps.ORDER.forEach {
            assertTrue(svc.completeTutorialStep(it) is WriteOutcome.Success)
        }
        assertTrue(svc.tutorialFinished())
        assertNull(svc.tutorialCurrentStep())
    }

    @Test
    fun oldSave_withoutTutorialKey_defaultsToIntro() = withService { svc ->
        // 模拟旧档：先写入一份不含 TutorialData 的 JSON 再重新构造服务
        val provider = FakeProvider()
        val bare = SaveData()
        provider.stored = bare.toJson()
        val reloaded = GameService(provider, testContent, provider.traces::add, Random(1))
        assertEquals(TutorialSteps.INTRO, reloaded.tutorialCurrentStep())
    }
}
