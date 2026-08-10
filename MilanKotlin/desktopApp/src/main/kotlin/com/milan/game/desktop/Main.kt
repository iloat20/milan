package com.milan.game.desktop

import com.milan.game.shared.GachaRng
import kotlin.random.Random

/**
 * 桌面抽卡模拟器（Compose Multiplatform 方向的最小验证）。
 * 纯 JVM，复用 :shared 的跨平台抽卡数学，证明领域逻辑可脱离 Android 运行。
 * 运行：./gradlew :desktopApp:run
 */
fun main() {
    val weights = listOf(1, 2, 5, 92) // 示意权重：UR, SSR, SR, R
    val names = arrayOf("UR", "SSR", "SR", "R")
    val rng = Random(20260810L)

    println("=== Milan 桌面抽卡模拟器（复用 :shared 领域数学）===")
    repeat(10) { i ->
        val idx = GachaRng.weightedPick(weights, rng)
        println("第 ${i + 1} 抽 -> ${names[idx]}")
    }
    val pity = GachaRng.applyPity(89, 90, 3)
    println("保底触发（counter=89, hardPity=90）-> ${if (pity != null) names[pity] else "未触发"}")
}
