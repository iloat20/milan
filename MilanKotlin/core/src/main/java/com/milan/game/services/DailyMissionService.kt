package com.milan.game.services

import com.milan.game.data.*
import com.milan.game.domain.mission.DailyMissionFormulas
import kotlin.random.Random

/**
 * 每日任务服务。
 *
 * 职责：
 * - 每日任务生成（随机6个）
 * - 进度追踪
 * - 活跃度宝箱领取
 * - 跨日重置
 */
class DailyMissionService(
    private val core: ServiceCore,
) : DailyMissionApi {

    /**
     * 每日任务模板池。
     *
     * R5-I3（2026-09-03 审查修复）：只保留有生产上报点的类型。此前 15 个模板里
     * `SPEND_SOFT_CURRENCY` / `CHECK_IN` / `FRIEND_GIFT` / `CLAIM_AFFINITY` 共 5 个
     * 全项目无 reportProgress 生产点（签到/好友/好感领取本身是未接线死功能），
     * 每日随机 6 个抽中即永不完成，极端日活跃度恒 0、30000 星尘宝箱不可达。
     */
    private val missionTemplates: List<DailyMissionDef> = listOf(
        DailyMissionDef("pull_3", "抽卡3次", "进行3次抽卡", DailyMissionType.PULL_GACHA, 3, 10, "🎰"),
        DailyMissionDef("pull_10", "抽卡10次", "进行10次抽卡", DailyMissionType.PULL_GACHA, 10, 20, "🎰"),
        DailyMissionDef("tower_1", "挑战无尽之塔", "挑战1次无尽之塔", DailyMissionType.BATTLE_TOWER, 1, 15, "🗼"),
        DailyMissionDef("tower_3", "挑战无尽之塔3次", "挑战3次无尽之塔", DailyMissionType.BATTLE_TOWER, 3, 25, "🗼"),
        DailyMissionDef("level_1", "升级角色1次", "消耗星尘升级角色", DailyMissionType.LEVEL_UP_CHARACTER, 1, 10, "⬆️"),
        DailyMissionDef("level_3", "升级角色3次", "消耗星尘升级角色3次", DailyMissionType.LEVEL_UP_CHARACTER, 3, 20, "⬆️"),
        DailyMissionDef("story_1", "完成剧情关卡", "完成1个剧情关卡", DailyMissionType.COMPLETE_STORY, 1, 15, "📖"),
        DailyMissionDef("arena_1", "挑战竞技场", "挑战1次竞技场", DailyMissionType.CHALLENGE_ARENA, 1, 10, "⚔️"),
        DailyMissionDef("arena_3", "挑战竞技场3次", "挑战3次竞技场", DailyMissionType.CHALLENGE_ARENA, 3, 20, "⚔️"),
        DailyMissionDef("enhance_1", "强化装备", "强化1次装备", DailyMissionType.ENHANCE_EQUIPMENT, 1, 10, "🔧"),
        // 2026-09-10：好感等级奖励领取已落地（ProgressionService.claimAffinityReward），
        // 补回此前因「无生产点」被剔除的 CLAIM_AFFINITY 模板。
        DailyMissionDef("affinity_1", "领取好感奖励", "领取1次好感度等级奖励", DailyMissionType.CLAIM_AFFINITY, 1, 15, "💝"),
    )

    /**
     * 获取当前每日任务数据（纯读）。
     *
     * R5-I4（2026-09-03 审查修复）：跨日重置不再在此只读 API 内进行——旧实现会在
     * `writeMutex` 外改写共享存档且不落盘，任何只读调用（getTodayMissions/getChestStatuses/
     * getDailyMissionData）都会触发。重置已迁至 [ensureDailyMissionReset]（事务 + 落盘）。
     */
    override fun getDailyMissionData(): DailyMissionSaveData {
        // R5-I5：懒创建改纯读——默认值由 sanitize/createDefault 保证非 null，不再锁外写存档。
        return core.saveData.dailyMissionData ?: DailyMissionSaveData()
    }

    /**
     * 确保今日任务已跨日重置（事务 + 落盘）。幂等：当日已重置则直接返回 Success（无锁）。
     *
     * R5-I4：跨日重置从只读 [getDailyMissionData] 迁入本 suspend 事务，原子改内存 + 落盘 + 失败回滚。
     * 由写路径（[reportDailyMissionProgress]/[claimActivityChest]）与进入每日任务页时调用。
     */
    override suspend fun ensureDailyMissionReset(): WriteOutcome {
        val data = getDailyMissionData()
        val today = core.today()
        if (data.currentDay == today && data.missionProgress.isNotEmpty()) return WriteOutcome.Success

        val origDay = data.currentDay
        val origPoints = data.activityPoints
        val origClaimed = data.claimedChests.toList()
        val origProgress = data.missionProgress
        val origCompleted = data.completedMissions.toList()

        return core.transaction(
            tag = "dailyMissions.ensureTodayReset",
            mutate = {
                data.currentDay = today
                data.activityPoints = 0
                data.claimedChests = emptyList()
                data.completedMissions = emptyList()
                generateDailyMissions(data)
            },
            rollback = {
                data.currentDay = origDay
                data.activityPoints = origPoints
                data.claimedChests = origClaimed
                data.completedMissions = origCompleted
                data.missionProgress = origProgress
            },
            onCommit = { core.refreshSnapshot() },
        )
    }

    /**
     * 生成今日每日任务（随机不重复）。
     *
     * R5-I4：用「当日日序号」作种子的独立 rng，绝不复用共享主 rng（core.rng）——旧实现的
     * `missionTemplates.shuffled(rng)` 会消耗抽卡随机序列，跨日首次打开每日任务页即打乱抽卡
     * 结果（注入 seed 的确定性测试尤其受影响）。同日任务稳定、测试可复现。
     */
    private fun generateDailyMissions(data: DailyMissionSaveData) {
        val dailyRng = Random(core.today())
        val shuffled = missionTemplates.shuffled(dailyRng)
        val selected = shuffled.take(DailyMissionSaveData.DAILY_MISSION_COUNT)
        data.missionProgress = selected.associate { it.id to 0 }
    }

    /** 获取今日任务定义列表。 */
    override fun getTodayMissions(): List<DailyMissionStatus> {
        val data = getDailyMissionData()
        return missionTemplates.filter { it.id in data.missionProgress.keys }.map { def ->
            DailyMissionStatus(
                def = def,
                progress = data.missionProgress[def.id] ?: 0,
                completed = data.completedMissions.contains(def.id),
            )
        }
    }

    /**
     * 上报任务进度（持锁 + 落盘）。由各服务在写操作成功后调用。
     *
     * R5-I4：旧实现是 `fun` 且锁外写共享存档、不落盘——聚合服务 SaveFailed 回滚后每日任务
     * 进度已推进且永不回滚，并与临界区写并发竞争。现改为 suspend 事务：先 [ensureDailyMissionReset]
     * 跨日重置，再在同一事务内推进进度并落盘，失败回滚。
     */
    override suspend fun reportDailyMissionProgress(type: DailyMissionType, amount: Int) {
        if (amount <= 0) return
        ensureDailyMissionReset()
        val data = getDailyMissionData()
        if (data.currentDay != core.today()) return
        // 今日未抽到该类型任务 → 无写、无落盘
        if (missionTemplates.none { it.type == type && !data.completedMissions.contains(it.id) }) return

        val origProgress = data.missionProgress
        val origCompleted = data.completedMissions.toList()
        val origPoints = data.activityPoints
        val origTotal = data.totalMissionsCompleted
        val origTotalPoints = data.totalActivityPoints

        core.transaction(
            tag = "dailyMissions.reportProgress",
            mutate = {
                for (def in missionTemplates) {
                    if (def.type != type) continue
                    val id = def.id
                    if (data.completedMissions.contains(id)) continue
                    val progress = data.missionProgress[id] ?: 0
                    // R6-P1：UI「升满」会传 Int.MAX_VALUE，progress+amount 溢出为负 → 任务永久卡死。
                    // 用 Long 累加后再钳到 targetCount。
                    val newProgress = (progress.toLong() + amount.toLong())
                        .coerceAtMost(def.targetCount.toLong())
                        .toInt()
                    data.missionProgress = data.missionProgress + (id to newProgress)
                    if (newProgress >= def.targetCount) {
                        data.completedMissions = data.completedMissions + id
                        data.activityPoints = DailyMissionFormulas.activityAfterReward(data.activityPoints, def.activityReward)
                        data.totalMissionsCompleted += 1
                        data.totalActivityPoints += def.activityReward
                    }
                }
            },
            rollback = {
                data.missionProgress = origProgress
                data.completedMissions = origCompleted
                data.activityPoints = origPoints
                data.totalMissionsCompleted = origTotal
                data.totalActivityPoints = origTotalPoints
            },
            onCommit = { core.refreshSnapshot() },
        )
    }

    /** 领取活跃度宝箱。 */
    override suspend fun claimActivityChest(milestone: Int): WriteOutcome {
        if (!DailyMissionSaveData.ACTIVITY_MILESTONES.contains(milestone)) return WriteOutcome.Rejected
        // R5-I4：跨日重置纳入事务（幂等），确保领奖前数据已是「今日」口径。
        ensureDailyMissionReset()

        // R6-P1：claimed/points 校验在锁内复检，防并发双领。
        return core.withWriteLock {
            val data = getDailyMissionData()
            if (data.activityPoints < milestone) return@withWriteLock WriteOutcome.Rejected
            if (data.claimedChests.contains(milestone)) return@withWriteLock WriteOutcome.Rejected

            val origClaimed = data.claimedChests.toList()
            val origSC = core.saveData.softCurrency
            val origHC = core.saveData.hardCurrency

            core.transactionLocked(
                tag = "dailyMissions.claimChest",
                mutate = {
                    data.claimedChests = data.claimedChests + milestone
                    val softReward = DailyMissionSaveData.MILESTONE_REWARDS[milestone] ?: 0
                    core.addCurrencyDelta(softReward, 0)
                    val hardReward = DailyMissionSaveData.MILESTONE_HARD_REWARDS[milestone] ?: 0
                    if (hardReward > 0) {
                        core.addCurrencyDelta(0, hardReward)
                    }
                },
                rollback = {
                    data.claimedChests = origClaimed
                    core.saveData.softCurrency = origSC
                    core.saveData.hardCurrency = origHC
                },
                onCommit = {
                    core.publishCurrencyChanged()
                },
            )
        }
    }

    /** 今日活跃度宝箱状态。 */
    override fun getChestStatuses(): List<ChestStatus> {
        val data = getDailyMissionData()
        return DailyMissionSaveData.ACTIVITY_MILESTONES.map { milestone ->
            ChestStatus(
                milestone = milestone,
                claimed = data.claimedChests.contains(milestone),
                unlocked = data.activityPoints >= milestone,
                softReward = DailyMissionSaveData.MILESTONE_REWARDS[milestone] ?: 0,
                hardReward = DailyMissionSaveData.MILESTONE_HARD_REWARDS[milestone] ?: 0,
            )
        }
    }

    /** 累计完成任务数（成就判定）。 */
    fun getTotalCompleted(): Int = getDailyMissionData().totalMissionsCompleted
}

/**
 * 每日任务状态（定义 + 实时进度）。
 */
data class DailyMissionStatus(
    val def: DailyMissionDef,
    val progress: Int,
    val completed: Boolean,
)

/**
 * 活跃度宝箱状态。
 */
data class ChestStatus(
    val milestone: Int,
    val claimed: Boolean,
    val unlocked: Boolean,
    val softReward: Int,
    val hardReward: Int,
)
