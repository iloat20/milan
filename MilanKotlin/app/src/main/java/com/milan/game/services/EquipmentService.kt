package com.milan.game.services

import com.milan.game.data.EquipmentSaveState
import com.milan.game.data.SaveData
import com.milan.game.data.StatValue
import kotlin.random.Random

/**
 * 装备服务（处理装备相关逻辑）。
 * 
 * 职责：
 * - 装备获取（抽卡、关卡掉落、商店购买）
 * - 装备强化
 * - 装备/卸下
 * - 装备分解
 * - 套装效果计算
 */
internal class EquipmentService(
    private val core: ServiceCore,
    private val rng: Random = Random.Default,
) {
    /** 装备 ID 单调递增序列，保证同毫秒批量生成也不碰撞（R5-T3）。 */
    private val idSeq = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * 生成一个随机装备。
     * 
     * @param templateId 装备模板ID
     * @param level 装备等级
     * @return 生成的装备存档状态
     */
    fun generateEquipment(templateId: String, level: Int = 1): EquipmentSaveState {
        val template = core.equipmentTemplates.firstOrNull { it.equipmentId == templateId }
            ?: throw IllegalArgumentException("Equipment template not found: $templateId")
        
        // 生成主属性
        val mainStat = generateMainStat(template)
        
        // 生成副属性（1-4条，根据稀有度决定初始数量）
        val subStatCount = when (template.rarity) {
            1 -> 1  // R: 1条
            2 -> 2  // SR: 2条
            3 -> 3  // SSR: 3条
            4 -> 4  // UR: 4条
            else -> 1
        }
        val subStats = generateSubStats(template, subStatCount)
        
        return EquipmentSaveState(
            equipmentId = generateEquipmentId(),
            templateId = templateId,
            level = level,
            exp = 0,
            mainStat = mainStat,
            subStats = subStats,
            locked = false,
        )
    }
    
    /**
     * 生成主属性。
     */
    private fun generateMainStat(template: EquipmentData): StatValue {
        // 主属性根据装备类型固定
        return when (template.type) {
            "weapon" -> StatValue(
                statType = StatValue.STAT_ATTACK,
                value = template.baseStats.firstOrNull { it.statType == StatValue.STAT_ATTACK }?.minValue ?: 10,
                isPercentage = false,
            )
            "head" -> StatValue(
                statType = StatValue.STAT_HP,
                value = template.baseStats.firstOrNull { it.statType == StatValue.STAT_HP }?.minValue ?: 100,
                isPercentage = false,
            )
            "body" -> StatValue(
                statType = StatValue.STAT_DEFENSE,
                value = template.baseStats.firstOrNull { it.statType == StatValue.STAT_DEFENSE }?.minValue ?: 10,
                isPercentage = false,
            )
            "accessory" -> {
                // 饰品主属性随机
                val statPool = template.baseStats
                if (statPool.isEmpty()) {
                    StatValue(statType = StatValue.STAT_ATTACK, value = 10, isPercentage = false)
                } else {
                    val selected = statPool.random(rng)
                    StatValue(
                        statType = selected.statType,
                        value = rng.nextInt(selected.minValue, selected.maxValue + 1),
                        isPercentage = selected.isPercentage,
                    )
                }
            }
            else -> StatValue(statType = StatValue.STAT_ATTACK, value = 10, isPercentage = false)
        }
    }
    
    /**
     * 生成副属性。
     */
    private fun generateSubStats(template: EquipmentData, count: Int): List<StatValue> {
        // R5-T1：只保留权重>0 的副属性候选——权重全 0 时 totalWeight=0，
        // rng.nextInt(0) 会抛 IllegalArgumentException；零权重副属性本就不应被选中。
        val availableStats = template.subStatPool.filter { it.weight > 0 }.toMutableList()
        val result = mutableListOf<StatValue>()
        
        repeat(count.coerceAtMost(availableStats.size)) {
            // 按权重随机选择
            val totalWeight = availableStats.sumOf { it.weight }
            var random = rng.nextInt(totalWeight)
            var selected: StatData? = null
            for (stat in availableStats) {
                random -= stat.weight
                if (random < 0) {
                    selected = stat
                    break
                }
            }
            
            if (selected != null) {
                result.add(StatValue(
                    statType = selected.statType,
                    value = rng.nextInt(selected.minValue, selected.maxValue + 1),
                    isPercentage = selected.isPercentage,
                ))
                availableStats.remove(selected)
            }
        }
        
        return result
    }
    
    /**
     * 生成装备ID。
     */
    private fun generateEquipmentId(): String {
        // R5-T3：时间戳 + 单调递增序列 + 随机因子，同毫秒批量发放也不会碰撞。
        return "eq_${System.currentTimeMillis()}_${idSeq.incrementAndGet()}_${rng.nextInt(10000)}"
    }
    
    /**
     * 获取角色装备的总属性加成。
     * 
     * @param characterId 角色ID
     * @return 属性加成列表
     */
    fun getCharacterEquipmentStats(characterId: String): List<StatValue> {
        val saveData = core.saveData
        val character = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
            ?: return emptyList()
        
        val stats = mutableListOf<StatValue>()
        
        // 收集所有装备的属性
        for (equipId in character.getEquippedIds()) {
            val equipment = saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipId }
                ?: continue
            
            // 主属性
            stats.add(equipment.mainStat)
            
            // 副属性
            stats.addAll(equipment.subStats.filterNotNull())
        }
        
        // 计算套装效果
        val setBonuses = calculateSetBonuses(characterId)
        for (bonus in setBonuses) {
            stats.add(StatValue(
                statType = bonus.statType,
                value = bonus.value,
                isPercentage = bonus.isPercentage,
            ))
        }
        
        return stats
    }
    
    /**
     * 计算套装效果。
     */
    private fun calculateSetBonuses(characterId: String): List<StatBonus> {
        val saveData = core.saveData
        val character = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
            ?: return emptyList()
        
        // 统计套装数量
        val setCounts = mutableMapOf<String, Int>()
        for (equipId in character.getEquippedIds()) {
            val equipment = saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipId }
                ?: continue
            val template = core.equipmentTemplates.firstOrNull { it.equipmentId == equipment.templateId }
                ?: continue
            
            if (template.setId.isNotEmpty()) {
                setCounts[template.setId] = (setCounts[template.setId] ?: 0) + 1
            }
        }
        
        // 计算套装加成
        val bonuses = mutableListOf<StatBonus>()
        for ((setId, count) in setCounts) {
            val setData = core.equipmentSets.firstOrNull { it.setId == setId }
                ?: continue
            
            if (count >= 2) {
                bonuses.addAll(setData.twoPieceBonus.statBonuses)
            }
            if (count >= 4) {
                bonuses.addAll(setData.fourPieceBonus.statBonuses)
            }
        }
        
        return bonuses
    }
    
    /**
     * 强化装备。
     * 
     * @param equipmentId 装备ID
     * @param expPoints 消耗的经验点数
     * @return 是否成功
     */
    suspend fun enhanceEquipment(equipmentId: String, expPoints: Int): WriteOutcome {
        val saveData = core.saveData

        // 预算校验（事务前，返回 Rejected 而非在 mutate 内抛异常）
        if (expPoints <= 0) return WriteOutcome.Rejected
        val equipment = saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipmentId }
            ?: return WriteOutcome.Rejected
        if (equipment.level >= EquipmentSaveState.MAX_LEVEL) return WriteOutcome.Rejected

        // Long 中介防 Int 溢出翻负
        val goldCostLong = expPoints.toLong() * 10
        if (goldCostLong > Int.MAX_VALUE) return WriteOutcome.Rejected
        val goldCost = goldCostLong.toInt()
        if (saveData.softCurrency < goldCost) return WriteOutcome.Rejected

        // 保存原始状态用于回滚
        val originalCurrency = saveData.softCurrency
        val originalExp = equipment.exp
        val originalLevel = equipment.level

        return core.transaction(
            tag = "equipment.enhance",
            mutate = {
                // 扣除金币
                saveData.softCurrency -= goldCost

                // 增加经验值
                equipment.exp += expPoints

                // 检查是否升级（每级所需经验随级递增，须在循环内按当前级重取）
                while (equipment.level < EquipmentSaveState.MAX_LEVEL) {
                    val expPerLevel = getExpForLevel(equipment.level + 1)
                    if (equipment.exp < expPerLevel) break
                    equipment.exp -= expPerLevel
                    equipment.level++
                }
            },
            rollback = {
                saveData.softCurrency = originalCurrency
                equipment.exp = originalExp
                equipment.level = originalLevel
            },
            onCommit = {
                core.publishCurrencyChanged()
            },
        )
    }
    
    /**
     * 获取升级所需经验。
     */
    private fun getExpForLevel(level: Int): Int {
        // 简化公式：每级需要的经验递增
        return 100 + (level - 1) * 50
    }
    
    /**
     * 装备到角色。
     * 
     * @param characterId 角色ID
     * @param equipmentId 装备ID
     * @param slot 槽位类型
     * @return 是否成功
     */
    suspend fun equipToCharacter(characterId: String, equipmentId: String, slot: String): WriteOutcome {
        val saveData = core.saveData

        // 预算校验（事务前，返回 Rejected 而非 mutate 内抛异常）
        val character = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
            ?: return WriteOutcome.Rejected
        val equipment = saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipmentId }
            ?: return WriteOutcome.Rejected
        val template = core.equipmentTemplates.firstOrNull { it.equipmentId == equipment.templateId }
            ?: return WriteOutcome.Rejected
        if (!isSlotValidForType(slot, template.type)) return WriteOutcome.Rejected
        if (saveData.ownedCharacters.any { it?.characterId != characterId && it?.getEquipment(slot) == equipmentId }) {
            return WriteOutcome.Rejected
        }

        // 保存原始状态用于回滚
        val originalEquipment = character.equipment?.get(slot)

        return core.transaction(
            tag = "equipment.equip",
            mutate = {
                character.setEquipment(slot, equipmentId)
            },
            rollback = {
                character.setEquipment(slot, originalEquipment)
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
    
    /**
     * 卸下装备。
     * 
     * @param characterId 角色ID
     * @param slot 槽位类型
     * @return 是否成功
     */
    suspend fun unequipFromCharacter(characterId: String, slot: String): WriteOutcome {
        val saveData = core.saveData

        // 预算校验（事务前）
        val character = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
            ?: return WriteOutcome.Rejected

        // 保存原始状态用于回滚
        val originalEquipment = character.equipment?.get(slot)

        return core.transaction(
            tag = "equipment.unequip",
            mutate = {
                character.setEquipment(slot, null)
            },
            rollback = {
                character.setEquipment(slot, originalEquipment)
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
    
    /**
     * 检查槽位是否匹配装备类型。
     */
    private fun isSlotValidForType(slot: String, type: String): Boolean {
        return when (slot) {
            EquipmentSaveState.SLOT_WEAPON -> type == "weapon"
            EquipmentSaveState.SLOT_HEAD -> type == "head"
            EquipmentSaveState.SLOT_BODY -> type == "body"
            EquipmentSaveState.SLOT_ACCESSORY1, EquipmentSaveState.SLOT_ACCESSORY2 -> type == "accessory"
            else -> false
        }
    }
    
    /**
     * 分解装备。
     * 
     * @param equipmentId 装备ID
     * @return 获得的材料数量
     */
    suspend fun disassembleEquipment(equipmentId: String): WriteOutcome {
        val saveData = core.saveData

        // 预算校验（事务前）
        val equipmentIndex = saveData.ownedEquipments.indexOfFirst { it?.equipmentId == equipmentId }
        if (equipmentIndex == -1) return WriteOutcome.Rejected

        // 保存原始状态用于回滚
        val originalEquipments = saveData.ownedEquipments.toList()

        return core.transaction(
            tag = "equipment.disassemble",
            mutate = {
                // 移除装备
                saveData.ownedEquipments = saveData.ownedEquipments.toMutableList().apply {
                    removeAt(equipmentIndex)
                }

                // TODO: 添加材料到背包（需要物品系统支持）
            },
            rollback = {
                saveData.ownedEquipments = originalEquipments
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }

    // ─────────────────── 装备获取 ───────────────────

    /**
     * 发放装备入库（R5-C3）。
     *
     * 修复背景：[generateEquipment] 只**返回**装备对象、**不写入** `SaveData.ownedEquipments`；
     * 而全项目 `ownedEquipments =` 的赋值此前仅出现在 [disassembleEquipment] 的移除与回滚两处。
     * 结果是背包恒为空（除非旧存档自带），[enhanceEquipment] / [equipToCharacter] /
     * [unequipFromCharacter] 三个 API 全部恒 `WriteOutcome.Rejected`——
     * 装备系统「工厂有了、入库路径缺」，与 R5-C2 活动系统「定义有了、激活路径缺」是同一模式。
     *
     * **必须先预检模板存在性**：[generateEquipment] 在模板缺失时抛
     * `IllegalArgumentException` 而非返回可空值，异常会从服务层逃逸到 UI 造成崩溃。
     * 本方法预检后返回 [WriteOutcome.Rejected]，把崩溃路径收敛为可处理的失败分支。
     *
     * @param templateId 装备模板 ID（对应 `core.equipmentTemplates` 的 `equipmentId`）
     * @param level 初始等级，默认 1；小于 1 视为脏输入直接拒绝
     */
    suspend fun grantEquipment(templateId: String, level: Int = 1): WriteOutcome {
        if (templateId.isEmpty()) return WriteOutcome.Rejected
        // 等级下界：负等级会让升级经验公式与属性计算拿到脏输入
        if (level < 1) return WriteOutcome.Rejected
        // 存在性预检：generateEquipment 对缺失模板抛异常，此处收敛为 Rejected
        if (core.equipmentTemplates.none { it.equipmentId == templateId }) return WriteOutcome.Rejected

        val saveData = core.saveData
        val equipment = generateEquipment(templateId, level)
        val originalEquipments = saveData.ownedEquipments.toList()

        return core.transaction(
            tag = "equipment.grant",
            mutate = {
                saveData.ownedEquipments = saveData.ownedEquipments + equipment
            },
            rollback = {
                saveData.ownedEquipments = originalEquipments
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
}
