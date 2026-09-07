package com.milan.game.domain.monetization

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MonetizationFormulas] 单元测试（2026-09-06 S5 配套）。
 *
 * 验证通行证奖励换算公式：星尘/钻石/素材均随 level 线性增长，
 * level<=0 钳位为 1（防 Rejected 路径误算）。
 */
class MonetizationFormulasTest {

    @Test
    fun `bpFreeRewardSoft 等级 1 起 2000 星尘，50 级封顶 100000`() {
        assertEquals(2000, MonetizationFormulas.bpFreeRewardSoft(1))
        assertEquals(10000, MonetizationFormulas.bpFreeRewardSoft(5))
        assertEquals(100000, MonetizationFormulas.bpFreeRewardSoft(50))
    }

    @Test
    fun `bpPremiumRewardHard 等级 10 起 50 钻石，50 级封顶 250`() {
        assertEquals(50, MonetizationFormulas.bpPremiumRewardHard(10))
        assertEquals(100, MonetizationFormulas.bpPremiumRewardHard(20))
        assertEquals(250, MonetizationFormulas.bpPremiumRewardHard(50))
    }

    @Test
    fun `bpPremiumRewardMaterial 等级 1 起 3 素材，非整 10 倍级发放`() {
        assertEquals(3, MonetizationFormulas.bpPremiumRewardMaterial(1))
        assertEquals(15, MonetizationFormulas.bpPremiumRewardMaterial(5))
        assertEquals(147, MonetizationFormulas.bpPremiumRewardMaterial(49))
    }

    @Test
    fun `负等级或零等级一律钳位为 1 防止 Rejected 路径误算`() {
        assertEquals(2000, MonetizationFormulas.bpFreeRewardSoft(0))
        assertEquals(2000, MonetizationFormulas.bpFreeRewardSoft(-5))
        assertEquals(5, MonetizationFormulas.bpPremiumRewardHard(0))
        assertEquals(3, MonetizationFormulas.bpPremiumRewardMaterial(-1))
    }
}
