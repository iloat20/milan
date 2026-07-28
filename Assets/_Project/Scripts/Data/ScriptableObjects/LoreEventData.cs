using UnityEngine;
namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/Lore Event")]
    public class LoreEventData : ScriptableObject
    {
        public string EventId;
        public string DisplayName;
        public string[] RequiredCharacterIds;
        public int[] RequiredStages;
        public string[] RewardItemIds;
        public string UnlockPoolId;
    }
}
