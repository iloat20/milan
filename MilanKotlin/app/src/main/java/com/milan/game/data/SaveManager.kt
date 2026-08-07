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
            if (!provider.exists()) {
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
        } catch (e: Exception) {
            // IO 瞬时异常 → 回退默认档（对齐 C#：任何分支都不抛异常）
            SaveData.createDefault()
        }
        current = result
        migrate(result)
        return result
    }

    /**
     * 持久化存档。返回是否成功；失败时已通过 [onTrace] 留痕，调用方应据此回滚内存改动。
     * 未 load 直接保存（current 为 null）时防御性新建默认档。
     */
    fun save(): Boolean {
        val data = current ?: SaveData.createDefault().also { current = it }
        return try {
            provider.save(data.toJson())
        } catch (e: Exception) {
            onTrace("save.failed: ${e.message}")
            false
        }
    }

    /** 版本迁移占位（C# Migrate stub）。 */
    private fun migrate(data: SaveData) { /* version migration stub */ }
}
