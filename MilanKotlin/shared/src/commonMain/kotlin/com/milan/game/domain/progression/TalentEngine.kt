package com.milan.game.domain.progression

/**
 * 天赋树分配引擎（C# Milan.Domain.Progression.TalentEngine 翻译）。
 *
 * 2026-08 KMP 下沉：自 app 迁入 shared commonMain。
 * 2026-09 扩展：支持 22 种天赋效果（属性加成/战斗机制/状态施加/必杀强化）。
 */
class TalentEngine {

    /**
     * 节点 [nodeId] 是否可分配：
     * - 已分配 → false；
     * - 无前置要求（不在 [prereqs] 中）或前置为 null → true（根节点可点；防御空引用）；
     * - 全部前置均已分配 → true。
     */
    fun canAllocate(nodeId: String, allocated: List<String>, prereqs: Map<String, List<String>?>): Boolean {
        if (nodeId in allocated) return false
        // key 不存在或值为 null 都视为「无前置」而非「锁死」
        val reqs = prereqs[nodeId] ?: return true
        return reqs.all { it in allocated }
    }

    /** 返回「满树总点数」= 所有节点 cost 之和（非已分配点数）。 */
    fun totalPoints(nodeCosts: Map<String, Int>): Int = nodeCosts.values.sum()

    // ── 天赋分支属性加成（旧系统，保留向后兼容）──

    /** 天赋分支属性加成（每节点 +3%）。 */
    data class TalentMultipliers(
        val atk: Float = 0f,
        val def: Float = 0f,
        val hp: Float = 0f,
        val spd: Float = 0f,
    )

    /** 由已点亮节点所属分支计算加成；未知分支忽略（防御内容数据脏值）。 */
    fun talentMultipliers(branchIds: List<String>): TalentMultipliers {
        var atk = 0f; var def = 0f; var hp = 0f; var spd = 0f
        for (b in branchIds) {
            when (b) {
                BRANCH_POWER   -> atk += 0.03f
                BRANCH_DEFENSE -> { def += 0.03f; hp += 0.03f }
                BRANCH_UTILITY -> spd += 0.03f
            }
        }
        return TalentMultipliers(atk, def, hp, spd)
    }

    // ── 天赋效果系统（新系统：基于节点 Effects 数组）──

