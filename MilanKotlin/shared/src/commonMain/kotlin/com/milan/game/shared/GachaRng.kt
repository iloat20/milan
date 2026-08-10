package com.milan.game.shared

import kotlin.random.Random

/**
 * 纯 Kotlin 抽卡数学（commonMain，跨平台复用）。
 * 从 C# GachaEngine 的概率内核移植，仅依赖 kotlin.random，无任何 android.*。
 * 桌面 / Android / 将来 iOS 共用同一份逻辑，验证领域层纯净性。
 */
object GachaRng {
    /** 按权重抽取索引；seed 决定结果，保证可复现（与 GameService.pull 一致）。 */
    fun weightedPick(weights: List<Int>, rng: Random): Int {
        val total = weights.sum()
        if (total <= 0) return 0
        var roll = rng.nextInt(total)
        weights.forEachIndexed { i, w ->
            if (roll < w) return i
            roll -= w
        }
        return weights.lastIndex
    }

    /** 保底计数：达到 hardPity 必出最高稀有度（与 GachaEngine 同语义）。 */
    fun applyPity(counter: Int, hardPity: Int, topRarityIndex: Int): Int? =
        if (hardPity > 0 && counter + 1 >= hardPity) topRarityIndex else null
}
