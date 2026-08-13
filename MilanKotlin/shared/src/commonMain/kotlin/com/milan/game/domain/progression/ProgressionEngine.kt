package com.milan.game.domain.progression

/**
 * 角色养成属性引擎（C# Milan.Domain.Progression.ProgressionEngine 翻译）。
 *
 * 2026-08 KMP 下沉：自 app 迁入 shared commonMain，与 EconomyFormulas 同层。
 */
class ProgressionEngine {

    /**
     * 由累计经验推导等级：每级 k→k+1 需 k×100 经验（k 为当前等级）。
     * 语义与 [EconomyFormulas.expForLevel] / [EconomyFormulas.cumulativeExp] 同口径。
     *
     * P3-9：实现委托 [EconomyFormulas.cumulativeExp]（单一事实来源）做逆推，
     * 消除与旧「循环扣减」实现并存的漂移点（两实现此前由测试锁口径，仍可能悄然分叉）。
     */
    fun expToLevel(totalExp: Int): Int {
        var level = 1
        while (EconomyFormulas.cumulativeExp(level + 1) <= totalExp) level++
        return level
    }

    /**
     * 计算属性值：base × (1 + (level-1)×10%) × stage × stageMultiplier。
     * 防御非法输入：level/stage ≤0 会让乘法结果归零/变负，导致 HP/攻防异常，一律钳到 1。
     */
    fun statAtLevel(baseStat: Int, level: Int, stage: Int, stageMultiplier: Float): Int {
        val lv = level.coerceAtLeast(1)
        val st = stage.coerceAtLeast(1)
        return (baseStat * (1 + (lv - 1) * 0.1f) * st * stageMultiplier).toInt()
    }

    companion object {
        /** 升星属性倍率：每星 +5%（1★→×1.0，满 7★→×1.30）。单一事实来源。 */
        fun starMultiplier(stars: Int): Float = 1f + (stars.coerceAtLeast(1) - 1) * 0.05f
    }
}
