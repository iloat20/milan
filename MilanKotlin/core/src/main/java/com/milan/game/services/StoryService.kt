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

    /** 检查章节是否已解锁。主线：前一章全通；外传：拥有角色且好感达档。 */
    override fun isStoryChapterUnlocked(chapterId: String): Boolean {
        val chapters = STORY_CHAPTERS
        val index = chapters.indexOfFirst { it.chapterId == chapterId }
        if (index < 0) return false
        val chapter = chapters[index]

        // 好感外传：不走「前一章全通」链，避免外传卡主线进度
        val affinityChar = chapter.requiredAffinityCharacterId
        if (affinityChar != null) {
            val owned = core.characters.any { it.characterId == affinityChar }
            if (!owned) return false
            val level = AffinityFormulas.levelOf(core.affinityOf(affinityChar))
            return level >= chapter.requiredAffinityLevel
        }

        return getStoryData().isChapterUnlocked(chapterId, chapters, index)
    }

    /** 获取章节完成进度。 */
    override fun getStoryChapterProgress(chapterId: String): Float {
        val chapter = getStoryChapter(chapterId) ?: return 0f
        return getStoryData().chapterProgress(chapterId, chapter.stages.size)
    }

    /** 第一纪元结局分支 ID。 */
    override fun getStoryEndingBranchId(): String? = getStoryData().endingBranchId

    /**
     * 写入结局分支（ch08「三道光」）。
     *
     * 可重复选择覆盖（重播关卡时以最后一次为准）；空串拒绝。
     * 落盘失败回滚，不阻断对话流程（UI 侧静默）。
     */
    override suspend fun setStoryEndingBranch(branchId: String): WriteOutcome {
        if (branchId.isBlank()) return WriteOutcome.Rejected
        return core.withWriteLock {
            val data = getStoryData()
            val orig = data.endingBranchId
            core.transactionLocked(
                tag = "story.setEndingBranch",
                mutate = { data.endingBranchId = branchId },
                rollback = { data.endingBranchId = orig },
                onCommit = {},
            )
        }
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
        val stage = findStoryStageDef(stageId) ?: return WriteOutcome.Rejected
        // R7-P1：completed/claimed 校验必须在锁内复检（防并发双领）
        return core.withWriteLock {
            val data = getStoryData()
            if (!data.isStageCompleted(stageId)) return@withWriteLock WriteOutcome.Rejected
            if (data.isRewardClaimed(stageId)) return@withWriteLock WriteOutcome.Rejected

            val origClaimed = data.claimedRewards.toList()
            val origSoft = core.saveData.softCurrency
            val origHard = core.saveData.hardCurrency

            core.transactionLocked(
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

        val stage = findStoryStageDef(stageId) ?: return StorySweepOutcome.Rejected
        // R7-P1：times*10 可 Int 溢出为负 → 扣费变加钱。Long 预算 + 上界钳制。
        val sweepCostLong = times.toLong() * 10L
        if (sweepCostLong > Int.MAX_VALUE) return StorySweepOutcome.Rejected
        val sweepCost = sweepCostLong.toInt()

        var totalSoft = 0
        var totalHard = 0
        // R7-P1：通关/余额校验入锁
        return core.withWriteLock {
            val data = getStoryData()
            if (!data.isStageCompleted(stageId)) return@withWriteLock StorySweepOutcome.Rejected
            if (core.saveData.softCurrency < sweepCost) return@withWriteLock StorySweepOutcome.Rejected

            val origSC = core.saveData.softCurrency
            val origHC = core.saveData.hardCurrency

            core.transactionLocked(
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

        val stage = findStoryStageDef(stageId) ?: return StorySweepOutcome.Rejected
        // R7-P1：times*20 同样可 Int 溢出
        val sweepCostLong = times.toLong() * 20L
        if (sweepCostLong > Int.MAX_VALUE) return StorySweepOutcome.Rejected
        val sweepCost = sweepCostLong.toInt()

        var totalSoft = 0
        var totalHard = 0
        // R7-P1：通关/余额校验入锁
        return core.withWriteLock {
            val data = getStoryData()
            if (!data.isStageCompleted(stageId)) return@withWriteLock StorySweepOutcome.Rejected
            if (core.saveData.softCurrency < sweepCost) return@withWriteLock StorySweepOutcome.Rejected

            val origSC = core.saveData.softCurrency
            val origHC = core.saveData.hardCurrency
            val origHard = data.hardModeCompleted.toList()
            val isFirstHard = !data.isHardModeCompleted(stageId)

            core.transactionLocked(
                tag = "story.sweepHard",
                mutate = {
                    core.addCurrencyDelta(-sweepCost, 0)
                    repeat(times) {
                        stage.rewards?.forEach { reward ->
                            when (reward.type) {
                                "soft_currency" -> {
                                    core.addCurrencyDelta(reward.amount * 2, 0)
                                    totalSoft += reward.amount * 2
                                }
                                "hard_currency" -> {
                                    core.addCurrencyDelta(0, reward.amount * 2)
                                    totalHard += reward.amount * 2
                                }
                            }
                        }
                    }
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
        val chapter = getStoryChapter(chapterId) ?: return WriteOutcome.Rejected
        // R7-P1：claimed/全通校验移入锁内
        return core.withWriteLock {
            val data = getStoryData()
            if (data.claimedChapterRewards.contains(chapterId)) return@withWriteLock WriteOutcome.Rejected
            val totalStages = chapter.stages.size
            val completedCount = chapter.stages.count { data.isStageCompleted(it.stageId) }
            if (completedCount < totalStages) return@withWriteLock WriteOutcome.Rejected

            val origClaimed = data.claimedChapterRewards.toList()
            val origSC = core.saveData.softCurrency
            val origHC = core.saveData.hardCurrency

            val chapterIndex = STORY_CHAPTERS.indexOfFirst { it.chapterId == chapterId }
            val chapterNumber = chapterIndex + 1
            val softReward = 2000 * chapterNumber
            val hardReward = 30 * chapterNumber

            core.transactionLocked(
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
        /**
         * 内置剧情章节内容。
         *
         * 2026-09-11 三幕九章续设 + P3：
         * - 主线 ch01–09（第一纪元）；ch08「三道光」写 EndingBranchId（end_dawn/end_debt/end_burn）
         * - 好感外传 side_*（不占主线序号；RequiredAffinityCharacterId + Level 解锁）
         * CHOICE 可指定好感入账角色与结局分支。
         * 规格见 docs/superpowers/specs/2026-09-11-story-campaign-design.md。
         */
        val STORY_CHAPTERS: List<StoryChapterDef> = listOf(
            // ── 第一幕 · 章一：永昼之焰（Shinwa）──
            StoryChapterDef(
                chapterId = "ch01",
                title = "第一章：永昼之焰",
                subtitle = "环的回响",
                world = "Shinwa",
                accentColor = 0xFF9A6BFF,
                coverCharacterId = "char_ur_zhulong",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch01_s1",
                        title = "序章·环的回响",
                        type = StoryStageType.DIALOGUE,
                        dialogue = listOf(
                            DialogueLine("narrator", "裂隙的光像旧瓷器上的金线，把你从虚无里缝了回来。"),
                            DialogueLine("char_ur_zhulong", "你终于醒了。我等这一刻，已经等了千年。", "normal"),
                            DialogueLine("narrator", "它睁着眼。神話的河，在第七天开始见底。"),
                            DialogueLine("char_ur_zhulong", "拿好你的名字——织环者。环碎了，你来拼。", "serious"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 500, "星尘 ×500"),
                            StoryReward("hard_currency", 50, "钻石 ×50"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch01_s2",
                        title = "河床见底",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch01_s1",
                        dialogue = listOf(
                            DialogueLine("narrator", "焦土延伸到山脚。有人骂烛龙，有人跪着求它再睁一日。"),
                            DialogueLine("char_ur_zhulong", "永昼不是恩赐。是我在拿河换时间。", "sad"),
                            DialogueLine("char_ur_jinwu", "节律撑不过长夜。该有人把光烧得更狠。", "angry"),
                            DialogueLine("narrator", "金乌落在断崖上，羽尖像第二轮不肯西沉的日。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 300, "星尘 ×300"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch01_s3",
                        title = "雾中召灵",
                        type = StoryStageType.CHOICE,
                        prerequisiteStageId = "ch01_s2",
                        dialogue = listOf(
                            DialogueLine("narrator", "山雾里浮出微光——裂隙送来的第一份力量，正等你伸手。"),
                            DialogueLine(
                                "char_ur_zhulong",
                                "织环者，你认哪一种光？",
                                "serious",
                                choices = listOf(
                                    StoryChoice(
                                        text = "节律比炽热更长久。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ur_zhulong",
                                    ),
                                    StoryChoice(
                                        text = "没有强度，节律撑不过长夜。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ur_jinwu",
                                    ),
                                    StoryChoice(
                                        text = "我两个都要。",
                                        affinityBonus = 3,
                                        affinityCharacterId = "char_ur_zhulong",
                                    ),
                                ),
                            ),
                            DialogueLine("narrator", "你的回响轻轻一颤。雾里，有什么睁开了眼。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 400, "星尘 ×400"),
                            StoryReward("hard_currency", 30, "钻石 ×30"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch01_s4",
                        title = "雾魑侵袭",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch01_s3",
                        enemyIds = listOf("rift_mistling", "rift_mistling", "rift_shadowwhisper"),
                        recommendedLevel = 3,
                        rewards = listOf(
                            StoryReward("soft_currency", 600, "星尘 ×600"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch01_s5",
                        title = "虚无的侧影",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch01_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "雾散了。更深处，却有一道没有形状的注视。"),
                            DialogueLine("char_ur_wuxu", "……织环？有意思。你们只是把债期延后。", "tease"),
                            DialogueLine("char_ur_zhulong", "虚无。退开。", "angry"),
                            DialogueLine("char_ur_jinwu", "记住这道影子。它不是来交朋友的。", "serious"),
                            DialogueLine("narrator", "以太的星图在远方转。下一站，浮岛。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
                            StoryReward("hard_currency", 100, "钻石 ×100"),
                        ),
                    ),
                ),
            ),
            // ── 第一幕 · 章二：星环低语（Aether）──
            StoryChapterDef(
                chapterId = "ch02",
                title = "第二章：星环低语",
                subtitle = "命运可违",
                world = "Aether",
                accentColor = 0xFF00E5FF,
                coverCharacterId = "char_ur_nuwa",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch02_s1",
                        title = "浮岛之门",
                        type = StoryStageType.DIALOGUE,
                        dialogue = listOf(
                            DialogueLine("narrator", "以太的星图在脚下转，像谁打翻了一盘碎钻。"),
                            DialogueLine("char_ur_nuwa", "站稳。这岛是我用最后一块补天石托住的。", "normal"),
                            DialogueLine("char_ssr_shangyang", "我看见两极。一头是昼，一头是无。中间是你。", "serious"),
                            DialogueLine("char_ur_keqing", "别念了。命运是拿来砍的，不是拿来跪的。", "angry"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 500, "星尘 ×500"),
                            StoryReward("hard_currency", 50, "钻石 ×50"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch02_s2",
                        title = "虚螨潮",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch02_s1",
                        enemyIds = listOf("rift_voidmite", "rift_voidmite", "rift_voidmite"),
                        recommendedLevel = 5,
                        rewards = listOf(
                            StoryReward("soft_currency", 700, "星尘 ×700"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch02_s3",
                        title = "议会的罪",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch02_s2",
                        dialogue = listOf(
                            DialogueLine("narrator", "水晶里封着一段会议记录：他们把裂隙当门，推得更开。"),
                            DialogueLine("char_ssr_shangyang", "是我们唤醒了它。预言看见了代价，议会却只想看见钥匙。", "sad"),
                            DialogueLine("char_ur_keqing", "所以更该由人来关。神和议会，都靠不住。", "serious"),
                            DialogueLine("char_ur_nuwa", "吵完了就干活。浮岛不会自己长牢。", "normal"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 500, "星尘 ×500"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch02_s4",
                        title = "命运可违？",
                        type = StoryStageType.CHOICE,
                        prerequisiteStageId = "ch02_s3",
                        dialogue = listOf(
                            DialogueLine(
                                "char_ssr_shangyang",
                                "织环者——你信路标，还是信手中的剑？",
                                "serious",
                                choices = listOf(
                                    StoryChoice(
                                        text = "预言是路标，不是锁链。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ssr_shangyang",
                                    ),
                                    StoryChoice(
                                        text = "剑比星轨快。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ur_keqing",
                                    ),
                                    StoryChoice(
                                        text = "先活过今晚，再吵哲学。",
                                        affinityBonus = 5,
                                        affinityCharacterId = "char_ur_nuwa",
                                    ),
                                ),
                            ),
                            DialogueLine("narrator", "星图忽然一滞。风暴心在岛下方成形。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 500, "星尘 ×500"),
                            StoryReward("hard_currency", 50, "钻石 ×50"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch02_s5",
                        title = "风暴心",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch02_s4",
                        enemyIds = listOf("rift_stormheart"),
                        recommendedLevel = 8,
                        rewards = listOf(
                            StoryReward("soft_currency", 1200, "星尘 ×1200"),
                            StoryReward("hard_currency", 120, "钻石 ×120"),
                        ),
                    ),
                ),
            ),
            // ── 第一幕 · 章三：钢心未冷（Ironveil）──
            StoryChapterDef(
                chapterId = "ch03",
                title = "第三章：钢心未冷",
                subtitle = "兵主之问",
                world = "Ironveil",
                accentColor = 0xFF4A90D9,
                coverCharacterId = "char_ur_xingtian",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch03_s1",
                        title = "齿轮之城",
                        type = StoryStageType.DIALOGUE,
                        dialogue = listOf(
                            DialogueLine("narrator", "铁帷的齿轮咬着天。锈味、机油、还有不肯熄的炉。"),
                            DialogueLine("char_ur_xingtian", "跟紧。这里只认编号，不认名字——但我会叫你织环者。", "serious"),
                            DialogueLine("char_ssr_leishen", "功率再超，整城要停电。你们动静小点。", "normal"),
                            DialogueLine("narrator", "刑天胸口的光很稳。像有人把「护」字焊进了核里。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 600, "星尘 ×600"),
                            StoryReward("hard_currency", 60, "钻石 ×60"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch03_s2",
                        title = "齿猎犬",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch03_s1",
                        enemyIds = listOf("rift_gearhound", "rift_gearhound"),
                        recommendedLevel = 10,
                        rewards = listOf(
                            StoryReward("soft_currency", 900, "星尘 ×900"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch03_s3",
                        title = "兵主之问",
                        type = StoryStageType.CHOICE,
                        prerequisiteStageId = "ch03_s2",
                        dialogue = listOf(
                            DialogueLine("char_ssr_chiyou", "战场只要结果。盾太慢，矛才活命。", "angry"),
                            DialogueLine("char_ur_xingtian", "平民还在外面。矛再快，也护不住跪着的人。", "serious"),
                            DialogueLine(
                                "char_ur_xingtian",
                                "织环者——你说，我们是什么？",
                                "serious",
                                choices = listOf(
                                    StoryChoice(
                                        text = "盾比矛难，但盾更久。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ur_xingtian",
                                    ),
                                    StoryChoice(
                                        text = "没有矛，盾只是等死。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ssr_chiyou",
                                    ),
                                    StoryChoice(
                                        text = "你们都是人。别用编号称呼自己。",
                                        affinityBonus = 6,
                                        affinityCharacterId = "char_ur_xingtian",
                                    ),
                                ),
                            ),
                            DialogueLine("narrator", "远处，一台铁卫的独眼忽然亮成红色。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 700, "星尘 ×700"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch03_s4",
                        title = "铁卫失控",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch03_s3",
                        enemyIds = listOf("rift_ironwarden"),
                        recommendedLevel = 12,
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
                            StoryReward("hard_currency", 100, "钻石 ×100"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch03_s5",
                        title = "钢心共鸣",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch03_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "你按住铁卫残骸。回响顺着合金爬进兵主核心。"),
                            DialogueLine("char_ur_xingtian", "感觉到了吗？我们不是终点，也还没认输。", "normal"),
                            DialogueLine("char_ssr_chiyou", "哼。总有一天，战场会证明谁才是对的。", "tease"),
                            DialogueLine("narrator", "三界的烽火同时亮起。会盟的箭，已经射出。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 2000, "星尘 ×2000"),
                            StoryReward("hard_currency", 200, "钻石 ×200"),
                        ),
                    ),
                ),
            ),
            // ── 第二幕 · 章四：三界会盟 ──
            StoryChapterDef(
                chapterId = "ch04",
                title = "第四章：三界会盟",
                subtitle = "神可并肩，不必跪",
                world = "Shinwa",
                accentColor = 0xFFE34234,
                coverCharacterId = "char_ur_zhulong",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch04_s1",
                        title = "撕裂的会盟",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch03_s5",
                        dialogue = listOf(
                            DialogueLine("narrator", "三方旗帜挤在同一座焦土台上。风一吹，像要撕开。"),
                            DialogueLine("char_ur_zhulong", "联军需要统御。节律不能乱。", "serious"),
                            DialogueLine("char_ur_keqing", "统御可以，崇拜不行。你们神，该被研究。", "angry"),
                            DialogueLine("char_ur_xingtian", "吵完了就列阵。平民还在外面。", "normal"),
                            DialogueLine("narrator", "你站在三角中央——回响，是唯一能同时接住三道目光的东西。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 800, "星尘 ×800"),
                            StoryReward("hard_currency", 80, "钻石 ×80"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch04_s2",
                        title = "前线·隙兵",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch04_s1",
                        enemyIds = listOf("rift_riftsoldier", "rift_riftsoldier", "rift_riftsoldier", "rift_riftsoldier"),
                        recommendedLevel = 15,
                        rewards = listOf(
                            StoryReward("soft_currency", 1200, "星尘 ×1200"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch04_s3",
                        title = "联军主帅",
                        type = StoryStageType.CHOICE,
                        prerequisiteStageId = "ch04_s2",
                        dialogue = listOf(
                            DialogueLine(
                                "char_ur_xingtian",
                                "织环者——这一仗，你把指挥权交给谁？",
                                "serious",
                                choices = listOf(
                                    StoryChoice(
                                        text = "烛龙统筹，节律为令。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ur_zhulong",
                                    ),
                                    StoryChoice(
                                        text = "曜打机动，快过他们合围。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ur_keqing",
                                    ),
                                    StoryChoice(
                                        text = "刑天固守，平民优先。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ur_xingtian",
                                    ),
                                ),
                            ),
                            DialogueLine("narrator", "命令落下。主影在烟里抬起了头。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
                            StoryReward("hard_currency", 100, "钻石 ×100"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch04_s4",
                        title = "主影现身",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch04_s3",
                        enemyIds = listOf("rift_lordshade", "rift_lordshade"),
                        recommendedLevel = 18,
                        rewards = listOf(
                            StoryReward("soft_currency", 1500, "星尘 ×1500"),
                            StoryReward("hard_currency", 150, "钻石 ×150"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch04_s5",
                        title = "裂隙深处有星",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch04_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "主影溃散。裂隙另一头，却有不属于三界的轨迹坠落。"),
                            DialogueLine("char_ur_zhulong", "那不是我们的星。", "surprised"),
                            DialogueLine("char_ur_keqing", "管他是谁的星。先看清再决定怕不怕。", "normal"),
                            DialogueLine("narrator", "坠星拖着青铜色的尾焰，砸向铁帷废土。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 2000, "星尘 ×2000"),
                            StoryReward("hard_currency", 200, "钻石 ×200"),
                        ),
                    ),
                ),
            ),
            // ── 第二幕 · 章五：域外坠星 ──
            StoryChapterDef(
                chapterId = "ch05",
                title = "第五章：域外坠星",
                subtitle = "异界来客",
                world = "Ironveil",
                accentColor = 0xFFC96A2E,
                coverCharacterId = "char_ur_ironman",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch05_s1",
                        title = "坠星",
                        type = StoryStageType.DIALOGUE,
                        dialogue = listOf(
                            DialogueLine("narrator", "残骸坑里爬出三道人影。机巧、雷楔、与不合时宜的星图。"),
                            DialogueLine("char_ur_ironman", "机巧偃师·公输玄。反应堆……这里叫心核对吧？参数我记下了。", "normal"),
                            DialogueLine("char_ur_thor", "破晓雷神·苍霆。谁管这雷怎么走——它听我的。", "happy"),
                            DialogueLine("char_ur_strange", "观星秘术·玄微。裂隙比卷轴上写的，更不讲理。", "serious"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
                            StoryReward("hard_currency", 100, "钻石 ×100"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch05_s2",
                        title = "残骸清场",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch05_s1",
                        enemyIds = listOf("rift_elitehusk", "rift_elitehusk"),
                        recommendedLevel = 20,
                        rewards = listOf(
                            StoryReward("soft_currency", 1800, "星尘 ×1800"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch05_s3",
                        title = "三把钥匙",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch05_s2",
                        dialogue = listOf(
                            DialogueLine("char_ur_ironman", "心核能锁住裂隙边缘——如果你们肯把图纸共享。", "serious"),
                            DialogueLine("char_ur_thor", "共享可以。先打一架，看谁配拿锤。", "tease"),
                            DialogueLine("char_ur_strange", "别吵。门已经开了。送回去，只会撕得更大。", "normal"),
                            DialogueLine("narrator", "域外是阵营，不是第四界。他们留下，裂隙也留下了。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1500, "星尘 ×1500"),
                            StoryReward("hard_currency", 150, "钻石 ×150"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch05_s4",
                        title = "留还是送",
                        type = StoryStageType.CHOICE,
                        prerequisiteStageId = "ch05_s3",
                        dialogue = listOf(
                            DialogueLine(
                                "char_ur_strange",
                                "织环者——送我们走，还是一起把门焊死？",
                                "serious",
                                choices = listOf(
                                    StoryChoice(
                                        text = "留下。一起把门焊死。",
                                        affinityBonus = 10,
                                        affinityCharacterId = "char_ur_ironman",
                                    ),
                                    StoryChoice(
                                        text = "先试一次回程。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ur_strange",
                                    ),
                                    StoryChoice(
                                        text = "谁的锤硬，谁说话。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ur_thor",
                                    ),
                                ),
                            ),
                            DialogueLine("narrator", "无论选哪条，天上的裂都比来时更宽了一寸。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1500, "星尘 ×1500"),
                            StoryReward("hard_currency", 150, "钻石 ×150"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch05_s5",
                        title = "裂得更宽",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch05_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "强行回程的实验失败。裂隙像被撬开的旧伤口。"),
                            DialogueLine("char_ur_ironman", "结论：留下。我讨厌这个结论，但数据不撒谎。", "sad"),
                            DialogueLine("char_ur_zhulong", "那就结盟。织环者，把他们也编进你的环。", "serious"),
                            DialogueLine("narrator", "边境传来毒与焰的气味。两害，出闸了。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 3000, "星尘 ×3000"),
                            StoryReward("hard_currency", 300, "钻石 ×300"),
                        ),
                    ),
                ),
            ),
            // ── 第二幕 · 章六：毒焰同盟（Shinwa 边境）──
            StoryChapterDef(
                chapterId = "ch06",
                title = "第六章：毒焰同盟",
                subtitle = "两害出闸",
                world = "Shinwa",
                accentColor = 0xFF1FB6A6,
                coverCharacterId = "char_ur_kikyo",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch06_s1",
                        title = "两害出闸",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch05_s5",
                        dialogue = listOf(
                            DialogueLine("narrator", "青铜鼎的封条烧穿了。毒泽与贪焰，同时舔上神話边境。"),
                            DialogueLine("char_ssr_xiangliu", "瘟疫是艺术。你们管这叫灾难？真没品味。", "tease"),
                            DialogueLine("char_sr_taotie", "饿了。你们都别跑。", "angry"),
                            DialogueLine("narrator", "被迫开鼎的人跪了一地。青璃的箭，却先一步钉进鼎耳。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1200, "星尘 ×1200"),
                            StoryReward("hard_currency", 120, "钻石 ×120"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch06_s2",
                        title = "毒华",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch06_s1",
                        enemyIds = listOf("rift_poisonbloom", "rift_poisonbloom", "rift_poisonbloom"),
                        recommendedLevel = 16,
                        rewards = listOf(
                            StoryReward("soft_currency", 1400, "星尘 ×1400"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch06_s3",
                        title = "陶土巫女",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch06_s2",
                        dialogue = listOf(
                            DialogueLine("char_ur_kikyo", "亡魂我来度。活着的，你们自己看好。", "serious"),
                            DialogueLine("narrator", "她掌心的陶土裂开一道缝——像女娲补过的天，也像她自己。"),
                            DialogueLine("char_ur_kikyo", "泥土记得所有生命最初的形状。我也是被捏回来的。", "sad"),
                            DialogueLine("narrator", "鼎还在。饕餮却已经把封条当零食嚼了。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1400, "星尘 ×1400"),
                            StoryReward("hard_currency", 140, "钻石 ×140"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch06_s4",
                        title = "喂饱还是封回",
                        type = StoryStageType.CHOICE,
                        prerequisiteStageId = "ch06_s3",
                        dialogue = listOf(
                            DialogueLine(
                                "char_ur_kikyo",
                                "织环者——鼎口还开着。你选哪边？",
                                "serious",
                                choices = listOf(
                                    StoryChoice(
                                        text = "饿了就吃。吃完跟我走。",
                                        affinityBonus = 10,
                                        affinityCharacterId = "char_sr_taotie",
                                    ),
                                    StoryChoice(
                                        text = "鼎还在。你选哪边？",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ur_kikyo",
                                    ),
                                    StoryChoice(
                                        text = "毒与焰可以是药，也可以是刀。",
                                        affinityBonus = 5,
                                        affinityCharacterId = "char_ssr_xiangliu",
                                    ),
                                ),
                            ),
                            DialogueLine("narrator", "贪焰忽然拔高——不是回应，是失控。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1400, "星尘 ×1400"),
                            StoryReward("hard_currency", 140, "钻石 ×140"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch06_s5",
                        title = "焰饕失控",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch06_s4",
                        enemyIds = listOf("rift_flameglutton", "rift_poisonbloom"),
                        recommendedLevel = 18,
                        rewards = listOf(
                            StoryReward("soft_currency", 2200, "星尘 ×2200"),
                            StoryReward("hard_currency", 220, "钻石 ×220"),
                        ),
                    ),
                ),
            ),
            // ── 第三幕 · 章七：洪水与石（Aether 浮岛）──
            StoryChapterDef(
                chapterId = "ch07",
                title = "第七章：洪水与石",
                subtitle = "看不见也要补",
                world = "Aether",
                accentColor = 0xFFC79BFF,
                coverCharacterId = "char_ur_nuwa",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch07_s1",
                        title = "最后一块石",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch06_s5",
                        dialogue = listOf(
                            DialogueLine("narrator", "浮岛在下沉。补天石只剩掌心那么大一块。"),
                            DialogueLine("char_ur_nuwa", "天还漏着。海也还脏着。石头只有一块。", "sad"),
                            DialogueLine("char_sr_jingwei", "我填了那么久……海还是黑的。但我不会停。", "serious"),
                            DialogueLine("char_sr_xuanwu", "岛背上的庙还在。人也还在。这就够我再扛一夜。", "normal"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1600, "星尘 ×1600"),
                            StoryReward("hard_currency", 160, "钻石 ×160"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch07_s2",
                        title = "浮岛守卫",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch07_s1",
                        enemyIds = listOf("rift_elitehusk", "rift_elitehusk", "rift_elitehusk"),
                        recommendedLevel = 20,
                        rewards = listOf(
                            StoryReward("soft_currency", 1800, "星尘 ×1800"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch07_s3",
                        title = "石给谁",
                        type = StoryStageType.CHOICE,
                        prerequisiteStageId = "ch07_s2",
                        dialogue = listOf(
                            DialogueLine(
                                "char_ur_nuwa",
                                "织环者——这块石，你交给谁？",
                                "serious",
                                choices = listOf(
                                    StoryChoice(
                                        text = "留给补天。天先不塌。",
                                        affinityBonus = 10,
                                        affinityCharacterId = "char_ur_nuwa",
                                    ),
                                    StoryChoice(
                                        text = "给精卫。海也要有人填。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_sr_jingwei",
                                    ),
                                    StoryChoice(
                                        text = "拆开研究裂隙的缝。",
                                        affinityBonus = 8,
                                        affinityCharacterId = "char_ssr_shangyang",
                                    ),
                                ),
                            ),
                            DialogueLine("narrator", "海面忽然隆起。沉岛之潮，正从下方顶上来。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 1600, "星尘 ×1600"),
                            StoryReward("hard_currency", 160, "钻石 ×160"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch07_s4",
                        title = "沉岛之潮",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch07_s3",
                        enemyIds = listOf("rift_stormheart", "rift_voidmite", "rift_voidmite"),
                        recommendedLevel = 22,
                        rewards = listOf(
                            StoryReward("soft_currency", 2000, "星尘 ×2000"),
                            StoryReward("hard_currency", 200, "钻石 ×200"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch07_s5",
                        title = "修补者的账",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch07_s4",
                        dialogue = listOf(
                            DialogueLine("char_ssr_shangyang", "结局仍看不见。我看见的，只有裂缝在变宽。", "sad"),
                            DialogueLine("char_ur_nuwa", "看不见也要补。这是我唯一会的事。", "serious"),
                            DialogueLine("char_sr_jingwei", "一粒一粒。总有一天会填满。", "happy"),
                            DialogueLine("narrator", "远方，烛龙的眼皮沉了一寸。无昼之夜，近了。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 2400, "星尘 ×2400"),
                            StoryReward("hard_currency", 240, "钻石 ×240"),
                        ),
                    ),
                ),
            ),
            // ── 第三幕 · 章八：无昼之夜（裂隙深处）──
            StoryChapterDef(
                chapterId = "ch08",
                title = "第八章：无昼之夜",
                subtitle = "还债者",
                world = "Aether",
                accentColor = 0xFF8B0000,
                coverCharacterId = "char_ur_wuxu",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch08_s1",
                        title = "闭眼",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch07_s5",
                        dialogue = listOf(
                            DialogueLine("narrator", "天空裂成两半。一半是烛龙将闭的眼，一半是虚无张开的口。"),
                            DialogueLine("char_ur_zhulong", "再睁一日，河就干了。闭上，夜就永驻。", "sad"),
                            DialogueLine("char_ur_jinwu", "那就别闭。我烧到只剩一盏，也够照亮神話。", "angry"),
                            DialogueLine("narrator", "你站在裂口正中。回响烫得像要从胸口跳出来。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 2000, "星尘 ×2000"),
                            StoryReward("hard_currency", 200, "钻石 ×200"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch08_s2",
                        title = "湮灭前哨",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch08_s1",
                        enemyIds = listOf("rift_elitehusk", "rift_elitehusk", "rift_elitehusk"),
                        recommendedLevel = 23,
                        rewards = listOf(
                            StoryReward("soft_currency", 2200, "星尘 ×2200"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch08_s3",
                        title = "还债者",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch08_s2",
                        dialogue = listOf(
                            DialogueLine("narrator", "没有风。声音却从每一粒尘埃里长出来。"),
                            DialogueLine("char_ur_wuxu", "我不是邪恶。我是宇宙在还债——一切存在，终将归于无。", "normal"),
                            DialogueLine("char_ur_wuxu", "你们把债期叫作生命。我只是来收账的。", "serious"),
                            DialogueLine("char_ur_nuwa", "那就再借一次。用泥，用石，用我。", "angry"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 2000, "星尘 ×2000"),
                            StoryReward("hard_currency", 200, "钻石 ×200"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch08_s4",
                        title = "湮灭核",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch08_s3",
                        enemyIds = listOf("rift_annihilation"),
                        recommendedLevel = 25,
                        rewards = listOf(
                            StoryReward("soft_currency", 2800, "星尘 ×2800"),
                            StoryReward("hard_currency", 280, "钻石 ×280"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch08_s5",
                        title = "三道光",
                        type = StoryStageType.CHOICE,
                        prerequisiteStageId = "ch08_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "湮灭核裂开一道缝。三道光同时探进来——你的选择，将被写进环的账。"),
                            DialogueLine(
                                "char_ur_zhulong",
                                "织环者——把光交给谁？",
                                "serious",
                                choices = listOf(
                                    StoryChoice(
                                        text = "永昼硬撑。节律不能断。",
                                        affinityBonus = 10,
                                        affinityCharacterId = "char_ur_zhulong",
                                        endingBranchId = "end_dawn",
                                    ),
                                    StoryChoice(
                                        text = "谈判。有价的存在也是存在。",
                                        affinityBonus = 10,
                                        affinityCharacterId = "char_ur_wuxu",
                                        endingBranchId = "end_debt",
                                    ),
                                    StoryChoice(
                                        text = "金乌，燃尽第二日。",
                                        affinityBonus = 10,
                                        affinityCharacterId = "char_ur_jinwu",
                                        endingBranchId = "end_burn",
                                    ),
                                ),
                            ),
                            DialogueLine("narrator", "夜被撕开一条金线。环的遗址，在更深处亮了。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 3000, "星尘 ×3000"),
                            StoryReward("hard_currency", 300, "钻石 ×300"),
                        ),
                    ),
                ),
            ),
            // ── 第三幕 · 章九：重织之环（原初之环遗址）──
            StoryChapterDef(
                chapterId = "ch09",
                title = "第九章：重织之环",
                subtitle = "新的开始",
                world = "Shinwa",
                accentColor = 0xFFF0C864,
                coverCharacterId = "char_ur_zhulong",
                stages = listOf(
                    StoryStageDef(
                        stageId = "ch09_s1",
                        title = "遗址",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch08_s5",
                        dialogue = listOf(
                            DialogueLine("narrator", "这里没有天，也没有地。只有环的残骸，像被打碎又不肯散的瓷。"),
                            DialogueLine("char_ur_ironman", "结构完整度 11%。理论上，可以织。理论上。", "serious"),
                            DialogueLine("char_ur_strange", "别看参数了。看人——他们全都来了。", "normal"),
                            DialogueLine("narrator", "三界英灵应召而至。你的回响，第一次不再发烫，而是发稳。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 2500, "星尘 ×2500"),
                            StoryReward("hard_currency", 250, "钻石 ×250"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch09_s2",
                        title = "最后守卫",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch09_s1",
                        enemyIds = listOf("rift_annihilation", "rift_lordshade"),
                        recommendedLevel = 25,
                        rewards = listOf(
                            StoryReward("soft_currency", 2800, "星尘 ×2800"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch09_s3",
                        title = "每人一句",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch09_s2",
                        dialogue = listOf(
                            DialogueLine("char_ur_zhulong", "昼会回来。节律不会死。", "normal"),
                            DialogueLine("char_ur_jinwu", "记住这温度，它叫黎明。", "happy"),
                            DialogueLine("char_ur_wuxu", "……你们只是把债期延后。", "tease"),
                            DialogueLine("char_ur_nuwa", "还缺一块。我来想办法。", "serious"),
                            DialogueLine("char_ur_xingtian", "平民安全了。这就够。", "normal"),
                            DialogueLine("char_ur_keqing", "神可以并肩，不必跪。", "serious"),
                            DialogueLine("char_ur_ironman", "环的参数，我记下了。", "happy"),
                            DialogueLine("char_ur_kikyo", "亡魂已度。路还长。", "sad"),
                            DialogueLine("narrator", "该你了，织环者。不用台词——动手就行。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 2500, "星尘 ×2500"),
                            StoryReward("hard_currency", 250, "钻石 ×250"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch09_s4",
                        title = "织环",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "ch09_s3",
                        enemyIds = listOf("rift_annihilation", "rift_annihilation"),
                        recommendedLevel = 28,
                        rewards = listOf(
                            StoryReward("soft_currency", 3500, "星尘 ×3500"),
                            StoryReward("hard_currency", 350, "钻石 ×350"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "ch09_s5",
                        title = "新的开始",
                        type = StoryStageType.DIALOGUE,
                        prerequisiteStageId = "ch09_s4",
                        dialogue = listOf(
                            DialogueLine("narrator", "环被重新拼上。金线沿着裂缝走，像给旧瓷器描边。"),
                            DialogueLine("char_ur_zhulong", "不完美。但足够再撑一个纪元。", "happy"),
                            DialogueLine("char_ur_wuxu", "债还在。只是账期，又长了一点。", "normal"),
                            DialogueLine("char_ur_nuwa", "去收集更多英灵吧。裂隙还在扩张。", "serious"),
                            DialogueLine("narrator", "【三幕九章 · 第一纪元完】织环者，新的故事等你继续。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 5000, "星尘 ×5000"),
                            StoryReward("hard_currency", 500, "钻石 ×500"),
                        ),
                    ),
                ),
            ),
            // ── 好感外传（P3）：不占主线序号，解锁走好感门槛 ──
            StoryChapterDef(
                chapterId = "side_zhulong",
                title = "外传：七日之重",
                subtitle = "烛龙 · 好感 Lv.3",
                world = "Shinwa",
                accentColor = 0xFFE34234,
                coverCharacterId = "char_ur_zhulong",
                requiredAffinityCharacterId = "char_ur_zhulong",
                requiredAffinityLevel = 3,
                stages = listOf(
                    StoryStageDef(
                        stageId = "side_zhulong_s1",
                        title = "河床夜话",
                        type = StoryStageType.DIALOGUE,
                        dialogue = listOf(
                            DialogueLine("narrator", "永昼的第七夜。河床裸露，像大地裂开的掌纹。"),
                            DialogueLine("char_ur_zhulong", "有人朝我扔石头。我不怪他们。", "sad"),
                            DialogueLine("char_ur_zhulong", "我只是……想知道，若再选一次，还会不会睁那七日。", "normal"),
                            DialogueLine("narrator", "你没有回答。风把焦土的气味吹过来，像一声很轻的叹息。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 800, "星尘 ×800"),
                            StoryReward("hard_currency", 40, "钻石 ×40"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "side_zhulong_s2",
                        title = "焦土余烬",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "side_zhulong_s1",
                        enemyIds = listOf("rift_mistling", "rift_shadowwhisper"),
                        recommendedLevel = 12,
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
                        ),
                    ),
                ),
            ),
            StoryChapterDef(
                chapterId = "side_jinwu",
                title = "外传：最后一盏",
                subtitle = "金乌 · 好感 Lv.3",
                world = "Shinwa",
                accentColor = 0xFFF0C864,
                coverCharacterId = "char_ur_jinwu",
                requiredAffinityCharacterId = "char_ur_jinwu",
                requiredAffinityLevel = 3,
                stages = listOf(
                    StoryStageDef(
                        stageId = "side_jinwu_s1",
                        title = "十日残影",
                        type = StoryStageType.DIALOGUE,
                        dialogue = listOf(
                            DialogueLine("narrator", "云层裂开，九道残影同时坠向地平线——那是被虚无吞掉的兄弟。"),
                            DialogueLine("char_ur_jinwu", "我不哭。一哭，火就弱了。", "serious"),
                            DialogueLine("char_ur_jinwu", "但你可以陪我站一会儿。就一会儿。", "sad"),
                            DialogueLine("narrator", "羽尖的温度降了一度。又立刻升了回来。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 800, "星尘 ×800"),
                            StoryReward("hard_currency", 40, "钻石 ×40"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "side_jinwu_s2",
                        title = "日轮不沉",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "side_jinwu_s1",
                        enemyIds = listOf("rift_elitehusk", "rift_shadowwhisper"),
                        recommendedLevel = 14,
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
                        ),
                    ),
                ),
            ),
            StoryChapterDef(
                chapterId = "side_xingtian",
                title = "外传：无首之护",
                subtitle = "刑天 · 好感 Lv.3",
                world = "Ironveil",
                accentColor = 0xFF4A90D9,
                coverCharacterId = "char_ur_xingtian",
                requiredAffinityCharacterId = "char_ur_xingtian",
                requiredAffinityLevel = 3,
                stages = listOf(
                    StoryStageDef(
                        stageId = "side_xingtian_s1",
                        title = "乳目所见",
                        type = StoryStageType.DIALOGUE,
                        dialogue = listOf(
                            DialogueLine("narrator", "贫民窟的灯一盏盏灭。刑天站在巷口，胸口的光替他们守夜。"),
                            DialogueLine("char_ur_xingtian", "我没有头可点。所以用这里。", "normal"),
                            DialogueLine("char_ur_xingtian", "护民不需要脸。需要站在前面。", "serious"),
                            DialogueLine("narrator", "远处铁卫的脚步声停下——似乎也在听。"),
                        ),
                        rewards = listOf(
                            StoryReward("soft_currency", 800, "星尘 ×800"),
                            StoryReward("hard_currency", 40, "钻石 ×40"),
                        ),
                    ),
                    StoryStageDef(
                        stageId = "side_xingtian_s2",
                        title = "巷口之盾",
                        type = StoryStageType.BATTLE,
                        prerequisiteStageId = "side_xingtian_s1",
                        enemyIds = listOf("rift_gearhound", "rift_gearhound"),
                        recommendedLevel = 12,
                        rewards = listOf(
                            StoryReward("soft_currency", 1000, "星尘 ×1000"),
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
