package com.milan.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C# SaveDataTests 的 Kotlin 翻译（kotlinx.serialization 契约验证）。 */
class SaveDataTest {

    @Test
    fun roundTrip_battleRecordFieldsSurvive() {
        // C# 全部模型都是公共字段，必须靠 IncludeFields=true 才写得出/读得进；
        // Kotlin 版由 @SerialName 对齐 PascalCase 键名，保证与旧档双向兼容。
        val data = SaveData().apply {
            softCurrency = 1234
            battleRecords = listOf(
                BattleRecord(
                    enemyName = "炎魔",
                    enemyElement = "Flame",
                    victory = true,
                    turns = 3,
                    remainingHp = 777,
                    teamPower = 650,
                    timestamp = 1700000000000L,
                ),
            )
        }

        val json = data.toJson()
        val back = SaveData.fromJson(json)

        assertEquals(1234, back.softCurrency)
        assertEquals(1, back.battleRecords.size)
        val rec = requireNotNull(back.battleRecords[0])
        assertEquals("炎魔", rec.enemyName)
        assertEquals("Flame", rec.enemyElement)
        assertTrue(rec.victory)
        assertEquals(3, rec.turns)
        assertEquals(777, rec.remainingHp)
        assertEquals(650, rec.teamPower)
        assertEquals(1700000000000L, rec.timestamp)
    }

    @Test
    fun fromJson_nullCollection_sanitizedToEmpty() {
        // JSON 中显式 null 会覆盖默认值；coerceInputValues 兜底为空列表，否则下游 NRE。
        val json = """{"OwnedCharacters":null,"BattleRecords":null}"""
        val d = SaveData.fromJson(json)
        assertNotNull(d.ownedCharacters)
        assertNotNull(d.battleRecords)
        assertTrue(d.ownedCharacters.isEmpty())
        assertTrue(d.battleRecords.isEmpty())
    }

    @Test
    fun fromJson_nullElementInBattleRecords_removed() {
        val json = """{"BattleRecords":[null]}"""
        val d = SaveData.fromJson(json)
        assertNotNull(d.battleRecords)
        assertTrue(d.battleRecords.isEmpty())
    }

    @Test
    fun fromJson_overCapBattleRecords_trimmedTo50() {
        // P3-7：旧档可能携带 >50 条战绩（recordBattle 只裁剪新写入），载入时按
        // MAX_BATTLE_RECORDS 丢弃最旧，避免超量条目永久残留。
        val many = (0 until 60).joinToString(",") {
            """{"EnemyName":"enemy$it","Victory":true}"""
        }
        val d = SaveData.fromJson("""{"BattleRecords":[$many]}""")
        assertEquals(SaveData.MAX_BATTLE_RECORDS, d.battleRecords.size)
        // 最旧 10 条被丢弃，保留最近 50 条（enemy10..enemy59）
        assertEquals("enemy10", requireNotNull(d.battleRecords.first()).enemyName)
        assertEquals("enemy59", requireNotNull(d.battleRecords.last()).enemyName)
    }

    @Test
    fun fromJson_garbage_fallsBackToDefault() {
        val d = SaveData.fromJson("这不是 json{{{")
        assertNotNull(d)
        assertNotNull(d.ownedCharacters)
        assertNotNull(d.battleRecords)
        assertEquals(1, d.version)
    }

    @Test
    fun fromJson_duplicateCharacterIds_keepFirst() {
        // C# Sanitize：同 CharacterId 出现多份会让「是否已拥有」判定与列表渲染分叉，保留首条。
        val json = """
            {"OwnedCharacters":[
                {"CharacterId":"c1","Level":5},
                {"CharacterId":"c1","Level":9}
            ]}
        """.trimIndent()
        val d = SaveData.fromJson(json)
        assertEquals(1, d.ownedCharacters.size)
        assertEquals(5, requireNotNull(d.ownedCharacters[0]).level)
    }

    @Test
    fun fromJson_duplicateItems_merged() {
        val json = """
            {"Items":[
                {"ItemId":"star_fragment","Count":3},
                {"ItemId":"star_fragment","Count":4}
            ]}
        """.trimIndent()
        val d = SaveData.fromJson(json)
        assertEquals(1, d.items.size)
        assertEquals(7, requireNotNull(d.items[0]).count)
    }

    @Test
    fun fromJson_negativeValues_clamped() {
        val json = """
            {"SoftCurrency":-500,"OwnedCharacters":[{"CharacterId":"c1","Level":0,"Stage":0,"Stars":0}]}
        """.trimIndent()
        val d = SaveData.fromJson(json)
        assertEquals(0, d.softCurrency)
        val c = requireNotNull(d.ownedCharacters[0])
        assertEquals(1, c.level)
        assertEquals(1, c.stage)
        assertEquals(1, c.stars)
    }

    @Test
    fun gachaCounter_getSetRoundTrip() {
        val d = SaveData.createDefault()
        assertEquals(0, d.getGachaCounter("pool_main"))
        d.setGachaCounter("pool_main", 42)
        assertEquals(42, d.getGachaCounter("pool_main"))
        val back = SaveData.fromJson(d.toJson())
        assertEquals(42, back.getGachaCounter("pool_main"))
    }
}
