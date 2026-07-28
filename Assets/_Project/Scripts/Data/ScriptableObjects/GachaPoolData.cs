using UnityEngine;
namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/Gacha Pool")]
    public class GachaPoolData : ScriptableObject
    {
        public string PoolId;
        public string DisplayName;
        public int[] RarityWeights;
        public int HardPity = 90;
        public string[] UpItemIds;
        public int UpGuarantee = 1;
        public string CostItemId;
        public int SingleCost = 100;
        public int TenCost = 1000;
    }
}
