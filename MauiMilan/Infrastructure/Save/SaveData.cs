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
    public List<BattleRecord> BattleRecords = new();
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
        d.BattleRecords ??= new();
        d.BattleRecords.RemoveAll(x => x == null);

        // ── 数值钳制 ──
        // 上面的空引用净化只挡住了 NRE，挡不住"结构合法但数值荒谬"的档
        // （手改存档、旧版本遗留、写盘被截断后侥幸解析成功）。
        // 负货币会让所有"可负担"判定失效；Level/Stage/Stars < 1 会让
        // ProgressionEngine.StatAtLevel 的倍率槽算出 0 或负属性，战斗里表现为
        // 打不动也打不死的僵尸单位。一律在入口钳到合法域。
        if (d.SoftCurrency < 0) d.SoftCurrency = 0;
        if (d.HardCurrency < 0) d.HardCurrency = 0;

        // 同一 CharacterId 出现多份会让"是否已拥有"判定与列表渲染分叉
        // （抽卡判重取第一条、列表按全部渲染 → 图鉴里出现重复卡）。保留首条。
        var seenChars = new HashSet<string>();
        d.OwnedCharacters.RemoveAll(x => !seenChars.Add(x.CharacterId));
        foreach (var c in d.OwnedCharacters)
        {
            c.Level = Math.Max(1, c.Level);
            c.Stage = Math.Max(1, c.Stage);
            c.Stars = Math.Max(1, c.Stars);
            c.TotalExp = Math.Max(0, c.TotalExp);
            c.UnspentPoints = Math.Max(0, c.UnspentPoints);
            c.TalentPoints ??= new();
            c.TalentPoints.RemoveAll(string.IsNullOrEmpty);
        }

        // 道具同理去重合并：分散的同 ID 条目会让"持有数量"读到的只是其中一条。
        var mergedItems = new Dictionary<string, ItemSaveState>();
        foreach (var it in d.Items)
        {
            if (mergedItems.TryGetValue(it.ItemId, out var exist)) exist.Count += Math.Max(0, it.Count);
            else { it.Count = Math.Max(0, it.Count); mergedItems[it.ItemId] = it; }
        }
        if (mergedItems.Count != d.Items.Count)
        {
            d.Items.Clear();
            d.Items.AddRange(mergedItems.Values);
        }

        var seenPools = new HashSet<string>();
        d.GachaCounters.RemoveAll(x => !seenPools.Add(x.PoolId));
        foreach (var g in d.GachaCounters) g.Count = Math.Max(0, g.Count);
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

[System.Serializable]
public class BattleRecord
{
    public string EnemyName = "";
    public string EnemyElement = "";
    public bool Victory;
    public int Turns;
    public int RemainingHp;   // 胜利时我方剩余总血量
    public int TeamPower;     // 队伍战力快照（攻击总和）
    public long Timestamp;    // 毫秒时间戳
}
