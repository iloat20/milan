package com.milan.game.domain.gacha

import com.milan.game.data.Rarity
import com.milan.game.domain.progression.EconomyFormulas
import kotlin.random.Random

/**
 * 保底计数器（C# Milan.Domain.Gacha.PityCounter 翻译）。
 *
 * 语义要点：
 * - 保底阈值无效（配置为 0 或负数）→ 不触发保底，按自然概率抽取，否则每抽必触发
 *   会导致卡池恒为保底稀有度、永远抽不到更低配的角色；
 * - 自然抽出保底档及以上稀有度时同样重置计数器（标准保底语义），否则玩家会在
 *   自然出货后紧接着又吃保底，概率被双倍放大。
 * - **P1-3（2026-08）**：自然出货的保底重置不在掷骰阶段判定，而是由调用方在
 *   「实际交付档位确定后」调用 [onNaturalPityOrAbove] 判定——若掷出保底档但该档
 *   在卡池中无候选、产出被 resolveRarityWithCandidates 降档，提前重置会把 90 抽
 *   保底白白消耗在低稀有度产出上。
 *
 * 2026-08 KMP 下沉：自 app 迁入 shared commonMain，随机源统一为 kotlin.random.Random。
 */
class PityCounter(val threshold: Int) {
    var counter: Int = 0

    /**
     * 保底掷稀有度。
     *
     * @param minRarityForPity 保底目标稀有度（[Rarity] 类型，直接传 [Rarity.SSR] 等具名值），
     *   不是 [Rarity.entries] 的下标（下标 3 是 UR）。与 [GachaEngine.rollRarity]
     *   的 entries 下标语义不同——两者在值 0..3 内恰好重合，类型化后调用方不会再误传下标。
     */
    fun rollWithPity(rng: Random, rarityWeights: IntArray, minRarityForPity: Rarity): Rarity {
        if (threshold <= 0) {
            return GachaEngine(rng).rollRarity(rarityWeights)
        }
        counter++
        if (counter >= threshold) {
            counter = 0
            return minRarityForPity
        }
        // 软保底（2026-08 优化）：进入软保底区间后逐抽上调保底档权重，概率平滑爬坡
        // （对标原神系 soft pity 的行业标准体验）。只调整本抽使用的权重副本、不改池配置，
        // 且不额外消耗随机数——种子确定性保持不变。
        val weights = softAdjustedWeights(rarityWeights, minRarityForPity)
        return GachaEngine(rng).rollRarity(weights)
    }

    /**
     * 按当前计数生成软保底调整后的权重副本（纯函数、不改入参；便于单测锁定边界）：
     * counter ≥ [EconomyFormulas.softPityStart] 时，保底档下标权重 +=
     * (counter − start + 1) × [EconomyFormulas.softPityRampStep]；
     * 未达起点 / 未启用保底 / 权重数组越界时返回原数组引用（只读安全，不拷贝）。
     */
    fun softAdjustedWeights(rarityWeights: IntArray, minRarityForPity: Rarity): IntArray {
        val start = EconomyFormulas.softPityStart(threshold)
        if (start <= 0 || counter < start) return rarityWeights
        val idx = minRarityForPity.value - 1
        if (idx < 0 || idx >= rarityWeights.size) return rarityWeights
        return rarityWeights.copyOf().also {
            it[idx] += (counter - start + 1) * EconomyFormulas.softPityRampStep()
        }
    }

    /**
     * 自然出货达到保底档时的重置判定（P1-3 修复）：
     * 调用方在完成「掷稀有度 → 按候选升/降档」之后，以**实际交付**档位判定——
     * 只有交付档位 ≥ 保底档才重置计数；掷出保底档但被降档时不重置，保底进度保留。
     */
    fun onNaturalPityOrAbove(deliveredRarity: Rarity, minRarityForPity: Rarity) {
        if (deliveredRarity.value >= minRarityForPity.value) counter = 0
    }

    fun reset() {
        counter = 0
    }
}
