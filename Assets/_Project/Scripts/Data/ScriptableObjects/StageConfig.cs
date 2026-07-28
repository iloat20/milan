using UnityEngine;
namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/Stage Config")]
    public class StageConfig : ScriptableObject
    {
        public int StageIndex;
        public string DisplayName;
        public int MaxLevel = 10;
        public float StatMultiplier = 1f;
        public string SkinId;
        public string[] RequiredMaterialIds;
        public int[] RequiredMaterialCounts;
        public int RequiredStars = 0;
    }
}
