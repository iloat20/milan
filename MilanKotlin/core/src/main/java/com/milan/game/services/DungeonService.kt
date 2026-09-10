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

    // ─────────────────────────── 日常副本 ───────────────────────────

    fun getDailyDungeonData(): DailyDungeonSaveData =
        saveData.dailyDungeonData ?: DailyDungeonSaveData()

    fun getRemainingChallenges(type: DailyDungeonType): Int {
        val data = getDailyDungeonData()
        val used = data.challengeCounts[type.name] ?: 0
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
        val remaining = getRemainingChallenges(type)
        val actualTimes = times.coerceAtMost(remaining)
        if (actualTimes <= 0) return DungeonSweepOutcome.Rejected

        val data = getDailyDungeonData()
        val origCounts = data.challengeCounts.toMap()
        val origTotal = data.totalChallenges
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
        return AbyssStatus(
            currentFloor = data.currentFloor,
            bestFloor = data.bestFloor,
            totalStars = data.totalStars,
            challengeCount = data.challengeCount,
            maxChallenges = AbyssSaveData.DAILY_FREE_CHALLENGES,
            remainingChallenges = (AbyssSaveData.DAILY_FREE_CHALLENGES - data.challengeCount).coerceAtLeast(0),
        )
    }

    override suspend fun challengeAbyss(floor: Int): AbyssChallengeOutcome {
        val data = getAbyssData()
        if (floor != data.currentFloor) return AbyssChallengeOutcome.Rejected
        if (data.challengeCount >= AbyssSaveData.DAILY_FREE_CHALLENGES) return AbyssChallengeOutcome.Rejected
        if (floor > AbyssSaveData.MAX_FLOORS) return AbyssChallengeOutcome.Rejected

        val origChallengeCount = data.challengeCount
        return core.withWriteLock {
            core.transactionLocked(
                tag = "abyss.challenge",
                mutate = { data.challengeCount += 1 },
                rollback = { data.challengeCount = origChallengeCount },
                onCommit = { core.refreshSnapshot() },
            )
            AbyssChallengeOutcome.Success(floor)
        }
    }

    override suspend fun completeAbyssStage(floor: Int, stars: Int): WriteOutcome {
        val data = getAbyssData()
        val clampedStars = stars.coerceIn(0, AbyssSaveData.MAX_STARS_PER_FLOOR)

        val origStars = data.stars.toList()
        val origTotal = data.totalStars
        val origBest = data.bestFloor
        val origCurrent = data.currentFloor
        val origSC = saveData.softCurrency
        val origHC = saveData.hardCurrency

        val softReward = floor * 500 * clampedStars
        val hardReward = if (floor % 5 == 0 && clampedStars >= 2) floor * 10 else 0

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
