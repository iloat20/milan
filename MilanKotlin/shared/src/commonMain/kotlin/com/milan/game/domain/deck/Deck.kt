package com.milan.game.domain.deck

import com.milan.game.data.Rarity

/**
 * 卡组（编队）的领域模型（2026-09-08 P0-2）。
 *
 * ## 为什么需要它
 * 在此之前项目**没有任何卡组抽象**：出战编队就是 `SaveData.formation: List<String>`，
 * 而「容量上限」「必须已拥有」两条约束以内联 if 的形式散落在服务层的多处写入口，
 * 各写一遍。这带来两个后果：
 *   1. 同一约束在多处重复实现，改一处漏一处；
 *   2. 新增赛季禁用表 / 稀有度上限 / 阵营要求时**没有落点**，只能继续往服务层堆 if。
 *
 * ## 边界（重要）
 * 本类型是**瞬态领域形状**，不是持久化形状：
 * 存档的唯一事实来源仍是 `SaveData.formation: List<String>`（app 层，含 `@SerialName` 契约，
 * 不可改动）。服务层负责两者双向映射。领域层（本模块）禁止依赖 app 层的存档模型，
 * 因此这里不放「玩家持有卡牌的养成状态」——那属于 `CharacterSaveState` 的职责。
 *
 * 由此，`Card`（静态定义）与 `CardInstance`（玩家持有态）两个概念**暂不引入**：
 * 它们的现有载体分别在 app 层的内容模型与存档模型里，在分层下沉（P2 多模块拆分）
 * 完成前引入只会制造概念重叠的死抽象。当前先补齐真正缺位的「约束落点」。
 */
class Deck private constructor(val slots: List<String>) {

    val size: Int get() = slots.size

    fun contains(cardId: String): Boolean = cardId in slots

    /**
     * 切换某张卡的入队状态（已在则移除，未在则追加）。空/空白 id 视为无操作。
     * 返回新实例——本类型不可变。
     */
    fun withToggled(cardId: String): Deck = when {
        cardId.isBlank() -> this
        cardId in slots -> Deck(slots - cardId)
        else -> Deck(slots + cardId)
    }

    /**
     * 按序校验规则，**短路返回第一条违反**（规则顺序即优先级）。
     * 全部通过返回 null。
     */
    fun validate(rules: List<DeckRule>, context: DeckContext): DeckViolation? {
        for (rule in rules) {
            rule.check(this, context)?.let { return it }
        }
        return null
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Deck && slots == other.slots)

    override fun hashCode(): Int = slots.hashCode()

    override fun toString(): String = "Deck($slots)"

    companion object {
        val Empty: Deck = Deck(emptyList())

        /**
         * 由任意 id 序列构造：trim → 丢弃空白 → **去重保序**。
         * 去重保序是构造期不变量，保证 [size] 与「不同卡牌数」始终一致，
         * 容量校验不会被重复 id 虚增/虚减。
         */
        fun of(ids: Iterable<String>): Deck =
            Deck(ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
    }
}

/** 规则校验所需的外界事实。规则本身不查询存档，只消费上下文。 */
data class DeckContext(
    val ownedIds: Set<String> = emptySet(),
    val cardRarityProvider: (String) -> Rarity? = { null },
    val cardElementProvider: (String) -> String? = { null },
)

/**
 * 可组合的卡组约束。返回 null 表示通过，否则返回具体违反。
 *
 * 用 fun interface 以便调用方就地写自定义规则（如赛季禁用表）：
 * ```kotlin
 * val noBanned = DeckRule { deck, _ -> ... }
 * ```
 * 新增约束 = 新增一个规则对象，[Deck] 本体无需改动（开闭原则）。
 */
fun interface DeckRule {
    fun check(deck: Deck, context: DeckContext): DeckViolation?
}

/** 具体的卡组违反。sealed 类型便于 UI 精确提示与埋点，无需解析字符串。 */
sealed interface DeckViolation {
    /** 槽位超出上限。 */
    data class TooManySlots(val size: Int, val max: Int) : DeckViolation

    /** 含有未拥有的卡牌。携带全部未拥有 id，便于一次性提示。 */
    data class NotOwned(val ids: List<String>) : DeckViolation

    /** 含有被禁用/限制使用的卡牌（赛季轮换、平衡性封禁等扩展位）。 */
    data class Banned(val ids: List<String>) : DeckViolation

    /** 含有稀有度超过上限的卡牌。 */
    data class OverRarity(val ids: List<String>, val maxRarity: Rarity) : DeckViolation

    /** 含有不在允许阵营内的卡牌。 */
    data class WrongElement(val ids: List<String>, val allowedElements: Set<String>) : DeckViolation
}

/** 容量上限约束。 */
data class MaxSizeRule(val max: Int) : DeckRule {
    override fun check(deck: Deck, context: DeckContext): DeckViolation? =
        if (deck.size > max) DeckViolation.TooManySlots(deck.size, max) else null
}

/** 必须已拥有约束。 */
object OwnershipRule : DeckRule {
    override fun check(deck: Deck, context: DeckContext): DeckViolation? {
        val missing = deck.slots.filter { it !in context.ownedIds }
        return if (missing.isEmpty()) null else DeckViolation.NotOwned(missing)
    }
}

/**
 * 稀有度上限约束。卡组中所有卡牌的稀有度不得超过指定上限。
 *
 * @param maxRarity 允许的最高稀有度（如 SSR 则 UR 卡牌不可入队）
 * @param cardRarityProvider 查询卡牌稀有度的函数（id → Rarity?），由服务层注入
 */
data class MaxRarityRule(
    val maxRarity: Rarity,
    val cardRarityProvider: (String) -> Rarity?,
) : DeckRule {
    override fun check(deck: Deck, context: DeckContext): DeckViolation? {
        val overRarity = deck.slots.filter { id ->
            val rarity = cardRarityProvider(id)
            rarity != null && rarity.value > maxRarity.value
        }
        return if (overRarity.isEmpty()) null else DeckViolation.OverRarity(overRarity, maxRarity)
    }
}

/**
 * 阵营（元素）约束。卡组中所有卡牌必须属于指定阵营之一。
 *
 * @param allowedElements 允许的元素集合（空集 = 不限制）
 * @param cardElementProvider 查询卡牌元素的函数（id → String?），由服务层注入
 */
data class ElementRule(
    val allowedElements: Set<String>,
    val cardElementProvider: (String) -> String?,
) : DeckRule {
    override fun check(deck: Deck, context: DeckContext): DeckViolation? {
        if (allowedElements.isEmpty()) return null
        val wrongElement = deck.slots.filter { id ->
            val element = cardElementProvider(id)
            element != null && element !in allowedElements
        }
        return if (wrongElement.isEmpty()) null else DeckViolation.WrongElement(wrongElement, allowedElements)
    }
}
