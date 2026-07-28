using UnityEngine;
namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/Character")]
    public class CharacterData : ScriptableObject
    {
        public string CharacterId;
        public string DisplayName;
        public WorldType World;
        public Rarity BaseRarity;
        public string LoreId;
        public string TalentTreeId;
        public string DefaultSkinId;
        public int[] BaseStats; // [ATK, DEF, HP, SPD]
        public int MaxStage;
        public int MaxStars;
        public bool CanBreakthrough;
        public string InspectionCameraProfileId;
    }
}
