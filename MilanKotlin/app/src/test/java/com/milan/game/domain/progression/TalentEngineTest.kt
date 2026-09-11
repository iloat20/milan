package com.milan.game.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 天赋引擎测试（翻译 C# TalentTests）。
 */
class TalentEngineTest {

    private val engine = TalentEngine()

    @Test
    fun canAllocate_alreadyAllocated_returnsFalse() {
        val alloc = listOf("n1")
        assertFalse(engine.canAllocate("n1", alloc, emptyMap()))
    }

    @Test
    fun canAllocate_rootNodeWithoutPrereq_returnsTrue() {
        assertTrue(engine.canAllocate("root", emptyList(), emptyMap()))
    }

    @Test
    fun canAllocate_allPrereqsMet_returnsTrue() {
        val alloc = listOf("a", "b")
        val pre = mapOf("c" to listOf("a", "b"))
        assertTrue(engine.canAllocate("c", alloc, pre))
    }

    @Test
    fun canAllocate_missingPrereq_returnsFalse() {
        val alloc = listOf("a")
        val pre = mapOf("c" to listOf("a", "b"))
        assertFalse(engine.canAllocate("c", alloc, pre))
    }

    // C# 侧 null 前置数组（Kotlin Map 值声明可空以保留该语义）
    @Test
    fun canAllocate_nullPrereqArray_returnsTrue() {
        val pre: Map<String, List<String>?> = mapOf("c" to null)
        assertTrue(engine.canAllocate("c", emptyList(), pre))
    }

    @Test
    fun totalPoints_sumsCosts() {
        val costs = mapOf("a" to 1, "b" to 2, "c" to 3)
        assertEquals(6, engine.totalPoints(costs))
    }

    // ── talentMultipliers：分支属性加成（每节点 +3%）──

    @Test
    fun talentMultipliers_powerBranch_onlyAtk() {
        val m = engine.talentMultipliers(listOf(TalentEngine.BRANCH_POWER))
        assertEquals(0.03f, m.atk, 1e-6f)
        assertEquals(0f, m.def, 1e-6f)
        assertEquals(0f, m.hp, 1e-6f)
        assertEquals(0f, m.spd, 1e-6f)
    }

    @Test
    fun talentMultipliers_twoPowerNodes_accumulates() {
        val m = engine.talentMultipliers(listOf(TalentEngine.BRANCH_POWER, TalentEngine.BRANCH_POWER))
        assertEquals(0.06f, m.atk, 1e-6f)
    }

    @Test
    fun talentMultipliers_defenseBranch_boostsDefAndHp() {
        val m = engine.talentMultipliers(listOf(TalentEngine.BRANCH_DEFENSE))
        assertEquals(0.03f, m.def, 1e-6f)
        assertEquals(0.03f, m.hp, 1e-6f)
    }

    @Test
    fun talentMultipliers_utilityBranch_boostsSpd() {
        val m = engine.talentMultipliers(listOf(TalentEngine.BRANCH_UTILITY))
        assertEquals(0.03f, m.spd, 1e-6f)
    }

    @Test
    fun talentMultipliers_unknownBranch_ignored() {
        val m = engine.talentMultipliers(listOf("branch_xxx"))
        assertEquals(0f, m.atk, 1e-6f)
        assertEquals(0f, m.def, 1e-6f)
        assertEquals(0f, m.hp, 1e-6f)
        assertEquals(0f, m.spd, 1e-6f)
    }

    @Test
    fun talentMultipliers_emptyList_allZero() {
        val m = engine.talentMultipliers(emptyList())
        assertEquals(0f, m.atk, 1e-6f)
        assertEquals(0f, m.def, 1e-6f)
        assertEquals(0f, m.hp, 1e-6f)
        assertEquals(0f, m.spd, 1e-6f)
    }

    // ── R7-P1：状态天赋 Chance=0 时从 Value 回退 ──

    @Test
    fun statusChance_explicitChanceWins() {
        val e = TalentEffect(TalentEffectType.Burn, value = 30f, chance = 0.25f, duration = 3)
        assertEquals(0.25f, engine.statusChanceOf(e), 1e-6f)
    }

    @Test
    fun statusChance_valueAsFraction() {
        val e = TalentEffect(TalentEffectType.Poison, value = 0.03f, chance = 0f, duration = 0)
        assertEquals(0.03f, engine.statusChanceOf(e), 1e-6f)
    }

    @Test
    fun statusChance_valueAsPercent() {
        val e = TalentEffect(TalentEffectType.Burn, value = 30f, chance = 0f, duration = 0)
        assertEquals(0.30f, engine.statusChanceOf(e), 1e-6f)
    }

    @Test
    fun statusChance_zeroBoth_staysZero() {
        val e = TalentEffect(TalentEffectType.Stun, value = 0f, chance = 0f, duration = 0)
        assertEquals(0f, engine.statusChanceOf(e), 1e-6f)
    }

    @Test
    fun statusDuration_zeroDefaultsToTwo() {
        val e = TalentEffect(TalentEffectType.Chill, value = 0.2f, chance = 0f, duration = 0)
        assertEquals(2, engine.statusDurationOf(e))
    }

    @Test
    fun talentEffects_legacyValueOnlyStatus_applies() {
        val map = mapOf(
            "n1" to listOf(TalentEffect(TalentEffectType.Burn, value = 30f, chance = 0f, duration = 0)),
        )
        val r = engine.talentEffects(listOf("n1"), map)
        assertEquals(0.30f, r.burnChance, 1e-6f)
        assertEquals(2, r.burnDuration)
    }
}
