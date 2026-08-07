package com.milan.game.domain.progression

/**
 * 天赋树分配引擎（C# Milan.Domain.Progression.TalentEngine 翻译）。
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
}
