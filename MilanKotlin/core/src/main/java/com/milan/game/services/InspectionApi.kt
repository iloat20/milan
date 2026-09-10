package com.milan.game.services

import com.milan.game.data.CharacterAction
import com.milan.game.data.InspectionSaveData
import com.milan.game.data.PhotoBackground
import com.milan.game.data.PhotoFilter
import com.milan.game.data.PhotoPose
import com.milan.game.data.PhotoRecord

/**
 * 角色检视与拍照契约（2026-09-08 P1-5 接口化）。
 *
 * ⚠️ **P2-11 (B12) 死功能**：检视/拍照系统 UI 层零调用，
 * 本接口全部方法无生产调用点。待后续接线或移除。
 */
@Deprecated("P2-11: 检视拍照系统UI零调用，待接线或移除", level = DeprecationLevel.WARNING)
interface InspectionApi {
    /** 检视存档数据（各角色检视次数 / 已解锁动作 / 照片）。 */
    fun getInspectionData(): InspectionSaveData

    /** 记录一次角色检视（每日上限内；推进解锁进度）。 */
    suspend fun recordInspection(characterId: String): WriteOutcome

    /** 指定角色累计检视次数。 */
    fun getInspectionCount(characterId: String): Int

    /** 指定角色当前可用互动动作列表（好感度门槛实时过滤）。 */
    fun getAvailableActions(characterId: String): List<CharacterAction>

    /** 保存一张照片记录。 */
    suspend fun savePhoto(record: PhotoRecord): WriteOutcome

    /** 拍照姿势资产列表。 */
    fun getPoses(): List<PhotoPose>

    /** 拍照背景资产列表。 */
    fun getBackgrounds(): List<PhotoBackground>

    /** 拍照滤镜资产列表。 */
    fun getFilters(): List<PhotoFilter>

    /** 隐藏互动判定（好感度达标角色专属剧情入口）。 */
    fun checkHiddenInteraction(characterId: String): Boolean

    /** 解锁互动动作（消耗检视解锁道具）。 */
    suspend fun unlockAction(actionId: String): WriteOutcome
}
