using UnityEngine;
namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/Character Lore")]
    public class CharacterLore : ScriptableObject
    {
        public string CharacterId;
        [TextArea(3, 10)] public string Origin;
        [TextArea(2, 6)] public string Personality;
        [TextArea(2, 6)] public string Motivation;
        public Relation[] Relationships;
        public string VoiceId;
    }

    [System.Serializable]
    public class Relation
    {
        public string TargetCharacterId;
        public RelationType Type;
        [TextArea] public string Description;
    }
}
