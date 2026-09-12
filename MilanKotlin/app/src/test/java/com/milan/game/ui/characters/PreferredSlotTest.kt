package com.milan.game.ui.characters

import com.milan.game.data.EquipmentSaveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 装备穿脱 UI 辅助逻辑：槽位建议映射（2026-09-12 游戏性）。 */
class PreferredSlotTest {

    @Test
    fun weaponHeadBody_mapToFixedSlots() {
        val empty = mapOf<String, EquipmentSaveState?>()
        assertEquals(EquipmentSaveState.SLOT_WEAPON, preferredSlotForType("weapon", empty))
        assertEquals(EquipmentSaveState.SLOT_HEAD, preferredSlotForType("head", empty))
        assertEquals(EquipmentSaveState.SLOT_BODY, preferredSlotForType("body", empty))
    }

    @Test
    fun accessory_prefersEmptySlot() {
        val empty1 = mapOf<String, EquipmentSaveState?>()
        assertEquals(EquipmentSaveState.SLOT_ACCESSORY1, preferredSlotForType("accessory", empty1))

        val taken1 = mapOf<String, EquipmentSaveState?>(
            EquipmentSaveState.SLOT_ACCESSORY1 to null,
        )
        // 值 null 视为未占用——仍进 1
        assertEquals(EquipmentSaveState.SLOT_ACCESSORY1, preferredSlotForType("accessory", taken1))

        // 1 号有装备 → 进 2 号（map 存在 key 且 value 非 null）
        val filled1 = mapOf<String, EquipmentSaveState?>(
            EquipmentSaveState.SLOT_ACCESSORY1 to EquipmentSaveState(
                equipmentId = "x",
                templateId = "eq_accessory_r_001",
                level = 1,
                exp = 0,
                mainStat = com.milan.game.data.StatValue("hp", 10, false),
            ),
        )
        assertEquals(
            EquipmentSaveState.SLOT_ACCESSORY2,
            preferredSlotForType("accessory", filled1),
        )
    }

    @Test
    fun unknownType_returnsNull() {
        assertNull(preferredSlotForType("ring", emptyMap()))
    }
}
