package com.milan.game.domain.progression

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 经济公式属性测试（2026-09-12 Wave 1）。
 *
 * 锁住 EconomyFormulas 不变量：非负、单调、预算不超支、累计经验与逐级和一致。
 * 与示例测互补——随机边界比手写用例更能抓 clamp/溢出回归。
 */
class EconomyFormulasPropertyTest {

    private val levels = Arb.int(1..200)
    private val stages = Arb.int(1..8)
    private val stars = Arb.int(1..6)
    private val budgets = Arb.int(0..200_000)
    private val requests = Arb.int(-5..50)

    @Test
    fun `levelCost 正且随等级单调不减`() {
        runBlocking {
            checkAll(levels, levels) { a, b ->
                val ca = EconomyFormulas.levelCost(a)
                val cb = EconomyFormulas.levelCost(b)
                assertTrue("levelCost($a)=$ca 应 >0", ca > 0)
                if (b > a) {
                    assertTrue("levelCost 应单调: $a->$b", cb >= ca)
                }
            }
        }
    }

    @Test
    fun `planLevelUp 不超支且不超上限`() {
        runBlocking {
            checkAll(levels, budgets, requests) { current, budget, requested ->
                val max = EconomyFormulas.maxLevelForStage(1) // 20
                val plan = EconomyFormulas.planLevelUp(current, max, budget, requested)
                assertTrue("实际升级数 >=0", plan.gained >= 0)
                assertTrue("花费 >=0", plan.cost >= 0)
                assertTrue("花费不得超预算", plan.cost <= budget)
                assertTrue("不得升过上限", current + plan.gained <= maxOf(current, max))
                if (requested <= 0) {
                    assertEquals(0, plan.gained)
                    assertEquals(0, plan.cost)
                }
            }
        }
    }

    @Test
    fun `累计经验等于逐级 expForLevel 之和`() {
        runBlocking {
            checkAll(levels) { level ->
                var sum = 0
                for (lv in 1 until level) {
                    sum += EconomyFormulas.expForLevel(lv)
                }
                assertEquals(sum, EconomyFormulas.cumulativeExp(level))
            }
        }
    }

    @Test
    fun `突破与升星消耗为正且随阶段不减`() {
        runBlocking {
            checkAll(stages, stages) { a, b ->
                assertTrue(EconomyFormulas.ascendFragments(a) > 0)
                assertTrue(EconomyFormulas.ascendSoft(a) > 0)
                assertTrue(EconomyFormulas.starUpFragments(a) > 0)
                if (b > a) {
                    assertTrue(EconomyFormulas.ascendFragments(b) >= EconomyFormulas.ascendFragments(a))
                    assertTrue(EconomyFormulas.starUpFragments(b) >= EconomyFormulas.starUpFragments(a))
                }
            }
        }
    }

    @Test
    fun `重复补偿碎片恒正`() {
        runBlocking {
            checkAll(Arb.int(-3..10)) { rarity ->
                assertTrue(EconomyFormulas.fragmentsForRarity(rarity) > 0)
            }
        }
    }
}
