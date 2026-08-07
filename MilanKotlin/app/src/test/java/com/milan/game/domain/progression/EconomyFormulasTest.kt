package com.milan.game.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 经济公式是养成与抽卡的唯一事实来源，任何改动都会直接影响存档里的资源余额。
 * 这里锁死口径与边界（非法输入被钳制、批量升级在预算/上限处正确截断）。
 * （翻译 C# EconomyFormulasTests）
 */
class EconomyFormulasTest {

    // [Theory] (1,20) (4,80) (0,20) (-5,20)：stage 非法值被钳到 1，不能返回 0 级上限（否则永远升不了级）
    @Test
    fun maxLevelForStage_clampsInvalidStage() {
        assertEquals(20, EconomyFormulas.maxLevelForStage(1))
        assertEquals(80, EconomyFormulas.maxLevelForStage(4))
        assertEquals(20, EconomyFormulas.maxLevelForStage(0))
        assertEquals(20, EconomyFormulas.maxLevelForStage(-5))
    }

    // [Theory] (1,50) (10,500) (0,50) (-3,50)：level 非法值被钳到 1，绝不能出现 0 成本（可无限白嫖升级）
    @Test
    fun levelCost_linearAndNeverZero() {
        assertEquals(50, EconomyFormulas.levelCost(1))
        assertEquals(500, EconomyFormulas.levelCost(10))
        assertEquals(50, EconomyFormulas.levelCost(0))
        assertEquals(50, EconomyFormulas.levelCost(-3))
    }

    // [Theory] (1,20,500) (3,60,1500) (0,20,500)
    @Test
    fun ascendCosts_scaleWithStage() {
        assertEquals(20, EconomyFormulas.ascendFragments(1))
        assertEquals(500, EconomyFormulas.ascendSoft(1))
        assertEquals(60, EconomyFormulas.ascendFragments(3))
        assertEquals(1500, EconomyFormulas.ascendSoft(3))
        assertEquals(20, EconomyFormulas.ascendFragments(0))
        assertEquals(500, EconomyFormulas.ascendSoft(0))
    }

    // [Theory] (1,20) (5,100) (0,20)
    @Test
    fun starUpFragments_scalesWithStars() {
        assertEquals(20, EconomyFormulas.starUpFragments(1))
        assertEquals(100, EconomyFormulas.starUpFragments(5))
        assertEquals(20, EconomyFormulas.starUpFragments(0))
    }

    // [Theory] (4,50) (3,20) (2,5) (1,1) (99,1) (0,1)：未知稀有度必须回退到最低补偿，不能抛异常中断抽卡事务
    @Test
    fun fragmentsForRarity_coversAllAndFallsBack() {
        assertEquals(50, EconomyFormulas.fragmentsForRarity(4)) // UR
        assertEquals(20, EconomyFormulas.fragmentsForRarity(3)) // SSR
        assertEquals(5, EconomyFormulas.fragmentsForRarity(2))  // SR
        assertEquals(1, EconomyFormulas.fragmentsForRarity(1))  // R
        assertEquals(1, EconomyFormulas.fragmentsForRarity(99)) // 未知稀有度兜底
        assertEquals(1, EconomyFormulas.fragmentsForRarity(0))
    }

    // 累计经验必须恰好等于逐级经验之和，否则经验条会与实际升级点错位。
    @Test
    fun cumulativeExp_consistentWithExpForLevel() {
        for (lv in 1..30) {
            var sum = 0
            for (k in 1 until lv) sum += EconomyFormulas.expForLevel(k)
            assertEquals(sum, EconomyFormulas.cumulativeExp(lv))
        }
    }

    @Test
    fun cumulativeExp_zeroAtLevelOneAndBelow() {
        assertEquals(0, EconomyFormulas.cumulativeExp(1))
        assertEquals(0, EconomyFormulas.cumulativeExp(0))
        assertEquals(0, EconomyFormulas.cumulativeExp(-9))
    }

    // 1→4 级：50 + 100 + 150 = 300
    @Test
    fun planLevelUp_fullRequestWhenBudgetEnough() {
        val (gained, cost) = EconomyFormulas.planLevelUp(currentLevel = 1, maxLevel = 20, budget = 10_000, requested = 3)
        assertEquals(3, gained)
        assertEquals(300, cost)
    }

    // 预算 120：能付 50（1→2），付不起接下来的 100 → 只升 1 级、只扣 50。
    @Test
    fun planLevelUp_partialWhenBudgetShort() {
        val (gained, cost) = EconomyFormulas.planLevelUp(1, 20, budget = 120, requested = 5)
        assertEquals(1, gained)
        assertEquals(50, cost)
    }

    @Test
    fun planLevelUp_stopsAtMaxLevel() {
        val (gained, cost) = EconomyFormulas.planLevelUp(currentLevel = 19, maxLevel = 20, budget = 1_000_000, requested = 10)
        assertEquals(1, gained)
        assertEquals(EconomyFormulas.levelCost(19), cost)
    }

    @Test
    fun planLevelUp_zeroWhenAlreadyMaxed() {
        val (gained, cost) = EconomyFormulas.planLevelUp(20, 20, budget = 1_000_000, requested = 5)
        assertEquals(0, gained)
        assertEquals(0, cost)
    }

    // [Theory] (0) (-1)：请求非正数是空操作
    @Test
    fun planLevelUp_noOpForNonPositiveRequest() {
        val (gained0, cost0) = EconomyFormulas.planLevelUp(1, 20, 1_000_000, 0)
        assertEquals(0, gained0)
        assertEquals(0, cost0)
        val (gainedNeg, costNeg) = EconomyFormulas.planLevelUp(1, 20, 1_000_000, -1)
        assertEquals(0, gainedNeg)
        assertEquals(0, costNeg)
    }

    @Test
    fun planLevelUp_zeroBudgetNoUpgrades() {
        val (gained, cost) = EconomyFormulas.planLevelUp(1, 20, budget = 0, requested = 5)
        assertEquals(0, gained)
        assertEquals(0, cost)
    }

    // 防止"实际扣费与显示价格不一致"这类只在长链路才暴露的漂移。
    @Test
    fun planLevelUp_costEqualsSumOfStepCosts() {
        val (gained, cost) = EconomyFormulas.planLevelUp(3, 20, budget = 100_000, requested = 6)
        var expected = 0
        for (lv in 3 until 3 + gained) expected += EconomyFormulas.levelCost(lv)
        assertEquals(expected, cost)
    }
}
