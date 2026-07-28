namespace Milan.Infrastructure.Save;

public interface ISaveProvider
{
    void Save(string json);
    string Load();
    void Delete();
}

public class SaveManager
{
    readonly ISaveProvider _prov;
    public SaveData Current { get; private set; }

    public SaveManager(ISaveProvider p) => _prov = p;

    public SaveData Load()
    {
        var j = _prov.Load();
        Current = j != null ? SaveData.FromJson(j) : SaveData.CreateDefault();
        Migrate(Current);
        return Current;
    }

    public void Save() => _prov.Save(Current.ToJson());

    void Migrate(SaveData d) { /* version migration stub */ }
}
