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
 *
 * ## 字段分组（2026-09-06 S7 重组：按业务域而非"新增时间"分组）
 *
 * | 分组 | 字段 | 说明 |
 * |---|---|---|
 * | 元信息 | Version / UserId / ServerSyncStatus | 存档版本与跨端同步标记 |
 * | 经济 | SoftCurrency / HardCurrency / Items | 星尘/钻石/道具 |
 * | 抽卡 | GachaCounters / PullHistory / TotalPullCount / GachaFeaturedLost | 保底/历史/累计/UP定轨 |
 * | 角色编队 | OwnedCharacters / OwnedSkins / Formation / TowerBestFloor / BattleRecords / CharacterAffinityData | 拥有/皮肤/出战/爬塔/战绩/好感 |
 * | 设置 | SoundEnabled / VibrationEnabled / PushEnabled | 偏好开关 |
 * | 商店 | DailyShopDate / DailyShopBought / ClaimedAchievements | 每日商店/成就领取 |
 * | 装备 | OwnedEquipments | 已拥有装备（保留字段，EquipmentService 已删 S2） |
 * | PVP | ArenaData / ArenaBattleRecords | 竞技场 |
 * | PVE（死字段） | AbyssData / DailyDungeonData | 保留序列化兼容，PvEService 已删 S2 |
 * | 检视 | InspectionData | 360° 检视系统 |
 * | 社交（死字段） | SocialData | 保留序列化兼容，SocialService 已删 S2 |
 * | 变现 | MonetizationData | 月卡/通行证/充值 |
 * | 活动 | EventRhythmData | 活动运营 |
 * | 剧情 | StoryData | 章节进度 |
 * | 每日任务 | DailyMissionData | 每日6任务+活跃度 |
 *
 * 死字段（装备/PVE/社交）保留是为序列化兼容旧档；ServiceCore 的 calculateEquipmentStats
 * 仍读 OwnedEquipments 喂战斗系统。重新接线见 docs/plans/2026-09-04-dead-feature-wiring-design.md。
 */
