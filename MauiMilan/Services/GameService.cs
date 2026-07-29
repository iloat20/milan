using Android.Content;
using Milan.Domain.Gacha;
using Milan.Domain.Progression;
using Milan.Infrastructure.Save;
using System.Text.Json;

namespace Milan.Maui.Services;

public class GameService
{
    private readonly SaveManager _save;
    private readonly GachaEngine _gacha;
    private readonly ProgressionEngine _progression = new();
    private readonly Random _rng = new();

    public SaveData SaveData => _save.Current;
    public List<CharacterDataEntry> Characters { get; } = new();
    public List<GachaPoolDataEntry> Pools { get; } = new();
    public List<TalentTreeData> TalentTrees { get; } = new();

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
                Characters.Clear(); Characters.AddRange(root.Characters);
                Pools.Clear(); Pools.AddRange(root.Pools);
                TalentTrees.Clear(); TalentTrees.AddRange(root.TalentTrees);
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
        BuildCharacters();
        BuildPools();
        BuildTalentTrees();
    }

    // ------------------------------------------------------------------ characters

    private void Add(string id, string name, string title, string world, string element,
        int rarity, int[] stats, int stars, bool breakthrough, string lore, string treeId)
    {
        Characters.Add(new CharacterDataEntry
        {
            CharacterId = id, DisplayName = name, Title = title, World = world, Element = element,
            BaseRarity = rarity, BaseStats = stats, MaxStage = 4, MaxStars = stars,
            CanBreakthrough = breakthrough, Lore = lore, TalentTreeId = treeId
        });
    }

    private void BuildCharacters()
    {
        Characters.Clear();
        // ========== UR 4★ ==========
        Add("char_ur_yan", "焱 Yan", "业火之蛇", "Shinwa", "Flame", 4, new[] { 160, 100, 1200, 18 }, 7, true, "相传为忍宗始祖封印的业火化身，觉醒之日，焚尽八荒。", "tree_yan");
        Add("char_ur_xu", "墟 Xu", "虚空领主", "Aether", "Void", 4, new[] { 150, 120, 1100, 16 }, 7, true, "来自维度裂隙的观察者，以星辰为食，以虚空为巢。", "tree_xu");
        Add("char_ur_shu", "枢 Shu", "天枢核心", "Ironveil", "Metal", 4, new[] { 140, 160, 1400, 14 }, 7, true, "铁帷纪元最古老的人工智能，觉醒自我意识后选择守护人类。", "tree_shu");
        Add("char_ur_yasha", "夜叉 Yasha", "暗夜夜叉王", "Shinwa", "Shadow", 4, new[] { 170, 90, 1150, 20 }, 7, true, "统御暗夜百鬼的王者，其一笑可令万物失色。", "tree_yasha");
        Add("char_ur_xinghuang", "星煌 Xinghuang", "星穹编织者", "Aether", "Star", 4, new[] { 155, 110, 1250, 17 }, 7, true, "以引力为丝、以星芒为线，编织命运之网的星界使徒。", "tree_xinghuang");
        // ========== SSR 3★ ==========
        Add("char_ssr_kasai", "烬 Kasai", "残烬之刃", "Shinwa", "Flame", 3, new[] { 120, 80, 1000, 15 }, 6, true, "灭族之夜唯一幸存者，以残烬之火重塑忍道。", "tree_kasai");
        Add("char_ssr_hui", "辉 Hui", "圣光使者", "Aether", "Light", 3, new[] { 110, 90, 1050, 14 }, 6, false, "来自星灵殿的治愈之光，所至之处，伤痛消散。", "tree_hui");
        Add("char_ssr_maichong", "脉冲 MaiChong", "电磁幽灵", "Ironveil", "Thunder", 3, new[] { 130, 85, 950, 18 }, 6, false, "电磁风暴中觉醒的AI幽灵，以雷霆之势贯穿战场。", "tree_maichong");
        Add("char_ssr_jifeng", "疾风 Jifeng", "风魔忍者", "Shinwa", "Wind", 3, new[] { 125, 75, 900, 22 }, 6, false, "风魔一族最后的传人，身法如风，来去无踪。", "tree_jifeng");
        Add("char_ssr_xingshuang", "星霜 Xingshuang", "深空冰晶", "Aether", "Frost", 3, new[] { 115, 100, 1100, 13 }, 6, false, "深空探测中觉醒的冰晶生命体，触碰之物皆凝霜华。", "tree_xingshuang");
        // ========== SR 2★ ==========
        Add("char_sr_deng", "灯 Deng", "灯火忍者", "Shinwa", "Flame", 2, new[] { 90, 70, 850, 13 }, 5, false, "忍宗灯影部的中坚，以灯火为号，传递情报。", "tree_deng");
        Add("char_sr_xingchen", "星尘 Xingchen", "星尘观测者", "Aether", "Light", 2, new[] { 85, 75, 900, 12 }, 5, false, "默默记录星象变化的观测者，知晓诸多秘密。", "tree_xingchen");
        Add("char_sr_luoshuan", "螺栓 Luoshuan", "机械修理工", "Ironveil", "Metal", 2, new[] { 95, 90, 1000, 10 }, 5, false, "铁帷下层的老修理工，能修好任何机械。", "tree_luoshuan");
        Add("char_sr_yanwu", "岩武 Yanwu", "岩石武士", "Shinwa", "Earth", 2, new[] { 100, 110, 1100, 9 }, 5, false, "以岩遁术守护忍宗要塞的忠诚武士。", "tree_yanwu");
        Add("char_sr_qiliu", "气流 Qiliu", "以太信使", "Aether", "Wind", 2, new[] { 88, 72, 820, 16 }, 5, false, "在以太风暴中穿梭的信使，传递跨次元讯息。", "tree_qiliu");
        // ========== R 1★ ==========
        Add("char_r_aoi", "葵 Aoi", "见习忍者", "Shinwa", "Wind", 1, new[] { 70, 60, 700, 12 }, 4, false, "刚刚通过试炼的年轻忍者，梦想成为传说中的强者。", "tree_aoi");
        Add("char_r_li", "砾 Li", "星砾采集者", "Aether", "Earth", 1, new[] { 75, 70, 750, 10 }, 4, false, "在星带中采集星砾的勤劳工人。", "tree_li");
        Add("char_r_ding", "钉 Ding", "废铁机器人", "Ironveil", "Metal", 1, new[] { 80, 80, 800, 8 }, 4, false, "被丢弃在废铁堆中的小型机器人，依然努力生存。", "tree_ding");
        Add("char_r_snow", "雪 Snow", "雪童子", "Shinwa", "Frost", 1, new[] { 65, 65, 680, 11 }, 4, false, "雪山上的神秘雪童，据说见到它会有好运。", "tree_snow");
        Add("char_r_ying", "萤 Ying", "萤火", "Aether", "Flame", 1, new[] { 68, 58, 650, 14 }, 4, false, "星灵界最微小的光点，却永不熄灭。", "tree_ying");
    }

    // ------------------------------------------------------------------ pools

    private void BuildPools()
    {
        Pools.Clear();
        GachaPoolEntry EntryFor(CharacterDataEntry c) => new()
        {
            CharacterId = c.CharacterId, RarityIndex = c.BaseRarity,
            Weight = c.BaseRarity == 4 ? 1 : c.BaseRarity == 3 ? 8 : c.BaseRarity == 2 ? 40 : 100
        };
        var all = Characters.Select(EntryFor).ToList();

        Pools.Add(new GachaPoolDataEntry
        {
            PoolId = "pool_main", DisplayName = "次元裂缝 · 常驻",
            RarityWeights = new[] { 820, 150, 29, 1 }, HardPity = 90,
            SingleCost = 160, TenCost = 1600, Entries = all
        });
        Pools.Add(new GachaPoolDataEntry
        {
            PoolId = "pool_flame", DisplayName = "业火轮盘 · UP",
            RarityWeights = new[] { 820, 150, 29, 1 }, HardPity = 80,
            SingleCost = 160, TenCost = 1600,
            Entries = Characters.Where(c => c.Element == "Flame" || c.BaseRarity >= 3).Select(EntryFor).ToList()
        });
    }

    // ------------------------------------------------------------------ talent trees

    private void BuildTalentTrees()
    {
        TalentTrees.Clear();
        foreach (var ch in Characters)
            TalentTrees.Add(BuildTree(ch));
    }

    private TalentTreeData BuildTree(CharacterDataEntry ch)
    {
        var branches = new List<string> { "branch_power", "branch_defense", "branch_utility" };
        var nodes = new List<TalentNodeData>
        {
            NewTalent(ch, "t1", "强攻", "branch_power", 1, "攻击力+10%"),
            NewTalent(ch, "t2", "破甲", "branch_power", 2, "无视敌方15%防御", "t1"),
            NewTalent(ch, "t3", "坚壁", "branch_defense", 1, "防御力+10%"),
            NewTalent(ch, "t4", "铁壁", "branch_defense", 2, "受到伤害-15%", "t3"),
            NewTalent(ch, "t5", "疾风步", "branch_utility", 1, "速度+8%"),
            NewTalent(ch, "t6", "灵动", "branch_utility", 2, "闪避率+10%", "t5"),
        };
        return new TalentTreeData { TreeId = ch.TalentTreeId, BranchIds = branches, Nodes = nodes };
    }

    private TalentNodeData NewTalent(CharacterDataEntry ch, string id, string name,
        string branch, int cost, string desc, string? prereq = null)
    {
        return new TalentNodeData
        {
            NodeId = ch.CharacterId + "_" + id,
            DisplayName = name,
            Description = desc,
            BranchId = branch,
            Cost = cost,
            PrerequisiteNodeIds = prereq != null ? new List<string> { ch.CharacterId + "_" + prereq } : new List<string>(),
            VisualLayerId = branch
        };
    }

    // ------------------------------------------------------------------ pull

    public List<PullResult> Pull(string poolId, bool tenPull)
    {
        var results = new List<PullResult>();
        var pool = Pools.FirstOrDefault(p => p.PoolId == poolId);
        if (pool == null) return results;

        int count = tenPull ? 10 : 1;
        int cost = tenPull ? pool.TenCost : pool.SingleCost;
        if (SaveData.SoftCurrency < cost) return results;
        SaveData.SoftCurrency -= cost;

        var pity = new PityCounter(pool.HardPity) { Counter = SaveData.GetGachaCounter(poolId) };
        for (int i = 0; i < count; i++)
        {
            var rarity = pity.RollWithPity(_rng, pool.RarityWeights, 3);
            var entries = GetEntriesForRarity(poolId, (int)rarity);
            string? id = PickFromEntries(entries);
            if (string.IsNullOrEmpty(id)) id = PickFromPool(pool);
            if (!string.IsNullOrEmpty(id))
            {
                bool isNew = !SaveData.OwnedCharacters.Exists(c => c.CharacterId == id);
                if (isNew) SaveData.OwnedCharacters.Add(new CharacterSaveState { CharacterId = id });
                var def = Characters.FirstOrDefault(c => c.CharacterId == id);
                results.Add(new PullResult
                {
                    Success = true, CharacterId = id,
                    CharacterName = def?.DisplayName ?? id,
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

// --------------------------------------------------------------------- data models

public class CharacterDataEntry
{
    public string CharacterId = "";
    public string DisplayName = "";
    public string Title = "";
    public string World = "Shinwa";
    public string Element = "Flame";
    public int BaseRarity = 1;
    public int[] BaseStats = { 100, 80, 1000, 12 };
    public int MaxStage = 4;
    public int MaxStars = 5;
    public bool CanBreakthrough;
    public string Lore = "";
    public string TalentTreeId = "";
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

public class TalentNodeData
{
    public string NodeId = "";
    public string DisplayName = "";
    public string Description = "";
    public string BranchId = "";
    public int Cost = 1;
    public List<string> PrerequisiteNodeIds = new();
    public string VisualLayerId = "";
}

public class TalentTreeData
{
    public string TreeId = "";
    public List<string> BranchIds = new();
    public List<TalentNodeData> Nodes = new();
}

class RootData
{
    public List<CharacterDataEntry> Characters { get; set; } = new();
    public List<GachaPoolDataEntry> Pools { get; set; } = new();
    public List<TalentTreeData> TalentTrees { get; set; } = new();
}