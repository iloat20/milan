package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 图鉴收集系统存档数据。
 *
 * 角色首次获得解锁图鉴；收集里程碑奖励永久星尘加成。
 *
 * **2026-09-08 概念收敛（方案 B）**：图鉴解锁态改由 `SaveData.ownedCharacters` 派生
 * （拥有即解锁），本数据仅保留里程碑领取态 [claimedMilestones]。
 */
@Serializable
class CollectionSaveData(
    /**
     * 已解锁图鉴的角色ID列表。
     *
     * @Deprecated 不再读写（唯一写入方 recordCharacterObtained 早已无调用方，读路径恒空）。
     * 解锁态现由 `ownedCharacters` 派生；字段仅保留以兼容旧存档反序列化，勿再写入。
     */
    @SerialName("UnlockedCharacters") var unlockedCharacters: List<String?> = emptyList(),
    /** 已领取的收集里程碑（required 阈值）。 */
    @SerialName("ClaimedMilestones") var claimedMilestones: List<Int?> = emptyList(),
)
