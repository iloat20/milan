package com.milan.game.domain.battle

import org.junit.Assert.assertTrue
import org.junit.Test

/** 伤害预估与实战公式同源（期望暴击）。 */
class DamageEstimatorTest {

    private fun unit(
        atk: Int,
        def: Int = 10,
        element: String = "Flame",
        critRate: Double = 0.0,
        critDmg: Double = 1.0,
    ) = BattleUnitState(
        stats = UnitStats(atk = atk, def = def, hp = 1000, spd = 10, element = element, critRate = critRate, critDmg = critDmg),
        hp = 1000,
        maxHp = 1000,
        isPlayer = true,
    )

    private fun skill(power: Int) = BattleSkill(
        skillId = "s",
        name = "s",
        description = "",
        type = SkillType.ACTIVE,
        power = power,
    )

    @Test
    fun `高攻打低防伤害高于低攻`() {
        val high = DamageEstimator.estimate(unit(atk = 200), unit(atk = 50), skill(100))
        val low = DamageEstimator.estimate(unit(atk = 50), unit(atk = 50), skill(100))
        assertTrue(high > low)
    }

    @Test
    fun `暴击期望抬高预估`() {
        val noCrit = DamageEstimator.estimate(unit(atk = 100, critRate = 0.0), unit(atk = 1), skill(150))
        val withCrit = DamageEstimator.estimate(unit(atk = 100, critRate = 1.0, critDmg = 2.0), unit(atk = 1), skill(150))
        assertTrue(withCrit > noCrit)
    }
}
