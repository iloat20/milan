package com.milan.game.data

/**
 * 道具类型（C# Milan.Data.ItemType）。
 */
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
