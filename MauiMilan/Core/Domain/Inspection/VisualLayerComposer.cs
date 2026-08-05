using System.Collections.Generic;
using System.Linq;

namespace Milan.Domain.Inspection
{
    public class VisualLayerComposer
    {
        // Given allocated talent node IDs and a map nodeId -> visualLayerId,
        // return the set of active VFX layer IDs.
        public HashSet<string> Compose(IEnumerable<string> allocatedNodeIds, Dictionary<string, string> nodeToLayer)
        {
            var layers = new HashSet<string>();
            foreach (var nodeId in allocatedNodeIds)
            {
                if (nodeToLayer.TryGetValue(nodeId, out var layer) && layer != null)
                    layers.Add(layer);
            }
            return layers;
        }

        public bool IsFullTree(IEnumerable<string> allocatedNodeIds, int totalNodes)
        {
            // totalNodes <= 0（空树/未初始化）不能判为「满树」，否则无天赋角色被误判解锁 VFX。
            if (totalNodes <= 0) return false;
            return allocatedNodeIds.Count() >= totalNodes;
        }
    }
}
