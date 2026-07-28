using UnityEngine;
namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/Skin")]
    public class SkinData : ScriptableObject
    {
        public string SkinId;
        public string DisplayName;
        public string OwnerCharacterId;
        public WorldType World;
        public GameObject ModelPrefab;
        public bool IsStageSkin;
        public int StatBonusPercent = 0;
    }
}
