package com.milan.game.domain.battle

/**
 * 八元素克制矩阵 + 元素反应系统（2026-08 优化引入；2026-09-07 扩展元素反应）。
 *
 * 元素键名沿用旧数据标识：
 * Metal=金 / Wood=木 / Water=水 / Flame=火 / Earth=土 / Light=光 / Shadow=暗 / Thunder=电。
 *
 * 克制关系（攻方 → 被克方）：
 * - 五行相克环：Metal→Wood→Earth→Water→Flame→Metal；
 * - 光暗互克：Light↔Shadow（双向）；
 * - Thunder 克 Water（导电），被 Earth 克（大地接地）。
 *
 * 元素反应系统：
 * - 两种不同元素同时作用于目标时，有概率触发元素反应
 * - 不同元素组合产生不同效果（伤害加成、控制、持续伤害等）
 * - 元素反应有冷却时间，防止无限触发
 *
 * 纯函数、无状态、无平台依赖（KMP commonMain）；战斗结算与 UI 展示共用同一份，
 * 是元素克制倍率和元素反应效果的**单一事实来源**——禁止在别处就地写克制数字或反应效果。
 */
object ElementChart {

    /** 克制时伤害乘算倍率。 */
    const val COUNTER_MULTIPLIER = 1.25

    /** 元素反应触发基础概率（%）。 */
    const val REACTION_BASE_CHANCE = 80

    /** 元素反应冷却回合数。 */
    const val REACTION_COOLDOWN = 2

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
     * 元素反应定义。
     * @param element1 第一种元素
     * @param element2 第二种元素
     * @param reactionName 反应名称
     * @param damageMultiplier 伤害倍率（相对于基础伤害）
     * @param effectType 附加效果类型
     * @param effectValue 效果值
     * @param effectDuration 效果持续回合数
     */
    data class ElementReaction(
        val element1: String,
        val element2: String,
        val reactionName: String,
        val damageMultiplier: Double = 1.0,
        val effectType: EffectType? = null,
        val effectValue: Int = 0,
        val effectDuration: Int = 0,
    )

