using System;
using System.Collections.Generic;
using System.Linq;

namespace Milan.Domain.Battle
{
    public class BattleSimulator
    {
        readonly Random _rng;
        public BattleSimulator(Random rng) { _rng = rng; }

        public BattleResult Simulate(UnitStats[] teamA, UnitStats[] teamB, int maxTurns)
        {
            teamA ??= System.Array.Empty<UnitStats>();
            teamB ??= System.Array.Empty<UnitStats>();
            var a = teamA.Select(u => new S { Stats = u, Hp = u.Hp, A = true }).ToList();
            var b = teamB.Select(u => new S { Stats = u, Hp = u.Hp, A = false }).ToList();

            for (int turn = 1; turn <= maxTurns; turn++)
            {
                var order = a.Where(x => x.Hp > 0)
                    .Concat(b.Where(x => x.Hp > 0))
                    .OrderByDescending(x => x.Stats.Spd)
                    .ThenBy(x => _rng.Next())
                    .ToList();

                foreach (var actor in order)
                {
                    if (actor.Hp <= 0) continue;
                    var enemies = actor.A ? b : a;
                    var target = enemies.Where(e => e.Hp > 0).OrderBy(e => e.Hp).FirstOrDefault();
                    if (target == null || target.Hp <= 0) continue;
                    // 伤害公式唯一事实来源：与 BattleActivity 手动出牌走同一入口。
                    target.Hp -= StrikeDamage(actor.Stats, target.Stats);
                }

                // 空队伍无法"全部死亡"，必须要求队伍非空，否则空 teamB 会因 LINQ 语义被误判为胜利。
                if (b.Count > 0 && b.All(x => x.Hp <= 0))
                    return Done(true, turn, a, b);
                if (a.Count > 0 && a.All(x => x.Hp <= 0))
                    return Done(false, turn, a, b);
            }

            return Done(false, maxTurns, a, b);
        }

        static BattleResult Done(bool victory, int turns, List<S> a, List<S> b) => new BattleResult
        {
            Victory = victory,
            Turns = turns,
            RemainingHp = a.Sum(x => Math.Max(0, x.Hp)),
            OpponentRemainingHp = b.Sum(x => Math.Max(0, x.Hp)),
        };

        /// <summary>单体攻击结算伤害（Simulate 与手动出牌共用，单一事实来源）。
        /// 攻方属性由 GameState.ComputeStats 生成，已含等级/突破/天赋/升星的加成。</summary>
        public static int StrikeDamage(UnitStats attacker, UnitStats defender)
            => System.Math.Max(1, attacker.Atk - defender.Def / 2);

        class S { public UnitStats Stats; public int Hp; public bool A; }
    }
}