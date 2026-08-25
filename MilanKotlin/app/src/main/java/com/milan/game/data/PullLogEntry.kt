package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 抽卡历史单条记录（2026-08 三期新增，公共字段模型）。
 *
 * 追加式日志，上限 [SaveData.Companion.MAX_PULL_HISTORY]（超限丢最旧，与战绩同一裁剪口径）。
 * 仅用于玩家回顾运气轨迹（对标主流 gacha 的抽卡记录页），**不参与任何逻辑判定**——
 * 保底计数仍走 GachaCounters，删历史不影响玩法。
 */
@Serializable
class PullLogEntry(
    @SerialName("PoolId") var poolId: String = "",
    @SerialName("CharacterId") var characterId: String = "",
    @SerialName("CharacterName") var characterName: String = "",
    @SerialName("Rarity") var rarity: Int = 0,
    @SerialName("IsNew") var isNew: Boolean = false,
    @SerialName("FragmentsAwarded") var fragmentsAwarded: Int = 0,
    /** 毫秒时间戳（仅展示用，不参与确定性/迁移逻辑）。 */
    @SerialName("Timestamp") var timestamp: Long = 0L,
)

/** 卡池布尔标记条目（UP 定轨「上次歪了」状态；sanitize 按 PoolId 去重保首条）。 */
@Serializable
class PoolFlagEntry(
    @SerialName("PoolId") var poolId: String = "",
    @SerialName("Flag") var flag: Boolean = false,
)
