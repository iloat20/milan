package com.milan.game.domain.gacha

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UP 定轨掷选（GachaEngine.pickFeatured）纯函数测试：
 * 必中分支 / 硬币正反面 / 无效 UP 回退 / 唯一候选退化——全部种子确定性锁定。
 */
class FeaturedPickTest {

    private val candidates = listOf("ur_a", "ur_b", "ur_c")

    private fun pick(seed: Long, guaranteed: Boolean, cands: List<String> = candidates) =
        GachaEngine(Random(seed)).pickFeatured(cands, "ur_a", guaranteed)

    @Test
    fun `guaranteed 强制交付 UP 并清除标记`() {
        for (seed in 0L..20L) {
            val result = pick(seed, guaranteed = true)
            assertEquals("ur_a", result.pickedId)
            assertFalse("必中交付后不得再欠 UP", result.guaranteedNext)
        }
    }

    @Test
    fun `硬币正面 交付 UP 且状态不变`() {
        // 扫描种子找到一个「中」面：pickedId == featured 即为正面
        val winSeed = (0L..100L).first { pick(it, guaranteed = false).pickedId == "ur_a" }
        val result = pick(winSeed, guaranteed = false)
        assertEquals("ur_a", result.pickedId)
        assertFalse(result.guaranteedNext)
    }

    @Test
    fun `硬币反面 歪出非 UP 并置欠账`() {
        val loseSeed = (0L..100L).first { pick(it, guaranteed = false).pickedId != "ur_a" }
        val result = pick(loseSeed, guaranteed = false)
        assertTrue(result.pickedId in listOf("ur_b", "ur_c"))
        assertTrue("歪了必须欠下次必中", result.guaranteedNext)
    }

    @Test
    fun `UP 不在候选 回退普通抽取且不置标记`() {
        val result = GachaEngine(Random(7)).pickFeatured(candidates, "ur_missing", guaranteed = false)
        assertNull(result.pickedId)
        assertFalse(result.guaranteedNext)
    }

    @Test
    fun `空候选或空 UP 同样回退`() {
        val r1 = GachaEngine(Random(7)).pickFeatured(emptyList(), "ur_a", guaranteed = false)
        assertNull(r1.pickedId)
        val r2 = GachaEngine(Random(7)).pickFeatured(candidates, null, guaranteed = false)
        assertNull(r2.pickedId)
        val r3 = GachaEngine(Random(7)).pickFeatured(candidates, "", guaranteed = false)
        assertNull(r3.pickedId)
    }

    @Test
    fun `唯一候选即 UP 视作必中 不产生假欠账`() {
        for (seed in 0L..50L) {
            val result = GachaEngine(Random(seed)).pickFeatured(listOf("ur_a"), "ur_a", guaranteed = false)
            assertEquals("ur_a", result.pickedId)
            assertFalse(result.guaranteedNext)
        }
    }
}
