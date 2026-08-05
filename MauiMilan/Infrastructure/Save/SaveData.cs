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

    // 设置项（A2）：开关类偏好持久化，避免重启即丢失。统一默认开启。
    public bool SoundEnabled = true;
    public bool VibrationEnabled = true;
    public bool PushEnabled = true;

    // ⚠️ IncludeFields = true 绝对不能删。
    // SaveData 及其子模型（CharacterSaveState / ItemSaveState / GachaCounterEntry）全部是
    // 公共字段，而 System.Text.Json 默认 IncludeFields = false ——> 字段既不写出也不读入。
    // 后果（曾真实发生）：ToJson() 输出的是字面量 "{}"，存档文件写了个寂寞，
    // 每次启动都退回 CreateDefault()，玩家抽到的角色与消耗的星尘全部凭空消失。
    static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        IncludeFields = true,
        PropertyNameCaseInsensitive = true,
    };

    public string ToJson() => JsonSerializer.Serialize(this, JsonOptions);

    /// <summary>
    /// 反序列化存档。任何 null/空/损坏输入都必须回退 CreateDefault()，绝不允许抛异常——
    /// 否则 GameState 静态初始化会抛 TypeInitializationException，App 永久无法启动。
    /// </summary>
    public static SaveData FromJson(string j) => TryParse(j, out var d) ? d : CreateDefault();

    /// <summary>
    /// 尝试解析存档；成功返回 true 并输出对象，失败（null/空/损坏/集合字段显式为 null）返回 false。
    /// 用于 SaveManager 区分「无档」与「损坏」并决定回退策略。
    /// </summary>
    public static bool TryParse(string j, out SaveData data)
    {
        data = CreateDefault();
        if (string.IsNullOrWhiteSpace(j)) return false;
        try
        {
            var d = JsonSerializer.Deserialize<SaveData>(j, JsonOptions);
            if (d == null) return false;
            Sanitize(d);
            data = d;
            return true;
        }
        catch (Exception)
        {
            // 刻意 catch 全部异常：解析失败一律视为损坏，交由调用方回退。
            return false;
        }
    }

    /// <summary>
    /// 修复反序列化后的空引用：JSON 中显式的 <c>"OwnedCharacters":null</c> 会覆盖字段初始化器，
    /// 得到 null 集合；若不加净化，下游 foreach/Find 即 NRE，且坏档常驻磁盘 -> 每次启动必崩。
    /// </summary>
    static void Sanitize(SaveData d)
    {
        d.OwnedCharacters ??= new();
        d.OwnedSkins ??= new();
        d.Items ??= new();
        d.GachaCounters ??= new();
        d.UserId ??= "";
        d.OwnedCharacters.RemoveAll(x => x == null || x.CharacterId == null);
        d.Items.RemoveAll(x => x == null || x.ItemId == null);
        d.GachaCounters.RemoveAll(x => x == null || x.PoolId == null);
    }

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
