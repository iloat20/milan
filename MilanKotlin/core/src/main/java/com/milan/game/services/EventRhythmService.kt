package com.milan.game.services

import com.milan.game.data.*
import kotlin.random.Random

/**
 * 活动运营节奏服务。
 * 
 * 职责：
 * - 活动生命周期管理（开启、进行、结算）
 * - 活动任务进度追踪
 * - 活动商店兑换
 * - 签到系统
 * - 重置机制
 */
@Suppress("DEPRECATION")
class EventRhythmService(
    private val core: ServiceCore,
    private val rng: Random = Random.Default,
) : EventRhythmApi {

    /** 获取活动数据（契约名 [EventRhythmApi.getEventRhythmData]）。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override fun getEventRhythmData(): EventRhythmSaveData {
        // R5-I5：懒创建改纯读——默认值由 sanitize/createDefault 保证非 null，不再锁外写存档。
        return core.saveData.eventRhythmData ?: EventRhythmSaveData()
    }

    // ─────────────────── 活动管理 ───────────────────

    /** 获取当前活跃活动列表。 */
    override fun getActiveEvents(): List<GameEvent> {
        val now = System.currentTimeMillis()
        return getEventRhythmData().activeEvents.filterNotNull().filter { event ->
            event.isActive && now in event.startTime..event.endTime
        }
    }

    /** 获取指定类型的活动。 */
    override fun getEventsByType(type: EventType): List<GameEvent> {
        return getActiveEvents().filter { it.eventType == type.name }
    }

    /** 检查活动是否在进行中。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override fun isEventActive(eventId: String): Boolean {
        val event = getEventRhythmData().activeEvents.firstOrNull { it?.eventId == eventId } ?: return false
        val now = System.currentTimeMillis()
        return event.isActive && now in event.startTime..event.endTime
    }

    // ─────────────────── 任务进度 ───────────────────

    /** 更新任务进度。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override suspend fun updateEventTaskProgress(eventId: String, taskId: String, progress: Int): WriteOutcome {
        val data = getEventRhythmData()
        val event = data.activeEvents.firstOrNull { it?.eventId == eventId }
            ?: return WriteOutcome.Rejected

        val task = event.tasks.firstOrNull { it?.taskId == taskId }
            ?: return WriteOutcome.Rejected

        val origProgress = data.eventTaskProgress.toMap()

        return core.transaction(
            tag = "event.taskProgress",
            mutate = {
                val currentEventProgress = data.eventTaskProgress[eventId]?.toMutableMap()
                    ?: mutableMapOf()
                val currentTaskProgress = currentEventProgress[taskId] ?: 0
                currentEventProgress[taskId] = (currentTaskProgress + progress)
                    .coerceAtMost(task.target)
                data.eventTaskProgress = data.eventTaskProgress + (eventId to currentEventProgress)
            },
            rollback = {
                data.eventTaskProgress = origProgress
            },
            onCommit = {
                core.publishProgressionChanged()
            },
        )
    }

    /** 读取活动任务进度（UI 展示；进度本身由 updateEventTaskProgress 写入）。 */
    override fun getEventTaskProgress(eventId: String): Map<String, Int> {
        return getEventRhythmData().eventTaskProgress[eventId] ?: emptyMap()
    }

    /** 已领取的任务奖励 taskId 列表（UI 领取态）。 */
    override fun getClaimedEventTaskRewards(eventId: String): List<String> {
        return getEventRhythmData().claimedTaskRewards[eventId] ?: emptyList()
    }

    /** 领取任务奖励。 */
    override suspend fun claimEventTaskReward(eventId: String, taskId: String): WriteOutcome {
        val data = getEventRhythmData()
        val event = data.activeEvents.firstOrNull { it?.eventId == eventId }
            ?: return WriteOutcome.Rejected
        val task = event.tasks.firstOrNull { it?.taskId == taskId }
            ?: return WriteOutcome.Rejected

        val currentProgress = data.eventTaskProgress[eventId]?.get(taskId) ?: 0
        if (currentProgress < task.target) return WriteOutcome.Rejected
        // 已领取门控：防无限重复领取（永动机）
        val claimed = data.claimedTaskRewards[eventId] ?: emptyList()
        if (claimed.contains(taskId)) return WriteOutcome.Rejected

        val origSC = core.saveData.softCurrency
        val origHC = core.saveData.hardCurrency
        val origEventProgress = data.eventTaskProgress.toMap()
        val origClaimed = data.claimedTaskRewards
        val origBalances = data.eventCurrencyBalances.toMap()

        return core.transaction(
            tag = "event.claimTask",
            mutate = {
                // R5-C1：按 rewardType 分别入账。原 `else` 分支把 EVENT_CURRENCY /
                // ACTIVITY_POINTS / COLLABORATION_TOKENS 三种活动代币**一律当星尘发放**，
                // 而商店侧（旧实现）也只扣星尘 —— 活动商店因此退化为「星尘商店」，
                // 且物品定价（五星装备 3000）是按代币规模设计的，换成星尘后性价比失衡。
                when (task.rewardType) {
                    "HARD_CURRENCY" -> core.addCurrencyDelta(0, task.rewardAmount)
                    "SOFT_CURRENCY" -> core.addCurrencyDelta(task.rewardAmount, 0)
                    else -> addEventCurrency(task.rewardType, task.rewardAmount)
                }
                // 标记已领取
                data.claimedTaskRewards = data.claimedTaskRewards + (eventId to (claimed + taskId))
            },
            rollback = {
                core.saveData.softCurrency = origSC
                core.saveData.hardCurrency = origHC
                data.eventTaskProgress = origEventProgress
                data.claimedTaskRewards = origClaimed
                data.eventCurrencyBalances = origBalances
            },
            onCommit = {
                core.publishCurrencyChanged()
            },
        )
    }

    // ─────────────────── 活动商店 ───────────────────

    /** 兑换活动商店物品。 */
    override suspend fun redeemEventShopItem(eventId: String, itemId: String, amount: Int): WriteOutcome {
        val data = getEventRhythmData()
        val event = data.activeEvents.firstOrNull { it?.eventId == eventId }
            ?: return WriteOutcome.Rejected

        val shopItem = event.shopItems.firstOrNull { it?.itemId == itemId }
            ?: return WriteOutcome.Rejected

        // 数量下界校验（负数会让 totalCost 翻负 → 扣款变加钱）
        if (amount <= 0) return WriteOutcome.Rejected

        val currentRedemptions = data.shopRedemptions[eventId]?.get(itemId) ?: 0
        if (currentRedemptions + amount > shopItem.maxRedemptions) {
            return WriteOutcome.Rejected
        }

        // Long 中介防 Int 溢出翻负
        val totalCost = shopItem.price.toLong() * amount
        if (totalCost > Int.MAX_VALUE) return WriteOutcome.Rejected
        val cost = totalCost.toInt()

        val currencyType = shopItem.currencyType
        val origSC = core.saveData.softCurrency
        val origHC = core.saveData.hardCurrency
        val origRedemptions = data.shopRedemptions.toMap()
        val origBalances = data.eventCurrencyBalances.toMap()
        val origEquipments = core.saveData.ownedEquipments.toList()

        // 余额预检（锁外快照仍可能竞态，锁内再兜底）。
        // R5-C1：按商品自身标注的 currencyType 查余额——旧实现一律查/扣星尘，
        // 与 [EventShopItem.currencyType] 默认 `EVENT_CURRENCY` 的语义不符，
        // 导致活动商店实际是「星尘商店」。
        if (balanceOf(currencyType) < cost) return WriteOutcome.Rejected

        // C3：装备类商品（itemId 前缀 eq_ 或名称含装备语义）兑换后入库
        val equipmentTemplate = resolveEquipmentTemplate(shopItem)

        return core.transaction(
            tag = "event.shopRedeem",
            mutate = {
                spendCurrency(currencyType, cost)
                if (equipmentTemplate != null) {
                    repeat(amount) {
                        val drop = core.rollEquipmentFromTemplate(equipmentTemplate, level = 1)
                        core.saveData.ownedEquipments = core.saveData.ownedEquipments + listOf(drop)
                    }
                }
                val currentEventRedemptions = data.shopRedemptions[eventId]?.toMutableMap()
                    ?: mutableMapOf()
                currentEventRedemptions[itemId] = (currentEventRedemptions[itemId] ?: 0) + amount
                data.shopRedemptions = data.shopRedemptions + (eventId to currentEventRedemptions)
            },
            rollback = {
                core.saveData.softCurrency = origSC
                core.saveData.hardCurrency = origHC
                data.shopRedemptions = origRedemptions
                data.eventCurrencyBalances = origBalances
                core.saveData.ownedEquipments = origEquipments
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    /**
     * 装备类商品 → 模板解析（C3）。
     * 约定：itemId 前缀 `eq_`，或名称含「装备/剑/甲/盔」。稀有度按价格/名称抬升。
     */
    private fun resolveEquipmentTemplate(shopItem: EventShopItem): EquipmentData? {
        val templates = core.equipmentTemplates
        if (templates.isEmpty()) return null
        val isEquip = shopItem.itemId.startsWith("eq_") ||
            shopItem.name.contains("装备") || shopItem.name.contains("剑") ||
            shopItem.name.contains("甲") || shopItem.name.contains("盔")
        if (!isEquip) return null
        val rarityHint = when {
            shopItem.name.contains("UR") || shopItem.price >= 3000 -> 4
            shopItem.name.contains("SSR") || shopItem.price >= 1000 -> 3
            shopItem.name.contains("SR") || shopItem.price >= 400 -> 2
            else -> 1
        }
        return templates.firstOrNull { it.rarity == rarityHint } ?: templates.firstOrNull()
    }

    /** 获取活动商店物品列表。 */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override fun getEventShopItems(eventId: String): List<EventShopItem> {
        val event = getEventRhythmData().activeEvents.firstOrNull { it?.eventId == eventId }
            ?: return emptyList()
        return event.shopItems.filterNotNull()
    }

    /** 商店已兑换次数（itemId → count）。 */
    override fun getEventShopRedemptions(eventId: String): Map<String, Int> {
        return getEventRhythmData().shopRedemptions[eventId] ?: emptyMap()
    }

    // ─────────────────── 签到系统 ───────────────────

    /** 签到。 */
    override suspend fun signIn(eventId: String): WriteOutcome {
        val data = getEventRhythmData()
        val event = data.activeEvents.firstOrNull { it?.eventId == eventId }
            ?: return WriteOutcome.Rejected

        if (event.eventType != EventType.SIGN_IN.name) return WriteOutcome.Rejected

        val currentDays = data.signInProgress[eventId] ?: 0
        if (currentDays >= event.signInDays) return WriteOutcome.Rejected

        val origProgress = data.signInProgress.toMap()
        val origHard = core.saveData.hardCurrency
        val rewardAmount = 100 + currentDays * 20 // 递增奖励

        return core.transaction(
            tag = "event.signIn",
            mutate = {
                data.signInProgress = data.signInProgress + (eventId to (currentDays + 1))
                core.addCurrencyDelta(0, rewardAmount)
            },
            rollback = {
                data.signInProgress = origProgress
                core.saveData.hardCurrency = origHard
            },
            onCommit = {
                core.publishCurrencyChanged()
                core.publishProgressionChanged()
            },
        )
    }

    /** 获取签到进度。 */
    override fun getSignInProgress(eventId: String): Int {
        return getEventRhythmData().signInProgress[eventId] ?: 0
    }

    // ─────────────────── 活动代币 ───────────────────

    /** 读指定代币余额；未知类型或从未入账一律按 0 处理。 */
    override fun getEventCurrencyBalance(currencyType: String): Int {
        return getEventRhythmData().eventCurrencyBalances[currencyType] ?: 0
    }

    /**
     * 活动代币入账（R5-C1）。**须在临界区内调用**。
     *
     * 沿用 [ServiceCore.addCurrencyDelta] 的饱和钳制语义：`toLong() + delta` 预算后
     * `coerceIn(0, Int.MAX_VALUE)` 封顶——上溢时封顶而非翻负（与 R5-I11 口径一致）。
     * 负 delta（脏档或配置写反）会扣减，下限同样钳到 0，不会产生负余额。
     *
     * 未知代币类型直接忽略：宁可不发，也不写入无法被任何商店消费的幽灵余额
     * （与 sanitize 剔除未知类型的口径保持一致）。
     */
    private fun addEventCurrency(currencyType: String, delta: Int) {
        if (!EventCurrencyType.entries.any { it.name == currencyType }) return
        val data = getEventRhythmData()
        val current = data.eventCurrencyBalances[currencyType] ?: 0
        data.eventCurrencyBalances = data.eventCurrencyBalances + (
            currencyType to (current.toLong() + delta).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            )
    }

    /** 统一货币余额查询：星尘 / 星玉 / 活动代币走同一口径。 */
    private fun balanceOf(currencyType: String): Int = when (currencyType) {
        "SOFT_CURRENCY" -> core.saveData.softCurrency
        "HARD_CURRENCY" -> core.saveData.hardCurrency
        else -> getEventCurrencyBalance(currencyType)
    }

    /**
     * 统一货币扣减（R5-C1）。**须在临界区内调用**，调用方需已过余额预检。
     *
     * 走 [ServiceCore.addCurrencyDelta] 而非裸 `-=`，保证下溢时钳到 0 而非翻正。
     * 未知货币类型不做任何扣减——宁可兑换失败，也不静默扣错账户。
     */
    private fun spendCurrency(currencyType: String, cost: Int) {
        when (currencyType) {
            "SOFT_CURRENCY" -> core.addCurrencyDelta(softDelta = -cost)
            "HARD_CURRENCY" -> core.addCurrencyDelta(hardDelta = -cost)
            else -> addEventCurrency(currencyType, -cost)
        }
    }

    // ─────────────────── 活动模板 ───────────────────

    /** 获取默认活动定义列表。 */
    override fun getDefaultEventDefinitions(): List<EventDefinition> {
        return listOf(
            EventDefinition(
                eventType = EventType.LIMITED_GACHA,
                name = "限定角色卡池",
                description = "活动期间限定角色概率UP！",
                durationDays = 14,
                tasks = listOf(
                    EventTaskDefinition("DAILY_GACHA", "每日抽卡", 10, "HARD_CURRENCY", 100),
                    EventTaskDefinition("GACHA_100", "累计抽卡100次", 100, "HARD_CURRENCY", 500),
                ),
                shopItems = listOf(
                    EventShopItemDefinition("char限定", "限定角色碎片", 500, 5),
                    EventShopItemDefinition("eq限定", "限定装备", 1000, 1),
                ),
            ),
            EventDefinition(
                eventType = EventType.LIMITED_DUNGEON,
                name = "限时挑战副本",
                description = "挑战强力BOSS获取稀有奖励！",
                durationDays = 7,
                tasks = listOf(
                    EventTaskDefinition("DAILY_CLEAR", "每日通关3次", 3, "EVENT_CURRENCY", 300),
                    EventTaskDefinition("BOSS_CLEAR", "击败BOSS", 5, "HARD_CURRENCY", 300),
                ),
                shopItems = listOf(
                    EventShopItemDefinition("eq五星", "五星装备自选", 3000, 1),
                    EventShopItemDefinition("mat觉醒", "觉醒材料", 200, 20),
                ),
            ),
            EventDefinition(
                eventType = EventType.SIGN_IN,
                name = "七日签到",
                description = "每日签到领取丰厚奖励！",
                durationDays = 7,
                tasks = emptyList(),
                shopItems = emptyList(),
                // R5-C2：补 signInDays。缺失时实例化出的 GameEvent.signInDays 为 0，
                // signIn() 的 `currentDays >= signInDays` 首日即成立 → 签到恒 Rejected。
                signInDays = 7,
            ),
        )
    }

    // ─────────────────── 活动激活 ───────────────────

    /**
     * 确保已有活跃活动（R5-C2）：把 [getDefaultEventDefinitions] 的模板实例化并事务化落盘。
     *
     * 修复背景：[EventRhythmSaveData.activeEvents] 默认为空，且全项目**无任何生产代码写入**
     * （仅 `EconomyGuardRegressionTest` 手工构造过），导致 [getActiveEvents] 恒返回空、
     * [signIn] / [claimTaskReward] / [redeemShopItem] / [updateTaskProgress] 四个写操作
     * 首行 `?: return WriteOutcome.Rejected` 恒命中。
     *
     * **这正是 R5-C1~C3 经济漏洞长期处于「休眠态」的根本原因**——不是被防御逻辑挡住了，
     * 而是活动从未被激活过。接线活动 UI 前必须先补这条激活路径，否则接线即激活漏洞。
     *
     * 幂等性：已存在未过期活动时不重建（重建会让按 eventId 索引的任务进度/兑换记录失配）。
     *
     * 触发时机：进入活动页时 `LaunchedEffect(Unit) { ensureActiveEvents() }` 懒激活，
     * 对齐 R5-I4 每日任务重置的触发模式。**不可放进 sanitize**——
     * 违反 R5-I5「getter/sanitize 纯读，不在 writeMutex 外写存档」铁律。
     */
    override suspend fun ensureActiveEvents(): WriteOutcome {
        val now = System.currentTimeMillis()
        if (getActiveEvents().isNotEmpty()) return WriteOutcome.Success

        val data = getEventRhythmData()
        val events = getDefaultEventDefinitions().mapIndexed { index, def ->
            def.toGameEvent(startTime = now, index = index)
        }
        val origEvents = data.activeEvents
        return core.transaction(
            tag = "event.activate",
            mutate = {
                // 保留未过期的旧活动，追加新实例化的；上限由 sanitize 再兜一道。
                data.activeEvents = (
                    origEvents.filterNotNull().filter { it.endTime > now } + events
                    ).take(EventRhythmSaveData.MAX_ACTIVE_EVENTS)
            },
            rollback = { data.activeEvents = origEvents },
            onCommit = { core.publishProgressionChanged() },
        )
    }

    /**
     * 活动定义 → 可存档的活动实例。
     *
     * [index] 参与 eventId 构造：同一毫秒批量实例化多个活动时，仅靠时间戳会让 eventId
     * 碰撞，后续按 eventId 查找会取到错误的那一个（与 R5-T3 装备 ID 碰撞同类问题）。
     */
    private fun EventDefinition.toGameEvent(startTime: Long, index: Int): GameEvent = GameEvent(
        eventId = "evt_${eventType.name.lowercase()}_${startTime}_$index",
        eventType = eventType.name,
        name = name,
        description = description,
        startTime = startTime,
        endTime = startTime + durationDays * 86_400_000L,
        tasks = tasks.map { t ->
            EventTask(
                taskId = "${eventType.name}_${t.taskType}",
                taskType = t.taskType,
                name = t.name,
                description = "",
                target = t.target,
                rewardType = t.rewardType,
                rewardAmount = t.rewardAmount,
            )
        },
        shopItems = shopItems.map { s ->
            EventShopItem(
                itemId = s.itemId,
                name = s.name,
                price = s.price,
                currencyType = s.currencyType,
                maxRedemptions = s.maxRedemptions,
            )
        },
        signInDays = signInDays,
        isActive = true,
    )
}
