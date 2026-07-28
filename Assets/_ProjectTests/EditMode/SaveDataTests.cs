using NUnit.Framework;
using Milan.Infrastructure.Save;

[TestFixture]
public class SaveDataTests
{
    [Test]
    public void Serialize_Deserialize_RoundTrip()
    {
        var d = new SaveData { Version = 1, SoftCurrency = 500 };
        d.OwnedCharacters.Add(new CharacterSaveState {
            CharacterId = "char_a", Level = 10, Stage = 2, Stars = 3 });
        d.OwnedCharacters[0].TalentPoints.Add("node_1");
        d.SetGachaCounter("pool_main", 42);

        var json = d.ToJson();
        var r = SaveData.FromJson(json);

        Assert.AreEqual(1, r.Version);
        Assert.AreEqual(500, r.SoftCurrency);
        Assert.AreEqual(1, r.OwnedCharacters.Count);
        Assert.AreEqual("char_a", r.OwnedCharacters[0].CharacterId);
        Assert.AreEqual(10, r.OwnedCharacters[0].Level);
        Assert.AreEqual(1, r.OwnedCharacters[0].TalentPoints.Count);
        Assert.AreEqual(42, r.GetGachaCounter("pool_main"));
    }
}
