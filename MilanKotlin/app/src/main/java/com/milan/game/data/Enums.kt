package com.milan.game.data

/**
 * 稀有度（与 C# Milan.Data.Rarity 对齐）。
 * R=1, SR=2, SSR=3, UR=4 —— 值语义与旧档/旧逻辑一致，禁止修改。
 */
enum class Rarity(val value: Int) {
    R(1),
    SR(2),
    SSR(3),
    UR(4),
    ;

    companion object {
        fun fromValue(v: Int): Rarity? = entries.firstOrNull { it.value == v }
    }
}

/** 道具类型（C# Milan.Data.ItemType）。 */
enum class ItemType {
    SOFT_CURRENCY,
    HARD_CURRENCY,
    EXP_MATERIAL,
    STAGE_MATERIAL,
    STAR_FRAGMENT,
    TALENT_ITEM,
    BREAKTHROUGH_ITEM,
    BATTLE_TICKET,
}

/** 角色间关系类型（C# Milan.Data.RelationType）。 */
enum class RelationType {
    RIVAL,
    ALLY,
    CREATOR,
    CREATION,
    CROSS_WORLD_BOND,
}

/** 世界观（C# Milan.Data.WorldType）。 */
enum class WorldType {
    SHINWA,
    AETHER,
    IRONVEIL,
}
