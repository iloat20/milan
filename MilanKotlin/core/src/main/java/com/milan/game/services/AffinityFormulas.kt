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
}
