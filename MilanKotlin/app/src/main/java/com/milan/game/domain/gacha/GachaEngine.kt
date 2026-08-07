package com.milan.game.domain.gacha

import com.milan.game.data.Rarity
import java.util.Random

/**
 * 加权随机抽卡引擎（C# Milan.Domain.Gacha.GachaEngine 翻译）。
 *
 * 随机源可注入（[Random] 带种子便于确定性测试），默认无种子。
 */
class GachaEngine(private val rng: Random = Random()) {

    /**
     * 按稀有度权重掷稀有度。权重数组下标与 Rarity 顺序一一对应
     * （i=0 → R(1)，i=1 → SR(2)，i=2 → SSR(3)，i=3 → UR(4)）。
     * 权重和为 0 时回退 R（对齐 C#：避免 Next(0) 抛异常）。
     */
    fun rollRarity(rarityWeights: IntArray): Rarity {
        val total = rarityWeights.sum()
        if (total <= 0) return Rarity.R
        val roll = rng.nextInt(total)
        var cumulative = 0
        for (i in rarityWeights.indices) {
            cumulative += rarityWeights[i]
            if (roll < cumulative) return Rarity.entries[i]
        }
        return Rarity.UR
    }

    /** 按权重从 id 列表中抽取一个；空列表/空权重返回 null，权重和为 0 返回首个 id。 */
    fun pickWeighted(ids: List<String>, weights: List<Int>): String? {
        if (ids.isEmpty() || weights.isEmpty()) return null
        val n = minOf(ids.size, weights.size)
        val total = weights.take(n).sum()
        if (total <= 0) return ids[0]
        val roll = rng.nextInt(total)
        var cumulative = 0
        for (i in 0 until n) {
            cumulative += weights[i]
            if (roll < cumulative) return ids[i]
        }
        return ids[n - 1]
    }
}
