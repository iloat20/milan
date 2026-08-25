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
    // ── 2026-08 优化新增（带默认值：旧档缺字段自动落默认，向后兼容）──
    /** 出战编队（characterId 有序槽位，空槽为 null；上限 [Companion.MAX_FORMATION_SIZE]）。 */
    @SerialName("Formation") var formation: List<String?> = emptyList(),
    /** 无尽之塔历史最高层（0 = 尚未挑战）。 */
    @SerialName("TowerBestFloor") var towerBestFloor: Int = 0,
    /** 每日商店归属的 UTC 日序号字符串（与当日不一致 = 跨日，已购列表作废重置）。 */
    @SerialName("DailyShopDate") var dailyShopDate: String = "",
    /** 今日已购的每日特惠槽位下标（跨日后由购买流程整体重置）。 */
    @SerialName("DailyShopBought") var dailyShopBought: List<Int?> = emptyList(),
    /** 已领取奖励的成就 id（重复领取在服务层拒绝）。 */
    @SerialName("ClaimedAchievements") var claimedAchievements: List<String?> = emptyList(),
    // ── 2026-08 三期新增（带默认值：旧档缺字段自动落默认，向后兼容）──
    /** 抽卡历史（追加式，上限 [Companion.MAX_PULL_HISTORY] 丢最旧；仅展示用途）。 */
    @SerialName("PullHistory") var pullHistory: List<PullLogEntry?> = emptyList(),
    /** UP 定轨「上次歪了」的池标记（true = 下次最高稀有度必中 UP 角色）。 */
    @SerialName("GachaFeaturedLost") var gachaFeaturedLost: List<PoolFlagEntry?> = emptyList(),
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

        battleRecords = battleRecords.filterNotNull().let { list ->
            // P3-7：旧档可能携带超过上限的战绩（recordBattle 只裁剪新写入），载入时一并裁剪，
            // 否则超量条目永久保留在档里（对齐 recordBattle 的「上限 50 丢弃最旧」契约）。
            if (list.size > MAX_BATTLE_RECORDS) list.drop(list.size - MAX_BATTLE_RECORDS) else list
        }.toMutableList()

        if (softCurrency < 0) softCurrency = 0
        if (hardCurrency < 0) hardCurrency = 0
        if (userId.isEmpty()) userId = ""

        // 编队（2026-08）：过滤空槽/空 id、去重保首条、钳制上限——脏档不得让战斗构建越界。
        val seenFormation = HashSet<String>()
        formation = formation.filterNotNull()
            .filter { it.isNotEmpty() }
            .filter { seenFormation.add(it) }
            .take(MAX_FORMATION_SIZE)
            .toList()

        if (towerBestFloor < 0) towerBestFloor = 0

        // 每日商店（2026-08）：槽位下标去重、钳非负；日期串原样保留（空 = 从未购过）。
        dailyShopBought = dailyShopBought.filterNotNull()
            .filter { it >= 0 }
            .distinct()
            .toList()

        // 成就（2026-08）：id 去重保首条、滤空。
        val seenAchievements = HashSet<String>()
        claimedAchievements = claimedAchievements.filterNotNull()
            .filter { it.isNotEmpty() }
            .filter { seenAchievements.add(it) }
            .toList()

        // 抽卡历史（2026-08 三期）：滤 null、钳上限（超量丢最旧，对齐战绩裁剪契约）。
        pullHistory = pullHistory.filterNotNull().let { list ->
            if (list.size > MAX_PULL_HISTORY) list.drop(list.size - MAX_PULL_HISTORY) else list
        }.toMutableList()

        // UP 定轨标记：滤空 id、同池去重保首条。
        val seenFlagPools = HashSet<String>()
        gachaFeaturedLost = gachaFeaturedLost.filterNotNull()
            .filter { it.poolId.isNotEmpty() }
            .filter { seenFlagPools.add(it.poolId) }
            .toMutableList()
    }

    /** 编队 characterId 列表（已滤空槽；顺序即槽位顺序）。 */
    fun getFormationIds(): List<String> = formation.filterNotNull()

    /** 已领取的成就 id 列表（已滤空；命名避开 claimedAchievements 属性的 JVM getter 签名）。 */
    fun claimedAchievementIds(): List<String> = claimedAchievements.filterNotNull()

    /** 该池是否处于「UP 必中」状态（上次抽到最高稀有度但歪出了别的角色）。 */
    fun isFeaturedGuaranteed(poolId: String): Boolean =
        gachaFeaturedLost.firstOrNull { it?.poolId == poolId }?.flag ?: false

    /** 写入该池的 UP 定轨状态（不存在则新建条目，与 setGachaCounter 同范式）。 */
    fun setFeaturedGuaranteed(poolId: String, flag: Boolean) {
        val entry = gachaFeaturedLost.firstOrNull { it?.poolId == poolId }
        if (entry != null) entry.flag = flag
        else gachaFeaturedLost = gachaFeaturedLost + PoolFlagEntry(poolId = poolId, flag = flag)
    }

    /**
     * 追加一条抽卡历史并裁剪到上限（超限丢最旧）。
     * 整体替换列表引用而非原地改——与 recordBattle 的回滚语义配套（整体恢复原引用即可）。
     */
    fun appendPullHistory(entry: PullLogEntry) {
        pullHistory = (pullHistory.filterNotNull() + entry).let { list ->
            if (list.size > MAX_PULL_HISTORY) list.drop(list.size - MAX_PULL_HISTORY) else list
        }
    }

    /** 抽卡历史快照（已滤空；时间正序，最旧在前；UI 自行倒序展示）。 */
    fun pullHistoryEntries(): List<PullLogEntry> = pullHistory.filterNotNull()

    companion object {
        /** 战绩列表上限（recordBattle 与 sanitize 共用同一契约，禁止就地写 50）。 */
        const val MAX_BATTLE_RECORDS = 50

        /** 抽卡历史上限（appendPullHistory 与 sanitize 共用同一契约；约等于 10 次十连的回顾窗口）。 */
        const val MAX_PULL_HISTORY = 100

        /** 出战编队槽位上限（setFormation 与 sanitize 共用同一契约，禁止就地写 5）。 */
        const val MAX_FORMATION_SIZE = 5
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
