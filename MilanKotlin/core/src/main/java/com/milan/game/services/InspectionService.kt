package com.milan.game.services

import com.milan.game.data.*
import kotlin.random.Random

/**
 * 360°检视系统增强服务。
 *
 * 职责：
 * - 角色互动动作管理
 * - 拍照模式（UI 未接线）
 * - 检视统计
 * - 隐藏内容解锁
 */
class InspectionService(
    private val core: ServiceCore,
    private val rng: Random = Random.Default,
) : InspectionApi {
    
    /**
     * 获取检视数据。
     */
    override fun getInspectionData(): InspectionSaveData {
        // R5-I5：懒创建改纯读——默认值由 sanitize/createDefault 保证非 null，不再锁外写存档。
        return core.saveData.inspectionData ?: InspectionSaveData()
    }
    
    /**
     * 记录角色检视（R5-I6 由锁外直改改为事务 + 落盘）。
     */
    override suspend fun recordInspection(characterId: String): WriteOutcome {
        val data = getInspectionData()
        val currentCount = data.inspectionCounts[characterId] ?: 0
        val original = data.inspectionCounts
        
        return core.transaction(
            tag = "inspection.record",
            mutate = {
                data.inspectionCounts = data.inspectionCounts + (characterId to (currentCount + 1))
            },
            rollback = {
                data.inspectionCounts = original
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
    
    /**
     * 获取角色检视次数。
     */
    override fun getInspectionCount(characterId: String): Int {
        val data = getInspectionData()
        return data.inspectionCounts[characterId] ?: 0
    }
    
    /**
     * 获取角色可用的互动动作。
     */
    override fun getAvailableActions(characterId: String): List<CharacterAction> {
        val data = getInspectionData()
        val inspectionCount = getInspectionCount(characterId)
        
        return getDefaultActions(characterId).filter { action ->
            when (action.type) {
                ActionType.HIDDEN -> inspectionCount >= InspectionSaveData.HIDDEN_ACTION_THRESHOLD
                ActionType.SPECIAL -> data.unlockedActions.contains(action.actionId)
                else -> true
            }
        }
    }
    
    /**
     * 获取默认动作列表。
     */
    private fun getDefaultActions(characterId: String): List<CharacterAction> {
        return listOf(
            CharacterAction(
                actionId = "${characterId}_greeting",
                name = "打招呼",
                description = "角色向你打招呼",
                type = ActionType.GREETING,
                animationName = "anim_greeting",
                voiceLine = "你好呀！",
                unlockCondition = null,
            ),
            CharacterAction(
                actionId = "${characterId}_battle",
                name = "战斗姿势",
                description = "展示角色的战斗姿态",
                type = ActionType.BATTLE_POSE,
                animationName = "anim_battle_pose",
                voiceLine = "准备战斗！",
                unlockCondition = null,
            ),
            CharacterAction(
                actionId = "${characterId}_relax",
                name = "放松",
                description = "角色放松休息",
                type = ActionType.RELAXED,
                animationName = "anim_relax",
                voiceLine = "今天天气真好~",
                unlockCondition = null,
            ),
            CharacterAction(
                actionId = "${characterId}_special",
                name = "特殊动作",
                description = "角色的特殊互动",
                type = ActionType.SPECIAL,
                animationName = "anim_special",
                voiceLine = null,
                unlockCondition = "检视50次解锁",
            ),
            CharacterAction(
                actionId = "${characterId}_hidden",
                name = "隐藏动作",
                description = "角色的隐藏互动",
                type = ActionType.HIDDEN,
                animationName = "anim_hidden",
                voiceLine = "你发现了我的秘密！",
                unlockCondition = "检视100次解锁",
            ),
        )
    }
    
    /**
     * 保存拍照记录。
     */
    override suspend fun savePhoto(record: PhotoRecord): WriteOutcome {
        val data = getInspectionData()
        
        if (data.photoCollection.size >= InspectionSaveData.MAX_PHOTOS) {
            return WriteOutcome.Rejected
        }
        
        val original = data.photoCollection.toList()
        
        return core.transaction(
            tag = "inspection.photo",
            mutate = {
                data.photoCollection = data.photoCollection + record
            },
            rollback = {
                data.photoCollection = original
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
    
    /**
     * 获取拍照姿势列表。
     */
    override fun getPoses(): List<PhotoPose> {
        return listOf(
            PhotoPose("pose_standing", "站立", "标准站立姿势", "anim_standing", null),
            PhotoPose("pose_sitting", "坐姿", "优雅坐姿", "anim_sitting", null),
            PhotoPose("pose_battle", "战斗", "战斗准备姿势", "anim_battle_stance", null),
            PhotoPose("pose_cute", "可爱", "可爱姿势", "anim_cute_pose", "检视20次解锁"),
            PhotoPose("pose_epic", "史诗", "史诗级姿势", "anim_epic_pose", "检视50次解锁"),
        )
    }
    
    /**
     * 获取拍照背景列表。
     */
    override fun getBackgrounds(): List<PhotoBackground> {
        return listOf(
            PhotoBackground("bg_default", "默认", "默认背景", true, null),
            PhotoBackground("bg_sakura", "樱花", "樱花飘落的背景", false, "通关第5章解锁"),
            PhotoBackground("bg_night", "夜晚", "星空夜景", false, "通关第10章解锁"),
            PhotoBackground("bg_battle", "战场", "激烈战场", false, "竞技场达到黄金段位"),
            PhotoBackground("bg_royal", "皇家", "华丽宫廷", false, "累计充值100元"),
        )
    }
    
    /**
     * 获取拍照滤镜列表。
     */
    override fun getFilters(): List<PhotoFilter> {
        return listOf(
            PhotoFilter("filter_none", "无", "无滤镜", 0f),
            PhotoFilter("filter_warm", "暖色", "温暖色调", 0.3f),
            PhotoFilter("filter_cool", "冷色", "冷色调", 0.3f),
            PhotoFilter("filter_vintage", "复古", "复古风格", 0.5f),
            PhotoFilter("filter_dramatic", "戏剧", "戏剧化效果", 0.7f),
            PhotoFilter("filter_anime", "动漫", "动漫风格", 0.4f),
        )
    }
    
    /**
     * 检查是否触发隐藏互动。
     */
    override fun checkHiddenInteraction(characterId: String): Boolean {
        val count = getInspectionCount(characterId)
        if (count < InspectionSaveData.HIDDEN_ACTION_THRESHOLD) return false
        
        // 10%概率触发隐藏互动
        return rng.nextDouble() < 0.1
    }
    
    /**
     * 解锁特殊动作（R5-I6 由锁外直改改为事务 + 落盘）。
     */
    override suspend fun unlockAction(actionId: String): WriteOutcome {
        // R7-P1：已解锁校验入锁
        return core.withWriteLock {
            val data = getInspectionData()
            if (data.unlockedActions.contains(actionId)) return@withWriteLock WriteOutcome.Rejected

            val original = data.unlockedActions.toList()
            core.transactionLocked(
                tag = "inspection.unlock",
                mutate = {
                    data.unlockedActions = data.unlockedActions + actionId
                },
                rollback = {
                    data.unlockedActions = original
                },
                onCommit = {
                    core.publishProgressionChanged()
                },
            )
        }
    }
}
