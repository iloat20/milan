package com.milan.game.domain.progression

import com.milan.game.domain.battle.UnitStats
import kotlin.math.max

/**
 * 角色属性计算器（2026-08 KMP 下沉延续：自 app 侧 ui/GameState.kt 迁入 shared commonMain）。
 *
 * 由基础值 + 养成状态推导实时战斗属性（atk/def/hp/spd）——单一事实来源：
 * 详情页 / 养成页 / 未来战斗页（[BattleSimulator]）跨端共用同一份，桌面模拟器同口径。
 *
 * 纯函数、无状态、无 Android 依赖；依赖引擎实例经参数注入（实例复用由调用方负责，
 * 避免热路径每次重组重复分配）。
 */
/**
 * 次级属性（暴击/急速/护甲/格挡），由主属性透明推导，仅面板展示（C# DeriveSecondary）。
 * 下沉至 shared domain：桌面模拟器与 App 同口径。
 */
data class SecondaryStats(val crit: Int, val haste: Int, val armor: Int, val block: Int)

object StatsCalculator {

    /**
     * 计算指定等级 / 阶段 / 星级下的战斗属性。
     *
     * - 等级/阶段 ≤0 一律钳到 1（与 [ProgressionEngine.statAtLevel] 同语义，防归零/变负）；
     * - 星级 ≤0 钳到 1★ 倍率（与 [ProgressionEngine.starMultiplier] 同语义）；
     * - [baseStats] 长度不可信（外部内容数据）：越界下标走 fallback（100/80/1000/12），
     *   绝不抛异常——本方法位于详情页/养成页/战斗页构建路径，抛了就是闪退；
     * - 天赋加成按已点亮节点 Effects 计算（[TalentEngine.talentEffects]），
     *   回退到旧分支模式（[TalentEngine.talentMultipliers]）当无 Effects 时。
     *
     * @param characterId 透传到 [UnitStats.characterId]（战斗结算/详情页关联角色）。
     * @param nodeEffectsMap 节点 ID → 效果列表映射（由调用方从 TalentTreeData 构建）；null = 旧模式。
     */
    fun compute(
        baseStats: List<Int>,
        level: Int,
        stage: Int,
        stars: Int,
        branchIds: List<String>,
        characterId: String,
        progression: ProgressionEngine,
        talent: TalentEngine,
        nodeEffectsMap: Map<String, List<TalentEffect>>? = null,
        allocatedNodes: List<String> = emptyList(),
    ): UnitStats {
        val stg = max(1, stage)
        val lv = max(1, level)
        // 星级小幅加成：每星 +5%（1★→×1.0，满 7★→×1.30）。并入 StatAtLevel 的倍率槽。
        val starMul = ProgressionEngine.starMultiplier(stars)

        // BaseStats 越界兜底（fallback 常量对齐旧实现）。
        fun base(i: Int, fallback: Int): Int = if (i < baseStats.size) baseStats[i] else fallback

        // 优先使用新 Effects 系统；无 Effects 时回退到旧分支模式
        val effects = if (nodeEffectsMap != null && allocatedNodes.isNotEmpty()) {
            talent.talentEffects(allocatedNodes, nodeEffectsMap)
        } else null

        val m = if (effects != null) {
            TalentEngine.TalentMultipliers(
                atk = effects.atkPercent,
                def = effects.defPercent,
                hp = effects.hpPercent,
                spd = effects.spdPercent,
            )
        } else {
            talent.talentMultipliers(branchIds)
        }

        val baseAtk = progression.statAtLevel(base(0, 100), lv, stg, starMul)
        val baseDef = progression.statAtLevel(base(1, 80), lv, stg, starMul)
        val baseHp = progression.statAtLevel(base(2, 1000), lv, stg, starMul)
        val baseSpd = progression.statAtLevel(base(3, 12), lv, stg, starMul)

        return UnitStats(
            atk = (baseAtk * (1 + m.atk)).toInt(),
            def = (baseDef * (1 + m.def)).toInt(),
            hp = (baseHp * (1 + m.hp)).toInt(),
            spd = (baseSpd * (1 + m.spd)).toInt(),
            characterId = characterId,
            // ── 天赋效果扩展字段 ──
            ignoreDefense = effects?.ignoreDefense ?: 0f,
            damageReduction = effects?.damageReduction ?: 0f,
            dodgeRate = effects?.dodgeRate ?: 0f,
            talentCritRate = effects?.critRate ?: 0f,
            talentCritDamage = effects?.critDamage ?: 0f,
            lifesteal = effects?.lifesteal ?: 0f,
            thorn = effects?.thorn ?: 0f,
            ultimateDamage = effects?.ultimateDamage ?: 0f,
            chargeGain = effects?.chargeGain ?: 0f,
            // ── 状态施加 ──
            poisonChance = effects?.poisonChance ?: 0f,
            poisonDuration = effects?.poisonDuration ?: 0,
            burnChance = effects?.burnChance ?: 0f,
            burnDuration = effects?.burnDuration ?: 0,
            bleedChance = effects?.bleedChance ?: 0f,
            bleedDuration = effects?.bleedDuration ?: 0,
            disarmChance = effects?.disarmChance ?: 0f,
            disarmDuration = effects?.disarmDuration ?: 0,
            stunChance = effects?.stunChance ?: 0f,
            stunDuration = effects?.stunDuration ?: 0,
            chillChance = effects?.chillChance ?: 0f,
            chillDuration = effects?.chillDuration ?: 0,
            stiffChance = effects?.stiffChance ?: 0f,
            stiffDuration = effects?.stiffDuration ?: 0,
            tauntChance = effects?.tauntChance ?: 0f,
            tauntDuration = effects?.tauntDuration ?: 0,
            unstoppable = effects?.unstoppable ?: false,
        )
    }

    /** 次级属性推导：从主属性透明映射（C# DeriveSecondary；纯函数，无 Android 依赖）。
     *  暴击：基础 8 + atk/120，叠加装备暴击率（UnitStats.critRate，0.0~1.0 → 百分比）。 */
    fun deriveSecondary(s: UnitStats): SecondaryStats = SecondaryStats(
        crit = (8 + s.atk / 120 + (s.critRate * 100).toInt()).coerceIn(8, 100),
        haste = (5 + s.spd * 2).coerceIn(5, 50),
        armor = (s.def * 1.6 + s.hp * 0.05).toInt(),
        block = (3 + s.def / 200).coerceIn(3, 30),
    )
}