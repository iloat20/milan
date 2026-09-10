package com.milan.game.infrastructure

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 台词语音播放（2026-08：详情页「语音/台词」面板从纯文本升级为可播报）。
 *
 * 设计取舍：
 * - 进程级单例引擎：TextToSpeech 初始化是异步 IO，反复创建/销毁代价高且易泄漏——
 *   首次点击惰性初始化后常驻复用（与 MilanAudio 的 ExoPlayer 常驻策略同思路）；
 * - 未就绪时挂起待播文本：初始化回调成功后自动补播首次点击的台词，
 *   避免「第一次点了没反应」的体验断裂；
 * - 中文不可用回退英文引擎：TTS 缺中文语音包时仍可发音（生硬但优于无声）；
 * - 全路径静默失败：设备无 TTS 引擎 / 初始化失败绝不抛异常中断 UI（对齐 MilanAudio 语义）。
 */
object SpeechPlayer {

    @Volatile
    private var tts: TextToSpeech? = null

    /** 引擎是否完成初始化（init 回调置位；shutdown 后复位）。 */
    @Volatile
    private var ready = false

    /** 初始化进行中标记（防止连点重复创建引擎实例）。 */
    @Volatile
    private var initializing = false

    /** 引擎未就绪期间的待播台词（就绪后自动补播，仅保留最后一条）。 */
    @Volatile
    private var pending: String? = null

    /**
     * 播报一句台词。引擎未初始化时先启动异步初始化并记下本句，就绪后自动播出。
     * QUEUE_FLUSH：连续点击只播最新一条，不排队堆叠。
     */
    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        if (ensureEngine(context)) {
            doSpeak(text)
        } else {
            pending = text
        }
    }

    /** 停止当前播报（不清引擎；页面退出时调用，避免跨页残留朗读）。 */
    fun stop() {
        runCatching { tts?.stop() }
    }

    /** 进程级释放（Application 退出等场景；常规使用无需调用）。 */
    fun release() {
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
        initializing = false
        pending = null
    }

    /** 惰性确保引擎存在并初始化中/已就绪。返回 true = 可立即播报。 */
    private fun ensureEngine(context: Context): Boolean {
        if (ready) return true
        if (!initializing) {
            initializing = true
            tts = try {
                TextToSpeech(context.applicationContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    initializing = false
                    if (ready) {
                        configureLanguage()
                        // 补播初始化期间点击的台词（体验闭环：首点必有所应）
                        val p = pending
                        if (p != null && ready) {
                            pending = null
                            doSpeak(p)
                        }
                    }
                }
            } catch (_: Exception) {
                initializing = false
                null
            }
        }
        return false
    }

    /** 语言配置：优先简中，缺失语音包回退英文（TTS 生硬但不至于完全无声）。 */
    private fun configureLanguage() {
        val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            runCatching { tts?.setLanguage(Locale.US) }
        }
    }

    private fun doSpeak(text: String) {
        val engine = tts ?: return
        runCatching {
            engine.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "milan_voice_${text.hashCode()}",
            )
        }
    }
}
