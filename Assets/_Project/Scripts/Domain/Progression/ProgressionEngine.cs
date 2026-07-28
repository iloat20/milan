namespace Milan.Domain.Progression
{
    public class ProgressionEngine
    {
        // Simple curve: level N requires N*100 cumulative exp.
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
