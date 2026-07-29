using System.Text.Json;

namespace Milan.Infrastructure.Save;

/// <summary>
/// Save data model (serialized with System.Text.Json).
/// </summary>
[System.Serializable]
public class SaveData
{
    public int Version = 1;
    public int SoftCurrency = 999999;
    public int HardCurrency = 0;
    public List<CharacterSaveState> OwnedCharacters = new();
    public List<string> OwnedSkins = new();
    public List<ItemSaveState> Items = new();
    public List<GachaCounterEntry> GachaCounters = new();
    public string UserId = "";
    public int ServerSyncStatus = 0;

    static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public string ToJson() => JsonSerializer.Serialize(this, JsonOptions);
    public static SaveData FromJson(string j) =>
        JsonSerializer.Deserialize<SaveData>(j, JsonOptions) ?? CreateDefault();
    public static SaveData CreateDefault() => new();

    public int GetGachaCounter(string poolId)
    {
        var e = GachaCounters.Find(x => x.PoolId == poolId);
        return e != null ? e.Count : 0;
    }

    public void SetGachaCounter(string poolId, int count)
    {
        var e = GachaCounters.Find(x => x.PoolId == poolId);
        if (e != null) e.Count = count;
        else GachaCounters.Add(new GachaCounterEntry { PoolId = poolId, Count = count });
    }
}

[System.Serializable]
public class GachaCounterEntry { public string PoolId = ""; public int Count; }

[System.Serializable]
public class CharacterSaveState
{
    public string CharacterId = "";
    public int Level = 1, Stage = 1, Stars = 1;
    public int TotalExp = 0;
    public int UnspentPoints = 0;
    public List<string> TalentPoints = new();
}

[System.Serializable]
public class ItemSaveState { public string ItemId = ""; public int Count; }
