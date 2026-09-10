package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 道具持有条目（C# ItemSaveState，公共字段模型）。 */
@Serializable
class ItemSaveState(
    @SerialName("ItemId") var itemId: String = "",
    @SerialName("Count") var count: Int = 0,
)
