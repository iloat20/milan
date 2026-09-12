package com.milan.game.domain.progression

/**
 * 养成经济公式的**单一事实来源**：等级上限、升级/突破/升星消耗、经验换算、重复补偿。
 * （C# Milan.Domain.Progression.EconomyFormulas 翻译）
 *
 * 铁律：任何一处需要"这次升级要花多少"的地方，都必须调用这里，禁止就地写数字。
 *
 * 2026-08 KMP 下沉：自 app 迁入 shared commonMain，跨端（Android / 桌面 / 将来 iOS）
 * 强制共享同一份数值——桌面模拟器与 App 定价不可能再漂移。
 */
object EconomyFormulas {

    /** 等级上限随突破阶段提高：Stage×20（Stage1→20 级，Stage4→80 级）。 */
    fun maxLevelForStage(stage: Int): Int = stage.coerceAtLeast(1) * 20

    /** 从 level 升到 level+1 的星尘消耗（随等级线性上升）。 */
    fun levelCost(level: Int): Int = level.coerceAtLeast(1) * 50

    /** stage→stage+1 突破所需星魂碎片（重复角色补偿货币）。 */
    fun ascendFragments(stage: Int): Int = stage.coerceAtLeast(1) * 20

    /** stage→stage+1 突破所需星尘。 */
    fun ascendSoft(stage: Int): Int = stage.coerceAtLeast(1) * 500

    /** stars→stars+1 升星所需星魂碎片（1★→2★ 耗 20，2★→3★ 耗 40…）。 */
    fun starUpFragments(stars: Int): Int = stars.coerceAtLeast(1) * 20

    /** 本等级内升到下一级所需的经验（与 [ProgressionEngine.expToLevel] 同一口径：level×100）。 */
    fun expForLevel(level: Int): Int = level.coerceAtLeast(1) * 100

    /** 升到 level 级所需的累计经验（用于经验条定位）。level≤1 时为 0。 */
    fun cumulativeExp(level: Int): Int {
        val lv = level.coerceAtLeast(1)
        // Σ(k×100), k=1..lv-1 —— 与 expForLevel 保持同一递增口径。
        return 100 * (lv - 1) * lv / 2
    }

    /** 重复角色按稀有度补偿的星魂碎片数量（UR 50 / SSR 20 / SR 5 / R 1）。未知值回退最低补偿。 */
    fun fragmentsForRarity(rarity: Int): Int = when (rarity) {
        4 -> 50 // UR
        3 -> 20 // SSR
        2 -> 5  // SR
        else -> 1 // R（含未知稀有度兜底，绝不抛异常中断抽卡事务）
    }

    /**
     * 计算在给定星尘预算下最多能连升几级，以及总花费。
     * 纯函数：批量升级（×5 / 升满）的边界行为（预算不足只升部分、已满级返回 0）在此锁死。
     *
     * @param currentLevel 当前等级
     * @param maxLevel 该突破阶段的等级上限
     * @param budget 可用星尘
     * @param requested 期望升的级数（≤0 视为 0）
     * @return 实际可升级数与总花费；一级都升不了时为 (0, 0)
     */
    fun planLevelUp(currentLevel: Int, maxLevel: Int, budget: Int, requested: Int): PlanResult {
        if (requested <= 0) return PlanResult(0, 0)
        var target = currentLevel
        var cost = 0
        for (i in 0 until requested) {
            if (target >= maxLevel) break
            val c = levelCost(target)
            if (budget < cost + c) break
            cost += c
            target++
        }
        return PlanResult(target - currentLevel, cost)
    }

    // ── 商店定价（同样遵守单一事实来源：商店结算也走这里，禁止就地写数字）──

    /** 商店碎片包档位：1=小包（10 片），2=大包（60 片）。非法档位返回 0（无该档）。 */
    fun fragmentPackSize(pack: Int): Int = when (pack) {
        1 -> 10
        2 -> 60
        else -> 0
    }

