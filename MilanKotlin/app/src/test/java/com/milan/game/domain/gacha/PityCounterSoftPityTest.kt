package com.milan.game.domain.gacha

import com.milan.game.data.Rarity
import com.milan.game.domain.progression.EconomyFormulas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.random.Random

/**
 * 软保底爬坡测试（2026-08 优化）：起始点、线性增量、入参不可变、无保底豁免。
 */
class PityCounterSoftPityTest {

    private fun weights() = intArrayOf(9300, 600, 90, 10)

    @Test
    fun softPityStart_90hardYields73() {
        assertEquals(73, EconomyFormulas.softPityStart(90))
    }

    @Test
    fun softPityStart_nonPositiveHardPity_disablesRamp() {
        assertEquals(0, EconomyFormulas.softPityStart(0))
        assertEquals(0, EconomyFormulas.softPityStart(-10))
    }

    @Test
    fun belowSoftPityStart_sameArrayReferenceNoBoost() {
        val pity = PityCounter(90)
        pity.counter = 72 // start(90)=73 的前一步
        val w = weights()
        assertSame(w, pity.softAdjustedWeights(w, Rarity.SSR))
    }

    @Test
    fun atSoftPityStart_singleStepBoost() {
        val pity = PityCounter(90)
        pity.counter = 73
        val out = pity.softAdjustedWeights(weights(), Rarity.SSR)
        // 保底档（SSR，下标 2）+1 步；其余档位不变。
        assertEquals(90 + EconomyFormulas.softPityRampStep(), out[2])
        assertEquals(9300, out[0])
        assertEquals(600, out[1])
        assertEquals(10, out[3])
    }

    @Test
    fun nearHardPity_boostGrowsLinearly() {
        val pity = PityCounter(90)
        pity.counter = 89 // 硬保底前最后一抽：(89-73+1)=17 步
        val out = pity.softAdjustedWeights(weights(), Rarity.SSR)
        assertEquals(90 + 17 * EconomyFormulas.softPityRampStep(), out[2])
    }

    @Test
    fun inputWeightsNotMutated() {
        val pity = PityCounter(90)
        pity.counter = 89
        val w = weights()
        pity.softAdjustedWeights(w, Rarity.SSR)
        assertEquals(90, w[2])
    }

    @Test
    fun noThreshold_rampDisabled() {
        val pity = PityCounter(0)
        pity.counter = 85
        val w = weights()
        assertSame(w, pity.softAdjustedWeights(w, Rarity.SSR))
    }

    @Test
    fun rollWithPity_softRegion_counterAdvancesNormally() {
        // 软保底只改权重不改计数语义：连续抽到 start 点时计数正常累加、不提前触发硬保底。
        val pity = PityCounter(90)
        repeat(EconomyFormulas.softPityStart(90)) {
            pity.rollWithPity(Random(1), weights(), Rarity.SSR)
            // 自然出货重置由调用方经 onNaturalPityOrAbove 判定，此处固定权重不会出保底档。
        }
        assertEquals(EconomyFormulas.softPityStart(90), pity.counter)
    }
}
