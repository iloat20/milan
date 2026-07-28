using Milan.Domain.Battle;
using Milan.Infrastructure.EventBus;
using Milan.Infrastructure.Save;

namespace Milan.Services
{
    public class BattleService
    {
        readonly SaveManager _save;
        public BattleService(SaveManager save) { _save = save; }

        public BattleResult RunStage(string stageId, UnitStats[] playerTeam)
        {
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

        UnitStats[] GetEnemyTeam(string stageId)
        {
            return new[] { new UnitStats { Atk = 50, Hp = 500, Spd = 8 } };
        }
    }
}