    /** 商店碎片包定价（星尘）：小包 1000（100/片），大包 5500（≈92/片，批量优惠）。非法档位返回 0。 */
    fun fragmentPackCost(pack: Int): Int = when (pack) {
        1 -> 1000
        2 -> 5500
        else -> 0
    }

    /** 钻石兑换星尘：单次兑换消耗的钻石数。 */
    fun diamondExchangeCost(): Int = 100

    /** 钻石兑换星尘：单次兑换获得的星尘数。 */
    fun diamondExchangeYield(): Int = 20000

    // ── 碎片兑换（2026-08 三期：星魂碎片→星尘回收阀门；回收单价低于商店购入价，双向流通必有损耗防套利）──

    /** 单次兑换消耗的星魂碎片批量。非法（≤0）由调用方视为无效档位拒绝。 */
    fun fragmentExchangeBatch(): Int = 10

    /** 单次兑换获得的星尘（10 片 → 800 ✦，即 80/片；商店购入价 100/片）。 */
    fun fragmentExchangeYield(): Int = 800

    // ── 软保底爬坡（2026-08 优化引入：接近硬保底时概率逐抽上升，对标原神系口径）──

    /** 软保底起始抽数占硬保底的千分比：90 抽 → 73 抽起爬坡（≈74，与行业惯例一致）。 */
    private const val SOFT_PITY_START_PERMILLE = 820

    /**
     * 软保底起始抽数：计数达到该值起逐抽上调保底档权重。
     * hardPity≤0（未启用保底）返回 0 = 无软保底。
     */
    fun softPityStart(hardPity: Int): Int =
        if (hardPity <= 0) 0 else hardPity * SOFT_PITY_START_PERMILLE / 1000

    /** 软保底每抽给保底档权重叠加的增量（线性爬坡：第 start 抽 +60，第 start+1 抽 +120…）。 */
    fun softPityRampStep(): Int = 60

    // ── 无尽之塔（2026-08 优化引入）──

    /**
     * 第 [floor] 层敌人的属性缩放倍率：1 + (floor−1)×15%。
     * 每层 +15% 全属性——保证「练度提升可多推几层」的正反馈曲线。
     */
    fun towerEnemyStatScale(floor: Int): Double = 1.0 + (floor.coerceAtLeast(1) - 1) * 0.15

    /** 第 [floor] 层敌人数：1 + floor/10，封顶 5（与编队槽位同宽）。 */
    fun towerEnemyCount(floor: Int): Int = (1 + floor.coerceAtLeast(1) / 10).coerceAtMost(5)

    /**
     * 深渊敌人数值相对塔层的额外倍率（2026-09-12 Dungeon keep）。
     * 深渊同层更难：属性 ×1.25，人数 +1（仍受 5 封顶），种子盐不同保证元素分布可复现且与塔错开。
     */
    const val ABYSS_ENEMY_STAT_SCALE_MUL = 1.25

    /** 统一层敌人数：塔走 [towerEnemyCount]；深渊 +1 后仍封顶 5。 */
    fun floorEnemyCount(floor: Int, isAbyss: Boolean): Int {
        val base = towerEnemyCount(floor)
        return if (isAbyss) (base + 1).coerceAtMost(5) else base
    }

    /** 统一层敌人属性缩放：塔走 [towerEnemyStatScale]；深渊再乘 [ABYSS_ENEMY_STAT_SCALE_MUL]。 */
    fun floorEnemyStatScale(floor: Int, isAbyss: Boolean): Double {
        val base = towerEnemyStatScale(floor)
        return if (isAbyss) base * ABYSS_ENEMY_STAT_SCALE_MUL else base
    }

