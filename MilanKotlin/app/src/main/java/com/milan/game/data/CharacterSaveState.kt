package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 已拥有角色的养成状态（C# CharacterSaveState，公共字段模型）。 */
@Serializable
class CharacterSaveState(
    @SerialName("CharacterId") var characterId: String = "",
    @SerialName("Level") var level: Int = 1,
    @SerialName("Stage") var stage: Int = 1,
    @SerialName("Stars") var stars: Int = 1,
    @SerialName("TotalExp") var totalExp: Int = 0,
    @SerialName("UnspentPoints") var unspentPoints: Int = 0,
    @SerialName("TalentPoints") var talentPoints: List<String?> = emptyList(),
)
