package com.milan.game.ui.characters

import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milan.game.data.EquipmentSaveState
import com.milan.game.ui.components.ArtifactPanel
import com.milan.game.ui.feedback.LocalFeedback
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** 槽位展示名（与 [EquipmentSaveState.ALL_SLOTS] 对齐）。 */
private fun slotLabel(slot: String): String = when (slot) {
    EquipmentSaveState.SLOT_WEAPON -> "武器"
    EquipmentSaveState.SLOT_HEAD -> "头盔"
    EquipmentSaveState.SLOT_BODY -> "铠甲"
    EquipmentSaveState.SLOT_ACCESSORY1 -> "饰品·壹"
    EquipmentSaveState.SLOT_ACCESSORY2 -> "饰品·贰"
    else -> slot
}

private fun rarityLabel(r: Int): String = when (r) {
    4 -> "UR"
    3 -> "SSR"
    2 -> "SR"
    else -> "R"
}

/**
 * 装备面板（C3，2026-09-09）：5 槽位 + 背包未装备列表 + 穿脱/强化/分解。
 * 数据由 [CharacterDetailViewModel] 随快照派生；写动作经 VM 回调。
 */
@Composable
internal fun EquipmentPanel(
    owned: Boolean,
    equipped: List<EquippedSlotView>,
    bag: List<BagEquipView>,
    onEquip: (equipmentId: String, slot: String) -> Unit,
    onUnequip: (slot: String) -> Unit,
    onEnhance: (equipmentId: String) -> Unit,
    onDismantle: (equipmentId: String) -> Unit,
) {
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()

    ArtifactPanel(modifier = Modifier.fillMaxWidth(), highlighted = owned) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = "穿戴槽位",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
            )
            Spacer(Modifier.height(8.dp))
            if (!owned) {
                Text(
                    text = "尚未拥有该角色，无法穿戴装备。",
                    color = AppTheme.Text3,
                )
            } else {
                equipped.forEach { slotView ->
                    SlotRow(
                        slotView = slotView,
                        onUnequip = { onUnequip(slotView.slot) },
                        onEnhance = slotView.equip?.let { e -> { onEnhance(e.equipmentId) } },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "背包装备（${bag.size}）",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Gold,
            )
            Spacer(Modifier.height(8.dp))
            if (bag.isEmpty()) {
                Text(
                    text = "背包为空。爬塔每 10 层刷新纪录可获得装备。",
                    color = AppTheme.Text3,
                )
            } else {
                bag.forEach { row ->
                    BagRow(
                        row = row,
                        onEquip = {
                            if (row.preferredSlot != null) onEquip(row.equip.equipmentId, row.preferredSlot)
                            else scope.launch { feedback.show("无法匹配槽位") }
                        },
                        onEnhance = { onEnhance(row.equip.equipmentId) },
                        onDismantle = { onDismantle(row.equip.equipmentId) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

/** 已穿戴槽位视图（VM 派生，避免 Panel 依赖 service/templates）。 */
data class EquippedSlotView(
    val slot: String,
    val equip: EquipmentSaveState?,
    val displayName: String,
    val rarity: Int,
)

/** 背包装备视图：含穿戴建议槽位（类型映射 + 饰品优先进空槽）。 */
data class BagEquipView(
    val equip: EquipmentSaveState,
    val displayName: String,
    val rarity: Int,
    val preferredSlot: String?,
    val canEquip: Boolean,
)

@Composable
private fun SlotRow(
    slotView: EquippedSlotView,
    onUnequip: () -> Unit,
    onEnhance: (() -> Unit)?,
) {
    val equip = slotView.equip
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Roundness.sm))
            .background(AppTheme.BgMid)
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.sm))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = slotLabel(slotView.slot),
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.Text2,
            modifier = Modifier.width(56.dp),
        )
        if (equip == null) {
            Text(text = "— 空 —", style = MaterialTheme.typography.labelLarge, color = AppTheme.Text3)
            Spacer(Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${rarityLabel(slotView.rarity)} ${slotView.displayName}",
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.rarityColor(slotView.rarity),
                )
                Text(
                    text = "+${equip.level} · ${equip.mainStat.statType} ${equip.mainStat.value}" +
                        if (equip.mainStat.isPercentage) "%" else "",
                    color = AppTheme.Text2,
                )
            }
            if (onEnhance != null) {
                ActionChip("强化", onClick = onEnhance)
                Spacer(Modifier.width(6.dp))
            }
            ActionChip("卸下", onClick = onUnequip)
        }
    }
}

@Composable
private fun BagRow(
    row: BagEquipView,
    onEquip: () -> Unit,
    onEnhance: () -> Unit,
    onDismantle: () -> Unit,
) {
    val equip = row.equip
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Roundness.sm))
            .background(AppTheme.BgMid)
            .border(1.dp, AppTheme.Stroke, RoundedCornerShape(AppTheme.Roundness.sm))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "${rarityLabel(row.rarity)} ${row.displayName}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AppTheme.rarityColor(row.rarity),
            )
            Text(
                text = "+${equip.level} · ${equip.mainStat.statType} ${equip.mainStat.value}" +
                    (if (equip.mainStat.isPercentage) "%" else "") +
                    " · 副词条 ${equip.subStats.size}",
                color = AppTheme.Text2,
            )
        }
        if (row.canEquip) {
            ActionChip("穿戴", onClick = onEquip)
            Spacer(Modifier.width(6.dp))
        }
        ActionChip("强化", onClick = onEnhance)
        Spacer(Modifier.width(6.dp))
        ActionChip("分解", onClick = onDismantle, danger = true)
    }
}

@Composable
private fun ActionChip(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (danger) AppTheme.Text3 else AppTheme.Gold,
        modifier = Modifier
            .clip(RoundedCornerShape(AppTheme.Roundness.xs))
            .background(if (danger) AppTheme.Text3.copy(alpha = 0.12f) else AppTheme.Gold.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** 由模板类型映射建议槽位；饰品优先进空槽。 */
internal fun preferredSlotForType(
    type: String,
    equippedSlots: Map<String, EquipmentSaveState?>,
): String? = when (type) {
    "weapon" -> EquipmentSaveState.SLOT_WEAPON
    "head" -> EquipmentSaveState.SLOT_HEAD
    "body" -> EquipmentSaveState.SLOT_BODY
    "accessory" -> listOf(
        EquipmentSaveState.SLOT_ACCESSORY1,
        EquipmentSaveState.SLOT_ACCESSORY2,
    ).firstOrNull { s -> equippedSlots[s] == null }
        ?: EquipmentSaveState.SLOT_ACCESSORY1
    else -> null
}
