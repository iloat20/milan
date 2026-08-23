package com.milan.game.ai

import com.milan.game.services.CharacterDataEntry

/**
 * 端侧 AI 抽象层（C# 无等价；2026 本地大模型方向）。
 *
 * 契约纯 Kotlin、无 android.* 依赖，便于将来接入 Compose Multiplatform / 桌面复用。
 * 当前默认实现 [StubOnDeviceAgent] 为离线、零依赖、确定性的「命运签文」生成器：
 * 与领域层引擎一致，注入 seed 即保证可复现（同一角色 + 同 seed → 同签文）。
 *
 * 接真实端侧大模型（MediaPipe LLM Inference / Gemini Nano via AICore）时，
 * 只需提供一个实现 [FortuneAgent] 的新对象并替换 [FortuneAgentRegistry.activeAgent]，
 * 见同包 OnDeviceAgentMediaPipe.kt（置于非默认 source set，避免无依赖时编译失败）。
 */
interface FortuneAgent {
    /** 基于角色数据与 seed 生成「命运签文」。seed 决定结果，保证可复现。 */
    fun fortune(def: CharacterDataEntry, seed: Long): String
}

/** 离线确定性实现：模板拼装 + seed 驱动的随机选词，无需任何模型或网络。 */
object StubOnDeviceAgent : FortuneAgent {
    private val openings = listOf("天道昭昭", "星河低语", "神谕将至", "命运之轮轻转", "诸神侧目")
    private val bodies = listOf(
        "此身承%s之血脉，于%s界中当立不世之功。",
        "%s的本命星今日偏耀，宜出击，忌犹疑。",
        "昔者%s神话流传于世，今执其名，当有回响。",
        "%s潜修于%s深处，机缘至则一鸣惊人。",
    )
    private val closings = listOf("——签曰：上上", "——签曰：中平", "——签曰：大有")

    override fun fortune(def: CharacterDataEntry, seed: Long): String {
        val r = kotlin.random.Random(seed)
        val o = openings[r.nextInt(openings.size)]
        val b = bodies[r.nextInt(bodies.size)].format(def.displayName, def.world)
        val c = closings[r.nextInt(closings.size)]
        return "$o。$b$c"
    }
}

/**
 * 签文实现注册表（I10：原名 OnDeviceAgent 有「真实端侧 Agent」的误导性，
 * 实为持有默认实现的注册表）。⚠️ 当前 [activeAgent] 为 Stub，非真实模型——
 * 接入端侧大模型（MediaPipe / AICore）时替换此处，并保持契约不变。
 */
object FortuneAgentRegistry {
    /** 当前启用的实现；默认 Stub（离线、零依赖、确定性）。接端侧大模型时替换此处。 */
    val activeAgent: FortuneAgent = StubOnDeviceAgent
}
