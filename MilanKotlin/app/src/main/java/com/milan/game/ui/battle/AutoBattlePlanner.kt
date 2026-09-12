package com.milan.game.ui.battle

import com.milan.game.domain.battle.BattleSkill
import com.milan.game.domain.battle.BattleState
import com.milan.game.domain.battle.BattleUnitState
import com.milan.game.domain.battle.DamageEstimator
import com.milan.game.domain.battle.EffectType
import com.milan.game.domain.battle.SkillTarget

/** 自动战斗策略（设置页可调；存档字段 autoBattleStrategy）。 */
enum class AutoBattleStrategy(val id: Int) {
    /** 治疗更积极、群攻更克制。 */
    CONSERVATIVE(2),
    /** 默认：斩杀/治疗/群攻平衡。 */
    BALANCED(0),
    /** 更早开大、群攻优先于治疗（除非必死）。 */
    AGGRESSIVE(1);

    companion object {
        fun fromId(id: Int): AutoBattleStrategy =
            entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

/**
 * 自动战斗决策（2026-09-12 游戏性：残血优先击杀 / 低血保命）。
 *
 * 估伤走 [DamageEstimator]（与 StrategicBattleSimulator 同公式期望值）。
 * 优先级随 [AutoBattleStrategy] 调整阈值与技能偏好。
 */
internal object AutoBattlePlanner {

    data class Plan(
        val skillId: String,
        val needTarget: Boolean,
        val targetIndex: Int?,
    )

    fun plan(
        actor: BattleUnitState,
        state: BattleState,
        strategy: AutoBattleStrategy = AutoBattleStrategy.BALANCED,
    ): Plan? {
        val ready = actor.skills.filter { skill ->
            actor.energy >= skill.energyCost && (actor.cooldowns[skill.skillId] ?: 0) <= 0
        }
        if (ready.isEmpty()) return null

        val enemies = state.enemyTeam.mapIndexedNotNull { i, u -> if (u.hp > 0) i to u else null }
        val allies = state.playerTeam.mapIndexedNotNull { i, u -> if (u.hp > 0) i to u else null }
        if (enemies.isEmpty() && ready.none { isHeal(it) }) return null

        val healRatio = when (strategy) {
            AutoBattleStrategy.CONSERVATIVE -> 0.45f
            AutoBattleStrategy.AGGRESSIVE -> 0.25f
            AutoBattleStrategy.BALANCED -> CRITICAL_HP_RATIO
        }

        return pickHeal(ready, allies, healRatio)
            ?: pickKill(actor, ready, enemies)
            ?: pickAoe(ready, enemies, requireTwo = strategy != AutoBattleStrategy.AGGRESSIVE)
            ?: pickSingleLowest(ready, enemies)
            ?: pickFallback(ready, enemies, allies)
    }

    const val CRITICAL_HP_RATIO = 0.35f

    private fun pickHeal(
        ready: List<BattleSkill>,
        allies: List<Pair<Int, BattleUnitState>>,
        healRatio: Float,
    ): Plan? {
        val lowest = allies.minByOrNull { (_, u) -> u.hpRatio() } ?: return null
        if (lowest.second.hpRatio() >= healRatio) return null
        val heal = ready.filter { isHealSkill(it) }.maxByOrNull { healPower(it) } ?: return null
        return when (heal.target) {
            SkillTarget.ALL_ALLIES, SkillTarget.SELF -> Plan(heal.skillId, false, null)
            else -> Plan(heal.skillId, true, lowest.first)
        }
    }

    private fun pickKill(
        actor: BattleUnitState,
        ready: List<BattleSkill>,
        enemies: List<Pair<Int, BattleUnitState>>,
    ): Plan? {
        if (enemies.isEmpty()) return null
        fun canKill(sk: BattleSkill, e: BattleUnitState): Boolean =
            sk.target == SkillTarget.SINGLE_ENEMY && DamageEstimator.estimate(actor, e, sk) >= e.hp

        val killable = enemies.filter { (_, e) -> ready.any { canKill(it, e) } }
            .minByOrNull { it.second.hp } ?: return null
        val sk = ready.filter { canKill(it, killable.second) }.maxByOrNull { it.power } ?: return null
        return Plan(sk.skillId, needTarget = true, targetIndex = killable.first)
    }

    private fun pickAoe(
        ready: List<BattleSkill>,
        enemies: List<Pair<Int, BattleUnitState>>,
        requireTwo: Boolean,
    ): Plan? {
        val minEnemies = if (requireTwo) 2 else 1
        if (enemies.size < minEnemies) return null
        val aoe = ready.filter { it.target == SkillTarget.ALL_ENEMIES }.maxByOrNull { it.power } ?: return null
        return Plan(aoe.skillId, needTarget = false, targetIndex = null)
    }

    private fun pickSingleLowest(
        ready: List<BattleSkill>,
        enemies: List<Pair<Int, BattleUnitState>>,
    ): Plan? {
        if (enemies.isEmpty()) return null
        val single = ready.filter { it.target == SkillTarget.SINGLE_ENEMY }.maxByOrNull { it.power } ?: return null
        val lowest = enemies.minByOrNull { it.second.hp } ?: return null
        return Plan(single.skillId, needTarget = true, targetIndex = lowest.first)
    }

    private fun pickFallback(
        ready: List<BattleSkill>,
        enemies: List<Pair<Int, BattleUnitState>>,
        allies: List<Pair<Int, BattleUnitState>>,
    ): Plan? {
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
}