    /** 元素反应表：两种元素组合 → 反应效果。 */
    private val reactions: Map<Pair<String, String>, ElementReaction> = mapOf(
        // 水 + 火 = 蒸发（伤害提升）
        Pair("Water", "Flame") to ElementReaction(
            "Water", "Flame", "蒸发",
            damageMultiplier = 1.5,
        ),
        Pair("Flame", "Water") to ElementReaction(
            "Flame", "Water", "蒸发",
            damageMultiplier = 1.5,
        ),
        
        // 水 + 雷 = 感电（持续伤害）
        Pair("Water", "Thunder") to ElementReaction(
            "Water", "Thunder", "感电",
            damageMultiplier = 1.2,
            effectType = EffectType.POISON,
            effectValue = 30,
            effectDuration = 3,
        ),
        Pair("Thunder", "Water") to ElementReaction(
            "Thunder", "Water", "感电",
            damageMultiplier = 1.2,
            effectType = EffectType.POISON,
            effectValue = 30,
            effectDuration = 3,
        ),
        
        // 火 + 雷 = 超载（范围伤害）
        Pair("Flame", "Thunder") to ElementReaction(
            "Flame", "Thunder", "超载",
            damageMultiplier = 1.8,
        ),
        Pair("Thunder", "Flame") to ElementReaction(
            "Thunder", "Flame", "超载",
            damageMultiplier = 1.8,
        ),
        
        // 水 + 土 = 结晶（获得护盾）
        Pair("Water", "Earth") to ElementReaction(
            "Water", "Earth", "结晶",
            damageMultiplier = 1.0,
            effectType = EffectType.BUFF_DEF,
            effectValue = 50,
            effectDuration = 3,
        ),
        Pair("Earth", "Water") to ElementReaction(
            "Earth", "Water", "结晶",
            damageMultiplier = 1.0,
            effectType = EffectType.BUFF_DEF,
            effectValue = 50,
            effectDuration = 3,
        ),
        
        // 火 + 土 = 熔炼（降低防御）
        Pair("Flame", "Earth") to ElementReaction(
            "Flame", "Earth", "熔炼",
            damageMultiplier = 1.3,
            effectType = EffectType.DEBUFF_DEF,
            effectValue = 30,
            effectDuration = 2,
        ),
        Pair("Earth", "Flame") to ElementReaction(
            "Earth", "Flame", "熔炼",
            damageMultiplier = 1.3,
            effectType = EffectType.DEBUFF_DEF,
            effectValue = 30,
            effectDuration = 2,
        ),
        
        // 金 + 木 = 穿透（无视防御）
        Pair("Metal", "Wood") to ElementReaction(
            "Metal", "Wood", "穿透",
            damageMultiplier = 1.4,
        ),
        Pair("Wood", "Metal") to ElementReaction(
            "Wood", "Metal", "穿透",
            damageMultiplier = 1.4,
        ),
        
        // 金 + 火 = 淬火（攻击提升）
        Pair("Metal", "Flame") to ElementReaction(
            "Metal", "Flame", "淬火",
            damageMultiplier = 1.2,
            effectType = EffectType.BUFF_ATK,
            effectValue = 40,
            effectDuration = 2,
        ),
        Pair("Flame", "Metal") to ElementReaction(
            "Flame", "Metal", "淬火",
            damageMultiplier = 1.2,
            effectType = EffectType.BUFF_ATK,
            effectValue = 40,
            effectDuration = 2,
        ),
        
        // 木 + 土 = 生长（治疗效果）
        Pair("Wood", "Earth") to ElementReaction(
            "Wood", "Earth", "生长",
            damageMultiplier = 1.0,
            effectType = EffectType.HEAL,
            effectValue = 80,
        ),
        Pair("Earth", "Wood") to ElementReaction(
            "Earth", "Wood", "生长",
            damageMultiplier = 1.0,
            effectType = EffectType.HEAL,
            effectValue = 80,
        ),
        
        // 光 + 暗 = 湮灭（高额伤害）
        Pair("Light", "Shadow") to ElementReaction(
            "Light", "Shadow", "湮灭",
            damageMultiplier = 2.0,
        ),
        Pair("Shadow", "Light") to ElementReaction(
            "Shadow", "Light", "湮灭",
            damageMultiplier = 2.0,
        ),
        
        // 雷 + 金 = 共鸣（速度提升）
        Pair("Thunder", "Metal") to ElementReaction(
            "Thunder", "Metal", "共鸣",
            damageMultiplier = 1.1,
            effectType = EffectType.BUFF_SPD,
            effectValue = 30,
            effectDuration = 2,
        ),
        Pair("Metal", "Thunder") to ElementReaction(
            "Metal", "Thunder", "共鸣",
            damageMultiplier = 1.1,
            effectType = EffectType.BUFF_SPD,
            effectValue = 30,
            effectDuration = 2,
        ),
        
        // 水 + 光 = 净化（清除减益）
        Pair("Water", "Light") to ElementReaction(
            "Water", "Light", "净化",
            damageMultiplier = 1.0,
            effectType = EffectType.HEAL,
            effectValue = 50,
        ),
        Pair("Light", "Water") to ElementReaction(
            "Light", "Water", "净化",
            damageMultiplier = 1.0,
            effectType = EffectType.HEAL,
            effectValue = 50,
        ),
        
        // 暗 + 雷 = 腐蚀（持续伤害+降低攻击）
        Pair("Shadow", "Thunder") to ElementReaction(
            "Shadow", "Thunder", "腐蚀",
            damageMultiplier = 1.3,
            effectType = EffectType.POISON,
            effectValue = 40,
            effectDuration = 3,
        ),
        Pair("Thunder", "Shadow") to ElementReaction(
            "Thunder", "Shadow", "腐蚀",
            damageMultiplier = 1.3,
            effectType = EffectType.POISON,
            effectValue = 40,
            effectDuration = 3,
        ),
        
        // 暗 + 木 = 枯萎（降低速度）
        Pair("Shadow", "Wood") to ElementReaction(
            "Shadow", "Wood", "枯萎",
            damageMultiplier = 1.2,
            effectType = EffectType.DEBUFF_SPD,
            effectValue = 30,
            effectDuration = 2,
        ),
        Pair("Wood", "Shadow") to ElementReaction(
            "Wood", "Shadow", "枯萎",
            damageMultiplier = 1.2,
            effectType = EffectType.DEBUFF_SPD,
            effectValue = 30,
            effectDuration = 2,
        ),
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

    /**
     * 检查两种元素是否能触发元素反应。
     * @return 如果能触发反应返回反应定义，否则返回 null。
     */
    fun getReaction(element1: String?, element2: String?): ElementReaction? {
        if (element1.isNullOrEmpty() || element2.isNullOrEmpty()) return null
        if (element1 == element2) return null  // 相同元素不触发反应
        return reactions[Pair(element1, element2)]
    }

    /**
     * 计算元素反应触发概率。
     * 基础概率 + 攻方元素精通加成。
     * @param attackerElement 攻方元素
     * @param attackerMastery 攻方元素精通（0-100）
     * @return 触发概率（0-100）
     */
    fun reactionChance(attackerElement: String?, attackerMastery: Int = 0): Int {
        if (attackerElement.isNullOrEmpty()) return 0
        val masteryBonus = (attackerMastery * 0.2).toInt().coerceIn(0, 20)
        return (REACTION_BASE_CHANCE + masteryBonus).coerceIn(0, 100)
    }

    /**
     * 获取所有可能的元素反应组合（用于UI显示）。
     * @return 元素反应列表
     */
    fun getAllReactions(): List<ElementReaction> {
        return reactions.values.distinctBy { it.reactionName }
    }
}
