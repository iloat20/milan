package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 社交系统存档数据。
 * 
 * 设计：
 * - 好友系统：添加好友、赠送体力、查看好友阵容
 * - 公会系统：创建/加入公会、公会任务、公会战
 * - 聊天系统（预留）
 */
@Serializable
class SocialSaveData(
    /** 好友列表。 */
    @SerialName("Friends") var friends: List<FriendData?> = emptyList(),
    /** 好友申请列表。 */
    @SerialName("FriendRequests") var friendRequests: List<FriendRequest?> = emptyList(),
    /** 已赠送体力的好友ID列表（每日重置）。 */
    @SerialName("GiftedFriends") var giftedFriends: List<String?> = emptyList(),
    /** 今日收到的体力。 */
    @SerialName("ReceivedStamina") var receivedStamina: Int = 0,
    /** 公会信息。 */
    @SerialName("GuildData") var guildData: GuildData? = null,
    /** 公会贡献点数。 */
    @SerialName("GuildContributions") var guildContributions: Int = 0,
    /** 最后刷新时间（用于每日重置）。 */
    @SerialName("LastRefreshTime") var lastRefreshTime: Long = 0,
) {
    companion object {
        /** 最大好友数 */
        const val MAX_FRIENDS = 50
        
        /** 每日可赠送体力次数 */
        const val DAILY_GIFT_LIMIT = 20
        
        /** 每次赠送获得的体力 */
        const val STAMINA_PER_GIFT = 5
        
        /** 每日可接受体力上限 */
        const val MAX_RECEIVED_STAMINA = 100
    }
}

/**
 * 好友数据。
 */
@Serializable
data class FriendData(
    @SerialName("FriendId") val friendId: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Level") val level: Int = 1,
    @SerialName("LastLoginTime") val lastLoginTime: Long = 0,
    @SerialName("TotalPower") val totalPower: Int = 0,
    @SerialName("IsOnline") val isOnline: Boolean = false,
    @SerialName("FavoriteCharacter") val favoriteCharacter: String = "",
)

/**
 * 好友申请。
 */
@Serializable
data class FriendRequest(
    @SerialName("RequestId") val requestId: String = "",
    @SerialName("FromId") val fromId: String = "",
    @SerialName("FromName") val fromName: String = "",
    @SerialName("Timestamp") val timestamp: Long = 0,
    @SerialName("Message") val message: String = "",
)

/**
 * 公会数据。
 */
@Serializable
data class GuildData(
    @SerialName("GuildId") val guildId: String = "",
    @SerialName("GuildName") val guildName: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Level") val level: Int = 1,
    @SerialName("MemberCount") val memberCount: Int = 1,
    @SerialName("MaxMembers") val maxMembers: Int = 30,
    @SerialName("TotalPower") val totalPower: Long = 0,
    @SerialName("GuildMaster") val guildMaster: String = "",
    @SerialName("JoinType") val joinType: JoinType = JoinType.AUTO,
    @SerialName("MinLevel") val minLevel: Int = 1,
    @SerialName("CreatedAt") val createdAt: Long = 0,
)

/**
 * 公会加入类型。
 */
enum class JoinType {
    AUTO,       // 自动加入
    APPROVAL,   // 需要审批
    CLOSED,     // 关闭招募
}

/**
 * 公会成员数据。
 */
@Serializable
data class GuildMember(
    @SerialName("MemberId") val memberId: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Level") val level: Int = 1,
    @SerialName("Contribution") val contribution: Int = 0,
    @SerialName("Role") val role: GuildRole = GuildRole.MEMBER,
    @SerialName("JoinTime") val joinTime: Long = 0,
    @SerialName("LastActive") val lastActive: Long = 0,
    @SerialName("TotalPower") val totalPower: Int = 0,
)

/**
 * 公会职位。
 */
enum class GuildRole {
    MASTER,     // 公会长
    OFFICER,    // 干部
    MEMBER,     // 成员
}

/**
 * 公会任务类型。
 */
enum class GuildTaskType(
    val displayName: String,
    val description: String,
    val contributionReward: Int,
) {
    DAILY_LOGIN("每日登录", "每日登录游戏", 10),
    DAILY_DUNGEON("挑战副本", "完成3次日常副本", 20),
    ARENA_WIN("竞技场胜利", "在竞技场获胜3次", 30),
    GACHA_PULL("抽卡", "进行10次抽卡", 40),
    DONATION("捐赠", "向公会捐赠资源", 50),
}

/**
 * 公会任务状态。
 */
@Serializable
data class GuildTaskState(
    @SerialName("TaskType") val taskType: GuildTaskType,
    @SerialName("Progress") var progress: Int = 0,
    @SerialName("Target") val target: Int = 1,
    @SerialName("Completed") var completed: Boolean = false,
    @SerialName("Claimed") var claimed: Boolean = false,
)

/**
 * 公会战数据（预留）。
 */
@Serializable
data class GuildWarData(
    @SerialName("WarId") val warId: String = "",
    @SerialName("Season") val season: Int = 1,
    @SerialName("GuildScore") var guildScore: Int = 0,
    @SerialName("EnemyScore") var enemyScore: Int = 0,
    @SerialName("Status") val status: String = "pending",
    @SerialName("StartTime") val startTime: Long = 0,
    @SerialName("EndTime") val endTime: Long = 0,
)

/**
 * 好友赠礼记录。
 */
@Serializable
data class GiftRecord(
    @SerialName("GiftId") val giftId: String = "",
    @SerialName("FromId") val fromId: String = "",
    @SerialName("ToId") val toId: String = "",
    @SerialName("GiftType") val giftType: String = "stamina",
    @SerialName("Amount") val amount: Int = 5,
    @SerialName("Timestamp") val timestamp: Long = 0,
    @SerialName("Claimed") val claimed: Boolean = false,
)
