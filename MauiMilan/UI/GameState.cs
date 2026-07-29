using Android.Content;
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
            try { Service.InitializeAsync(context).GetAwaiter().GetResult(); }
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

    /// <summary>Compute live battle stats from base + progression, mirroring the Unity BattleService.</summary>
    public static Milan.Domain.Battle.UnitStats ComputeStats(OwnedCharacterView ch)
    {
        var def = ch.Def;
        var save = ch.Save;
        var engine = new ProgressionEngine();
        int stg = System.Math.Max(1, save.Stage);
        if (def == null)
            return new Milan.Domain.Battle.UnitStats { CharacterId = save.CharacterId, Hp = 1 };
        return new Milan.Domain.Battle.UnitStats
        {
            CharacterId = save.CharacterId,
            Atk = engine.StatAtLevel(def.BaseStats[0], save.Level, stg, 1f),
            Def = engine.StatAtLevel(def.BaseStats[1], save.Level, stg, 1f),
            Hp = engine.StatAtLevel(def.BaseStats[2], save.Level, stg, 1f),
            Spd = engine.StatAtLevel(def.BaseStats[3], save.Level, stg, 1f),
        };
    }
}

public class OwnedCharacterView
{
    public CharacterSaveState Save = null!;
    public CharacterDataEntry? Def;
    public string Name => Def?.DisplayName ?? Save.CharacterId;
    public int Rarity => Def?.BaseRarity ?? 1;
    public string World => Def?.World ?? "Shinwa";
    public bool CanBreakthrough => Def?.CanBreakthrough ?? false;
}
