package com.milan.game.services

import com.milan.game.data.*
import kotlin.random.Random

/**
 * 社交系统服务。
 * 
 * 职责：
 * - 好友管理（添加/删除/赠送体力）
 * - 公会管理（创建/加入/任务/捐献）
 * - 社交互动
 */
internal class SocialService(
    private val core: ServiceCore,
    private val rng: Random = Random.Default,
) {
    
    /**
     * 获取社交数据。
     */
    fun getSocialData(): SocialSaveData {
        // R5-I5：懒创建改纯读——默认值由 sanitize/createDefault 保证非 null，不再锁外写存档。
        return core.saveData.socialData ?: SocialSaveData()
    }
    
    // ─────────────────────────── 好友系统 ───────────────────────────
    
    /**
     * 获取好友列表。
     */
    fun getFriends(): List<FriendData> {
        return getSocialData().friends.filterNotNull()
    }
    
    /**
     * 添加好友。
     */
    suspend fun addFriend(friendId: String, friendName: String): WriteOutcome {
        val socialData = getSocialData()
        
        // 检查好友数量上限
        if (socialData.friends.size >= SocialSaveData.MAX_FRIENDS) {
            return WriteOutcome.Rejected
        }
        
        // 检查是否已是好友
        if (socialData.friends.any { it?.friendId == friendId }) {
            return WriteOutcome.Rejected
        }
        
        val original = socialData.friends.toList()
        
        return core.transaction(
            tag = "social.addFriend",
            mutate = {
                socialData.friends = socialData.friends + FriendData(
                    friendId = friendId,
                    name = friendName,
                    level = 1,
                    lastLoginTime = System.currentTimeMillis(),
                    totalPower = rng.nextInt(1000, 5000),
                    isOnline = rng.nextBoolean(),
                    favoriteCharacter = "char_r_001",
                )
            },
            rollback = {
                socialData.friends = original
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
    
    /**
     * 删除好友。
     */
    suspend fun removeFriend(friendId: String): WriteOutcome {
        val socialData = getSocialData()
        val original = socialData.friends.toList()
        
        return core.transaction(
            tag = "social.removeFriend",
            mutate = {
                socialData.friends = socialData.friends.filter { it?.friendId != friendId }
            },
            rollback = {
                socialData.friends = original
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
    
    /**
     * 赠送体力给好友（R5-I2 补跨日重置）。
     */
    suspend fun giftStamina(friendId: String): WriteOutcome {
        val socialData = getSocialData()
        val today = core.today()

        // 检查今日赠送次数（跨日视为 0 次，重置将在 mutate 内落地）
        val effectiveGifted = if (socialData.lastRefreshTime != today) emptyList() else socialData.giftedFriends
        if (effectiveGifted.size >= SocialSaveData.DAILY_GIFT_LIMIT) {
            return WriteOutcome.Rejected
        }
        
        // 检查是否已赠送
        if (effectiveGifted.contains(friendId)) {
            return WriteOutcome.Rejected
        }
        
        val original = socialData.giftedFriends.toList()
        val originalLastRefresh = socialData.lastRefreshTime
        val originalReceived = socialData.receivedStamina
        
        return core.transaction(
            tag = "social.giftStamina",
            mutate = {
                // 跨日重置（R5-I2：与本次写同事务原子落盘，赠礼名单 + 已收体力一并清零）
                if (socialData.lastRefreshTime != today) {
                    socialData.lastRefreshTime = today
                    socialData.giftedFriends = emptyList()
                    socialData.receivedStamina = 0
                }
                socialData.giftedFriends = socialData.giftedFriends + friendId
            },
            rollback = {
                socialData.giftedFriends = original
                socialData.lastRefreshTime = originalLastRefresh
                socialData.receivedStamina = originalReceived
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
    
    /**
     * 领取好友赠送的体力。
     */
    suspend fun claimGiftedStamina(): WriteOutcome {
        val socialData = getSocialData()
        
        // 计算可领取的体力（模拟：每个在线好友赠送5体力）
        val onlineFriends = socialData.friends.filterNotNull().count { it.isOnline }
        val staminaToClaim = (onlineFriends * SocialSaveData.STAMINA_PER_GIFT)
            .coerceAtMost(SocialSaveData.MAX_RECEIVED_STAMINA - socialData.receivedStamina)
        
        if (staminaToClaim <= 0) return WriteOutcome.Rejected
        
        val originalReceived = socialData.receivedStamina
        val originalSoft = core.saveData.softCurrency
        
        return core.transaction(
            tag = "social.claimStamina",
            mutate = {
                socialData.receivedStamina += staminaToClaim
                // 体力转换为星尘（简化处理）
                core.addCurrencyDelta(staminaToClaim * 10, 0)
            },
            rollback = {
                socialData.receivedStamina = originalReceived
                core.saveData.softCurrency = originalSoft
            },
            onCommit = {
                core.publishCurrencyChanged()
            },
        )
    }
    
    /**
     * 获取好友申请列表。
     */
    fun getFriendRequests(): List<FriendRequest> {
        return getSocialData().friendRequests.filterNotNull()
    }
    
    /**
     * 处理好友申请。
     */
    suspend fun handleFriendRequest(requestId: String, accept: Boolean): WriteOutcome {
        val socialData = getSocialData()
        val request = socialData.friendRequests.firstOrNull { it?.requestId == requestId }
            ?: return WriteOutcome.Rejected
        
        val originalRequests = socialData.friendRequests.toList()
        val originalFriends = socialData.friends.toList()
        
        return core.transaction(
            tag = "social.handleRequest",
            mutate = {
                // 移除申请
                socialData.friendRequests = socialData.friendRequests.filter { it?.requestId != requestId }
                
                // 如果接受，添加好友
                if (accept && socialData.friends.size < SocialSaveData.MAX_FRIENDS) {
                    socialData.friends = socialData.friends + FriendData(
                        friendId = request.fromId,
                        name = request.fromName,
                        level = 1,
                        lastLoginTime = System.currentTimeMillis(),
                        totalPower = rng.nextInt(1000, 5000),
                        isOnline = true,
                        favoriteCharacter = "char_r_001",
                    )
                }
            },
            rollback = {
                socialData.friendRequests = originalRequests
                socialData.friends = originalFriends
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
    
    // ─────────────────────────── 公会系统 ───────────────────────────
    
    /**
     * 获取公会信息。
     */
    fun getGuildData(): GuildData? {
        return getSocialData().guildData
    }
    
    /**
     * 创建公会。
     */
    suspend fun createGuild(name: String, description: String): WriteOutcome {
        val socialData = getSocialData()
        
        // 检查是否已有公会
        if (socialData.guildData != null) {
            return WriteOutcome.Rejected
        }
        
        // 检查创建消耗
        val createCost = 1000
        if (core.saveData.softCurrency < createCost) {
            return WriteOutcome.Rejected
        }
        
        val originalCurrency = core.saveData.softCurrency
        val originalGuild = socialData.guildData
        val originalContributions = socialData.guildContributions
        
        return core.transaction(
            tag = "guild.create",
            mutate = {
                core.saveData.softCurrency -= createCost
                socialData.guildData = GuildData(
                    guildId = "guild_${System.currentTimeMillis()}",
                    guildName = name,
                    description = description,
                    level = 1,
                    memberCount = 1,
                    maxMembers = 30,
                    totalPower = 0,
                    guildMaster = "player",
                    joinType = JoinType.AUTO,
                    minLevel = 1,
                    createdAt = System.currentTimeMillis(),
                )
                socialData.guildContributions = 0
            },
            rollback = {
                core.saveData.softCurrency = originalCurrency
                socialData.guildData = originalGuild
                socialData.guildContributions = originalContributions
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }
    
    /**
     * 捐献给公会。
     */
    suspend fun donateToGuild(amount: Int): WriteOutcome {
        val socialData = getSocialData()

        if (socialData.guildData == null) return WriteOutcome.Rejected
        if (amount <= 0) return WriteOutcome.Rejected // 负数会让余额校验失效并反向加钱
        if (core.saveData.softCurrency < amount) return WriteOutcome.Rejected
        
        val originalCurrency = core.saveData.softCurrency
        val originalContributions = socialData.guildContributions
        
        return core.transaction(
            tag = "guild.donate",
            mutate = {
                core.saveData.softCurrency -= amount
                socialData.guildContributions += amount / 10
            },
            rollback = {
                core.saveData.softCurrency = originalCurrency
                socialData.guildContributions = originalContributions
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }
    
    /**
     * 获取公会任务列表。
     */
    fun getGuildTasks(): List<GuildTaskState> {
        return listOf(
            GuildTaskState(GuildTaskType.DAILY_LOGIN, 0, 1, false, false),
            GuildTaskState(GuildTaskType.DAILY_DUNGEON, 0, 3, false, false),
            GuildTaskState(GuildTaskType.ARENA_WIN, 0, 3, false, false),
            GuildTaskState(GuildTaskType.GACHA_PULL, 0, 10, false, false),
            GuildTaskState(GuildTaskType.DONATION, 0, 1, false, false),
        )
    }
    
    /**
     * 领取公会任务奖励。
     */
    suspend fun claimGuildTaskReward(taskType: GuildTaskType): WriteOutcome {
        val socialData = getSocialData()
        
        val originalContributions = socialData.guildContributions
        
        return core.transaction(
            tag = "guild.claimReward",
            mutate = {
                socialData.guildContributions += taskType.contributionReward
            },
            rollback = {
                socialData.guildContributions = originalContributions
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }
}
