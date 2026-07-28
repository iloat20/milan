using System.Collections.Generic;
using UnityEngine;

namespace Milan.Infrastructure.Save
{
    [System.Serializable] public class SaveData
    {
        public int Version = 1;
        public int SoftCurrency = 1000;
        public int HardCurrency = 0;
        public List<CharacterSaveState> OwnedCharacters = new();
        public List<string> OwnedSkins = new();
        public List<ItemSaveState> Items = new();
        public List<GachaCounterEntry> GachaCounters = new(); // JsonUtility-safe (Dict not serializable)
        public string UserId = "";
        public int ServerSyncStatus = 0;
        public string ToJson() => JsonUtility.ToJson(this);
        public static SaveData FromJson(string j) => JsonUtility.FromJson<SaveData>(j);
        public static SaveData CreateDefault() => new();
        public int GetGachaCounter(string poolId)
        {
            var e = GachaCounters.Find(x => x.PoolId == poolId);
            return e != null ? e.Count : 0;
        }
        public void SetGachaCounter(string poolId, int count)
        {
            var e = GachaCounters.Find(x => x.PoolId == poolId);
            if (e != null) e.Count = count; else GachaCounters.Add(new GachaCounterEntry { PoolId = poolId, Count = count });
        }
    }
    [System.Serializable] public class GachaCounterEntry { public string PoolId; public int Count; }
    [System.Serializable] public class CharacterSaveState
    {
        public string CharacterId;
        public int Level = 1, Stage = 1, Stars = 1;
        public int TotalExp = 0;          // 累计经验值（I-4）
        public int UnspentPoints = 0;     // 未消耗的天赋点（I-5）
        public List<string> TalentPoints = new();
    }
    [System.Serializable] public class ItemSaveState { public string ItemId; public int Count; }
}
