using UnityEngine;
namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/Talent Node")]
    public class TalentNodeData : ScriptableObject
    {
        public string NodeId;
        public string DisplayName;
        [TextArea] public string Description;
        public string BranchId;
        public int Cost = 1;
        public string[] PrerequisiteNodeIds;
        public string VisualLayerId;
    }
}
