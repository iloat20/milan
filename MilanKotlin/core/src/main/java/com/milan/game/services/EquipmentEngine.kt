package com.milan.game.services

import com.milan.game.data.EquipmentSaveState
import com.milan.game.data.StatValue
import kotlin.random.Random

/**
 * 装备领域引擎（2026-09-12 从 [ServiceCore] 下沉）。
 *
 * ServiceCore KDoc 约定「只提供状态 + 事务模板 + 原子辅助，不含业务规则」；
 * 此前模板表、随机词条、爬塔掉落、套装汇总都堆在 ServiceCore（~160 行硬编码）。
 * 现全部收敛为**纯函数/静态数据**，rng 与模板由调用方注入。
 */
object EquipmentEngine {

    /** 代码内兜底装备模板（data.json 暂无装备段时的唯一内容源）。 */
    fun fallbackTemplates(): List<EquipmentData> = listOf(
        EquipmentData(
            equipmentId = "eq_weapon_r_001",
            displayName = "铁剑",
            description = "普通的铁剑",
            rarity = 1,
            type = "weapon",
            setId = "",
            baseStats = listOf(StatData(StatValue.STAT_ATTACK, 10, 15, false, 100)),
            subStatPool = listOf(
                StatData(StatValue.STAT_HP, 20, 50, false, 100),
                StatData(StatValue.STAT_DEFENSE, 5, 15, false, 100),
                StatData(StatValue.STAT_CRIT_RATE, 1, 3, false, 50),
            ),
            maxLevel = 15,
            expPerLevel = listOf(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800),
            goldPerLevel = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000),
        ),
        EquipmentData(
            equipmentId = "eq_weapon_sr_001",
            displayName = "精钢剑",
            description = "精钢打造的长剑",
            rarity = 2,
            type = "weapon",
            setId = "set_attack",
            baseStats = listOf(StatData(StatValue.STAT_ATTACK, 20, 30, false, 100)),
            subStatPool = listOf(
                StatData(StatValue.STAT_HP, 30, 80, false, 100),
                StatData(StatValue.STAT_DEFENSE, 10, 25, false, 100),
                StatData(StatValue.STAT_CRIT_RATE, 2, 5, false, 75),
                StatData(StatValue.STAT_CRIT_DMG, 4, 10, false, 50),
            ),
            maxLevel = 15,
            expPerLevel = listOf(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800),
            goldPerLevel = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000),
        ),
        EquipmentData(
            equipmentId = "eq_weapon_ssr_001",
            displayName = "玄铁重剑",
            description = "山海秘境所出，剑脊隐有雷纹",
            rarity = 3,
            type = "weapon",
            setId = "set_attack",
            baseStats = listOf(StatData(StatValue.STAT_ATTACK, 40, 60, false, 100)),
            subStatPool = listOf(
                StatData(StatValue.STAT_HP, 50, 120, false, 100),
                StatData(StatValue.STAT_DEFENSE, 15, 35, false, 100),
                StatData(StatValue.STAT_CRIT_RATE, 3, 7, false, 80),
                StatData(StatValue.STAT_CRIT_DMG, 6, 14, false, 70),
            ),
            maxLevel = 15,
            expPerLevel = listOf(120, 180, 240, 300, 360, 420, 480, 540, 600, 660, 720, 780, 840, 900, 960),
            goldPerLevel = listOf(1200, 1800, 2400, 3000, 3600, 4200, 4800, 5400, 6000, 6600, 7200, 7800, 8400, 9000, 9600),
        ),
        EquipmentData(
            equipmentId = "eq_weapon_ur_001",
            displayName = "烛龙之锋",
            description = "以烛龙鳞锻成，出鞘如见晨昏",
            rarity = 4,
            type = "weapon",
            setId = "set_attack",
            baseStats = listOf(StatData(StatValue.STAT_ATTACK, 70, 100, false, 100)),
            subStatPool = listOf(
                StatData(StatValue.STAT_HP, 80, 180, false, 100),
                StatData(StatValue.STAT_DEFENSE, 25, 50, false, 100),
                StatData(StatValue.STAT_CRIT_RATE, 5, 10, false, 90),
                StatData(StatValue.STAT_CRIT_DMG, 10, 20, false, 80),
                StatData(StatValue.STAT_SPEED, 3, 8, false, 60),
            ),
            maxLevel = 15,
            expPerLevel = listOf(150, 220, 290, 360, 430, 500, 570, 640, 710, 780, 850, 920, 990, 1060, 1130),
            goldPerLevel = listOf(1500, 2200, 2900, 3600, 4300, 5000, 5700, 6400, 7100, 7800, 8500, 9200, 9900, 10600, 11300),
        ),
        EquipmentData(
            equipmentId = "eq_head_r_001",
            displayName = "皮盔",
            description = "普通的皮质头盔",
            rarity = 1,
            type = "head",
            setId = "",
            baseStats = listOf(StatData(StatValue.STAT_HP, 50, 100, false, 100)),
            subStatPool = listOf(
                StatData(StatValue.STAT_ATTACK, 5, 15, false, 100),
                StatData(StatValue.STAT_DEFENSE, 5, 15, false, 100),
                StatData(StatValue.STAT_CRIT_RATE, 1, 3, false, 50),
            ),
            maxLevel = 15,
            expPerLevel = listOf(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800),
            goldPerLevel = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000),
        ),
        EquipmentData(
            equipmentId = "eq_body_r_001",
            displayName = "皮甲",
            description = "普通的皮质铠甲",
            rarity = 1,
            type = "body",
            setId = "",
            baseStats = listOf(StatData(StatValue.STAT_DEFENSE, 10, 20, false, 100)),
            subStatPool = listOf(
                StatData(StatValue.STAT_ATTACK, 5, 15, false, 100),
                StatData(StatValue.STAT_HP, 20, 50, false, 100),
                StatData(StatValue.STAT_CRIT_RATE, 1, 3, false, 50),
            ),
            maxLevel = 15,
            expPerLevel = listOf(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800),
            goldPerLevel = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000),
        ),
        EquipmentData(
            equipmentId = "eq_accessory_r_001",
            displayName = "生命戒指",
            description = "增加生命值的戒指",
            rarity = 1,
            type = "accessory",
            setId = "",
            baseStats = listOf(
                StatData(StatValue.STAT_HP, 30, 60, false, 100),
                StatData(StatValue.STAT_ATTACK, 5, 10, false, 50),
            ),
            subStatPool = listOf(
                StatData(StatValue.STAT_DEFENSE, 5, 15, false, 100),
                StatData(StatValue.STAT_SPEED, 2, 5, false, 75),
                StatData(StatValue.STAT_CRIT_RATE, 1, 3, false, 50),
            ),
            maxLevel = 15,
            expPerLevel = listOf(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800),
            goldPerLevel = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000),
        ),
    )

    /** 代码内兜底套装。 */
    fun fallbackSets(): List<EquipmentSetData> = listOf(
        EquipmentSetData(
            setId = "set_attack",
            displayName = "攻击套",
            description = "2件套：攻击力+15%；4件套：暴击率+10%",
            twoPieceBonus = SetBonus(
                description = "攻击力+15%",
                statBonuses = listOf(StatBonus(StatValue.STAT_ATTACK, 15, true)),
            ),
            fourPieceBonus = SetBonus(
                description = "暴击率+10%",
                statBonuses = listOf(StatBonus(StatValue.STAT_CRIT_RATE, 10, true)),
            ),
        ),
        EquipmentSetData(
            setId = "set_defense",
            displayName = "防御套",
            description = "2件套：防御力+15%；4件套：生命值+20%",
            twoPieceBonus = SetBonus(
                description = "防御力+15%",
                statBonuses = listOf(StatBonus(StatValue.STAT_DEFENSE, 15, true)),
            ),
            fourPieceBonus = SetBonus(
                description = "生命值+20%",
                statBonuses = listOf(StatBonus(StatValue.STAT_HP, 20, true)),
            ),
        ),
    )

    /** 按模板实例化装备（主属性 + 按稀有度随机副词条）。纯函数，不写存档。 */
    fun rollFromTemplate(
        rng: Random,
        template: EquipmentData,
        level: Int = 1,
    ): EquipmentSaveState {
        val instanceId =
            "eq_${template.equipmentId}_${System.currentTimeMillis()}_${rng.nextInt(100000)}"
        val mainSource = template.baseStats.firstOrNull()
            ?: StatData(StatValue.STAT_ATTACK, 1, 1, false, 100)
        val mainStat = StatValue(
            statType = mainSource.statType,
            value = rollRange(rng, mainSource.minValue, mainSource.maxValue),
            isPercentage = mainSource.isPercentage,
        )
        val wantSubs = when (template.rarity) {
            4 -> 4
            3 -> 3
            2 -> 2
            else -> 1
        }.coerceAtMost(EquipmentSaveState.MAX_SUB_STATS)

        val pool = template.subStatPool.filter { it.statType != mainStat.statType }
        val rolled = mutableMapOf<String, StatValue>()
        val remaining = pool.toMutableList()
        while (rolled.size < wantSubs && remaining.isNotEmpty()) {
            val totalWeight = remaining.sumOf { it.weight.coerceAtLeast(1) }.coerceAtLeast(1)
            var pick = rng.nextInt(totalWeight)
            var chosen = remaining[0]
            for (stat in remaining) {
                pick -= stat.weight.coerceAtLeast(1)
                if (pick < 0) {
                    chosen = stat
                    break
                }
            }
            remaining.remove(chosen)
            rolled[chosen.statType] = StatValue(
                statType = chosen.statType,
                value = rollRange(rng, chosen.minValue, chosen.maxValue),
                isPercentage = chosen.isPercentage,
            )
        }
        return EquipmentSaveState(
            equipmentId = instanceId,
            templateId = template.equipmentId,
            level = level.coerceIn(1, template.maxLevel),
            exp = 0,
            mainStat = mainStat,
            subStats = rolled.values.toList(),
            locked = false,
        )
    }

    /**
     * 爬塔里程碑掉落：仅「层数为 10 的倍数」时产出。
     * 稀有度随层数：≥50 UR / ≥30 SSR / ≥15 SR / 其余 R。
     */
    fun rollTowerDrop(
        rng: Random,
        templates: List<EquipmentData>,
        floor: Int,
    ): EquipmentSaveState? {
        if (floor <= 0 || floor % 10 != 0) return null
        if (templates.isEmpty()) return null
        val rarity = when {
            floor >= 50 -> 4
            floor >= 30 -> 3
            floor >= 15 -> 2
            else -> 1
        }
        val candidates = templates.filter { it.rarity == rarity }.ifEmpty { templates }
        return rollFromTemplate(rng, candidates[rng.nextInt(candidates.size)], level = 1)
    }

    /**
     * 汇总某角色已穿装备的主/副词条 + 套装加成（战斗属性计算，不写存档）。
     * @param findEquipment 按 equipmentId 查已拥有装备实例
     */
    fun calculateEquipmentStatBonus(
        templates: List<EquipmentData>,
        sets: List<EquipmentSetData>,
        equippedIds: List<String>,
        findEquipment: (String) -> EquipmentSaveState?,
    ): EquipmentStatBonus {
        var atk = 0
        var def = 0
        var hp = 0
        var spd = 0
        var critRate = 0.0
        var critDmg = 0.0
        var atkPct = 0f
        var defPct = 0f
        var hpPct = 0f

        fun applyStat(statType: String, value: Int, isPercentage: Boolean) {
            when (statType) {
                StatValue.STAT_ATTACK ->
                    if (isPercentage) atkPct += value / 100f else atk += value
                StatValue.STAT_DEFENSE ->
                    if (isPercentage) defPct += value / 100f else def += value
                StatValue.STAT_HP ->
                    if (isPercentage) hpPct += value / 100f else hp += value
                StatValue.STAT_SPEED -> spd += value
                StatValue.STAT_CRIT_RATE -> critRate += value / 100.0
                StatValue.STAT_CRIT_DMG -> critDmg += value / 100.0
                else -> {}
            }
        }

        val ownedById = equippedIds.mapNotNull { findEquipment(it) }
        for (equipment in ownedById) {
            applyStat(
                equipment.mainStat.statType,
                equipment.mainStat.value,
                equipment.mainStat.isPercentage,
            )
            for (subStat in equipment.subStats.filterNotNull()) {
                applyStat(subStat.statType, subStat.value, subStat.isPercentage)
            }
        }

        val setCounts = mutableMapOf<String, Int>()
        for (equipment in ownedById) {
            val template = templates.firstOrNull { it.equipmentId == equipment.templateId }
                ?: continue
            if (template.setId.isNotEmpty()) {
                setCounts[template.setId] = (setCounts[template.setId] ?: 0) + 1
            }
        }
        for ((setId, count) in setCounts) {
            val setData = sets.firstOrNull { it.setId == setId } ?: continue
            if (count >= 2) {
                for (bonus in setData.twoPieceBonus.statBonuses) applyStat(bonus.statType, bonus.value, bonus.isPercentage)
            }
            if (count >= 4) {
                for (bonus in setData.fourPieceBonus.statBonuses) applyStat(bonus.statType, bonus.value, bonus.isPercentage)
            }
        }

        return EquipmentStatBonus(
            atk = atk,
            def = def,
            hp = hp,
            spd = spd,
            critRate = critRate,
            critDmg = critDmg,
            atkPct = atkPct,
            defPct = defPct,
            hpPct = hpPct,
        )
    }

    private fun rollRange(rng: Random, min: Int, max: Int): Int =
        if (max > min) rng.nextInt(min, max + 1) else min
}

/** 装备属性汇总（战斗单位属性计算内部结果）。 */
data class EquipmentStatBonus(
    val atk: Int = 0,
    val def: Int = 0,
    val hp: Int = 0,
    val spd: Int = 0,
    val critRate: Double = 0.0,
    val critDmg: Double = 0.0,
    val atkPct: Float = 0f,
    val defPct: Float = 0f,
    val hpPct: Float = 0f,
)
