package com.milan.game.services

import com.milan.game.data.ArenaOpponent
import com.milan.game.data.ArenaSaveData
import com.milan.game.data.SeasonReward

/**
 * 竞技场挑战结果（2026-09-10 UI 结算演出）。
 *
 * 替代原先仅返回 [WriteOutcome] 的挑战契约：成功时携带胜负 / 回合 / 积分变动，
 * 供 ArenaScreen 弹出结果卡（与爬塔 BattleResultOverlay 同级信息密度的轻量版）。
 */
sealed interface ArenaChallengeOutcome {
    data class Completed(
        val victory: Boolean,
        val turns: Int,
        val pointsDelta: Int,
        val pointsAfter: Int,
        val opponentName: String,
    ) : ArenaChallengeOutcome

    data object Rejected : ArenaChallengeOutcome
    data object SaveFailed : ArenaChallengeOutcome
}

/**
 * 竞技场契约（2026-09-08 P1-5 接口化）。
 *
 * 方法面 = GameService 曾直接暴露给 UI 的**对外契约名**（ArenaScreen 经
 * `GameState.service.xxx` 调用），由 [ArenaService] 实现。GameService 仅对
 * [challengeOpponent] 做显式 override 追加编排（胜利/失败 → 赛季记录 + 每日任务/通行证
 * 上报），底层结算逻辑在本接口实现中，行为与拆分前等价。
 */
interface ArenaApi {
    /** 竞技场当前存档数据（段位/积分/防守编队/挑战次数）。 */
    fun getArenaData(): ArenaSaveData

    /** 设置防守编队（整体替换）。 */
    suspend fun setDefenseTeam(characterIds: List<String>): WriteOutcome

    /** 当前可挑战对手列表。 */
    fun getOpponents(): List<ArenaOpponent>

    /** 挑战对手（写盘结算 + 奖励发放；成功时携带胜负与积分变动供 UI 结算）。 */
    suspend fun challengeOpponent(opponent: ArenaOpponent): ArenaChallengeOutcome

    /** 当前段位（名次, 称号）。 */
    fun getArenaRank(): Pair<Int, String>

    /** 赛季结算奖励列表。 */
    fun getSeasonRewards(): List<SeasonReward>
}
