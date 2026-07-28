using UnityEngine;
namespace Milan.Infrastructure.Save
{
    public class LocalSaveProvider : ISaveProvider
    {
        readonly string _p;
        public LocalSaveProvider(string f = "save.json")
            => _p = System.IO.Path.Combine(Application.persistentDataPath, f);
        public void Save(string j) => System.IO.File.WriteAllText(_p, j);
        public string Load() => System.IO.File.Exists(_p) ? System.IO.File.ReadAllText(_p) : null;
        public void Delete() { if (System.IO.File.Exists(_p)) System.IO.File.Delete(_p); }
    }
}
