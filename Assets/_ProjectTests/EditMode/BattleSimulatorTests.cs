using NUnit.Framework;
using Milan.Domain.Battle;

[TestFixture]
public class BattleSimulatorTests
{
    [Test]
    public void StrongTeam_Wins_AgainstWeak()
    {
        var teamA = new[] { new UnitStats { Atk = 100, Hp = 1000, Spd = 10 } };
        var teamB = new[] { new UnitStats { Atk = 10, Hp = 100, Spd = 5 } };
        var result = new BattleSimulator(new System.Random(7)).Simulate(teamA, teamB, maxTurns: 50);
        Assert.IsTrue(result.Victory);
        Assert.Greater(result.Turns, 0);
    }

    [Test]
    public void Battle_Terminates_At_MaxTurns()
    {
        var teamA = new[] { new UnitStats { Atk = 1, Hp = 10000, Spd = 10 } };
        var teamB = new[] { new UnitStats { Atk = 1, Hp = 10000, Spd = 5 } };
        var result = new BattleSimulator(new System.Random(7)).Simulate(teamA, teamB, maxTurns: 10);
        Assert.IsFalse(result.Victory);
        Assert.AreEqual(10, result.Turns);
    }

    [Test]
    public void MultiUnit_TeamSize2_Wins()
    {
        var teamA = new[] {
            new UnitStats { Atk = 100, Hp = 500, Spd = 10 },
            new UnitStats { Atk = 100, Hp = 500, Spd = 8 }
        };
        var teamB = new[] { new UnitStats { Atk = 10, Hp = 100, Spd = 5 } };
        var result = new BattleSimulator(new System.Random(7)).Simulate(teamA, teamB, maxTurns: 50);
        Assert.IsTrue(result.Victory);
        Assert.Greater(result.RemainingHp, 0);
    }
}