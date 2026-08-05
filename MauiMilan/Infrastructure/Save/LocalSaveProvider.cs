namespace Milan.Infrastructure.Save;

/// <summary>
/// 本地存档提供器（使用 Android 内部存储）。
/// </summary>
public class LocalSaveProvider : ISaveProvider
{
    readonly string _path;

    public LocalSaveProvider(string filename = "save.json")
    {
        // 使用应用内部存储目录
        var dir = System.Environment.GetFolderPath(System.Environment.SpecialFolder.Personal);
        _path = Path.Combine(dir, filename);
    }

    public bool Save(string json)
    {
        var dir = Path.GetDirectoryName(_path);
        if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            Directory.CreateDirectory(dir);

        // 原子写档：先写临时文件，成功后再替换正式文件。
        // 直接 WriteAllText 覆盖时若进程中途被杀，存档会被截断损坏。
        var tmp = _path + ".tmp";
        try
        {
            File.WriteAllText(tmp, json);
            if (File.Exists(_path))
                File.Replace(tmp, _path, _path + ".bak");
            else
                File.Move(tmp, _path);
            return true;
        }
        catch (Exception)
        {
            // 写盘失败：清理残留临时文件，避免下次 Load 读到半截 .tmp。
            try { if (File.Exists(tmp)) File.Delete(tmp); } catch { }
            return false;
        }
    }

    public string Load()
    {
        if (!File.Exists(_path)) return null;
        return File.ReadAllText(_path);
    }

    public bool Exists() => File.Exists(_path);

    public string? LoadBackup()
    {
        if (File.Exists(_path + ".bak")) return SafeRead(_path + ".bak");
        if (File.Exists(_path + ".tmp")) return SafeRead(_path + ".tmp");
        return null;
    }

    static string? SafeRead(string p)
    {
        try { return File.ReadAllText(p); }
        catch { return null; }
    }

    public void Delete()
    {
        if (File.Exists(_path)) File.Delete(_path);
    }
}
