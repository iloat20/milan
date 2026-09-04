package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PVP竞技场存档数据。
 * 
 * 设计：
 * - 异步PVP：玩家上传防守阵容，其他玩家挑战
 * - 竞技场等级：根据胜负调整段位
 * - 赛季制：每赛季重置排名和奖励
 */
@Serializable
class ArenaSaveData(
    @SerialName("ArenaPoints") var arenaPoints: Int = 1000,
    @SerialName("ArenaRank") var arenaRank: Int = 0,
    @SerialName("DefenseTeam") var defenseTeam: List<String?> = emptyList(),
    @SerialName("AttackCount") var attackCount: Int = 0,
    @SerialName("WinCount") var winCount: Int = 0,
    @SerialName("LoseCount") var loseCount: Int = 0,
    @SerialName("SeasonRewards") var seasonRewards: List<String?> = emptyList(),
    @SerialName("LastRefreshTime") var lastRefreshTime: Long = 0,
) {
    companion object {
        /** 每日免费挑战次数 */
        const val DAILY_FREE_ATTACKS = 5
        
        /** 胜利获得的积分 */
        const val WIN_POINTS = 30
        
        /** 失败扣除的积分 */
        const val LOSE_POINTS = 20
        
        /** 最低积分 */
        const val MIN_POINTS = 0
        
        /** 最高积分 */
        const val MAX_POINTS = 9999
    }
}

/**
 * PVP战斗记录。
 */
@Serializable
class PvPBattleRecord(
    @SerialName("RecordId") var recordId: String = "",
    @SerialName("AttackerId") var attackerId: String = "",
    @SerialName("DefenderId") var defenderId: String = "",
    @SerialName("AttackerName") var attackerName: String = "",
    @SerialName("DefenderName") var defenderName: String = "",
    @SerialName("AttackerTeam") var attackerTeam: List<String?> = emptyList(),
    @SerialName("DefenderTeam") var defenderTeam: List<String?> = emptyList(),
    @SerialName("AttackerPoints") var attackerPoints: Int = 0,
    @SerialName("DefenderPoints") var defenderPoints: Int = 0,
    @SerialName("Result") var result: String = "",  // "win" / "lose" / "draw"
    @SerialName("Turns") var turns: Int = 0,
    @SerialName("Timestamp") var timestamp: Long = 0,
    @SerialName("PointsChanged") var pointsChanged: Int = 0,
)

/**
 * PVP对手信息。
 */
data class ArenaOpponent(
    val characterId: String,
    val name: String,
    val level: Int,
    val rank: Int,
    val teamPower: Int,
    val defenseTeam: List<String>,
    val points: Int,
    val isOnline: Boolean,
)

/**
 * PVP赛季奖励。
 */
data class SeasonReward(
    val rank: Int,
    val title: String,
    val softCurrency: Int,
    val hardCurrency: Int,
    val fragments: Int,
    val exclusiveReward: String?,
)
