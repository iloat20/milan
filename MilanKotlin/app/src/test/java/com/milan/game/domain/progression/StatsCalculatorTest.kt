package com.milan.game.domain.progression

import com.milan.game.domain.battle.UnitStats
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 属性计算器测试（迁移不变式：锁定 GameState.computeStatsAt 历史公式行为）。
 *
 * 2026-08 KMP 下沉延续：属性计算公式从 app 侧 ui/GameState.kt 下沉 shared domain，
 * 由 [StatsCalculator] 承担（跨端复用：桌面模拟器/战斗页与 App 共用同一份）。
 * 本测试在迁移前锁定公式输入输出，保证迁移后行为逐位一致。
 */
class StatsCalculatorTest {

    private val progression = ProgressionEngine()
    private val talent = TalentEngine()

    /** 默认基础值（对齐 data.json 常见尺寸：atk/def/hp/spd）。 */
    private val base = listOf(100, 80, 1000, 12)

    private fun compute(
        baseStats: List<Int> = base,
        level: Int = 1,
        stage: Int = 1,
        stars: Int = 1,
        branchIds: List<String> = emptyList(),
        characterId: String = "",
    ): UnitStats = StatsCalculator.compute(
        baseStats = baseStats,
        level = level,
        stage = stage,
        stars = stars,
        branchIds = branchIds,
        characterId = characterId,
        progression = progression,
        talent = talent,
    )

    @Test
    fun compute_baseAtLevel1Stage1SingleStar() {
        val s = compute()
        assertEquals(UnitStats(atk = 100, def = 80, hp = 1000, spd = 12, characterId = ""), s)
    }

    // 每级 +10%：level 1 → base；level 11 → base×2。
    @Test
    fun compute_levelScalesTenPercentPerLevel() {
        val s = compute(level = 11)
        assertEquals(UnitStats(atk = 200, def = 160, hp = 2000, spd = 24, characterId = ""), s)
    }

    @Test
    fun compute_stageMultiplies() {
        val s = compute(stage = 2)
        assertEquals(UnitStats(atk = 200, def = 160, hp = 2000, spd = 24, characterId = ""), s)
    }

    // 每星 +5%：4★ → ×1.15；spd 12×1.15=13.8 → toInt 截断为 13。
    @Test
    fun compute_starScalesFivePercentPerStar() {
        val s = compute(stars = 4)
        assertEquals(UnitStats(atk = 115, def = 92, hp = 1150, spd = 13, characterId = ""), s)
    }

    // 组合：lv2(×1.1) × stage2(×2) × 4★(×1.15)。
    @Test
    fun compute_combinedScales() {
        val s = compute(level = 2, stage = 2, stars = 4)
        assertEquals(UnitStats(atk = 253, def = 202, hp = 2530, spd = 30, characterId = ""), s)
    }

    // 分支加成：branch_power 每节点 atk +3%。
    @Test
    fun compute_powerBranchBoostsAtk() {
        val s = compute(branchIds = listOf(TalentEngine.BRANCH_POWER))
        assertEquals(103, s.atk)
        assertEquals(80, s.def)
        assertEquals(1000, s.hp)
        assertEquals(12, s.spd)
    }

    // 分支加成：branch_defense 每节点 def/hp 各 +3%。
    @Test
    fun compute_defenseBranchBoostsDefAndHp() {
        val s = compute(branchIds = listOf(TalentEngine.BRANCH_DEFENSE))
        assertEquals(100, s.atk)
        assertEquals(82, s.def)   // 80×1.03=82.4 → 82
        assertEquals(1030, s.hp)  // 1000×1.03 → 1030
        assertEquals(12, s.spd)
    }

    // 分支加成：branch_utility 每节点 spd +3%（spd 基数取 120 使截断可区分）。
    @Test
    fun compute_utilityBranchBoostsSpd() {
        val s = compute(baseStats = listOf(100, 80, 1000, 120), branchIds = listOf(TalentEngine.BRANCH_UTILITY))
        assertEquals(123, s.spd) // 120×1.03=123.6 → 123
        assertEquals(100, s.atk)
    }

    // 多分支叠加：power + defense 各 1 节点。
    @Test
    fun compute_multipleBranchesStack() {
        val s = compute(branchIds = listOf(TalentEngine.BRANCH_POWER, TalentEngine.BRANCH_DEFENSE))
        assertEquals(103, s.atk)  // ×1.03
        assertEquals(82, s.def)   // ×1.03
        assertEquals(1030, s.hp)  // ×1.03
        assertEquals(12, s.spd)
    }

    // 未知分支忽略（防御内容数据脏值，语义同 TalentEngine.talentMultipliers）。
    @Test
    fun compute_unknownBranchIgnored() {
        val s = compute(branchIds = listOf("branch_unknown"))
        assertEquals(UnitStats(atk = 100, def = 80, hp = 1000, spd = 12, characterId = ""), s)
    }

    // BaseStats 越界兜底：空列表 → 全部 fallback（100/80/1000/12）。
    @Test
    fun compute_emptyBaseStatsFallsBack() {
        val s = compute(baseStats = emptyList())
        assertEquals(UnitStats(atk = 100, def = 80, hp = 1000, spd = 12, characterId = ""), s)
    }

    // 部分越界：仅 atk 有值，其余走 fallback。
    @Test
    fun compute_shortBaseStatsFallsBackPerIndex() {
        val s = compute(baseStats = listOf(999))
        assertEquals(UnitStats(atk = 999, def = 80, hp = 1000, spd = 12, characterId = ""), s)
    }

    // 防御非法输入：level/stage ≤0 钳到 1（语义同 statAtLevel）。
    @Test
    fun compute_nonPositiveLevelStageClamped() {
        val s = compute(level = 0, stage = 0)
        assertEquals(UnitStats(atk = 100, def = 80, hp = 1000, spd = 12, characterId = ""), s)
    }

    // stars ≤0 钳到 1★ 倍率（语义同 starMultiplier）。
    @Test
    fun compute_nonPositiveStarsClamped() {
        val s = compute(stars = 0)
        assertEquals(UnitStats(atk = 100, def = 80, hp = 1000, spd = 12, characterId = ""), s)
    }

    // characterId 原样透传（战斗结算/详情页需要关联角色）。
    @Test
    fun compute_characterIdPassedThrough() {
        val s = compute(characterId = "hero_1")
        assertEquals("hero_1", s.characterId)
    }
}