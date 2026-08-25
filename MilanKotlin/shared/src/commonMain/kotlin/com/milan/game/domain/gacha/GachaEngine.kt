package com.milan.game.domain.gacha

import com.milan.game.data.Rarity
import kotlin.random.Random

/**
 * 加权随机抽卡引擎（C# Milan.Domain.Gacha.GachaEngine 翻译）。
 *
 * 随机源可注入（[Random] 带种子便于确定性测试），默认无种子。
 * 2026-08 KMP 下沉：自 app 迁入 shared commonMain，随机源由 java.util.Random
 * 统一为 kotlin.random.Random（跨平台纯 Kotlin；与 BattleSimulator 同族，
 * 取代早期试验品 GachaRng 的重复实现）。
 */
class GachaEngine(private val rng: Random = Random.Default) {

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
        val maxIdx = Rarity.entries.lastIndex
        for (i in rarityWeights.indices) {
            cumulative += rarityWeights[i]
            if (roll < cumulative) {
                // 钳制下标：权重数组长度 > 稀有度枚举数时（data.json 漏校验），
                // 溢出的权重统一归到最高稀有度，避免 IndexOutOfBounds 崩溃（#3）。
                return Rarity.entries[i.coerceAtMost(maxIdx)]
            }
        }
        return Rarity.entries[maxIdx]
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

    /**
     * UP 池定轨掷选（2026-08 三期引入，对标原神系 50/50：歪一次后下次必中）。
     *
     * - [featuredId] 为空或不在候选中 → 返回 pickedId=null，调用方回退普通加权抽取，
     *   定轨状态不变（常驻池零开销路径）；
     * - [guaranteed]=true（上次歪了）→ 直接交付 UP 并清除标记；
     * - 否则掷硬币：中 → 交付 UP；歪 → 从非 UP 候选均匀取一个并置 guaranteedNext=true。
     *
     * 确定性：只经注入的 [rng] 消费随机数（nextInt(2) 与 nextInt(others.size)），种子可复现。
     */
    fun pickFeatured(
        candidateIds: List<String>,
        featuredId: String?,
        guaranteed: Boolean,
    ): FeaturedPick {
        if (featuredId.isNullOrEmpty() || candidateIds.isEmpty() || featuredId !in candidateIds) {
            return FeaturedPick(pickedId = null, guaranteedNext = false)
        }
        if (guaranteed) return FeaturedPick(pickedId = featuredId, guaranteedNext = false)
        return if (rng.nextInt(2) == 0) {
            FeaturedPick(pickedId = featuredId, guaranteedNext = false)
        } else {
            val others = candidateIds.filter { it != featuredId }
            if (others.isEmpty()) {
                // 唯一候选即 UP：无从歪起，视作必中（不置标记，避免假「欠 UP」状态）
                FeaturedPick(pickedId = featuredId, guaranteedNext = false)
            } else {
                FeaturedPick(pickedId = others[rng.nextInt(others.size)], guaranteedNext = true)
            }
        }
    }
}

/** [GachaEngine.pickFeatured] 的结果。pickedId=null 表示池无有效 UP（调用方回退普通抽取、状态不变）。 */
data class FeaturedPick(val pickedId: String?, val guaranteedNext: Boolean)
