package com.milan.game.services

import com.milan.game.data.*
import kotlin.random.Random

/**
 * 深渊挑战 + 日常副本服务。
 *
 * 职责：
 * - 日常副本挑战（经验/金币/材料/装备/碎片，每日限次）
 * - 深渊挑战（高难度多层PVE，每期重置）
 * - 扫荡功能（已通关关卡一键扫荡）
 * - 星星评级与奖励发放
 *
 * 注意：经验奖励不在此发放（跨服务 Mutex 不可重入），由 GameService 门面编排
 * 挑战成功后统一调用 ProgressionService.grantExp。
 */
@Suppress("DEPRECATION")
class DungeonService(
    private val core: ServiceCore,
    private val rng: Random,
) : DungeonApi {

    private val saveData get() = core.saveData

    /**
     * R7-P1：深渊/日常副本每日次数跨日重置。
     * `lastResetTime` 字段一直存在但从未写入——用完 3 次后永久 Rejected。
     * 语义：存「今日日序号」（core.today()），与 DailyMission 同口径。
     */
    suspend fun ensureDailyReset(): WriteOutcome {
        val today = core.today()
        val abyss = saveData.abyssData
        val dungeon = saveData.dailyDungeonData
        val abyssDue = abyss != null && abyss.lastResetTime != today
        val dungeonDue = dungeon != null && dungeon.lastResetTime != today
        if (!abyssDue && !dungeonDue) return WriteOutcome.Success

        val origAbyssCount = abyss?.challengeCount
        val origAbyssReset = abyss?.lastResetTime
        val origDungeonCounts = dungeon?.challengeCounts
        val origDungeonReset = dungeon?.lastResetTime

        return core.withWriteLock {
            core.transactionLocked(
                tag = "dungeon.ensureDailyReset",
                mutate = {
                    saveData.abyssData?.let { d ->
                        if (d.lastResetTime != today) {
                            d.challengeCount = 0
                            d.lastResetTime = today
                        }
                    }
                    saveData.dailyDungeonData?.let { d ->
                        if (d.lastResetTime != today) {
                            d.challengeCounts = emptyMap()
                            d.lastResetTime = today
                        }
                    }
                },
                rollback = {
                    saveData.abyssData?.let { d ->
                        origAbyssCount?.let { d.challengeCount = it }
                        origAbyssReset?.let { d.lastResetTime = it }
                    }
                    saveData.dailyDungeonData?.let { d ->
                        origDungeonCounts?.let { d.challengeCounts = it }
                        origDungeonReset?.let { d.lastResetTime = it }
                    }
                },
                onCommit = { core.refreshSnapshot() },
            )
        }
    }

    /** 读路径：是否因跨日应视为已重置（不落盘，供状态展示）。 */
    private fun isDailyResetDue(lastResetTime: Long): Boolean =
        lastResetTime != core.today()

    // ─────────────────────────── 日常副本 ───────────────────────────

    fun getDailyDungeonData(): DailyDungeonSaveData =
        saveData.dailyDungeonData ?: DailyDungeonSaveData()

    fun getRemainingChallenges(type: DailyDungeonType): Int {
        val data = getDailyDungeonData()
        val used = if (isDailyResetDue(data.lastResetTime)) 0 else (data.challengeCounts[type.name] ?: 0)
        return (DailyDungeonSaveData.MAX_CHALLENGES_PER_TYPE - used).coerceAtLeast(0)
    }

    override fun getDailyDungeonStatuses(): List<DailyDungeonStatus> =
        DailyDungeonType.entries.map { type ->
            DailyDungeonStatus(type, getRemainingChallenges(type), DailyDungeonSaveData.MAX_CHALLENGES_PER_TYPE)
        }

    /**
     * 扫荡日常副本（已通关内容一键完成）。
     * 仅发放星尘/钻石/道具奖励，经验由调用方编排。
     */
    override suspend fun sweepDungeon(type: DailyDungeonType, times: Int): DungeonSweepOutcome {
        if (times <= 0) return DungeonSweepOutcome.Rejected
        // R7-P1：先跨日重置，再取剩余次数（否则用完永久锁死）
        ensureDailyReset()
        val remaining = getRemainingChallenges(type)
        val actualTimes = times.coerceAtMost(remaining)
        if (actualTimes <= 0) return DungeonSweepOutcome.Rejected

        val data = getDailyDungeonData()
        val origCounts = data.challengeCounts.toMap()
        val origTotal = data.totalChallenges
        val origReset = data.lastResetTime
        val origSC = saveData.softCurrency
        val origHC = saveData.hardCurrency

        val level = (data.totalChallenges / 10 + 1).coerceAtMost(5)
        val reward = calculateDungeonReward(type, level)
        val totalSoft = reward.softReward * actualTimes
        val totalHard = reward.hardReward * actualTimes

        return core.withWriteLock {
            core.transactionLocked(
                tag = "dungeon.sweep",
                mutate = {
                    data.challengeCounts = data.challengeCounts +
                        (type.name to ((data.challengeCounts[type.name] ?: 0) + actualTimes))
                    data.totalChallenges += actualTimes
                    if (totalSoft > 0) core.addCurrencyDelta(totalSoft, 0)
                    if (totalHard > 0) core.addCurrencyDelta(0, totalHard)
                },
                rollback = {
                    data.challengeCounts = origCounts
                    data.totalChallenges = origTotal
                    data.lastResetTime = origReset
                    saveData.softCurrency = origSC
                    saveData.hardCurrency = origHC
                },
                onCommit = { core.publishCurrencyChanged() },
            )
            DungeonSweepOutcome.Success(type, actualTimes, totalSoft, totalHard)
        }
    }

    private fun calculateDungeonReward(type: DailyDungeonType, level: Int): DungeonRewardResult {
        val baseSoft = when (type) {
            DailyDungeonType.GOLD_DUNGEON -> 2000
            DailyDungeonType.EXP_DUNGEON -> 500
            DailyDungeonType.MATERIAL_DUNGEON -> 500
            DailyDungeonType.EQUIPMENT_DUNGEON -> 300
            DailyDungeonType.FRAGMENT_DUNGEON -> 200
        }
        val multiplier = 1.0 + (level - 1) * 0.3
        return DungeonRewardResult(
            softReward = (baseSoft * multiplier).toInt(),
            hardReward = if (level >= 4 && type == DailyDungeonType.EQUIPMENT_DUNGEON) 10 else 0,
        )
    }

    // ─────────────────────────── 深渊挑战 ───────────────────────────

    fun getAbyssData(): AbyssSaveData = saveData.abyssData ?: AbyssSaveData()

    override fun getAbyssStatus(): AbyssStatus {
        val data = getAbyssData()
        // R7-P1：跨日视为次数已重置（读路径不落盘）
        val usedToday =
            if (isDailyResetDue(data.lastResetTime)) 0 else data.challengeCount
        return AbyssStatus(
            currentFloor = data.currentFloor,
            bestFloor = data.bestFloor,
            totalStars = data.totalStars,
            challengeCount = usedToday,
            maxChallenges = AbyssSaveData.DAILY_FREE_CHALLENGES,
            remainingChallenges = (AbyssSaveData.DAILY_FREE_CHALLENGES - usedToday).coerceAtLeast(0),
        )
    }

    override suspend fun challengeAbyss(floor: Int): AbyssChallengeOutcome {
        ensureDailyReset()
        val data = getAbyssData()
        if (floor != data.currentFloor) return AbyssChallengeOutcome.Rejected
        if (data.challengeCount >= AbyssSaveData.DAILY_FREE_CHALLENGES) return AbyssChallengeOutcome.Rejected
        if (floor > AbyssSaveData.MAX_FLOORS) return AbyssChallengeOutcome.Rejected

        val origChallengeCount = data.challengeCount
        val origReset = data.lastResetTime
        return core.withWriteLock {
            core.transactionLocked(
                tag = "abyss.challenge",
                mutate = {
                    data.challengeCount += 1
                    // 今日首次挑战写入日戳（ensure 可能已写；此处幂等兜底）
                    data.lastResetTime = core.today()
                },
                rollback = {
                    data.challengeCount = origChallengeCount
                    data.lastResetTime = origReset
                },
                onCommit = { core.refreshSnapshot() },
            )
            AbyssChallengeOutcome.Success(floor)
        }
    }

    override suspend fun completeAbyssStage(floor: Int, stars: Int): WriteOutcome {
        val data = getAbyssData()
        if (floor < 1 || floor > AbyssSaveData.MAX_FLOORS) return WriteOutcome.Rejected
        val clampedStars = stars.coerceIn(0, AbyssSaveData.MAX_STARS_PER_FLOOR)

        val origStars = data.stars.toList()
        val origTotal = data.totalStars
        val origBest = data.bestFloor
        val origCurrent = data.currentFloor
        val origSC = saveData.softCurrency
        val origHC = saveData.hardCurrency

        // R7-P0-3：仅「本层首次通关」或「星数刷新纪录」才发奖。
        // 旧逻辑每次都发 floor*500*stars，可反复 completeAbyssStage(1,3) 刷星尘。
        val prevStars = data.stars.getOrNull(floor - 1) ?: 0
        val isNewClear = prevStars <= 0 && clampedStars >= 1
        val isNewBestStars = clampedStars > prevStars
        val shouldReward = isNewClear || isNewBestStars
        val softReward = if (shouldReward) floor * 500 * clampedStars else 0
        val hardReward =
            if (shouldReward && floor % 5 == 0 && clampedStars >= 2) floor * 10 else 0

        return core.transaction(
            tag = "abyss.complete",
            mutate = {
                val starsList = data.stars.toMutableList()
                while (starsList.size < floor) starsList.add(0)
                if (clampedStars > (starsList[floor - 1] ?: 0)) {
                    starsList[floor - 1] = clampedStars
                }
                data.stars = starsList
                data.totalStars = starsList.filterNotNull().sum()

                if (clampedStars >= 1 && data.currentFloor == floor) {
                    data.currentFloor = (floor + 1).coerceAtMost(AbyssSaveData.MAX_FLOORS)
                }
                if (data.currentFloor > data.bestFloor) {
                    data.bestFloor = data.currentFloor
                }
                if (softReward > 0) core.addCurrencyDelta(softReward, 0)
                if (hardReward > 0) core.addCurrencyDelta(0, hardReward)
            },
            rollback = {
                data.stars = origStars
                data.totalStars = origTotal
                data.bestFloor = origBest
                data.currentFloor = origCurrent
                saveData.softCurrency = origSC
                saveData.hardCurrency = origHC
            },
            onCommit = { core.publishCurrencyChanged() },
        )
    }
}

// ── 数据模型 ──

data class DailyDungeonStatus(
    val type: DailyDungeonType,
    val remaining: Int,
    val maxChallenges: Int,
)

sealed interface DungeonSweepOutcome {
    data class Success(val type: DailyDungeonType, val times: Int, val totalSoft: Int, val totalHard: Int) : DungeonSweepOutcome
    data object Rejected : DungeonSweepOutcome
}

private data class DungeonRewardResult(val softReward: Int, val hardReward: Int)

data class AbyssStatus(
    val currentFloor: Int, val bestFloor: Int, val totalStars: Int,
    val challengeCount: Int, val maxChallenges: Int, val remainingChallenges: Int,
)

sealed interface AbyssChallengeOutcome {
    data class Success(val floor: Int) : AbyssChallengeOutcome
    data object Rejected : AbyssChallengeOutcome
}
