package com.milan.game.services

import com.milan.game.data.EquipmentSaveState
import com.milan.game.data.SaveProvider
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C3 装备发放路径回归（2026-09-09）。
 *
 * 此前 `generateEquipment` 等价物只返回实例、从不写入 `ownedEquipments`，
 * 强化/穿脱因 `firstOrNull` 恒空而全部 Rejected。本测试锁住「唯一入库路径」语义。
 */
class EquipmentGrantWiringTest {

    private class FakeProvider(var failSave: Boolean = false) : SaveProvider {
        var stored: String? = null
        override fun save(json: String): Boolean {
            if (failSave) return false
            stored = json
            return true
        }
        override fun load(): String = stored ?: ""
        override fun delete() { stored = null }
        override fun exists(): Boolean = stored != null
        override fun loadBackup(): String? = null
    }

    private val testContent = """
        {
          "Characters": [
            { "CharacterId": "char_a", "DisplayName": "测试甲", "BaseRarity": 3, "TalentTreeId": "tree_test" }
          ],
          "Pools": [
            {
              "PoolId": "pool_test", "RarityWeights": [0, 0, 1000, 0], "HardPity": 90,
              "SingleCost": 160, "TenCost": 1600,
              "Entries": [ { "CharacterId": "char_a", "RarityIndex": 3, "Weight": 1000 } ]
            }
          ],
          "TalentTrees": [
            { "TreeId": "tree_test", "Nodes": [ { "NodeId": "t1", "Cost": 1 } ] }
          ]
        }
    """.trimIndent()

    private fun makeService(provider: FakeProvider = FakeProvider(), soft: Int = 100_000): GameService =
        GameService(provider, testContent, { }, Random(42)).also {
            it.saveData.softCurrency = soft
            // 给角色一份存档，便于穿脱测试
            it.saveData.ownedCharacters = listOf(
                com.milan.game.data.CharacterSaveState(characterId = "char_a"),
            )
        }

    @Test
    fun `grantEquipment 入库后可被读到且模板有效`() = runTest {
        val service = makeService()
        val templates = service.equipmentTemplates
        assertTrue("兜底模板应非空", templates.isNotEmpty())
        val templateId = templates.first().equipmentId

        assertEquals(0, service.getUnequippedEquipments().size)
        val outcome = service.grantEquipment(templateId, level = 1)
        assertTrue("发放应成功", outcome == WriteOutcome.Success)
        assertEquals("背包应净增 1 件", 1, service.getUnequippedEquipments().size)

        val owned = service.getUnequippedEquipments().first()
        assertEquals(templateId, owned.templateId)
        assertTrue("主词条必须有效", owned.mainStat.statType.isNotBlank())
        assertTrue("副词条条数 ≥1", owned.subStats.isNotEmpty())
    }

    @Test
    fun `未知模板拒绝且不入库`() = runTest {
        val service = makeService()
        val outcome = service.grantEquipment("eq_not_exist", level = 1)
        assertTrue(outcome == WriteOutcome.Rejected)
        assertEquals(0, service.saveData.ownedEquipments.size)
    }

    @Test
    fun `发放后可穿戴与强化`() = runTest {
        val service = makeService()
        val template = service.equipmentTemplates.first { it.type == "weapon" }
        assertTrue(service.grantEquipment(template.equipmentId) == WriteOutcome.Success)
        val equip = service.getUnequippedEquipments().first()

        val wear = service.equipItem("char_a", equip.equipmentId, EquipmentSaveState.SLOT_WEAPON)
        assertTrue("穿戴应成功", wear == WriteOutcome.Success)
        assertEquals(1, service.getEquipped("char_a").size)
        assertEquals(0, service.getUnequippedEquipments().size)

        val enhance = service.enhanceEquipment(equip.equipmentId, times = 1)
        assertTrue("强化应成功", enhance is EquipmentEnhanceOutcome.Success)
    }

    @Test
    fun `连续发放净增背包且 id 不碰撞`() = runTest {
        val service = makeService()
        val t = service.equipmentTemplates.first()
        service.grantEquipment(t.equipmentId)
        service.grantEquipment(t.equipmentId)
        service.grantEquipment(t.equipmentId)
        val list = service.saveData.ownedEquipments
        assertEquals(3, list.size)
        assertEquals("实例 id 必须唯一", 3, list.map { it?.equipmentId }.toSet().size)
    }
}
