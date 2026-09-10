package com.milan.game.services

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 装备内容数据模型（装备模板）。
 * 
 * 装备设计：
 * - 装备分为武器、头盔、铠甲、饰品四大类
 * - 每类装备有固定主属性（武器=攻击，头盔=生命，铠甲=防御，饰品=随机）
 * - 装备有1-4条随机副属性
 * - 装备有套装效果（2件套/4件套）
 */
@Serializable
data class EquipmentData(
    @SerialName("EquipmentId") var equipmentId: String = "",
    @SerialName("DisplayName") var displayName: String = "",
    @SerialName("Description") var description: String = "",
    @SerialName("Rarity") var rarity: Int = 1,  // 1=R, 2=SR, 3=SSR, 4=UR
    @SerialName("Type") var type: String = "",  // weapon/head/body/accessory
    @SerialName("SetId") var setId: String = "",  // 套装ID（空=无套装）
    @SerialName("BaseStats") var baseStats: List<StatData> = emptyList(),
    @SerialName("SubStatPool") var subStatPool: List<StatData> = emptyList(),
    @SerialName("MaxLevel") var maxLevel: Int = 15,
    @SerialName("ExpPerLevel") var expPerLevel: List<Int> = emptyList(),  // 每级消耗经验
    @SerialName("GoldPerLevel") var goldPerLevel: List<Int> = emptyList(),  // 每级消耗金币
)

/**
 * 属性数据（用于定义属性池）。
 */
@Serializable
data class StatData(
    @SerialName("StatType") var statType: String = "",
    @SerialName("MinValue") var minValue: Int = 0,
    @SerialName("MaxValue") var maxValue: Int = 0,
    @SerialName("IsPercentage") var isPercentage: Boolean = false,
    @SerialName("Weight") var weight: Int = 100,  // 随机权重
)

/**
 * 套装效果数据。
 */
@Serializable
data class EquipmentSetData(
    @SerialName("SetId") var setId: String = "",
    @SerialName("DisplayName") var displayName: String = "",
    @SerialName("Description") var description: String = "",
    @SerialName("TwoPieceBonus") var twoPieceBonus: SetBonus = SetBonus(),
    @SerialName("FourPieceBonus") var fourPieceBonus: SetBonus = SetBonus(),
)

/**
 * 套装加成效果。
 */
@Serializable
data class SetBonus(
    @SerialName("Description") var description: String = "",
    @SerialName("StatBonuses") var statBonuses: List<StatBonus> = emptyList(),
    @SerialName("SpecialEffect") var specialEffect: String = "",  // 特殊效果ID（空=无）
)

/**
 * 属性加成。
 */
@Serializable
data class StatBonus(
    @SerialName("StatType") var statType: String = "",
    @SerialName("Value") var value: Int = 0,
    @SerialName("IsPercentage") var isPercentage: Boolean = false,
)

/**
 * 装备模板内容（用于从data.json加载）。
 */
@Serializable
data class EquipmentTemplateContent(
    @SerialName("Equipments") var equipments: List<EquipmentData?> = emptyList(),
    @SerialName("Sets") var sets: List<EquipmentSetData?> = emptyList(),
)
