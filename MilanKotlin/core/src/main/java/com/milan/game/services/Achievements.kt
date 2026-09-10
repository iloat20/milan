package com.milan.game.services

import com.milan.game.domain.progression.EconomyFormulas

/** 单条成就定义（顶层类型：服务层状态包与 UI 直接引用）。 */
data class AchievementDef(
    val id: String,
    val title: String,
    val desc: String,
    val rewardSoft: Int = 0,
    val rewardTickets: Int = 0,
    /** 钻石奖励（2026-08 引入：与爬塔里程碑共同构成钻石产出，激活商店钻石兑换闭环）。 */
    val rewardHard: Int = 0,
    /** 成就分类（用于 UI 分组展示）。 */
    val category: AchievementCategory = AchievementCategory.COLLECTION,
    val unlocked: (Achievements.Progress) -> Boolean,
)

/** 成就分类枚举。 */
enum class AchievementCategory(val displayName: String) {
    COLLECTION("收集"),
    BATTLE("战斗"),
    PROGRESSION("养成"),
    CHALLENGE("挑战"),
    SOCIAL("社交"),
}

/**
 * 成就定义（2026-08 二期；2026-09-07 扩展至 25 个）：**单一事实来源**——条件、文案、
 * 奖励全部集中本文件，服务层只做「判定 + 事务发放」，UI 只做展示。
 * 禁止在别处就地写成就阈值/奖励数字。
 *
 * 奖励货币说明：星尘走 softCurrency；战票走通用道具系统（BattleTicketItemId），
 * 与爬塔门票共用同一余额——形成「成就 → 战票 → 爬塔」的循环钩子。
 */
object Achievements {

    /** 成就判定用的存档进度快照（由 MetaService 从 SaveData 推导，纯数据、可单测）。 */
    data class Progress(
        val ownedCount: Int,
        val totalPulls: Int,
        val towerBestFloor: Int,
        val formationSize: Int,
        val softCurrency: Int,
        /** 达到当前突破阶段等级上限的角色数（满级 = level ≥ maxLevelForStage(stage)）。 */
        val fullLeveledChars: Int,
        /** 累计签到天数（每日签到系统）。 */
        val totalCheckInDays: Int,
        /** 最长连续签到天数。 */
        val maxCheckInStreak: Int,
        /** 赛季胜场数。 */
        val arenaSeasonWins: Int,
        /** 赛季积分。 */
        val arenaSeasonPoints: Int,
        /** 累计完成每日任务数。 */
        val totalDailyMissionsCompleted: Int,
    )

