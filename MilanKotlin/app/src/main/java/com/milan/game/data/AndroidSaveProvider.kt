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
            // 主档更替前先滚动备份（读旧主档写 bak）。
            //
            // 2026-09-08 P0-1：此处**不再对 bak 做 fsync**（原实现 bak.sync()）。
            // 推理：fsync 是单次 save() 里最昂贵的磁盘屏障，本方法原本串行触发两次。
            // 备份档只在「主档损坏」时才被 [loadBackup] 读取，而主档始终经 ATOMIC_MOVE
            // 替换——要么旧档要么新档，不存在半截态。因此 bak 未落盘即崩溃的唯一后果是
            // 「备份停留在上一版或半截」，而这只在主档同时损坏时才会被读到，
            // 且 [SaveData.tryParse] 会拒绝半截内容并回退默认档（与「无备份」后果等价）。
            // 主档的持久性保证完全不受影响（tmp.sync + ATOMIC_MOVE 已覆盖）。
            if (main.exists()) {
                bak.writeText(main.readText())
            }
            try {
                // 原子替换：同卷 ATOMIC_MOVE 保证「要么旧档要么新档」，杜绝半截主档
                // （旧实现 renameTo 在 Windows 覆盖失败 → copyTo+delete 非原子回退，P2-2）。
                Files.move(
                    tmp.toPath(), main.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // 个别文件系统不支持原子移动：退化为 REPLACE_EXISTING（非常规路径，靠 .bak 兜底）
                Files.move(tmp.toPath(), main.toPath(), StandardCopyOption.REPLACE_EXISTING)
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