    /**
     * 爬塔敌人基础属性模板 [atk, def, hp, spd]（未经层数缩放；缩放走 [towerEnemyStatScale]）。
     * 基准对齐 StatsCalculator 的角色兜底值量级（100/80/1000/12），略压攻防让首层可平推。
     */
    fun towerEnemyBaseStats(): List<Int> = listOf(100, 60, 1200, 12)

    /** 通关第 [floor] 层的星尘奖励：500×floor+500（首层 1000，线性增长）。 */
    fun towerRewardSoft(floor: Int): Int = 500 * floor.coerceAtLeast(1) + 500

    /**
     * 爬塔里程碑钻石奖励（2026-08 引入，钻石唯一稳定产出口径）：
     * 仅「首次攻克」5 的倍数层发放 floor×2 颗（第5层10、第10层20…），非里程碑层返回 0。
     * 调用方必须以 newBest 推进为前提（复刷已通层不发）——本函数只算数值不管语义。
     */
    fun towerRewardHard(floor: Int): Int {
        val f = floor.coerceAtLeast(1)
        return if (f % 5 == 0) f * 2 else 0
    }

    /** 成就奖励的钻石档位参考：普通成就 30 / 进阶 60 / 里程碑 100（与 [towerRewardHard] 同一量级）。 */
    fun achievementRewardHard(tier: Int): Int = when (tier.coerceIn(1, 3)) {
        3 -> 100
        2 -> 60
        else -> 30
    }

    /**
     * 无尽之塔的层数上界（M6，2026-08-28 审查引入）：服务层防御性护栏。
     * 超过该层号后 [towerRewardSoft] 与 [towerEnemyStatScale] 的数值量级会脱离设计区间
     * （并存在 Int 溢出敞口），故在服务层显式拒绝而非静默产出异常数值。
     */
    fun towerMaxFloor(): Int = 999

    /**
     * 通关第 [floor] 层给出战编队发放的经验（2026-08-28 F4 引入：经验条的唯一驱动源）。
     * 50×floor+50 —— 首层 100 点，恰好等于 [expForLevel](1)，即首次通关升 1 级。
     * 与 [towerRewardSoft] 不同，经验在**每次胜利**都发（含复刷），星尘只在刷新纪录时发。
     */
    fun towerRewardExp(floor: Int): Int = 50 * floor.coerceAtLeast(1) + 50

    /** 通关任意层的战票（BATTLE_TICKET 道具）奖励数量。胜利返 1 张（净消耗 0），亏损局才是真消耗。 */
    fun towerRewardTickets(): Int = 1

    /** 挑战任意层的战票门槛：入场扣 [towerTicketCost] 张，票不足拒绝进入。 */
    fun towerTicketCost(): Int = 1

    // ── 每日商店（2026-08 优化引入：日期种子确定性轮换，跨端同日同价）──

    /** 每日特惠槽位数（免费补给 / 折扣碎片包 / 战票礼包）。 */
    fun dailyOfferSlots(): Int = 3

    /** 每日折扣包的折扣千分比（80‰ = 8 折）。 */
    fun dailyDiscountPermille(): Int = 800

    /**
     * 每日折扣碎片包售价：原价 × [dailyDiscountPermille] / 1000。
     * pack 非法时返回 0（调用方视为无效档位）。
     */
    fun dailyDiscountPackCost(pack: Int): Int = fragmentPackCost(pack) * dailyDiscountPermille() / 1000

    /** 每日免费补给发放的星尘。 */
    fun dailyFreeSupplySoft(): Int = 2000

    /** 每日免费补给发放的战票数（零票玩家的启动来源，避免「没票永远打不了塔」死局）。 */
    fun dailyTicketGrant(): Int = 3

    /** 每日战票礼包张数。 */
    fun dailyTicketBundleSize(): Int = 5

    /** 每日战票礼包售价（星尘）。 */
    fun dailyTicketBundleCost(): Int = 2500
}

/** [EconomyFormulas.planLevelUp] 的结果。 */
data class PlanResult(val gained: Int, val cost: Int)
