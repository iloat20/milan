package com.milan.game.services

import com.milan.game.data.*
import com.milan.game.domain.monetization.MonetizationFormulas
import kotlin.random.Random

/**
 * 变现模型服务。
 * 
 * 职责：
 * - 月卡：激活、每日领取、过期检测
 * - 通行证：经验、等级、奖励领取
 * - 首充/充值：档位、首充双倍、累计充值里程碑
 */
internal class MonetizationService(
    private val core: ServiceCore,
    private val rng: Random = Random.Default,
) {

    /** 获取变现数据。 */
    fun getData(): MonetizationSaveData {
        // R5-I5：懒创建改纯读——默认值由 sanitize/createDefault 保证非 null，不再锁外写存档。
        return core.saveData.monetizationData ?: MonetizationSaveData()
    }

    // ─────────────────── 月卡 ───────────────────

    /** 购买/激活月卡。 */
    suspend fun activateMonthlyCard(cost: Int): WriteOutcome {
        val data = getData()
        if (cost <= 0) return WriteOutcome.Rejected // R5-I11：负数会让余额校验失效并反向加钱
        if (core.saveData.hardCurrency < cost) return WriteOutcome.Rejected

        val origHC = core.saveData.hardCurrency
        val origActive = data.monthlyCardActive
        val origDays = data.monthlyCardDaysLeft

        return core.transaction(
            tag = "monetization.monthlyCard",
            mutate = {
                core.saveData.hardCurrency -= cost
                data.monthlyCardActive = true
                data.monthlyCardDaysLeft = MonetizationSaveData.MONTHLY_CARD_DURATION
                data.monthlyCardLastClaim = System.currentTimeMillis()
            },
            rollback = {
                core.saveData.hardCurrency = origHC
                data.monthlyCardActive = origActive
                data.monthlyCardDaysLeft = origDays
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    /** 领取月卡每日奖励。 */
    suspend fun claimMonthlyCardReward(): WriteOutcome {
        val data = getData()
        if (!data.monthlyCardActive) return WriteOutcome.Rejected
        if (data.monthlyCardDaysLeft <= 0) return WriteOutcome.Rejected

        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        if (now - data.monthlyCardLastClaim < oneDayMs) return WriteOutcome.Rejected // 今日已领

        val origHC = core.saveData.hardCurrency
        val origSC = core.saveData.softCurrency
        val origLastClaim = data.monthlyCardLastClaim
        val origDaysLeft = data.monthlyCardDaysLeft
        val origActive = data.monthlyCardActive

        return core.transaction(
            tag = "monetization.monthlyClaim",
            mutate = {
                core.addCurrencyDelta(0, MonetizationSaveData.MONTHLY_CARD_HARDCURRENCY)
                core.addCurrencyDelta(MonetizationSaveData.MONTHLY_CARD_SOFTCURRENCY, 0)
                data.monthlyCardLastClaim = now
                data.monthlyCardDaysLeft -= 1
                if (data.monthlyCardDaysLeft <= 0) {
                    data.monthlyCardActive = false
                    data.monthlyCardDaysLeft = -1
                }
            },
            rollback = {
                core.saveData.hardCurrency = origHC
                core.saveData.softCurrency = origSC
                data.monthlyCardLastClaim = origLastClaim
                data.monthlyCardDaysLeft = origDaysLeft
                data.monthlyCardActive = origActive
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    /** 月卡剩余天数。 */
    fun getMonthlyCardDaysLeft(): Int = getData().monthlyCardDaysLeft

    // ─────────────────── 通行证 ───────────────────

    /** 购买豪华通行证。 */
    suspend fun purchaseBattlePass(cost: Int): WriteOutcome {
        val data = getData()
        if (cost <= 0) return WriteOutcome.Rejected // R5-I11：负数会让余额校验失效并反向加钱
        if (core.saveData.hardCurrency < cost) return WriteOutcome.Rejected
        if (data.battlePassPremium) return WriteOutcome.Rejected

        val origHC = core.saveData.hardCurrency
        val origPremium = data.battlePassPremium
        val origSeason = data.battlePassSeason

        return core.transaction(
            tag = "monetization.battlePass",
            mutate = {
                core.saveData.hardCurrency -= cost
                data.battlePassPremium = true
                data.battlePassSeason += 1
            },
            rollback = {
                core.saveData.hardCurrency = origHC
                data.battlePassPremium = origPremium
                data.battlePassSeason = origSeason
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    /**
     * 增加通行证经验。
     *
     * @param broadcast 是否独立广播 [ProgressionChanged]。作为抽卡/升级等父操作的
     *   副作用调用时应传 `false`——父操作自身已广播事件并刷新快照，通行证经验作为
     *   子计数器随之刷新；再广播一次会破坏「父操作只发一个事件」的精确计数契约，
     *   并造成冗余的 UI 刷新。仅当直接由门面 [GameService.grantBattlePassExp] 暴露、
     *   作为独立操作调用时才传 `true`。
     */
    suspend fun grantBattlePassExp(amount: Int, broadcast: Boolean): WriteOutcome {
        val data = getData()
        val origLevel = data.battlePassLevel
        val origExp = data.battlePassExp

        return core.transaction(
            tag = "monetization.bpExp",
            mutate = {
                data.battlePassExp += amount
                while (data.battlePassExp >= MonetizationSaveData.BP_EXP_PER_LEVEL
                    && data.battlePassLevel < MonetizationSaveData.BP_MAX_LEVEL
                ) {
                    data.battlePassExp -= MonetizationSaveData.BP_EXP_PER_LEVEL
                    data.battlePassLevel += 1
                }
                if (data.battlePassLevel >= MonetizationSaveData.BP_MAX_LEVEL) {
                    data.battlePassExp = 0
                }
            },
            rollback = {
                data.battlePassLevel = origLevel
                data.battlePassExp = origExp
            },
            onCommit = {
                if (broadcast) {
                    core.publishProgressionChanged()
                } else {
                    core.refreshSnapshot()
                }
            },
        )
    }

    /** 领取通行证等级奖励。 */
    suspend fun claimBattlePassReward(level: Int): WriteOutcome {
        val data = getData()
        if (level < 1) return WriteOutcome.Rejected
        if (level > data.battlePassLevel) return WriteOutcome.Rejected
        if (data.claimedBPRewards.contains(level)) return WriteOutcome.Rejected

        val origClaimed = data.claimedBPRewards.toList()
        val origSoft = core.saveData.softCurrency
        val origHard = core.saveData.hardCurrency

        return core.transaction(
            tag = "monetization.bpClaim",
            mutate = {
                data.claimedBPRewards = data.claimedBPRewards + level
                // 基础奖励（免费轨）—— 数值单一事实来源：[MonetizationFormulas.bpFreeRewardSoft]
                core.addCurrencyDelta(MonetizationFormulas.bpFreeRewardSoft(level), 0)
                // 豪华轨额外奖励
                if (data.battlePassPremium) {
                    core.addCurrencyDelta(0, MonetizationFormulas.bpPremiumRewardHard(level))
                }
            },
            rollback = {
                data.claimedBPRewards = origClaimed
                core.saveData.softCurrency = origSoft
                core.saveData.hardCurrency = origHard
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    /** 获取通行证定义奖励列表。 */
    fun getBattlePassRewards(): List<BattlePassReward> {
        return (1..MonetizationSaveData.BP_MAX_LEVEL).map { level ->
            BattlePassReward(
                level = level,
                freeReward = BPReward(BPRewardType.SOFT_CURRENCY, "soft", MonetizationFormulas.bpFreeRewardSoft(level)),
                premiumReward = if (level % 10 == 0) {
                    BPReward(BPRewardType.HARD_CURRENCY, "hard", MonetizationFormulas.bpPremiumRewardHard(level))
                } else {
                    BPReward(BPRewardType.MATERIAL, "mat通用素材", MonetizationFormulas.bpPremiumRewardMaterial(level))
                },
            )
        }
    }

    // ─────────────────── 首充/充值 ───────────────────

    /** 充值（模拟，写入星琼）。 */
    suspend fun charge(tierId: String, hardCurrency: Int, costCents: Int): WriteOutcome {
        val data = getData()
        val isDouble = !data.firstChargeClaimed.contains(tierId)
        val actualHC = if (isDouble) hardCurrency * 2 else hardCurrency

        val origHC = core.saveData.hardCurrency
        val origFirstCharge = data.firstChargeClaimed.toList()
        val origTotal = data.totalChargeAmount

        return core.transaction(
            tag = "monetization.charge",
            mutate = {
                core.addCurrencyDelta(0, actualHC)
                if (isDouble) {
                    data.firstChargeClaimed = data.firstChargeClaimed + tierId
                }
                data.totalChargeAmount += costCents
            },
            rollback = {
                core.saveData.hardCurrency = origHC
                data.firstChargeClaimed = origFirstCharge
                data.totalChargeAmount = origTotal
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    /** 领取累计充值里程碑奖励。 */
    suspend fun claimChargeMilestone(amountCents: Int): WriteOutcome {
        val data = getData()
        // 档位白名单：只有已定义的里程碑可领取（防按精确值刷 600,599,…,1）
        val milestone = getChargeMilestones().firstOrNull { it.amountCents == amountCents }
            ?: return WriteOutcome.Rejected
        if (data.totalChargeAmount < amountCents) return WriteOutcome.Rejected
        if (data.claimedChargeMilestones.contains(amountCents)) return WriteOutcome.Rejected

        val origClaimed = data.claimedChargeMilestones.toList()
        val origHard = core.saveData.hardCurrency
        val origSoft = core.saveData.softCurrency

        return core.transaction(
            tag = "monetization.milestone",
            mutate = {
                data.claimedChargeMilestones = data.claimedChargeMilestones + amountCents
                // 按里程碑定义的实际奖励类型分发（旧实现一律发硬通货，无视 EQUIPMENT/SKIN/CHARACTER）
                when (milestone.rewardType) {
                    BPRewardType.HARD_CURRENCY -> core.addCurrencyDelta(0, milestone.rewardAmount)
                    BPRewardType.SOFT_CURRENCY -> core.addCurrencyDelta(milestone.rewardAmount, 0)
                    BPRewardType.CHARACTER_EXP -> { /* TODO：角色经验/道具/角色/装备/皮肤发放需物品系统支持 */ }
                    else -> { /* 其余类型（EQUIPMENT/SKIN/CHARACTER 等）待物品系统接入后分发 */ }
                }
            },
            rollback = {
                data.claimedChargeMilestones = origClaimed
                core.saveData.hardCurrency = origHard
                core.saveData.softCurrency = origSoft
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    /** 获取充值档位列表。 */
    fun getChargeTiers(): List<ChargeTier> = listOf(
        ChargeTier("tier_6", "6元", 600, 60, 0, 60, false),
        ChargeTier("tier_30", "30元", 3000, 300, 0, 300, false),
        ChargeTier("tier_68", "68元", 6800, 680, 0, 680, true),
        ChargeTier("tier_128", "128元", 12800, 1280, 0, 1280, false),
        ChargeTier("tier_328", "328元", 32800, 3280, 0, 3280, true),
        ChargeTier("tier_648", "648元", 64800, 6480, 0, 6480, true),
    )

    /** 获取累计充值里程碑。 */
    fun getChargeMilestones(): List<ChargeMilestone> = listOf(
        ChargeMilestone(600, BPRewardType.HARD_CURRENCY, "hard", 60, "累计充值6元"),
        ChargeMilestone(3000, BPRewardType.HARD_CURRENCY, "hard", 300, "累计充值30元"),
        ChargeMilestone(9800, BPRewardType.CHARACTER_EXP, "exp", 50000, "累计充值98元"),
        ChargeMilestone(19800, BPRewardType.EQUIPMENT, "eq_五星装备自选", 1, "累计充值198元"),
        ChargeMilestone(32800, BPRewardType.SKIN, "skin_限定", 1, "累计充值328元"),
        ChargeMilestone(64800, BPRewardType.CHARACTER, "char_限定SSR", 1, "累计充值648元"),
    )
}
