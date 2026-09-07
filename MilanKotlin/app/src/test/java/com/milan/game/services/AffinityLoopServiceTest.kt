package com.milan.game.services

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.infrastructure.eventbus.EventBus
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 好感度完整闭环回归测试（2026-09-02 按钮审查 P3 收口）。
 *
 * 覆盖此前「零接线」缺陷的三处修复：
 * 1. 赠送入口 [GameService.giftAffinity]（100 星尘 → +200，产品拍板，数值见 [AffinityFormulas]）；
 * 2. 战斗胜利产出（[TowerService.runTowerFloor] 出战全员 +20/场）；
 * 3. 剧情选择产出 [GameService.addCharacterAffinity]（DialogueScreen 接线）。
 *
 * 断言口径：**净变动**（连续调用断言累计），防单次调用看不出问题的假修复。
 * 内容复用双角色带 BaseStats JSON（胜负由练度差锁死，不依赖随机种子——与 FormationTowerServiceTest 同构）。
 */
class AffinityLoopServiceTest {

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

    private fun withService(block: suspend (GameService) -> Unit) = runTest {
        block(makeService())
    }

    /** 读好感度便捷断言。 */
    private fun GameService.affinity(id: String): Int = getCharacterAffinityData()[id] ?: 0

    // ── 赠送入口 ──

    @Test
    fun gift_success_netChange_deductsSoftAndAddsAffinity() = withService { svc ->
        svc.saveData.softCurrency = 1000

        assertEquals(WriteOutcome.Success, svc.giftAffinity("char_a"))
        assertEquals("扣 100 星尘", 900, svc.saveData.softCurrency)
        assertEquals("好感 +200", 200, svc.affinity("char_a"))

        // 连续赠送断言净变动（单次调用无法发现「漏扣/漏加」假修复）
        assertEquals(WriteOutcome.Success, svc.giftAffinity("char_a"))
        assertEquals(800, svc.saveData.softCurrency)
        assertEquals(400, svc.affinity("char_a"))

        // 快照随落盘刷新（AffinityScreen 依赖 revision 重组；softCurrency 口径一致）
        assertEquals(800, svc.snapshot.value.softCurrency)
    }

    @Test
    fun gift_insufficientSoft_rejected_noChange() = withService { svc ->
        svc.saveData.softCurrency = 99

        assertEquals(WriteOutcome.Rejected, svc.giftAffinity("char_a"))
        assertEquals("余额不动", 99, svc.saveData.softCurrency)
        assertEquals("好感不动", 0, svc.affinity("char_a"))
    }

    @Test
    fun gift_alreadyMaxLevel_rejected_noSoftSpent() = withService { svc ->
        svc.saveData.softCurrency = 1000
        svc.saveData.characterAffinityData = mapOf("char_a" to AffinityFormulas.MAX_AFFINITY)

        assertEquals(WriteOutcome.Rejected, svc.giftAffinity("char_a"))
        assertEquals("满级不扣钱", 1000, svc.saveData.softCurrency)
        assertEquals(AffinityFormulas.MAX_AFFINITY, svc.affinity("char_a"))
    }

    @Test
    fun gift_nearCap_clampsToMaxNotOverflow() = withService { svc ->
        svc.saveData.softCurrency = 1000
        // 9900 + 200 会越过 10000 → 必须钳到 MAX_AFFINITY，不得存 10100（超 10 级数据失真）
        svc.saveData.characterAffinityData = mapOf("char_a" to 9900)

        assertEquals(WriteOutcome.Success, svc.giftAffinity("char_a"))
        assertEquals(AffinityFormulas.MAX_AFFINITY, svc.affinity("char_a"))
        assertEquals(900, svc.saveData.softCurrency)
    }

    @Test
    fun gift_saveFailure_rollsBackSoftAndAffinity() = runTest {
        val svc = makeService(failSave = true)
        svc.saveData.softCurrency = 1000
        svc.saveData.characterAffinityData = mapOf("char_a" to 500)

        assertEquals(WriteOutcome.SaveFailed, svc.giftAffinity("char_a"))
        assertEquals("星尘整体回滚", 1000, svc.saveData.softCurrency)
        assertEquals("好感整体回滚", 500, svc.affinity("char_a"))
    }

    // ── 剧情选择产出（addCharacterAffinity）──

