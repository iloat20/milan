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
        public int RemainingHp;
    }
}