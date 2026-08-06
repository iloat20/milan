using Milan.Domain.Progression;
using Xunit;

namespace Milan.Tests;

/// <summary>
/// 经济公式是养成与抽卡的唯一事实来源，任何改动都会直接影响存档里的资源余额。
/// 这里锁死口径与边界（非法输入被钳制、批量升级在预算/上限处正确截断）。
/// </summary>
public class EconomyFormulasTests
{
    [Theory]
    [InlineData(1, 20)]
    [InlineData(4, 80)]
    [InlineData(0, 20)]   // stage 非法值被钳到 1，不能返回 0 级上限（否则永远升不了级）
    [InlineData(-5, 20)]
    public void MaxLevelForStage_钳制非法阶段(int stage, int expected)
        => Assert.Equal(expected, EconomyFormulas.MaxLevelForStage(stage));

    [Theory]
    [InlineData(1, 50)]
    [InlineData(10, 500)]
    [InlineData(0, 50)]   // level 非法值被钳到 1，绝不能出现 0 成本（可无限白嫖升级）
    [InlineData(-3, 50)]
    public void LevelCost_随等级线性且不为零(int level, int expected)
        => Assert.Equal(expected, EconomyFormulas.LevelCost(level));

    [Theory]
    [InlineData(1, 20, 500)]
    [InlineData(3, 60, 1500)]
    [InlineData(0, 20, 500)]
    public void 突破消耗_碎片与星尘同阶递增(int stage, int frag, int soft)
    {
        Assert.Equal(frag, EconomyFormulas.AscendFragments(stage));
        Assert.Equal(soft, EconomyFormulas.AscendSoft(stage));
    }

    [Theory]
    [InlineData(1, 20)]
    [InlineData(5, 100)]
    [InlineData(0, 20)]
    public void StarUpFragments_按当前星数递增(int stars, int expected)
        => Assert.Equal(expected, EconomyFormulas.StarUpFragments(stars));

    [Theory]
    [InlineData(4, 50)]   // UR
    [InlineData(3, 20)]   // SSR
    [InlineData(2, 5)]    // SR
    [InlineData(1, 1)]    // R
    [InlineData(99, 1)]   // 未知稀有度必须回退到最低补偿，不能抛异常中断抽卡事务
    [InlineData(0, 1)]
    public void FragmentsForRarity_覆盖全稀有度并有兜底(int rarity, int expected)
        => Assert.Equal(expected, EconomyFormulas.FragmentsForRarity(rarity));

    [Fact]
    public void CumulativeExp_与ExpForLevel口径一致()
    {
        // 累计经验必须恰好等于逐级经验之和，否则经验条会与实际升级点错位。
        for (int lv = 1; lv <= 30; lv++)
        {
            int sum = 0;
            for (int k = 1; k < lv; k++) sum += EconomyFormulas.ExpForLevel(k);
            Assert.Equal(sum, EconomyFormulas.CumulativeExp(lv));
        }
    }

    [Fact]
    public void CumulativeExp_一级为零()
    {
        Assert.Equal(0, EconomyFormulas.CumulativeExp(1));
        Assert.Equal(0, EconomyFormulas.CumulativeExp(0));
        Assert.Equal(0, EconomyFormulas.CumulativeExp(-9));
    }

    [Fact]
    public void PlanLevelUp_预算充足时按请求级数升满()
    {
        // 1→4 级：50 + 100 + 150 = 300
        var (gained, cost) = EconomyFormulas.PlanLevelUp(currentLevel: 1, maxLevel: 20, budget: 10_000, requested: 3);
        Assert.Equal(3, gained);
        Assert.Equal(300, cost);
    }

    [Fact]
    public void PlanLevelUp_预算不足时只升到付得起的那一级()
    {
        // 预算 120：能付 50（1→2），付不起接下来的 100 → 只升 1 级、只扣 50。
        var (gained, cost) = EconomyFormulas.PlanLevelUp(1, 20, budget: 120, requested: 5);
        Assert.Equal(1, gained);
        Assert.Equal(50, cost);
    }

    [Fact]
    public void PlanLevelUp_触到等级上限即停止扣费()
    {
        var (gained, cost) = EconomyFormulas.PlanLevelUp(currentLevel: 19, maxLevel: 20, budget: 1_000_000, requested: 10);
        Assert.Equal(1, gained);
        Assert.Equal(EconomyFormulas.LevelCost(19), cost);
    }

    [Fact]
    public void PlanLevelUp_已满级返回零且不扣费()
    {
        var (gained, cost) = EconomyFormulas.PlanLevelUp(20, 20, budget: 1_000_000, requested: 5);
        Assert.Equal(0, gained);
        Assert.Equal(0, cost);
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    public void PlanLevelUp_请求非正数是空操作(int requested)
    {
        var (gained, cost) = EconomyFormulas.PlanLevelUp(1, 20, 1_000_000, requested);
        Assert.Equal(0, gained);
        Assert.Equal(0, cost);
    }

    [Fact]
    public void PlanLevelUp_零预算不产生任何升级()
    {
        var (gained, cost) = EconomyFormulas.PlanLevelUp(1, 20, budget: 0, requested: 5);
        Assert.Equal(0, gained);
        Assert.Equal(0, cost);
    }

    [Fact]
    public void PlanLevelUp_花费恰好等于逐级成本之和()
    {
        // 防止"实际扣费与显示价格不一致"这类只在长链路才暴露的漂移。
        var (gained, cost) = EconomyFormulas.PlanLevelUp(3, 20, budget: 100_000, requested: 6);
        int expected = 0;
        for (int lv = 3; lv < 3 + gained; lv++) expected += EconomyFormulas.LevelCost(lv);
        Assert.Equal(expected, cost);
    }
}
