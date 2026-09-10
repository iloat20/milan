package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 活动运营节奏数据。
 * 
 * 设计：
 * - 活动类型：限时抽卡、限时副本、签到活动、累充活动、联动活动
 * - 活动周期管理：开始/结束时间、每日/每周重置
 * - 活动商店：兑换物品
 */
@Serializable
class EventRhythmSaveData(
    /** 当前活跃的活动列表。 */
    @SerialName("ActiveEvents") var activeEvents: List<GameEvent?> = emptyList(),
    /** 已完成的活动ID列表。 */
    @SerialName("CompletedEvents") var completedEvents: List<String?> = emptyList(),
    /** 活动商店兑换记录（活动ID → 商品ID → 兑换次数）。 */
    @SerialName("ShopRedemptions") var shopRedemptions: Map<String, Map<String, Int>> = emptyMap(),
    /** 活动任务进度（活动ID → 任务ID → 进度）。 */
    @SerialName("EventTaskProgress") var eventTaskProgress: Map<String, Map<String, Int>?> = emptyMap(),
    /** 已领取的活动任务奖励（活动ID → 任务ID 集合）。防重复领取。 */
    @SerialName("ClaimedTaskRewards") var claimedTaskRewards: Map<String, List<String>> = emptyMap(),
    /** 签到活动进度（活动ID → 已签到天数）。 */
    @SerialName("SignInProgress") var signInProgress: Map<String, Int?> = emptyMap(),
    /** 最后每日重置时间。 */
    @SerialName("LastDailyReset") var lastDailyReset: Long = 0,
    /** 最后每周重置时间。 */
    @SerialName("LastWeeklyReset") var lastWeeklyReset: Long = 0,
    /**
     * 活动代币余额（R5-C1 补）：[EventCurrencyType] 枚举名 → 余额。
     *
     * 此前三种活动代币（EVENT_CURRENCY / ACTIVITY_POINTS / COLLABORATION_TOKENS）
     * 有枚举定义却**无处存储**：任务奖励把代币当星尘发，商店扣款只扣星尘，
     * 活动商店因此退化为「星尘商店」，而物品定价（五星装备 3000）是按代币规模设计的。
     * 补此字段后收支两侧才有统一的记账对象。
     *
     * 默认空 Map 保证旧存档反序列化兼容（缺键 → 空余额），无需数据迁移。
     */
    @SerialName("EventCurrencyBalances") var eventCurrencyBalances: Map<String, Int> = emptyMap(),
) {
    companion object {
        /** 最大同时活跃活动数 */
        const val MAX_ACTIVE_EVENTS = 10
        
        /** 活动商店单物品兑换上限 */
        const val MAX_SHOP_REDEMPTION_PER_ITEM = 10
    }
}

/**
 * 游戏活动。
 */
@Serializable
data class GameEvent(
    @SerialName("EventId") val eventId: String = "",
    @SerialName("EventType") val eventType: String = "LIMITED_GACHA",
    @SerialName("Name") val name: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("StartTime") val startTime: Long = 0,
    @SerialName("EndTime") val endTime: Long = 0,
    @SerialName("BannerCharacterId") val bannerCharacterId: String = "",
    @SerialName("BannerType") val bannerType: String = "SINGLE_UP",
    @SerialName("Tasks") val tasks: List<EventTask?> = emptyList(),
    @SerialName("ShopItems") val shopItems: List<EventShopItem?> = emptyList(),
    @SerialName("SignInDays") val signInDays: Int = 0,
    @SerialName("IsActive") var isActive: Boolean = true,
)

/**
 * 活动任务。
 */
@Serializable
data class EventTask(
    @SerialName("TaskId") val taskId: String = "",
    @SerialName("TaskType") val taskType: String = "DAILY",
    @SerialName("Name") val name: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Target") val target: Int = 1,
    @SerialName("RewardType") val rewardType: String = "HARD_CURRENCY",
    @SerialName("RewardAmount") val rewardAmount: Int = 100,
    @SerialName("IsCompleted") val isCompleted: Boolean = false,
    @SerialName("IsClaimed") val isClaimed: Boolean = false,
)

/**
 * 活动商店物品。
 */
@Serializable
data class EventShopItem(
    @SerialName("ItemId") val itemId: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Price") val price: Int = 100,
    @SerialName("CurrencyType") val currencyType: String = "EVENT_CURRENCY",
    @SerialName("MaxRedemptions") val maxRedemptions: Int = 10,
    @SerialName("CurrentRedemptions") val currentRedemptions: Int = 0,
)

/**
 * 活动类型枚举。
 */
enum class EventType(val displayName: String) {
    LIMITED_GACHA("限时抽卡"),      // 限定卡池
    LIMITED_DUNGEON("限时副本"),    // 限时挑战副本
    SIGN_IN("签到活动"),            // 每日签到
    ACCUMULATE_RECHARGE("累充活动"), // 累计充值
    COLLABORATION("联动活动"),      // IP联动
    SEASON_ACTIVITY("赛季活动"),    // 赛季主题活动
}

/**
 * 活动货币类型。
 */
enum class EventCurrencyType(val displayName: String) {
    EVENT_CURRENCY("活动代币"),
    ACTIVITY_POINTS("活动积分"),
    COLLABORATION_TOKENS("联动代币"),
}

/**
 * 活动定义（供后台配置用）。
 */
data class EventDefinition(
    val eventType: EventType,
    val name: String,
    val description: String,
    val durationDays: Int,
    val repeatable: Boolean = false,
    val requiredLevel: Int = 1,
    val tasks: List<EventTaskDefinition>,
    val shopItems: List<EventShopItemDefinition>,
    /**
     * 签到活动可签天数（R5-C2 补）。签到类活动填正数，其余类型填 0。
     *
     * 原模型缺此字段，实例化 [GameEvent] 时只能借 `durationDays` 顶替 [GameEvent.signInDays]——
     * 两者语义不同（活动持续天数 vs 可签到天数），一旦未来出现「持续 14 天但只签 7 天」
     * 的活动就会发错奖励次数。
     */
    val signInDays: Int = 0,
)

data class EventTaskDefinition(
    val taskType: String,
    val name: String,
    val target: Int,
    val rewardType: String,
    val rewardAmount: Int,
)

data class EventShopItemDefinition(
    val itemId: String,
    val name: String,
    val price: Int,
    val maxRedemptions: Int,
    /**
     * 计价货币类型（R5-C1 补）。
     *
     * 原模型缺此字段，导致「定义 → [EventShopItem]」实例化时只能硬编码 `"EVENT_CURRENCY"`，
     * 而扣款侧却一律扣星尘——活动商店定价（五星装备 3000）是按活动代币规模设计的，
     * 换成星尘后性价比严重失衡。补充此字段后定义与实例语义对齐。
     */
    val currencyType: String = EventCurrencyType.EVENT_CURRENCY.name,
)
