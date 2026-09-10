package com.milan.game.ui.stats

import com.milan.game.OwnedCharacterView
import com.milan.game.domain.battle.UnitStats
import com.milan.game.domain.progression.ProgressionEngine
import com.milan.game.domain.progression.StatsCalculator
import com.milan.game.domain.progression.TalentEngine
import kotlin.math.max

/**
 * 角色战斗属性推导（展示口径）。
 *
 * 从 [com.milan.game.GameState] 抽出：进程单例不应承载领域计算——
 * 这里是纯函数适配器，引擎实例复用（ProgressionEngine 无状态），
 * 天赋分支/效果从 [OwnedCharacterView.talent] 读取（构造时注入，不再隐式回查 GameState）。
 *
 * 与 [com.milan.game.services.ServiceCore.unitStatsFor] 的差异：
 * 本入口服务「详情页 / 养成页 / 爬塔预览」的假设等级/阶段/星级推导；
 * 战斗实际属性以 ServiceCore 为准（含装备加成）。
 */
object CharacterStats {

    private val talentEngine = TalentEngine()
    private val progressionEngine = ProgressionEngine()

    /** 当前等级/阶段/星级下的属性。 */
    fun compute(view: OwnedCharacterView): UnitStats =
        computeAt(view, view.save.level, max(1, view.save.stage))

    /**
     * 在指定等级/阶段/星级下计算属性（养成页「下一级 / 下一阶 / 升星」预测）。
     * 天赋加成按当前已点亮节点计算，不随等级/阶段/星级假设改变。
     * [stars] 缺省（<0）时取 [view.save.stars]。
     */
    fun computeAt(
        view: OwnedCharacterView,
        level: Int,
        stage: Int,
        stars: Int = -1,
    ): UnitStats {
        val def = view.def
        val save = view.save
        if (def == null) {
            return UnitStats(atk = 0, def = 0, hp = 1, spd = 0, characterId = save.characterId)
        }

        val st = if (stars < 0) save.stars.coerceAtLeast(1) else stars
        val branchIds = view.talent?.nodes.orEmpty()
            .filter { save.talentPoints.contains(it.nodeId) }
            .map { it.branchId }
        val allocatedNodes = view.talent?.nodes.orEmpty()
            .filter { save.talentPoints.contains(it.nodeId) }
            .map { it.nodeId }
        val nodeEffectsMap = view.talent?.nodes.orEmpty()
            .filter { it.effects.isNotEmpty() }
            .associate { it.nodeId to it.effects }

        return StatsCalculator.compute(
            baseStats = def.baseStats,
            level = level,
            stage = stage,
            stars = st,
            branchIds = branchIds,
            characterId = save.characterId,
            progression = progressionEngine,
            talent = talentEngine,
            nodeEffectsMap = nodeEffectsMap.ifEmpty { null },
            allocatedNodes = allocatedNodes,
        )
    }
}
