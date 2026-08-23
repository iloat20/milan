package com.milan.game.desktop

import com.milan.game.data.Rarity
import com.milan.game.domain.gacha.GachaEngine
import com.milan.game.domain.gacha.PityCounter
import com.milan.game.domain.progression.EconomyFormulas
import kotlin.random.Random

/**
 * 桌面抽卡模拟器（Compose Multiplatform 方向的最小验证，2026-08 KMP 下沉后升级）：
 * 直接复用 :shared 的**真实**抽卡引擎（GachaEngine/PityCounter）与养成经济公式
 * （EconomyFormulas）——与 Android App 同一份实现，彻底消除"演示用近似数学"漂移
 * （旧 GachaRng 试验品已删除，见 git 历史）。
 *
 * 运行：./gradlew :desktopApp:run
 */
fun main() {
    // pool_main 同构权重：R/SR/SSR/UR（data.json 默认池）
    val weights = intArrayOf(400, 300, 200, 100)
    val names = arrayOf("R", "SR", "SSR", "UR")
    val engine = GachaEngine(Random(20260813L))

    println("=== Milan 桌面抽卡模拟器（复用 :shared 真实抽卡引擎）===")
    // 保底 90 抽，自然出货/硬保底语义与 App 内完全一致
    val pity = PityCounter(threshold = 90)
    repeat(10) { i ->
        val rarity = pity.rollWithPity(Random(20260813L + i), weights, minRarityForPity = Rarity.SSR)
        println("第 ${i + 1} 抽 -> ${names[rarity.value - 1]}（保底计数 ${pity.counter}）")
    }

    // 养成经济公式（跨端单一事实来源）
    println("\n=== 养成经济（EconomyFormulas，桌面与 App 同源）===")
    println("Stage1 等级上限 ${EconomyFormulas.maxLevelForStage(1)} 级")
    println("SSR 重复补偿 ${EconomyFormulas.fragmentsForRarity(Rarity.SSR.value)} 碎片")
    println("升到 40 级累计经验 ${EconomyFormulas.cumulativeExp(40)}")
    println("升到 41 级需星尘 ${EconomyFormulas.levelCost(40)}（随等级线性上升）")
}
