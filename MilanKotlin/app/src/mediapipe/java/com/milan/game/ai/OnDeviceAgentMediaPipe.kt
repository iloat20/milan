package com.milan.game.ai

import com.milan.game.services.CharacterDataEntry

/**
 * 端侧大模型实现参考（MediaPipe LLM Inference API / Gemma-2B-int4）。
 *
 * ⚠️ 本文件位于 `app/src/mediapipe/java`（非默认 source set），默认不参与编译。
 * 启用真实端侧推理时：
 *   1) 在 libs.versions.toml 增加 `mediapipeGenai = "0.10.22"`，并在 [libraries] 增加
 *      `androidx-mediapipe-genai = { group = "com.google.mediapipe", name = "tasks-genai", version.ref = "mediapipeGenai" }`
 *   2) 在 app/build.gradle.kts 增加 `implementation(libs.androidx.mediapipe.genai)`
 *   3) 把本文件移动到 `app/src/main/java/...`，并将 OnDeviceAgent.current 指向本实现
 *   4) 将 Gemma-2B-int4 模型 .bin 放入设备可读路径（APK +200~500MB；试验田可接受）
 *
 * 设备不支持 / 模型缺失时，回到 [StubOnDeviceAgent]（在调用方用 supportsOnDevice() 判断）。
 */
class MediaPipeOnDeviceAgent(
    private val modelPath: String,
) : OnDeviceAgent {

    // LlmInference 懒加载：首次推理前创建，避免冷启动 ANR
    private val inference by lazy {
        // com.google.mediapipe.tasks.genai.LlmInference
        // LlmInference.createFromOptions(context, LlmInference.LlmInferenceOptions.builder()
        //     .setModelPath(modelPath)
        //     .setResultListener { partial, done -> /* 流式拼接 */ }
        //     .build())
        TODO("需在启用 MediaPipe 依赖后取消 TODO；此处仅作架构占位")
    }

    override fun fortune(def: CharacterDataEntry, seed: Long): String {
        val prompt = buildString {
            append("你是抽卡游戏「Milan·诸神黄昏」的签文官。基于角色【${def.displayName}】")
            append("（出处：${def.world}，元素：${def.element}），生成一句中文命运签文，")
            append("30 字以内，押韵或有古风。seed=$seed")
        }
        // 真实实现：inference.generateResponse(prompt)（同步/流式）
        return "（MediaPipe 占位）${prompt.take(20)}…"
    }

    companion object {
        /** 当前设备/模型是否可用（AICore 或本地 .bin 存在）。 */
        fun supportsOnDevice(): Boolean = false
    }
}
