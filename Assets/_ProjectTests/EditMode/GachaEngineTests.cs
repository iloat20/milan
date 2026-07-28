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
        // Natural roll is always R; pity forces SSR (rarity 3) at the threshold.
        var pity = new PityCounter(5);
        Rarity r1 = pity.RollWithPity(new System.Random(1), new int[] { 100, 0, 0, 0 }, 3);
        Rarity r2 = pity.RollWithPity(new System.Random(2), new int[] { 100, 0, 0, 0 }, 3);
        Rarity r3 = pity.RollWithPity(new System.Random(3), new int[] { 100, 0, 0, 0 }, 3);
        Rarity r4 = pity.RollWithPity(new System.Random(4), new int[] { 100, 0, 0, 0 }, 3);
        Rarity r5 = pity.RollWithPity(new System.Random(5), new int[] { 100, 0, 0, 0 }, 3);
        Assert.AreEqual(Rarity.R, r1);
        Assert.AreEqual(Rarity.R, r2);
        Assert.AreEqual(Rarity.R, r3);
        Assert.AreEqual(Rarity.R, r4);
        Assert.AreEqual(Rarity.SSR, r5);   // forced at threshold
    }
}
