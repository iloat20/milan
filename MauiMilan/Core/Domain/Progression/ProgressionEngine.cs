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
            // 防御非法输入：stage<=0 会让乘法结果归零/变负，导致 HP/攻防异常。
            level = System.Math.Max(1, level);
            stage = System.Math.Max(1, stage);
            return (int)(baseStat * (1 + (level - 1) * 0.1f) * stage * stageMultiplier);
        }

        /// <summary>升星属性倍率：每星 +5%（1★→×1.0，满 7★→×1.30）。
        /// 抽成纯方法作为单一事实来源，GameState.ComputeStatsAt 与单元测试共用，避免公式漂移。</summary>
        public static float StarMultiplier(int stars) => 1f + (System.Math.Max(1, stars) - 1) * 0.05f;
    }
}
