package com.milan.game.services

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.infrastructure.eventbus.EventBus
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 编队 + 无尽之塔服务层测试（2026-08 优化新增）：
 * setFormation 校验/事务回滚、runTowerFloor 胜负结算/奖励落盘/失败整体回滚。
 * 内容用双角色 JSON（BaseStats 显式给定，胜负由练度差锁死，不依赖随机种子）。
 */
class FormationTowerServiceTest {

    private val testContent = """
        {
          "Characters": [
            {
              "CharacterId": "char_a",
              "DisplayName": "测试甲",
              "BaseRarity": 3,
              "Element": "Flame",
              "BaseStats": [500, 100, 20000, 30]
            },
            {
              "CharacterId": "char_b",
              "DisplayName": "测试乙",
              "BaseRarity": 2,
              "Element": "Water",
              "BaseStats": [400, 90, 18000, 25]
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
                { "CharacterId": "char_a", "RarityIndex": 3, "Weight": 1000 },
                { "CharacterId": "char_b", "RarityIndex": 3, "Weight": 1000 }
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

    private class FakeProvider(var failSave: Boolean = false) : SaveProvider {
        var stored: String? = null
        val traces = mutableListOf<String>()
        override fun save(json: String): Boolean {
            if (failSave) return false
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

    private fun makeService(failSave: Boolean = false): GameService {
        val provider = FakeProvider(failSave)
        return GameService(provider, testContent, provider.traces::add, Random(42))
    }

    /** 直接在内存档中登记已拥有角色（绕过抽卡；编队/爬塔只依赖 owned 集合与内存态）。 */
    private fun GameService.own(vararg ids: Pair<String, Int>) {
        saveData.ownedCharacters = ids.map { (id, lv) ->
            CharacterSaveState(characterId = id, level = lv)
        }
    }

    private fun withService(block: suspend (GameService) -> Unit) = runTest {
        block(makeService())
    }

    // ── 编队 ──

    @Test
    fun setFormation_success_persistsAndSnapshots() = withService { svc ->
        svc.own("char_a" to 10, "char_b" to 10)
        val outcome = svc.setFormation(listOf("char_a", "char_b"))
        assertTrue(outcome is WriteOutcome.Success)
        assertEquals(listOf("char_a", "char_b"), svc.getFormation())
        assertEquals(listOf("char_a", "char_b"), svc.snapshot.value.formation)
    }

    @Test
    fun setFormation_rejectsUnownedAndOversize_dedupesInput() = withService { svc ->
        svc.own("char_a" to 1)
        assertTrue(svc.setFormation(listOf("ghost_id")) is WriteOutcome.Rejected)

        val six = (1..6).map { "char_a" } + "ghost" // 去重后 1 个有效 + 未拥有
        assertTrue(svc.setFormation(six) is WriteOutcome.Rejected)

        assertTrue(svc.setFormation(listOf("char_a", "char_a")) is WriteOutcome.Success)
        assertEquals(listOf("char_a"), svc.getFormation()) // 去重保序
    }

    @Test
    fun setFormation_saveFailed_rollsBack() = runTest {
        val svc = makeService(failSave = true)
        svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a"))
        val before = svc.getFormation()

        val outcome = svc.setFormation(listOf("char_a"))
        assertTrue(outcome is WriteOutcome.SaveFailed)
        assertEquals(before, svc.getFormation()) // 回滚：内存与存档一致
        assertEquals(before, svc.snapshot.value.formation)
    }

    // ── 无尽之塔 ──

    @Test
    fun tower_rejected_whenFormationEmpty() = withService { svc ->
        svc.own("char_a" to 80)
        assertTrue(svc.runTowerFloor(1) is TowerOutcome.Rejected)
        svc.setFormation(listOf("char_a"))
        assertTrue(svc.runTowerFloor(0) is TowerOutcome.Rejected) // 非法层号
    }

    @Test
    fun tower_highLevelTeam_winsFloorOne_rewardsPersist() = withService { svc ->
        svc.own("char_a" to 80, "char_b" to 80)
        svc.setFormation(listOf("char_a", "char_b")) // 双火？b 是 Water——混编无双星，纯数值碾压
        // 二期门票：入场 -1 + 胜利返 +1 = 净 0
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 1))

        val softBefore = svc.saveData.softCurrency
        val recordsBefore = svc.getBattleRecords().size

        val outcome = svc.runTowerFloor(1)
        assertTrue(outcome is TowerOutcome.Completed)
        val done = outcome as TowerOutcome.Completed
        assertTrue(done.victory)
        assertEquals(1, done.bestFloorAfter)
        assertEquals(1, svc.saveData.towerBestFloor)
        assertEquals(EconomyFormulas.towerRewardSoft(1), done.rewardSoft)
        assertEquals(softBefore + done.rewardSoft, svc.saveData.softCurrency)
        assertEquals(recordsBefore + 1, svc.getBattleRecords().size)
        assertEquals(1, svc.battleTickets())
    }

    @Test
    fun tower_lowLevelTeam_losesHighFloor_noReward() = withService { svc ->
        svc.own("char_b" to 1)
        svc.setFormation(listOf("char_b"))
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 2))

        val softBefore = svc.saveData.softCurrency
        val recordsBefore = svc.getBattleRecords().size

        val outcome = svc.runTowerFloor(50) // 缩放 8.35 倍的 5 人敌队：单人 1 级必败
        assertTrue(outcome is TowerOutcome.Completed)
        val done = outcome as TowerOutcome.Completed
        assertTrue(!done.victory)
        assertEquals(0, done.rewardSoft)
        assertEquals(0, svc.saveData.towerBestFloor)
        assertEquals(softBefore, svc.saveData.softCurrency)
        assertEquals(recordsBefore + 1, svc.getBattleRecords().size) // 失败也留战绩
        assertEquals(1, svc.battleTickets()) // 2 - 1 门票，失败无返还
    }

    @Test
    fun tower_saveFailed_fullRollback() = runTest {
        val svc = makeService(failSave = true)
        svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a", level = 80))
        svc.saveData.formation = listOf("char_a")
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 3))
        val softBefore = svc.saveData.softCurrency
        val recordsBefore = svc.getBattleRecords().size

        val outcome = svc.runTowerFloor(1)
        assertTrue(outcome is TowerOutcome.SaveFailed)
        assertEquals(softBefore, svc.saveData.softCurrency)
        assertEquals(0, svc.saveData.towerBestFloor)
        assertEquals(recordsBefore, svc.getBattleRecords().size)
        assertEquals(3, svc.battleTickets()) // 战票同样回滚
    }
}
