package com.milan.game

import android.app.Application
import com.milan.game.data.AndroidSaveProvider
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.ui.GameState

/**
 * 应用入口（对应 C# MauiApp 的启动职责）。
 *
 * 最先执行三件事，顺序敏感：
 *   1. [CrashReporter.install] —— 未捕获异常落盘（必须先于任何业务逻辑）；
 *   2. [CrashReporter.beginBootTrace] —— 归档上一轮启动面包屑并开新轮；
 *   3. [GameState.ensureInitialized] —— 装配存档提供者 + 内容（缺失走代码内兜底）。
 *
 * 任何一步失败都不抛：GameState 未初始化时 HomeScreen 的守卫会兜底，
 * 避免「启动即闪退且无任何现场」的排查困境。
 */
class MilanApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        CrashReporter.beginBootTrace()
        CrashReporter.boot("app.oncreate")
        MilanAudio.init(this) // 音频服务（资源缺失静默，见 MilanAudio 注释）

        try {
            GameState.ensureInitialized(
                saveProvider = AndroidSaveProvider(this),
                // 内容包：优先 assets/data.json（GameService 解析失败/无有效角色时
                // 自动回退 GameContent 代码内兜底，两条路径均走 enrich 补派生字段）。
                // 读取失败传 null，行为与 C# 侧 asset 缺失时一致（静默兜底）。
                contentJson = runCatching {
                    assets.open("data.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull(),
                onTrace = { CrashReporter.boot(it) },
            )
            CrashReporter.boot("app.init.done")
        } catch (e: Exception) {
            CrashReporter.write("MilanApp.onCreate", e)
            // 不重抛：主界面会显示错误态，且 CrashReporter 已留痕。
        }
    }
}
