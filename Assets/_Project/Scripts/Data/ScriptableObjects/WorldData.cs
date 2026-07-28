using UnityEngine;
namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/World")]
    public class WorldData : ScriptableObject
    {
        public WorldType WorldType;
        public string DisplayName;
        [TextArea] public string Description;
        public Material DefaultMaterial;
        public bool UseCelShading = true;
    }
}
