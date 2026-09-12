package com.milan.game.ui.battle

import com.milan.game.domain.battle.BattleSkill
import com.milan.game.domain.battle.BattleState
import com.milan.game.domain.battle.BattleUnitState
import com.milan.game.domain.battle.EffectType
import com.milan.game.domain.battle.SkillTarget

/**
 * 自动战斗决策（2026-09-12 游戏性：残血优先击杀 / 低血保命）。
 *
 * 纯函数，便于 JVM 单测。优先级：
 * 1. 己方最低血 < [CRITICAL_HP_RATIO] 且有治疗 → 治疗该队友
 * 2. 体技可击杀最低血敌人 → 斩杀
 * 3. 存活敌 ≥2 且有全体伤害 → 群攻
 * 4. 最高 power 单体 → 打最低血敌人
 * 5. 其余可用技能兜底
 */
internal object AutoBattlePlanner {

    const val CRITICAL_HP_RATIO = 0.35f

    data class Plan(
        val skillId: String,
        val needTarget: Boolean,
        val targetIndex: Int?,
    )

    fun plan(actor: BattleUnitState, state: BattleState): Plan? {
        val ready = actor.skills.filter { skill ->
            actor.energy >= skill.energyCost && (actor.cooldowns[skill.skillId] ?: 0) <= 0
        }
        if (ready.isEmpty()) return null

        val enemies = state.enemyTeam.mapIndexedNotNull { i, u -> if (u.hp > 0) i to u else null }
        val allies = state.playerTeam.mapIndexedNotNull { i, u -> if (u.hp > 0) i to u else null }
        if (enemies.isEmpty() && ready.none { isHeal(it) }) return null

        val lowestAlly = allies.minByOrNull { (_, u) -> u.hpRatio() }
        val criticalAlly = lowestAlly != null && lowestAlly.second.hpRatio() < CRITICAL_HP_RATIO

        // ── 1) 保命治疗 ──
        if (criticalAlly && lowestAlly != null) {
            val heal = ready
                .filter { isHealSkill(it) }
                .maxByOrNull { healPower(it) }
            if (heal != null) {
                return when (heal.target) {
                    SkillTarget.SINGLE_ALLY -> Plan(heal.skillId, true, lowestAlly.first)
                    SkillTarget.ALL_ALLIES, SkillTarget.SELF -> Plan(heal.skillId, false, null)
                    else -> Plan(heal.skillId, true, lowestAlly.first)
                }
            }
        }

        // ── 2) 斩杀：单体伤害能带走最低血敌人 ──
        if (enemies.isNotEmpty()) {
            val killable = enemies
                .filter { (idx, e) ->
                    ready.any { sk ->
                        sk.target == SkillTarget.SINGLE_ENEMY && estimateDamage(actor, sk) >= e.hp
                    } && e.hp > 0
                }
                .minByOrNull { it.second.hp }
            if (killable != null) {
                val sk = ready
                    .filter {
                        it.target == SkillTarget.SINGLE_ENEMY &&
                            estimateDamage(actor, it) >= killable.second.hp
                    }
                    .maxByOrNull { it.power }!!
                return Plan(sk.skillId, needTarget = true, targetIndex = killable.first)
            }
        }

        // ── 3) 群攻（≥2 存活敌人）──
        if (enemies.size >= 2) {
            val aoe = ready.filter { it.target == SkillTarget.ALL_ENEMIES }.maxByOrNull { it.power }
            if (aoe != null) return Plan(aoe.skillId, needTarget = false, targetIndex = null)
        }

        // ── 4) 单体最高 power → 打最低血 ──
        if (enemies.isNotEmpty()) {
            val single = ready
                .filter { it.target == SkillTarget.SINGLE_ENEMY }
                .maxByOrNull { it.power }
            if (single != null) {
                val lowestEnemy = enemies.minByOrNull { it.second.hp }!!
                return Plan(single.skillId, needTarget = true, targetIndex = lowestEnemy.first)
            }
        }

        // ── 5) 兜底 ──
        val fallback = ready.maxByOrNull { it.power } ?: return null
        val need = fallback.target == SkillTarget.SINGLE_ENEMY || fallback.target == SkillTarget.SINGLE_ALLY
        val idx = when (fallback.target) {
            SkillTarget.SINGLE_ENEMY -> enemies.minByOrNull { it.second.hp }?.first ?: 0
            SkillTarget.SINGLE_ALLY -> allies.minByOrNull { it.second.hpRatio() }?.first ?: 0
            else -> null
        }
        return Plan(fallback.skillId, needTarget = need, targetIndex = if (need) idx else null)
    }

    private fun BattleUnitState.hpRatio(): Float =
        if (maxHp <= 0) 0f else hp.toFloat() / maxHp

    private fun isHeal(skill: BattleSkill): Boolean =
        skill.effects.any { it.type == EffectType.HEAL } ||
            skill.target == SkillTarget.SINGLE_ALLY ||
            skill.target == SkillTarget.ALL_ALLIES

    private fun isHealSkill(skill: BattleSkill): Boolean =
        skill.effects.any { it.type == EffectType.HEAL } ||
            skill.target == SkillTarget.SINGLE_ALLY ||
            skill.target == SkillTarget.ALL_ALLIES ||
            skill.target == SkillTarget.SELF

    private fun healPower(skill: BattleSkill): Int =
        skill.effects.filter { it.type == EffectType.HEAL }.sumOf { it.value } + skill.power / 10

    /** 粗估单次伤害：atk×power%（忽略防御/克制，仅作斩杀阈值）。 */
    private fun estimateDamage(actor: BattleUnitState, skill: BattleSkill): Int {
        val base = actor.stats.atk.toLong() * skill.power / 100L
        return base.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }
}
