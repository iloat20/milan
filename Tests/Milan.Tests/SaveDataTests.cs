using Milan.Infrastructure.Save;
using Xunit;

namespace Milan.Tests
{
    public class SaveDataTests
    {
        [Fact]
        public void RoundTrip_BattleRecordPublicFieldsSurviveIncludeFields()
        {
            // 全部模型都是公共字段，必须靠 IncludeFields=true 才写得出/读得进。
            var data = new SaveData
            {
                SoftCurrency = 1234,
                BattleRecords =
                {
                    new BattleRecord
                    {
                        EnemyName = "炎魔",
                        EnemyElement = "Flame",
                        Victory = true,
                        Turns = 3,
                        RemainingHp = 777,
                        TeamPower = 650,
                        Timestamp = 1700000000000L,
                    },
                },
            };
            string json = data.ToJson();
            SaveData back = SaveData.FromJson(json);

            Assert.Equal(1234, back.SoftCurrency);
            Assert.Single(back.BattleRecords);
            var rec = back.BattleRecords[0];
            Assert.Equal("炎魔", rec.EnemyName);
            Assert.Equal("Flame", rec.EnemyElement);
            Assert.True(rec.Victory);
            Assert.Equal(3, rec.Turns);
            Assert.Equal(777, rec.RemainingHp);
            Assert.Equal(650, rec.TeamPower);
            Assert.Equal(1700000000000L, rec.Timestamp);
        }

        [Fact]
        public void FromJson_NullCollection_SanitizedToEmpty()
        {
            // JSON 中显式 null 会覆盖字段初始化器；Sanitize 必须兜底，否则下游 NRE。
            string json = "{\"OwnedCharacters\":null,\"BattleRecords\":null}";
            SaveData d = SaveData.FromJson(json);
            Assert.NotNull(d.OwnedCharacters);
            Assert.NotNull(d.BattleRecords);
            Assert.Empty(d.OwnedCharacters);
            Assert.Empty(d.BattleRecords);
        }

        [Fact]
        public void FromJson_NullElementInBattleRecords_Removed()
        {
            string json = "{\"BattleRecords\":[null]}";
            SaveData d = SaveData.FromJson(json);
            Assert.NotNull(d.BattleRecords);
            Assert.Empty(d.BattleRecords);
        }

        [Fact]
        public void FromJson_Garbage_FallsBackToDefault()
        {
            SaveData d = SaveData.FromJson("这不是 json{{{");
            Assert.NotNull(d);
            Assert.NotNull(d.OwnedCharacters);
            Assert.NotNull(d.BattleRecords);
            Assert.Equal(1, d.Version);
        }
    }
}
