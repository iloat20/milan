package com.milan.game.services

import com.milan.game.data.*
import kotlin.random.Random

/**
 * 剧情系统服务。
 *
 * 职责：
 * - 章节解锁判定
 * - 关卡进度追踪
 * - 奖励发放
 * - 剧情重播
 */
internal class StoryService(
    private val core: ServiceCore,
    private val rng: Random = Random.Default,
) {

    /** 获取剧情存档数据。 */
    fun getData(): StorySaveData {
        // R5-I5：懒创建改纯读——默认值由 sanitize/createDefault 保证非 null，不再锁外写存档。
        return core.saveData.storyData ?: StorySaveData()
    }

    /** 获取所有章节定义。 */
    fun getChapters(): List<StoryChapterDef> = STORY_CHAPTERS

    /** 获取指定章节定义。 */
    fun getChapter(chapterId: String): StoryChapterDef? =
        STORY_CHAPTERS.firstOrNull { it.chapterId == chapterId }

    /** 检查章节是否已解锁。 */
    fun isChapterUnlocked(chapterId: String): Boolean {
        val chapters = STORY_CHAPTERS
        val index = chapters.indexOfFirst { it.chapterId == chapterId }
        if (index < 0) return false
        return getData().isChapterUnlocked(chapterId, chapters, index)
    }

    /** 获取章节完成进度。 */
    fun getChapterProgress(chapterId: String): Float {
        val chapter = getChapter(chapterId) ?: return 0f
        return getData().chapterProgress(chapterId, chapter.stages.size)
    }

    /** 获取关卡完成状态。 */
    fun isStageCompleted(stageId: String): Boolean = getData().isStageCompleted(stageId)

    /** 检查关卡是否可进入（前置关卡已完成或无前置）。 */
    fun canEnterStage(stageId: String): Boolean {
        val stage = findStageDef(stageId) ?: return false
        if (stage.prerequisiteStageId == null) return true
        return getData().isStageCompleted(stage.prerequisiteStageId)
    }

    /** 查找关卡定义。 */
    fun findStageDef(stageId: String): StoryStageDef? {
        for (chapter in STORY_CHAPTERS) {
            val stage = chapter.stages.firstOrNull { it.stageId == stageId }
            if (stage != null) return stage
        }
        return null
    }

    /**
     * 完成关卡（标记完成 + 一步到位发放奖励）。
     *
     * 事务范式：校验 → 改内存 → 落盘 → 失败回滚。
     * 奖励在完成时一次性发放并同时标记已领取（claimedRewards），杜绝
     * 「completeStage 发一次 + claimReward 再发一次」的双份奖励漏洞。
     */
    suspend fun completeStage(stageId: String): WriteOutcome {
        val data = getData()
        if (data.isStageCompleted(stageId)) return WriteOutcome.Rejected

        val stage = findStageDef(stageId) ?: return WriteOutcome.Rejected
        // 前置校验：服务层防御，杜绝绕过 UI 直接跳关领奖
        if (!canEnterStage(stageId)) return WriteOutcome.Rejected

        // 记录原始状态（回滚用）
        val origCompleted = data.completedStages.toList()
        val origClaimed = data.claimedRewards.toList()
        val origSoft = core.saveData.softCurrency
        val origHard = core.saveData.hardCurrency
        val origCurrentChapter = data.currentChapterId
        val origCurrentStage = data.currentStageId

        return core.transaction(
            tag = "story.completeStage",
            mutate = {
                // 标记关卡完成
                data.completedStages = data.completedStages + stageId

                // 一步到位发放奖励 + 标记已领取
                stage.rewards?.forEach { reward ->
                    when (reward.type) {
                        "soft_currency" -> core.addCurrencyDelta(reward.amount, 0)
                        "hard_currency" -> core.addCurrencyDelta(0, reward.amount)
                        // 其他奖励类型可扩展
                    }
                }
                data.claimedRewards = data.claimedRewards + stageId

                // 更新进行中的章节/关卡
                val chapter = STORY_CHAPTERS.firstOrNull { ch ->
                    ch.stages.any { it.stageId == stageId }
                }
                if (chapter != null) {
                    data.currentChapterId = chapter.chapterId
                    // 找到下一个关卡
                    val stageIndex = chapter.stages.indexOfFirst { it.stageId == stageId }
                    val nextStage = chapter.stages.getOrNull(stageIndex + 1)
                    data.currentStageId = nextStage?.stageId
                }
            },
            rollback = {
                data.completedStages = origCompleted
                data.claimedRewards = origClaimed
                core.saveData.softCurrency = origSoft
                core.saveData.hardCurrency = origHard
                data.currentChapterId = origCurrentChapter
                data.currentStageId = origCurrentStage
            },
            onCommit = {
                core.publishCurrencyChanged()
            },
        )
    }

    /**
     * 领取关卡奖励（如果奖励未领取）。
     */
    suspend fun claimReward(stageId: String): WriteOutcome {
        val data = getData()
        if (!data.isStageCompleted(stageId)) return WriteOutcome.Rejected
        if (data.isRewardClaimed(stageId)) return WriteOutcome.Rejected

        val stage = findStageDef(stageId) ?: return WriteOutcome.Rejected

        val origClaimed = data.claimedRewards.toList()
        val origSoft = core.saveData.softCurrency
        val origHard = core.saveData.hardCurrency

        return core.transaction(
            tag = "story.claimReward",
            mutate = {
                data.claimedRewards = data.claimedRewards + stageId
                stage.rewards?.forEach { reward ->
                    when (reward.type) {
                        "soft_currency" -> core.addCurrencyDelta(reward.amount, 0)
                        "hard_currency" -> core.addCurrencyDelta(0, reward.amount)
                    }
                }
            },
            rollback = {
                data.claimedRewards = origClaimed
                core.saveData.softCurrency = origSoft
                core.saveData.hardCurrency = origHard
            },
            onCommit = {
                core.publishCurrencyChanged()
            },
        )
    }

    companion object {
        /** 内置剧情章节内容（3章，每章5关）。 */
        val STORY_CHAPTERS: List<StoryChapterDef> = listOf(
            // ── 第一章：神谱之始 ──
            StoryChapterDef(
                chapterId = "ch01",
                title = "第一章：神谱之始",
                subtitle = "命运的齿轮开始转动",
                world = "Shinwa",
                accentColor = 0xFF9370DB,
                coverCharacterId = "char_ur_zhulong",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch01_s1",
                        title = "序章·觉醒",
                        type = StoryStageType.DIALOGUE,
                        dialogue = listOf(
                            DialogueLine("narrator", "在神谱大陆的深处，沉睡已久的力量正在苏醒……"),
                            DialogueLine("char_ur_zhulong", "你终于来了。我等这一刻，已经等了千年。", "normal"),
                            DialogueLine("narrator", "一道耀眼的光芒从烛龙的眼中迸发，照亮了整个神殿。"),
                            DialogueLine("char_ur_zhulong", "拿起这把剑，你的命运从这一刻开始改写。", "serious"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 500, "星尘 ×500"),
                            StoryReward("hard_currency", 50, "钻石 ×50"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch01_s2",
                        title = "初入神谱",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch01_s1",
                        dialogue = listOf(
                            DialogueLine("narrator", "你踏上了神谱大陆的土地，四周是陌生而壮丽的景色。"),
                            DialogueLine("char_ur_zhulong", "这片大陆曾经繁荣昌盛，直到黑暗降临。", "sad"),
                            DialogueLine("narrator", "远处传来阵阵嘶吼，似乎有什么东西正在靠近。"),
                            DialogueLine("char_ur_zhulong", "准备好了吗？这是你的第一场战斗。", "serious"),
                        ),
                        enemyIds = listOf("enemy_slime_1", "enemy_slime_2"),
                        recommendedLevel = 1,
                        rewards = listOf(
                            StoryReward("soft_currency", 300, "星尘 ×300"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch01_s3",
                        title = "初遇同伴",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch01_s2",
                        dialogue = listOf(
                            DialogueLine("narrator", "战斗结束后，一位神秘的少女出现在你面前。"),
                            DialogueLine("char_ur_wuxu", "哦？你就是被选中的人？看起来……也没什么特别的嘛。", "tease"),
                            DialogueLine("char_ur_zhulong", "虚无，休得无礼。他/她是我们最后的希望。", "angry"),
                            DialogueLine("char_ur_wuxu", "好吧好吧，既然你这么说。那我就勉强帮帮忙好了。", "happy"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 400, "星尘 ×400"),
                            StoryReward("hard_currency", 30, "钻石 ×30"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch01_s4",
                        title = "黑暗侵袭",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch01_s3",
                        enemyIds = listOf("enemy_shadow_1", "enemy_shadow_2", "enemy_shadow_3"),
                        recommendedLevel = 3,
                        rewards = listOf(
                            StoryReward("soft_currency", 600, "星尘 ×600"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch01_s5",
                        title = "第一章·终章",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch01_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "黑暗势力被击退，神谱大陆暂时恢复了平静。"),
                            DialogueLine("char_ur_zhulong", "做得好。但这只是开始，更大的危机即将来临。", "serious"),
                            DialogueLine("char_ur_wuxu", "哼，别吓唬新人了。不过……确实不能掉以轻心。", "normal"),
                            DialogueLine("narrator", "你握紧了手中的剑，踏上了更遥远的旅途。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
                            StoryReward("hard_currency", 100, "钻石 ×100"),
                        ),
                    ),
                ),
            ),
            // ── 第二章：Aether 之谜 ──
            StoryChapterDef(
                chapterId = "ch02",
                title = "第二章：Aether 之谜",
                subtitle = "虚空深处的秘密",
                world = "Aether",
                accentColor = 0xFF4169E1,
                coverCharacterId = "char_ur_xingtian",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch02_s1",
                        title = "虚空裂隙",
                        type = StoryStageType.DIALOGUE,
                        dialogue = listOf(
                            DialogueLine("narrator", "你来到了 Aether 世界的入口，一道巨大的虚空裂隙横亘在眼前。"),
                            DialogueLine("char_ur_xingtian", "这道裂隙……是通往虚空的门户。我们必须小心。", "serious"),
                            DialogueLine("narrator", "裂隙中传出阵阵低语，似乎在诱惑着什么。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 500, "星尘 ×500"),
                            StoryReward("hard_currency", 50, "钻石 ×50"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch02_s2",
                        title = "虚空守护者",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch02_s1",
                        enemyIds = listOf("enemy_void_1", "enemy_void_2"),
                        recommendedLevel = 5,
                        rewards = listOf(
                            StoryReward("soft_currency", 700, "星尘 ×700"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch02_s3",
                        title = "失落的记忆",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch02_s2",
                        dialogue = listOf(
                            DialogueLine("narrator", "你触碰了裂隙中的水晶，一段失落的记忆涌入脑海。"),
                            DialogueLine("narrator", "你看到了一个古老的文明，他们掌握了操控虚空的力量。"),
                            DialogueLine("char_ur_xingtian", "这是……我们的祖先？原来如此……", "surprised"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 500, "星尘 ×500"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch02_s4",
                        title = "虚空风暴",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch02_s3",
                        enemyIds = listOf("enemy_void_storm"),
                        recommendedLevel = 8,
                        rewards = listOf(
                            StoryReward("soft_currency", 800, "星尘 ×800"),
                            StoryReward("hard_currency", 80, "钻石 ×80"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch02_s5",
                        title = "第二章·终章",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch02_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "虚空风暴平息，裂隙开始缓缓闭合。"),
                            DialogueLine("char_ur_xingtian", "看来我们暂时安全了。但虚空的力量远不止于此。", "serious"),
                            DialogueLine("narrator", "你凝视着闭合的裂隙，心中充满了对未来的期待。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1200, "星尘 ×1200"),
                            StoryReward("hard_currency", 120, "钻石 ×120"),
                        ),
                    ),
                ),
            ),
            // ── 第三章：Ironveil 试炼 ──
            StoryChapterDef(
                chapterId = "ch03",
                title = "第三章：Ironveil 试炼",
                subtitle = "钢铁意志的考验",
                world = "Ironveil",
                accentColor = 0xFFCD853F,
                coverCharacterId = "char_ur_taotie",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch03_s1",
                        title = "钢铁之城",
                        type = StoryStageType.DIALOGUE,
                        dialogue = listOf(
                            DialogueLine("narrator", "你来到了 Ironveil 的核心城市——钢铁之城。"),
                            DialogueLine("char_ur_taotie", "欢迎来到我的地盘！这里的一切都由钢铁铸就。", "happy"),
                            DialogueLine("narrator", "巨大的齿轮在城市上方缓缓转动，发出低沉的轰鸣。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 600, "星尘 ×600"),
                            StoryReward("hard_currency", 60, "钻石 ×60"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch03_s2",
                        title = "试炼开始",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch03_s1",
                        enemyIds = listOf("enemy_golem_1", "enemy_golem_2"),
                        recommendedLevel = 10,
                        rewards = listOf(
                            StoryReward("soft_currency", 900, "星尘 ×900"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch03_s3",
                        title = "钢铁之心",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch03_s2",
                        dialogue = listOf(
                            DialogueLine("narrator", "你发现了一块闪烁着光芒的金属——钢铁之心。"),
                            DialogueLine("char_ur_taotie", "这就是 Ironveil 的力量源泉！只有最强者才能驾驭它。", "serious"),
                            DialogueLine("narrator", "你伸出手，钢铁之心开始共鸣。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 700, "星尘 ×700"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch03_s4",
                        title = "最终试炼",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch03_s3",
                        enemyIds = listOf("enemy_iron_guardian"),
                        recommendedLevel = 12,
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
                            StoryReward("hard_currency", 100, "钻石 ×100"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch03_s5",
                        title = "第三章·终章",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch03_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "你成功通过了 Ironveil 的试炼，获得了钢铁之心的认可。"),
                            DialogueLine("char_ur_taotie", "干得漂亮！你已经证明了自己的实力。", "happy"),
                            DialogueLine("narrator", "三大世界的考验已经完成，但更大的冒险正在等待着你……"),
                            DialogueLine("narrator", "【第一章完结】感谢游玩！更多章节敬请期待。", "normal"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 2000, "星尘 ×2000"),
                            StoryReward("hard_currency", 200, "钻石 ×200"),
                        ),
                    ),
                ),
            ),
        )
    }
}
