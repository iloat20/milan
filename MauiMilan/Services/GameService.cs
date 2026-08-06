using Android.Content;
using Milan.Domain.Gacha;
using Milan.Domain.Progression;
using Milan.Infrastructure.EventBus;
using Milan.Infrastructure.Save;
using System.Text.Json;

namespace Milan.Maui.Services;

public partial class GameService
{
    private readonly SaveManager _save;
    private readonly GachaEngine _gacha;
        private readonly ProgressionEngine _progression = new();
        private readonly TalentEngine _talent = new();
        private readonly Random _rng = new();
    // 抽卡是进程级单例的唯一可变写入口，用锁保证原子性与 Random 线程安全（#19）。
    private readonly object _pullLock = new();

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

    // ⚠️ IncludeFields = true 绝对不能删。
    // 本文件下方所有数据模型（CharacterDataEntry / GachaPoolDataEntry / GachaPoolEntry /
    // SkillData / TalentTreeData ...）都是用「公共字段」而非属性定义的，而 System.Text.Json
    // 默认 IncludeFields = false ——> 字段一律不参与反序列化。
    // 后果（曾真实发生）：data.json 里 20 个角色能被反序列化成 20 个"对象"，但每个对象的
    // 字段全是默认值（CharacterId=""、Entries=空 List）。Initialize 只检查 Characters.Count
    // 会误判为加载成功，于是抽卡时 pool.Entries 为空 -> Pull 返回空列表 ->
    // GachaActivity 的 results.First() 抛 InvalidOperationException -> 点抽卡必闪退。
    private static readonly JsonSerializerOptions ContentJsonOptions = new()
    {
        IncludeFields = true,
        PropertyNameCaseInsensitive = true,
        AllowTrailingCommas = true,
        ReadCommentHandling = JsonCommentHandling.Skip,
    };

    // 注意：必须用同步读取。曾经的实现是 async Task + UI 线程 .GetResult() 阻塞，
    // 而 ReadToEndAsync 会捕获主线程 SynchronizationContext —— 主线程等任务完成、
    // 任务的 continuation 又必须回到主线程才能继续，于是永久死锁（表现为「卡在加载页、不弹窗」）。
    public void Initialize(Context context)
    {
        // 打包路径曾经出过错（asset 被放到 assets/Platforms/Android/Assets/ 下），
        // 这里按候选路径依次尝试，任何一条命中即可，避免再次静默退回兜底数据。
        string[] candidates =
        {
            "data.json",
            "Assets/data.json",
            "Platforms/Android/Assets/data.json",
        };

        foreach (var path in candidates)
        {
            try
            {
                using var stream = context.Assets!.Open(path);
                using var reader = new StreamReader(stream);
                var json = reader.ReadToEnd();
                var root = JsonSerializer.Deserialize<RootData>(json, ContentJsonOptions);

                // 反序列化成功但没有角色 = 数据无效，继续试下一个候选，最后走兜底。
                if (root == null || root.Characters == null || root.Characters.Count == 0)
                    continue;

                // ⚠️ 只看 Count 是不够的。反序列化配置一旦出错（例如漏了 IncludeFields），
                // 会得到「数量对得上、内容全是默认值」的僵尸数据，Count 检查完全看不出来。
                // 必须校验内容确实有效，否则宁可走 LoadFallback() 也不能带着空数据进游戏。
                // 注意 JSON 中的显式 null 会覆盖字段初始值，所以这里一律做空引用防护。
                var validChars = root.Characters.Where(c => c != null && !string.IsNullOrEmpty(c.CharacterId)).ToList();
                if (validChars.Count == 0)
                {
                    CrashReporter.Boot($"data.json at '{path}' parsed but all CharacterId empty -> treat as invalid");
                    continue;
                }

                Characters.Clear(); Characters.AddRange(validChars);
                Pools.Clear();
                // 校验卡池完整性：Entries 非空且每个元素非 null；RarityWeights 长度>=4 且权重和>0
                // （否则抽卡时 RollRarity 会因 null/空权重抛 NRE 或 Next(0) 异常）。
                Pools.AddRange((root.Pools ?? new List<GachaPoolDataEntry>())
                    .Where(p => p != null
                        && !string.IsNullOrEmpty(p.PoolId)
                        && p.Entries != null && p.Entries.Count > 0 && p.Entries.All(e => e != null)
                        && p.RarityWeights != null && p.RarityWeights.Length >= 4
                        && p.RarityWeights.Sum() > 0));
                TalentTrees.Clear();
                // 过滤掉 Nodes 为 null 的树（随包数据一旦漏字段即变成空树，养成界面静默空白）。
                TalentTrees.AddRange((root.TalentTrees ?? new List<TalentTreeData>())
                    .Where(t => t != null && t.Nodes != null));

                // 与兜底路径一致：补齐全量角色字段（武器名/背景故事/语音等），保证两条加载路径数据一致（#31）。
                EnrichCharacters();

                // 卡池为空会让抽卡直接崩，补一个兜底卡池。
                if (Pools.Count == 0) BuildPools();
                // 天赋树缺失不会崩（Talent 是可空的），但养成界面会空掉，按角色补全。
                if (TalentTrees.Count == 0) BuildTalentTrees();

                CrashReporter.Boot($"data.json loaded from '{path}' chars={Characters.Count} pools={Pools.Count} entries={Pools.FirstOrDefault()?.Entries.Count ?? 0} trees={TalentTrees.Count}");
                return;
            }
            catch (Exception ex)
            {
                // Release 下 Debug.WriteLine 会被编译器裁掉，导致加载失败完全静默；
                // 改用 CrashReporter 持久留痕，便于事后定位（#35）。
                CrashReporter.Boot($"data load failed at '{path}': {ex.Message}");
            }
        }

        CrashReporter.Boot("data.json NOT FOUND -> LoadFallback()");
        LoadFallback();
    }

