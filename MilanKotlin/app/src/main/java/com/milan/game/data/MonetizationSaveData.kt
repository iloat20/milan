package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 变现模型数据（月卡、通行证）。
 * 
 * 设计：
 * - 月卡：每日领取星琼+星尘，持续30天
 * - 通行证：等级奖励系统，免费/付费双轨
 * - 首充双倍：仅一次
 * - 累计充值奖励：里程碑式
 */
@Serializable
class MonetizationSaveData(
    // ── 月卡 ──
    /** 月卡是否激活。 */
    @SerialName("MonthlyCardActive") var monthlyCardActive: Boolean = false,
    /** 月卡剩余天数（-1=无月卡）。 */
    @SerialName("MonthlyCardDaysLeft") var monthlyCardDaysLeft: Int = -1,
    /** 月卡上次领取时间戳。 */
    @SerialName("MonthlyCardLastClaim") var monthlyCardLastClaim: Long = 0,
    
    // ── 通行证 ──
    /** 通行证是否购买了豪华版。 */
    @SerialName("BattlePassPremium") var battlePassPremium: Boolean = false,
    /** 当前通行证等级。 */
    @SerialName("BattlePassLevel") var battlePassLevel: Int = 1,
    /** 当前通行证经验。 */
    @SerialName("BattlePassExp") var battlePassExp: Int = 0,
    /** 已领取的通行证奖励等级列表。 */
    @SerialName("ClaimedBPRewards") var claimedBPRewards: List<Int?> = emptyList(),
    /** 当前通行证赛季（0=从未购买）。 */
    @SerialName("BattlePassSeason") var battlePassSeason: Int = 0,
    
    // ── 首充/充值 ──
    /** 已首充的充值档位。 */
    @SerialName("FirstChargeClaimed") var firstChargeClaimed: List<String?> = emptyList(),
    /** 累计充值金额（分）。 */
    @SerialName("TotalChargeAmount") var totalChargeAmount: Int = 0,
    /** 已领取的累计充值奖励里程碑。 */
    @SerialName("ClaimedChargeMilestones") var claimedChargeMilestones: List<Int?> = emptyList(),
    
    // ── 限时礼包 ──
    /** 已购买的限时礼包ID。 */
    @SerialName("PurchasedTimeLimited") var purchasedTimeLimited: List<String?> = emptyList(),
) {
    companion object {
        /** 月卡持续天数 */
        const val MONTHLY_CARD_DURATION = 30
        
        /** 月卡每日奖励：星琼 */
        const val MONTHLY_CARD_HARDCURRENCY = 300
        
        /** 月卡每日奖励：星尘 */
        const val MONTHLY_CARD_SOFTCURRENCY = 50000
        
        /** 通行证每级所需经验 */
        const val BP_EXP_PER_LEVEL = 1000
        
        /** 通行证最高等级 */
        const val BP_MAX_LEVEL = 50
        
        /** 通行证每日任务经验 */
        const val BP_DAILY_TASK_EXP = 200
        
        /** 通行证每周任务经验 */
        const val BP_WEEKLY_TASK_EXP = 1000
    }
}

/**
 * 通行证奖励定义。
 */
data class BattlePassReward(
    val level: Int,
    val freeReward: BPReward?,
    val premiumReward: BPReward?,
)

/**
 * 通行证奖励内容。
 */
data class BPReward(
    val type: BPRewardType,
    val itemId: String,
    val amount: Int,
)

/**
 * 通行证奖励类型。
 */
enum class BPRewardType {
    SOFT_CURRENCY,  // 星尘
    HARD_CURRENCY,  // 星琼
    CHARACTER,      // 角色
    CHARACTER_EXP,  // 角色经验
    MATERIAL,       // 素材
    EQUIPMENT,      // 装备
    SKIN,           // 皮肤
    TITLE,          // 称号
    PORTRAIT,       // 头像
}

/**
 * 充值档位定义。
 */
data class ChargeTier(
    val tierId: String,
    val name: String,
    val priceCents: Int,     // 价格（分）
    val hardCurrency: Int,   // 星琼数量
    val softCurrency: Int,   // 星尘数量
    val firstChargeBonus: Int, // 首充双倍星琼
    val isHot: Boolean = false,
)

/**
 * 累计充值里程碑。
 */
data class ChargeMilestone(
    val amountCents: Int,
    val rewardType: BPRewardType,
    val rewardItemId: String,
    val rewardAmount: Int,
    val description: String,
)

/**
 * 限时礼包定义。
 */
data class TimeLimitedPack(
    val packId: String,
    val name: String,
    val description: String,
    val priceCents: Int,
    val hardCurrency: Int,
    val items: List<BPReward>,
    val expireTime: Long,
    val maxPurchases: Int = 1,
)
