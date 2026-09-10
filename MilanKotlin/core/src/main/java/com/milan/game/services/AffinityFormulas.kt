package com.milan.game.services

/**
 * 角色好感度数值单一事实来源（2026-09-02 收敛）。
 *
 * 此前「每级 1000 经验 / 10 级封顶 / 奖励等级 1·3·5·8·10」全部硬编码在
 * AffinityScreen 的 UI 内，且服务层 addCharacterAffinity 无任何上限语义。
 *
 * 放置说明：好感数据存于 app 侧存档（SaveData.characterAffinityData，Map<characterId, exp>），
 * 不在 :shared 领域层——故常量定义在 app 服务层而非 EconomyFormulas（后者只管 shared 领域数值）。
 *
 * 数值口径（2026-09-02 产品拍板）：
 * - 赠送礼物：消耗 100 星尘 → +200 好感（满级 10000 = 50 次 = 5000 星尘/角色）
 * - 战斗胜利：出战编队全员 +20/场
 * - 剧情选择：由内容数据 StoryChoice.affinityBonus 决定（逐条配置）
 */
object AffinityFormulas {
    /** 每级所需好感经验。 */
    const val EXP_PER_LEVEL = 1000

    /** 好感等级上限（UI 奖励档位 1/3/5/8/10 级均不超此值）。 */
    const val MAX_LEVEL = 10

    /** 好感经验上限（MAX_LEVEL × EXP_PER_LEVEL）。 */
    const val MAX_AFFINITY = MAX_LEVEL * EXP_PER_LEVEL

    /** 赠送一次礼物的星尘消耗。 */
    const val GIFT_COST_SOFT = 100

    /** 赠送一次礼物的好感收益。 */
    const val GIFT_AFFINITY_AMOUNT = 200

    /** 战斗胜利给出战编队全员的好感收益（每场）。 */
    const val BATTLE_WIN_AFFINITY = 20

    /** 好感等级（0 起，封顶 MAX_LEVEL）。 */
    fun levelOf(affinity: Int): Int = (affinity / EXP_PER_LEVEL).coerceAtMost(MAX_LEVEL)

    /** 当前等级内经验进度。 */
    fun expInLevel(affinity: Int): Int = affinity % EXP_PER_LEVEL

    // ─────────────────── 等级奖励（2026-09-10 从「规划中」落地）───────────────────

    /** 好感等级奖励类型。当前版本只发可落盘的经济资源；语音/皮肤等视觉奖励待美术接入。 */
    enum class RewardKind { SOFT, HARD, FRAGMENT }

    /** 单档好感等级奖励。 */
    data class LevelReward(
        val level: Int,
        val kind: RewardKind,
        val amount: Int,
        val label: String,
    )

    /**
     * 好感等级奖励档位（产品拍板 1/3/5/8/10）。
     *
     * 数值口径与 EconomyFormulas 同尺度：满级累计约 1.2 万星尘 + 350 星玉 + 10 碎片，
     * 约等于 2 次十连的星玉回报，作为长期培养的保底收益，不与抽卡经济抢主轴。
     */
    val LEVEL_REWARDS: List<LevelReward> = listOf(
        LevelReward(1, RewardKind.SOFT, 2_000, "星尘 ×2000"),
        LevelReward(3, RewardKind.HARD, 50, "星玉 ×50"),
        LevelReward(5, RewardKind.FRAGMENT, 10, "星魂碎片 ×10"),
        LevelReward(8, RewardKind.HARD, 100, "星玉 ×100"),
        LevelReward(10, RewardKind.HARD, 200, "星玉 ×200"),
    )
}