@Serializable
class SaveData(
    // ─────────── 元信息 ───────────
    @SerialName("Version") var version: Int = 1,
    @SerialName("UserId") var userId: String = "",
    @SerialName("ServerSyncStatus") var serverSyncStatus: Int = 0,

    // ─────────── 经济（星尘/钻石/道具）───────────
    @SerialName("SoftCurrency") var softCurrency: Int = DEFAULT_SOFT_CURRENCY,
    @SerialName("HardCurrency") var hardCurrency: Int = 0,
    @SerialName("Items") var items: List<ItemSaveState?> = emptyList(),

    // ─────────── 抽卡（保底/历史/累计/UP定轨）───────────
    @SerialName("GachaCounters") var gachaCounters: List<GachaCounterEntry?> = emptyList(),
    /** 抽卡历史（追加式，上限 [Companion.MAX_PULL_HISTORY] 丢最旧；仅展示用途）。 */
    @SerialName("PullHistory") var pullHistory: List<PullLogEntry?> = emptyList(),
    /**
     * 累计抽卡次数（2026-08 三期补；**永不清零**，与 [gachaCounters] 语义严格区分）。
     * [gachaCounters] 是保底计数，出货即归零——用它当「累计抽数」会让累计型成就进度
     * 随出货倒退（F3）。本字段是成就「寻访百次」等累计判定的唯一口径。
     */
    @SerialName("TotalPullCount") var totalPullCount: Int = 0,
    /** UP 定轨「上次歪了」的池标记（true = 下次最高稀有度必中 UP 角色）。 */
    @SerialName("GachaFeaturedLost") var gachaFeaturedLost: List<PoolFlagEntry?> = emptyList(),

    // ─────────── 角色与编队 ───────────
    @SerialName("OwnedCharacters") var ownedCharacters: List<CharacterSaveState?> = emptyList(),
    @SerialName("OwnedSkins") var ownedSkins: List<String?> = emptyList(),
    /** 出战编队（characterId 有序槽位，空槽为 null；上限 [Companion.MAX_FORMATION_SIZE]）。 */
    @SerialName("Formation") var formation: List<String?> = emptyList(),
    /** 无尽之塔历史最高层（0 = 尚未挑战）。 */
    @SerialName("TowerBestFloor") var towerBestFloor: Int = 0,
    @SerialName("BattleRecords") var battleRecords: List<BattleRecord?> = emptyList(),
    /** 角色好感度数据（角色ID → 好感度等级/经验）。 */
    @SerialName("CharacterAffinityData") var characterAffinityData: Map<String, Int?>? = null,
    /**
     * 好感等级奖励领取态（characterId → 已领取的等级档位列表，档位见 AffinityFormulas.LEVEL_REWARDS）。
     * 2026-09-10：此前 UI 只展示「规划中」，无领取路径；CLAIM_AFFINITY 任务类型也因此恒死。
     */
    @SerialName("ClaimedAffinityRewards") var claimedAffinityRewards: Map<String, List<Int?>?>? = null,

    // ─────────── 设置（开关类偏好持久化，避免重启即丢失；统一默认开启）───────────
    @SerialName("SoundEnabled") var soundEnabled: Boolean = true,
    @SerialName("VibrationEnabled") var vibrationEnabled: Boolean = true,
    @SerialName("PushEnabled") var pushEnabled: Boolean = true,

    // ─────────── 商店与成就领取 ───────────
    /** 每日商店归属的 UTC 日序号字符串（与当日不一致 = 跨日，已购列表作废重置）。 */
    @SerialName("DailyShopDate") var dailyShopDate: String = "",
    /** 今日已购的每日特惠槽位下标（跨日后由购买流程整体重置）。 */
    @SerialName("DailyShopBought") var dailyShopBought: List<Int?> = emptyList(),
    /** 已领取奖励的成就 id（重复领取在服务层拒绝）。 */
    @SerialName("ClaimedAchievements") var claimedAchievements: List<String?> = emptyList(),

    // ─────────── 装备（死字段保留，EquipmentService 已删 S2）───────────
    /** 已拥有的装备列表。ServiceCore.calculateEquipmentStats 仍读此喂战斗系统。 */
    @SerialName("OwnedEquipments") var ownedEquipments: List<EquipmentSaveState?> = emptyList(),

    // ─────────── PVP 竞技场 ───────────
    /** 竞技场数据。 */
    @SerialName("ArenaData") var arenaData: ArenaSaveData? = null,
    /** PVP战斗记录。 */
    @SerialName("ArenaBattleRecords") var arenaBattleRecords: List<PvPBattleRecord?> = emptyList(),

    // ─────────── PVE 内容（死字段保留，PvEService 已删 S2）───────────
    /** 深渊数据。 */
    @SerialName("AbyssData") var abyssData: AbyssSaveData? = null,
    /** 日常副本数据。 */
    @SerialName("DailyDungeonData") var dailyDungeonData: DailyDungeonSaveData? = null,

    // ─────────── 360° 检视系统 ───────────
    /** 360°检视系统数据。 */
    @SerialName("InspectionData") var inspectionData: InspectionSaveData? = null,

    // ─────────── 社交（死字段保留，SocialService 已删 S2）───────────
    /** 社交系统数据（好友、公会、赠礼）。 */
    @SerialName("SocialData") var socialData: SocialSaveData? = null,

    // ─────────── 变现模型 ───────────
    /** 变现模型数据（月卡、通行证、充值）。 */
    @SerialName("MonetizationData") var monetizationData: MonetizationSaveData? = null,

    // ─────────── 活动运营 ───────────
    /** 活动运营数据（活动、任务、商店、签到）。 */
    @SerialName("EventRhythmData") var eventRhythmData: EventRhythmSaveData? = null,

    // ─────────── 剧情系统 ───────────
    /** 剧情系统数据（章节进度、关卡完成、奖励领取）。 */
    @SerialName("StoryData") var storyData: StorySaveData? = null,

    // ─────────── 每日任务 ───────────
    /** 每日任务数据（每日6个任务、活跃度宝箱）。 */
    @SerialName("DailyMissionData") var dailyMissionData: DailyMissionSaveData? = null,

    // ─────────── 每日签到连续奖励 ───────────
    /** 每日签到数据（7天周期、连续签到、累计天数）。 */
    @SerialName("DailyCheckInData") var dailyCheckInData: DailyCheckInSaveData? = null,

    // ─────────── 赛季系统 ───────────
    /** 赛季数据（竞技场赛季、排名奖励）。 */
    @SerialName("SeasonData") var seasonData: SeasonSaveData? = null,

    // ─────────── 图鉴收集 ───────────
    /** 图鉴收集数据（角色解锁/收集里程碑）。 */
    @SerialName("CollectionData") var collectionData: CollectionSaveData? = null,

    // ─────────── 新手引导（第 8 节）───────────
    /** 新手引导进度；null=未开始（旧档兼容）。 */
    @SerialName("TutorialData") var tutorialData: TutorialSaveData? = null,
) {
    /**
     * 序列化为紧凑 JSON（落盘热路径，[json] 的 prettyPrint=false）。
     * 每次写事务都全量序列化，体积直接决定耗时与 IO 字节数，故不留缩进。
     */
    fun toJson(): String = json.encodeToString(serializer(), this)

    /** 格式化输出，仅用于调试/导出（人工阅读），不参与落盘。 */
    fun toPrettyJson(): String = prettyJson.encodeToString(serializer(), this)

    fun getGachaCounter(poolId: String): Int =
        gachaCounters.firstOrNull { it?.poolId == poolId }?.count ?: 0

    fun setGachaCounter(poolId: String, count: Int) {
        // 防御：保底计数器不得为负——反序列化脏数据 / 回滚计算溢出时钳到 0，
        // 与 sanitize() 的 onEach { it.count = it.count.coerceAtLeast(0) } 口径一致。
        val safe = count.coerceAtLeast(0)
        val entry = gachaCounters.firstOrNull { it?.poolId == poolId }
        if (entry != null) entry.count = safe
        else gachaCounters = gachaCounters + GachaCounterEntry(poolId = poolId, count = safe)
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
        // M7（2026-08-28 审查修复）：丢弃数量为 0 的道具条目。
        // 养成扣减把碎片扣到 0 后会残留 count=0 的幽灵条目（历史 BUG_REVIEW #10）。
        items = merged.values.filter { it.count > 0 }.toMutableList()

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
        // M7（2026-08-28 审查修复）：额外校验「成员必须已拥有」。旧档/内容变更可能残留
        // 未拥有的 id，会让编队页显示幽灵角色、战力预览与实战人数不符
        //（战斗侧 mapNotNull 会静默跳过，UI 却照常显示）。
        val seenFormation = HashSet<String>()
        val ownedIds = HashSet<String>().apply { unique.forEach { add(it.characterId) } }
        formation = formation.filterNotNull()
            .filter { it.isNotEmpty() }
            .filter { seenFormation.add(it) }
            .filter { it in ownedIds }
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

        // 累计抽数（F3）：负值脏档钳到 0，绝不因脏数据让累计型成就判定异常。
        if (totalPullCount < 0) totalPullCount = 0

        // UP 定轨标记：滤空 id、同池去重保首条。
        val seenFlagPools = HashSet<String>()
        gachaFeaturedLost = gachaFeaturedLost.filterNotNull()
            .filter { it.poolId.isNotEmpty() }
            .filter { seenFlagPools.add(it.poolId) }
            .toMutableList()
        
        // 装备系统：滤空 id、同装备去重保首条、钳制等级。
        val seenEquipments = HashSet<String>()
        ownedEquipments = ownedEquipments.filterNotNull()
            .filter { it.equipmentId.isNotEmpty() }
            .filter { seenEquipments.add(it.equipmentId) }
            .onEach {
                it.level = it.level.coerceIn(1, EquipmentSaveState.MAX_LEVEL)
                it.exp = it.exp.coerceAtLeast(0)
                it.subStats = it.subStats.filterNotNull().take(EquipmentSaveState.MAX_SUB_STATS)
            }
            .toMutableList()
        
        // 清理角色装备引用：移除不存在的装备ID。
        val ownedEquipmentIds = HashSet<String>().apply { 
            ownedEquipments.forEach { it?.let { equip -> add(equip.equipmentId) } }
        }
        for (char in unique) {
            val validEquipment = char.equipment.filter { (slot, equipId) ->
                equipId == null || equipId in ownedEquipmentIds
            }
            char.equipment = validEquipment
        }
        
        // 竞技场数据：确保防御队伍引用有效。
        arenaData?.let { arena ->
            val seenDefense = HashSet<String>()
            arena.defenseTeam = arena.defenseTeam.filterNotNull()
                .filter { it.isNotEmpty() }
                .filter { seenDefense.add(it) }
                .filter { it in ownedIds }
                .take(MAX_FORMATION_SIZE)
                .toList()
            
            // 积分钳制
            arena.arenaPoints = arena.arenaPoints.coerceIn(ArenaSaveData.MIN_POINTS, ArenaSaveData.MAX_POINTS)
            arena.attackCount = arena.attackCount.coerceAtLeast(0)
            arena.winCount = arena.winCount.coerceAtLeast(0)
            arena.loseCount = arena.loseCount.coerceAtLeast(0)
        }
        
        // PVP战斗记录：去重、滤空。
        val seenPvPRecords = HashSet<String>()
        arenaBattleRecords = arenaBattleRecords.filterNotNull()
            .filter { it.recordId.isNotEmpty() }
            .filter { seenPvPRecords.add(it.recordId) }
            .take(50)  // 保留最近50条
            .toMutableList()
        
        // 深渊数据：确保层数有效。
        abyssData?.let { abyss ->
            abyss.currentFloor = abyss.currentFloor.coerceIn(1, AbyssSaveData.MAX_FLOORS)
            abyss.currentStage = abyss.currentStage.coerceIn(1, AbyssSaveData.STAGES_PER_FLOOR)
            abyss.bestFloor = abyss.bestFloor.coerceIn(0, AbyssSaveData.MAX_FLOORS)
            abyss.totalStars = abyss.totalStars.coerceAtLeast(0)
            abyss.challengeCount = abyss.challengeCount.coerceAtLeast(0)
        }
        
        // 日常副本数据：确保挑战次数有效。
        dailyDungeonData?.let { dungeon ->
            val validCounts = mutableMapOf<String, Int>()
            for ((key, value) in dungeon.challengeCounts) {
                if (key.isNotEmpty()) {
                    validCounts[key] = (value ?: 0).coerceIn(0, DailyDungeonSaveData.MAX_CHALLENGES_PER_TYPE)
                }
            }
            dungeon.challengeCounts = validCounts
            dungeon.totalChallenges = dungeon.totalChallenges.coerceAtLeast(0)
        }
        
        // 检视系统数据：清理无效收藏照片和检视计数。
        inspectionData?.let { inspection ->
            // 收藏照片去重、滤空、钳上限。
            val seenPhotos = HashSet<String>()
            inspection.photoCollection = inspection.photoCollection.filterNotNull()
                .filter { it.photoId.isNotEmpty() }
                .filter { seenPhotos.add(it.photoId) }
                .take(InspectionSaveData.MAX_PHOTOS)
                .toMutableList()
            
            // 检视计数：滤空 id、钳非负。
            val validCounts = mutableMapOf<String, Int>()
            for ((key, value) in inspection.inspectionCounts) {
                if (key.isNotEmpty()) {
                    validCounts[key] = (value ?: 0).coerceAtLeast(0)
                }
            }
            inspection.inspectionCounts = validCounts
            
            // 已解锁动作：去重、滤空。
            val seenActions = HashSet<String>()
            inspection.unlockedActions = inspection.unlockedActions.filterNotNull()
                .filter { it.isNotEmpty() }
                .filter { seenActions.add(it) }
                .toList()
            
            // 已触发互动：去重、滤空。
            val seenInteractions = HashSet<String>()
            inspection.triggeredInteractions = inspection.triggeredInteractions.filterNotNull()
                .filter { it.isNotEmpty() }
                .filter { seenInteractions.add(it) }
                .toList()
        }
        
        // 社交系统数据：清理好友、赠礼、公会。
        socialData?.let { social ->
            // 好友列表：去重、滤空、钳上限。
            val seenFriends = HashSet<String>()
            social.friends = social.friends.filterNotNull()
                .filter { it.friendId.isNotEmpty() }
                .filter { seenFriends.add(it.friendId) }
                .take(SocialSaveData.MAX_FRIENDS)
                .toList()
            
            // 好友申请：去重、滤空、限 20 条。
            val seenRequests = HashSet<String>()
            social.friendRequests = social.friendRequests.filterNotNull()
                .filter { it.requestId.isNotEmpty() }
                .filter { seenRequests.add(it.requestId) }
                .take(20)
                .toList()
            
            // 已赠礼好友：去重、滤空、钳上限。
            val seenGifted = HashSet<String>()
            social.giftedFriends = social.giftedFriends.filterNotNull()
                .filter { it.isNotEmpty() }
                .filter { seenGifted.add(it) }
                .take(SocialSaveData.DAILY_GIFT_LIMIT)
                .toList()
            
            // 体力：钳上限。
            social.receivedStamina = social.receivedStamina.coerceIn(0, SocialSaveData.MAX_RECEIVED_STAMINA)
            social.guildContributions = social.guildContributions.coerceAtLeast(0)
        }
        
        // 变现模型数据：确保月卡天数、通行证等级有效。
        monetizationData?.let { mono ->
            mono.monthlyCardDaysLeft = if (mono.monthlyCardActive) {
                mono.monthlyCardDaysLeft.coerceIn(0, MonetizationSaveData.MONTHLY_CARD_DURATION)
            } else {
                -1
            }
            mono.battlePassLevel = mono.battlePassLevel.coerceIn(1, MonetizationSaveData.BP_MAX_LEVEL)
            mono.battlePassExp = mono.battlePassExp.coerceAtLeast(0)
            mono.totalChargeAmount = mono.totalChargeAmount.coerceAtLeast(0)
            
            // 已领取通行证奖励：去重、滤空、钳有效等级。
            val seenBP = HashSet<Int>()
            mono.claimedBPRewards = mono.claimedBPRewards.filterNotNull()
                .filter { it in 1..MonetizationSaveData.BP_MAX_LEVEL }
                .filter { seenBP.add(it) }
                .toList()
            
            // 首充档位：去重、滤空。
            val seenFC = HashSet<String>()
            mono.firstChargeClaimed = mono.firstChargeClaimed.filterNotNull()
                .filter { it.isNotEmpty() }
                .filter { seenFC.add(it) }
                .toList()
            
            // 累计充值里程碑：去重、滤空。
            val seenCM = HashSet<Int>()
            mono.claimedChargeMilestones = mono.claimedChargeMilestones.filterNotNull()
                .filter { it > 0 }
                .filter { seenCM.add(it) }
                .toList()
            
            // 限时礼包：去重、滤空。
            val seenTL = HashSet<String>()
            mono.purchasedTimeLimited = mono.purchasedTimeLimited.filterNotNull()
                .filter { it.isNotEmpty() }
                .filter { seenTL.add(it) }
                .toList()
        }
        
        // 活动运营数据：清理活跃活动、商店兑换、任务进度、签到。
        eventRhythmData?.let { evt ->
            // 活跃活动：滤空、钳上限。
            val now = System.currentTimeMillis()
            evt.activeEvents = evt.activeEvents.filterNotNull()
                .filter { it.eventId.isNotEmpty() }
                .take(EventRhythmSaveData.MAX_ACTIVE_EVENTS)
                .toList()
            
            // 已完成活动：去重、滤空。
            val seenCompleted = HashSet<String>()
            evt.completedEvents = evt.completedEvents.filterNotNull()
                .filter { it.isNotEmpty() }
                .filter { seenCompleted.add(it) }
                .toList()
            
            // 商店兑换：清理无效活动ID，钳上限。
            val validShop = mutableMapOf<String, Map<String, Int>>()
            for ((eventId, redemptions) in evt.shopRedemptions) {
                if (eventId.isNotEmpty()) {
                    val validRedemptions = mutableMapOf<String, Int>()
                    for ((itemId, count) in redemptions) {
                        if (itemId.isNotEmpty()) {
                            validRedemptions[itemId] = count
                                .coerceIn(0, EventRhythmSaveData.MAX_SHOP_REDEMPTION_PER_ITEM)
                        }
                    }
                    if (validRedemptions.isNotEmpty()) {
                        validShop[eventId] = validRedemptions
                    }
                }
            }
            evt.shopRedemptions = validShop
            
            // 任务进度：清理无效活动/任务ID。
            val validTasks = mutableMapOf<String, Map<String, Int>>()
            for ((eventId, tasks) in evt.eventTaskProgress) {
                if (eventId.isNotEmpty() && tasks != null) {
                    val validTaskProgress = mutableMapOf<String, Int>()
                    for ((taskId, progress) in tasks) {
                        if (taskId.isNotEmpty()) {
                            validTaskProgress[taskId] = progress.coerceAtLeast(0)
                        }
                    }
                    if (validTaskProgress.isNotEmpty()) {
                        validTasks[eventId] = validTaskProgress
                    }
                }
            }
            evt.eventTaskProgress = validTasks
            
            // 签到进度：清理无效ID、钳非负。
            val validSignIn = mutableMapOf<String, Int>()
            for ((eventId, days) in evt.signInProgress) {
                if (eventId.isNotEmpty()) {
                    validSignIn[eventId] = (days ?: 0).coerceAtLeast(0)
                }
            }
            evt.signInProgress = validSignIn

            // 活动代币余额（R5-C1）：滤空键、钳非负；未知代币类型一并剔除，
            // 避免脏档残留无法被任何商店消费的幽灵余额。
            val validBalances = mutableMapOf<String, Int>()
            for ((currencyType, balance) in evt.eventCurrencyBalances) {
                if (currencyType.isNotEmpty() &&
                    EventCurrencyType.entries.any { it.name == currencyType }
                ) {
                    validBalances[currencyType] = balance.coerceAtLeast(0)
                }
            }
            evt.eventCurrencyBalances = validBalances
        }

        // R5-I5（2026-09-03 审查修复）：为所有「按需懒创建的嵌套数据」补齐默认实例，
        // 使 load/createDefault 产出后各字段恒非 null。这样服务层的 getXxxData() 只读接口
        // 不再需要 `?: XxxData().also { saveData.x = it }` 的锁外懒写——旧实现会在 writeMutex
        // 外改写共享存档，且与 resetSave 整体替换 saveData 引用并发竞态（check-then-act）。
        // 默认实例是干净初值，无需再过 sanitize 的 `.let` 钳制块。
        if (arenaData == null) arenaData = ArenaSaveData()
        if (abyssData == null) abyssData = AbyssSaveData()
        if (dailyDungeonData == null) dailyDungeonData = DailyDungeonSaveData()
        if (inspectionData == null) inspectionData = InspectionSaveData()
        if (socialData == null) socialData = SocialSaveData()
        if (monetizationData == null) monetizationData = MonetizationSaveData()
        if (eventRhythmData == null) eventRhythmData = EventRhythmSaveData()
        if (storyData == null) storyData = StorySaveData()
        if (dailyMissionData == null) dailyMissionData = DailyMissionSaveData()
        // 2026-09-08 补漏（回归测试 CollectionUnlockDerivationTest 红→绿暴露）：
        // 初版 R5-I5 清单漏了这三个同样「服务层 `?: XxxData()` 只读兜底」的子系统。
        // 缺省时其写事务 mutate 的是兜底瞬态对象、从不回写 saveData.xxxData →
        // 里程碑/签到/赛季领取态静默丢失，可无限重复领取（货币刷取）。补上后与其余子系统一致。
        if (dailyCheckInData == null) dailyCheckInData = DailyCheckInSaveData()
        if (seasonData == null) seasonData = SeasonSaveData()
        if (collectionData == null) collectionData = CollectionSaveData()
        if (characterAffinityData == null) characterAffinityData = emptyMap()
// 新手引导：旧档无键 → 空初值（未完成、未跳过），与其余子系统同范式
        if (tutorialData == null) tutorialData = TutorialSaveData()
        if (claimedAffinityRewards == null) claimedAffinityRewards = emptyMap()
        // 好感领取档位去空 + 去负
        claimedAffinityRewards = claimedAffinityRewards?.mapValues { (_, levels) ->
            levels?.filterNotNull()?.filter { it > 0 }?.distinct() ?: emptyList()
        } ?: emptyMap()
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
        /**
         * 新档起始星尘（H1，2026-08-28 审查修复）：原为 **999999**，系开发期调试遗留——
         * 新玩家开局即可十连 600+ 次，抽卡与养成经济被完全压平。
         * 1600 恰好等于一次十连（[GameService] 的 TenCost），保留「先来一发十连」的开局体验。
         */
        const val DEFAULT_SOFT_CURRENCY = 1600

        /** 战绩列表上限（recordBattle 与 sanitize 共用同一契约，禁止就地写 50）。 */
        const val MAX_BATTLE_RECORDS = 50

        /** 抽卡历史上限（appendPullHistory 与 sanitize 共用同一契约；约等于 10 次十连的回顾窗口）。 */
        const val MAX_PULL_HISTORY = 100

        /** 出战编队槽位上限（setFormation 与 sanitize 共用同一契约，禁止就地写 5）。 */
        const val MAX_FORMATION_SIZE = 5
        /**
         * ignoreUnknownKeys：旧版/未来字段不致命（System.Text.Json 默认忽略未知属性，对齐）。
         * coerceInputValues：JSON 显式 null 落到非空字段时用默认值而非抛异常（C# null 覆盖 + Sanitize 兜底）。
         *
         * prettyPrint=false（2026-09-08 P0-1）：存档是机器读的热路径产物，**每次写事务都全量序列化**，
         * 缩进与换行使体积膨胀约 30–50%，直接放大序列化耗时与 IO 字节数。
         * 紧凑格式与反序列化完全兼容（JSON 空白无语义，[tryParse] 无格式假设），无迁移成本。
         * 需要人工阅读时用 [toPrettyJson]（调试/导出路径，非热路径）。
         */
        val json: Json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /** 调试/导出用格式化输出（不用于落盘热路径）。 */
        private val prettyJson: Json = Json {
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

        fun createDefault(): SaveData = SaveData().apply { sanitize() }
    }
}
