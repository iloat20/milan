package com.milan.game.domain.gacha

import com.milan.game.data.Rarity
import com.milan.game.domain.progression.EconomyFormulas
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PityCounter 保底语义测试（C# 无对应测试，补确定性覆盖）。
 *
 * 2026-08 P1-3 修复后：自然出货的重置判定移到 [PityCounter.onNaturalPityOrAbove]，
 * 由调用方以「实际交付档位」触发（防降档吞保底）——本文件同步锁定新语义。
 */
class PityCounterTest {

    // 权重只落 R 槽：自然抽永远低于保底档（SSR=3），计数器必然递增到阈值
    private val alwaysR = intArrayOf(10, 0, 0, 0)
    // 权重只落 SSR 槽：自然抽恒为保底档
    private val alwaysSsr = intArrayOf(0, 0, 10, 0)

    @Test
    fun invalidThreshold_neverTriggersPity_andDoesNotCount() {
        val pity = PityCounter(threshold = 0)
        repeat(50) {
            assertEquals(Rarity.R, pity.rollWithPity(Random(1), alwaysR, minRarityForPity = Rarity.SSR))
        }
        assertEquals(0, pity.counter)
    }

    @Test
    fun negativeThreshold_treatedAsInvalid() {
        val pity = PityCounter(threshold = -1)
        assertEquals(Rarity.R, pity.rollWithPity(Random(1), alwaysR, minRarityForPity = Rarity.SSR))
        assertEquals(0, pity.counter)
    }

    @Test
    fun pity_firesExactlyAtThreshold() {
        // 2026-08 软保底引入后的新契约：
        // - 硬保底 90 的软保底起点为 softPityStart(90)=73——起点【之前】权重不被调整，
        //   固定种子下每抽必为自然 R，计数器照常递增；
        // - 推进到第 threshold 抽时无条件交付保底档并归零（软保底只影响概率，不改硬保底语义）。
        val pity = PityCounter(threshold = 90)
        val start = EconomyFormulas.softPityStart(90)
        repeat(start - 1) { i ->
            assertEquals(Rarity.R, pity.rollWithPity(Random(2), alwaysR, minRarityForPity = Rarity.SSR))
            assertEquals(i + 1, pity.counter)
        }
        pity.counter = 89
        assertEquals(Rarity.SSR, pity.rollWithPity(Random(2), alwaysR, minRarityForPity = Rarity.SSR))
        assertEquals(0, pity.counter)
    }

    @Test
    fun naturalPityOrAbove_resetsCounter() {
        // 每次自然抽都是 SSR（保底档）→ 交付后判定重置 → 计数器始终为 0，永不触发保底
        val pity = PityCounter(threshold = 3)
        repeat(10) {
            val rolled = pity.rollWithPity(Random(3), alwaysSsr, minRarityForPity = Rarity.SSR)
            assertEquals(Rarity.SSR, rolled)
            pity.onNaturalPityOrAbove(rolled, minRarityForPity = Rarity.SSR)
            assertEquals(0, pity.counter)
        }
    }

    @Test
    fun deliveredBelowPityBand_doesNotResetCounter() {
        // P1-3 回归：掷出保底档但实际交付被降档（如 SR）→ 保底计数不重置
        val pity = PityCounter(threshold = 90)
        repeat(3) {
            pity.rollWithPity(Random(1), alwaysSsr, minRarityForPity = Rarity.SSR)
        }
        assertEquals(3, pity.counter)
        // 交付 SR（< 3）→ 不重置
        pity.onNaturalPityOrAbove(Rarity.SR, minRarityForPity = Rarity.SSR)
        assertEquals(3, pity.counter)
        // 交付 SSR（>= 3）→ 重置
        pity.onNaturalPityOrAbove(Rarity.SSR, minRarityForPity = Rarity.SSR)
        assertEquals(0, pity.counter)
    }

    @Test
    fun mixedSequence_counterAccumulatesUntilNaturalHit() {
        val pity = PityCounter(threshold = 10)
        // 2 发自然 R → counter=2；再一发自然 SSR（交付即保底档）→ 判定重置为 0
        assertEquals(Rarity.R, pity.rollWithPity(Random(4), alwaysR, minRarityForPity = Rarity.SSR))
        assertEquals(Rarity.R, pity.rollWithPity(Random(4), alwaysR, minRarityForPity = Rarity.SSR))
        assertEquals(2, pity.counter)
        val hit = pity.rollWithPity(Random(4), alwaysSsr, minRarityForPity = Rarity.SSR)
        assertEquals(Rarity.SSR, hit)
        pity.onNaturalPityOrAbove(hit, minRarityForPity = Rarity.SSR)
        assertEquals(0, pity.counter)
    }

    @Test
    fun reset_clearsCounter() {
        val pity = PityCounter(threshold = 5)
        pity.rollWithPity(Random(5), alwaysR, minRarityForPity = Rarity.SSR)
        pity.rollWithPity(Random(5), alwaysR, minRarityForPity = Rarity.SSR)
        assertEquals(2, pity.counter)
        pity.reset()
        assertEquals(0, pity.counter)
    }
}