    @Test
    fun addCharacterAffinity_capsAtMax_thenRejects() = withService { svc ->
        assertEquals(WriteOutcome.Rejected, svc.grantAffinity("char_a", 0)) // 非正数拒绝
        assertEquals(WriteOutcome.Rejected, svc.grantAffinity("char_a", -10))

        assertEquals(WriteOutcome.Success, svc.grantAffinity("char_a", 200))
        assertEquals(200, svc.affinity("char_a"))

        // 越过上限 → 钳位不超发（9900+200=10100 → 10000）
        svc.saveData.characterAffinityData = mapOf("char_a" to 9900)
        assertEquals(WriteOutcome.Success, svc.grantAffinity("char_a", 200))
        assertEquals(AffinityFormulas.MAX_AFFINITY, svc.affinity("char_a"))

        // 已满级 → 明确 Rejected（无意义落盘不再发生）
        assertEquals(WriteOutcome.Rejected, svc.grantAffinity("char_a", 200))
        assertEquals(AffinityFormulas.MAX_AFFINITY, svc.affinity("char_a"))
    }

    @Test
    fun addCharacterAffinity_saveFailure_rollsBack() = runTest {
        val svc = makeService(failSave = true)
        svc.saveData.characterAffinityData = mapOf("char_a" to 500)

        assertEquals(WriteOutcome.SaveFailed, svc.grantAffinity("char_a", 200))
        assertEquals(500, svc.affinity("char_a"))
    }

    // ── 战斗胜利产出（runTowerFloor 出战全员 +20/场）──

    @Test
    fun tower_victory_grantsAffinityToAllFormationMembers() = withService { svc ->
        svc.saveData.ownedCharacters = listOf(
            CharacterSaveState(characterId = "char_a", level = 80),
            CharacterSaveState(characterId = "char_b", level = 80),
        )
        assertEquals(WriteOutcome.Success, svc.setFormation(listOf("char_a", "char_b")))
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 20))

        val first = svc.runTowerFloor(1)
        assertTrue("首层应获胜", first is TowerOutcome.Completed && first.victory)
        assertEquals("出战全员 +20", 20, svc.affinity("char_a"))
        assertEquals("出战全员 +20", 20, svc.affinity("char_b"))

        // 复刷已通层：星尘已按 F1 门控为 0，但好感按「每场胜利」照发——净变动断言
        val softAfterFirst = svc.saveData.softCurrency
        val replay = svc.runTowerFloor(1) as TowerOutcome.Completed
        assertTrue(replay.victory)
        assertEquals("复刷不再产星尘（F1 回归）", softAfterFirst, svc.saveData.softCurrency)
        assertEquals("复刷胜利好感照发", 40, svc.affinity("char_a"))
        assertEquals("复刷胜利好感照发", 40, svc.affinity("char_b"))
    }

    @Test
    fun tower_loss_grantsNoAffinity() = withService { svc ->
        svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_b", level = 1))
        assertEquals(WriteOutcome.Success, svc.setFormation(listOf("char_b")))
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 3))

        val outcome = svc.runTowerFloor(50) // 1 级单人 vs 8 倍缩放敌队：必败
        assertTrue(outcome is TowerOutcome.Completed)
        assertTrue("应失败", !(outcome as TowerOutcome.Completed).victory)
        assertEquals("失败不发好感", 0, svc.affinity("char_b"))
    }

    @Test
    fun tower_victory_affinityCapsAtMax() = withService { svc ->
        svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a", level = 80))
        assertEquals(WriteOutcome.Success, svc.setFormation(listOf("char_a")))
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 20))
        svc.saveData.characterAffinityData = mapOf("char_a" to (AffinityFormulas.MAX_AFFINITY - 10))

        val first = svc.runTowerFloor(1) as TowerOutcome.Completed
        assertTrue(first.victory)
        assertEquals("好感钳到满级，不超 10000", AffinityFormulas.MAX_AFFINITY, svc.affinity("char_a"))
    }

    @Test
    fun tower_saveFailure_rollsBackAffinity() = runTest {
        val svc = makeService(failSave = true)
        svc.saveData.ownedCharacters = listOf(CharacterSaveState(characterId = "char_a", level = 80))
        svc.saveData.formation = listOf("char_a")
        svc.saveData.items = listOf(ItemSaveState(itemId = GameService.BattleTicketItemId, count = 3))
        svc.saveData.characterAffinityData = mapOf("char_a" to 500)

        assertEquals(TowerOutcome.SaveFailed, svc.runTowerFloor(1))
        assertEquals("好感随事务回滚", 500, svc.affinity("char_a"))
        assertEquals("门票随事务回滚", 3, svc.battleTickets())
    }
}
