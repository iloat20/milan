using UnityEngine;
using System.Collections.Generic;

namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/Gacha Pool")]
    public class GachaPoolData : ScriptableObject
    {
        public string PoolId;
        public string DisplayName;
        public int[] RarityWeights;           // 各稀有度的权重，索引 0=R, 1=SR, 2=SSR, 3=UR
        public int HardPity = 90;
        public string CostItemId;
        public int SingleCost = 100;
        public int TenCost = 1000;

        // 卡池角色条目：每个条目绑定一个角色到某个稀有度，并带权重
        public GachaPoolEntry[] Entries;

        // 辅助方法：获取某稀有度的所有条目
        public List<GachaPoolEntry> GetEntriesForRarity(Rarity rarity)
        {
            var list = new List<GachaPoolEntry>();
            if (Entries == null) return list;
            int target = (int)rarity;
            foreach (var e in Entries)
                if (e.RarityIndex == target) list.Add(e);
            return list;
        }
    }

    [System.Serializable]
    public class GachaPoolEntry
    {
        public string CharacterId;
        public int RarityIndex;   // 1=R, 2=SR, 3=SSR, 4=UR
        public int Weight = 100;
    }
}
