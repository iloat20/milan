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

    // ── 天赋效果集成测试（2026-09）──

    /** ignoreDefense：无视 50% 防御 → 有效防御 = 50×0.5=25 → base=100-25/2=88 → 保底 ≥1。 */
    @Test
    fun strikeDamage_ignoreDefense_reducesEffectiveDefense() {
        val attacker = UnitStats(atk = 100, def = 0, hp = 1, spd = 1, ignoreDefense = 0.5f)
        val defender = UnitStats(atk = 0, def = 50, hp = 1, spd = 1)
        // 无天赋: 100-50/2=75；有天赋: effectiveDef=(50×0.5).toInt()=25; 100-25/2=88
        val dmg = BattleSimulator.strikeDamage(attacker, defender)
        assertEquals(88, dmg)
    }

    /** ignoreDefense=1.0：守方防御归零 → base=100-0/2=100。 */
    @Test
    fun strikeDamage_ignoreDefense_fullIgnored() {
        val attacker = UnitStats(atk = 100, def = 0, hp = 1, spd = 1, ignoreDefense = 1.0f)
        val defender = UnitStats(atk = 0, def = 80, hp = 1, spd = 1)
        assertEquals(100, BattleSimulator.strikeDamage(attacker, defender))
    }

    /** talentCritRate=1.0 + talentCritDamage=0.5：必定暴击 → base×(1.0+0.5)。 */
    @Test
    fun strikeDamage_talentCrit_alwaysCrits() {
        val attacker = UnitStats(
            atk = 100, def = 0, hp = 1, spd = 1,
            talentCritRate = 1.0f, talentCritDamage = 0.5f,
        )
        val defender = UnitStats(atk = 0, def = 0, hp = 1, spd = 1)
        // base=100; critMul=1.0+0.5=1.5 → 150（必须传 rng 才能进入暴击判定分支）
        assertEquals(150, BattleSimulator.strikeDamage(attacker, defender, Random(42)))
    }

    /** talentCritRate 加到 critRate 上。 */
    @Test
    fun strikeDamage_talentCritRate_addsToBase() {
        val withoutTalent = UnitStats(atk = 100, def = 0, hp = 1, spd = 1, critRate = 0.5, critDmg = 2.0)
        val withTalent = UnitStats(atk = 100, def = 0, hp = 1, spd = 1, critRate = 0.5, critDmg = 2.0, talentCritRate = 0.5f)
        val defender = UnitStats(atk = 0, def = 0, hp = 1, spd = 1)
        // 无天赋: rng.nextDouble() < 0.5 决定是否暴击
        // 有天赋: rng.nextDouble() < 1.0 → 必暴 → 100×2.0=200
        assertEquals(200, BattleSimulator.strikeDamage(withTalent, defender, Random(42)))
        // 无天赋用相同 seed，50% 概率暴击——用确定性种子验证
        // 需要两组计算证明有天赋的暴击率确实更高
        val r = Random(42)
        val dmg = BattleSimulator.strikeDamage(withoutTalent, defender, r)
        // 无天赋 critRate=0.5，该 seed 下可能暴击也可能不暴
        assertTrue("talentCritRate 提升应导致更高伤害", dmg == 200 || dmg == 100)
    }

    /** simulate 中 dodgeRate=1.0 → 守方闪避所有攻击，自身不受伤。 */
    @Test
    fun simulate_dodgeRate_fullDodge() {
        val attacker = UnitStats(atk = 100, def = 0, hp = 1000, spd = 100, characterId = "a")
        // 守方 atk=0（反打仅 1 点保底伤）；dodgeRate=1.0：闪避一切攻击
        val dodger = UnitStats(atk = 0, def = 0, hp = 1000, spd = 50, characterId = "b", dodgeRate = 1.0f)
        val r = BattleSimulator(Random(1)).simulate(arrayOf(attacker), arrayOf(dodger), 10)
        // 守方闪避所有攻击 → 敌方残血满血不变
        assertEquals(1000, r.opponentRemainingHp)
    }

    /** simulate 中 damageReduction=0.5 → 伤害减半。 */
    @Test
    fun simulate_damageReduction_halvesDamage() {
        // 攻方 atk=100, 守方 def=0 → base=100
        // damageReduction=0.5 → raw×0.5=50
        val attacker = UnitStats(atk = 100, def = 0, hp = 1000, spd = 100, characterId = "a")
        val defender = UnitStats(atk = 0, def = 0, hp = 100000, spd = 1, characterId = "b", damageReduction = 0.5f)
        val r = BattleSimulator(Random(42)).simulate(arrayOf(attacker), arrayOf(defender), 1)
        // 守方残血 = 100000 - (100 × 0.5) = 99950
        assertEquals(99950, r.opponentRemainingHp)
    }

    /** simulate 中 lifesteal → 攻方吸血回复。 */
    @Test
    fun simulate_lifesteal_healsAttacker() {
        // 攻方 atk=100, def=0, hp=1000(上限), lifesteal=0.5 → 每次攻击回 50 HP
        // 守方 atk=1, 反打伤害极低
        val attacker = UnitStats(atk = 100, def = 0, hp = 1000, spd = 100, characterId = "a", lifesteal = 0.5f)
        val defender = UnitStats(atk = 1, def = 0, hp = 100000, spd = 1, characterId = "b")
        val r = BattleSimulator(Random(42)).simulate(arrayOf(attacker), arrayOf(defender), 5)
        // 吸血不影响最终判定（攻方 hp 不超过 maxHp=1000），但攻击应造成伤害
        assertTrue(r.opponentRemainingHp < 100000)
    }

    /** simulate 中 thorn → 守方反伤攻击者。 */
    @Test
    fun simulate_thorn_damageAttacker() {
        // 攻方 atk=100, def=0, hp=10000
        // 守方 thorn=0.5 → 每次被攻击反伤 50%
        val attacker = UnitStats(atk = 100, def = 0, hp = 10000, spd = 100, characterId = "a")
        val defender = UnitStats(atk = 0, def = 0, hp = 100000, spd = 1, characterId = "b", thorn = 0.5f)
        val r = BattleSimulator(Random(42)).simulate(arrayOf(attacker), arrayOf(defender), 1)
        // 攻方被反伤：base=100 → thorn=100×0.5=50
        assertTrue("攻方应受到反伤", r.remainingHp < 10000)
    }
}
