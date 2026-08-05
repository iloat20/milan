using Milan.Maui;

namespace Milan.Infrastructure.Save;

public interface ISaveProvider
{
    bool Save(string json);
    string Load();
    void Delete();
    bool Exists();
    /// <summary>读取备份档（.bak 优先，其次 .tmp），损坏/缺失返回 null。</summary>
    string? LoadBackup();
}

public class SaveManager
{
    readonly ISaveProvider _prov;
    public SaveData Current { get; private set; }

    public SaveManager(ISaveProvider p) => _prov = p;

    /// <summary>
    /// 载入存档。区分三类情况：
    ///   1. 文件不存在 → 正常新号，用默认档；
    ///   2. 主档存在但损坏/截断 → 先尝试 .bak / .tmp 备份，全失败才回退默认档并留痕（绝不静默抹档）；
    ///   3. IO 瞬时异常 → 回退默认档。
    /// 任何分支都不抛异常（GameState 在静态初始化中调用，抛异常会让 App 永久打不开）。
    /// </summary>
    public SaveData Load()
    {
        try
        {
            if (!_prov.Exists())
            {
                Current = SaveData.CreateDefault();
                Migrate(Current);
                return Current;
            }
            var j = _prov.Load();
            if (SaveData.TryParse(j, out var data))
            {
                Current = data;
                Migrate(Current);
                return Current;
            }
            // 主档损坏：尝试备份档恢复
            var backup = _prov.LoadBackup();
            if (backup != null && SaveData.TryParse(backup, out var recovered))
            {
                Current = recovered;
                Migrate(Current);
                CrashReporter.Boot("save.load.recovered.from.backup");
                return Current;
            }
            // 全部失败：回退默认档并留痕（不静默抹档）
            Current = SaveData.CreateDefault();
            CrashReporter.Boot("save.load.fallback: all sources corrupt");
        }
        catch
        {
            Current = SaveData.CreateDefault();
        }
        Migrate(Current);
        return Current;
    }

    /// <summary>
    /// 持久化存档。返回是否成功；失败时已通过 CrashReporter 留痕，调用方应据此回滚内存改动。
    /// 未 Load 直接保存（Current 为 null）时防御性新建默认档，避免 NRE。
    /// </summary>
    public bool Save()
    {
        if (Current == null) Current = SaveData.CreateDefault();
        try
        {
            return _prov.Save(Current.ToJson());
        }
        catch (System.Exception ex)
        {
            CrashReporter.Boot("save.failed: " + ex.Message);
            return false;
        }
    }

    void Migrate(SaveData d) { /* version migration stub */ }
}
