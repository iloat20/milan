using UnityEngine;
namespace Milan.Data.ScriptableObjects
{
    [CreateAssetMenu(menuName = "Milan/Data/Talent Tree")]
    public class TalentTreeData : ScriptableObject
    {
        public string TreeId;
        public string[] BranchIds;
        public Sprite[] BranchIcons;
        public TalentNodeData[] Nodes;
        public int TotalPointsToComplete;
    }
}
