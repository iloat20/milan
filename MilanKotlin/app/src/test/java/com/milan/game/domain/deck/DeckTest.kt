package com.milan.game.domain.deck

import com.milan.game.data.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡组（[Deck]）领域模型单测（2026-09-08 P0-2）。
 *
 * 背景：项目此前**没有任何卡组领域抽象**——编队就是 `SaveData.formation: List<String>`，
 * 「容量上限 5 / 必须已拥有」两条约束以内联 if 形式散落在 TowerService 的两处写入口
 * （setFormation 与 toggleFormation 各写一遍）。新增赛季禁用表、稀有度上限、阵营要求时
 * 无处落地，只能继续往服务层堆 if。
 *
 * 本测试固化 [Deck] 的取值语义与 [DeckRule] 的可组合校验行为。
 */
class DeckTest {

    private val owned = setOf("a", "b", "c", "d", "e", "f")

    // 卡牌稀有度映射（测试用）
    private val rarityMap = mapOf(
        "a" to Rarity.R,
        "b" to Rarity.SR,
        "c" to Rarity.SSR,
        "d" to Rarity.UR,
        "e" to Rarity.R,
        "f" to Rarity.SR,
    )

    // 卡牌元素映射（测试用）
    private val elementMap = mapOf(
        "a" to "Flame",
        "b" to "Water",
        "c" to "Wood",
        "d" to "Metal",
        "e" to "Earth",
        "f" to "Light",
    )

    private fun deckContext(
        ownedIds: Set<String> = owned,
        rarityProvider: (String) -> Rarity? = { rarityMap[it] },
        elementProvider: (String) -> String? = { elementMap[it] },
    ) = DeckContext(
        ownedIds = ownedIds,
        cardRarityProvider = rarityProvider,
        cardElementProvider = elementProvider,
    )

    // ───────────────── 构造与取值语义 ─────────────────

    @Test
    fun of_deduplicatesPreservingOrder() {
        val deck = Deck.of(listOf("a", "b", "a", "c", "b"))
        assertEquals(listOf("a", "b", "c"), deck.slots)
        assertEquals(3, deck.size)
    }

    @Test
    fun of_blankIdsAreDropped() {
        // 空 id 会让「已拥有」校验产生无意义违反，且无法映射到任何卡牌定义
        assertEquals(listOf("a"), Deck.of(listOf("a", "", " ")).slots)
    }

    @Test
    fun empty_hasNoSlotsAndPassesVacuously() {
        assertEquals(0, Deck.Empty.size)
        assertNull(Deck.Empty.validate(listOf(MaxSizeRule(5)), deckContext()))
    }

    // ───────────────── MaxSizeRule ─────────────────

    @Test
    fun maxSizeRule_acceptsAtLimit() {
        val deck = Deck.of(listOf("a", "b", "c", "d", "e"))
        assertNull(deck.validate(listOf(MaxSizeRule(5)), deckContext()))
    }

    @Test
    fun maxSizeRule_rejectsOverLimit() {
        val deck = Deck.of(listOf("a", "b", "c", "d", "e", "f"))
        val v = deck.validate(listOf(MaxSizeRule(5)), deckContext())
        assertEquals(DeckViolation.TooManySlots(size = 6, max = 5), v)
    }

    // ───────────────── OwnershipRule ─────────────────

    @Test
    fun ownershipRule_acceptsAllOwned() {
        val deck = Deck.of(listOf("a", "c", "e"))
        assertNull(deck.validate(listOf(OwnershipRule), deckContext()))
    }

    @Test
    fun ownershipRule_reportsEveryUnownedId() {
        // 一次性报出全部未拥有 id：UI 才能准确提示，而不是逐个试错
        val deck = Deck.of(listOf("a", "zz", "yy"))
        val v = deck.validate(listOf(OwnershipRule), deckContext())
        assertEquals(DeckViolation.NotOwned(listOf("zz", "yy")), v)
    }

    // ───────────────── MaxRarityRule ─────────────────

    @Test
    fun maxRarityRule_acceptsWithinLimit() {
        // R/SR/SSR 都在 SSR 上限内
        val deck = Deck.of(listOf("a", "b", "c"))
        val rule = MaxRarityRule(Rarity.SSR) { rarityMap[it] }
        assertNull(deck.validate(listOf(rule), deckContext()))
    }

