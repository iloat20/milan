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

    public void Save(string json)
    {
        var dir = Path.GetDirectoryName(_path);
        if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            Directory.CreateDirectory(dir);
        File.WriteAllText(_path, json);
    }

    public string Load()
    {
        if (!File.Exists(_path)) return null;
        return File.ReadAllText(_path);
    }

    public void Delete()
    {
        if (File.Exists(_path)) File.Delete(_path);
    }
}
