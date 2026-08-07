package com.milan.game.domain.progression

/**
 * 角色养成属性引擎（C# Milan.Domain.Progression.ProgressionEngine 翻译）。
 */
class ProgressionEngine {

    /**
     * 由累计经验推导等级：每级 k→k+1 需 k×100 经验（k 为当前等级）。
     * 语义与 [EconomyFormulas.expForLevel] / [EconomyFormulas.cumulativeExp] 同口径。
     */
    fun expToLevel(totalExp: Int): Int {
        var exp = totalExp
        var level = 1
        var required = 100
        while (exp >= required) {
            exp -= required
            level++
            required = level * 100
        }
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
