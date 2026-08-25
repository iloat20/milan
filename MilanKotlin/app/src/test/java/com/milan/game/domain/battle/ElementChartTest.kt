package com.milan.game.domain.battle

import org.junit.Assert.assertEquals
import org.junit.Test

/** 八元素克制矩阵测试：五行环、光暗互克、电水土三角、脏值豁免。 */
class ElementChartTest {

    @Test
    fun `五行相克环单向成立`() {
        val ring = listOf("Metal", "Wood", "Earth", "Water", "Flame")
        for (i in ring.indices) {
            val attacker = ring[i]
            val defender = ring[(i + 1) % ring.size]
            assertEquals(
                "$attacker 应克制 $defender",
                ElementChart.COUNTER_MULTIPLIER,
                ElementChart.damageMultiplier(attacker, defender),
                0.0,
            )
            // 环是单向的：反向无加成
            assertEquals(
                "$defender 不应克制 $attacker",
                1.0,
                ElementChart.damageMultiplier(defender, attacker),
                0.0,
            )
        }
    }

    @Test
    fun `光暗互克双向`() {
        assertEquals(
            ElementChart.COUNTER_MULTIPLIER,
            ElementChart.damageMultiplier("Light", "Shadow"),
            0.0,
        )
        assertEquals(
            ElementChart.COUNTER_MULTIPLIER,
            ElementChart.damageMultiplier("Shadow", "Light"),
            0.0,
        )
    }

    @Test
    fun `雷克水_土克雷_水不克雷`() {
        assertEquals(ElementChart.COUNTER_MULTIPLIER, ElementChart.damageMultiplier("Thunder", "Water"), 0.0)
        assertEquals(ElementChart.COUNTER_MULTIPLIER, ElementChart.damageMultiplier("Earth", "Thunder"), 0.0)
        assertEquals(1.0, ElementChart.damageMultiplier("Water", "Thunder"), 0.0)
    }

    @Test
    fun `未知或空元素返回1倍`() {
        assertEquals(1.0, ElementChart.damageMultiplier(null, "Wood"), 0.0)
        assertEquals(1.0, ElementChart.damageMultiplier("Metal", null), 0.0)
        assertEquals(1.0, ElementChart.damageMultiplier("", "Wood"), 0.0)
        assertEquals(1.0, ElementChart.damageMultiplier("Metal", ""), 0.0)
        assertEquals(1.0, ElementChart.damageMultiplier("Poison", "Wood"), 0.0) // 未知键
        assertEquals(1.0, ElementChart.damageMultiplier("Metal", "Poison"), 0.0)
    }

    @Test
    fun `同元素不互克`() {
        assertEquals(1.0, ElementChart.damageMultiplier("Flame", "Flame"), 0.0)
    }
}
