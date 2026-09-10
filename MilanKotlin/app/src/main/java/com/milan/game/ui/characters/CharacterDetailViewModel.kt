package com.milan.game.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.milan.game.data.CharacterSaveState
import com.milan.game.data.EquipmentSaveState
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.EquipmentDismantleOutcome
import com.milan.game.services.EquipmentEnhanceOutcome
import com.milan.game.services.GameService
import com.milan.game.services.TalentTreeData
import com.milan.game.services.WriteOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 角色详情页 UI 状态（ownedSave 随快照 revision 刷新，其余按 characterId 缓存）。 */
data class CharacterDetailUiState(
    /** 内容定义；null → Screen 渲染 MissingCharacter 空态（C# ResolveCharacter 失败语义）。 */
    val def: CharacterDataEntry?,
    /** 拥有时的实时存档；未拥有为 Level/Stage/Stars=1 的兜底渲染模型。 */
    val save: CharacterSaveState,
    val owned: Boolean,
    /** 全表角色 id（左右切换用，含未拥有角色——图鉴剪影也能浏览）。 */
    val characterIds: List<String>,
    /** 5 槽位视图（穿脱/强化用）。 */
    val equippedSlots: List<EquippedSlotView> = emptyList(),
    /** 背包未装备列表。 */
    val bag: List<BagEquipView> = emptyList(),
)

/**
 * 角色详情页 ViewModel（2026-09-09 P1-6 D 批，按 characterId 建 VM 实例）。
 *
 * 行为与 VM 化前等价（纯状态搬移）：
 * - P2-13/P3-5 语义保留：订阅快照，任何成功写操作后重读最新存档（ownedSaves 随快照刷新）；
 * - 未拥有兜底存档（Level/Stage/Stars=1）按角色构建；
 * - 属性推导（CharacterStats）为纯展示计算，仍留 Screen；
 * - 装备穿脱/强化/分解写动作在此（C3，2026-09-09）。
 */
class CharacterDetailViewModel(
    private val characterId: String,
    private val service: GameService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<CharacterDetailUiState> = _uiState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }
        }
    }

    /** 当前角色天赋树（构造 OwnedCharacterView 时注入，视图不再回查单例）。 */
    fun talentTree(): TalentTreeData? =
        service.character(characterId)?.talentTreeId?.let { service.talentTree(it) }

    private fun buildState(): CharacterDetailUiState {
        val def = service.character(characterId)
        val ownedSave = service.snapshot.value.ownedSaves[characterId]
        val templatesById = service.equipmentTemplates.associateBy { it.equipmentId }
        val equippedMap = mutableMapOf<String, EquipmentSaveState?>()
        for (slot in EquipmentSaveState.ALL_SLOTS) {
            equippedMap[slot] = service.getEquipAtSlot(characterId, slot)
        }
        val equippedSlots = EquipmentSaveState.ALL_SLOTS.map { slot ->
            val equip = equippedMap[slot]
            val template = equip?.let { templatesById[it.templateId] }
            EquippedSlotView(
                slot = slot,
                equip = equip,
                displayName = template?.displayName ?: (equip?.templateId ?: ""),
                rarity = template?.rarity ?: 1,
            )
        }
        val bag = service.getUnequippedEquipments().map { equip ->
            val template = templatesById[equip.templateId]
            BagEquipView(
                equip = equip,
                displayName = template?.displayName ?: equip.templateId,
                rarity = template?.rarity ?: 1,
                preferredSlot = preferredSlotForType(template?.type ?: "", equippedMap),
                canEquip = ownedSave != null && template != null,
            )
        }
        return CharacterDetailUiState(
            def = def,
            save = ownedSave ?: CharacterSaveState(
                characterId = characterId, level = 1, stage = 1, stars = 1,
            ),
            owned = ownedSave != null,
            characterIds = service.characters.map { it.characterId },
            equippedSlots = equippedSlots,
            bag = bag,
        )
    }

    fun equip(equipmentId: String, slot: String) = launchWrite {
        when (service.equipItem(characterId, equipmentId, slot)) {
            WriteOutcome.Success -> _toasts.send("已穿戴")
            WriteOutcome.Rejected -> _toasts.send("无法穿戴")
            WriteOutcome.SaveFailed -> _toasts.send("保存失败，请重试")
        }
    }

    fun unequip(slot: String) = launchWrite {
        when (service.unequipItem(characterId, slot)) {
            WriteOutcome.Success -> _toasts.send("已卸下")
            WriteOutcome.Rejected -> _toasts.send("无法卸下")
            WriteOutcome.SaveFailed -> _toasts.send("保存失败，请重试")
        }
    }

    fun enhance(equipmentId: String) = launchWrite {
        when (val r = service.enhanceEquipment(equipmentId, times = 1)) {
            is EquipmentEnhanceOutcome.Success ->
                _toasts.send("强化成功 +${r.oldLevel} → +${r.newLevel}")
            EquipmentEnhanceOutcome.Rejected -> _toasts.send("无法强化（材料/等级）")
        }
    }

    fun dismantle(equipmentId: String) = launchWrite {
        when (val r = service.dismantleEquipment(equipmentId)) {
            is EquipmentDismantleOutcome.Success ->
                _toasts.send("分解成功：星尘 +${r.softReward}" + if (r.fragmentReward > 0) " 碎片 +${r.fragmentReward}" else "")
            EquipmentDismantleOutcome.Rejected -> _toasts.send("无法分解")
        }
    }

    private fun launchWrite(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (_: Exception) {
                _toasts.send("操作异常，请重试")
            } finally {
                _busy.value = false
            }
        }
    }
}
