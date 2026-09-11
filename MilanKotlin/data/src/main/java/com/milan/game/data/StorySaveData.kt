package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 剧情系统存档数据。
 *
 * 对标崩铁/原神的剧情章节系统：每个章节有多个关卡，每个关卡包含对话/战斗/奖励。
 * 存档只记录进度（已完成的关卡、领取的奖励），内容定义在代码内兜底。
 */
@Serializable
class StorySaveData(
    /** 已完成的关卡 ID 列表（stageId）。 */
    @SerialName("CompletedStages")
    var completedStages: List<String?> = emptyList(),
    /** 已领取奖励的关卡 ID 列表。 */
    @SerialName("ClaimedRewards")
    var claimedRewards: List<String?> = emptyList(),
    /** 当前进行中的章节 ID（null = 无进行中）。 */
    @SerialName("CurrentChapterId")
    var currentChapterId: String? = null,
    /** 当前进行中的关卡 ID（null = 无进行中）。 */
    @SerialName("CurrentStageId")
    var currentStageId: String? = null,
    /** 已领取奖励的章节 ID 列表。 */
    @SerialName("ClaimedChapterRewards")
    var claimedChapterRewards: List<String?> = emptyList(),
    /** 已通关困难模式的关卡 ID 列表。 */
    @SerialName("HardModeCompleted")
    var hardModeCompleted: List<String?> = emptyList(),
    /**
     * 第一纪元结局分支 ID（ch08 三道光选择落档；null = 未选）。
     * 可选字段带默认值，旧档缺键时反序列化为 null，不破坏兼容。
     */
    @SerialName("EndingBranchId")
    var endingBranchId: String? = null,
) {
    /** 检查关卡是否已完成。 */
    fun isStageCompleted(stageId: String): Boolean =
        completedStages.filterNotNull().contains(stageId)

    /** 检查奖励是否已领取。 */
    fun isRewardClaimed(stageId: String): Boolean =
        claimedRewards.filterNotNull().contains(stageId)

    /** 检查关卡困难模式是否已完成。 */
    fun isHardModeCompleted(stageId: String): Boolean =
        hardModeCompleted.filterNotNull().contains(stageId)

    /** 获取章节完成进度（已完成关卡数 / 总关卡数）。 */
    fun chapterProgress(chapterId: String, totalStages: Int): Float {
        if (totalStages <= 0) return 0f
        val completed = completedStages.filterNotNull().count { it.startsWith(chapterId) }
        return completed.toFloat() / totalStages
    }

    /** 检查章节是否已解锁（前置章节完成或为首章）。 */
    fun isChapterUnlocked(chapterId: String, allChapters: List<StoryChapterDef>, currentIndex: Int): Boolean {
        if (currentIndex <= 0) return true
        val prevChapter = allChapters.getOrNull(currentIndex - 1) ?: return true
        val prevTotalStages = prevChapter.stages.size
        return chapterProgress(prevChapter.chapterId, prevTotalStages) >= 1f
    }

    companion object {
        /** 每章最大关卡数。 */
        const val MAX_STAGES_PER_CHAPTER = 10
    }
}

/**
 * 剧情关卡类型。
 */
@Serializable
enum class StoryStageType {
    /** 对话关卡（视觉小说式）。 */
    DIALOGUE,
    /** 战斗关卡。 */
    BATTLE,
    /** 选择分支关卡。 */
    CHOICE,
}

/**
 * 对话行数据。
 */
@Serializable
data class DialogueLine(
    /** 说话者 ID（对应角色 ID 或特殊标识如 "narrator"）。 */
    @SerialName("SpeakerId")
    val speakerId: String,
    /** 对话文本。 */
    @SerialName("Text")
    val text: String,
    /** 说话者立绘状态（normal/happy/angry/sad/surprised）。 */
    @SerialName("Emotion")
    val emotion: String = "normal",
    /** 选择分支（仅 CHOICE 类型关卡使用）。 */
    @SerialName("Choices")
    val choices: List<StoryChoice>? = null,
)

