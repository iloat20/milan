package com.milan.game.data

/**
 * 存档读写提供者（C# ISaveProvider）。
 * Android 侧实现（文件系统 + .bak/.tmp 备份）在接入层完成。
 */
interface SaveProvider {
    fun save(json: String): Boolean
    fun load(): String
    fun delete()
    fun exists(): Boolean
    /** 读取备份档（.bak 优先，其次 .tmp），损坏/缺失返回 null。 */
    fun loadBackup(): String?
}

/**
 * 存档管理器（C# SaveManager 的 Kotlin 翻译）。
 *
 * 载入存档区分三类情况：
 *   1. 文件不存在 → 正常新号，用默认档；
 *   2. 主档存在但损坏/截断 → 先尝试 .bak / .tmp 备份，全失败才回退默认档并留痕（绝不静默抹档）；
 *   3. IO 瞬时异常 → 回退默认档。
 * 任何分支都不抛异常（进程级状态在初始化中调用，抛异常会让 App 永久打不开）。
 *
 * @param onTrace 留痕回调（对应 C# CrashReporter.Boot），Android 接入层注入。
 */
class SaveManager(
    private val provider: SaveProvider,
    private val onTrace: (String) -> Unit = {},
) {
    var current: SaveData? = null
        private set

    fun load(): SaveData {
        val result = try {
            val data = if (!provider.exists()) {
                SaveData.createDefault()
            } else {
                val parsed = SaveData.tryParse(provider.load())
                if (parsed != null) {
                    parsed
                } else {
                    // 主档损坏：尝试备份档恢复
                    val backup = provider.loadBackup()
                    val recovered = if (backup != null) SaveData.tryParse(backup) else null
                    if (recovered != null) {
                        onTrace("save.load.recovered.from.backup")
                        recovered
                    } else {
                        // 全部失败：回退默认档并留痕（不静默抹档）
                        onTrace("save.load.fallback: all sources corrupt")
                        SaveData.createDefault()
                    }
                }
            }
            // 版本迁移必须在 try 内执行：迁移逻辑一旦抛异常，必须走兜底而非让 load 崩溃
            // （对齐「载入永不抛异常」红线；当前 stub 安全，但预防未来迁移代码出错）。
            migrate(data)
            data
        } catch (e: Exception) {
            // IO 瞬时异常 / 迁移异常 → 回退默认档（对齐 C#：任何分支都不抛异常）。
            // 留痕：与「主档损坏分支」同等的排障可见性——否则无法区分「新号」与「IO 失败」，
            // 违背「绝不静默抹档」的精神（P2-1）。
            onTrace("save.load.failed: ${e.message}")
            SaveData.createDefault()
        }
        current = result
        // 载入后不设 lastPersistedJson：load 路径可能做过 sanitize，磁盘原文与内存
        // 序列化结果未必一致。首次 save 必须真正落盘，短路只在一次成功 save 之后生效。
        return result
    }

    /**
     * 持久化存档。返回是否成功；失败时已通过 [onTrace] 留痕，调用方应据此回滚内存改动。
     * 未 load 直接保存（current 为 null）时防御性新建默认档。
     *
     * 2026-09-10 P0 写路径：序列化结果与上次成功落盘完全一致时跳过 IO。
     * 事务范式仍保证「mutate 后内存与磁盘语义一致」；本短路只砍掉无语义变化的重复写
     * （如双 publish 触发的二次 save、或 mutate 空转后仍走 save 的防御路径）。
     */
    private var lastPersistedJson: String? = null

    fun save(): Boolean {
        val data = current ?: SaveData.createDefault().also { current = it }
        return try {
            val json = data.toJson()
            if (json == lastPersistedJson) return true
            val ok = provider.save(json)
            if (ok) lastPersistedJson = json
            ok
        } catch (e: Exception) {
            onTrace("save.failed: ${e.message}")
            false
        }
    }

    /** 版本迁移占位（C# Migrate stub）。 */
    private fun migrate(data: SaveData) { /* version migration stub */ }

    /**
     * 重置存档：删除存档文件并重新载入默认档（对齐「载入永不抛异常」红线）。
     * 删除失败返回 null 且不重载——调用方应保持原引用不动（事务语义：失败无任何变更）。
     */
    fun reset(): SaveData? {
        try {
            provider.delete()
        } catch (e: Exception) {
            onTrace("save.reset.delete.failed: ${e.message}")
            return null
        }
        lastPersistedJson = null
        return load()
    }
}
