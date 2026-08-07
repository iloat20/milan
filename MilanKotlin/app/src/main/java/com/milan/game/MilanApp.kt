package com.milan.game

import android.app.Application
import com.milan.game.data.AndroidSaveProvider
import com.milan.game.infrastructure.CrashReporter
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

        try {
            GameState.ensureInitialized(
                saveProvider = AndroidSaveProvider(this),
                // 内容包（data.json）尚未迁入 assets：走 GameContent 代码内兜底数据，
                // 行为与 C# 侧 asset 缺失时一致（静默使用兜底）。
                contentJson = null,
                onTrace = { CrashReporter.boot(it) },
            )
            CrashReporter.boot("app.init.done")
        } catch (e: Exception) {
            CrashReporter.write("MilanApp.onCreate", e)
            // 不重抛：主界面会显示错误态，且 CrashReporter 已留痕。
        }
    }
}
