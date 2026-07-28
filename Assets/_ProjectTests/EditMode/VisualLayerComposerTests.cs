using NUnit.Framework;
using Milan.Domain.Inspection;
using System.Collections.Generic;

[TestFixture]
public class VisualLayerComposerTests
{
    [Test]
    public void Compose_MapsNodesToLayers()
    {
        var composer = new VisualLayerComposer();
        var map = new Dictionary<string, string> { ["n1"] = "fire"; ["n2"] = "shadow"; ["n3"] = "fire" };
        var result = composer.Compose(new[] { "n1", "n2" }, map);
        Assert.AreEqual(2, result.Count);
        Assert.IsTrue(result.Contains("fire"));
        Assert.IsTrue(result.Contains("shadow"));
    }

    [Test]
    public void IsFullTree_ChecksCount()
    {
        var composer = new VisualLayerComposer();
        Assert.IsTrue(composer.IsFullTree(new[] { "a", "b", "c" }, 3));
        Assert.IsFalse(composer.IsFullTree(new[] { "a", "b" }, 3));
    }
}
