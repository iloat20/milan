package com.milan.game.services

import com.milan.game.data.BattleRecord

/**
 * 元进度契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（SettingsScreen / ShopScreen /
 * AchievementScreen 经 `GameState.service.xxx` 调用），由 [MetaService] 实现。
 * 设置开关、存档重置、战绩记录、每日商店、成就 —— 非玩法域的「元进度」集合。
 */
interface MetaApi {
    /** 音效开关持久化。 */
    suspend fun setSoundEnabled(enabled: Boolean): WriteOutcome

    /** 振动开关持久化。 */
    suspend fun setVibrationEnabled(enabled: Boolean): WriteOutcome

    /** 推送开关持久化。 */
    suspend fun setPushEnabled(enabled: Boolean): WriteOutcome

    /** 动效减弱开关持久化（无障碍；true=关闭高负载演出）。 */
    suspend fun setReduceMotionEnabled(enabled: Boolean): WriteOutcome

    /** 重置存档为新档（删除失败返回 false 且不动内存）。 */
    suspend fun resetSave(): Boolean

    /** 读取战绩（最近在前）。 */
    fun getBattleRecords(): List<BattleRecord>

    /** 追加一条战绩并落盘（上限 50 条）。 */
    suspend fun recordBattle(rec: BattleRecord?)

    /** 今日特惠槽位（确定性轮换）。 */
    fun dailyOffers(): List<DailyOffer>

    /** 今日已购槽位下标。 */
    fun dailyBoughtToday(): List<Int>

    /** 购买每日特惠槽位。 */
    suspend fun buyDailyOffer(index: Int): WriteOutcome

    /** 全部成就状态（定义 + 解锁 + 领取）。 */
    fun achievementStatuses(): List<AchievementStatus>

    /** 领取成就奖励。 */
    suspend fun claimAchievement(id: String): WriteOutcome

    /** 新手引导当前步骤（null=已完成/已跳过/无需展示）。 */
    fun tutorialCurrentStep(): String?

    /** 新手引导是否已结束（完成或跳过）。 */
    fun tutorialFinished(): Boolean

    /** 完成一步引导（幂等；落盘失败回滚）。 */
    suspend fun completeTutorialStep(step: String): WriteOutcome

    /** 跳过整段引导。 */
    suspend fun skipTutorial(): WriteOutcome
}
