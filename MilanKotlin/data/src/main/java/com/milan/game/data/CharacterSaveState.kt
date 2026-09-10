package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 已拥有角色的养成状态（C# CharacterSaveState，公共字段模型）。 */
@Serializable
class CharacterSaveState(
    @SerialName("CharacterId") var characterId: String = "",
    @SerialName("Level") var level: Int = 1,
    @SerialName("Stage") var stage: Int = 1,
    @SerialName("Stars") var stars: Int = 1,
    @SerialName("TotalExp") var totalExp: Int = 0,
    @SerialName("UnspentPoints") var unspentPoints: Int = 0,
    @SerialName("TalentPoints") var talentPoints: List<String?> = emptyList(),
    // ── 装备系统新增（2026-08 装备系统）──
    /** 装备槽位（槽位ID → 装备存档ID；空槽不存储）。 */
    @SerialName("Equipment") var equipment: Map<String, String?> = emptyMap(),
) {
    /**
     * 获取指定槽位的装备ID。
     */
    fun getEquipment(slot: String): String? = equipment[slot]
    
    /**
     * 设置指定槽位的装备。
     */
    fun setEquipment(slot: String, equipmentId: String?) {
        if (equipmentId == null) {
            equipment = equipment - slot
        } else {
            equipment = equipment + (slot to equipmentId)
        }
    }
    
    /**
     * 获取所有已装备的装备ID列表。
     */
    fun getEquippedIds(): List<String> = equipment.values.filterNotNull()
}
