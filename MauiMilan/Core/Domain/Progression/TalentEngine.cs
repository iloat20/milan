using System.Collections.Generic;
using System.Linq;

namespace Milan.Domain.Progression
{
    public class TalentEngine
    {
        public bool CanAllocate(string nodeId, List<string> allocated, Dictionary<string, string[]> prereqs)
        {
            if (allocated.Contains(nodeId)) return false;
            // 根节点（无前置要求，不在字典中）也应可点；缺省视为「无前置」而非「锁死」。
            // 同时防御 reqs 本身为 null 时直接 NRE。
            if (!prereqs.TryGetValue(nodeId, out var reqs) || reqs == null)
                return true;
            return reqs.All(r => allocated.Contains(r));
        }

        /// <summary>返回「满树总点数」= 所有节点 cost 之和（非已分配点数）。
        /// 命名易误导调用方以为是已分配点数，故以文档显式说明语义。</summary>
        public int TotalPoints(Dictionary<string, int> nodeCosts)
        {
            return nodeCosts.Values.Sum();
        }
    }
}
