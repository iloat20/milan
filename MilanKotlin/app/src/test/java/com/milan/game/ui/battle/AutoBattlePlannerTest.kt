package com.milan.game.ui.battle

import com.milan.game.domain.battle.BattlePhase
import com.milan.game.domain.battle.BattleSkill
import com.milan.game.domain.battle.BattleState
import com.milan.game.domain.battle.BattleUnitState
import com.milan.game.domain.battle.EffectType
import com.milan.game.domain.battle.SkillTarget
import com.milan.game.domain.battle.SkillType
import com.milan.game.domain.battle.UnitStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自动战斗决策：残血斩杀 / 低血治疗 / 群攻优先。 */
class AutoBattlePlannerTest {

    private fun unit(
        id: String,
        atk: Int = 100,
        hp: Int = 1000,
        maxHp: Int = 1000,
        energy: Int = 100,
        skills: List<BattleSkill>,
    ): BattleUnitState = BattleUnitState(
        stats = UnitStats(atk = atk, def = 10, hp = maxHp, spd = 10, characterId = id),
        hp = hp,
        maxHp = maxHp,
        energy = energy,
        isPlayer = true,
        skills = skills,
    )

    private fun skill(
        id: String,
        power: Int = 100,
        target: SkillTarget = SkillTarget.SINGLE_ENEMY,
        cost: Int = 0,
        effects: List<com.milan.game.domain.battle.SkillEffect> = emptyList(),
        type: SkillType = SkillType.ACTIVE,
    ) = BattleSkill(
        skillId = id,
        name = id,
        description = "",
        type = type,
        energyCost = cost,
        power = power,
        target = target,
        effects = effects,
    )

    private fun state(players: List<BattleUnitState>, enemies: List<BattleUnitState>) =
        BattleState(turn = 1, phase = BattlePhase.PLAYER_INPUT, playerTeam = players, enemyTeam = enemies)

    @Test
    fun `残血敌人优先被单体高伤斩杀`() {
        val normal = skill("normal", power = 50)
        val heavy = skill("heavy", power = 200, cost = 30)
        val actor = unit("p0", atk = 100, skills = listOf(normal, heavy))
        // 残血 80 血：heavy 估伤 200 可杀
        val low = unit("e0", hp = 80, maxHp = 1000, skills = emptyList(), energy = 0).copy(isPlayer = false)
        val full = unit("e1", hp = 900, maxHp = 1000, skills = emptyList(), energy = 0).copy(isPlayer = false)
        val plan = AutoBattlePlanner.plan(actor, state(listOf(actor), listOf(full, low)))
        assertNotNull(plan)
        assertEquals("heavy", plan!!.skillId)
        assertTrue(plan.needTarget)
        assertEquals(1, plan.targetIndex)
    }

    @Test
    fun `己方低血优先治疗`() {
        val heal = skill(
            "heal",
            power = 0,
            target = SkillTarget.SINGLE_ALLY,
            cost = 20,
            effects = listOf(com.milan.game.domain.battle.SkillEffect(EffectType.HEAL, 300)),
        )
        val strike = skill("strike", power = 180, cost = 30)
        val actor = unit("p0", skills = listOf(strike, heal))
        val wounded = unit("p1", hp = 200, maxHp = 1000, skills = emptyList()).copy(isPlayer = true)
        val enemy = unit("e0", hp = 1000, skills = emptyList(), energy = 0).copy(isPlayer = false)
        val plan = AutoBattlePlanner.plan(actor, state(listOf(actor, wounded), listOf(enemy)))
        assertNotNull(plan)
        assertEquals("heal", plan!!.skillId)
        assertEquals(1, plan.targetIndex)
    }

    @Test
    fun `两名以上敌人优先群攻`() {
        val aoe = skill("aoe", power = 120, target = SkillTarget.ALL_ENEMIES, cost = 40)
        val single = skill("single", power = 90)
        val actor = unit("p0", skills = listOf(single, aoe))
        val e0 = unit("e0", hp = 800, skills = emptyList(), energy = 0).copy(isPlayer = false)
        val e1 = unit("e1", hp = 700, skills = emptyList(), energy = 0).copy(isPlayer = false)
        val plan = AutoBattlePlanner.plan(actor, state(listOf(actor), listOf(e0, e1)))
        assertNotNull(plan)
        assertEquals("aoe", plan!!.skillId)
        assertTrue(!plan.needTarget)
    }

    @Test
    fun `保守策略更早治疗`() {
        val heal = skill(
            "heal",
            power = 0,
            target = SkillTarget.SINGLE_ALLY,
            cost = 20,
            effects = listOf(com.milan.game.domain.battle.SkillEffect(EffectType.HEAL, 100)),
        )
        val strike = skill("strike", power = 150, cost = 30)
        val actor = unit("p0", skills = listOf(strike, heal))
        // 40% 血：保守(阈值 0.45)会治疗，激进(0.25)不会
        val wounded = unit("p1", hp = 400, maxHp = 1000, skills = emptyList())
        val enemy = unit("e0", hp = 900, skills = emptyList(), energy = 0).copy(isPlayer = false)
        val st = state(listOf(actor, wounded), listOf(enemy))
        val cons = AutoBattlePlanner.plan(actor, st, AutoBattleStrategy.CONSERVATIVE)
        assertEquals("heal", cons!!.skillId)
        val agg = AutoBattlePlanner.plan(actor, st, AutoBattleStrategy.AGGRESSIVE)
        assertTrue(agg == null || agg.skillId != "heal")
    }
}
