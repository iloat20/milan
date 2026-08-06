using Milan.Domain.Battle;
using Xunit;

namespace Milan.Tests
{
    public class BattleTests
    {
        private static UnitStats U(int atk, int def, int hp, int spd) => new()
        {
            Atk = atk,
            Def = def,
            Hp = hp,
            Spd = spd,
        };

        [Fact]
        public void StrikeDamage_FullFormula()
        {
            Assert.Equal(75, BattleSimulator.StrikeDamage(U(100, 50, 1, 1), U(0, 50, 1, 1)));
            Assert.Equal(105, BattleSimulator.StrikeDamage(U(130, 50, 1, 1), U(0, 50, 1, 1)));
        }

        [Fact]
        public void StrikeDamage_FlooredAtOne()
        {
            // atk - def/2 为负时取 1，避免 0 伤害导致战斗死循环。
            Assert.Equal(1, BattleSimulator.StrikeDamage(U(5, 80, 1, 1), U(0, 80, 1, 1)));
        }

        [Fact]
        public void Simulate_StrongTeamWins()
        {
            var team = new[] { U(130, 80, 1000, 12), U(130, 80, 1000, 12), U(130, 80, 1000, 12), U(130, 80, 1000, 12), U(130, 80, 1000, 12) };
            var enemy = new[] { U(64, 50, 220, 12) };
            var r = new BattleSimulator(new System.Random(12345)).Simulate(team, enemy, 50);
            Assert.True(r.Victory);
            Assert.True(r.RemainingHp > 0);
        }

        [Fact]
        public void Simulate_WeakTeamLoses()
        {
            var team = new[] { U(5, 10, 30, 12) };
            var enemy = new[] { U(64, 50, 220, 12) };
            var r = new BattleSimulator(new System.Random(12345)).Simulate(team, enemy, 50);
            Assert.False(r.Victory);
        }

        [Fact]
        public void Simulate_EmptyEnemyTeam_NotFalseVictory()
        {
            // 空敌队不可被误判为胜利（防 LINQ 全死语义）。
            var team = new[] { U(130, 80, 1000, 12) };
            var r = new BattleSimulator(new System.Random(12345)).Simulate(team, System.Array.Empty<UnitStats>(), 50);
            Assert.False(r.Victory);
            Assert.Equal(1000, r.RemainingHp);
        }

        [Fact]
        public void Simulate_Stalemate_HitsTurnCap()
        {
            // 双方伤害恒为 1、血量极大，maxTurns 内无人阵亡。
            var a = new[] { U(100, 200, 100000, 12) };
            var b = new[] { U(100, 200, 100000, 12) };
            var r = new BattleSimulator(new System.Random(12345)).Simulate(a, b, 50);
            Assert.False(r.Victory);
            Assert.Equal(50, r.Turns);
            Assert.True(r.RemainingHp > 0);
        }

        [Fact]
        public void Simulate_胜利时敌方剩余血量为零()
        {
            var team = new[] { U(130, 80, 1000, 12), U(130, 80, 1000, 12) };
            var enemy = new[] { U(64, 50, 220, 12) };
            var r = new BattleSimulator(new System.Random(12345)).Simulate(team, enemy, 50);
            Assert.True(r.Victory);
            Assert.Equal(0, r.OpponentRemainingHp);
        }

        [Fact]
        public void Simulate_未分胜负时回报敌方真实残血()
        {
            // BattleActivity 的「自动战斗」依赖这个字段续接血条：
            // 若不回报敌方残血，调用方只能在"满血"和"清零"之间二选一，
            // 表现就是手动打掉半血后点自动战斗，敌人血量被重置。
            var a = new[] { U(100, 200, 100000, 12) };
            var b = new[] { U(100, 200, 100000, 12) };
            var r = new BattleSimulator(new System.Random(12345)).Simulate(a, b, 50);
            Assert.False(r.Victory);
            Assert.InRange(r.OpponentRemainingHp, 1, 99999); // 掉了血但没死
        }

        [Fact]
        public void Simulate_接续残血开局_不会凭空回满()
        {
            // 传入的是"当前剩余血量"，模拟必须从该血量继续，而不是从任何上限重置。
            var team = new[] { U(200, 10, 50, 20) };   // 高速高攻，一击必杀
            var enemy = new[] { U(1, 0, 10, 1) };      // 只剩 10 血
            var r = new BattleSimulator(new System.Random(1)).Simulate(team, enemy, 50);
            Assert.True(r.Victory);
            Assert.Equal(1, r.Turns);                  // 残血敌人一回合内被清掉
            Assert.Equal(0, r.OpponentRemainingHp);
        }

        [Fact]
        public void Simulate_空敌队时敌方剩余血量为零()
        {
            var team = new[] { U(130, 80, 1000, 12) };
            var r = new BattleSimulator(new System.Random(1)).Simulate(team, System.Array.Empty<UnitStats>(), 50);
            Assert.False(r.Victory);
            Assert.Equal(0, r.OpponentRemainingHp);
        }
    }
}
