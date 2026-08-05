using System.Collections.Generic;
using Milan.Domain.Progression;
using Xunit;

namespace Milan.Tests
{
    public class TalentTests
    {
        private readonly TalentEngine _engine = new();

        [Fact]
        public void CanAllocate_AlreadyAllocated_ReturnsFalse()
        {
            var alloc = new List<string> { "n1" };
            Assert.False(_engine.CanAllocate("n1", alloc, new Dictionary<string, string[]>()));
        }

        [Fact]
        public void CanAllocate_RootNodeWithoutPrereq_ReturnsTrue()
        {
            var alloc = new List<string>();
            Assert.True(_engine.CanAllocate("root", alloc, new Dictionary<string, string[]>()));
        }

        [Fact]
        public void CanAllocate_AllPrereqsMet_ReturnsTrue()
        {
            var alloc = new List<string> { "a", "b" };
            var pre = new Dictionary<string, string[]> { ["c"] = new[] { "a", "b" } };
            Assert.True(_engine.CanAllocate("c", alloc, pre));
        }

        [Fact]
        public void CanAllocate_MissingPrereq_ReturnsFalse()
        {
            var alloc = new List<string> { "a" };
            var pre = new Dictionary<string, string[]> { ["c"] = new[] { "a", "b" } };
            Assert.False(_engine.CanAllocate("c", alloc, pre));
        }

        [Fact]
        public void CanAllocate_NullPrereqArray_ReturnsTrue()
        {
            var alloc = new List<string>();
            var pre = new Dictionary<string, string[]> { ["c"] = null };
            Assert.True(_engine.CanAllocate("c", alloc, pre));
        }

        [Fact]
        public void TotalPoints_SumsCosts()
        {
            var costs = new Dictionary<string, int> { ["a"] = 1, ["b"] = 2, ["c"] = 3 };
            Assert.Equal(6, _engine.TotalPoints(costs));
        }
    }
}
