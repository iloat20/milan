package com.milan.game.ai

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveData
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GameService

/**
 * AI 智能推荐系统（Gemini 集成预留）。
 *
 * 基于玩家数据提供个性化推荐：
 * - 角色培养建议
 * - 阵容搭配推荐
 * - 抽卡策略指导
 * - 资源分配优化
 *
 * 当前实现：规则引擎（离线、确定性）。
 * 未来可接入 Gemini Nano / 云端 Gemini API 实现更智能的推荐。
 */
object AIRecommendationEngine {

    /**
     * 角色培养推荐。
     *
     * @param save 玩家存档
     * @param service 游戏服务（获取角色定义）
     * @return 推荐培养的角色列表（已排序）
     */
    fun recommendCharactersToLevelUp(
        save: SaveData,
        service: GameService
    ): List<CharacterRecommendation> {
        val recommendations = mutableListOf<CharacterRecommendation>()

        for (owned in save.ownedCharacters) {
            if (owned == null) continue
            val def = service.character(owned.characterId) ?: continue

            // 计算推荐分数
            var score = 0
            val reasons = mutableListOf<String>()

            // 1. 稀有度权重
            score += def.baseRarity * 25
            if (def.baseRarity >= 3) {
                reasons.add("高稀有度(${rarityName(def.baseRarity)})")
            }

            // 2. 当前等级 vs 突破阶段
            val levelRatio = owned.level.toFloat() / (owned.stage * 10 + 10)
            if (levelRatio < 0.5f) {
                score += 20
                reasons.add("等级较低(Lv.${owned.level}，${owned.stage}突)")
            }

            // 3. 阵容搭配（检查是否有互补元素）
            val teamElements = save.formation
                .filterNotNull()
                .mapNotNull { service.character(it)?.element }
                .toSet()
            if (def.element !in teamElements && teamElements.size < 3) {
                score += 15
                reasons.add("补充${elementName(def.element)}元素")
            }

            // 4. 突破潜力
            if (def.canBreakthrough && owned.stage < def.maxStage) {
                score += 10
                reasons.add("可突破至${owned.stage + 1}突")
            }

            if (score > 30) {
                recommendations.add(
                    CharacterRecommendation(
                        characterId = owned.characterId,
                        characterName = def.displayName,
                        rarity = def.baseRarity,
                        element = def.element,
                        score = score,
                        reasons = reasons,
                        type = RecommendationType.LEVEL_UP
                    )
                )
            }
        }

        return recommendations.sortedByDescending { it.score }.take(5)
    }

    /**
     * 阵容搭配推荐。
     *
     * @param save 玩家存档
     * @param service 游戏服务
     * @return 推荐阵容列表
     */
    fun recommendTeamCompositions(
        save: SaveData,
        service: GameService
    ): List<TeamRecommendation> {
        val teams = mutableListOf<TeamRecommendation>()
        val owned = save.ownedCharacters.mapNotNull { owned ->
            if (owned == null) return@mapNotNull null
            val def = service.character(owned.characterId) ?: return@mapNotNull null
            OwnedWithDef(owned, def)
        }

        // 按元素分组
        val byElement = owned.groupBy { it.def.element }

        // 推荐1：元素共鸣队
        for ((element, chars) in byElement) {
            if (chars.size >= 3) {
                val top3 = chars.sortedByDescending { it.def.baseRarity }.take(3)
                teams.add(
                    TeamRecommendation(
                        name = "${elementName(element)}共鸣队",
                        characters = top3.map { it.def.characterId },
                        bonus = "${elementName(element)}伤害+20%",
                        score = top3.sumOf { it.def.baseRarity * 10 },
                        reasons = listOf("3个${elementName(element)}角色触发元素共鸣")
                    )
                )
            }
        }

        // 推荐2：均衡队（覆盖多种元素）
        val diversityTeam = mutableListOf<OwnedWithDef>()
        val usedElements = mutableSetOf<String>()
        for (char in owned.sortedByDescending { it.def.baseRarity }) {
            if (diversityTeam.size >= 4) break
            if (char.def.element !in usedElements) {
                diversityTeam.add(char)
                usedElements.add(char.def.element)
            }
        }
        if (diversityTeam.size >= 3) {
            teams.add(
                TeamRecommendation(
                    name = "均衡搭配队",
                    characters = diversityTeam.map { it.def.characterId },
                    bonus = "元素反应多样化",
                    score = diversityTeam.sumOf { it.def.baseRarity * 8 },
                    reasons = listOf("覆盖${diversityTeam.size}种元素，触发多种元素反应")
                )
            )
        }

        return teams.sortedByDescending { it.score }.take(3)
    }

