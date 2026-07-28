using Milan.Data.ScriptableObjects;
using Milan.Domain.Progression;
using Milan.Infrastructure.EventBus;
using Milan.Infrastructure.Save;
using System.Collections.Generic;
using System.Linq;

namespace Milan.Services
{
    public class ProgressionService
    {
        readonly SaveManager _save;
        readonly ProgressionEngine _prog;
        readonly TalentEngine _talent;

        public ProgressionService(SaveManager save)
        {
            _save = save;
            _prog = new ProgressionEngine();
            _talent = new TalentEngine();
        }

        public void AddExp(string characterId, int exp)
        {
            var ch = _save.Current.OwnedCharacters.Find(c => c.CharacterId == characterId);
            if (ch == null) return;
            ch.TotalExp += exp;  // I-4: 累计经验值
            int newLevel = _prog.ExpToLevel(ch.TotalExp);
            if (newLevel != ch.Level)
            {
                int gained = newLevel - ch.Level;
                ch.Level = newLevel;
                ch.UnspentPoints += gained;  // I-5: 每升一级获得 1 天赋点
                EventBus.Publish(new CharacterLevelUpEvent { CharacterId = characterId, NewLevel = newLevel });
            }
            _save.Save();
        }

        public bool AllocateTalent(string characterId, string nodeId, TalentTreeData tree)
        {
            var ch = _save.Current.OwnedCharacters.Find(c => c.CharacterId == characterId);
            if (ch == null) return false;
            if (ch.UnspentPoints <= 0) return false;  // I-5: 需要未消耗的天赋点
            var prereqs = tree.Nodes.ToDictionary(n => n.NodeId, n => n.PrerequisiteNodeIds);
            if (!_talent.CanAllocate(nodeId, ch.TalentPoints, prereqs)) return false;
            ch.TalentPoints.Add(nodeId);
            ch.UnspentPoints--;  // I-5: 消耗 1 天赋点
            EventBus.Publish(new TalentAllocatedEvent { CharacterId = characterId, NodeId = nodeId });
            _save.Save();
            return true;
        }
    }
}
