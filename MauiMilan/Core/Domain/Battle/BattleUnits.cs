namespace Milan.Domain.Battle
{
    public struct UnitStats
    {
        public int Atk;
        public int Def;
        public int Hp;
        public int Spd;
        public string CharacterId;
    }

    public struct BattleResult
    {
        public bool Victory;
        public int Turns;
        /// <summary>我方（teamA）剩余总血量。</summary>
        public int RemainingHp;
        /// <summary>敌方（teamB）剩余总血量。调用方据此续接战斗状态，避免重置血条。</summary>
        public int OpponentRemainingHp;
    }
}