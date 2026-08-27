package com.milan.game.services

import com.milan.game.data.SaveProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * data.json 真实内容双路径验证（C# #31 的 Kotlin 落地检查）：
 * - 以真实资产文件驱动，防 ContentModels 的 @SerialName 与 data.json 键漂移（改键名=内容全丢）；
 * - 两条加载路径（data.json / GameContent 兜底）都必须经过 [GameContent.enrich] 且口径一致；
 * - enrich 只补空字段：data.json 已有值（UR/SSR 的 Story/Weapon 等）不被覆盖。
 *
 * 已知内容缺口（从旧版继承，非本任务回归）：SR/R 角色的 Weapon/WeaponDesc 在 data.json 与
 * enrich 武器表里均无数据 → 两条路径下一致为空。本测试只断言「两路径一致」，不固化空值：
 * 内容方补齐 SR/R 武器名后，一致性断言依旧成立。
 */
class DataJsonContentTest {

    private val dataJson: String =
        File("src/main/assets/data.json").takeIf { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("测试需真实内容文件：app/src/main/assets/data.json 不存在（Gradle 测试工作目录应为 app/）")

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

    @Test
    fun `json 路径解析真实 data_json 并完成 enrich`() {
        val traces = mutableListOf<String>()
        val service = GameService(
            saveProvider = MemoryProvider(),
            contentJson = dataJson,
            onTrace = { traces.add(it) },
        )

        assertTrue(
            "应走 json 路径，实际 trace：${traces.joinToString(" | ")}",
            traces.any { it.startsWith("content.loaded.from.json") },
        )
        assertEquals(31, service.characters.size)
        // 2026-08 三期：UP 定轨进主来源，data.json 与兜底同为「常驻 + UP」双池
        assertEquals(2, service.pools.size)
        val mainPool = service.pools.first { it.poolId == "pool_main" }
        val upPool = service.pools.first { it.poolId == "pool_flame" }
        assertEquals(31, mainPool.entries.size)
        assertEquals("char_ur_zhulong", upPool.featuredCharacterId)
        // data.json 的 31 棵天赋树 Nodes 全空 → loadContent 丢弃后由 buildTalentTrees 兜底补全
        assertEquals(31, service.talentTrees.size)

        // 所有角色的核心派生字段最终非空（data.json 已有或 enrich 补齐）
        service.characters.forEach { c ->
            assertFalse("${c.characterId}.story", c.story.isEmpty())
            assertTrue("${c.characterId}.voices", c.voices.isNotEmpty())
            assertFalse("${c.characterId}.weaponVfx", c.weaponVfx.isEmpty())
            assertFalse("${c.characterId}.ambientVfx", c.ambientVfx.isEmpty())
            assertFalse("${c.characterId}.faction", c.faction.isEmpty())
        }

        // enrich 不覆盖已有值：UR 的 Story/Weapon 保持 data.json 原文
        val zhulong = service.characters.first { it.characterId == "char_ur_zhulong" }
        assertEquals("阖辟神瞳·昼夜轮", zhulong.weapon)
        assertTrue(zhulong.weaponDesc.startsWith("由烛龙本瞳炼化的神环"))
        assertTrue(zhulong.story.startsWith("烛龙是 Shinwa 的最高图腾之一"))
    }

    @Test
    fun `兜底路径与 json 路径派生字段口径一致`() {
        val traces = mutableListOf<String>()
        val jsonService = GameService(MemoryProvider(), dataJson, onTrace = { traces.add(it) })
        val fallbackService = GameService(MemoryProvider(), null, onTrace = { traces.add(it) })

        assertTrue("应走兜底路径：${traces.joinToString(" | ")}", traces.any { it == "content.load.fallback" })
        assertEquals(31, fallbackService.characters.size)
        assertEquals(jsonService.characters.size, fallbackService.characters.size)
        // 2026-08 三期收敛：UP 定轨进主来源，两路径同为「常驻 + UP」双池（消除兜底独有差异）；
        // 仍仅对 pool_main 做逐字段口径断言（UP 池条目集允许内容方演进，只固化定轨角色一致）
        assertEquals(2, jsonService.pools.size)
        assertEquals(2, fallbackService.pools.size)
        val jsonMain = jsonService.pools.first { it.poolId == "pool_main" }
        val fallbackMain = fallbackService.pools.first { it.poolId == "pool_main" }
        assertEquals(jsonMain.entries.size, fallbackMain.entries.size)
        assertEquals(jsonMain.rarityWeights, fallbackMain.rarityWeights)
        assertEquals(jsonMain.hardPity, fallbackMain.hardPity)
        assertEquals(jsonMain.singleCost, fallbackMain.singleCost)
        assertEquals(jsonMain.tenCost, fallbackMain.tenCost)
        val jsonUp = jsonService.pools.first { it.poolId == "pool_flame" }
        val fallbackUp = fallbackService.pools.first { it.poolId == "pool_flame" }
        assertEquals(jsonUp.featuredCharacterId, fallbackUp.featuredCharacterId)
        assertEquals("char_ur_zhulong", jsonUp.featuredCharacterId)
        assertEquals(jsonService.talentTrees.size, fallbackService.talentTrees.size)

        // 逐角色逐字段一致性：包括「SR/R 武器名两路径同为空」的现状（不固化空值，只固化一致性）
        for (j in jsonService.characters) {
            val f = fallbackService.characters.first { it.characterId == j.characterId }
            assertEquals("${j.characterId}.displayName", j.displayName, f.displayName)
            assertEquals("${j.characterId}.story", j.story, f.story)
            assertEquals("${j.characterId}.voices", j.voices, f.voices)
            assertEquals("${j.characterId}.weaponVfx", j.weaponVfx, f.weaponVfx)
            assertEquals("${j.characterId}.ambientVfx", j.ambientVfx, f.ambientVfx)
            assertEquals("${j.characterId}.weapon", j.weapon, f.weapon)
            assertEquals("${j.characterId}.weaponDesc", j.weaponDesc, f.weaponDesc)
            assertEquals("${j.characterId}.faction", j.faction, f.faction)
            assertEquals("${j.characterId}.lore", j.lore, f.lore)
        }
    }
}
