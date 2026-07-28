using NUnit.Framework;
using Milan.Domain.Progression;
using System.Collections.Generic;

[TestFixture]
public class ProgressionEngineTests
{
    [Test]
    public void ExpToLevel_Clamps()
    {
        var engine = new ProgressionEngine();
        Assert.AreEqual(1, engine.ExpToLevel(0));
        Assert.AreEqual(2, engine.ExpToLevel(100));
    }

    [Test]
    public void Talent_CanAllocate_WithPrereqs()
    {
        var engine = new TalentEngine();
        var prereqs = new Dictionary<string, string[]>
        {
            ["n1"] = new string[0],
            ["n2"] = new[] { "n1" }
        };
        Assert.IsTrue(engine.CanAllocate("n1", new List<string>(), prereqs));
        Assert.IsFalse(engine.CanAllocate("n2", new List<string> { "n0" }, prereqs));
        Assert.IsTrue(engine.CanAllocate("n2", new List<string> { "n1" }, prereqs));
    }

    [Test]
    public void Talent_TotalPoints_MatchesTree()
    {
        var engine = new TalentEngine();
        var costs = new Dictionary<string, int> { ["n1"] = 1, ["n2"] = 2, ["n3"] = 3 };
        Assert.AreEqual(6, engine.TotalPoints(costs));
    }
}
