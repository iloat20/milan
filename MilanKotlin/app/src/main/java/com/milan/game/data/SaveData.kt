package com.milan.game.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 存档数据模型（C# SaveData 的 Kotlin 翻译）。
 *
 * ⚠️ 序列化契约（改动前必读）：
 * 1. 所有字段用 @SerialName 对齐 C# 的 PascalCase 字段名 —— 旧档（System.Text.Json 字段原名写出）
 *    依赖此契约可读；改名 = 玩家存档全丢。
 * 2. 集合字段元素声明为可空（List<T?>），配合 Json.coerceInputValues：
 *    - JSON 显式 null 字段 → 用默认值（C# 里 null 覆盖字段初始化器后再 Sanitize，语义一致）；
 *    - JSON 数组内 null 元素 → 保留给 sanitize() 过滤（C# RemoveAll(x => x == null)）。
 * 3. 模型用普通 class + var（可变），与 C# 公共字段语义一致，养成/抽卡写操作可原地修改。
 */
@Serializable
class SaveData(
    @SerialName("Version") var version: Int = 1,
    @SerialName("SoftCurrency") var softCurrency: Int = 999999,
    @SerialName("HardCurrency") var hardCurrency: Int = 0,
    @SerialName("OwnedCharacters") var ownedCharacters: List<CharacterSaveState?> = emptyList(),
    @SerialName("OwnedSkins") var ownedSkins: List<String?> = emptyList(),
    @SerialName("Items") var items: List<ItemSaveState?> = emptyList(),
    @SerialName("GachaCounters") var gachaCounters: List<GachaCounterEntry?> = emptyList(),
    @SerialName("BattleRecords") var battleRecords: List<BattleRecord?> = emptyList(),
    @SerialName("UserId") var userId: String = "",
    @SerialName("ServerSyncStatus") var serverSyncStatus: Int = 0,
    // 设置项：开关类偏好持久化，避免重启即丢失。统一默认开启。
    @SerialName("SoundEnabled") var soundEnabled: Boolean = true,
    @SerialName("VibrationEnabled") var vibrationEnabled: Boolean = true,
    @SerialName("PushEnabled") var pushEnabled: Boolean = true,
) {
    /** 序列化为 JSON（prettyPrint 对齐 C# WriteIndented）。 */
    fun toJson(): String = json.encodeToString(serializer(), this)

    fun getGachaCounter(poolId: String): Int =
        gachaCounters.firstOrNull { it?.poolId == poolId }?.count ?: 0

    fun setGachaCounter(poolId: String, count: Int) {
        val entry = gachaCounters.firstOrNull { it?.poolId == poolId }
        if (entry != null) entry.count = count
        else gachaCounters = gachaCounters + GachaCounterEntry(poolId = poolId, count = count)
    }

    /**
     * 修复反序列化后的脏数据（对应 C# Sanitize）：
     * - 数组内 null 元素（JSON 显式 null）→ 过滤；
     * - 主键为空的条目 → 过滤；
     * - 负货币/非法养成值 → 钳制到合法域；
     * - 同 CharacterId / 同 PoolId 重复 → 保留首条；
     * - 同 ItemId 道具 → 合并数量。
     */
    fun sanitize() {
        val chars = ownedCharacters.filterNotNull().filter { it.characterId.isNotEmpty() }.toMutableList()
        val seenChars = HashSet<String>()
        val unique = chars.filter { seenChars.add(it.characterId) }
        for (c in unique) {
            c.level = c.level.coerceAtLeast(1)
            c.stage = c.stage.coerceAtLeast(1)
            c.stars = c.stars.coerceAtLeast(1)
            c.totalExp = c.totalExp.coerceAtLeast(0)
            c.unspentPoints = c.unspentPoints.coerceAtLeast(0)
            c.talentPoints = c.talentPoints.filterNotNull().filter { it.isNotEmpty() }.toMutableList()
        }
        ownedCharacters = unique.toMutableList()

        ownedSkins = ownedSkins.filterNotNull().toMutableList()

        // 道具去重合并：分散的同 ID 条目会让「持有数量」读到的只是其中一条。
        val merged = LinkedHashMap<String, ItemSaveState>()
        for (it in items.filterNotNull().filter { it.itemId.isNotEmpty() }) {
            val existing = merged[it.itemId]
            if (existing != null) existing.count += it.count.coerceAtLeast(0)
            else {
                it.count = it.count.coerceAtLeast(0)
                merged[it.itemId] = it
            }
        }
        items = merged.values.toMutableList()

        val seenPools = HashSet<String>()
        gachaCounters = gachaCounters.filterNotNull()
            .filter { it.poolId.isNotEmpty() }
            .filter { seenPools.add(it.poolId) }
            .onEach { it.count = it.count.coerceAtLeast(0) }
            .toMutableList()

        battleRecords = battleRecords.filterNotNull().toMutableList()

        if (softCurrency < 0) softCurrency = 0
        if (hardCurrency < 0) hardCurrency = 0
        if (userId.isEmpty()) userId = ""
    }

    companion object {
        /**
         * ignoreUnknownKeys：旧版/未来字段不致命（System.Text.Json 默认忽略未知属性，对齐）。
         * coerceInputValues：JSON 显式 null 落到非空字段时用默认值而非抛异常（C# null 覆盖 + Sanitize 兜底）。
         */
        val json: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /**
         * 反序列化存档。任何 null/空/损坏输入都必须回退 [createDefault]，绝不允许抛异常——
         * 否则进程级状态初始化会抛异常，App 永久无法启动。
         */
        fun fromJson(j: String): SaveData = tryParse(j) ?: createDefault()

        /**
         * 尝试解析存档；成功返回对象，失败（null/空/损坏）返回 null。
         * 用于 [SaveManager] 区分「无档」与「损坏」并决定回退策略。
         */
        fun tryParse(j: String): SaveData? {
            if (j.isBlank()) return null
            return try {
                val d = json.decodeFromString<SaveData>(j)
                d.sanitize()
                d
            } catch (e: Exception) {
                // 刻意捕获全部异常：解析失败一律视为损坏，交由调用方回退（对齐 C# 注释语义）。
                null
            }
        }

        fun createDefault(): SaveData = SaveData()
    }
}