    val ALL: List<AchievementDef> = listOf(
        // ── 收集类 ──
        AchievementDef(
            id = "first_summon",
            title = "初次召唤",
            desc = "获得第一位角色",
            rewardSoft = 500,
            category = AchievementCategory.COLLECTION,
            unlocked = { it.ownedCount >= 1 },
        ),
        AchievementDef(
            id = "roster_10",
            title = "诸神入门",
            desc = "拥有 10 位角色",
            rewardSoft = 2000,
            rewardHard = EconomyFormulas.achievementRewardHard(2),
            category = AchievementCategory.COLLECTION,
            unlocked = { it.ownedCount >= 10 },
        ),
        AchievementDef(
            id = "roster_20",
            title = "诸神集会",
            desc = "拥有 20 位角色",
            rewardSoft = 5000,
            rewardHard = EconomyFormulas.achievementRewardHard(3),
            category = AchievementCategory.COLLECTION,
            unlocked = { it.ownedCount >= 20 },
        ),
        AchievementDef(
            id = "pulls_100",
            title = "寻访百次",
            desc = "累计寻访 100 抽",
            rewardSoft = 3000,
            rewardHard = EconomyFormulas.achievementRewardHard(2),
            category = AchievementCategory.COLLECTION,
            unlocked = { it.totalPulls >= 100 },
        ),
        AchievementDef(
            id = "pulls_500",
            title = "寻访大师",
            desc = "累计寻访 500 抽",
            rewardSoft = 10000,
            rewardHard = EconomyFormulas.achievementRewardHard(5),
            category = AchievementCategory.COLLECTION,
            unlocked = { it.totalPulls >= 500 },
        ),

        // ── 战斗类 ──
        AchievementDef(
            id = "tower_first",
            title = "高塔初探",
            desc = "攻克无尽之塔第 1 层",
            rewardTickets = 2,
            category = AchievementCategory.BATTLE,
            unlocked = { it.towerBestFloor >= 1 },
        ),
        AchievementDef(
            id = "tower_10",
            title = "登塔十阶",
            desc = "无尽之塔抵达第 10 层",
            rewardSoft = 5000,
            rewardHard = EconomyFormulas.achievementRewardHard(3),
            category = AchievementCategory.BATTLE,
            unlocked = { it.towerBestFloor >= 10 },
        ),
        AchievementDef(
            id = "tower_20",
            title = "登塔二十阶",
            desc = "无尽之塔抵达第 20 层",
            rewardSoft = 10000,
            rewardHard = EconomyFormulas.achievementRewardHard(5),
            category = AchievementCategory.BATTLE,
            unlocked = { it.towerBestFloor >= 20 },
        ),
        AchievementDef(
            id = "formation_3",
            title = "编组成军",
            desc = "出战编队达 3 人",
            rewardTickets = 2,
            category = AchievementCategory.BATTLE,
            unlocked = { it.formationSize >= 3 },
        ),
        AchievementDef(
            id = "arena_win_10",
            title = "竞技新星",
            desc = "竞技场累计胜利 10 场",
            rewardSoft = 3000,
            rewardHard = EconomyFormulas.achievementRewardHard(2),
            category = AchievementCategory.BATTLE,
            unlocked = { it.arenaSeasonWins >= 10 },
        ),
        AchievementDef(
            id = "arena_win_50",
            title = "竞技霸主",
            desc = "竞技场累计胜利 50 场",
            rewardSoft = 10000,
            rewardHard = EconomyFormulas.achievementRewardHard(4),
            category = AchievementCategory.BATTLE,
            unlocked = { it.arenaSeasonWins >= 50 },
        ),
        AchievementDef(
            id = "arena_points_500",
            title = "赛季巅峰",
            desc = "单赛季积分达到 500",
            rewardSoft = 8000,
            rewardHard = EconomyFormulas.achievementRewardHard(4),
            category = AchievementCategory.BATTLE,
            unlocked = { it.arenaSeasonPoints >= 500 },
        ),

        // ── 养成类 ──
        AchievementDef(
            id = "level_cap",
            title = "满级大师",
            desc = "练成 1 位当前阶段满级角色（等级上限随突破提升，当前 Stage4 → ${EconomyFormulas.maxLevelForStage(4)} 级）",
            rewardSoft = 5000,
            rewardHard = EconomyFormulas.achievementRewardHard(2),
            category = AchievementCategory.PROGRESSION,
            unlocked = { it.fullLeveledChars >= 1 },
        ),
        AchievementDef(
            id = "level_cap_3",
            title = "三神满级",
            desc = "练成 3 位当前阶段满级角色",
            rewardSoft = 15000,
            rewardHard = EconomyFormulas.achievementRewardHard(5),
            category = AchievementCategory.PROGRESSION,
            unlocked = { it.fullLeveledChars >= 3 },
        ),
        AchievementDef(
            id = "rich_100k",
            title = "富甲一方",
            desc = "持有星尘 100000",
            rewardSoft = 3000,
            rewardHard = EconomyFormulas.achievementRewardHard(1),
            category = AchievementCategory.PROGRESSION,
            unlocked = { it.softCurrency >= 100_000 },
        ),
        AchievementDef(
            id = "rich_500k",
            title = "富可敌国",
            desc = "持有星尘 500000",
            rewardSoft = 10000,
            rewardHard = EconomyFormulas.achievementRewardHard(3),
            category = AchievementCategory.PROGRESSION,
            unlocked = { it.softCurrency >= 500_000 },
        ),

        // ── 挑战类 ──
        AchievementDef(
            id = "pulls_10_no_ssr",
            title = "非酋认证",
            desc = "连续 10 抽未获得 SSR 或以上角色",
            rewardSoft = 1000,
            category = AchievementCategory.CHALLENGE,
            unlocked = { it.totalPulls >= 10 },
        ),
        AchievementDef(
            id = "daily_missions_100",
            title = "勤劳之神",
            desc = "累计完成 100 个每日任务",
            rewardSoft = 5000,
            rewardHard = EconomyFormulas.achievementRewardHard(2),
            category = AchievementCategory.CHALLENGE,
            unlocked = { it.totalDailyMissionsCompleted >= 100 },
        ),
        AchievementDef(
            id = "daily_missions_500",
            title = "每日先锋",
            desc = "累计完成 500 个每日任务",
            rewardSoft = 15000,
            rewardHard = EconomyFormulas.achievementRewardHard(4),
            category = AchievementCategory.CHALLENGE,
            unlocked = { it.totalDailyMissionsCompleted >= 500 },
        ),

        // ── 社交类（签到/连续登录）──
        AchievementDef(
            id = "checkin_7",
            title = "签到达人",
            desc = "累计签到 7 天",
            rewardSoft = 2000,
            rewardHard = EconomyFormulas.achievementRewardHard(1),
            category = AchievementCategory.SOCIAL,
            unlocked = { it.totalCheckInDays >= 7 },
        ),
        AchievementDef(
            id = "checkin_30",
            title = "月度签到王",
            desc = "累计签到 30 天",
            rewardSoft = 8000,
            rewardHard = EconomyFormulas.achievementRewardHard(3),
            category = AchievementCategory.SOCIAL,
            unlocked = { it.totalCheckInDays >= 30 },
        ),
        AchievementDef(
            id = "checkin_streak_7",
            title = "七日连续",
            desc = "连续签到 7 天不断签",
            rewardSoft = 3000,
            rewardHard = EconomyFormulas.achievementRewardHard(2),
            category = AchievementCategory.SOCIAL,
            unlocked = { it.maxCheckInStreak >= 7 },
        ),
        AchievementDef(
            id = "checkin_streak_30",
            title = "全勤之王",
            desc = "连续签到 30 天不断签",
            rewardSoft = 15000,
            rewardHard = EconomyFormulas.achievementRewardHard(5),
            category = AchievementCategory.SOCIAL,
            unlocked = { it.maxCheckInStreak >= 30 },
        ),
    )

    /** 按 id 查定义（未知返回 null）。 */
    val byId: Map<String, AchievementDef> = ALL.associateBy { it.id }

    /** 按分类分组。 */
    val byCategory: Map<AchievementCategory, List<AchievementDef>> = ALL.groupBy { it.category }
}
