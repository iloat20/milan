package com.milan.game.domain.battle

/**
 * 八元素克制矩阵（2026-08 优化引入；元素键名沿用旧数据标识：
 * Metal=金 / Wood=木 / Water=水 / Flame=火 / Earth=土 / Light=光 / Shadow=暗 / Thunder=电）。
 *
 * 克制关系（攻方 → 被克方）：
 * - 五行相克环：Metal→Wood→Earth→Water→Flame→Metal；
 * - 光暗互克：Light↔Shadow（双向）；
 * - Thunder 克 Water（导电），被 Earth 克（大地接地）。
 *
 * 纯函数、无状态、无平台依赖（KMP commonMain）；战斗结算与 UI 展示共用同一份，
 * 是元素克制倍率的**单一事实来源**——禁止在别处就地写克制数字。
 */
object ElementChart {

    /** 克制时伤害乘算倍率。 */
    const val COUNTER_MULTIPLIER = 1.25

    /** 攻方元素 → 其克制的守方元素集合。未知/缺失键 = 无克制关系。 */
    private val counters: Map<String, Set<String>> = mapOf(
        "Metal" to setOf("Wood"),
        "Wood" to setOf("Earth"),
        "Earth" to setOf("Water", "Thunder"),
        "Water" to setOf("Flame"),
        "Flame" to setOf("Metal"),
        "Light" to setOf("Shadow"),
        "Shadow" to setOf("Light"),
        "Thunder" to setOf("Water"),
    )

    /**
     * 攻方对守方造成伤害的元素倍率。
     * 任一侧为 null/空串/未知元素时返回 1.0——内容数据脏值绝不抛异常（战斗路径禁炸）。
     */
    fun damageMultiplier(attackerElement: String?, defenderElement: String?): Double {
        if (attackerElement.isNullOrEmpty() || defenderElement.isNullOrEmpty()) return 1.0
        val hit = counters[attackerElement] ?: return 1.0
        return if (defenderElement in hit) COUNTER_MULTIPLIER else 1.0
    }
}
