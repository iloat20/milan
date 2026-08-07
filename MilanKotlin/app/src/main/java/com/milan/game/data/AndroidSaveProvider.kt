package com.milan.game.data

import android.content.Context
import java.io.File

/**
 * Android 文件系统存档提供者（C# LocalSaveProvider 翻译）。
 *
 * 布局：filesDir/save/save.json 主档 + save.json.bak 备份 + save.json.tmp 暂存。
 * 写档走原子事务：先写 .tmp，成功后经备份替换主档（C# File.Replace 同层语义），
 * 进程中途被杀也不会截断主档；失败时清理残留 .tmp，避免下次 Load 读到半截。
 */
class AndroidSaveProvider(context: Context) : SaveProvider {

    private val dir = File(context.filesDir, "save")
    private val main = File(dir, "save.json")
    private val bak = File(dir, "save.json.bak")
    private val tmp = File(dir, "save.json.tmp")

    init {
        dir.mkdirs()
    }

    override fun save(json: String): Boolean = try {
        tmp.writeText(json)
        if (main.exists()) main.copyTo(bak, overwrite = true)
        tmp.copyTo(main, overwrite = true)
        tmp.delete()
        true
    } catch (e: Exception) {
        // 写盘失败：清理残留临时文件，避免下次 Load 读到半截 .tmp。
        try { if (tmp.exists()) tmp.delete() } catch (_: Exception) { }
        false
    }

    override fun load(): String = main.readText()

    override fun delete() {
        if (main.exists()) main.delete()
    }

    override fun exists(): Boolean = main.exists()

    /** 备份档优先 .bak，其次 .tmp；损坏/缺失返回 null。 */
    override fun loadBackup(): String? = when {
        bak.exists() -> safeRead(bak)
        tmp.exists() -> safeRead(tmp)
        else -> null
    }

    private fun safeRead(f: File): String? = try { f.readText() } catch (_: Exception) { null }
}
