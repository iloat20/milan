package com.milan.game.domain.battle

/**
 * 战斗单位属性（C# Milan.Domain.Battle.UnitStats 翻译）。
 * 2026-08 KMP 下沉：自 app 迁入 shared commonMain。
 */
data class UnitStats(
    val atk: Int,
    val def: Int,
    val hp: Int,
    val spd: Int,
    val characterId: String = "",
    /**
     * 元素键名（Metal/Wood/Water/Flame/Earth/Light/Shadow/Thunder，沿用旧数据标识）。
     * 默认空串 = 无元素（旧调用方/桌面模拟器不传也不受影响）；空串不参与克制与共鸣。
     */
    val element: String = "",
    /** 暴击率（0.0~1.0，由装备/共鸣注入；0 = 无暴击来源，不触发暴击判定）。 */
    val critRate: Double = 0.0,
    /** 暴击伤害倍率（1.0 = 无暴击加成，2.0 = 暴击造成 200% 伤害）。 */
    val critDmg: Double = 1.0,
)

/** 单次攻击事件（2026-08 战报：纯展示数据，不落盘，仅随本次 [BattleResult] 返回）。 */
data class StrikeEvent(
    /** 回合序号（1 起）。 */
    val turn: Int,
    val attackerId: String,
    val attackerElement: String,
    val targetId: String,
    val targetElement: String,
    /** 实际结算伤害（已含克制乘算与保底）。 */
    val damage: Int,
    /** 本次攻击是否击杀目标。 */
    val targetDefeated: Boolean,
)

/** 战斗结算结果（C# Milan.Domain.Battle.BattleResult 翻译）。 */
data class BattleResult(
    val victory: Boolean,
    val turns: Int,
    /** 我方（teamA）剩余总血量。 */
    val remainingHp: Int,
    /** 敌方（teamB）剩余总血量。调用方据此续接战斗状态，避免重置血条。 */
    val opponentRemainingHp: Int,
    /**
     * 逐回合攻击事件流（战报展示；默认空 = 无记录）。
     * 默认值保证旧构造调用方（测试/桌面模拟器）零改动兼容。
     */
    val log: List<StrikeEvent> = emptyList(),
    /**
     * 平局标记：回合耗尽双方仍有存活单位时为 true。
     * 调用方据此决定不消耗门票/不发奖励（与胜负区分）。
     */
    val draw: Boolean = false,
)
