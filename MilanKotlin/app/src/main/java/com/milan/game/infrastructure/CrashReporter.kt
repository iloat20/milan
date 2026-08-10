package com.milan.game.infrastructure

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * 崩溃取证与留痕（C# CrashReporter 翻译）。
 *
 * 职责：
 * - [install] 在 Application.onCreate 最开始调用，挂全局未捕获异常钩子；
 * - [boot] / [beginBootTrace] 记录启动面包屑，native 崩溃时唯一的线索；
 * - [write] 落盘 last_crash.txt（内部 + 外部镜像 /sdcard/Android/data/<pkg>/files/crash/），
 *   尽力弹出崩溃现场对话框（复制 / 退出）；
 * - 上一轮启动没走完全流程（[previousBootIncomplete]）→ 首页兜底回显现场。
 *
 * 铁律：取证代码本身绝不能再抛异常，所有 IO 全 catch（C# 同款）；
 * 镜像落盘在后台线程，不阻塞 UI 线程 / 启动路径。
 */
object CrashReporter {

    private const val MAX_TRACE_BYTES = 65536
    private const val HISTORY_DIR = "history"
    private const val MAX_HISTORY_FILES = 10

    private val gate = Any()
    private var installed = false

    private lateinit var appContext: Context
    private lateinit var baseDir: File
    private lateinit var crashFile: File
    private lateinit var traceFile: File
    private var externalDir: File? = null
    private var currentActivity: Activity? = null

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val stampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    // 归档文件名专用：无冒号，保证 adb pull 到 Windows 不出问题
    private val histStampFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** 安装全局异常钩子。必须在 Application.onCreate 最开始调用。 */
    fun install(context: Context) {
        if (installed) return
        installed = true
        appContext = context.applicationContext
        baseDir = File(context.filesDir, "crash").apply { mkdirs() }
        crashFile = File(baseDir, "last_crash.txt")
        traceFile = File(baseDir, "boot_trace.txt")
        // 外部镜像目录（adb 可读，便于取证）；拿不到（无外部存储）则跳过镜像。
        externalDir = runCatching { context.getExternalFilesDir(null)?.let { File(it, "crash") } }.getOrNull()

        // 前台 Activity 跟踪：崩溃对话框需要宿主 Activity（C# MauiApp.Current 同层）。
        (appContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) { currentActivity = activity }
                override fun onActivityPaused(activity: Activity) { if (currentActivity === activity) currentActivity = null }
                override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
        )