    @Test
    fun maxRarityRule_rejectsOverLimit() {
        // UR 超过 SSR 上限
        val deck = Deck.of(listOf("a", "b", "d"))
        val rule = MaxRarityRule(Rarity.SSR) { rarityMap[it] }
        val v = deck.validate(listOf(rule), deckContext())
        assertEquals(DeckViolation.OverRarity(listOf("d"), Rarity.SSR), v)
    }

    @Test
    fun maxRarityRule_unknownCardPasses() {
        // 未知卡牌（稀有度查询返回 null）不触发违反
        val deck = Deck.of(listOf("a", "zz"))
        val rule = MaxRarityRule(Rarity.SSR) { rarityMap[it] }
        assertNull(deck.validate(listOf(rule), deckContext()))
    }

    // ───────────────── ElementRule ─────────────────

    @Test
    fun elementRule_acceptsMatchingElements() {
        // 允许 Flame/Water，a=Flame, b=Water 都匹配
        val deck = Deck.of(listOf("a", "b"))
        val rule = ElementRule(setOf("Flame", "Water")) { elementMap[it] }
        assertNull(deck.validate(listOf(rule), deckContext()))
    }

    @Test
    fun elementRule_rejectsWrongElement() {
        // 允许 Flame/Water，c=Wood 不匹配
        val deck = Deck.of(listOf("a", "b", "c"))
        val rule = ElementRule(setOf("Flame", "Water")) { elementMap[it] }
        val v = deck.validate(listOf(rule), deckContext())
        assertEquals(DeckViolation.WrongElement(listOf("c"), setOf("Flame", "Water")), v)
    }

    @Test
    fun elementRule_emptyAllowedSetPassesAll() {
        // 空集合 = 不限制
        val deck = Deck.of(listOf("a", "b", "c"))
        val rule = ElementRule(emptySet()) { elementMap[it] }
        assertNull(deck.validate(listOf(rule), deckContext()))
    }

    // ───────────────── 规则组合 ─────────────────

    @Test
    fun rules_shortCircuitOnFirstViolation() {
        // 同时违反容量与拥有权：返回**第一条**规则的违反（规则顺序即优先级）
        val deck = Deck.of(listOf("a", "b", "c", "d", "e", "f", "zz"))
        val ctx = deckContext()
        val v = deck.validate(listOf(MaxSizeRule(5), OwnershipRule), ctx)
        assertTrue("容量规则在前应优先命中", v is DeckViolation.TooManySlots)

        val v2 = deck.validate(listOf(OwnershipRule, MaxSizeRule(5)), ctx)
        assertTrue("拥有权规则在前应优先命中", v2 is DeckViolation.NotOwned)
    }

    @Test
    fun rules_multipleViolationTypes() {
        // 验证稀有度规则与阵营规则可组合
        val deck = Deck.of(listOf("a", "b", "d")) // d=UR 超稀有度, c=Wood 超阵营
        val rules = listOf(
            MaxRarityRule(Rarity.SSR) { rarityMap[it] },
            ElementRule(setOf("Flame", "Water")) { elementMap[it] },
        )
        val v = deck.validate(rules, deckContext())
        // 稀有度规则在前，应先命中
        assertEquals(DeckViolation.OverRarity(listOf("d"), Rarity.SSR), v)
    }

    @Test
    fun customRule_composesWithBuiltins() {
        // 可扩展性验证：新增赛季约束 = 新增一个规则对象，不改 Deck 本体
        val noBanned = DeckRule { deck, _ ->
            deck.slots.firstOrNull { it == "b" }?.let { DeckViolation.Banned(listOf(it)) }
        }
        val ctx = deckContext()
        assertNull(Deck.of(listOf("a", "c")).validate(listOf(noBanned), ctx))
        assertEquals(
            DeckViolation.Banned(listOf("b")),
            Deck.of(listOf("a", "b")).validate(listOf(noBanned), ctx),
        )
    }

    // ───────────────── withToggled ─────────────────

    @Test
    fun withToggled_addsThenRemoves() {
        val deck = Deck.of(listOf("a", "b"))
        val added = deck.withToggled("c")
        assertEquals(listOf("a", "b", "c"), added.slots)
        assertEquals(listOf("a", "b"), added.withToggled("c").slots)
    }

    @Test
    fun withToggled_ignoresBlankId() {
        val deck = Deck.of(listOf("a"))
        assertEquals(deck.slots, deck.withToggled("").slots)
    }

    @Test
    fun contains_reflectsSlots() {
        val deck = Deck.of(listOf("a", "b"))
        assertTrue(deck.contains("b"))
        assertFalse(deck.contains("z"))
    }
}
