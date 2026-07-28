namespace Milan.Domain.Progression
{
    public class ProgressionEngine
    {
        // Incremental cost to advance from level k to k+1 is k*100 exp.
        public int ExpToLevel(int totalExp)
        {
            int level = 1;
            int required = 100;
            while (totalExp >= required)
            {
                totalExp -= required;
                level++;
                required = level * 100;
            }
            return level;
        }

        public int StatAtLevel(int baseStat, int level, int stage, float stageMultiplier)
        {
            return (int)(baseStat * (1 + (level - 1) * 0.1f) * stage * stageMultiplier);
        }
    }
}