        // 未捕获异常统一走 write（Android 主/子线程未捕获异常都到达默认处理器）。
        // 不设「已处理」——让它照常崩，避免应用停在不一致状态。
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            write("Thread.UncaughtException", e)
        }
    }

    /** 启动面包屑。阶段名要短且唯一。 */
    fun boot(stage: String) {
        try {
            synchronized(gate) {
                val line = "${timeFmt.format(Date())}  $stage\n"
                // 轮转保护：单轮启动面包屑过长时停止追加，避免无限增长与反复读全文件造成 O(n^2) IO。
                if (traceFile.length() < MAX_TRACE_BYTES)
                    traceFile.appendText(line)
                // 外部镜像改为增量追加单行，不再每次读回全量 trace。
                mirrorAppend("boot_trace.txt", line)
            }
        } catch (_: Exception) { /* 取证代码本身绝不能再抛 */ }
    }

    /** 新一轮启动：把上一轮的面包屑归档为 prev_boot_trace，然后清空。 */
    fun beginBootTrace() {
        try {
            synchronized(gate) {
                if (traceFile.exists()) {
                    val prev = traceFile.readText()
                    File(baseDir, "prev_boot_trace.txt").writeText(prev)
                    mirror("prev_boot_trace.txt", prev)
                }
                val header = "=== boot ${stampFmt.format(Date())} ===\n"
                traceFile.writeText(header)
                mirror("boot_trace.txt", header)
            }
        } catch (_: Exception) { }
    }

    /** 落盘崩溃报告（内部 + 外部镜像）并尽力弹窗。返回报告文本。 */
    fun write(source: String, ex: Throwable?): String {
        // 构建报告文本：主路径拼装异常时至少保留最小线索，避免崩溃现场完全蒸发。
        val text = try {
            buildReport(source, ex)
        } catch (_: Exception) {
            "$source: ${ex?.message ?: "unknown"}"
        }
        // 统一落盘（内部 + 外部镜像），尽力而为。
        try {
            synchronized(gate) {
                crashFile.writeText(text)
                mirror("last_crash.txt", text)
            }
        } catch (_: Exception) { }
        // 取证增强：历史归档（保留最近 N 份）+ 崩溃计数，均尽力而为
        archiveCrash(text)
        bumpCrashCount()
        tryShowDialog(text)
        return text
    }

    /** 拼装崩溃报告全文（异常链 ≤6 层 + 本次启动面包屑）。 */
    private fun buildReport(source: String, ex: Throwable?): String {
        val sb = StringBuilder()
        sb.appendLine("时间: ${stampFmt.format(Date())}")
        sb.appendLine("来源: $source")
        sb.appendLine("机型: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("ABI : ${Build.SUPPORTED_ABIS.joinToString(",")}")
        sb.appendLine()

        var e: Throwable? = ex
        var depth = 0
        while (e != null && depth++ < 6) {
            sb.appendLine("[${e.javaClass.name}] ${e.message}")
            val st = e.stackTrace
            if (st != null && st.isNotEmpty())
                st.forEach { sb.appendLine("  at $it") }
            else
                sb.appendLine("(无堆栈)")
            e = e.cause
            if (e != null) sb.appendLine("--- Caused by ---")
        }

        sb.appendLine()
        sb.appendLine("--- 本次启动面包屑 ---")
        try { sb.append(traceFile.readText()) } catch (_: Exception) { sb.appendLine("(无)") }
        return sb.toString()
    }

    /** 读取上一次崩溃报告（不删除），供「先展示、后清除」流程使用。 */
    fun peekCrash(): String? {
        try { synchronized(gate) { return if (crashFile.exists()) crashFile.readText() else null } }
        catch (_: Exception) { return null }
    }

    /** 删除已落盘的崩溃报告（内部 + 外部镜像）。 */
    fun clearCrash() {
        try {
            synchronized(gate) {
                if (crashFile.exists()) crashFile.delete()
                try {
                    externalDir?.let { File(it, "last_crash.txt").delete() }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    /** 读取上一次崩溃报告（读完即删，避免反复弹窗）。首页兜底回显使用。 */
    fun readAndClear(): String? {
        val text = peekCrash()
        if (text != null) clearCrash()
        return text
    }

    /** 历史累计崩溃次数（crash_count.txt，跨启动累计，含 EventBus 等非致命留痕）。 */
    fun crashCount(): Int {
        try { synchronized(gate) { return File(baseDir, "crash_count.txt").readText().trim().toIntOrNull() ?: 0 } }
        catch (_: Exception) { return 0 }
    }

    /** 同步导出全部取证（最近崩溃 + 历史归档 + 启动面包屑）到外部镜像目录（adb 可读）。
     *  崩溃对话框「导出」按钮调用；返回导出目录路径，失败返回 null。 */
    fun exportAll(): String? {
        try {
            val d = externalDir ?: return null
            synchronized(gate) {
                d.mkdirs()
                if (crashFile.exists()) File(d, "last_crash.txt").writeText(crashFile.readText())
                File(baseDir, HISTORY_DIR).listFiles()?.forEach { f ->
                    runCatching { File(d, f.name).writeText(f.readText()) }
                }
                if (traceFile.exists()) File(d, "boot_trace.txt").writeText(traceFile.readText())
            }
            return d.absolutePath
        } catch (_: Exception) { return null }
    }

    /** 删除「上一轮启动」归档标记。Application 阶段已展示过现场时调用，防止把同一份现场弹两次。 */
    fun clearPreviousBootFlag() {
        try {
            synchronized(gate) {
                File(baseDir, "prev_boot_trace.txt").delete()
                try {
                    externalDir?.let { File(it, "prev_boot_trace.txt").delete() }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    /** 上一次启动的面包屑 —— native 崩溃时唯一的线索。 */
    fun previousBootTrace(): String? {
        try {
            val p = File(baseDir, "prev_boot_trace.txt")
            return if (p.exists()) p.readText() else null
        } catch (_: Exception) { return null }
    }

    /** 上一轮启动是否走完了全流程。没走完 = 上次是崩溃退出。 */
    fun previousBootIncomplete(): Boolean {
        val t = previousBootTrace()
        return !t.isNullOrEmpty() && !t.contains("home.oncreate.done")
    }

    // ── 内部 ──

    /** 把取证文件整体镜像到外部目录（调用方需持有 gate 锁）。外部目录 IO 较重，后台线程落盘。 */
    private fun mirror(name: String, content: String) {
        try {
            val d = externalDir ?: return
            val full = File(d, name)
            thread {
                try { d.mkdirs(); full.writeText(content) } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    /** 把单行增量追加进外部镜像（调用方需持有 gate 锁）。高频 Boot 路径专用，避免每次读回全量 trace。 */
    private fun mirrorAppend(name: String, line: String) {
        try {
            val d = externalDir ?: return
            val full = File(d, name)
            thread {
                try { d.mkdirs(); full.appendText(line) } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    /** 崩溃历史归档：crash/history/crash_<时间戳>.txt，保留最近 [MAX_HISTORY_FILES] 份，超出删最旧。
     *  last_crash.txt 语义保持「最近一次」，历史文件供后续排查多次崩溃序列。 */
    private fun archiveCrash(text: String) {
        try {
            synchronized(gate) {
                val dir = File(baseDir, HISTORY_DIR).apply { mkdirs() }
                File(dir, "crash_${histStampFmt.format(Date())}.txt").writeText(text)
                val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }?.toMutableList()
                    ?: return
                while (files.size > MAX_HISTORY_FILES) {
                    files.removeAt(0).delete()
                }
            }
        } catch (_: Exception) { }
    }

    /** 崩溃计数递增（crash_count.txt，跨启动累计）。 */
    private fun bumpCrashCount() {
        try {
            synchronized(gate) {
                val f = File(baseDir, "crash_count.txt")
                val n = (f.readText().trim().toIntOrNull() ?: 0) + 1
                f.writeText(n.toString())
            }
        } catch (_: Exception) { }
    }

    /** 若当前有前台 Activity，尽力弹出崩溃现场对话框。进程可能即将死亡，故仅尽力而为；
     * 即使弹不出来，落盘的 last_crash.txt 仍保证下次启动回显。 */
    private fun tryShowDialog(report: String) {
        try {
            val act = currentActivity ?: return
            act.runOnUiThread {
                try {
                    val shown = if (report.length > 3000) report.substring(0, 3000) else report
                    AlertDialog.Builder(act)
                        .setTitle("应用崩溃 · 现场已记录")
                        .setMessage(shown)
                        .setPositiveButton("复制") { _, _ ->
                            try {
                                val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("milan-crash", report))
                            } catch (_: Exception) { }
                        }
                        .setNeutralButton("导出") { _, _ ->
                            try {
                                val path = exportAll()
                                Toast.makeText(
                                    act,
                                    if (path != null) "已导出取证到 $path" else "导出失败（无外部存储）",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } catch (_: Exception) { }
                        }
                        .setNegativeButton("退出") { _, _ -> act.finish() }
                        .setCancelable(false)
                        .show()
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }
}
