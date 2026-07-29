using System.Collections.Generic;
using System.Linq;

namespace Milan.Domain.Progression
{
    public class TalentEngine
    {
        public bool CanAllocate(string nodeId, List<string> allocated, Dictionary<string, string[]> prereqs)
        {
            if (allocated.Contains(nodeId)) return false;
            if (!prereqs.TryGetValue(nodeId, out var reqs)) return false;
            return reqs.All(r => allocated.Contains(r));
        }

        public int TotalPoints(Dictionary<string, int> nodeCosts)
        {
            return nodeCosts.Values.Sum();
        }
    }
}
