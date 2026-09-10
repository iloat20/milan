package com.milan.game.domain.battle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 策略结算胜利可信度（R6-P2）。 */
class StrategicSettleGuardTest {

    private fun strike(
        attacker: String,
        target: String,
        defeated: Boolean,
    ) = StrikeEvent(
        turn = 1,
        attackerId = attacker,
        attackerElement = "Flame",
        targetId = target,
        targetElement = "Water",
        damage = 10,
        targetDefeated = defeated,
    )

    private val players = setOf("char_a", "char_b")
    private val enemy = "enemy_x"

    @Test
    fun emptyLog_victory_rejected_defeat_ok() {
        assertFalse(StrategicSettleGuard.isVictoryPlausible(emptyList(), players, victory = true))
        assertTrue(StrategicSettleGuard.isVictoryPlausible(emptyList(), players, victory = false))
    }

    @Test
    fun lastDefeatOnEnemy_victory_ok() {
        val log = listOf(
            strike("char_a", enemy, defeated = false),
            strike("char_a", enemy, defeated = true),
        )
        assertTrue(StrategicSettleGuard.isVictoryPlausible(log, players, victory = true))
        assertFalse(StrategicSettleGuard.isVictoryPlausible(log, players, victory = false))
    }

    @Test
    fun lastDefeatOnPlayer_defeat_ok_victory_rejected() {
        val log = listOf(
            strike(enemy, "char_a", defeated = false),
            strike(enemy, "char_a", defeated = true),
        )
        assertTrue(StrategicSettleGuard.isVictoryPlausible(log, players, victory = false))
        assertFalse(StrategicSettleGuard.isVictoryPlausible(log, players, victory = true))
    }

    @Test
    fun noDefeatInLog_both_rejected() {
        val log = listOf(strike("char_a", enemy, defeated = false))
        assertFalse(StrategicSettleGuard.isVictoryPlausible(log, players, victory = true))
        assertFalse(StrategicSettleGuard.isVictoryPlausible(log, players, victory = false))
    }
}
