using UnityEngine;
namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/Item")]
    public class ItemData : ScriptableObject
    {
        public string ItemId;
        public string DisplayName;
        public ItemType Type;
        public int MaxStack = 9999;
        public Sprite Icon;
    }
}