/**
 * 剧情选择分支。
 *
 * [affinityCharacterId] 为空时，好感记到「该选项所在对白行的说话者」头上
 * （DialogueScreen 既有语义）。需要给同屏其他角色加好感时显式指定。
 */
@Serializable
data class StoryChoice(
    /** 选项文本。 */
    @SerialName("Text")
    val text: String,
    /** 选择后跳转的关卡 ID（null = 结束当前关卡）。 */
    @SerialName("NextStageId")
    val nextStageId: String? = null,
    /** 选择后增加的好感度。 */
    @SerialName("AffinityBonus")
    val affinityBonus: Int = 0,
    /** 好感入账角色（null = 当前行说话者）。 */
    @SerialName("AffinityCharacterId")
    val affinityCharacterId: String? = null,
    /** 结局分支 ID（非空时写入 StorySaveData.endingBranchId，覆盖先前选择）。 */
    @SerialName("EndingBranchId")
    val endingBranchId: String? = null,
)

/**
 * 剧情奖励定义。
 */
@Serializable
data class StoryReward(
    /** 奖励类型（soft_currency/hard_currency/item/character_exp）。 */
    @SerialName("Type")
    val type: String,
    /** 奖励数量。 */
    @SerialName("Amount")
    val amount: Int,
    /** 奖励描述（展示用）。 */
    @SerialName("Description")
    val description: String,
)

/**
 * 剧情关卡定义（内容）。
 */
@Serializable
data class StoryStageDef(
    /** 关卡 ID（格式：chapterId_stageN）。 */
    @SerialName("StageId")
    val stageId: String,
    /** 关卡标题。 */
    @SerialName("Title")
    val title: String,
    /** 关卡类型。 */
    @SerialName("Type")
    val type: StoryStageType,
    /** 对话内容（DIALOGUE/CHOICE 类型）。 */
    @SerialName("Dialogue")
    val dialogue: List<DialogueLine>? = null,
    /** 战斗关卡的敌人列表（BATTLE 类型）。 */
    @SerialName("EnemyIds")
    val enemyIds: List<String>? = null,
    /** 战斗关卡推荐等级。 */
    @SerialName("RecommendedLevel")
    val recommendedLevel: Int = 1,
    /** 通关奖励。 */
    @SerialName("Rewards")
    val rewards: List<StoryReward>? = null,
    /** 前置关卡 ID（null = 无前置，首关可直接进入）。 */
    @SerialName("PrerequisiteStageId")
    val prerequisiteStageId: String? = null,
)

/**
 * 剧情章节定义（内容）。
 */
@Serializable
data class StoryChapterDef(
    /** 章节 ID。 */
    @SerialName("ChapterId")
    val chapterId: String,
    /** 章节标题。 */
    @SerialName("Title")
    val title: String,
    /** 章节副标题/描述。 */
    @SerialName("Subtitle")
    val subtitle: String,
    /** 章节世界归属（Shinwa/Aether/Ironveil）。 */
    @SerialName("World")
    val world: String,
    /** 章节内的关卡列表。 */
    @SerialName("Stages")
    val stages: List<StoryStageDef>,
    /** 章节背景色（十六进制）。 */
    @SerialName("AccentColor")
    val accentColor: Long = 0xFF9370DB,
    /** 章节封面角色 ID（展示用）。 */
    @SerialName("CoverCharacterId")
    val coverCharacterId: String? = null,
    /**
     * 好感外传门槛：目标角色 ID（null = 主线章，走「前一章全通」解锁）。
     * 与 [requiredAffinityLevel] 成对使用。
     */
    @SerialName("RequiredAffinityCharacterId")
    val requiredAffinityCharacterId: String? = null,
    /** 好感外传门槛：所需好感等级（0 = 只要求拥有该角色）。 */
    @SerialName("RequiredAffinityLevel")
    val requiredAffinityLevel: Int = 0,
)
