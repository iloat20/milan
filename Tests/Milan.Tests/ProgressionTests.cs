using Milan.Domain.Progression;
using Xunit;

namespace Milan.Tests
{
    public class ProgressionTests
    {
        private readonly ProgressionEngine _engine = new();

        [Fact]
        public void ExpToLevel_ZeroExp_IsLevel1()
        {
            Assert.Equal(1, _engine.ExpToLevel(0));
        }

        [Theory]
        [InlineData(100, 2)]
        [InlineData(299, 2)]
        [InlineData(300, 3)]
        [InlineData(600, 4)]
        [InlineData(1000, 5)]
        public void ExpToLevel_MatchesCumulativeCost(int exp, int expectedLevel)
        {
            Assert.Equal(expectedLevel, _engine.ExpToLevel(exp));
        }

        [Fact]
        public void ExpToLevel_RoundTrip()
        {
            // 到达等级 L 所需累计经验 = 50*(L-1)*L（每级 k→k+1 增量 k*100）。
            for (int L = 1; L <= 20; L++)
            {
                int need = 50 * (L - 1) * L;
                Assert.Equal(L, _engine.ExpToLevel(need));
                if (L > 1) Assert.Equal(L - 1, _engine.ExpToLevel(need - 1));
            }
        }

        [Fact]
        public void StatAtLevel_BaseAtLevel1Stage1()
        {
            Assert.Equal(100, _engine.StatAtLevel(100, 1, 1, 1f));
        }

        [Fact]
        public void StatAtLevel_LevelScalesTenPercentPerLevel()
        {
            // 每级 +10%：level 1 → base；level 11 → base*2。
            Assert.Equal(200, _engine.StatAtLevel(100, 11, 1, 1f));
        }

        [Fact]
        public void StatAtLevel_StageMultiplies()
        {
            Assert.Equal(200, _engine.StatAtLevel(100, 1, 2, 1f));
        }

        [Fact]
        public void StatAtLevel_NegativeInputsClamped()
        {
            Assert.Equal(100, _engine.StatAtLevel(100, 0, 0, 1f));
        }

        [Theory]
        [InlineData(0, 1.0f)]
        [InlineData(1, 1.0f)]
        [InlineData(2, 1.05f)]
        [InlineData(7, 1.30f)]
        public void StarMultiplier_Endpoints(int stars, float expected)
        {
            Assert.Equal(expected, ProgressionEngine.StarMultiplier(stars), 4);
        }

        [Fact]
        public void StarMultiplier_Monotonic()
        {
            float prev = 0f;
            for (int s = 1; s <= 7; s++)
            {
                float m = ProgressionEngine.StarMultiplier(s);
                Assert.True(m > prev, $"star {s} multiplier should strictly increase");
                prev = m;
            }
        }
    }
}
