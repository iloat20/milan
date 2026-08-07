package com.milan.game.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 养成引擎测试（翻译 C# ProgressionTests）。
 */
class ProgressionEngineTest {

    private val engine = ProgressionEngine()

    @Test
    fun expToLevel_zeroExpIsLevel1() {
        assertEquals(1, engine.expToLevel(0))
    }

    // [Theory] (100,2) (299,2) (300,3) (600,4) (1000,5)
    @Test
    fun expToLevel_matchesCumulativeCost() {
        assertEquals(2, engine.expToLevel(100))
        assertEquals(2, engine.expToLevel(299))
        assertEquals(3, engine.expToLevel(300))
        assertEquals(4, engine.expToLevel(600))
        assertEquals(5, engine.expToLevel(1000))
    }

    // 到达等级 L 所需累计经验 = 50×(L-1)×L（每级 k→k+1 增量 k×100）。
    @Test
    fun expToLevel_roundTrip() {
        for (L in 1..20) {
            val need = 50 * (L - 1) * L
            assertEquals(L, engine.expToLevel(need))
            if (L > 1) assertEquals(L - 1, engine.expToLevel(need - 1))
        }
    }

    @Test
    fun statAtLevel_baseAtLevel1Stage1() {
        assertEquals(100, engine.statAtLevel(100, 1, 1, 1f))
    }

    // 每级 +10%：level 1 → base；level 11 → base×2。
    @Test
    fun statAtLevel_levelScalesTenPercentPerLevel() {
        assertEquals(200, engine.statAtLevel(100, 11, 1, 1f))
    }

    @Test
    fun statAtLevel_stageMultiplies() {
        assertEquals(200, engine.statAtLevel(100, 1, 2, 1f))
    }

    @Test
    fun statAtLevel_negativeInputsClamped() {
        assertEquals(100, engine.statAtLevel(100, 0, 0, 1f))
    }

    // [Theory] (0,1.0) (1,1.0) (2,1.05) (7,1.30)
    @Test
    fun starMultiplier_endpoints() {
        assertEquals(1.0f, ProgressionEngine.starMultiplier(0), 1e-4f)
        assertEquals(1.0f, ProgressionEngine.starMultiplier(1), 1e-4f)
        assertEquals(1.05f, ProgressionEngine.starMultiplier(2), 1e-4f)
        assertEquals(1.30f, ProgressionEngine.starMultiplier(7), 1e-4f)
    }

    @Test
    fun starMultiplier_monotonic() {
        var prev = 0f
        for (s in 1..7) {
            val m = ProgressionEngine.starMultiplier(s)
            assertTrue("star $s multiplier should strictly increase", m > prev)
            prev = m
        }
    }
}
