using Android.Content;
using Milan.Domain.Battle;
using Milan.Domain.Progression;
using Milan.Infrastructure.Save;
using Milan.Maui.Services;

namespace Milan.Maui;

/// <summary>
/// Process-wide singleton holding the shared GameService (and thus the SaveManager / save file).
/// All Activities resolve to one instance so a pull in GachaActivity is immediately reflected in
/// the Character list, and the save file is loaded exactly once.
/// </summary>
public static class GameState
{
    public static GameService Service { get; } = new GameService();

    static bool _initialized;
    static readonly object _gate = new();

    /// <summary>Load content from the asset (or fallback). Idempotent; safe to call from every Activity.</summary>
    public static void EnsureInitialized(Context context)
    {
        if (_initialized) return;
        lock (_gate)
        {
            if (_initialized) return;
            try { Service.Initialize(context); }
            catch { Service.LoadFallback(); }
            _initialized = true;
        }
    }

    // ---- Convenience accessors used by the UI ----

    public static int Currency => Service.SaveData.SoftCurrency;

    public static string CurrencyLabel => $"星尘: {Currency:N0}";

    public static int OwnedCount => Service.SaveData.OwnedCharacters.Count;

    public static List<OwnedCharacterView> Owned()
    {
        var result = new List<OwnedCharacterView>();
        foreach (var ch in Service.SaveData.OwnedCharacters)
        {
            var def = Service.Characters.FirstOrDefault(c => c.CharacterId == ch.CharacterId);
            result.Add(new OwnedCharacterView
            {
                Save = ch,
                Def = def
            });
        }
        return result;
    }

    public static int Stat(Milan.Domain.Battle.UnitStats s, int idx) => idx switch
    {
        0 => s.Atk,
        1 => s.Def,
        2 => s.Hp,
        3 => s.Spd,
        _ => 0
    };

    /// <summary>Compute live battle stats from base + progression, mirroring the Unity BattleService.
    /// 含已点亮天赋的分支加成（单一事实来源：详情页/养成页/战斗页都走这里）。</summary>
    public static Milan.Domain.Battle.UnitStats ComputeStats(OwnedCharacterView ch)
        => ComputeStatsAt(ch, ch.Save.Level, System.Math.Max(1, ch.Save.Stage));

    /// <summary>在指定等级/阶段/星级下计算属性（用于养成页"下一级 / 下一阶 / 升星"预测值）。
    /// 天赋加成按当前已点亮节点计算，不随等级/阶段/星级假设改变。
    /// <paramref name="stars"/> 缺省（&lt;0）时取 ch.Save.Stars 的实时值。</summary>
    public static Milan.Domain.Battle.UnitStats ComputeStatsAt(OwnedCharacterView ch, int level, int stage, int stars = -1)
    {
        var def = ch.Def;
        var save = ch.Save;
        var engine = new ProgressionEngine();
        int stg = System.Math.Max(1, stage);
        int lv = System.Math.Max(1, level);
        int st = stars < 0 ? (ch.Save?.Stars ?? 1) : stars;
        // 星级小幅加成：每星 +5%（1★→×1.0，满 7★→×1.30）。并入 StatAtLevel 的倍率槽。
        float starMul = ProgressionEngine.StarMultiplier(st);
        if (def == null)
            return new Milan.Domain.Battle.UnitStats { CharacterId = save.CharacterId, Hp = 1 };

        // BaseStats 来自外部 data.json，长度不可信。越界会直接抛 IndexOutOfRangeException，
        // 而本方法在详情页/检视页/战斗页的构建路径上被调用 —— 抛了就是闪退。
        var bs = def.BaseStats;
        int Base(int i, int fallback) => bs != null && i < bs.Length ? bs[i] : fallback;

        // 已点亮天赋的分支加成：power→攻, defense→防+生命, utility→速度（每节点 +3%）。
        float atkB = 0, defB = 0, hpB = 0, spdB = 0;
        var tree = ch.Talent;
        if (tree?.Nodes != null && save.TalentPoints != null)
        {
            foreach (var n in tree.Nodes)
            {
                if (n == null || !save.TalentPoints.Contains(n.NodeId)) continue;
                if (n.BranchId == "branch_power") atkB += 0.03f;
                else if (n.BranchId == "branch_defense") { defB += 0.03f; hpB += 0.03f; }
                else if (n.BranchId == "branch_utility") spdB += 0.03f;
            }
        }

        return new Milan.Domain.Battle.UnitStats
        {
            CharacterId = save.CharacterId,
            Atk = (int)(engine.StatAtLevel(Base(0, 100), lv, stg, starMul) * (1 + atkB)),
            Def = (int)(engine.StatAtLevel(Base(1, 80), lv, stg, starMul) * (1 + defB)),
            Hp = (int)(engine.StatAtLevel(Base(2, 1000), lv, stg, starMul) * (1 + hpB)),
            Spd = (int)(engine.StatAtLevel(Base(3, 12), lv, stg, starMul) * (1 + spdB)),
        };
    }
}

public class OwnedCharacterView
{
    public CharacterSaveState Save = null!;
    public CharacterDataEntry? Def;
    public string Name => Def?.DisplayName ?? Save.CharacterId;
    public string Title => Def?.Title ?? "";
    public int Rarity => Def?.BaseRarity ?? 1;
    public string World => Def?.World ?? "Shinwa";
    public string Element => Def?.Element ?? "Flame";
    public string Lore => Def?.Lore ?? "";
    public bool CanBreakthrough => Def?.CanBreakthrough ?? false;
    public TalentTreeData? Talent => GameState.Service.TalentTrees.FirstOrDefault(t => t.TreeId == Def?.TalentTreeId);
}
