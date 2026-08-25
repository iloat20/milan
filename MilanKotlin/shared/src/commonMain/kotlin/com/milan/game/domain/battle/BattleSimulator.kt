package com.milan.game.domain.battle

import kotlin.random.Random

/**
 * 回合制战斗模拟器（C# Milan.Domain.Battle.BattleSimulator 翻译）。
 * 2026-08 KMP 下沉：自 app 迁入 shared commonMain（已用 kotlin.random.Random，天然跨平台）。
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
        // P3-6：maxTurns<=0 会让 `1..0` 空循环并返回 turns=0 的非正结果（调用方可能除零/显示异常），
        // 钳到至少 1 回合（单回合语义 = 双方各行动一轮后结算）。
        val turns = maxTurns.coerceAtLeast(1)

        for (turn in 1..turns) {
            // 排序前先为每个存活单位预生成一次随机 tiebreaker（Spd 相同时用）。
            // 坑因（旧实现 `thenBy { rng.nextInt() }`）：比较器内每次配对比较都消耗随机数，
            // 使比较过程非传递且随机序列依赖排序算法内部比较次数——同一 seed 注入不可复现，
            // 违背「领域引擎保证确定性」的约定。预分配后每次排序恰好消费 N 个随机数。
            val order = a.filter { it.hp > 0 }
                .plus(b.filter { it.hp > 0 })
                .map { it to rng.nextInt() }
                .sortedWith(compareByDescending<Pair<S, Int>> { it.first.stats.spd }.thenBy { it.second })
                .map { it.first }

            for (actor in order) {
                if (actor.hp <= 0) continue
                val enemies = if (actor.a) b else a
                val target = enemies.filter { it.hp > 0 }.minByOrNull { it.hp }
                if (target == null || target.hp <= 0) continue
                // 伤害公式唯一事实来源：与手动出牌走同一入口；
                // 元素克制在结算点乘算（ElementChart 单一事实来源），保底 1 点防 0 伤。
                val base = strikeDamage(actor.stats, target.stats)
                val mul = ElementChart.damageMultiplier(actor.stats.element, target.stats.element)
                target.hp -= (base * mul).toInt().coerceAtLeast(1)
            }

            // 空队伍无法"全部死亡"，必须要求队伍非空，否则空 teamB 会被误判为胜利。
            if (b.isNotEmpty() && b.all { it.hp <= 0 }) return done(true, turn, a, b)
            if (a.isNotEmpty() && a.all { it.hp <= 0 }) return done(false, turn, a, b)
        }

        return done(false, turns, a, b)
    }

    private fun done(victory: Boolean, turns: Int, a: List<S>, b: List<S>): BattleResult = BattleResult(
        victory = victory,
        turns = turns,
        remainingHp = a.sumOf { it.hp.coerceAtLeast(0) },
        opponentRemainingHp = b.sumOf { it.hp.coerceAtLeast(0) },
    )

    companion object {
        /** 单体攻击结算的基础伤害（simulate 与手动出牌共用，单一事实来源）。
         * 攻方属性由 StatsCalculator 生成，已含等级/突破/天赋/升星的加成；
         * 元素克制倍率不在本函数内——由 [simulate] 结算点查 [ElementChart] 乘算。 */
        fun strikeDamage(attacker: UnitStats, defender: UnitStats): Int =
            (attacker.atk - defender.def / 2).coerceAtLeast(1)
    }

    private class S(val stats: UnitStats, var hp: Int, val a: Boolean)
}
