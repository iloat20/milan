namespace Milan.Maui.DataProviders;

/// <summary>
/// 数据提供器：从 JSON 文件加载角色和卡池数据（替代 Unity Resources.LoadAll）。
/// </summary>
public class JsonDataProvider
{
    public List<CharacterDataEntry> Characters { get; } = new();
    public List<GachaPoolDataEntry> Pools { get; } = new();

    public async Task LoadAsync()
    {
        try
        {
            using var stream = await FileSystem.OpenAppPackageFileAsync("data.json");
            using var reader = new StreamReader(stream);
            var json = await reader.ReadToEndAsync();
            var root = System.Text.Json.JsonSerializer.Deserialize<RootData>(json);
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

    // 兜底：如果 JSON 加载失败，使用硬编码数据
    void LoadFallback()
    {
        Characters.Clear();
        Characters.Add(new CharacterDataEntry
        {
            CharacterId = "char_kasai",
            DisplayName = "烬 Kasai",
            World = "Shinwa",
            BaseRarity = 3,
            BaseStats = new[] { 120, 80, 1000, 15 },
            MaxStage = 4, MaxStars = 6, CanBreakthrough = true
        });
        Characters.Add(new CharacterDataEntry
        {
            CharacterId = "char_hikari",
            DisplayName = "光 Hikari",
            World = "Aether",
            BaseRarity = 2,
            BaseStats = new[] { 90, 70, 900, 12 },
            MaxStage = 4, MaxStars = 5, CanBreakthrough = false
        });

        Pools.Clear();
        Pools.Add(new GachaPoolDataEntry
        {
            PoolId = "pool_main",
            DisplayName = "次元裂缝 · 常驻",
            RarityWeights = new[] { 820, 150, 29, 1 },
            HardPity = 90,
            SingleCost = 160, TenCost = 1600,
            Entries = new List<GachaPoolEntry>
            {
                new() { CharacterId = "char_kasai", RarityIndex = 3, Weight = 50 },
                new() { CharacterId = "char_hikari", RarityIndex = 2, Weight = 100 },
            }
        });
    }

    public CharacterDataEntry? GetCharacter(string id) =>
        Characters.FirstOrDefault(c => c.CharacterId == id);

    public List<GachaPoolEntry> GetEntriesForRarity(string poolId, int rarity)
    {
        var pool = Pools.FirstOrDefault(p => p.PoolId == poolId);
        if (pool == null) return new List<GachaPoolEntry>();
        return pool.Entries.Where(e => e.RarityIndex == rarity).ToList();
    }

    class RootData
    {
        public List<CharacterDataEntry> Characters { get; set; } = new();
        public List<GachaPoolDataEntry> Pools { get; set; } = new();
    }
}
