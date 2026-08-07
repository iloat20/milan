package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 战斗记录（C# BattleRecord，公共字段模型）。 */
@Serializable
class BattleRecord(
    @SerialName("EnemyName") var enemyName: String = "",
    @SerialName("EnemyElement") var enemyElement: String = "",
    @SerialName("Victory") var victory: Boolean = false,
    @SerialName("Turns") var turns: Int = 0,
    /** 胜利时我方剩余总血量 */
    @SerialName("RemainingHp") var remainingHp: Int = 0,
    /** 队伍战力快照（攻击总和） */
    @SerialName("TeamPower") var teamPower: Int = 0,
    /** 毫秒时间戳 */
    @SerialName("Timestamp") var timestamp: Long = 0L,
)
