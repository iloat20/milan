package com.milan.game.domain.battle

/** 战斗单位属性（C# Milan.Domain.Battle.UnitStats 翻译）。 */
data class UnitStats(
    val atk: Int,
    val def: Int,
    val hp: Int,
    val spd: Int,
    val characterId: String = "",
)

/** 战斗结算结果（C# Milan.Domain.Battle.BattleResult 翻译）。 */
data class BattleResult(
    val victory: Boolean,
    val turns: Int,
    /** 我方（teamA）剩余总血量。 */
    val remainingHp: Int,
    /** 敌方（teamB）剩余总血量。调用方据此续接战斗状态，避免重置血条。 */
    val opponentRemainingHp: Int,
)
