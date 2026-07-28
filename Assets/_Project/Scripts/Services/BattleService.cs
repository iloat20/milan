using UnityEngine;
using Milan.Data;
using Milan.Data.ScriptableObjects;
using Milan.Domain.Battle;
using Milan.Domain.Progression;
using Milan.Infrastructure.EventBus;
using Milan.Infrastructure.Save;
using System.Collections.Generic;
using System.Linq;

namespace Milan.Services
{
    public class BattleService
    {
        readonly SaveManager _save;
        public BattleService(SaveManager save) { _save = save; }

        public BattleResult RunStage(string stageId)
        {
            var playerTeam = BuildPlayerTeam();  // I-3: 从玩家已拥有角色构建队伍
            var sim = new BattleSimulator(new System.Random());
            var enemyTeam = GetEnemyTeam(stageId);
            var result = sim.Simulate(playerTeam, enemyTeam, 100);
            if (result.Victory)
            {
                _save.Current.SoftCurrency += 100;
                _save.Save();
            }
            EventBus.Publish(new BattleCompletedEvent { StageId = stageId, Victory = result.Victory });
            return result;
        }

        // I-3: 将玩家已拥有角色通过 ProgressionEngine 计算为战斗属性
        UnitStats[] BuildPlayerTeam()
        {
            var owned = _save.Current.OwnedCharacters;
            if (owned == null || owned.Count == 0) return new UnitStats[0];
            var allData = Resources.LoadAll<CharacterData>("Content/Characters");
            var stats = new List<UnitStats>();
            var engine = new ProgressionEngine();
            foreach (var ch in owned)
            {
                var data = allData.FirstOrDefault(d => d.CharacterId == ch.CharacterId);
                if (data == null || data.BaseStats == null || data.BaseStats.Length < 4) continue;
                int stageMult = Mathf.Max(1, ch.Stage);
                stats.Add(new UnitStats
                {
                    CharacterId = ch.CharacterId,
                    Atk = engine.StatAtLevel(data.BaseStats[0], ch.Level, stageMult, 1f),
                    Def = engine.StatAtLevel(data.BaseStats[1], ch.Level, stageMult, 1f),
                    Hp = engine.StatAtLevel(data.BaseStats[2], ch.Level, stageMult, 1f),
                    Spd = engine.StatAtLevel(data.BaseStats[3], ch.Level, stageMult, 1f),
                });
            }
            return stats.ToArray();
        }

        UnitStats[] GetEnemyTeam(string stageId)
        {
            return new[] { new UnitStats { Atk = 50, Hp = 500, Spd = 8 } };
        }
    }
}
