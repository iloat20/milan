package com.milan.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 编队/爬塔存档字段测试：sanitize 清洗、旧档缺字段向后兼容。 */
class SaveDataFormationTest {

    @Test
    fun sanitize_filtersNullEmpty_dedups_capsAtFive() {
        val d = SaveData(
            // M7：sanitize 要求编队成员必须已拥有，故先登记全部候选
            ownedCharacters = listOf("char_a", "char_b", "char_c", "char_d", "char_e", "char_f")
                .map { CharacterSaveState(characterId = it) },
            formation = listOf(
                null,
                "",
                "char_a",
                "char_a", // 重复：保留首条
                "char_b",
                "char_c",
                "char_d",
                "char_e",
                "char_f", // 超上限第 6 个有效 id：被裁剪
            ),
            towerBestFloor = -3,
        )
        d.sanitize()
        assertEquals(listOf("char_a", "char_b", "char_c", "char_d", "char_e"), d.getFormationIds())
        assertEquals(SaveData.MAX_FORMATION_SIZE, d.formation.size)
        assertEquals(0, d.towerBestFloor) // 负值钳制
    }

    /** M7（2026-08-28 审查回归）：编队里的未拥有角色必须被剔除（防幽灵成员）。 */
    @Test
    fun sanitize_dropsFormationMembersNotOwned() {
        val d = SaveData(
            ownedCharacters = listOf(CharacterSaveState(characterId = "char_a")),
            formation = listOf("char_a", "ghost_id", "char_b"),
        )
        d.sanitize()
        assertEquals(listOf("char_a"), d.getFormationIds())
    }

    /** M7（2026-08-28 审查回归）：数量为 0 的道具条目必须被清理（历史 BUG_REVIEW #10）。 */
    @Test
    fun sanitize_dropsZeroCountItems() {
        val d = SaveData(
            items = mutableListOf(
                ItemSaveState(itemId = "item_star_fragment", count = 5),
                ItemSaveState(itemId = "item_battle_ticket", count = 0), // 扣减到 0 的残留
                ItemSaveState(itemId = "item_star_fragment", count = 3), // 同 id 合并 → 8
            ),
        )
        d.sanitize()
        assertEquals(1, d.items.size)
        assertEquals(8, d.items.first()!!.count)
        assertEquals("item_star_fragment", d.items.first()!!.itemId)
    }

    @Test
    fun legacySaveWithoutNewFields_parsesWithDefaults() {
        // 旧档 JSON 无 Formation / TowerBestFloor 键：ignoreUnknownKeys + 默认值保证可读（存档兼容红线）。
        val d = SaveData.fromJson("""{"Version": 1, "SoftCurrency": 123}""")
        assertEquals(emptyList<String>(), d.getFormationIds())
        assertEquals(0, d.towerBestFloor)
        assertEquals(123, d.softCurrency)
    }

    @Test
    fun newFields_roundTripThroughJson() {
        val d = SaveData(
            // M7：编队成员需已拥有，否则会被 sanitize 剔除
            ownedCharacters = listOf(
                CharacterSaveState(characterId = "char_a"),
                CharacterSaveState(characterId = "char_b"),
            ),
            formation = listOf("char_a", null, "char_b"),
            towerBestFloor = 7,
        )
        val parsed = SaveData.fromJson(d.toJson())
        assertEquals(listOf("char_a", "char_b"), parsed.getFormationIds()) // sanitize 滤空槽
        assertEquals(7, parsed.towerBestFloor)
    }
}
