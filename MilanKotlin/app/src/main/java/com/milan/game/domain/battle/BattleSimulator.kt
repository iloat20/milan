package com.milan.game.domain.battle

import kotlin.random.Random

/**
 * 回合制战斗模拟器（C# Milan.Domain.Battle.BattleSimulator 翻译）。
 */
class BattleSimulator(private val rng: Random) {

    /**
     * 模拟 [maxTurns] 回合的战斗：
     * - 行动顺序：存活单位按 Spd 降序，同速随机（rng）决定先后；
     * - 目标选择：存活敌人中剩余血量最少者（先集火残血）；
     * - 空队伍不参与胜负判定（防空敌队被误判为胜利）；
     * - 回合数耗尽未分胜负按失败返回。
     */
    fun simulate(teamA: Array<UnitStats>?, teamB: Array<UnitStats>?, maxTurns: Int): BattleResult {
        val a = (teamA ?: emptyArray()).map { S(it, it.hp, true) }
        val b = (teamB ?: emptyArray()).map { S(it, it.hp, false) }

        for (turn in 1..maxTurns) {
            val order = a.filter { it.hp > 0 }
                .plus(b.filter { it.hp > 0 })
                .sortedWith(compareByDescending<S> { it.stats.spd }.thenBy { rng.nextInt() })

            for (actor in order) {
                if (actor.hp <= 0) continue
                val enemies = if (actor.a) b else a
                val target = enemies.filter { it.hp > 0 }.minByOrNull { it.hp }
                if (target == null || target.hp <= 0) continue
                // 伤害公式唯一事实来源：与手动出牌走同一入口。
                target.hp -= strikeDamage(actor.stats, target.stats)
            }

            // 空队伍无法"全部死亡"，必须要求队伍非空，否则空 teamB 会被误判为胜利。
            if (b.isNotEmpty() && b.all { it.hp <= 0 }) return done(true, turn, a, b)
            if (a.isNotEmpty() && a.all { it.hp <= 0 }) return done(false, turn, a, b)
        }

        return done(false, maxTurns, a, b)
    }

    private fun done(victory: Boolean, turns: Int, a: List<S>, b: List<S>): BattleResult = BattleResult(
        victory = victory,
        turns = turns,
        remainingHp = a.sumOf { it.hp.coerceAtLeast(0) },
        opponentRemainingHp = b.sumOf { it.hp.coerceAtLeast(0) },
    )

    companion object {
        /** 单体攻击结算伤害（simulate 与手动出牌共用，单一事实来源）。
         * 攻方属性由 GameState.ComputeStats 生成，已含等级/突破/天赋/升星的加成。 */
        fun strikeDamage(attacker: UnitStats, defender: UnitStats): Int =
            (attacker.atk - defender.def / 2).coerceAtLeast(1)
    }

    private class S(val stats: UnitStats, var hp: Int, val a: Boolean)
}
