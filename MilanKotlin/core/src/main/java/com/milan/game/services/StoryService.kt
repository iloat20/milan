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
@Suppress("DEPRECATION")
class StoryService(
    private val core: ServiceCore,
    private val rng: Random = Random.Default,
) : StoryApi {

    /** 获取剧情存档数据。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override fun getStoryData(): StorySaveData {
        // R5-I5：懒创建改纯读——默认值由 sanitize/createDefault 保证非 null，不再锁外写存档。
        return core.saveData.storyData ?: StorySaveData()
    }

    /** 获取所有章节定义。 */
    override fun getStoryChapters(): List<StoryChapterDef> = STORY_CHAPTERS

    /** 获取指定章节定义。 */
    override fun getStoryChapter(chapterId: String): StoryChapterDef? =
        STORY_CHAPTERS.firstOrNull { it.chapterId == chapterId }

    /** 检查章节是否已解锁。 */
    override fun isStoryChapterUnlocked(chapterId: String): Boolean {
        val chapters = STORY_CHAPTERS
        val index = chapters.indexOfFirst { it.chapterId == chapterId }
        if (index < 0) return false
        return getStoryData().isChapterUnlocked(chapterId, chapters, index)
    }

    /** 获取章节完成进度。 */
    override fun getStoryChapterProgress(chapterId: String): Float {
        val chapter = getStoryChapter(chapterId) ?: return 0f
        return getStoryData().chapterProgress(chapterId, chapter.stages.size)
    }

    /** 获取关卡完成状态。 */
    override fun isStoryStageCompleted(stageId: String): Boolean = getStoryData().isStageCompleted(stageId)

    /** 检查关卡是否可进入（前置关卡已完成或无前置）。 */
    override fun canEnterStoryStage(stageId: String): Boolean {
        val stage = findStoryStageDef(stageId) ?: return false
        val prereqId = stage.prerequisiteStageId
        if (prereqId == null) return true
        return getStoryData().isStageCompleted(prereqId)
    }

    /** 查找关卡定义。 */
    override fun findStoryStageDef(stageId: String): StoryStageDef? {
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
    override suspend fun completeStoryStage(stageId: String): WriteOutcome {
        val stage = findStoryStageDef(stageId) ?: return WriteOutcome.Rejected

        // R6-P1：校验必须在 writeMutex 内复检——锁外 TOCTOU 可并发双完成双发奖。
        return core.withWriteLock {
            val data = getStoryData()
            if (data.isStageCompleted(stageId)) return@withWriteLock WriteOutcome.Rejected
            if (!canEnterStoryStage(stageId)) return@withWriteLock WriteOutcome.Rejected

            // 记录原始状态（回滚用）
            val origCompleted = data.completedStages.toList()
            val origClaimed = data.claimedRewards.toList()
            val origSoft = core.saveData.softCurrency
            val origHard = core.saveData.hardCurrency
            val origCurrentChapter = data.currentChapterId
            val origCurrentStage = data.currentStageId

            core.transactionLocked(
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
    }

    /**
     * 领取关卡奖励（如果奖励未领取）。
     */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override suspend fun claimStoryReward(stageId: String): WriteOutcome {
        val data = getStoryData()
        if (!data.isStageCompleted(stageId)) return WriteOutcome.Rejected
        if (data.isRewardClaimed(stageId)) return WriteOutcome.Rejected

        val stage = findStoryStageDef(stageId) ?: return WriteOutcome.Rejected

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

    // ─────────────────────────── 扫荡（已通关关卡一键重刷）──────────────────────────

    /**
     * 扫荡已通关的剧情关卡（跳过战斗，直接发放奖励）。
     *
     * 前置条件：关卡必须已通关（completedStages 包含该 stageId）。
     * 每次扫荡消耗星尘（体力替代），奖励与首次通关一致。
     */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override suspend fun sweepStoryStage(stageId: String, times: Int): StorySweepOutcome {
        if (times <= 0) return StorySweepOutcome.Rejected
        val data = getStoryData()
        if (!data.isStageCompleted(stageId)) return StorySweepOutcome.Rejected

        val stage = findStoryStageDef(stageId) ?: return StorySweepOutcome.Rejected
        val sweepCost = times * 10 // 每次扫荡 10 星尘

        if (core.saveData.softCurrency < sweepCost) return StorySweepOutcome.Rejected

        val origSC = core.saveData.softCurrency
        // R6-P1：扫荡可发 hard_currency，rollback 必须一并还原，否则落盘失败白嫖钻石
        val origHC = core.saveData.hardCurrency
        var totalSoft = 0
        var totalHard = 0

        return core.transaction(
            tag = "story.sweep",
            mutate = {
                core.addCurrencyDelta(-sweepCost, 0) // 扣除扫荡费用
                repeat(times) {
                    stage.rewards?.forEach { reward ->
                        when (reward.type) {
                            "soft_currency" -> {
                                core.addCurrencyDelta(reward.amount, 0)
                                totalSoft += reward.amount
                            }
                            "hard_currency" -> {
                                core.addCurrencyDelta(0, reward.amount)
                                totalHard += reward.amount
                            }
                        }
                    }
                }
            },
            rollback = {
                core.saveData.softCurrency = origSC
                core.saveData.hardCurrency = origHC
            },
            onCommit = { core.publishCurrencyChanged() },
        ).let {
            when (it) {
                WriteOutcome.Success -> StorySweepOutcome.Success(times, totalSoft, totalHard, sweepCost)
                WriteOutcome.SaveFailed -> StorySweepOutcome.Rejected
                WriteOutcome.Rejected -> StorySweepOutcome.Rejected
            }
        }
    }

    // ─────────────────────────── 困难模式扫荡 ───────────────────────────

    /**
     * 困难模式扫荡已通关的剧情关卡（2倍奖励，消耗2倍星尘）。
     *
     * 前置条件：关卡必须已通关（普通模式）。
     * 困难模式奖励 = 普通模式 × 2，消耗 = 普通模式 × 2。
     * 首次困难通关记录到 hardModeCompleted，后续扫荡不再标记。
     */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override suspend fun sweepStoryStageHard(stageId: String, times: Int): StorySweepOutcome {
        if (times <= 0) return StorySweepOutcome.Rejected
        val data = getStoryData()
        if (!data.isStageCompleted(stageId)) return StorySweepOutcome.Rejected

        val stage = findStoryStageDef(stageId) ?: return StorySweepOutcome.Rejected
        val sweepCost = times * 20 // 困难模式 20 星尘/次

        if (core.saveData.softCurrency < sweepCost) return StorySweepOutcome.Rejected

        val origSC = core.saveData.softCurrency
        // R6-P1：困难扫荡同样可发 hard，rollback 需还原 hardCurrency
        val origHC = core.saveData.hardCurrency
        val origHard = data.hardModeCompleted.toList()
        var totalSoft = 0
        var totalHard = 0
        val isFirstHard = !data.isHardModeCompleted(stageId)

        return core.transaction(
            tag = "story.sweepHard",
            mutate = {
                core.addCurrencyDelta(-sweepCost, 0)
                repeat(times) {
                    stage.rewards?.forEach { reward ->
                        when (reward.type) {
                            "soft_currency" -> {
                                core.addCurrencyDelta(reward.amount * 2, 0) // 困难模式 2 倍
                                totalSoft += reward.amount * 2
                            }
                            "hard_currency" -> {
                                core.addCurrencyDelta(0, reward.amount * 2)
                                totalHard += reward.amount * 2
                            }
                        }
                    }
                }
                // 首次困难通关标记
                if (isFirstHard) {
                    data.hardModeCompleted = data.hardModeCompleted + stageId
                }
            },
            rollback = {
                core.saveData.softCurrency = origSC
                core.saveData.hardCurrency = origHC
                data.hardModeCompleted = origHard
            },
            onCommit = { core.publishCurrencyChanged() },
        ).let {
            when (it) {
                WriteOutcome.Success -> StorySweepOutcome.Success(times, totalSoft, totalHard, sweepCost)
                WriteOutcome.SaveFailed -> StorySweepOutcome.Rejected
                WriteOutcome.Rejected -> StorySweepOutcome.Rejected
            }
        }
    }

    /** 关卡困难模式是否已完成。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override fun isStoryHardModeCompleted(stageId: String): Boolean = getStoryData().isHardModeCompleted(stageId)

    // ─────────────────────────── 章节完成奖励 ───────────────────────────

    /**
     * 领取章节全通关奖励（该章节所有关卡已完成时可领取）。
     *
     * 每个章节全通关发放一次性额外奖励（钻石 + 星尘），鼓励推完全部关卡。
     * 每个章节只能领取一次。
     */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override suspend fun claimStoryChapterReward(chapterId: String): WriteOutcome {
        val data = getStoryData()
        val chapter = getStoryChapter(chapterId) ?: return WriteOutcome.Rejected

        // 已领取过
        if (data.claimedChapterRewards.contains(chapterId)) return WriteOutcome.Rejected

        // 章节未全通关
        val totalStages = chapter.stages.size
        val completedCount = chapter.stages.count { data.isStageCompleted(it.stageId) }
        if (completedCount < totalStages) return WriteOutcome.Rejected

        val origClaimed = data.claimedChapterRewards.toList()
        val origSC = core.saveData.softCurrency
        val origHC = core.saveData.hardCurrency

        // 章节全通奖励：基于章节数的阶梯奖励
        val chapterIndex = STORY_CHAPTERS.indexOfFirst { it.chapterId == chapterId }
        val chapterNumber = chapterIndex + 1
        val softReward = 2000 * chapterNumber
        val hardReward = 30 * chapterNumber

        return core.transaction(
            tag = "story.claimChapterReward",
            mutate = {
                data.claimedChapterRewards = data.claimedChapterRewards + chapterId
                core.addCurrencyDelta(softReward, hardReward)
            },
            rollback = {
                data.claimedChapterRewards = origClaimed
                core.saveData.softCurrency = origSC
                core.saveData.hardCurrency = origHC
            },
            onCommit = { core.publishCurrencyChanged() },
        )
    }

    // ─────────────────────────── 剧情进度统计 ───────────────────────────

    /**
     * 获取剧情整体进度统计（供 UI 展示）。
     */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override fun getStoryProgress(): StoryProgressStats {
        val data = getStoryData()
        val totalStages = STORY_CHAPTERS.sumOf { it.stages.size }
        val completedStages = data.completedStages.filterNotNull().size

        val chapterStats = STORY_CHAPTERS.map { chapter ->
            val chapterCompleted = chapter.stages.count { data.isStageCompleted(it.stageId) }
            val chapterTotal = chapter.stages.size
            val rewardClaimed = data.claimedChapterRewards.contains(chapter.chapterId)
            StoryChapterProgress(
                chapterId = chapter.chapterId,
                title = chapter.title,
                completedStages = chapterCompleted,
                totalStages = chapterTotal,
                progressPercent = if (chapterTotal > 0) chapterCompleted.toFloat() / chapterTotal else 0f,
                rewardClaimed = rewardClaimed,
                rewardAvailable = !rewardClaimed && chapterCompleted == chapterTotal,
            )
        }

        return StoryProgressStats(
            totalStages = totalStages,
            completedStages = completedStages,
            overallProgress = if (totalStages > 0) completedStages.toFloat() / totalStages else 0f,
            chaptersCompleted = STORY_CHAPTERS.size,
            chaptersFullyCompleted = chapterStats.count { it.completedStages == it.totalStages },
            chapterStats = chapterStats,
        )
    }

    companion object {
        /** 内置剧情章节内容（5章，每章5关，共25关）。 */
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
            // ── 第四章：裂隙之战 ──
            StoryChapterDef(
                chapterId = "ch04",
                title = "第四章：裂隙之战",
                subtitle = "三界联军的反击",
                world = "Shinwa",
                accentColor = 0xFFFF6347,
                coverCharacterId = "char_ur_xingtian",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch04_s1",
                        title = "战争序幕",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch03_s5",
                        dialogue = listOf(
                            DialogueLine("narrator", "裂隙的扩大引发了三界的动荡，黑暗势力开始大规模入侵。"),
                            DialogueLine("char_ur_xingtian", "我们必须联合三界的力量，否则将被各个击破。", "serious"),
                            DialogueLine("char_ur_zhulong", "同意。我去联络神谱的守卫者，你去集结铁帷的军团。", "normal"),
                            DialogueLine("narrator", "一场跨越三界的战争即将打响。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 800, "星尘 ×800"),
                            StoryReward("hard_currency", 80, "钻石 ×80"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch04_s2",
                        title = "前线战场",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch04_s1",
                        enemyIds = listOf("enemy_dark_1", "enemy_dark_2", "enemy_dark_3", "enemy_dark_4"),
                        recommendedLevel = 15,
                        rewards = listOf(
                            StoryReward("soft_currency", 1200, "星尘 ×1200"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch04_s3",
                        title = "英雄集结",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch04_s2",
                        dialogue = listOf(
                            DialogueLine("narrator", "三界的英雄们齐聚一堂，组成了前所未有的联军。"),
                            DialogueLine("char_ur_wuxu", "哼，没想到有一天我会和这些家伙并肩作战。", "tease"),
                            DialogueLine("char_ur_keqing", "少废话，敌人不会等我们准备好。", "angry"),
                            DialogueLine("narrator", "联军士气高涨，准备发起反攻。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
                            StoryReward("hard_currency", 100, "钻石 ×100"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch04_s4",
                        title = "总攻开始",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch04_s3",
                        enemyIds = listOf("enemy_dark_lord_1", "enemy_dark_lord_2"),
                        recommendedLevel = 18,
                        rewards = listOf(
                            StoryReward("soft_currency", 1500, "星尘 ×1500"),
                            StoryReward("hard_currency", 150, "钻石 ×150"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch04_s5",
                        title = "第四章·终章",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch04_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "联军取得了初步胜利，但黑暗势力的主力仍未被消灭。"),
                            DialogueLine("char_ur_xingtian", "这只是开始，真正的敌人还在裂隙深处。", "serious"),
                            DialogueLine("char_ur_zhulong", "准备进入裂隙，终结这场战争。", "normal"),
                            DialogueLine("narrator", "联军开始向裂隙深处进发。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 2000, "星尘 ×2000"),
                            StoryReward("hard_currency", 200, "钻石 ×200"),
                        ),
                    ),
                ),
            ),
            // ── 第五章：终焉之战 ──
            StoryChapterDef(
                chapterId = "ch05",
                title = "第五章：终焉之战",
                subtitle = "命运的最终决战",
                world = "Aether",
                accentColor = 0xFF8B0000,
                coverCharacterId = "char_ur_wuxu",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch05_s1",
                        title = "裂隙深处",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch04_s5",
                        dialogue = listOf(
                            DialogueLine("narrator", "联军深入裂隙，发现这里是一个扭曲的空间。"),
                            DialogueLine("char_ur_wuxu", "这里的空间法则完全混乱，小心。", "serious"),
                            DialogueLine("narrator", "远处传来强大的压迫感，黑暗势力的首领正在等待着他们。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
                            StoryReward("hard_currency", 100, "钻石 ×100"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch05_s2",
                        title = "黑暗军团",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch05_s1",
                        enemyIds = listOf("enemy_dark_elite_1", "enemy_dark_elite_2", "enemy_dark_elite_3"),
                        recommendedLevel = 20,
                        rewards = listOf(
                            StoryReward("soft_currency", 1800, "星尘 ×1800"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch05_s3",
                        title = "最终对决",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch05_s2",
                        dialogue = listOf(
                            DialogueLine("narrator", "黑暗势力的首领出现在众人面前，散发着毁灭的气息。"),
                            DialogueLine("char_ur_wuxu", "终于见面了，虚无的化身。", "serious"),
                            DialogueLine("narrator", "一场决定三界命运的战斗即将开始。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1500, "星尘 ×1500"),
                            StoryReward("hard_currency", 150, "钻石 ×150"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch05_s4",
                        title = "终焉之战",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch05_s3",
                        enemyIds = listOf("enemy_dark_final_boss"),
                        recommendedLevel = 25,
                        rewards = listOf(
                            StoryReward("soft_currency", 3000, "星尘 ×3000"),
                            StoryReward("hard_currency", 300, "钻石 ×300"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch05_s5",
                        title = "终章·新的开始",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch05_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "黑暗势力被彻底击败，裂隙开始愈合。"),
                            DialogueLine("char_ur_zhulong", "我们做到了。三界终于可以恢复和平了。", "happy"),
                            DialogueLine("char_ur_wuxu", "哼，别高兴得太早。裂隙虽然愈合，但世界已经改变了。", "normal"),
                            DialogueLine("narrator", "联军开始重建三界，新的时代即将到来。"),
                            DialogueLine("narrator", "【全章完结】感谢游玩！更多内容敬请期待。", "normal"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 5000, "星尘 ×5000"),
                            StoryReward("hard_currency", 500, "钻石 ×500"),
                        ),
                    ),
                ),
            ),
        )
    }
}

/** 剧情关卡扫荡结果。 */
sealed interface StorySweepOutcome {
    data class Success(val times: Int, val totalSoft: Int, val totalHard: Int, val costSoft: Int) : StorySweepOutcome
    data object Rejected : StorySweepOutcome
}

/** 剧情整体进度统计（供 UI 展示）。 */
data class StoryProgressStats(
    val totalStages: Int,
    val completedStages: Int,
    val overallProgress: Float,
    val chaptersCompleted: Int,
    val chaptersFullyCompleted: Int,
    val chapterStats: List<StoryChapterProgress>,
)

/** 单个章节进度。 */
data class StoryChapterProgress(
    val chapterId: String,
    val title: String,
    val completedStages: Int,
    val totalStages: Int,
    val progressPercent: Float,
    val rewardClaimed: Boolean,
    val rewardAvailable: Boolean,
)
