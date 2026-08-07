package com.milan.game.domain.progression

/**
 * 养成经济公式的**单一事实来源**：等级上限、升级/突破/升星消耗、经验换算、重复补偿。
 * （C# Milan.Domain.Progression.EconomyFormulas 翻译）
 *
 * 铁律：任何一处需要"这次升级要花多少"的地方，都必须调用这里，禁止就地写数字。
 */
object EconomyFormulas {

    /** 等级上限随突破阶段提高：Stage×20（Stage1→20 级，Stage4→80 级）。 */
    fun maxLevelForStage(stage: Int): Int = stage.coerceAtLeast(1) * 20

    /** 从 level 升到 level+1 的星尘消耗（随等级线性上升）。 */
    fun levelCost(level: Int): Int = level.coerceAtLeast(1) * 50

    /** stage→stage+1 突破所需星魂碎片（重复角色补偿货币）。 */
    fun ascendFragments(stage: Int): Int = stage.coerceAtLeast(1) * 20

    /** stage→stage+1 突破所需星尘。 */
    fun ascendSoft(stage: Int): Int = stage.coerceAtLeast(1) * 500

    /** stars→stars+1 升星所需星魂碎片（1★→2★ 耗 20，2★→3★ 耗 40…）。 */
    fun starUpFragments(stars: Int): Int = stars.coerceAtLeast(1) * 20

    /** 本等级内升到下一级所需的经验（与 [ProgressionEngine.expToLevel] 同一口径：level×100）。 */
    fun expForLevel(level: Int): Int = level.coerceAtLeast(1) * 100

    /** 升到 level 级所需的累计经验（用于经验条定位）。level≤1 时为 0。 */
    fun cumulativeExp(level: Int): Int {
        val lv = level.coerceAtLeast(1)
        // Σ(k×100), k=1..lv-1 —— 与 expForLevel 保持同一递增口径。
        return 100 * (lv - 1) * lv / 2
    }

    /** 重复角色按稀有度补偿的星魂碎片数量（UR 50 / SSR 20 / SR 5 / R 1）。未知值回退最低补偿。 */
    fun fragmentsForRarity(rarity: Int): Int = when (rarity) {
        4 -> 50 // UR
        3 -> 20 // SSR
        2 -> 5  // SR
        else -> 1 // R（含未知稀有度兜底，绝不抛异常中断抽卡事务）
    }

    /**
     * 计算在给定星尘预算下最多能连升几级，以及总花费。
     * 纯函数：批量升级（×5 / 升满）的边界行为（预算不足只升部分、已满级返回 0）在此锁死。
     *
     * @param currentLevel 当前等级
     * @param maxLevel 该突破阶段的等级上限
     * @param budget 可用星尘
     * @param requested 期望升的级数（≤0 视为 0）
     * @return 实际可升级数与总花费；一级都升不了时为 (0, 0)
     */
    fun planLevelUp(currentLevel: Int, maxLevel: Int, budget: Int, requested: Int): PlanResult {
        if (requested <= 0) return PlanResult(0, 0)
        var target = currentLevel
        var cost = 0
        for (i in 0 until requested) {
            if (target >= maxLevel) break
            val c = levelCost(target)
            if (budget < cost + c) break
            cost += c
            target++
        }
        return PlanResult(target - currentLevel, cost)
    }
}

/** [EconomyFormulas.planLevelUp] 的结果。 */
data class PlanResult(val gained: Int, val cost: Int)
