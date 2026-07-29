using Android.Content;
using Milan.Domain.Gacha;
using Milan.Domain.Progression;
using Milan.Infrastructure.Save;
using System.Text.Json;

namespace Milan.Maui.Services;

/// <summary>
/// 游戏服务：连接 Domain 引擎、存档和 JSON 数据（不依赖 MAUI）。
/// </summary>
public class GameService
{
    private readonly SaveManager _save;
    private readonly GachaEngine _gacha;
    private readonly ProgressionEngine _progression = new();
    private readonly Random _rng = new();

    public SaveData SaveData => _save.Current;
    public List<CharacterDataEntry> Characters { get; } = new();
    public List<GachaPoolDataEntry> Pools { get; } = new();

    public GameService()
    {
        _save = new SaveManager(new LocalSaveProvider("milan_save.json"));
        _save.Load();
        _gacha = new GachaEngine(_rng);
    }

    public async Task InitializeAsync(Context context)
    {
        try
        {
            using var stream = context.Assets.Open("data.json");
            using var reader = new StreamReader(stream);
            var json = await reader.ReadToEndAsync();
            var root = JsonSerializer.Deserialize<RootData>(json);
            if (root != null)
            {
                Characters.Clear();
                Characters.AddRange(root.Characters);
                Pools.Clear();
                Pools.AddRange(root.Pools);
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"[Milan] data load failed: {ex.Message}");
            LoadFallback();
        }
    }

    public void LoadFallback()
    {
        Characters.Clear();
        Characters.Add(new CharacterDataEntry
        {
            CharacterId = "char_kasai", DisplayName = "烬 Kasai", World = "Shinwa",
            BaseRarity = 3, BaseStats = new[] { 120, 80, 1000, 15 },
            MaxStage = 4, MaxStars = 6, CanBreakthrough = true
        });
        Characters.Add(new CharacterDataEntry
        {
            CharacterId = "char_hikari", DisplayName = "光 Hikari", World = "Aether",
            BaseRarity = 2, BaseStats = new[] { 90, 70, 900, 12 },
            MaxStage = 4, MaxStars = 5, CanBreakthrough = false
        });
        Pools.Clear();
        Pools.Add(new GachaPoolDataEntry
        {
            PoolId = "pool_main", DisplayName = "次元裂缝",
            RarityWeights = new[] { 820, 150, 29, 1 }, HardPity = 90,
            SingleCost = 160, TenCost = 1600,
            Entries = new List<GachaPoolEntry>
            {
                new() { CharacterId = "char_kasai", RarityIndex = 3, Weight = 50 },
                new() { CharacterId = "char_hikari", RarityIndex = 2, Weight = 100 }
            }
        });
    }

    public List<PullResult> Pull(string poolId, bool tenPull)
    {
        var results = new List<PullResult>();
        var pool = Pools.FirstOrDefault(p => p.PoolId == poolId);
        if (pool == null) return results;

        int count = tenPull ? 10 : 1;
        int cost = tenPull ? pool.TenCost : pool.SingleCost;
        if (SaveData.SoftCurrency < cost) return results;
        SaveData.SoftCurrency -= cost;

        var pity = new PityCounter(pool.HardPity)
        {
            Counter = SaveData.GetGachaCounter(poolId)
        };

        for (int i = 0; i < count; i++)
        {
            var rarity = pity.RollWithPity(_rng, pool.RarityWeights, 3);
            var entries = GetEntriesForRarity(poolId, (int)rarity);
            string? id = PickFromEntries(entries);
            if (string.IsNullOrEmpty(id)) id = PickFromPool(pool);

            if (!string.IsNullOrEmpty(id))
            {
                bool isNew = !SaveData.OwnedCharacters.Exists(c => c.CharacterId == id);
                if (isNew)
                    SaveData.OwnedCharacters.Add(new CharacterSaveState { CharacterId = id });
                var ch = Characters.FirstOrDefault(c => c.CharacterId == id);
                results.Add(new PullResult
                {
                    Success = true, CharacterId = id,
                    CharacterName = ch?.DisplayName ?? id,
                    Rarity = (int)rarity, IsNew = isNew
                });
            }
        }

        SaveData.SetGachaCounter(poolId, pity.Counter);
        _save.Save();
        return results;
    }

    private string? PickFromEntries(List<GachaPoolEntry> entries)
    {
        if (entries.Count == 0) return null;
        return _gacha.PickWeighted(entries.Select(e => e.CharacterId).ToArray(), entries.Select(e => e.Weight).ToArray());
    }

    private string? PickFromPool(GachaPoolDataEntry pool)
    {
        if (pool.Entries.Count == 0) return null;
        return _gacha.PickWeighted(pool.Entries.Select(e => e.CharacterId).ToArray(), pool.Entries.Select(e => e.Weight).ToArray());
    }

    private List<GachaPoolEntry> GetEntriesForRarity(string poolId, int rarity)
    {
        var pool = Pools.FirstOrDefault(p => p.PoolId == poolId);
        if (pool == null) return new List<GachaPoolEntry>();
        return pool.Entries.Where(e => e.RarityIndex == rarity).ToList();
    }
}

public class CharacterDataEntry
{
    public string CharacterId = "";
    public string DisplayName = "";
    public string World = "Shinwa";
    public int BaseRarity = 1;
    public int[] BaseStats = { 100, 80, 1000, 12 };
    public int MaxStage = 4;
    public int MaxStars = 5;
    public bool CanBreakthrough;
}

public class GachaPoolEntry
{
    public string CharacterId = "";
    public int RarityIndex = 1;
    public int Weight = 100;
}

public class GachaPoolDataEntry
{
    public string PoolId = "";
    public string DisplayName = "";
    public int[] RarityWeights = { 820, 150, 29, 1 };
    public int HardPity = 90;
    public int SingleCost = 100;
    public int TenCost = 1000;
    public List<GachaPoolEntry> Entries = new();
}

public class PullResult
{
    public bool Success;
    public string? CharacterId;
    public string CharacterName = "";
    public int Rarity;
    public bool IsNew;
}

class RootData
{
    public List<CharacterDataEntry> Characters { get; set; } = new();
    public List<GachaPoolDataEntry> Pools { get; set; } = new();
}
