namespace Milan.Domain.Progression
{
    /// <summary>
    /// 养成经济公式的<b>单一事实来源</b>：等级上限、升级/突破/升星消耗、经验换算、重复补偿。
    ///
    /// 为什么单独成类：这些公式原先散落在 <c>GameService</c>（带 <c>using Android.Content</c>）里，
    /// 既无法被单元测试直接覆盖，也容易在 UI 侧被就地重算而与服务端口径漂移。
    /// 本类是纯 .NET（无 Android / 无 UnityEngine），可被 Tests/Milan.Tests 直接链接编译。
    ///
    /// 铁律：任何一处需要"这次升级要花多少"的地方，都必须调用这里，禁止就地写数字。
    /// </summary>
    public static class EconomyFormulas
    {
        /// <summary>等级上限随突破阶段提高：Stage×20（Stage1→20 级，Stage4→80 级）。</summary>
        public static int MaxLevelForStage(int stage) => System.Math.Max(1, stage) * 20;

        /// <summary>从 level 升到 level+1 的星尘消耗（随等级线性上升）。</summary>
        public static int LevelCost(int level) => System.Math.Max(1, level) * 50;

        /// <summary>stage→stage+1 突破所需星魂碎片（重复角色补偿货币）。</summary>
        public static int AscendFragments(int stage) => System.Math.Max(1, stage) * 20;

        /// <summary>stage→stage+1 突破所需星尘。</summary>
        public static int AscendSoft(int stage) => System.Math.Max(1, stage) * 500;

        /// <summary>stars→stars+1 升星所需星魂碎片（1★→2★ 耗 20，2★→3★ 耗 40…）。</summary>
        public static int StarUpFragments(int stars) => System.Math.Max(1, stars) * 20;

        /// <summary>本等级内升到下一级所需的经验（与 <see cref="ProgressionEngine.ExpToLevel"/> 同一口径：level×100）。</summary>
        public static int ExpForLevel(int level) => System.Math.Max(1, level) * 100;

        /// <summary>升到 level 级所需的累计经验（用于经验条定位）。level≤1 时为 0。</summary>
        public static int CumulativeExp(int level)
        {
            int lv = System.Math.Max(1, level);
            // Σ(k×100), k=1..lv-1 —— 与 ExpForLevel 保持同一递增口径。
            return 100 * (lv - 1) * lv / 2;
        }

        /// <summary>重复角色按稀有度补偿的星魂碎片数量（UR 50 / SSR 20 / SR 5 / R 1）。</summary>
        public static int FragmentsForRarity(int rarity) => rarity switch
        {
            4 => 50, // UR
            3 => 20, // SSR
            2 => 5,  // SR
            _ => 1,  // R
        };

        /// <summary>
        /// 计算在给定星尘预算下最多能连升几级，以及总花费。
        /// 抽成纯函数是为了让"批量升级（×5 / 升满）"的边界行为（预算不足只升部分、已满级返回 0）
        /// 可被单元测试锁死，而不必拉起 Android 运行时。
        /// </summary>
        /// <param name="currentLevel">当前等级。</param>
        /// <param name="maxLevel">该突破阶段的等级上限。</param>
        /// <param name="budget">可用星尘。</param>
        /// <param name="requested">期望升的级数（≤0 视为 0）。</param>
        /// <returns>(gained 实际可升级数, cost 总花费)。一级都升不了时为 (0, 0)。</returns>
        public static (int gained, int cost) PlanLevelUp(int currentLevel, int maxLevel, int budget, int requested)
        {
            if (requested <= 0) return (0, 0);
            int target = currentLevel, cost = 0;
            for (int i = 0; i < requested; i++)
            {
                if (target >= maxLevel) break;
                int c = LevelCost(target);
                if (budget < cost + c) break;
                cost += c;
                target++;
            }
            return (target - currentLevel, cost);
        }
    }
}
