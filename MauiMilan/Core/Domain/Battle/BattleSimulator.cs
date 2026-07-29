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
                    int dmg = Math.Max(1, actor.Stats.Atk - target.Stats.Def / 2);
                    target.Hp -= dmg;
                }

                if (b.All(x => x.Hp <= 0))
                    return new BattleResult { Victory = true, Turns = turn, RemainingHp = a.Sum(x => Math.Max(0, x.Hp)) };
                if (a.All(x => x.Hp <= 0))
                    return new BattleResult { Victory = false, Turns = turn, RemainingHp = 0 };
            }

            return new BattleResult { Victory = false, Turns = maxTurns, RemainingHp = a.Sum(x => Math.Max(0, x.Hp)) };
        }

        class S { public UnitStats Stats; public int Hp; public bool A; }
    }
}