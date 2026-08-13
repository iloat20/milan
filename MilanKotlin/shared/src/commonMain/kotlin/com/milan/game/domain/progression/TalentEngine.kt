package com.milan.game.domain.progression

/**
 * 天赋树分配引擎（C# Milan.Domain.Progression.TalentEngine 翻译）。
 *
 * 2026-08 KMP 下沉：自 app 迁入 shared commonMain。
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

    // ── 天赋分支属性加成（单一事实来源：0.03 系数与分支映射只允许存在于此）──

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

    companion object {
        /** 分支 ID 常量：与 App 侧 ContentModels.TalentNodeData.BranchId（@SerialName("BranchId")）契约对齐。 */
        const val BRANCH_POWER = "branch_power"
        const val BRANCH_DEFENSE = "branch_defense"
        const val BRANCH_UTILITY = "branch_utility"
    }
}
