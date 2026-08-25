package com.milan.game.services

import com.milan.game.domain.progression.EconomyFormulas

/** 单条成就定义（顶层类型：服务层状态包与 UI 直接引用）。 */
data class AchievementDef(
    val id: String,
    val title: String,
    val desc: String,
    val rewardSoft: Int = 0,
    val rewardTickets: Int = 0,
    val unlocked: (Achievements.Progress) -> Boolean,
)

/**
 * 成就定义（2026-08 二期）：**单一事实来源**——条件、文案、奖励全部集中本文件，
 * 服务层只做「判定 + 事务发放」，UI 只做展示。禁止在别处就地写成就阈值/奖励数字。
 *
 * 设计取舍：v1 用代码定义而非 data.json 内容管道——8 个成就的判定全部可由现有存档
 * 推导（Progress 纯数据），后续若成就膨胀再迁内容管线（GameContent.enrich 同路径）。
 *
 * 奖励货币说明：星尘走 softCurrency；战票走通用道具系统（BattleTicketItemId），
 * 与爬塔门票共用同一余额——形成「成就 → 战票 → 爬塔」的循环钩子。
 */
object Achievements {

    /** 成就判定用的存档进度快照（由 GameService 从 SaveData 推导，纯数据、可单测）。 */
    data class Progress(
        val ownedCount: Int,
        val totalPulls: Int,
        val towerBestFloor: Int,
        val formationSize: Int,
        val softCurrency: Int,
        /** 达到当前突破阶段等级上限的角色数（满级 = level ≥ maxLevelForStage(stage)）。 */
        val fullLeveledChars: Int,
    )

    val ALL: List<AchievementDef> = listOf(
        AchievementDef(
            id = "first_summon",
            title = "初次召唤",
            desc = "获得第一位角色",
            rewardSoft = 500,
            unlocked = { it.ownedCount >= 1 },
        ),
        AchievementDef(
            id = "roster_10",
            title = "诸神入门",
            desc = "拥有 10 位角色",
            rewardSoft = 2000,
            unlocked = { it.ownedCount >= 10 },
        ),
        AchievementDef(
            id = "pulls_100",
            title = "寻访百次",
            desc = "累计寻访 100 抽",
            rewardSoft = 3000,
            unlocked = { it.totalPulls >= 100 },
        ),
        AchievementDef(
            id = "tower_first",
            title = "高塔初探",
            desc = "攻克无尽之塔第 1 层",
            rewardTickets = 2,
            unlocked = { it.towerBestFloor >= 1 },
        ),
        AchievementDef(
            id = "tower_10",
            title = "登塔十阶",
            desc = "无尽之塔抵达第 10 层",
            rewardSoft = 5000,
            unlocked = { it.towerBestFloor >= 10 },
        ),
        AchievementDef(
            id = "formation_3",
            title = "编组成军",
            desc = "出战编队达 3 人",
            rewardTickets = 2,
            unlocked = { it.formationSize >= 3 },
        ),
        AchievementDef(
            id = "rich_100k",
            title = "富甲一方",
            desc = "持有星尘 100000",
            rewardSoft = 3000,
            unlocked = { it.softCurrency >= 100_000 },
        ),
        AchievementDef(
            id = "level_cap",
            title = "满级大师",
            desc = "练成 1 位当前阶段满级角色（等级上限随突破提升，当前 Stage4 → ${EconomyFormulas.maxLevelForStage(4)} 级）",
            rewardSoft = 5000,
            unlocked = { it.fullLeveledChars >= 1 },
        ),
    )

    /** 按 id 查定义（未知返回 null）。 */
    val byId: Map<String, AchievementDef> = ALL.associateBy { it.id }
}