    public void LoadFallback()
    {
        BuildCharacters();
        BuildPools();
        BuildTalentTrees();
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
            PoolId = "pool_main", DisplayName = "诸神黄昏 · 常驻",
            RarityWeights = new[] { 400, 300, 200, 100 }, HardPity = 90,
            SingleCost = 160, TenCost = 1600, Entries = all
        });
        Pools.Add(new GachaPoolDataEntry
        {
            PoolId = "pool_flame", DisplayName = "业火轮盘 · UP",
            RarityWeights = new[] { 400, 300, 200, 100 }, HardPity = 80,
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

    /// <summary>重复角色按稀有度补偿的星魂碎片数量。公式在 <see cref="EconomyFormulas"/>（纯领域、可单测）。</summary>
    public static int FragmentsForRarity(int rarity) => EconomyFormulas.FragmentsForRarity(rarity);

    public const string StarFragmentItemId = "item_star_fragment";

    public List<PullResult> Pull(string poolId, bool tenPull)
    {
        lock (_pullLock)
        {
            var results = new List<PullResult>();
            var pool = Pools.FirstOrDefault(p => p.PoolId == poolId);
            if (pool == null) return results;

            // 空卡池一张牌也抽不出来。必须在扣款【之前】拦截，
            // 否则玩家的星尘会被静默吞掉，而调用方只收到一个空列表。
            if (pool.Entries == null || pool.Entries.Count == 0) return results;

            int count = tenPull ? 10 : 1;
            int cost = tenPull ? pool.TenCost : pool.SingleCost;
            if (SaveData.SoftCurrency < cost) return results;

            // 先算出所有产出（不扣款、不改存档）：任何配置错误（如某稀有度无候选）只导致"少抽"，
            // 绝不会"扣了钱没东西"（#8）。展示稀有度与补偿碎片统一用抽中角色的真实稀有度（#11）。
            var pity = new PityCounter(pool.HardPity) { Counter = SaveData.GetGachaCounter(poolId) };
            var plan = new List<(string id, CharacterDataEntry? def, bool isNew, int fragments, int rarity)>();
            for (int i = 0; i < count; i++)
            {
                var rolledRarity = (int)pity.RollWithPity(_rng, pool.RarityWeights, 3);
                // 掷出的稀有度段在本池可能没有候选角色（如 UP 池没有 R 角色）。
                // 此时就近向上升档（保证玩家不亏），全部向上无候选再向下回退。
                int effectiveRarity = ResolveRarityWithCandidates(pool, rolledRarity);
                var entries = pool.Entries.Where(e => e != null && e.RarityIndex == effectiveRarity).ToList();
                string? id = PickFromEntries(entries);
                if (string.IsNullOrEmpty(id)) continue; // 该稀有度无候选，跳过（不影响其它抽）

                var def = Characters.FirstOrDefault(c => c.CharacterId == id);
                // 展示稀有度与补偿碎片必须口径一致：统一用抽中角色的真实稀有度（#11）。
                int rarity = def?.BaseRarity ?? effectiveRarity;
                bool isNew = !SaveData.OwnedCharacters.Exists(c => c.CharacterId == id);
                int fragments = isNew ? 0 : FragmentsForRarity(rarity);
                plan.Add((id!, def, isNew, fragments, rarity));
            }
            if (plan.Count == 0) return results; // 没抽到任何东西，绝不扣款

            // 确认有产出后再扣款 + 落盘；落盘失败回滚本次扣款与发货，让玩家可重试（#5）。
            int originalCurrency = SaveData.SoftCurrency;
            int originalCounter = SaveData.GetGachaCounter(poolId);
            // 碎片条目在本次抽卡前是否已存在：决定回滚时是"减回数量"还是"整条移除"，
            // 否则首次抽到重复角色且落盘失败，会在存档里留下一条数量为 0 的幽灵道具。
            bool fragItemExisted = SaveData.Items.Exists(x => x.ItemId == StarFragmentItemId);
            int fragDelta = 0;
            foreach (var (id, def, isNew, fragments, rarity) in plan)
            {
                results.Add(new PullResult
                {
                    Success = true,
                    CharacterId = id,
                    CharacterName = def?.DisplayName ?? id,
                    Rarity = rarity,
                    IsNew = isNew,
                    FragmentsAwarded = fragments
                });
                if (isNew) SaveData.OwnedCharacters.Add(new CharacterSaveState { CharacterId = id });
                else fragDelta += fragments;
            }
            if (fragDelta > 0)
            {
                var item = SaveData.Items.Find(x => x.ItemId == StarFragmentItemId);
                if (item != null) item.Count += fragDelta;
                else SaveData.Items.Add(new ItemSaveState { ItemId = StarFragmentItemId, Count = fragDelta });
            }
            SaveData.SoftCurrency -= cost;
            SaveData.SetGachaCounter(poolId, pity.Counter);
            if (!_save.Save())
            {
                SaveData.SoftCurrency = originalCurrency;
                SaveData.SetGachaCounter(poolId, originalCounter);
                SaveData.OwnedCharacters.RemoveAll(c => plan.Exists(p => p.id == c.CharacterId && p.isNew));
                // 碎片补偿也必须回滚：只退钱不退货会让玩家"存档没变但碎片凭空多出来"，
                // 下次任意一次成功落盘就把这份幻影收益固化成真实资源（可无限刷）。
                if (fragDelta > 0)
                {
                    if (fragItemExisted)
                    {
                        var frag = SaveData.Items.Find(x => x.ItemId == StarFragmentItemId);
                        if (frag != null) frag.Count = Math.Max(0, frag.Count - fragDelta);
                    }
                    else
                    {
                        SaveData.Items.RemoveAll(x => x.ItemId == StarFragmentItemId);
                    }
                }
                CrashReporter.Boot("pull.save.failed: rolled back");
                return new List<PullResult>();
            }
            // 扣费 + 落盘都成功后才广播经济变动。回滚分支不会跑到这里，
            // 否则 UI 会显示"已扣但存档未变"的陈旧值。
            PublishCurrencyChanged();
            return results;
        }
    }

    // ── 经济变动统一出口（EventBus 接线点）──
    // 任何改动星尘/钻石的地方都走这里，改动即广播，订阅方只刷受影响控件，不再依赖 OnResume 整页重建。
    private void PublishCurrencyChanged()
    {
        EventBus.Publish(new CurrencyChanged());
        EventBus.Dispatch();
    }

    // ── 货币增减 ──
    // 全部遵循与 Pull 一致的事务范式：先校验可负担 → 改内存 → 落盘 → 失败回滚 → 仅成功才广播。
    // 早期版本只改内存、不落盘也不校验余额，任何调用方都会造成
    // 「UI 显示已扣，重启后钱又回来」以及「星尘可被扣成负数」两类问题。
    // 返回 false 表示未发生任何变更（余额不足或落盘失败），调用方应据此提示玩家。

    /// <summary>扣除星尘。余额不足或落盘失败时不做任何变更并返回 false。</summary>
    public bool SpendSoft(int amount) => ApplyCurrencyDelta(-amount, 0);

    /// <summary>增加星尘。落盘失败时回滚并返回 false。</summary>
    public bool AddSoft(int amount) => ApplyCurrencyDelta(amount, 0);

    /// <summary>扣除钻石。余额不足或落盘失败时不做任何变更并返回 false。</summary>
    public bool SpendHard(int amount) => ApplyCurrencyDelta(0, -amount);

    /// <summary>增加钻石。落盘失败时回滚并返回 false。</summary>
    public bool AddHard(int amount) => ApplyCurrencyDelta(0, amount);

    private bool ApplyCurrencyDelta(int softDelta, int hardDelta)
    {
        if (softDelta == 0 && hardDelta == 0) return false;
        // 负余额是不可恢复的脏状态（UI 会显示负数、后续所有可负担判定都失效），必须前置拦截。
        if (SaveData.SoftCurrency + softDelta < 0) return false;
        if (SaveData.HardCurrency + hardDelta < 0) return false;

        int origSoft = SaveData.SoftCurrency, origHard = SaveData.HardCurrency;
        SaveData.SoftCurrency += softDelta;
        SaveData.HardCurrency += hardDelta;
        if (!_save.Save())
        {
            SaveData.SoftCurrency = origSoft;
            SaveData.HardCurrency = origHard;
            return false;
        }
        PublishCurrencyChanged();
        return true;
    }

    /// <summary>立即落盘（设置项改 SaveData 字段后调用）。SaveData 是 _save.Current 的同一引用，
    /// 改动字段后再调此方法即可持久化，无需经抽卡路径。</summary>
    public void Save() => _save.Save();

    // ─────────────────────────────────────────────────────────── 战绩
    /// <summary>读取战绩（最近在前）。空列表返回新实例，调用方无需判 null。</summary>
    public IReadOnlyList<BattleRecord> GetBattleRecords()
    {
        SaveData.BattleRecords ??= new();
        return SaveData.BattleRecords;
    }

    /// <summary>追加一条战绩并落盘。落盘失败回滚本次追加（不广播事件，战绩非经济）。
    /// 列表上限 50 条，超出丢弃最旧记录。</summary>
    public void RecordBattle(BattleRecord rec)
    {
        if (rec == null) return;
        SaveData.BattleRecords ??= new();
        SaveData.BattleRecords.Add(rec);
        const int MaxRecords = 50;
        if (SaveData.BattleRecords.Count > MaxRecords)
            SaveData.BattleRecords.RemoveRange(0, SaveData.BattleRecords.Count - MaxRecords);
        if (!_save.Save())
            SaveData.BattleRecords.Remove(rec); // 回滚，避免"内存与存档不一致"
    }

    // ─────────────────────────────────────────────────────────── 养成操作
    // 所有写操作遵循 Pull 的事务范式：先预算/校验可支付，再变更内存并落盘；
    // 落盘失败回滚本次内存改动，绝不让"内存与存档不一致"。回滚路径不广播事件。
    // 未拥有的角色（不在 OwnedCharacters）一律拒绝养成。

    // 以下四个公式方法保留为实例方法只是为了不破坏 UI 调用点；实现一律委托
    // EconomyFormulas（纯领域、单一事实来源、被 Tests/Milan.Tests 直接覆盖）。
    // 禁止在此处重新写数字——改数值请改 EconomyFormulas。

    /// <summary>等级上限随突破阶段提高：Stage×20（Stage1→20 级，Stage4→80 级）。</summary>
    public int MaxLevelForStage(int stage) => EconomyFormulas.MaxLevelForStage(stage);

    /// <summary>从 level 升到 level+1 的星尘消耗（随等级线性上升）。</summary>
    public int LevelCost(int level) => EconomyFormulas.LevelCost(level);

    /// <summary>stage→stage+1 突破所需星魂碎片（重复角色补偿货币）。</summary>
    public int AscendFragments(int stage) => EconomyFormulas.AscendFragments(stage);

    /// <summary>stage→stage+1 突破所需星尘。</summary>
    public int AscendSoft(int stage) => EconomyFormulas.AscendSoft(stage);

    /// <summary>当前经验条进度（本等级内已积累 / 本级所需）。达到等级上限时返回 (need, need)。</summary>
    public (int cur, int need) ExpProgress(string charId)
    {
        var save = GetSave(charId);
        if (save == null) return (0, 1);
        int need = EconomyFormulas.ExpForLevel(save.Level);
        int cur = System.Math.Clamp(save.TotalExp - EconomyFormulas.CumulativeExp(save.Level), 0, need);
        if (save.Level >= EconomyFormulas.MaxLevelForStage(save.Stage)) return (need, need);
        return (cur, need);
    }

    /// <summary>取角色存档；未拥有返回 null（养成操作应据此拒绝）。</summary>
    public CharacterSaveState? GetSave(string charId)
        => SaveData.OwnedCharacters.FirstOrDefault(c => c.CharacterId == charId);

    /// <summary>当前持有的星魂碎片（重复角色补偿货币，以 Item 形式存储）。</summary>
    public int GetStarFragments()
    {
        var item = SaveData.Items.FirstOrDefault(x => x.ItemId == StarFragmentItemId);
        return item?.Count ?? 0;
    }

    /// <summary>升级 n 级（默认 1）。星尘不足或已达等级上限时尽可能少升；一级都升不了返回 false。
    /// 每升 1 级 +1 天赋点。落盘失败回滚。</summary>
    public bool LevelUp(string charId, int n = 1)
    {
        var save = GetSave(charId);
        if (save == null || n <= 0) return false;

        // 预算规划抽到纯领域（EconomyFormulas.PlanLevelUp），边界行为由单元测试锁死。
        var (gained, cost) = EconomyFormulas.PlanLevelUp(
            save.Level, EconomyFormulas.MaxLevelForStage(save.Stage), SaveData.SoftCurrency, n);
        if (gained <= 0) return false; // 一级都升不了（资源不足 / 已满级）

        int target = save.Level + gained;
        int origSoft = SaveData.SoftCurrency;
        SaveData.SoftCurrency -= cost;
        save.Level = target;
        save.TotalExp = EconomyFormulas.CumulativeExp(target);   // 经验条跟随等级定位
        save.UnspentPoints += gained;
        if (!_save.Save())
        {
            SaveData.SoftCurrency = origSoft;
            save.Level -= gained;
            save.UnspentPoints -= gained;
            save.TotalExp = EconomyFormulas.CumulativeExp(save.Level);
            return false;
        }
        PublishCurrencyChanged();
        PublishProgressionChanged();
        return true;
    }

    /// <summary>突破（Stage+1）。需未达 MaxStage 且星魂碎片 + 星尘充足。落盘失败回滚。</summary>
    public bool Ascend(string charId)
    {
        var save = GetSave(charId);
        if (save == null) return false;
        var def = Characters.FirstOrDefault(c => c.CharacterId == charId);
        if (def == null || save.Stage >= def.MaxStage) return false;

        int frags = AscendFragments(save.Stage);
        int soft = AscendSoft(save.Stage);
        int have = GetStarFragments();
        if (have < frags || SaveData.SoftCurrency < soft) return false;

        int origSoft = SaveData.SoftCurrency;
        int origFrags = have;
        SaveData.SoftCurrency -= soft;
        var item = SaveData.Items.FirstOrDefault(x => x.ItemId == StarFragmentItemId);
        if (item != null) item.Count -= frags;
        save.Stage += 1;
        if (!_save.Save())
        {
            SaveData.SoftCurrency = origSoft;
            if (item != null) item.Count = origFrags;
            save.Stage -= 1;
            return false;
        }
        PublishCurrencyChanged();
        PublishProgressionChanged();
        return true;
    }

    /// <summary>升星（Stars+1）所需星魂碎片（随当前星数线性上升：1★→2★ 耗 20，2★→3★ 耗 40…）。</summary>
    public int StarUpFragments(int stars) => EconomyFormulas.StarUpFragments(stars);

    /// <summary>升星（Stars+1）。需未达 MaxStars 且星魂碎片充足。落盘失败回滚。每次仅 +1 星。
    /// 升星是星级的小幅属性加成（见 GameState.ComputeStatsAt 的 starMul），消耗重复角色补偿的星魂碎片。</summary>
    public bool StarUp(string charId)
    {
        var save = GetSave(charId);
        if (save == null) return false;
        var def = Characters.FirstOrDefault(c => c.CharacterId == charId);
        if (def == null || save.Stars >= def.MaxStars) return false;

        int cost = StarUpFragments(save.Stars);
        int have = GetStarFragments();
        if (have < cost) return false;

        int origFrags = have;
        var item = SaveData.Items.FirstOrDefault(x => x.ItemId == StarFragmentItemId);
        if (item != null) item.Count -= cost;
        save.Stars += 1;
        if (!_save.Save())
        {
            if (item != null) item.Count = origFrags;
            save.Stars -= 1;
            return false;
        }
        PublishCurrencyChanged();
        PublishProgressionChanged();
        return true;
    }

    /// <summary>取角色天赋树（含节点与前置关系）。无树返回 null。</summary>
    public TalentTreeData? GetTalentTree(string charId)
    {
        var def = Characters.FirstOrDefault(c => c.CharacterId == charId);
        if (def == null) return null;
        return TalentTrees.FirstOrDefault(t => t.TreeId == def.TalentTreeId);
    }

    /// <summary>构建 nodeId → 前置节点数组 的映射，喂给 TalentEngine.CanAllocate。</summary>
    public Dictionary<string, string[]> PrereqMap(TalentTreeData tree)
    {
        var m = new Dictionary<string, string[]>();
        if (tree?.Nodes == null) return m;
        foreach (var n in tree.Nodes)
            if (n != null) m[n.NodeId] = n.PrerequisiteNodeIds?.ToArray() ?? System.Array.Empty<string>();
        return m;
    }

    /// <summary>不落盘地预判某天赋节点当前是否可点亮（用于 UI 三态与按钮可用性）。</summary>
    public bool CanAllocateTalent(string charId, string nodeId)
    {
        var save = GetSave(charId);
        if (save == null) return false;
        // 存档里 "TalentPoints": null 会覆盖字段初始化器 —— 这里若不补齐就是一次
        // 渲染路径上的 NRE（本方法在天赋节点三态渲染中被逐节点调用），直接闪退。
        save.TalentPoints ??= new();
        var tree = GetTalentTree(charId);
        if (tree?.Nodes == null) return false;
        var node = tree.Nodes.FirstOrDefault(x => x != null && x.NodeId == nodeId);
        if (node == null || save.TalentPoints.Contains(nodeId)) return false;
        if (save.UnspentPoints < node.Cost) return false;
        return _talent.CanAllocate(nodeId, save.TalentPoints, PrereqMap(tree));
    }

    /// <summary>点亮天赋节点：校验前置（TalentEngine）与天赋点余额，扣点并落盘。
    /// 已点过 / 点不够 / 前置未满足 / 落盘失败均返回 false。</summary>
    public bool AllocateTalent(string charId, string nodeId)
    {
        var save = GetSave(charId);
        if (save == null) return false;
        save.TalentPoints ??= new();
        var tree = GetTalentTree(charId);
        if (tree?.Nodes == null) return false;
        var node = tree.Nodes.FirstOrDefault(x => x != null && x.NodeId == nodeId);
        if (node == null || save.TalentPoints.Contains(nodeId)) return false;
        if (save.UnspentPoints < node.Cost) return false;
        if (!_talent.CanAllocate(nodeId, save.TalentPoints, PrereqMap(tree))) return false;

        int origPoints = save.UnspentPoints;
        save.UnspentPoints -= node.Cost;
        save.TalentPoints.Add(nodeId);
        if (!_save.Save())
        {
            save.UnspentPoints = origPoints;
            save.TalentPoints.Remove(nodeId);
            return false;
        }
        PublishProgressionChanged();
        return true;
    }

    // ── 养成变动统一出口（EventBus 接线点）──
    // 事件目前是无载荷标记（订阅方自行重读当前角色），故不带 charId 参数，
    // 避免"看起来会按角色过滤、实际全量广播"的误导性签名。
    private static void PublishProgressionChanged()
    {
        EventBus.Publish(new ProgressionChanged());
        EventBus.Dispatch();
    }

    /// <summary>返回距 rolled 最近且有候选角色的稀有度档位（优先向上）。</summary>
    private static int ResolveRarityWithCandidates(GachaPoolDataEntry pool, int rolled)
    {
        if (pool.Entries.Any(e => e.RarityIndex == rolled)) return rolled;
        for (int r = rolled + 1; r <= 4; r++)
            if (pool.Entries.Any(e => e.RarityIndex == r)) return r;
        for (int r = rolled - 1; r >= 1; r--)
            if (pool.Entries.Any(e => e.RarityIndex == r)) return r;
        return rolled;
    }

    private string? PickFromEntries(List<GachaPoolEntry> entries)
    {
        if (entries.Count == 0) return null;
        return _gacha.PickWeighted(entries.Select(e => e.CharacterId).ToArray(), entries.Select(e => e.Weight).ToArray());
    }

}

// --------------------------------------------------------------------- data models

public class CharacterDataEntry
{
    public string CharacterId = "";
    public string DisplayName = "";
    public string Title = "";
    public string World = "Shinwa";
    public string Faction = "";
    public string Element = "Flame";
    public int BaseRarity = 1;
    public int[] BaseStats = { 100, 80, 1000, 12 };
    public int MaxStage = 4;
    public int MaxStars = 5;
    public bool CanBreakthrough;
    public string Lore = "";
    public string Story = "";
    public string TalentTreeId = "";
    public List<SkillData> Skills = new();
    public List<string> Voices = new();
    public string WeaponVfx = "";
    public string AmbientVfx = "";
    public string Weapon = "";        // 专属武器名称（UR 特色武器，依据背景故事设计）
    public string WeaponDesc = "";    // 武器背景故事 / 描述
}

public class SkillData
{
    public string SkillId = "";
    public string DisplayName = "";
    public string Description = "";
    public string Element = "";
    public string Type = ""; // Active / Passive / Ultimate
    public int Power = 0;
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
    public int[] RarityWeights = { 400, 300, 200, 100 };
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
    /// <summary>重复角色时补偿的星魂碎片数量（新角色为 0）。</summary>
    public int FragmentsAwarded;
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