package com.milan.game.services

import com.milan.game.data.EquipmentSaveState

/**
 * 装备契约（2026-09-08 P1-5 接口化；2026-09-09 C3 补发放路径）。
 *
 * 获取途径：[grantEquipment]（爬塔里程碑 / 活动商店 / 剧情奖励可调用）。
 */
interface EquipmentApi {
    /** 装备内容模板（进程内不变；UI 解析 displayName/rarity 用）。 */
    val equipmentTemplates: List<EquipmentData>

    /** 发放装备到背包（按模板实例化主/副词条）。模板不存在返回 [WriteOutcome.Rejected]。 */
    suspend fun grantEquipment(templateId: String, level: Int = 1): WriteOutcome

    /** 指定角色已装备列表。 */
    fun getEquipped(characterId: String): List<EquipmentSaveState>

    /** 指定槽位已装备（未装备返回 null）。 */
    fun getEquipAtSlot(characterId: String, slot: String): EquipmentSaveState?

    /** 未装备的背包装备列表。 */
    fun getUnequippedEquipments(): List<EquipmentSaveState>

    /** 强化装备（多次；等级上限内）。 */
    suspend fun enhanceEquipment(equipmentId: String, times: Int = 1): EquipmentEnhanceOutcome

    /** 穿戴装备到指定槽位（槽位占用自动交换回背包）。 */
    suspend fun equipItem(characterId: String, equipmentId: String, slot: String): WriteOutcome

    /** 卸下指定槽位装备。 */
    suspend fun unequipItem(characterId: String, slot: String): WriteOutcome

    /** 分解装备（回收素材/星尘）。 */
    suspend fun dismantleEquipment(equipmentId: String): EquipmentDismantleOutcome

    /** 指定角色激活的套装效果列表。 */
    fun getActiveSetBonuses(characterId: String): List<SetBonusStatus>
}
