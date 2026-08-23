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
object StatsCalculator {

    /**
     * 计算指定等级 / 阶段 / 星级下的战斗属性。
     *
     * - 等级/阶段 ≤0 一律钳到 1（与 [ProgressionEngine.statAtLevel] 同语义，防归零/变负）；
     * - 星级 ≤0 钳到 1★ 倍率（与 [ProgressionEngine.starMultiplier] 同语义）；
     * - [baseStats] 长度不可信（外部内容数据）：越界下标走 fallback（100/80/1000/12），
     *   绝不抛异常——本方法位于详情页/养成页/战斗页构建路径，抛了就是闪退；
     * - 天赋加成按已点亮节点所属分支计算（每节点 +3%，见 [TalentEngine.talentMultipliers]），
     *   未知分支忽略（防御内容脏值）。
     *
     * @param characterId 透传到 [UnitStats.characterId]（战斗结算/详情页关联角色）。
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
    ): UnitStats {
        val stg = max(1, stage)
        val lv = max(1, level)
        // 星级小幅加成：每星 +5%（1★→×1.0，满 7★→×1.30）。并入 StatAtLevel 的倍率槽。
        val starMul = ProgressionEngine.starMultiplier(stars)

        // BaseStats 越界兜底（fallback 常量对齐旧实现）。
        fun base(i: Int, fallback: Int): Int = if (i < baseStats.size) baseStats[i] else fallback

        val m = talent.talentMultipliers(branchIds)

        return UnitStats(
            atk = (progression.statAtLevel(base(0, 100), lv, stg, starMul) * (1 + m.atk)).toInt(),
            def = (progression.statAtLevel(base(1, 80), lv, stg, starMul) * (1 + m.def)).toInt(),
            hp = (progression.statAtLevel(base(2, 1000), lv, stg, starMul) * (1 + m.hp)).toInt(),
            spd = (progression.statAtLevel(base(3, 12), lv, stg, starMul) * (1 + m.spd)).toInt(),
            characterId = characterId,
        )
    }
}