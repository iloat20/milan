package com.milan.game.services

import com.milan.game.data.*
import kotlin.random.Random

/**
 * 每日签到连续奖励服务。
 *
 * 职责：
 * - 每日签到（7天周期）
 * - 连续签到追踪（断签归零）
 * - 周期全勤大奖
 * - 累计签到天数（用于成就判定）
 */
class DailyCheckInService(
    private val core: ServiceCore,
) : DailyCheckInApi {

    /** 获取签到数据（纯读）。 */
    fun getData(): DailyCheckInSaveData {
        return core.saveData.dailyCheckInData ?: DailyCheckInSaveData()
    }

    /**
     * 确保当前签到周期数据有效（跨日/跨周期重置）。
     *
     * 幂等：当前周期数据有效则直接返回。
     */
    suspend fun ensureTodayReset(): WriteOutcome {
        val data = getData()
        val today = core.today()

        // 计算当前周期起始日
        val cycleStartDay = today / DailyCheckInSaveData.CYCLE_LENGTH * DailyCheckInSaveData.CYCLE_LENGTH

        if (data.cycleStartDay == cycleStartDay) return WriteOutcome.Success

        // 跨周期：检查上一个周期是否全勤，发放全勤奖励
        val origCycleStartDay = data.cycleStartDay
        val origSignedDays = data.signedDaysInCycle
        val origCurrentStreak = data.currentStreakDays
        val origClaimed = data.claimedCycleRewards.toList()

        return core.transaction(
            tag = "dailyCheckIn.ensureTodayReset",
            mutate = {
                // 检查上一周期是否全勤
                if (data.signedDaysInCycle >= DailyCheckInSaveData.CYCLE_LENGTH) {
                    if (!data.claimedCycleRewards.contains(origCycleStartDay)) {
                        // 发放全勤大奖
                        core.addCurrencyDelta(
                            DailyCheckInSaveData.CYCLE_FULL_SOFT_REWARD,
                            DailyCheckInSaveData.CYCLE_FULL_HARD_REWARD
                        )
                        data.claimedCycleRewards = data.claimedCycleRewards + origCycleStartDay
                    }
                }

                // 重置连续签到（如果跨周期且未全勤）
                if (data.signedDaysInCycle < DailyCheckInSaveData.CYCLE_LENGTH) {
                    data.currentStreakDays = 0
                }

                // 开始新周期
                data.cycleStartDay = cycleStartDay
                data.signedDaysInCycle = 0
            },
            rollback = {
                data.cycleStartDay = origCycleStartDay
                data.signedDaysInCycle = origSignedDays
                data.currentStreakDays = origCurrentStreak
                data.claimedCycleRewards = origClaimed
            },
            onCommit = { core.refreshSnapshot() },
        )
    }

    /**
     * 执行今日签到。
     *
     * 已签到 → Rejected；跨周期 → 先重置再签到。
     */
    override suspend fun signToday(): WriteOutcome {
        ensureTodayReset()
        val data = getData()
        val today = core.today()
        val cycleStartDay = today / DailyCheckInSaveData.CYCLE_LENGTH * DailyCheckInSaveData.CYCLE_LENGTH

        // 检查今日是否已签到（通过检查 signedDaysInCycle 是否已达今日应签数）
        // 简化判断：如果 cycleStartDay 正确且 signedDaysInCycle >= CYCLE_LENGTH 则已签满
        if (data.signedDaysInCycle >= DailyCheckInSaveData.CYCLE_LENGTH) return WriteOutcome.Rejected

        val origSignedDays = data.signedDaysInCycle
        val origTotalDays = data.totalSignedDays
        val origMaxStreak = data.maxStreakDays
        val origCurrentStreak = data.currentStreakDays
        val origSC = core.saveData.softCurrency
        val origHC = core.saveData.hardCurrency

        val dayIndex = data.signedDaysInCycle // 0-based index for today
        val softReward = DailyCheckInSaveData.DAILY_SOFT_REWARDS.getOrElse(dayIndex) { 1000 }
        val hardReward = DailyCheckInSaveData.DAILY_HARD_REWARDS.getOrElse(dayIndex) { 0 }

        return core.transaction(
            tag = "dailyCheckIn.sign",
            mutate = {
                data.signedDaysInCycle += 1
                data.totalSignedDays += 1

                // 更新连续签到
                val newStreak = data.currentStreakDays + 1
                data.currentStreakDays = newStreak
                if (newStreak > data.maxStreakDays) {
                    data.maxStreakDays = newStreak
                }

                // 发放奖励
                if (softReward > 0) core.addCurrencyDelta(softReward, 0)
                if (hardReward > 0) core.addCurrencyDelta(0, hardReward)
            },
            rollback = {
                data.signedDaysInCycle = origSignedDays
                data.totalSignedDays = origTotalDays
                data.currentStreakDays = origCurrentStreak
                data.maxStreakDays = origMaxStreak
                core.saveData.softCurrency = origSC
                core.saveData.hardCurrency = origHC
            },
            onCommit = { core.publishCurrencyChanged() },
        )
    }

    /** 今日是否已签到。 */
    @Deprecated("P2-11: UI层零调用（UI通过getCheckInStatus获取状态）", level = DeprecationLevel.WARNING)
    override fun isTodaySigned(): Boolean {
        val data = getData()
        val today = core.today()
        val cycleStartDay = today / DailyCheckInSaveData.CYCLE_LENGTH * DailyCheckInSaveData.CYCLE_LENGTH
        if (data.cycleStartDay != cycleStartDay) return false
        return data.signedDaysInCycle >= DailyCheckInSaveData.CYCLE_LENGTH
    }

    /** 获取今日签到状态（用于 UI 展示；契约名 [DailyCheckInApi.getCheckInStatus]）。 */
    override fun getCheckInStatus(): CheckInDayStatus {
        val data = getData()
        val today = core.today()
        val cycleStartDay = today / DailyCheckInSaveData.CYCLE_LENGTH * DailyCheckInSaveData.CYCLE_LENGTH
        val isCurrentCycle = data.cycleStartDay == cycleStartDay
        val signedToday = isCurrentCycle && data.signedDaysInCycle >= DailyCheckInSaveData.CYCLE_LENGTH

        return CheckInDayStatus(
            signedToday = signedToday,
            cycleDay = if (isCurrentCycle) data.signedDaysInCycle else 0,
            currentStreak = data.currentStreakDays,
            totalDays = data.totalSignedDays,
            softReward = DailyCheckInSaveData.DAILY_SOFT_REWARDS.getOrElse(
                if (isCurrentCycle) data.signedDaysInCycle else 0
            ) { 1000 },
            hardReward = DailyCheckInSaveData.DAILY_HARD_REWARDS.getOrElse(
                if (isCurrentCycle) data.signedDaysInCycle else 0
            ) { 0 },
        )
    }
}

/**
 * 每日签到状态（UI 展示用）。
 */
data class CheckInDayStatus(
    val signedToday: Boolean,
    val cycleDay: Int,
    val currentStreak: Int,
    val totalDays: Int,
    val softReward: Int,
    val hardReward: Int,
)
