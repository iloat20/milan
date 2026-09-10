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
 * 内容完整性（2026-08-30 校正）：SR/R 角色的 Weapon/WeaponDesc 已于 2026-08 武器重做后全量补齐
 * （31/31 非空），旧注释「两路径同为空」已失真。本测试只断言「两路径一致」，不固化空值：
 * 内容方再改武器名后，一致性断言依旧成立。
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
        // R4-57（2026-08-30）：Nodes 已全量补齐，旧注释「全空 → 兜底补全」已失真。
        // 逐树断言非空——否则一旦再退化为空树，会被 loadContent 静默丢弃并由兜底掩盖，
        // 本断言（只比数量 31）对此毫无鉴别力。
        assertEquals(31, service.talentTrees.size)
        service.talentTrees.forEach { t ->
            assertTrue("天赋树 ${t.treeId} 的 Nodes 为空会被 loadContent 静默丢弃", t.nodes.isNotEmpty())
        }

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
        assertTrue(zhulong.story.startsWith("本源：烛龙"))
    }

    @Test
    fun `兜底路径与 json 路径派生字段口径一致`() {
        val traces = mutableListOf<String>()
        val jsonService = GameService(MemoryProvider(), dataJson, onTrace = { traces.add(it) })
        val fallbackService = GameService(MemoryProvider(), null, onTrace = { traces.add(it) })

        assertTrue("应走兜底路径：${traces.joinToString(" | ")}", traces.any { it == "content.load.fallback" })
        assertEquals(31, fallbackService.characters.size)
        assertEquals(jsonService.characters.size, fallbackService.characters.size)
        // 2026-08 三期收敛：UP 定轨进主来源，两路径同为「常驻 + UP」双池。
        // R4-03（2026-08-30）：改为**逐池**比对权重与条目集。此前只比 pool_main，
        // 兜底 pool_flame「R 档 40% 权重 / 0 候选」的塌缩因此被静默放过——
        // 而 loadContent 的池校验只作用于 json 路径，兜底池绕过校验，两路径呈现两套概率。
        assertEquals(2, jsonService.pools.size)
        assertEquals(2, fallbackService.pools.size)
        jsonService.pools.forEach { jp ->
            val fp = fallbackService.pools.first { it.poolId == jp.poolId }
            assertEquals("${jp.poolId}.rarityWeights", jp.rarityWeights, fp.rarityWeights)
            assertEquals("${jp.poolId}.hardPity", jp.hardPity, fp.hardPity)
            assertEquals("${jp.poolId}.singleCost", jp.singleCost, fp.singleCost)
            assertEquals("${jp.poolId}.tenCost", jp.tenCost, fp.tenCost)
            assertEquals("${jp.poolId}.featuredCharacterId", jp.featuredCharacterId, fp.featuredCharacterId)
            assertEquals(
                "${jp.poolId}.entries",
                jp.entries.map { it.characterId }.toSet(),
                fp.entries.map { it.characterId }.toSet(),
            )
        }
        assertEquals(
            "char_ur_zhulong",
            jsonService.pools.first { it.poolId == "pool_flame" }.featuredCharacterId,
        )
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

    /**
     * C4（2026-08-28 审查回归）：每个角色都必须带技能。
     * 此前 data.json 仅 11/31 角色有 `Skills`，其余 20 个（含**全部 UR/SSR**）在角色详情页
     * 的技能面板显示空态；旧断言唯独漏了 skills，故缺口被静默放过。
     */
    @Test
    fun `每个角色都必须带技能`() {
        val service = GameService(MemoryProvider(), dataJson)
        service.characters.forEach { c ->
            assertTrue("${c.characterId} 缺少技能（详情页技能面板会空白）", c.skills.isNotEmpty())
            c.skills.forEach { s ->
                assertFalse("${c.characterId} 技能 ${s.skillId} 缺名称", s.displayName.isEmpty())
                assertFalse("${c.characterId} 技能 ${s.skillId} 缺描述", s.description.isEmpty())
            }
        }
    }

    /**
     * C2（2026-08-28 审查回归）：声明的 weaponVfx 必须有对应资源文件。
     * 缺图时 CharacterDetailScreen 回退显示武器名（不崩溃），但玩家看到的是空白武器区。
     */
    @Test
    fun `全部 weaponVfx 均有对应资源文件`() {
        val service = GameService(MemoryProvider(), dataJson)
        val dir = File("src/main/assets/weapons")
        val available = dir.takeIf { it.exists() }?.listFiles()
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?: error("武器资源目录不存在：${dir.path}")
        service.characters.forEach { c ->
            assertTrue(
                "${c.characterId} 的武器图缺失：weapons/${c.weaponVfx}.webp",
                c.weaponVfx in available,
            )
        }
    }

    /**
     * C1（2026-08-28 审查回归）：非零权重档位必须有候选角色。
     * 无候选的档位权重会被 [GameService.resolveRarityWithCandidates] 就近上抬，造成概率失真——
     * UP 池曾有 R 档 40% 权重但 0 候选，全部塌缩进仅 1 个候选的 SR 档，
     * 导致饕餮独占该池 70% 出货（名义仅 30%）。权重为 0 属刻意设计，放行。
     *
     * R4-03（2026-08-30）：**两条加载路径都必须校验**。C1 只在 data.json 侧修复，
     * 兜底 GameContent.buildPools 原样复现同一缺陷（R 权重 400 / 0 候选 → SR 档 70%），
     * 而本测试此前只对 json 路径执行，兜底路径完全在网外。
     */
    @Test
    fun `非零权重档位必须有候选角色（防概率塌缩，两路径）`() {
        val bothPaths = listOf(
            "json" to GameService(MemoryProvider(), dataJson),
            "fallback" to GameService(MemoryProvider(), null),
        )
        bothPaths.forEach { (label, service) ->
            assertTrue("$label 路径应加载真实卡池", service.pools.isNotEmpty())
            service.pools.forEach { pool ->
                pool.rarityWeights.forEachIndexed { i, w ->
                    if (w > 0) {
                        assertTrue(
                            "$label 路径：池 ${pool.poolId} 的档位 ${i + 1} 权重为 $w 却没有候选角色 —— " +
                                "该档产出会被上抬到邻近档位，造成概率塌缩",
                            pool.entries.any { it.rarityIndex == i + 1 },
                        )
                    }
                }
            }
        }
    }

    /**
     * P1-7（2026-09-08）：数值与技能字段两路径对账。
     *
     * 既有「口径一致」测试只比对文本字段（displayName/story/voices/weapon 等）。
     * 若 data.json 改了**数值/结构字段**（BaseRarity/BaseStats/MaxStars/Element/TalentTreeId/Skills）
     * 而兜底 GameContent 未同步，抽卡权重、养成成长、属性克制会呈现两套数值，
     * 而既有断言（文本比对）**不会红**——这是单测网的真实缺口。
     * 本测试把 [sync_gamecontent.py] 覆盖的字段全部纳入对账（含逐技能逐字段）。
     */
    @Test
    fun `数值与技能字段两路径对账`() {
        val jsonService = GameService(MemoryProvider(), dataJson)
        val fallbackService = GameService(MemoryProvider(), null)

        for (j in jsonService.characters) {
            val f = fallbackService.characters.firstOrNull { it.characterId == j.characterId }
                ?: error("兜底缺角色 ${j.characterId}（data.json 新增角色后必须跑 sync_gamecontent.py）")

            assertEquals("${j.characterId}.title", j.title, f.title)
            assertEquals("${j.characterId}.world", j.world, f.world)
            assertEquals("${j.characterId}.element", j.element, f.element)
            assertEquals("${j.characterId}.baseRarity", j.baseRarity, f.baseRarity)
            assertEquals("${j.characterId}.baseStats", j.baseStats, f.baseStats)
            assertEquals("${j.characterId}.maxStage", j.maxStage, f.maxStage)
            assertEquals("${j.characterId}.maxStars", j.maxStars, f.maxStars)
            assertEquals("${j.characterId}.canBreakthrough", j.canBreakthrough, f.canBreakthrough)
            assertEquals("${j.characterId}.talentTreeId", j.talentTreeId, f.talentTreeId)

            assertEquals("${j.characterId}.skills.size", j.skills.size, f.skills.size)
            j.skills.zip(f.skills).forEachIndexed { i, (js, fs) ->
                assertEquals("${j.characterId}.skills[$i].skillId", js.skillId, fs.skillId)
                assertEquals("${j.characterId}.skills[$i].displayName", js.displayName, fs.displayName)
                assertEquals("${j.characterId}.skills[$i].description", js.description, fs.description)
                assertEquals("${j.characterId}.skills[$i].element", js.element, fs.element)
                assertEquals("${j.characterId}.skills[$i].type", js.type, fs.type)
                assertEquals("${j.characterId}.skills[$i].power", js.power, fs.power)
            }
        }
    }
}
