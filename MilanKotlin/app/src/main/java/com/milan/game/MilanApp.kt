package com.milan.game

import android.app.Application
import com.milan.game.data.AndroidSaveProvider
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.MilanAudio
import com.milan.game.GameState
import com.milan.game.ui.components.PortraitLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口（对应 C# MauiApp 的启动职责）。
 *
 * 最先执行三件事，顺序敏感：
 *   1. [CrashReporter.install] —— 未捕获异常落盘（必须先于任何业务逻辑）；
 *   2. [CrashReporter.beginBootTrace] —— 归档上一轮启动面包屑并开新轮；
 *   3. [GameState.ensureInitialized] —— 装配存档提供者 + 内容（缺失走代码内兜底）。
 *      2026-08 起移入后台线程执行（P3-11 启动性能），UI 经 [GameState.ready] 门控。
 *
 * 任何一步失败都不抛：GameState 未初始化时 HomeScreen 的守卫会兜底，
 * 避免「启动即闪退且无任何现场」的排查困境。
 */
class MilanApp : Application() {

    /** 进程级后台初始化作用域（仅承载一次性启动任务；Application 生命周期 = 进程生命周期）。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 顺序红线不变：取证钩子必须最先安装（同步、轻量），业务初始化随后。
        CrashReporter.install(this)
        CrashReporter.beginBootTrace()
        CrashReporter.boot("app.oncreate")
        MilanAudio.init(this) // 音频服务（资源缺失静默，见 MilanAudio 注释）
        // P0-C5：系统内存压力回调集中注册一次（立绘 24MB LRU 缓存随压力收缩/清空）。
        // 不再由每个 PortraitImage 各自注册/注销——Context.registerComponentCallbacks
        // 非引用计数，多实例场景下首个 dispose 会误删仍被其他实例需要的回调。
        registerComponentCallbacks(PortraitLoader.memoryCallbacks)
        // 2026-09-12：武器图 LRU 同挂内存压力（此前不在 onTrimMemory 链路）
        registerComponentCallbacks(com.milan.game.ui.characters.WeaponArtMemoryCallbacks)

        // P3-11 启动性能：data.json（~50KB）读取/解析/enrich 与存档载入/sanitize 移出
        // 主线程冷启动关键路径。ensureInitialized 幂等且线程安全（双检锁 + @Volatile）；
        // UI 宿主经 GameState.ready 门控——就绪前 MilanNavHost 显示加载态，不存在未初始化访问。
        appScope.launch {
            try {
                GameState.ensureInitialized(
                    saveProvider = AndroidSaveProvider(this@MilanApp),
                    // 内容包：优先 assets/data.json（GameService 解析失败/无有效角色时
                    // 自动回退 GameContent 代码内兜底，两条路径均走 enrich 补派生字段）。
                    // 读取失败传 null，行为与 C# 侧 asset 缺失时一致（静默兜底）。
                    contentJson = runCatching {
                        assets.open("data.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }.getOrNull(),
                    onTrace = { CrashReporter.boot(it) },
                )
                // 冷启动同步音效开关（2026-08 bug 审查修复）：sfxVolume 默认 0.9f，
                // 此前只有设置页开关联动 setSfxVolume——存档「音效关」的玩家重启后
                // 音效照常响，直到再进设置页拨动开关（UI 显示关 / 实际行为开的错位）。
                if (!GameState.service.saveData.soundEnabled) MilanAudio.setSfxVolume(0f)
                CrashReporter.boot("app.init.done")
            } catch (e: Exception) {
                CrashReporter.write("MilanApp.onCreate", e)
                // 不重抛：主界面会显示错误态，且 CrashReporter 已留痕。
            }
        }
    }
}
