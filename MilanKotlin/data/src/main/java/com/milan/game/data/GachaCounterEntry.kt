package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 抽卡计数条目（C# GachaCounterEntry，公共字段模型）。 */
@Serializable
class GachaCounterEntry(
    @SerialName("PoolId") var poolId: String = "",
    @SerialName("Count") var count: Int = 0,
)
