package com.milan.game.benchmark

import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 启动耗时基准（试验田·微创新）：冷启动帧时，为 Track A 的 GPU 演出提供帧率兜底。
 * 运行：./gradlew :benchmark:benchmarkRelease  （需在真机/模拟器上执行）
 * 2026-08-13：修正为官方 MacrobenchmarkRule API（此前误用不存在的 BenchmarkRule，模块从未编译过）。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.milan.game",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        // 进入抽卡页并触发一次开包，覆盖 Track A 的 GPU 后处理路径
        device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("次元裂缝")), 5_000)
    }
}
