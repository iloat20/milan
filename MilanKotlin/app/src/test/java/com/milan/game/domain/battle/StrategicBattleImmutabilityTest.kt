package com.milan.game.domain.battle

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 策略战斗状态不可变性回归测试（2026-09-08 P0-3）。
 *
 * ## 缺陷（重构前的行为）
 * [StrategicBattleSimulator.executePlayerAction] / [executeEnemyTurn] 名义上返回新的
 * [BattleState]（函数式签名），实际却在 `state.copy(log = ...)` **之前就地 mutate 了入参内部的
 * 单位对象**：`actor.energy -= `、`target.hp -= `、`actor.cooldowns[id] = `、
 * `newEnemyTeam = state.enemyTeam.toMutableList()`（浅拷贝，元素仍是同一批对象）。
 *
 * 由于 [BattleState] 是 data class 而 [BattleUnitState] 含 `var hp` / `MutableMap` /
 * `MutableList`，`copy()` 只是浅拷贝——返回值与入参共享同一批已变异的单位。后果：
 *   1. 调用方持有的旧状态被静默污染，无法做撤销 / 战斗回放 / AI 预演；
 *   2. 同一份状态两次执行结果不同（第二次基于被改写的 hp / energy）。
 *
 * 本测试在重构前为**红**（断言入参不被污染），重构为深不可变后转绿。
 */
class StrategicBattleImmutabilityTest {

    private fun unit(id: String, isPlayer: Boolean, hp: Int = 1000, energy: Int = 100) =
        BattleUnitState(
            stats = UnitStats(atk = 100, def = 50, hp = hp, spd = 100, characterId = id, element = ""),
            hp = hp,
            maxHp = hp,
            energy = energy,
            isPlayer = isPlayer,
            skills = listOf(
                BattleSkill(
                    skillId = "s1",
                    name = "strike",
                    description = "",
                    type = SkillType.NORMAL,
                    energyCost = 10,
                    cooldown = 2,
                    power = 100,
                    target = SkillTarget.SINGLE_ENEMY,
                ),
            ),
        )

    private fun buildState(): BattleState = BattleState(
        turn = 1,
        phase = BattlePhase.PLAYER_INPUT,
        playerTeam = listOf(unit("p0", isPlayer = true)),
        enemyTeam = listOf(unit("e0", isPlayer = false)),
        log = emptyList(),
    )

    private val action = PlayerAction(actorIndex = 0, skillId = "s1", targetIndex = 0)

    // ───────────── 核心：入参不得被污染 ─────────────

    @Test
    fun executePlayerAction_doesNotMutateInputState() {
        val state = buildState()
        val actor = state.playerTeam[0]
        val target = state.enemyTeam[0]
        val energyBefore = actor.energy
        val enemyHpBefore = target.hp
        val cooldownsBefore = actor.cooldowns.size

        val result = StrategicBattleSimulator(Random(1)).executePlayerAction(state, action)

        // 效果必须体现在返回值上（否则说明根本没执行）
        assertTrue("返回的敌方 hp 应下降", result.enemyTeam[0].hp < enemyHpBefore)
        assertEquals("返回的 actor 应扣能量", energyBefore - 10, result.playerTeam[0].energy)

        // 同时入参必须原封不动
        assertEquals("入参 actor.energy 被就地改写", energyBefore, state.playerTeam[0].energy)
        assertEquals("入参 enemy.hp 被就地改写", enemyHpBefore, state.enemyTeam[0].hp)
        assertEquals("入参 actor.cooldowns 被就地改写", cooldownsBefore, state.playerTeam[0].cooldowns.size)
    }

    @Test
    fun executeEnemyTurn_doesNotMutateInputState() {
        val state = buildState()
        val playerHpBefore = state.playerTeam[0].hp
        val enemyEnergyBefore = state.enemyTeam[0].energy

        val result = StrategicBattleSimulator(Random(1)).executeEnemyTurn(state)

        assertTrue("返回的玩家 hp 应下降", result.playerTeam[0].hp < playerHpBefore)
        assertEquals("入参 player.hp 被就地改写", playerHpBefore, state.playerTeam[0].hp)
        assertEquals("入参 enemy.energy 被就地改写", enemyEnergyBefore, state.enemyTeam[0].energy)
    }

    // ───────────── 同状态两次执行结果必须一致 ─────────────

    @Test
    fun sameInputYieldsSameResult() {
        val r1 = StrategicBattleSimulator(Random(7)).executePlayerAction(buildState(), action)
        val r2 = StrategicBattleSimulator(Random(7)).executePlayerAction(buildState(), action)

        assertEquals("同 seed 下 playerTeam 应一致", r1.playerTeam, r2.playerTeam)
        assertEquals("同 seed 下 enemyTeam 应一致", r1.enemyTeam, r2.enemyTeam)
        assertEquals("同 seed 下日志应一致", r1.log.size, r2.log.size)
    }

    // ───────────── 能量/冷却门禁（既有行为锁定，防止重构改坏） ─────────────

    @Test
    fun rejectsActionWhenEnergyInsufficient() {
        val state = buildState().copy(
            playerTeam = listOf(unit("p0", isPlayer = true, energy = 5)),
        )
        val result = StrategicBattleSimulator(Random(1)).executePlayerAction(state, action)
        assertEquals("能量不足时不应改变 hp", state.enemyTeam[0].hp, result.enemyTeam[0].hp)
    }

    @Test
    fun rejectsActionWhenSkillOnCooldown() {
        // 不可变后不能就地写 cooldowns，改为构造期预置
        val state = buildState().copy(
            playerTeam = listOf(unit("p0", isPlayer = true).copy(cooldowns = mapOf("s1" to 3))),
        )
        val result = StrategicBattleSimulator(Random(1)).executePlayerAction(state, action)
        assertEquals("冷却中不应改变 hp", state.enemyTeam[0].hp, result.enemyTeam[0].hp)
    }
}
