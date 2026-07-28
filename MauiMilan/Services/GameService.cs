using Milan.Domain.Gacha;
using Milan.Domain.Progression;
using Milan.Infrastructure.Save;
using Milan.Maui.DataProviders;

namespace Milan.Maui.Services;

/// <summary>
/// MAUI 游戏服务：复用现有 Domain 引擎（GachaEngine/PityCounter/ProgressionEngine），
/// 连接 JSON 数据提供器和本地存档。
/// </summary>
public class GameService
{
    private readonly SaveManager _save;
    private readonly JsonDataProvider _data;
    private readonly GachaEngine _gacha;
    private readonly Random _rng = new();
    private readonly ProgressionEngine _progression = new();

    public SaveData SaveData => _save.Current;
    public JsonDataProvider Data => _data;

    public GameService()
    {
        _save = new SaveManager(new LocalSaveProvider("milan_save.json"));
        _save.Load();
        _data = new JsonDataProvider();
        _gacha = new GachaEngine(_rng);
    }

    public async Task InitializeAsync() => await _data.LoadAsync();

    // ---- 抽卡 ----
    public class PullResult
    {
        public bool Success;
        public string? CharacterId;
        public string CharacterName = "";
        public int Rarity; // 1=R 2=SR 3=SSR 4=UR
        public bool IsNew;
    }

    public List<PullResult> Pull(string poolId, bool tenPull)
    {
        var results = new List<PullResult>();
        var pool = _data.Pools.FirstOrDefault(p => p.PoolId == poolId);
        if (pool == null) return results;

        int count = tenPull ? 10 : 1;
        int cost = tenPull ? pool.TenCost : pool.SingleCost;
        if (_save.Current.SoftCurrency < cost) return results;
        _save.Current.SoftCurrency -= cost;

        var pity = new PityCounter(pool.HardPity)
        {
            Counter = _save.Current.GetGachaCounter(poolId)
        };

        for (int i = 0; i < count; i++)
        {
            var rarity = pity.RollWithPity(_rng, pool.RarityWeights, 3);
            var entries = _data.GetEntriesForRarity(poolId, (int)rarity);
            string? id = PickFromEntries(entries);
            if (string.IsNullOrEmpty(id))
                id = PickFromAll(pool);

            if (!string.IsNullOrEmpty(id))
            {
                bool isNew = !_save.Current.OwnedCharacters.Exists(c => c.CharacterId == id);
                GrantCharacter(id);
                var ch = _data.GetCharacter(id);
                results.Add(new PullResult
                {
                    Success = true,
                    CharacterId = id,
                    CharacterName = ch?.DisplayName ?? id,
                    Rarity = (int)rarity,
                    IsNew = isNew
                });
            }
        }

        _save.Current.SetGachaCounter(poolId, pity.Counter);
        _save.Save();
        return results;
    }

    private string? PickFromEntries(List<GachaPoolEntry> entries)
    {
        if (entries.Count == 0) return null;
        var ids = entries.Select(e => e.CharacterId).ToArray();
        var weights = entries.Select(e => e.Weight).ToArray();
        return _gacha.PickWeighted(ids, weights);
    }

    private string? PickFromAll(GachaPoolDataEntry pool)
    {
        if (pool.Entries.Count == 0) return null;
        var ids = pool.Entries.Select(e => e.CharacterId).ToArray();
        var weights = pool.Entries.Select(e => e.Weight).ToArray();
        return _gacha.PickWeighted(ids, weights);
    }

    private void GrantCharacter(string id)
    {
        if (!_save.Current.OwnedCharacters.Exists(c => c.CharacterId == id))
            _save.Current.OwnedCharacters.Add(new CharacterSaveState { CharacterId = id });
    }

    // ---- 培养 ----
    public void AddExp(string characterId, int exp)
    {
        var ch = _save.Current.OwnedCharacters.Find(c => c.CharacterId == characterId);
        if (ch == null) return;
        ch.TotalExp += exp;
        int newLevel = _progression.ExpToLevel(ch.TotalExp);
        if (newLevel != ch.Level)
        {
            ch.UnspentPoints += newLevel - ch.Level;
            ch.Level = newLevel;
        }
        _save.Save();
    }

    // ---- 战斗 ----
    public BattleRunResult RunStage(string stageId)
    {
        var owned = _save.Current.OwnedCharacters;
        int totalAtk = 0, totalHp = 0;
        foreach (var ch in owned)
        {
            var data = _data.GetCharacter(ch.CharacterId);
            if (data == null) continue;
            int s = Math.Max(1, ch.Stage);
            totalAtk += _progression.StatAtLevel(data.BaseStats[0], ch.Level, s, 1f);
            totalHp += _progression.StatAtLevel(data.BaseStats[2], ch.Level, s, 1f);
        }
        // 简化战斗：玩家总属性 vs 敌人固定属性
        int enemyAtk = 50, enemyHp = 500;
        int playerTurns = (int)Math.Ceiling((double)enemyHp / Math.Max(1, totalAtk));
        int enemyTurns = (int)Math.Ceiling((double)Math.Max(1, totalHp) / Math.Max(1, enemyAtk));
        bool victory = playerTurns <= enemyTurns;
        if (victory)
        {
            _save.Current.SoftCurrency += 100;
            _save.Save();
        }
        return new BattleRunResult
        {
            Victory = victory,
            OwnedCount = owned.Count,
            TotalAtk = totalAtk,
            TotalHp = totalHp
        };
    }

    public class BattleRunResult
    {
        public bool Victory;
        public int OwnedCount;
        public int TotalAtk;
        public int TotalHp;
    }
}
