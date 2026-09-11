package com.milan.game.services

import com.milan.game.data.*
import kotlin.random.Random

/**
 * 装备强化/分解/套装服务（2026-09-07 激活：数据模型已有，服务层 S2 删后重建）。
 *
 * 职责：
 * - 装备强化（+1 到 +15，消耗星尘；每 3 级随机提升一条副属性）
 * - 装备分解（回收星尘 + 星魂碎片）
 * - 装备穿脱（角色绑定，Map<String, String?> 槽位模型）
 * - 套装效果查询（战斗属性计算由 ServiceCore.calculateSetBonuses 处理）
 */
@Suppress("DEPRECATION")
class EquipmentService(
    private val core: ServiceCore,
    private val rng: Random,
) : EquipmentApi {

    private val saveData get() = core.saveData

    override val equipmentTemplates: List<EquipmentData>
        get() = core.equipmentTemplates

    // ─────────────────────────── 装备发放（C3）───────────────────────────

    /**
     * 事务化发放装备到背包。
     *
     * C3（2026-09-09）：此前模板实例化只返回对象、从不写入 `ownedEquipments`，
     * 强化/穿脱因 `firstOrNull` 恒空而全部 Rejected。本方法是装备系统的唯一入库路径。
     * 实例化逻辑在 [ServiceCore.rollEquipmentFromTemplate]（模板 + rng 共用内核）。
     */
    override suspend fun grantEquipment(templateId: String, level: Int): WriteOutcome {
        val template = core.equipmentTemplates.firstOrNull { it.equipmentId == templateId }
            ?: return WriteOutcome.Rejected
        val equipment = core.rollEquipmentFromTemplate(template, level)
        val original = saveData.ownedEquipments.toList()
        return core.withWriteLock {
            core.transactionLocked(
                tag = "equipment.grant",
                mutate = { saveData.ownedEquipments = original + equipment },
                rollback = { saveData.ownedEquipments = original },
                onCommit = { core.refreshSnapshot() },
            )
        }
    }

    // ─────────────────────────── 装备查询 ───────────────────────────

    /** 获取角色已装备的装备列表。 */
    override fun getEquipped(characterId: String): List<EquipmentSaveState> {
        val save = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
            ?: return emptyList()
        return save.getEquippedIds().mapNotNull { equipId ->
            saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipId }
        }
    }

    /** 获取角色指定槽位的装备。 */
    override fun getEquipAtSlot(characterId: String, slot: String): EquipmentSaveState? {
        val save = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
            ?: return null
        val equipId = save.getEquipment(slot) ?: return null
        return saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipId }
    }

    /** 获取所有未装备的装备（契约名 [EquipmentApi.getUnequippedEquipments]）。 */
    override fun getUnequippedEquipments(): List<EquipmentSaveState> {
        val equippedIds = saveData.ownedCharacters.filterNotNull()
            .flatMap { it.getEquippedIds() }
            .toSet()
        return saveData.ownedEquipments.filterNotNull()
            .filter { it.equipmentId !in equippedIds }
    }

    // ─────────────────────────── 装备强化 ───────────────────────────

    override suspend fun enhanceEquipment(equipmentId: String, times: Int): EquipmentEnhanceOutcome {
        if (times <= 0) return EquipmentEnhanceOutcome.Rejected

        val equipment = saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipmentId }
            ?: return EquipmentEnhanceOutcome.Rejected

        val template = core.equipmentTemplates.firstOrNull { it.equipmentId == equipment.templateId }
            ?: return EquipmentEnhanceOutcome.Rejected

        if (equipment.level >= template.maxLevel) return EquipmentEnhanceOutcome.Rejected

        val actualTimes = times.coerceAtMost(template.maxLevel - equipment.level)
        var totalCost = 0
        for (i in 0 until actualTimes) {
            totalCost += getEnhanceCost(equipment.level + i)
        }
        // R7-P1：余额复检入锁
        return core.withWriteLock {
            if (saveData.softCurrency < totalCost) return@withWriteLock EquipmentEnhanceOutcome.Rejected

            val origLevel = equipment.level
            val origSubStats = equipment.subStats.toList()
            val origSC = saveData.softCurrency

            core.transactionLocked(
                tag = "equipment.enhance",
                mutate = {
                    saveData.softCurrency -= totalCost
                    equipment.level += actualTimes

                    // 每 3 级随机提升一条副属性
                    val subStats = equipment.subStats.filterNotNull().toMutableList()
                    for (i in 0 until actualTimes) {
                        val checkLevel = equipment.level - actualTimes + i + 1
                        if (checkLevel % 3 == 0 && subStats.isNotEmpty()) {
                            val idx = rng.nextInt(subStats.size)
                            val stat = subStats[idx]
                            val boost = if (stat.isPercentage) rng.nextInt(1, 4) else rng.nextInt(5, 15)
                            subStats[idx] = stat.copy(value = stat.value + boost)
                        }
                    }
                    equipment.subStats = subStats
                },
                rollback = {
                    equipment.level = origLevel
                    equipment.subStats = origSubStats
                    saveData.softCurrency = origSC
                },
                onCommit = { core.publishCurrencyChanged() },
            )
            EquipmentEnhanceOutcome.Success(equipmentId, origLevel, equipment.level, totalCost)
        }
    }

    fun getEnhanceCost(currentLevel: Int): Int = currentLevel * 100

    // ─────────────────────────── 装备穿脱 ───────────────────────────

    override suspend fun equipItem(characterId: String, equipmentId: String, slot: String): WriteOutcome {
        if (slot !in EquipmentSaveState.ALL_SLOTS) return WriteOutcome.Rejected
        // R7-P1：装备/角色存在性与槽位兼容入锁
        return core.withWriteLock {
            val equipment = saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipmentId }
                ?: return@withWriteLock WriteOutcome.Rejected
            val save = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
                ?: return@withWriteLock WriteOutcome.Rejected
            val template = core.equipmentTemplates.firstOrNull { it.equipmentId == equipment.templateId }
            if (template != null && !isSlotCompatible(slot, template.type)) {
                return@withWriteLock WriteOutcome.Rejected
            }

            val origEquipment = save.equipment.toMap()
            core.transactionLocked(
                tag = "equipment.equip",
                mutate = { save.setEquipment(slot, equipmentId) },
                rollback = { save.equipment = origEquipment },
                onCommit = { core.refreshSnapshot() },
            )
        }
    }

    override suspend fun unequipItem(characterId: String, slot: String): WriteOutcome {
        if (slot !in EquipmentSaveState.ALL_SLOTS) return WriteOutcome.Rejected
        // R7-P1：角色存在性入锁
        return core.withWriteLock {
            val save = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
                ?: return@withWriteLock WriteOutcome.Rejected

            val origEquipment = save.equipment.toMap()
            core.transactionLocked(
                tag = "equipment.unequip",
                mutate = { save.setEquipment(slot, null) },
                rollback = { save.equipment = origEquipment },
                onCommit = { core.refreshSnapshot() },
            )
        }
    }

    // ─────────────────────────── 装备分解 ───────────────────────────

    override suspend fun dismantleEquipment(equipmentId: String): EquipmentDismantleOutcome {
        // R7-P1：locked/已穿戴校验入锁
        return core.withWriteLock {
            val equipment = saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipmentId }
                ?: return@withWriteLock EquipmentDismantleOutcome.Rejected
            if (equipment.locked) return@withWriteLock EquipmentDismantleOutcome.Rejected

            val isEquipped = saveData.ownedCharacters.filterNotNull()
                .any { it.getEquippedIds().contains(equipmentId) }
            if (isEquipped) return@withWriteLock EquipmentDismantleOutcome.Rejected

            val template = core.equipmentTemplates.firstOrNull { it.equipmentId == equipment.templateId }
            val rarity = template?.rarity ?: 1
            val level = equipment.level
            val softReward = rarity * level * 50 + rarity * 100
            val fragmentReward = rarity * level * 2

            val origEquipments = saveData.ownedEquipments
            val origSC = saveData.softCurrency
            val fragExisted = saveData.items.any { it?.itemId == ServiceCore.StarFragmentItemId }
            val origFrags = core.itemCount(ServiceCore.StarFragmentItemId)

            core.transactionLocked(
                tag = "equipment.dismantle",
                mutate = {
                    saveData.ownedEquipments = saveData.ownedEquipments.filterNot { it?.equipmentId == equipmentId }
                    core.addCurrencyDelta(softReward, 0)
                    if (fragmentReward > 0) core.addItemDelta(ServiceCore.StarFragmentItemId, fragmentReward)
                },
                rollback = {
                    saveData.ownedEquipments = origEquipments
                    saveData.softCurrency = origSC
                    core.restoreItemCount(ServiceCore.StarFragmentItemId, fragExisted, origFrags)
                },
                onCommit = { core.publishCurrencyChanged() },
            )
            EquipmentDismantleOutcome.Success(equipmentId, softReward, fragmentReward)
        }
    }

    // ─────────────────────────── 套装效果 ───────────────────────────

    override fun getActiveSetBonuses(characterId: String): List<SetBonusStatus> {
        val save = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
            ?: return emptyList()

        val setCounts = mutableMapOf<String, Int>()
        for (equipId in save.getEquippedIds()) {
            val equipment = saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipId }
                ?: continue
            val template = core.equipmentTemplates.firstOrNull { it.equipmentId == equipment.templateId }
                ?: continue
            if (template.setId.isNotEmpty()) {
                setCounts[template.setId] = (setCounts[template.setId] ?: 0) + 1
            }
        }

        return setCounts.flatMap { (setId, count) ->
            val setDef = core.equipmentSets.firstOrNull { it.setId == setId } ?: return@flatMap emptyList()
            buildList {
                if (count >= 2) add(SetBonusStatus(setId, setDef.displayName, 2, setDef.twoPieceBonus))
                if (count >= 4) add(SetBonusStatus(setId, setDef.displayName, 4, setDef.fourPieceBonus))
            }
        }
    }

    private fun isSlotCompatible(slot: String, type: String): Boolean = when (slot) {
        EquipmentSaveState.SLOT_WEAPON -> type == "weapon"
        EquipmentSaveState.SLOT_HEAD -> type == "head"
        EquipmentSaveState.SLOT_BODY -> type == "body"
        EquipmentSaveState.SLOT_ACCESSORY1, EquipmentSaveState.SLOT_ACCESSORY2 -> type == "accessory"
        else -> false
    }
}

// ── 数据模型 ──

sealed interface EquipmentEnhanceOutcome {
    data class Success(val equipmentId: String, val oldLevel: Int, val newLevel: Int, val costSoft: Int) : EquipmentEnhanceOutcome
    data object Rejected : EquipmentEnhanceOutcome
}

sealed interface EquipmentDismantleOutcome {
    data class Success(val equipmentId: String, val softReward: Int, val fragmentReward: Int) : EquipmentDismantleOutcome
    data object Rejected : EquipmentDismantleOutcome
}

data class SetBonusStatus(val setId: String, val setName: String, val piecesActive: Int, val bonus: SetBonus)
