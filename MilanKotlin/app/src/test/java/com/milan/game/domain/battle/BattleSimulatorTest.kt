package com.milan.game.domain.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 战斗模拟器测试（翻译 C# BattleTests）。
 */
class BattleSimulatorTest {

    private fun u(atk: Int, def: Int, hp: Int, spd: Int) = UnitStats(atk, def, hp, spd)

    @Test
    fun strikeDamage_fullFormula() {
        assertEquals(75, BattleSimulator.strikeDamage(u(100, 50, 1, 1), u(0, 50, 1, 1)))
        assertEquals(105, BattleSimulator.strikeDamage(u(130, 50, 1, 1), u(0, 50, 1, 1)))
    }

    // atk - def/2 为负时取 1，避免 0 伤害导致战斗死循环。
    @Test
    fun strikeDamage_flooredAtOne() {
        assertEquals(1, BattleSimulator.strikeDamage(u(5, 80, 1, 1), u(0, 80, 1, 1)))
    }

    @Test
    fun simulate_strongTeamWins() {
        val team = Array(5) { u(130, 80, 1000, 12) }
        val enemy = arrayOf(u(64, 50, 220, 12))
        val r = BattleSimulator(Random(12345)).simulate(team, enemy, 50)
        assertTrue(r.victory)
        assertTrue(r.remainingHp > 0)
    }

    @Test
    fun simulate_weakTeamLoses() {
        val team = arrayOf(u(5, 10, 30, 12))
        val enemy = arrayOf(u(64, 50, 220, 12))
        val r = BattleSimulator(Random(12345)).simulate(team, enemy, 50)
        assertFalse(r.victory)
    }

    // 空敌队不可被误判为胜利（防"全灭"语义）。
    @Test
    fun simulate_emptyEnemyTeam_notFalseVictory() {
        val team = arrayOf(u(130, 80, 1000, 12))
        val r = BattleSimulator(Random(12345)).simulate(team, null, 50)
        assertFalse(r.victory)
        assertEquals(1000, r.remainingHp)
    }

    // 双方伤害恒为 1、血量极大，maxTurns 内无人阵亡 → P3-7 改为平局（draw=true）。
    @Test
    fun simulate_stalemate_hitsTurnCap() {
        val a = arrayOf(u(100, 200, 100000, 12))
        val b = arrayOf(u(100, 200, 100000, 12))
        val r = BattleSimulator(Random(12345)).simulate(a, b, 50)
        assertFalse(r.victory)
        assertTrue("回合耗尽应为平局", r.draw)
        assertEquals(50, r.turns)
        assertTrue(r.remainingHp > 0)
    }

    @Test
    fun simulate_victory_enemyHpZero() {
        val team = arrayOf(u(130, 80, 1000, 12), u(130, 80, 1000, 12))
        val enemy = arrayOf(u(64, 50, 220, 12))
        val r = BattleSimulator(Random(12345)).simulate(team, enemy, 50)
        assertTrue(r.victory)
        assertEquals(0, r.opponentRemainingHp)
    }

    // 「自动战斗」依赖该字段续接血条：若不回报敌方残血，手动打掉半血后点自动战斗敌人会被重置满血。
    @Test
    fun simulate_noVerdict_reportsRealEnemyHp() {
        val a = arrayOf(u(100, 200, 100000, 12))
        val b = arrayOf(u(100, 200, 100000, 12))
        val r = BattleSimulator(Random(12345)).simulate(a, b, 50)
        assertFalse(r.victory)
        // 掉了血但没死
        assertTrue(r.opponentRemainingHp in 1..99999)
    }

    // 传入的是"当前剩余血量"，模拟必须从该血量继续，而不是从任何上限重置。
    @Test
    fun simulate_residualHp_continues() {
        val team = arrayOf(u(200, 10, 50, 20)) // 高速高攻，一击必杀
        val enemy = arrayOf(u(1, 0, 10, 1))    // 只剩 10 血
        val r = BattleSimulator(Random(1)).simulate(team, enemy, 50)
        assertTrue(r.victory)
        assertEquals(1, r.turns)               // 残血敌人一回合内被清掉
        assertEquals(0, r.opponentRemainingHp)
    }

    @Test
    fun simulate_emptyEnemyTeam_opponentHpZero() {
        val team = arrayOf(u(130, 80, 1000, 12))
        val r = BattleSimulator(Random(1)).simulate(team, null, 50)
        assertFalse(r.victory)
        assertEquals(0, r.opponentRemainingHp)
    }

    // P3-6：maxTurns<=0 不得返回非正回合数（旧实现 `1..0` 空循环返回 turns=0）
    @Test
    fun simulate_nonPositiveMaxTurns_coercedToAtLeastOne() {
        val a = arrayOf(u(100, 200, 100000, 12))
        val b = arrayOf(u(100, 200, 100000, 12))
        val r = BattleSimulator(Random(1)).simulate(a, b, 0)
        assertFalse(r.victory)
        assertEquals(1, r.turns)

        val r2 = BattleSimulator(Random(1)).simulate(a, b, -5)
        assertEquals(1, r2.turns)
    }

    // ── 元素克制（2026-08 优化）──

    /** 高速攻方先手一击：base=100-0/2=100；守方 atk=0 只反打 1 点。 */
    private fun oneTurnRun(defenderElement: String): BattleResult {
        val attacker = UnitStats(atk = 100, def = 0, hp = 1000, spd = 100, characterId = "a", element = "Metal")
        val defender = UnitStats(atk = 0, def = 0, hp = 300, spd = 1, characterId = "b", element = defenderElement)
        return BattleSimulator(Random(7)).simulate(arrayOf(attacker), arrayOf(defender), 1)
    }

    @Test
    fun elementCounter_dealsBonusDamage() {
        // Metal 克 Wood：100×1.25=125 → 残血 175；我方被反打 1 点 → 999。
        val r = oneTurnRun("Wood")
        assertEquals(999, r.remainingHp)
        assertEquals(175, r.opponentRemainingHp)
    }

    @Test
    fun elementNeutral_noMultiplier() {
        // 同元素无克制：100×1.0=100 → 残血 200（与克制分支差值恰为 25）。
        val r = oneTurnRun("Metal")
        assertEquals(999, r.remainingHp)
        assertEquals(200, r.opponentRemainingHp)
    }

    @Test
    fun elementEmpty_treatedAsNeutral() {
        val r = oneTurnRun("")
        assertEquals(200, r.opponentRemainingHp)
    }
}
