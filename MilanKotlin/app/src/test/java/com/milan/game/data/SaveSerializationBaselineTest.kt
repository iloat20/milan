package com.milan.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 存档序列化基线（2026-09-08 P0-1）。
 *
 * 背景：每次写事务都走 [SaveData.toJson] **全量**序列化 + 原子写，成本与存档规模线性相关。
 * 2026-09-08 P0-1 把 [SaveData.json] 的 prettyPrint 从 true 改为 false，本测试固化收益并防回归：
 *   - 体积：紧凑格式必须显著小于格式化格式（否则说明 prettyPrint 被改回去了）；
 *   - 兼容：紧凑格式必须能被 [SaveData.tryParse] 正确解析（JSON 空白无语义，本应恒成立）；
 *   - 耗时：粗粒度上限，只用于拦截灾难性回归（如往 SaveData 塞入大字段），非精确性能门禁。
 *
 * 样本取「中后期存档」：31 个满级角色 + 50 条战绩 + 满编队，代表玩家真实规模上限。
 */
class SaveSerializationBaselineTest {

    /** 中后期存档样本：31 角色（满级/满星/带天赋与装备）+ 50 战绩 + 5 人编队。 */
    private fun matureSave(): SaveData = SaveData.createDefault().apply {
        ownedCharacters = (0 until 31).map { i ->
            CharacterSaveState(
                characterId = "char_$i",
                level = 80,
                stage = 5,
                stars = 5,
                totalExp = 1_200_000,
                unspentPoints = 12,
                talentPoints = listOf("t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8"),
                equipment = mapOf("weapon" to "w_$i", "armor" to "a_$i", "accessory" to "c_$i"),
            )
        }
        battleRecords = (0 until 50).map { i ->
            BattleRecord(enemyName = "enemy_$i", victory = i % 2 == 0)
        }
        formation = listOf("char_0", "char_1", "char_2", "char_3", "char_4")
        softCurrency = 999_999
        hardCurrency = 99_999
    }

    @Test
    fun compactFormatIsSignificantlySmallerThanPretty() {
        val d = matureSave()
        val compact = d.toJson().toByteArray(Charsets.UTF_8).size
        val pretty = d.toPrettyJson().toByteArray(Charsets.UTF_8).size

        println("[baseline] compact=$compact bytes, pretty=$pretty bytes, ratio=${compact.toDouble() / pretty}")

        // 紧凑格式至少比格式化小 20%（实测约 40–50%，阈值留余量防抖动）。
        assertTrue(
            "紧凑存档体积应显著小于格式化（compact=$compact, pretty=$pretty）",
            compact.toDouble() / pretty < 0.80,
        )
        // 同时确认样本确实具备代表规模，避免空存档让断言失去意义。
        assertTrue("样本存档应达到中后期规模（pretty=$pretty）", pretty > 8 * 1024)
    }

    @Test
    fun compactFormatRoundTrips() {
        val d = matureSave()
        val parsed = SaveData.fromJson(d.toJson())
        assertEquals("紧凑格式必须可无损解析", 31, parsed.ownedCharacters.size)
        assertEquals(50, parsed.battleRecords.size)
        assertEquals("char_0", parsed.ownedCharacters.first()?.characterId)
        assertEquals(999_999, parsed.softCurrency)
    }

    @Test
    fun serializationTimeWithinCoarseBudget() {
        val d = matureSave()
        repeat(5) { d.toJson() } // 预热

        val samples = (0 until 20).map {
            val t0 = System.nanoTime()
            d.toJson()
            System.nanoTime() - t0
        }.sorted()
        val medianMs = samples[samples.size / 2] / 1_000_000.0

        println("[baseline] serialization median=${"%.2f".format(medianMs)} ms, bytes=${d.toJson().length}")

        // 粗粒度护栏：中后期存档序列化中位数不应超过 100ms（实测个位数 ms）。
        // 目的是拦截「往 SaveData 塞入大字段」这类灾难性回归，非精确性能门禁。
        assertTrue(
            "序列化耗时中位数 ${"%.2f".format(medianMs)}ms 超过 100ms 预算",
            medianMs < 100.0,
        )
    }
}
