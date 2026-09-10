package com.milan.game.services

import com.milan.game.data.*
import kotlin.random.Random

/**
 * 赛季系统服务。
 *
 * 职责：
 * - 竞技场赛季管理（14天周期）
 * - 赛季胜利/失败记录
 * - 赛季奖励发放（积分里程碑）
 * - 赛季重置
 */
class SeasonService(
    private val core: ServiceCore,
) : SeasonApi {

    /** 获取赛季数据（纯读）。 */
    fun getData(): SeasonSaveData {
        return core.saveData.seasonData ?: SeasonSaveData()
    }

    /**
     * 确保当前赛季有效（跨日/跨周期重置）。
     *
     * 幂等：当前赛季数据有效则直接返回。
     */
    suspend fun ensureCurrentSeason(): WriteOutcome {
        val data = getData()
        val today = core.today()
        val seasonStartDay = data.seasonStartDay
        val seasonDays = data.seasonDurationDays

        if (seasonStartDay == 0L || today >= seasonStartDay + seasonDays) {
            // 赛季已过期或不存在，开启新赛季
            return startNewSeason(data, today)
        }

        return WriteOutcome.Success
    }

    /**
     * 开启新赛季。
     */
    private suspend fun startNewSeason(data: SeasonSaveData, today: Long): WriteOutcome {
        val origNumber = data.seasonNumber
        val origStartDay = data.seasonStartDay
        val origWins = data.seasonWins
        val origLosses = data.seasonLosses
        val origMaxStreak = data.maxWinStreak
        val origCurrentStreak = data.currentWinStreak
        val origPoints = data.seasonPoints
        val origClaimed = data.claimedSeasonRewards.toList()

        return core.transaction(
            tag = "season.startNew",
            mutate = {
                // 检查旧赛季是否还有未领取的奖励
                val oldSeason = data.seasonNumber
                val milestones = SeasonSaveData.SEASON_REWARD_MILESTONES
                for (i in milestones.indices) {
                    if (data.seasonPoints >= milestones[i] && !data.claimedSeasonRewards.contains(oldSeason * 100 + i)) {
                        // 发放旧赛季奖励
                        core.addCurrencyDelta(
                            SeasonSaveData.SEASON_SOFT_REWARDS.getOrElse(i) { 5000 },
                            SeasonSaveData.SEASON_HARD_REWARDS.getOrElse(i) { 50 }
                        )
                        data.claimedSeasonRewards = data.claimedSeasonRewards + (oldSeason * 100 + i)
                    }
                }

                // 开启新赛季
                data.seasonNumber += 1
                data.seasonStartDay = today
                data.seasonWins = 0
                data.seasonLosses = 0
                data.maxWinStreak = 0
                data.currentWinStreak = 0
                data.seasonPoints = 0
            },
            rollback = {
                data.seasonNumber = origNumber
                data.seasonStartDay = origStartDay
                data.seasonWins = origWins
                data.seasonLosses = origLosses
                data.maxWinStreak = origMaxStreak
                data.currentWinStreak = origCurrentStreak
                data.seasonPoints = origPoints
                data.claimedSeasonRewards = origClaimed
            },
            onCommit = { core.refreshSnapshot() },
        )
    }

    /**
     * 记录竞技场胜利。
     */
    suspend fun recordWin(): WriteOutcome {
        ensureCurrentSeason()
        val data = getData()

        val origWins = data.seasonWins
        val origMaxStreak = data.maxWinStreak
        val origCurrentStreak = data.currentWinStreak
        val origPoints = data.seasonPoints

        return core.transaction(
            tag = "season.recordWin",
            mutate = {
                data.seasonWins += 1

                // 更新连胜
                data.currentWinStreak += 1
                if (data.currentWinStreak > data.maxWinStreak) {
                    data.maxWinStreak = data.currentWinStreak
                }

                // 计算积分（含连胜奖励）
                var points = SeasonSaveData.POINTS_PER_WIN
                if (data.currentWinStreak > 1) {
                    points += (points * SeasonSaveData.WIN_STREAK_BONUS_MULTIPLIER * (data.currentWinStreak - 1)).toInt()
                }
                data.seasonPoints += points
            },
            rollback = {
                data.seasonWins = origWins
                data.currentWinStreak = origCurrentStreak
                data.maxWinStreak = origMaxStreak
                data.seasonPoints = origPoints
            },
            onCommit = { core.refreshSnapshot() },
        )
    }

    /**
     * 记录竞技场失败。
     */
    suspend fun recordLoss(): WriteOutcome {
        ensureCurrentSeason()
        val data = getData()

        val origLosses = data.seasonLosses
        val origCurrentStreak = data.currentWinStreak
        val origPoints = data.seasonPoints

        return core.transaction(
            tag = "season.recordLoss",
            mutate = {
                data.seasonLosses += 1
                data.currentWinStreak = 0 // 断连胜
                data.seasonPoints += SeasonSaveData.POINTS_PER_LOSS
            },
            rollback = {
                data.seasonLosses = origLosses
                data.currentWinStreak = origCurrentStreak
                data.seasonPoints = origPoints
            },
            onCommit = { core.refreshSnapshot() },
        )
    }

    /**
     * 领取赛季奖励。
     */
    override suspend fun claimSeasonReward(milestoneIndex: Int): WriteOutcome {
        val data = getData()
        val milestones = SeasonSaveData.SEASON_REWARD_MILESTONES
        if (milestoneIndex !in milestones.indices) return WriteOutcome.Rejected

        val seasonKey = data.seasonNumber * 100 + milestoneIndex
        if (data.claimedSeasonRewards.contains(seasonKey)) return WriteOutcome.Rejected

        if (data.seasonPoints < milestones[milestoneIndex]) return WriteOutcome.Rejected

        val origClaimed = data.claimedSeasonRewards.toList()
        val origSC = core.saveData.softCurrency
        val origHC = core.saveData.hardCurrency

        return core.transaction(
            tag = "season.claimReward",
            mutate = {
                data.claimedSeasonRewards = data.claimedSeasonRewards + seasonKey
                core.addCurrencyDelta(
                    SeasonSaveData.SEASON_SOFT_REWARDS.getOrElse(milestoneIndex) { 5000 },
                    SeasonSaveData.SEASON_HARD_REWARDS.getOrElse(milestoneIndex) { 50 }
                )
            },
            rollback = {
                data.claimedSeasonRewards = origClaimed
                core.saveData.softCurrency = origSC
                core.saveData.hardCurrency = origHC
            },
            onCommit = { core.publishCurrencyChanged() },
        )
    }

    /**
     * 获取赛季状态（用于 UI 展示）。
     */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override fun getSeasonStatus(): SeasonStatus {
        val data = getData()
        val today = core.today()
        val daysRemaining = if (data.seasonStartDay > 0) {
            (data.seasonStartDay + data.seasonDurationDays - today).toInt().coerceAtLeast(0)
        } else 0

        val milestones = SeasonSaveData.SEASON_REWARD_MILESTONES
        val milestoneStatuses = milestones.mapIndexed { index, threshold ->
            SeasonMilestoneStatus(
                threshold = threshold,
                softReward = SeasonSaveData.SEASON_SOFT_REWARDS.getOrElse(index) { 5000 },
                hardReward = SeasonSaveData.SEASON_HARD_REWARDS.getOrElse(index) { 50 },
                unlocked = data.seasonPoints >= threshold,
                claimed = data.claimedSeasonRewards.contains(data.seasonNumber * 100 + index),
            )
        }

        return SeasonStatus(
            seasonNumber = data.seasonNumber,
            daysRemaining = daysRemaining,
            wins = data.seasonWins,
            losses = data.seasonLosses,
            maxWinStreak = data.maxWinStreak,
            currentWinStreak = data.currentWinStreak,
            points = data.seasonPoints,
            milestones = milestoneStatuses,
        )
    }
}

/**
 * 赛季状态（UI 展示用）。
 */
data class SeasonStatus(
    val seasonNumber: Int,
    val daysRemaining: Int,
    val wins: Int,
    val losses: Int,
    val maxWinStreak: Int,
    val currentWinStreak: Int,
    val points: Int,
    val milestones: List<SeasonMilestoneStatus>,
)

/**
 * 赛季奖励里程碑状态。
 */
data class SeasonMilestoneStatus(
    val threshold: Int,
    val softReward: Int,
    val hardReward: Int,
    val unlocked: Boolean,
    val claimed: Boolean,
)
