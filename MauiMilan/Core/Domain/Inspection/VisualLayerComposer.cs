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
            return allocatedNodeIds.Count() >= totalNodes;
        }
    }
}
