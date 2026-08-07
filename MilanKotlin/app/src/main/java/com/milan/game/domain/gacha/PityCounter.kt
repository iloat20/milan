package com.milan.game.domain.gacha

import com.milan.game.data.Rarity
import java.util.Random

/**
 * 保底计数器（C# Milan.Domain.Gacha.PityCounter 翻译）。
 *
 * 语义要点：
 * - 保底阈值无效（配置为 0 或负数）→ 不触发保底，按自然概率抽取，否则每抽必触发
 *   会导致卡池恒为保底稀有度、永远抽不到更低配的角色；
 * - 自然抽出保底档及以上稀有度时同样重置计数器（标准保底语义），否则玩家会在
 *   自然出货后紧接着又吃保底，概率被双倍放大。
 */
class PityCounter(val threshold: Int) {
    var counter: Int = 0

    fun rollWithPity(rng: Random, rarityWeights: IntArray, minRarityForPity: Int): Rarity {
        if (threshold <= 0) {
            return GachaEngine(rng).rollRarity(rarityWeights)
        }
        counter++
        if (counter >= threshold) {
            counter = 0
            return Rarity.fromValue(minRarityForPity) ?: Rarity.R
        }
        val rolled = GachaEngine(rng).rollRarity(rarityWeights)
        // 自然抽出保底档及以上 → 重置计数器（对齐 C# (int)rolled >= minRarityForPity）
        if (rolled.value >= minRarityForPity) counter = 0
        return rolled
    }

    fun reset() {
        counter = 0
    }
}
