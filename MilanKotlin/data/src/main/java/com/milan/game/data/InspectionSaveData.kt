package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 360°检视系统增强数据模型。
 * 
 * 设计：
 * - 角色互动动作库
 * - 拍照模式配置
 * - AR投影设置（预留）
 * - 分享记录
 */
@Serializable
class InspectionSaveData(
    /** 已解锁的互动动作ID列表。 */
    @SerialName("UnlockedActions") var unlockedActions: List<String?> = emptyList(),
    /** 拍照模式收藏的照片。 */
    @SerialName("PhotoCollection") var photoCollection: List<PhotoRecord?> = emptyList(),
    /** 角色检视次数统计（characterId → 次数）。 */
    @SerialName("InspectionCounts") var inspectionCounts: Map<String, Int?> = emptyMap(),
    /** 已触发的特殊互动（用于解锁隐藏内容）。 */
    @SerialName("TriggeredInteractions") var triggeredInteractions: List<String?> = emptyList(),
) {
    companion object {
        /** 最大收藏照片数 */
        const val MAX_PHOTOS = 50
        
        /** 触发隐藏动作的检视次数阈值 */
        const val HIDDEN_ACTION_THRESHOLD = 100
    }
}

/**
 * 拍照记录。
 */
@Serializable
data class PhotoRecord(
    @SerialName("PhotoId") val photoId: String = "",
    @SerialName("CharacterId") val characterId: String = "",
    @SerialName("Pose") val pose: String = "",
    @SerialName("Background") val background: String = "",
    @SerialName("Filter") val filter: String = "",
    @SerialName("Timestamp") val timestamp: Long = 0,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
)

/**
 * 角色互动动作。
 */
data class CharacterAction(
    val actionId: String,
    val name: String,
    val description: String,
    val type: ActionType,
    val animationName: String,
    val voiceLine: String?,
    val unlockCondition: String?,
    val triggerChance: Double = 1.0,
)

/**
 * 动作类型。
 */
enum class ActionType {
    GREETING,      // 打招呼
    BATTLE_POSE,   // 战斗姿势
    RELAXED,       // 放松姿态
    SPECIAL,       // 特殊动作
    HIDDEN,        // 隐藏动作
    INTERACTION,   // 双人互动
}

/**
 * 拍照姿势。
 */
data class PhotoPose(
    val poseId: String,
    val name: String,
    val description: String,
    val animationName: String,
    val unlockCondition: String?,
)

/**
 * 拍照背景。
 */
data class PhotoBackground(
    val backgroundId: String,
    val name: String,
    val description: String,
    val isUnlocked: Boolean = false,
    val unlockCondition: String?,
)

/**
 * 拍照滤镜。
 */
data class PhotoFilter(
    val filterId: String,
    val name: String,
    val description: String,
    val intensity: Float = 1.0f,
)

/**
 * AR投影设置（预留）。
 */
data class ARConfig(
    val enabled: Boolean = false,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val position: Pair<Float, Float> = Pair(0f, 0f),
    val shadowEnabled: Boolean = true,
    val lightingMode: LightingMode = LightingMode.AUTO,
)

/**
 * 光照模式。
 */
enum class LightingMode {
    AUTO,       // 自动
    NATURAL,    // 自然光
    STUDIO,     // 影棚光
    DRAMATIC,   // 戏剧光
}
