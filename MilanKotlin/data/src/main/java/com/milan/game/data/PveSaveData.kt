package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 深渊挑战存档数据。
 * 
 * 设计：
 * - 深渊是高难度PVE内容，需要特定阵容搭配
 * - 每层有多个关卡，通关获得丰厚奖励
 * - 每期深渊会重置，奖励根据最高通关层数发放
 */
@Serializable
class AbyssSaveData(
    @SerialName("CurrentFloor") var currentFloor: Int = 1,
    @SerialName("BestFloor") var bestFloor: Int = 0,
    @SerialName("CurrentStage") var currentStage: Int = 1,
    @SerialName("Stars") var stars: List<Int?> = emptyList(),  // 每层获得的星星数
    @SerialName("TotalStars") var totalStars: Int = 0,
    @SerialName("LastResetTime") var lastResetTime: Long = 0,
    @SerialName("ChallengeCount") var challengeCount: Int = 0,
) {
    companion object {
        /** 深渊最大层数 */
        const val MAX_FLOORS = 50
        
        /** 每层最大关卡数 */
        const val STAGES_PER_FLOOR = 3
        
        /** 每层最大星星数 */
        const val MAX_STARS_PER_FLOOR = 3
        
        /** 每日免费挑战次数 */
        const val DAILY_FREE_CHALLENGES = 3
        
        /** 每次挑战消耗的体力 */
        const val STAMINA_COST = 20
    }
}

/**
 * 日常副本类型。
 */
enum class DailyDungeonType(
    val displayName: String,
    val description: String,
    val staminaCost: Int,
    val levels: Int,
) {
    EXP_DUNGEON("经验副本", "获取角色经验", 20, 5),
    GOLD_DUNGEON("金币副本", "获取金币", 15, 5),
    MATERIAL_DUNGEON("材料副本", "获取突破材料", 25, 5),
    EQUIPMENT_DUNGEON("装备副本", "获取装备", 30, 5),
    FRAGMENT_DUNGEON("碎片副本", "获取角色碎片", 20, 5),
}

/**
 * 日常副本存档数据。
 */
@Serializable
class DailyDungeonSaveData(
    @SerialName("ChallengeCounts") var challengeCounts: Map<String, Int?> = emptyMap(),
    @SerialName("LastResetTime") var lastResetTime: Long = 0,
    @SerialName("TotalChallenges") var totalChallenges: Int = 0,
) {
    companion object {
        /** 每种副本每日挑战次数限制 */
        const val MAX_CHALLENGES_PER_TYPE = 3
    }
}

/**
 * 日常副本奖励配置。
 */
data class DungeonReward(
    val exp: Int,
    val gold: Int,
    val materials: Map<String, Int>,
    val equipmentChance: Double = 0.0,
)

/**
 * 深渊关卡配置。
 */
data class AbyssStage(
    val floor: Int,
    val stage: Int,
    val name: String,
    val description: String,
    val recommendedPower: Int,
    val enemyCount: Int,
    val enemyLevel: Int,
    val rewards: DungeonReward,
    val starConditions: List<String>,
)

/**
 * 深渊层数奖励。
 */
data class AbyssFloorReward(
    val floor: Int,
    val starsRequired: Int,
    val softCurrency: Int,
    val hardCurrency: Int,
    val fragments: Int,
    val exclusiveReward: String?,
)
