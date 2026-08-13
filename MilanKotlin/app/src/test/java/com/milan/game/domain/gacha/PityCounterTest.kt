package com.milan.game.domain.gacha

import com.milan.game.data.Rarity
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
            assertEquals(Rarity.R, pity.rollWithPity(Random(1), alwaysR, minRarityForPity = 3))
        }
        assertEquals(0, pity.counter)
    }

    @Test
    fun negativeThreshold_treatedAsInvalid() {
        val pity = PityCounter(threshold = -1)
        assertEquals(Rarity.R, pity.rollWithPity(Random(1), alwaysR, minRarityForPity = 3))
        assertEquals(0, pity.counter)
    }

    @Test
    fun pity_firesExactlyAtThreshold() {
        val pity = PityCounter(threshold = 5)
        // 前 4 抽都是自然 R（低于保底档），counter 递增 1..4
        repeat(4) { i ->
            assertEquals(Rarity.R, pity.rollWithPity(Random(2), alwaysR, minRarityForPity = 3))
            assertEquals(i + 1, pity.counter)
        }
        // 第 5 抽触发保底 → SSR 且 counter 归零
        assertEquals(Rarity.SSR, pity.rollWithPity(Random(2), alwaysR, minRarityForPity = 3))
        assertEquals(0, pity.counter)
    }

    @Test
    fun naturalPityOrAbove_resetsCounter() {
        // 每次自然抽都是 SSR（保底档）→ 交付后判定重置 → 计数器始终为 0，永不触发保底
        val pity = PityCounter(threshold = 3)
        repeat(10) {
            val rolled = pity.rollWithPity(Random(3), alwaysSsr, minRarityForPity = 3)
            assertEquals(Rarity.SSR, rolled)
            pity.onNaturalPityOrAbove(rolled, minRarityForPity = 3)
            assertEquals(0, pity.counter)
        }
    }

    @Test
    fun deliveredBelowPityBand_doesNotResetCounter() {
        // P1-3 回归：掷出保底档但实际交付被降档（如 SR）→ 保底计数不重置
        val pity = PityCounter(threshold = 90)
        repeat(3) {
            pity.rollWithPity(Random(1), alwaysSsr, minRarityForPity = 3)
        }
        assertEquals(3, pity.counter)
        // 交付 SR（< 3）→ 不重置
        pity.onNaturalPityOrAbove(Rarity.SR, minRarityForPity = 3)
        assertEquals(3, pity.counter)
        // 交付 SSR（>= 3）→ 重置
        pity.onNaturalPityOrAbove(Rarity.SSR, minRarityForPity = 3)
        assertEquals(0, pity.counter)
    }

    @Test
    fun mixedSequence_counterAccumulatesUntilNaturalHit() {
        val pity = PityCounter(threshold = 10)
        // 2 发自然 R → counter=2；再一发自然 SSR（交付即保底档）→ 判定重置为 0
        assertEquals(Rarity.R, pity.rollWithPity(Random(4), alwaysR, minRarityForPity = 3))
        assertEquals(Rarity.R, pity.rollWithPity(Random(4), alwaysR, minRarityForPity = 3))
        assertEquals(2, pity.counter)
        val hit = pity.rollWithPity(Random(4), alwaysSsr, minRarityForPity = 3)
        assertEquals(Rarity.SSR, hit)
        pity.onNaturalPityOrAbove(hit, minRarityForPity = 3)
        assertEquals(0, pity.counter)
    }

    @Test
    fun reset_clearsCounter() {
        val pity = PityCounter(threshold = 5)
        pity.rollWithPity(Random(5), alwaysR, minRarityForPity = 3)
        pity.rollWithPity(Random(5), alwaysR, minRarityForPity = 3)
        assertEquals(2, pity.counter)
        pity.reset()
        assertEquals(0, pity.counter)
    }
}
