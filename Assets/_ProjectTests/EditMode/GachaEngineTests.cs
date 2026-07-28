using NUnit.Framework;
using Milan.Data;
using Milan.Domain.Gacha;

[TestFixture]
public class GachaEngineTests
{
    [Test]
    public void Roll_RespectsWeights()
    {
        var weights = new int[] { 820, 150, 29, 1 };
        var engine = new GachaEngine(new System.Random(123));
        int r = 0, sr = 0, ssr = 0, ur = 0;
        for (int i = 0; i < 10000; i++)
        {
            switch (engine.RollRarity(weights))
            {
                case Rarity.R: r++; break;
                case Rarity.SR: sr++; break;
                case Rarity.SSR: ssr++; break;
                case Rarity.UR: ur++; break;
            }
        }
        Assert.Greater(r, sr);
        Assert.Greater(sr, ssr);
        Assert.Greater(ssr, ur);
        Assert.Greater(ssr, 0);
        Assert.Greater(ur, 0);
    }

    [Test]
    public void HardPity_Triggers_At_Threshold()
    {
        var pity = new PityCounter(5);
        Rarity r1 = pity.RollWithPity(new System.Random(1), new int[] { 0, 0, 100, 0 }, 5);
        Rarity r5 = pity.RollWithPity(new System.Random(5), new int[] { 0, 0, 100, 0 }, 5);
        Assert.AreNotEqual(Rarity.SSR, r1);
        Assert.AreEqual(Rarity.SSR, r5);
    }
}