    /**
     * 由已点亮节点的 Effects 计算天赋效果总和。
     *
     * @param allocatedNodes 已分配的节点 ID 列表
     * @param nodeEffectsMap 节点 ID → 效果列表映射（由调用方从 TalentTreeData 构建）
     * @return 聚合后的天赋效果结果
     */
    fun talentEffects(
        allocatedNodes: List<String>,
        nodeEffectsMap: Map<String, List<TalentEffect>>,
    ): TalentEffectResult {
        var atkPercent = 0f; var defPercent = 0f; var hpPercent = 0f; var spdPercent = 0f
        var ignoreDefense = 0f; var damageReduction = 0f; var dodgeRate = 0f
        var critRate = 0f; var critDamage = 0f; var lifesteal = 0f; var thorn = 0f

        // 状态概率（取同类型最大值，不叠加）
        var poisonChance = 0f; var poisonDuration = 0
        var burnChance = 0f; var burnDuration = 0
        var bleedChance = 0f; var bleedDuration = 0
        var disarmChance = 0f; var disarmDuration = 0
        var stunChance = 0f; var stunDuration = 0
        var chillChance = 0f; var chillDuration = 0
        var stiffChance = 0f; var stiffDuration = 0
        var tauntChance = 0f; var tauntDuration = 0
        var unstoppable = false

        var ultimateDamage = 0f; var chargeGain = 0f

        for (nodeId in allocatedNodes) {
            val effects = nodeEffectsMap[nodeId] ?: continue
            for (effect in effects) {
                when (effect.type) {
                    // ── 属性加成（叠加）──
                    TalentEffectType.AtkPercent -> atkPercent += effect.value
                    TalentEffectType.DefPercent -> defPercent += effect.value
                    TalentEffectType.HpPercent -> hpPercent += effect.value
                    TalentEffectType.SpdPercent -> spdPercent += effect.value

                    // ── 战斗机制（叠加，上限 1.0）──
                    TalentEffectType.IgnoreDefense -> ignoreDefense = (ignoreDefense + effect.value).coerceAtMost(1f)
                    TalentEffectType.DamageReduction -> damageReduction = (damageReduction + effect.value).coerceAtMost(1f)
                    TalentEffectType.DodgeRate -> dodgeRate = (dodgeRate + effect.value).coerceAtMost(1f)
                    TalentEffectType.CritRate -> critRate = (critRate + effect.value).coerceAtMost(1f)
                    TalentEffectType.CritDamage -> critDamage += effect.value
                    TalentEffectType.Lifesteal -> lifesteal = (lifesteal + effect.value).coerceAtMost(1f)
                    TalentEffectType.Thorn -> thorn = (thorn + effect.value).coerceAtMost(1f)

                    // ── 状态施加（取同类型最大值）──
                    TalentEffectType.Poison -> {
                        if (effect.chance > poisonChance) { poisonChance = effect.chance; poisonDuration = effect.duration }
                    }
                    TalentEffectType.Burn -> {
                        if (effect.chance > burnChance) { burnChance = effect.chance; burnDuration = effect.duration }
                    }
                    TalentEffectType.Bleed -> {
                        if (effect.chance > bleedChance) { bleedChance = effect.chance; bleedDuration = effect.duration }
                    }
                    TalentEffectType.Disarm -> {
                        if (effect.chance > disarmChance) { disarmChance = effect.chance; disarmDuration = effect.duration }
                    }
                    TalentEffectType.Stun -> {
                        if (effect.chance > stunChance) { stunChance = effect.chance; stunDuration = effect.duration }
                    }
                    TalentEffectType.Chill -> {
                        if (effect.chance > chillChance) { chillChance = effect.chance; chillDuration = effect.duration }
                    }
                    TalentEffectType.Stiff -> {
                        if (effect.chance > stiffChance) { stiffChance = effect.chance; stiffDuration = effect.duration }
                    }
                    TalentEffectType.Taunt -> {
                        if (effect.chance > tauntChance) { tauntChance = effect.chance; tauntDuration = effect.duration }
                    }
                    TalentEffectType.Unstoppable -> unstoppable = true

                    // ── 必杀强化（叠加）──
                    TalentEffectType.UltimateDamage -> ultimateDamage += effect.value
                    TalentEffectType.ChargeGain -> chargeGain += effect.value
                }
            }
        }

        return TalentEffectResult(
            atkPercent = atkPercent,
            defPercent = defPercent,
            hpPercent = hpPercent,
            spdPercent = spdPercent,
            ignoreDefense = ignoreDefense,
            damageReduction = damageReduction,
            dodgeRate = dodgeRate,
            critRate = critRate,
            critDamage = critDamage,
            lifesteal = lifesteal,
            thorn = thorn,
            poisonChance = poisonChance,
            poisonDuration = poisonDuration,
            burnChance = burnChance,
            burnDuration = burnDuration,
            bleedChance = bleedChance,
            bleedDuration = bleedDuration,
            disarmChance = disarmChance,
            disarmDuration = disarmDuration,
            stunChance = stunChance,
            stunDuration = stunDuration,
            chillChance = chillChance,
            chillDuration = chillDuration,
            stiffChance = stiffChance,
            stiffDuration = stiffDuration,
            tauntChance = tauntChance,
            tauntDuration = tauntDuration,
            unstoppable = unstoppable,
            ultimateDamage = ultimateDamage,
            chargeGain = chargeGain,
        )
    }

    companion object {
        /** 分支 ID 常量：与 App 侧 ContentModels.TalentNodeData.BranchId（@SerialName("BranchId")）契约对齐。 */
        const val BRANCH_POWER = "branch_power"
        const val BRANCH_DEFENSE = "branch_defense"
        const val BRANCH_UTILITY = "branch_utility"
    }
}
