namespace Milan.Infrastructure.Save;

/// <summary>
/// MAUI 版本地存档提供器（替代 Unity 版，使用 MAUI 文件系统替代 Application.persistentDataPath）。
/// </summary>
public class LocalSaveProvider : ISaveProvider
{
    readonly string _path;

    public LocalSaveProvider(string filename = "save.json")
    {
        // MAUI 专用数据目录
        var dir = FileSystem.AppDataDirectory;
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
