package com.milan.game.domain.battle

/**
 * 队伍同源共鸣（2026-08 优化引入）：按编队内元素分布给角色加成，
 * 让「抽什么、编什么」产生构筑目标感（对标主流卡牌的队伍协同层：Balatro 的
 * Joker 连锁 / 原神系队伍元素反应的简化同构）。
 *
 * 规则：
 * - 双星共鸣：编队 ≥2 人且某元素恰好凑满 2 人以上（未达成全队同调）→
 *   这些角色的攻击力 +[PAIR_ATK_BONUS]；
 * - 同调共鸣：编队 ≥2 人且全员同一非空元素 → 全员攻击力 +[UNISON_ATK_BONUS]、
 *   速度 +[UNISON_SPD_BONUS]（取代双星，不叠加）；
 * - 空/未知元素不参与共鸣判定。
 *
 * 数值是战斗共鸣的**单一事实来源**：UI 共鸣预览与 BattleSimulator 编队构建共用本文件，
 * 禁止在别处就地写共鸣数字。
 */
object TeamResonance {

    /** 双星共鸣：同元素 ≥2 人的攻击加成。 */
    const val PAIR_ATK_BONUS = 0.10

    /** 同调共鸣：全队同元素的攻击加成。 */
    const val UNISON_ATK_BONUS = 0.20

    /** 同调共鸣：全队同元素的速度加成。 */
    const val UNISON_SPD_BONUS = 0.10

    /** 单个角色的共鸣加成（加法比例；调用方按 (1+bonus) 乘算基础属性）。 */
    data class Buff(val atkBonus: Double, val spdBonus: Double)

    /**
     * 计算 [element] 元素的角色在 [teamElements]（全队元素列表，含自身、可含空串）下的共鸣。
     * 纯函数：队伍不足 2 人或该角色无元素时不触发任何共鸣。
     */
    fun buffFor(element: String, teamElements: List<String>): Buff {
        if (teamElements.size < 2 || element.isEmpty()) return Buff(0.0, 0.0)
        if (teamElements.all { it == element }) return Buff(UNISON_ATK_BONUS, UNISON_SPD_BONUS)
        val sameCount = teamElements.count { it == element }
        return if (sameCount >= 2) Buff(PAIR_ATK_BONUS, 0.0) else Buff(0.0, 0.0)
    }

    /**
     * 把整队的共鸣加成套到 [units] 上（顺序一一对应；元素取自 [UnitStats.element]）。
     * 返回新列表、不改入参（UnitStats 为 data class，copy 安全）；无任何共鸣时原样返回。
     */
    fun apply(units: List<UnitStats>): List<UnitStats> {
        if (units.size < 2) return units
        val elements = units.map { it.element }
        return units.map { u ->
            val b = buffFor(u.element, elements)
            if (b.atkBonus == 0.0 && b.spdBonus == 0.0) {
                u
            } else {
                u.copy(
                    atk = (u.atk * (1 + b.atkBonus)).toInt(),
                    spd = (u.spd * (1 + b.spdBonus)).toInt(),
                )
            }
        }
    }
}
