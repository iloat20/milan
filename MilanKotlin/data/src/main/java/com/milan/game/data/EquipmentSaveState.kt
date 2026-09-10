package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 装备存档状态模型。
 * 
 * 装备系统设计：
 * - 每个角色可装备5件装备：武器、头盔、铠甲、饰品1、饰品2
 * - 装备有基础属性和随机词条
 * - 装备可强化（+1到+15）
 * - 装备有套装效果（2件套/4件套）
 */
@Serializable
class EquipmentSaveState(
    @SerialName("EquipmentId") var equipmentId: String = "",
    @SerialName("TemplateId") var templateId: String = "",  // 装备模板ID，对应EquipmentData
    @SerialName("Level") var level: Int = 1,
    @SerialName("Exp") var exp: Int = 0,
    @SerialName("MainStat") var mainStat: StatValue = StatValue(),
    @SerialName("SubStats") var subStats: List<StatValue?> = emptyList(),
    @SerialName("Locked") var locked: Boolean = false,  // 锁定防误操作
) {
    companion object {
        /** 装备槽位类型 */
        const val SLOT_WEAPON = "weapon"
        const val SLOT_HEAD = "head"
        const val SLOT_BODY = "body"
        const val SLOT_ACCESSORY1 = "accessory1"
        const val SLOT_ACCESSORY2 = "accessory2"
        
        /** 所有槽位 */
        val ALL_SLOTS = listOf(
            SLOT_WEAPON, SLOT_HEAD, SLOT_BODY, SLOT_ACCESSORY1, SLOT_ACCESSORY2
        )
        
        /** 最大强化等级 */
        const val MAX_LEVEL = 15
        
        /** 最大副词条数量 */
        const val MAX_SUB_STATS = 4
    }
}

/**
 * 属性值（主属性/副属性通用）。
 */
@Serializable
data class StatValue(
    @SerialName("StatType") var statType: String = "",  // 攻击/防御/生命/速度/暴击率/暴击伤害等
    @SerialName("Value") var value: Int = 0,
    @SerialName("IsPercentage") var isPercentage: Boolean = false,  // 是否百分比属性
) {
    companion object {
        /** 属性类型常量 */
        const val STAT_ATTACK = "attack"
        const val STAT_DEFENSE = "defense"
        const val STAT_HP = "hp"
        const val STAT_SPEED = "speed"
        const val STAT_CRIT_RATE = "crit_rate"
        const val STAT_CRIT_DMG = "crit_dmg"
        const val STAT_HEALING = "healing"
        const val STAT_ELEMENTAL_MASTERY = "elemental_mastery"
        const val STAT_ENERGY_RECHARGE = "energy_recharge"
        
        /** 所有属性类型 */
        val ALL_STATS = listOf(
            STAT_ATTACK, STAT_DEFENSE, STAT_HP, STAT_SPEED,
            STAT_CRIT_RATE, STAT_CRIT_DMG, STAT_HEALING,
            STAT_ELEMENTAL_MASTERY, STAT_ENERGY_RECHARGE
        )
    }
}
