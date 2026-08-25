package com.milan.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 编队/爬塔存档字段测试：sanitize 清洗、旧档缺字段向后兼容。 */
class SaveDataFormationTest {

    @Test
    fun sanitize_filtersNullEmpty_dedups_capsAtFive() {
        val d = SaveData(
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
        val d = SaveData(formation = listOf("char_a", null, "char_b"), towerBestFloor = 7)
        val parsed = SaveData.fromJson(d.toJson())
        assertEquals(listOf("char_a", "char_b"), parsed.getFormationIds()) // sanitize 滤空槽
        assertEquals(7, parsed.towerBestFloor)
    }
}
