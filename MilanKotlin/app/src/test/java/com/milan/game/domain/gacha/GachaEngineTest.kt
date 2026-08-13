package com.milan.game.domain.gacha

import com.milan.game.data.Rarity
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GachaEngine 加权抽取测试（C# 无对应测试，补确定性边界覆盖）。 */
class GachaEngineTest {

    @Test
    fun rollRarity_zeroTotalWeights_fallsBackToR() {
        val engine = GachaEngine(Random(1))
        assertEquals(Rarity.R, engine.rollRarity(intArrayOf(0, 0, 0, 0)))
    }

    @Test
    fun rollRarity_singleWeight_slotsThatRarity() {
        val engine = GachaEngine(Random(7))
        // 权重只落在 SR 槽（i=1 → SR(2)），任何随机数都命中 SR
        assertEquals(Rarity.SR, engine.rollRarity(intArrayOf(0, 10, 0, 0)))
        assertEquals(Rarity.UR, engine.rollRarity(intArrayOf(0, 0, 0, 10)))
        assertEquals(Rarity.R, engine.rollRarity(intArrayOf(10, 0, 0, 0)))
    }

    @Test
    fun rollRarity_twoSlots_neverHitsZeroWeightSlots() {
        // 权重 [5,5,0,0] → 只可能 R 或 SR；大量抽取验证域
        val engine = GachaEngine(Random(42))
        repeat(200) {
            val rarity = engine.rollRarity(intArrayOf(5, 5, 0, 0))
            assertTrue("unexpected $rarity", rarity == Rarity.R || rarity == Rarity.SR)
        }
    }

    @Test
    fun rollRarity_distribution_respectsWeightBoundaries() {
        // 权重 [1,1,1,7]：roll ∈ [0,10)。roll<1 → R；1≤roll<2 → SR；2≤roll<3 → SSR；其余 → UR
        val engine = GachaEngine(Random(99))
        val counts = IntArray(4)
        repeat(10_000) { counts[engine.rollRarity(intArrayOf(1, 1, 1, 7)).value - 1]++ }
        // UR 占比 ~70% 允许 ±5% 抖动（确定性种子下是固定值，仅作冒烟断言）
        assertTrue("UR ratio ${counts[3]}", counts[3] > 6_500)
        assertTrue("R count ${counts[0]}", counts[0] in 800..1_200)
    }

    @Test
    fun rollRarity_extraWeightsClampToHighestRarity_noCrash() {
        // data.json 若提供 >4 个权重（漏校验），旧实现 Rarity.entries[4] 越界崩溃；
        // 现钳到最高稀有度 UR(4)，绝不抛异常（#3）。
        val engine = GachaEngine(Random(123))
        repeat(500) {
            val rarity = engine.rollRarity(intArrayOf(1, 1, 1, 1, 6))
            assertTrue("unexpected $rarity", rarity.ordinal in 0..3)
        }
    }

    @Test
    fun pickWeighted_emptyInputs_returnsNull() {
        val engine = GachaEngine(Random(3))
        assertNull(engine.pickWeighted(emptyList(), emptyList()))
        assertNull(engine.pickWeighted(listOf("a"), emptyList()))
    }

    @Test
    fun pickWeighted_zeroTotal_returnsFirstId() {
        val engine = GachaEngine(Random(5))
        assertEquals("a", engine.pickWeighted(listOf("a", "b"), listOf(0, 0)))
    }

    @Test
    fun pickWeighted_singleWeight_returnsThatId() {
        val engine = GachaEngine(Random(11))
        assertEquals("b", engine.pickWeighted(listOf("a", "b", "c"), listOf(0, 10, 0)))
    }

    @Test
    fun pickWeighted_weightsShorterThanIds_usesMinLength() {
        val engine = GachaEngine(Random(17))
        // ids 3 个、weights 2 个 → 只在 a/b 间抽取，永不返回 c
        repeat(200) {
            val id = requireNotNull(engine.pickWeighted(listOf("a", "b", "c"), listOf(1, 1)))
            assertTrue(id == "a" || id == "b")
        }
    }
}
