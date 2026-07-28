using Milan.Infrastructure.Save;
using System.Collections.Generic;
using System.Linq;

namespace Milan.Services
{
    public class CollectionService
    {
        readonly SaveManager _save;
        public CollectionService(SaveManager save) { _save = save; }

        public List<CharacterSaveState> GetOwned() => _save.Current.OwnedCharacters;
        public CharacterSaveState Get(string id) => _save.Current.OwnedCharacters.Find(c => c.CharacterId == id);
        public bool Owns(string id) => _save.Current.OwnedCharacters.Exists(c => c.CharacterId == id);
        public int TotalCharacters() => _save.Current.OwnedCharacters.Count;
    }
}
