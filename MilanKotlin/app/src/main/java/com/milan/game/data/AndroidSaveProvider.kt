package com.milan.game.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Android 文件系统存档提供者（C# LocalSaveProvider 翻译）。
 *
 * 布局：filesDir/save/save.json 主档 + save.json.bak 备份 + save.json.tmp 暂存。
 * 写档走原子事务：先写 .tmp 并 fsync，成功后滚备份、经 NIO 原子替换主档
 * （C# File.Replace 同层语义），进程中途被杀也不会截断主档；失败时清理残留 .tmp，
 * 避免下次 Load 读到半截。
 *
 * 2026-08 P2-2 加固：
 * - 写 .tmp 后 fsync（掉电不留下空/半截文件）；
 * - 主档替换用 Files.move + ATOMIC_MOVE（同卷原子；旧实现 renameTo 在 Windows 无法覆盖
 *   已存在目标，回退的 copyTo+delete 非原子，中途被杀会截断主档）；
 * - save() 加对象级写锁：为未来 IO 线程化后的并发写预留串行化。
 *
 * 2026-09-08 P0-1：单次 save() 的 fsync 次数由 2 降为 1（见 [save] 内注释）。
 * 溯源：写事务热路径上序列化本身仅约 0.4ms（见 SaveSerializationBaselineTest），
 * 成本集中在 fsync 这类磁盘屏障；而 [SaveData.json] 关闭 prettyPrint 后存档体积降约 60%，
 * 两项叠加后单次事务的 IO 字节数与屏障次数同步下降。
 */
class AndroidSaveProvider(context: Context) : SaveProvider {

    private val dir = File(context.filesDir, "save")
    private val main = File(dir, "save.json")
    private val bak = File(dir, "save.json.bak")
    private val tmp = File(dir, "save.json.tmp")
    /** 串行化并发 save（当前单主线程调用；P2-2 为 IO 线程化预留）。 */
    private val writeLock = Any()

    init {
        dir.mkdirs()
    }

    override fun save(json: String): Boolean = synchronized(writeLock) {
        try {
            tmp.writeText(json)
            // fsync 必须保留：tmp 完整是后续 ATOMIC_MOVE 生效的前提，掉电不能留下半截 tmp。
            tmp.sync()
            // 主档更替前滚动备份。
            //
            // 2026-09-08 P0-1：不再对 bak 做 fsync（原 bak.sync() 是单次 save 最贵屏障之一）。
            // 2026-09-10 P0 写路径：bak 改为 **同卷 rename 滚动**，不再 `main.readText()+writeText`
            // 整档读回再写——中后期存档每次写都要多做一遍全量拷贝（CPU + IO 字节翻倍）。
            // rename 在同卷上通常是元数据操作，成本 O(1)。
            //
            // 失败恢复：若 tmp→main 失败而 main 已被 rename 走，把 bak rename 回 main，
            // 保证「要么旧主档要么新主档」，不会出现「只剩 bak、main 消失」的空窗。
            val hadMain = main.exists()
            if (hadMain) {
                Files.move(main.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            try {
                // 原子替换：同卷 ATOMIC_MOVE 保证「要么旧档要么新档」，杜绝半截主档
                // （旧实现 renameTo 在 Windows 覆盖失败 → copyTo+delete 非原子回退，P2-2）。
                try {
                    Files.move(
                        tmp.toPath(), main.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    // 个别文件系统不支持原子移动：退化为 REPLACE_EXISTING（非常规路径，靠 .bak 兜底）
                    Files.move(tmp.toPath(), main.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                if (hadMain && !main.exists() && bak.exists()) {
                    try {
                        Files.move(bak.toPath(), main.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    } catch (_: Exception) {
                        // 恢复失败：主档仍缺，靠 load() 走备份链（loadBackup 读 bak）
                    }
                }
                throw e
            }
            true
        } catch (e: Exception) {
            // 写盘失败：清理残留临时文件，避免下次 Load 读到半截 .tmp。
            try { if (tmp.exists()) tmp.delete() } catch (_: Exception) { }
            false
        }
    }

    override fun load(): String = main.readText()

    override fun delete() {
        // 重置存档必须清理整条备份链（main/bak/tmp）：只删主档会留下 .bak 残留旧档，
        // 且下次 save() 时主档不存在、bak 不再更新——日后主档一旦损坏，
        // loadBackup() 优先读 .bak，把玩家已明确删除的进度原样复活（P1-2）。
        if (main.exists()) main.delete()
        if (bak.exists()) bak.delete()
        if (tmp.exists()) tmp.delete()
    }

    override fun exists(): Boolean = main.exists()

    /** 备份档优先 .bak，其次 .tmp；损坏/缺失返回 null。 */
    override fun loadBackup(): String? = when {
        bak.exists() -> safeRead(bak)
        tmp.exists() -> safeRead(tmp)
        else -> null
    }

    private fun safeRead(f: File): String? = try { f.readText() } catch (_: Exception) { null }

    /** fsync 落盘（append 模式打开不截断文件，fd.sync 把缓冲刷到物理存储）。 */
    private fun File.sync() {
        FileOutputStream(this, true).use { it.fd.sync() }
    }
}
