package com.milan.game.domain.progression

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 天赋效果类型枚举（22种）。
 *
 * 三层效果体系：
 * 1. 属性加成（百分比，进 ComputeStats）
 * 2. 战斗机制（常驻字段，进 UnitStats）
 * 3. 状态施加（攻击概率触发，需 Duration）
 * 4. 必杀强化（终极技能专用）
 */
@Serializable
enum class TalentEffectType {
    // ── 属性加成（百分比，进 ComputeStats）──
    @SerialName("AtkPercent") AtkPercent,
    @SerialName("DefPercent") DefPercent,
    @SerialName("HpPercent") HpPercent,
    @SerialName("SpdPercent") SpdPercent,

    // ── 战斗机制（常驻字段，进 UnitStats）──
    @SerialName("IgnoreDefense") IgnoreDefense,
    @SerialName("DamageReduction") DamageReduction,
    @SerialName("DodgeRate") DodgeRate,
    @SerialName("CritRate") CritRate,
    @SerialName("CritDamage") CritDamage,
    @SerialName("Lifesteal") Lifesteal,
    @SerialName("Thorn") Thorn,

    // ── 状态施加（攻击概率触发，需 Duration）──
    @SerialName("Poison") Poison,
    @SerialName("Burn") Burn,
    @SerialName("Bleed") Bleed,
    @SerialName("Disarm") Disarm,
    @SerialName("Stun") Stun,
    @SerialName("Chill") Chill,
    @SerialName("Stiff") Stiff,
    @SerialName("Taunt") Taunt,
    @SerialName("Unstoppable") Unstoppable,

    // ── 必杀强化（终极技能专用）──
    @SerialName("UltimateDamage") UltimateDamage,
    @SerialName("ChargeGain") ChargeGain,
}

/**
 * 天赋效果数据类。
 *
 * - [type] 效果类型
 * - [value] 数值或百分比（0.1 = 10%）
 * - [chance] 状态施加概率（0-1），非状态 = 0
 * - [duration] 状态持续回合，非状态 = 0
 */
@Serializable
data class TalentEffect(
    @SerialName("Type") val type: TalentEffectType,
    @SerialName("Value") val value: Float = 0f,
    @SerialName("Chance") val chance: Float = 0f,
    @SerialName("Duration") val duration: Int = 0,
)

/**
 * 天赋效果聚合结果（由 TalentEngine.talentEffects 计算）。
 *
 * 包含所有已点亮节点的效果总和，供 StatsCalculator 和战斗引擎使用。
 */
data class TalentEffectResult(
    // ── 属性加成（百分比）──
    val atkPercent: Float = 0f,
    val defPercent: Float = 0f,
    val hpPercent: Float = 0f,
    val spdPercent: Float = 0f,

    // ── 战斗机制（常驻字段）──
    val ignoreDefense: Float = 0f,
    val damageReduction: Float = 0f,
    val dodgeRate: Float = 0f,
    val critRate: Float = 0f,
    val critDamage: Float = 0f,
    val lifesteal: Float = 0f,
    val thorn: Float = 0f,

    // ── 状态施加（攻击概率触发）──
    val poisonChance: Float = 0f,
    val poisonDuration: Int = 0,
    val burnChance: Float = 0f,
    val burnDuration: Int = 0,
    val bleedChance: Float = 0f,
    val bleedDuration: Int = 0,
    val disarmChance: Float = 0f,
    val disarmDuration: Int = 0,
    val stunChance: Float = 0f,
    val stunDuration: Int = 0,
    val chillChance: Float = 0f,
    val chillDuration: Int = 0,
    val stiffChance: Float = 0f,
    val stiffDuration: Int = 0,
    val tauntChance: Float = 0f,
    val tauntDuration: Int = 0,
    val unstoppable: Boolean = false,

    // ── 必杀强化 ──
    val ultimateDamage: Float = 0f,
    val chargeGain: Float = 0f,
)
