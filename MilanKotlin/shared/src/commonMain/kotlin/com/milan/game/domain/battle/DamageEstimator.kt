package com.milan.game.domain.battle

/**
 * 策略战斗伤害预估（2026-09-12）。
 *
 * 与 [StrategicBattleSimulator] 实战 `calculateDamage` 同一公式的**确定性期望值**版：
 * 暴击按 E[crit] = 1 + critRate×(critDmg−1)，不掷骰。供自动战斗斩杀判定与 UI 预览共用。
 */
object DamageEstimator {

    /**
     * 预估 [attacker] 用 [skill] 打 [defender] 的期望伤害（≥1）。
     * 公式对齐 StrategicBattleSimulator.calculateDamage（去随机）。
     */
    fun estimate(
        attacker: BattleUnitState,
        defender: BattleUnitState,
        skill: BattleSkill,
    ): Int {
        val baseDamage = (attacker.stats.atk * skill.power / 100).coerceAtLeast(1)
        val effectiveDef = (defender.stats.def * (1f - attacker.stats.ignoreDefense)).toInt()
        val damage = (baseDamage - effectiveDef / 2).coerceAtLeast(1)
        val elementMultiplier = ElementChart.damageMultiplier(
            attacker.stats.element,
            defender.stats.element,
        )
        val totalCritRate = (attacker.stats.critRate + attacker.stats.talentCritRate)
            .coerceIn(0.0, 1.0)
        val totalCritDmg = (attacker.stats.critDmg + attacker.stats.talentCritDamage)
            .coerceAtLeast(1.0)
        val expectedCritMul = 1.0 + totalCritRate * (totalCritDmg - 1.0)
        return (damage * elementMultiplier * expectedCritMul).toInt().coerceAtLeast(1)
    }
}