    /**
     * 抽卡策略推荐。
     *
     * @param save 玩家存档
     * @param service 游戏服务
     * @return 抽卡建议
     */
    fun recommendGachaStrategy(
        save: SaveData,
        service: GameService
    ): GachaRecommendation {
        val pity = save.totalPullCount
        val ownedCount = save.ownedCharacters.filterNotNull().size
        val totalChars = service.characters.size

        val recommendations = mutableListOf<String>()

        // 1. 累计抽卡进度
        if (pity >= 70) {
            recommendations.add("已累计 ${pity} 抽，接近硬保底，建议继续抽")
        } else if (pity >= 50) {
            recommendations.add("已累计 ${pity} 抽，接近软保底，出率提升")
        }

        // 2. 收集进度
        if (totalChars > 0) {
            val collectionRatio = ownedCount.toFloat() / totalChars
            if (collectionRatio < 0.3f) {
                recommendations.add("角色收集率较低(${ownedCount}/${totalChars})，建议多抽常驻池")
            } else if (collectionRatio > 0.7f) {
                recommendations.add("角色收集率较高，可专注限定池")
            }
        }

        // 3. 资源状况
        val softCurrency = save.softCurrency
        val pullCost = 300 // 单抽消耗
        val pullsAvailable = softCurrency / pullCost
        if (pullsAvailable >= 10) {
            recommendations.add("星尘充足(${pullsAvailable}抽)，可考虑十连")
        } else if (pullsAvailable < 5) {
            recommendations.add("星尘较少(${pullsAvailable}抽)，建议积攒后再抽")
        }

        return GachaRecommendation(
            currentPity = pity,
            pullsAvailable = pullsAvailable,
            recommendations = recommendations,
            suggestedPool = if (pity < 50) "常驻池" else "当前限定池"
        )
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // 辅助函数
    // ══════════════════════════════════════════════════════════════════════════════

    private fun rarityName(rarity: Int): String = when (rarity) {
        4 -> "UR"
        3 -> "SSR"
        2 -> "SR"
        else -> "R"
    }

    private fun elementName(element: String): String = when (element) {
        "Metal" -> "金"
        "Wood" -> "木"
        "Water" -> "水"
        "Flame" -> "火"
        "Earth" -> "土"
        "Light" -> "光"
        "Shadow" -> "暗"
        "Thunder" -> "电"
        else -> element
    }

    private data class OwnedWithDef(
        val owned: CharacterSaveState,
        val def: CharacterDataEntry
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// 推荐数据模型
// ══════════════════════════════════════════════════════════════════════════════

/** 推荐类型 */
enum class RecommendationType {
    LEVEL_UP,       // 培养建议
    TEAM_COMPOSE,   // 阵容搭配
    GACHA_STRATEGY, // 抽卡策略
}

/** 角色推荐 */
data class CharacterRecommendation(
    val characterId: String,
    val characterName: String,
    val rarity: Int,
    val element: String,
    val score: Int,
    val reasons: List<String>,
    val type: RecommendationType,
)

/** 阵容推荐 */
data class TeamRecommendation(
    val name: String,
    val characters: List<String>,
    val bonus: String,
    val score: Int,
    val reasons: List<String>,
)

/** 抽卡推荐 */
data class GachaRecommendation(
    val currentPity: Int,
    val pullsAvailable: Int,
    val recommendations: List<String>,
    val suggestedPool: String,
)
