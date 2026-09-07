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
        // 战报事件流：只追加记录，不影响任何随机数消耗与结算路径（确定性断言保持可复现）。
        val log = mutableListOf<StrikeEvent>()

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
                // 元素克制在结算点乘算（ElementChart 单一事实来源），暴击在 strikeDamage 内乘算，保底 1 点防 0 伤。
                val base = strikeDamage(actor.stats, target.stats, rng)
                val mul = ElementChart.damageMultiplier(actor.stats.element, target.stats.element)
                // 闪避判定：守方 dodgeRate 掷骰，命中则伤害归零
                val dodged = rng.nextDouble() < target.stats.dodgeRate.toDouble().coerceIn(0.0, 1.0)
                val rawDamage = (base * mul).toInt().coerceAtLeast(1)
                // 伤害减免：守方 damageReduction 在最终伤害上百分比削减
                val damage = if (dodged) 0 else (rawDamage * (1f - target.stats.damageReduction)).toInt().coerceAtLeast(1)
                target.hp -= damage
                // 吸血：攻方按 lifesteal 比例回复生命（不超过最大 HP）
                if (!dodged && damage > 0 && actor.stats.lifesteal > 0f) {
                    val heal = (damage * actor.stats.lifesteal).toInt()
                    actor.hp = (actor.hp + heal).coerceAtMost(actor.stats.hp)
                }
                // 反伤：守方 thorn 按比例对攻方造成伤害（吸血和反伤互不影响最终 HP）
                if (!dodged && damage > 0 && target.stats.thorn > 0f) {
                    val thornDmg = (damage * target.stats.thorn).toInt().coerceAtLeast(1)
                    actor.hp -= thornDmg
                }
                log += StrikeEvent(
                    turn = turn,
                    attackerId = actor.stats.characterId,
                    attackerElement = actor.stats.element,
                    targetId = target.stats.characterId,
                    targetElement = target.stats.element,
                    damage = damage,
                    targetDefeated = target.hp <= 0,
                )
            }

            // 空队伍无法"全部死亡"，必须要求队伍非空，否则空 teamB 会被误判为胜利。
            if (b.isNotEmpty() && b.all { it.hp <= 0 }) return done(true, turn, a, b, log)
            if (a.isNotEmpty() && a.all { it.hp <= 0 }) return done(false, turn, a, b, log)
        }

        // P3-7：回合耗尽双方仍存活 → 平局（此前误判为负，消耗门票且无奖励）；
        // 一方为空（防空敌队/己方全灭被前面拦截后）不视为平局。
        val bothAlive = a.any { it.hp > 0 } && b.any { it.hp > 0 }
        return done(victory = false, turns, a, b, log, draw = bothAlive)
    }

    private fun done(
        victory: Boolean,
        turns: Int,
        a: List<S>,
        b: List<S>,
        log: List<StrikeEvent>,
        draw: Boolean = false,
    ): BattleResult = BattleResult(
        victory = victory,
        turns = turns,
        remainingHp = a.sumOf { it.hp.coerceAtLeast(0) },
        opponentRemainingHp = b.sumOf { it.hp.coerceAtLeast(0) },
        log = log.toList(),
        draw = draw,
    )

    companion object {
        /** 单体攻击结算的基础伤害（simulate 与手动出牌共用，单一事实来源）。
         * 攻方属性由 StatsCalculator 生成，已含等级/突破/天赋/升星的加成；
         * 元素克制倍率不在本函数内——由 [simulate] 结算点查 [ElementChart] 乘算。
         *
         * 暴击判定：攻击方 (critRate + talentCritRate) 随机 → 命中则伤害乘 (critDmg + talentCritDamage)。
         * 无视防御：攻击方 ignoreDefense 在防御计算前降低守方有效防御。 */
        fun strikeDamage(attacker: UnitStats, defender: UnitStats, rng: Random? = null): Int {
            // 无视防御：有效防御 = 原防御 × (1 - ignoreDefense)
            val effectiveDef = (defender.def * (1f - attacker.ignoreDefense)).toInt()
            val base = (attacker.atk - effectiveDef / 2).coerceAtLeast(1)
            // 暴击判定：天赋暴击率叠加到基础暴击率
            val totalCritRate = attacker.critRate + attacker.talentCritRate.toDouble()
            val totalCritDmg = attacker.critDmg + attacker.talentCritDamage
            val critMul = if (rng != null && totalCritRate > 0.0) {
                if (rng.nextDouble() < totalCritRate.coerceIn(0.0, 1.0)) totalCritDmg.coerceAtLeast(1.0) else 1.0
            } else {
                1.0
            }
            return (base * critMul).toInt().coerceAtLeast(1)
        }
    }

    private class S(val stats: UnitStats, var hp: Int, val a: Boolean)
}
