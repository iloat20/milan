package com.milan.game.services

import com.milan.game.data.BattleRecord
import com.milan.game.data.CharacterSaveState
import com.milan.game.data.EquipmentSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.Rarity
import com.milan.game.data.SaveData
import com.milan.game.data.SaveManager
import com.milan.game.data.StatValue
import com.milan.game.domain.battle.UnitStats
import com.milan.game.domain.gacha.GachaEngine
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.domain.progression.ProgressionEngine
import com.milan.game.domain.progression.StatsCalculator
import com.milan.game.domain.progression.TalentEngine
import com.milan.game.infrastructure.eventbus.CurrencyChanged
import com.milan.game.infrastructure.eventbus.EventBus
import com.milan.game.infrastructure.eventbus.ProgressionChanged
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString

/**
 * 服务层共享内核（2026-08-28 P1 重构：自 [GameService] 上帝类拆出）。
 *
 * **为什么需要它**：原 `GameService` 单类 1385 行 / 46 个公开方法，聚合了抽卡、养成、爬塔、
 * 经济、元进度五个业务域。拆分为五个服务后，这些服务**共享同一份可变状态**（存档、写锁、
 * 快照、领域引擎），必须由单一持有者统一管理——否则写锁与快照会分裂成多份，事务范式失效。
 *
 * **职责边界（严格）**：
 * - 只提供「状态 + 事务模板 + 跨服务共享的原子辅助」，**不含任何业务规则**；
 * - 业务规则一律在 `GachaService` / `ProgressionService` / `TowerService` /
 *   `EconomyService` / `MetaService` 五个聚合服务里实现；
 * - 五个聚合服务之间**互不调用**（已验证：所有方法只依赖本内核），因此不存在
 *   [writeMutex] 重入问题——Mutex 不可重入，跨服务加锁会直接死锁。
 *
 * 事务范式（AGENTS.md 红线，拆分后保持不变）：
 * 预算/校验 → 改内存 → 落盘 → 失败回滚 → 仅成功广播；回滚路径不广播事件。
 */

/** 装备属性加成聚合（含暴击，UnitStats 无此字段，由 [calculateEquipmentStats] 返回）。 */
private data class EquipmentStatBonus(
    val atk: Int = 0,
    val def: Int = 0,
    val hp: Int = 0,
    val spd: Int = 0,
    val critRate: Double = 0.0,
    val critDmg: Double = 0.0,
    /** 套装百分比（R6-P1：isPercentage=true 时按基数百分比加成，不可当 flat 加）。 */
    val atkPct: Float = 0f,
    val defPct: Float = 0f,
    val hpPct: Float = 0f,
)

class ServiceCore(
    val saveManager: SaveManager,
    val gacha: GachaEngine,
    val talent: TalentEngine,
    val progression: ProgressionEngine,
    val rng: Random,
    val onTrace: (String) -> Unit,
    /** UTC 日序号提供器（每日商店按天重置；注入便于测试固定「今天」）。 */
    val today: () -> Long,
) {

    // ── 串行写锁（2026-08 主线程 IO 异步化）──
    // 所有写操作 suspend：内存变更与落盘在同一临界区内串行执行，落盘经 IO 线程移出主线程。
    // 注意 Mutex 不可重入：持锁调用方必须走 [transactionLocked]（不重复加锁）。
    val writeMutex = Mutex()

    // ── 单写者调度器（2026-09-08 P0-1）──
    // 限制 IO 写操作并发为 1，避免多个写事务同时落盘导致文件锁竞争或写顺序错乱。
    // 与 writeMutex 互补：writeMutex 保证内存变更串行，writeDispatcher 保证 IO 串行。
    @PublishedApi internal val writeDispatcher = Dispatchers.IO.limitedParallelism(1)

    // ── 状态快照（UI 订阅 StateFlow，替代「EventBus 轻标记 + 手动重读」）──
    private val _snapshot = MutableStateFlow(GameSnapshot(0, 0, 0, 0, 0))
    val snapshot: StateFlow<GameSnapshot> = _snapshot.asStateFlow()

    // ── 快照切片（2026-09-08 P0-4）──
    // 与全量 snapshot 并存，供 Screen 按关注点订阅；内容未变则不发射（见 [setIfChanged]），
    // 避免「改了一个开关导致货币 UI 重组」这类无谓重算。全部 Screen 迁完前勿删全量。
    private val _economy = MutableStateFlow(EconomySlice())
    val economy: StateFlow<EconomySlice> = _economy.asStateFlow()

    private val _roster = MutableStateFlow(RosterSlice())
    val roster: StateFlow<RosterSlice> = _roster.asStateFlow()

    private val _progress = MutableStateFlow(ProgressSlice())
    val progress: StateFlow<ProgressSlice> = _progress.asStateFlow()

    private val _gachaSlice = MutableStateFlow(GachaSlice())
    val gachaSlice: StateFlow<GachaSlice> = _gachaSlice.asStateFlow()

    private val _meta = MutableStateFlow(MetaSlice())
    val meta: StateFlow<MetaSlice> = _meta.asStateFlow()

    /**
     * 内容未变则不写入——StateFlow 以引用/相等性判定发射，写入相等值仍会触发订阅者。
     * 切片多为 data class，`!=` 即值比较，可挡掉无关字段变化引发的重算。
     *
     * 例外：[RosterSlice.ownedSaves] 的值是 [CharacterSaveState]（普通 class，引用相等），
     * 而每次刷新都会重新拷贝，故 roster 切片总被视为「已变」——已知限制，
     * 根治需把 ownedSaves 改按角色 id 懒加载（P1）。
     */
    private fun <T> MutableStateFlow<T>.setIfChanged(next: T) {
        if (value != next) value = next
    }

    /** 每次成功写操作后统一刷新快照；revision 是重组触发器。 */
    fun refreshSnapshot() {
        val ownedFp = ownedFingerprint()
        // 货币/开关类写：养成指纹未变时复用上次 ownedSaves 拷贝，省 O(n) 分配 + GC。
        val ownedSaves = if (ownedFp == lastOwnedFingerprint && lastOwnedSaves.isNotEmpty()) {
            lastOwnedSaves
        } else {
            saveData.ownedCharacters.filterNotNull()
                .associate { it.characterId to it.toSnapshotCopy() }
                .also {
                    lastOwnedFingerprint = ownedFp
                    lastOwnedSaves = it
                }
        }
        val snap = GameSnapshot(
            revision = _snapshot.value.revision + 1,
            softCurrency = saveData.softCurrency,
            hardCurrency = saveData.hardCurrency,
            starFragments = getStarFragments(),
            ownedCount = saveData.ownedCharacters.size,
            soundEnabled = saveData.soundEnabled,
            vibrationEnabled = saveData.vibrationEnabled,
            pushEnabled = saveData.pushEnabled,
            reduceMotionEnabled = saveData.reduceMotionEnabled,
            fontScaleTier = saveData.fontScaleTier.coerceIn(0, 2),
            pityByPool = pools.associate { it.poolId to saveData.getGachaCounter(it.poolId) },
            ownedSaves = ownedSaves,
            formation = saveData.getFormationIds(),
            towerBestFloor = saveData.towerBestFloor,
            battleTickets = itemCount(BattleTicketItemId),
            // UP 定轨状态：GachaScreen 展示「下次必中」标记
            featuredLostByPool = pools.associate { it.poolId to saveData.isFeaturedGuaranteed(it.poolId) },
        )
        _snapshot.value = snap
        // 切片同步：内容未变则不发射（setIfChanged），无关字段变化不再惊动订阅者。
        _economy.setIfChanged(
            EconomySlice(
                softCurrency = snap.softCurrency,
                hardCurrency = snap.hardCurrency,
                starFragments = snap.starFragments,
                battleTickets = snap.battleTickets,
                dailyShopDate = saveData.dailyShopDate,
                dailyBought = saveData.dailyShopBought.filterNotNull(),
            )
        )
        _roster.setIfChanged(
            RosterSlice(
                ownedCount = snap.ownedCount,
                formation = snap.formation,
                ownedFingerprint = ownedFp,
            )
        )
        _progress.setIfChanged(
            ProgressSlice(
                towerBestFloor = snap.towerBestFloor,
                affinityTotal = saveData.characterAffinityData.orEmpty().values.sumOf { it ?: 0 },
                affinityClaims = saveData.claimedAffinityRewards.orEmpty().values.sumOf { it?.size ?: 0 },
            )
        )
        _gachaSlice.setIfChanged(GachaSlice(snap.pityByPool, snap.featuredLostByPool))
        _meta.setIfChanged(
            MetaSlice(
                soundEnabled = snap.soundEnabled,
                vibrationEnabled = snap.vibrationEnabled,
                pushEnabled = snap.pushEnabled,
                reduceMotionEnabled = snap.reduceMotionEnabled,
                fontScaleTier = snap.fontScaleTier,
            )
        )
    }

    /** 角色养成指纹（列表类 VM 的重建门控；不含装备——装备写走 Detail 页全量快照）。 */
    private fun ownedFingerprint(): Long {
        var h = 1125899906842597L
        for (ch in saveData.ownedCharacters) {
            if (ch == null) continue
            h = 31 * h + ch.characterId.hashCode()
            h = 31 * h + ch.level
            h = 31 * h + ch.stage
            h = 31 * h + ch.stars
            h = 31 * h + ch.totalExp
            h = 31 * h + ch.unspentPoints
            h = 31 * h + ch.talentPoints.size
        }
        return h
    }

    private var lastOwnedFingerprint: Long = 0L
    private var lastOwnedSaves: Map<String, CharacterSaveState> = emptyMap()

    /** 存档整体替换（resetSave）后必须清空指纹缓存，否则复用陈旧拷贝。 */
    fun invalidateOwnedSavesCache() {
        lastOwnedFingerprint = 0L
        lastOwnedSaves = emptyMap()
    }

    /** 快照拷贝：显式构造副本，防快照持有可变存档引用（resetSave 后陈旧）。 */
    private fun CharacterSaveState.toSnapshotCopy(): CharacterSaveState = CharacterSaveState(
        characterId = characterId,
        level = level,
        stage = stage,
        stars = stars,
        totalExp = totalExp,
        unspentPoints = unspentPoints,
        talentPoints = talentPoints,
    )

    /**
     * 进程级存档引用（与 SaveManager.current 同一对象，写操作原地修改后 [save] 持久化）。
     * 重置存档时整体替换为新档引用。
     */
    var saveData: SaveData = saveManager.load()

    // ── 内容数据（赋值时同步重建索引，任何加载路径都不会漏）──

    var characters: List<CharacterDataEntry> = emptyList()
        set(value) {
            field = value
            charactersById = value.associateBy { it.characterId }
        }
    var pools: List<GachaPoolDataEntry> = emptyList()
    var talentTrees: List<TalentTreeData> = emptyList()
        set(value) {
            field = value
            talentTreesById = value.associateBy { it.treeId }
            // R4-05：前置索引随内容一次性重建，读者永不写（见 [prereqMap]）。
            prereqIndex = value.associate { t ->
                t.treeId to t.nodes.associate { n -> n.nodeId to n.prerequisiteNodeIds }
            }
        }
    
    // ── 装备系统新增 ──
    var equipmentTemplates: List<EquipmentData> = emptyList()
    var equipmentSets: List<EquipmentSetData> = emptyList()

    /**
     * 按模板实例化装备（主属性 + 按稀有度随机副词条）。纯函数，不写存档。
     * 放在 ServiceCore：模板与 rng 在此，且 TowerService 等聚合服务**互不调用**，
     * 掉落生成只能经内核共用（C3，2026-09-09）。
     */
    fun rollEquipmentFromTemplate(template: EquipmentData, level: Int = 1): EquipmentSaveState {
        val instanceId =
            "eq_${template.equipmentId}_${System.currentTimeMillis()}_${rng.nextInt(100000)}"
        val mainSource = template.baseStats.firstOrNull()
            ?: StatData(StatValue.STAT_ATTACK, 1, 1, false, 100)
        val mainStat = StatValue(
            statType = mainSource.statType,
            value = rollRange(mainSource.minValue, mainSource.maxValue),
            isPercentage = mainSource.isPercentage,
        )
        val wantSubs = when (template.rarity) {
            4 -> 4
            3 -> 3
            2 -> 2
            else -> 1
        }.coerceAtMost(EquipmentSaveState.MAX_SUB_STATS)

        val pool = template.subStatPool.filter { it.statType != mainStat.statType }
        val rolled = mutableMapOf<String, StatValue>()
        val remaining = pool.toMutableList()
        while (rolled.size < wantSubs && remaining.isNotEmpty()) {
            val totalWeight = remaining.sumOf { it.weight.coerceAtLeast(1) }.coerceAtLeast(1)
            var pick = rng.nextInt(totalWeight)
            var chosen = remaining[0]
            for (stat in remaining) {
                pick -= stat.weight.coerceAtLeast(1)
                if (pick < 0) {
                    chosen = stat
                    break
                }
            }
            remaining.remove(chosen)
            rolled[chosen.statType] = StatValue(
                statType = chosen.statType,
                value = rollRange(chosen.minValue, chosen.maxValue),
                isPercentage = chosen.isPercentage,
            )
        }
        return EquipmentSaveState(
            equipmentId = instanceId,
            templateId = template.equipmentId,
            level = level.coerceIn(1, template.maxLevel),
            exp = 0,
            mainStat = mainStat,
            subStats = rolled.values.toList(),
            locked = false,
        )
    }

    private fun rollRange(min: Int, max: Int): Int =
        if (max > min) rng.nextInt(min, max + 1) else min

    /**
     * 爬塔里程碑掉落：仅「层数为 10 的倍数」时产出。不入库、不加锁。
     * 稀有度随层数：≥50 UR / ≥30 SSR / ≥15 SR / 其余 R。
     */
    fun rollTowerEquipmentDrop(floor: Int): EquipmentSaveState? {
        if (floor <= 0 || floor % 10 != 0) return null
        if (equipmentTemplates.isEmpty()) return null
        val rarity = when {
            floor >= 50 -> 4
            floor >= 30 -> 3
            floor >= 15 -> 2
            else -> 1
        }
        val candidates = equipmentTemplates.filter { it.rarity == rarity }
            .ifEmpty { equipmentTemplates }
        return rollEquipmentFromTemplate(candidates[rng.nextInt(candidates.size)], level = 1)
    }

    private var charactersById: Map<String, CharacterDataEntry> = emptyMap()
    private var talentTreesById: Map<String, TalentTreeData> = emptyMap()

    /** 按 ID 查角色定义（O(1) 索引）。 */
    fun character(id: String): CharacterDataEntry? = charactersById[id]

    /** 按 ID 查天赋树（O(1) 索引）。 */
    fun talentTree(id: String): TalentTreeData? = talentTreesById[id]

    // ─────────────────────────── 事务模板 ───────────────────────────

    /**
     * 事务模板核心。**调用方必须已持有 [writeMutex]**（Mutex 不可重入）。
     * mutate 改内存 → 落盘成功 → onCommit；落盘失败 → rollback 恢复内存 → 留痕 → SaveFailed。
     *
     * R4-06（2026-08-30 审查修复）：**mutate 自身抛异常也纳入事务语义**。
     * 旧实现只覆盖「落盘失败」，mutate 抛异常时内存已被部分改写（如十连循环跑到第 3 次）、
     * 而 rollback 不执行、save() 不执行、onTrace 不留痕；锁随 withLock 的 finally 释放后，
     * 残留的半截状态会被后续任意一次成功落盘持久化 —— 磁盘与内存静默分叉且无排查线索。
     * 现行为：先回滚（回滚自身失败单独留痕，不掩盖原始异常）→ 留痕 → 原样上抛。
     */
    suspend inline fun transactionLocked(
        tag: String,
        mutate: () -> Unit,
        rollback: () -> Unit,
        onCommit: () -> Unit,
    ): WriteOutcome {
        try {
            mutate()
        } catch (t: Throwable) {
            try {
                rollback()
            } catch (rt: Throwable) {
                onTrace("$tag.rollback.threw: ${rt.message}")
            }
            onTrace("$tag.mutate.threw: ${t.message}")
            throw t
        }
        val saved = withContext(writeDispatcher) { saveManager.save() }
        if (saved) {
            onCommit()
            return WriteOutcome.Success
        }
        rollback()
        onTrace("$tag.save.failed: rolled back")
        return WriteOutcome.SaveFailed
    }

    /** 事务模板（自动持 [writeMutex]；单操作写入口用）。 */
    suspend inline fun transaction(
        tag: String,
        mutate: () -> Unit,
        rollback: () -> Unit,
        onCommit: () -> Unit,
    ): WriteOutcome {
        // R5/M2 根治（2026-09-06 S1）：dispatch 出 writeMutex 临界区。
        // 原实现：onCommit 闭包内调 [publishCurrencyChanged]/[publishProgressionChanged]，
        // 后者直接 EventBus.dispatch()，在 writeMutex 持锁上下文执行订阅者 handler。
        // 若订阅者 handler 内调用任意 service 写（再 transaction → writeMutex.withLock），
        // 即 Mutex 不可重入死锁。当前零订阅者故未爆雷，接线即激活。
        // 现约定：[publishCurrencyChanged]/[publishProgressionChanged] 只入队 + 刷新快照，
        // dispatch 由本模板在出锁后统一执行（[withWriteLock] 同范式）。
        val outcome = writeMutex.withLock { transactionLocked(tag, mutate, rollback, onCommit) }
        EventBus.dispatch()
        return outcome
    }

    /**
     * 持锁执行写操作并在出锁后派发积压事件。统一收口 [writeMutex] 临界区模式：
     * - 替代原手动范式 `core.writeMutex.withLock { ... transactionLocked(...) }`，
     *   所有聚合服务的写入口现已全量迁移到本模板（21 处调用点，2026-09-06 S1），
     *   确保 dispatch 永远在锁外（防订阅者 handler 重入死锁）；
     * - 单独的 `transaction` 模板（自动范式）内部也走本模板范式。
     */
    suspend inline fun <T> withWriteLock(action: () -> T): T {
        val result = writeMutex.withLock { action() }
        EventBus.dispatch()
        return result
    }

    // ─────────────────────────── 事件发布 ───────────────────────────

    /**
     * 经济变动统一出口：刷新快照 + 入队事件。
     *
     * **不在此 dispatch**（2026-09-06 S1 改造）：原实现 `EventBus.dispatch()` 在
     * [writeMutex] 持锁上下文执行订阅者 handler，订阅者内再调 service 写即死锁
     * （Mutex 不可重入）。现约定 dispatch 由 [transaction]/[withWriteLock] 在出锁后统一执行。
     * 直接调用本函数的场景（非经 transaction 模板）需自行在临界区外调 [EventBus.dispatch]。
     */
    fun publishCurrencyChanged() {
        refreshSnapshot()
        EventBus.publish(CurrencyChanged)
    }

    /**
     * 养成变动统一出口（事件无载荷，订阅方自行重读当前角色）。
     *
     * **不在此 dispatch**：同 [publishCurrencyChanged] 注释。dispatch 由 [transaction]/[withWriteLock]
     * 在出 [writeMutex] 临界区后统一执行，防订阅者 handler 重入死锁。
     */
    fun publishProgressionChanged() {
        refreshSnapshot()
        EventBus.publish(ProgressionChanged)
    }

    // ─────────────────────────── 共享原子辅助 ───────────────────────────

    /** 当前持有的星魂碎片。 */
    fun getStarFragments(): Int =
        saveData.items.firstOrNull { it?.itemId == StarFragmentItemId }?.count ?: 0

    /** 取角色存档；未拥有返回 null。 */
    fun getSave(charId: String): CharacterSaveState? =
        saveData.ownedCharacters.firstOrNull { it?.characterId == charId }

    /** 道具数量（不存在视为 0）。须在临界区内调用。 */
    fun itemCount(itemId: String): Int =
        saveData.items.firstOrNull { it?.itemId == itemId }?.count ?: 0

    /** 道具数量增减（delta=0 无操作；条目缺失时以 delta 建档）。须在临界区内调用。 */
    fun addItemDelta(itemId: String, delta: Int) {
        if (delta == 0) return
        val item = saveData.items.firstOrNull { it?.itemId == itemId }
        if (item != null) item.count += delta
        else saveData.items = saveData.items + ItemSaveState(itemId = itemId, count = delta)
    }

    /**
     * 货币饱和增减（R5-I11 溢出收口）：用 Long 预算（toLong()+delta）避免 Int 溢出，
     * 结果钳到 [0, Int.MAX]——奖励溢出封顶而非翻负，扣减永不为负。
     * 须在 [writeMutex] 临界区内调用（非 suspend，与 [addItemDelta]/[addAffinityDelta] 同范式）。
     *
     * 边界：「扣减/兑换」入口（余额不足必须整体拒绝）仍走调用方的 Long 预检 + Rejected
     * （如 [EconomyService.applyCurrencyDelta] / TowerService.runTowerFloor 的 165-171），
     * 本辅助用于「奖励发放」类就地增减（正常路径 delta 有界，钳制不触发；异常溢出封顶兜底）。
     */
    fun addCurrencyDelta(softDelta: Int = 0, hardDelta: Int = 0) {
        if (softDelta != 0) {
            saveData.softCurrency = (saveData.softCurrency.toLong() + softDelta)
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
        if (hardDelta != 0) {
            saveData.hardCurrency = (saveData.hardCurrency.toLong() + hardDelta)
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
    }

    /**
     * 回滚道具到事务前快照：此前不存在 → 整条移除（防幽灵零数量条目）；
     * 此前已存在 → 恢复原数量。须在临界区内调用。
     */
    fun restoreItemCount(itemId: String, existedBefore: Boolean, originalCount: Int) {
        if (existedBefore) {
            saveData.items.firstOrNull { it?.itemId == itemId }?.count = originalCount
        } else {
            saveData.items = saveData.items.filterNot { it?.itemId == itemId }
        }
    }

    /**
     * 角色好感度（不存在视为 0）。须在临界区内调用。
     * 好感数据自 2026-09-02 起成为多服务共享字段（赠送/战斗胜利/剧情选项三处写），
     * 读写统一走本辅助 + [addAffinityDelta]，勿在聚合服务里散落就地修改。
     */
    fun affinityOf(characterId: String): Int =
        saveData.characterAffinityData?.get(characterId) ?: 0

    /**
     * 好感度增加（钳到 [AffinityFormulas.MAX_AFFINITY]；delta<=0 无操作）。
     * 满级时静默不加（数值安全由本辅助兜底，业务层可另行 Rejected）。须在临界区内调用。
     */
    fun addAffinityDelta(characterId: String, delta: Int) {
        if (delta <= 0) return
        val map = saveData.characterAffinityData ?: emptyMap()
        val current = map[characterId] ?: 0
        val next = minOf(current + delta, AffinityFormulas.MAX_AFFINITY)
        if (next != current) {
            saveData.characterAffinityData = map + (characterId to next)
        }
    }

    /**
     * 战绩追加（上限 50，超限丢最旧）。须在 [writeMutex] 临界区内调用；
     * 禁止就地写 50（契约见 [SaveData.MAX_BATTLE_RECORDS]）。
     */
    fun appendBattleRecordCapped(rec: BattleRecord) {
        saveData.battleRecords = saveData.battleRecords + rec
        if (saveData.battleRecords.size > SaveData.MAX_BATTLE_RECORDS)
            saveData.battleRecords =
                saveData.battleRecords.drop(saveData.battleRecords.size - SaveData.MAX_BATTLE_RECORDS)
    }

    /**
     * 由存档 + 内容定义构建战斗单位属性（[StatsCalculator] 单一事实来源）。
     * 角色未拥有或内容定义缺失返回 null（调用方跳过，绝不让脏档炸战斗路径）。
     *
     * **战斗系统核心依赖**（2026-09-06 S3 KDoc 更新）：
     * [ArenaService] 与 [TowerService] 的战斗结算经此构建 [UnitStats] 喂给 [BattleSimulator]。
     * 装备属性加成经 [calculateEquipmentStats] 合并到主属性——这是装备系统删除后
     * 仍保留装备字段（[SaveData.ownedEquipments] + [equipmentSets]）的根本原因：
     * 战斗单位属性计算依赖装备存档，与"装备业务规则"（已随 [EquipmentService] 删除）属不同关注点。
     */
    fun unitStatsFor(characterId: String): UnitStats? {
        val save = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId } ?: return null
        val def = charactersById[characterId] ?: return null
        val branchIds = def.talentTreeId.let { talentTreesById[it] }
            ?.nodes?.filter { node -> save.talentPoints.contains(node.nodeId) }
            ?.map { it.branchId }.orEmpty()

        // 构建节点 Effects 映射（新天赋效果系统）
        val tree = def.talentTreeId.let { talentTreesById[it] }
        val allocatedNodes = tree?.nodes
            ?.filter { node -> save.talentPoints.contains(node.nodeId) }
            ?.map { it.nodeId }.orEmpty()
        val nodeEffectsMap = tree?.nodes
            ?.filter { it.effects.isNotEmpty() }
            ?.associate { it.nodeId to it.effects }.orEmpty()

        // 计算基础属性
        val baseUnitStats = StatsCalculator.compute(
            baseStats = def.baseStats,
            level = save.level,
            stage = save.stage,
            stars = save.stars,
            branchIds = branchIds,
            characterId = characterId,
            progression = progression,
            talent = talent,
            nodeEffectsMap = nodeEffectsMap.ifEmpty { null },
            allocatedNodes = allocatedNodes,
        ).copy(element = def.element)
        
        // 计算装备属性加成
        val equipmentStats = calculateEquipmentStats(characterId)

        // 合并属性：四主属性 + 暴击；套装百分比作用在「基础+flat 装备」之上（R6-P1）
        return baseUnitStats.copy(
            atk = ((baseUnitStats.atk + equipmentStats.atk) * (1f + equipmentStats.atkPct)).toInt(),
            def = ((baseUnitStats.def + equipmentStats.def) * (1f + equipmentStats.defPct)).toInt(),
            hp = ((baseUnitStats.hp + equipmentStats.hp) * (1f + equipmentStats.hpPct)).toInt(),
            spd = baseUnitStats.spd + equipmentStats.spd,
            critRate = baseUnitStats.critRate + equipmentStats.critRate,
            critDmg = baseUnitStats.critDmg + equipmentStats.critDmg,
        )
    }
    
    /**
     * 计算角色装备的总属性加成（**战斗属性计算辅助**，非"装备业务规则"）。
     *
     * 2026-09-06 S3 KDoc 更新：原归类为"core 越界业务规则"不准确。本方法被
     * [unitStatsFor] 调用，而 [unitStatsFor] 是 [ArenaService]/[TowerService]
     * 战斗系统的核心依赖（构建 [UnitStats] 喂给 [BattleSimulator]）。
     * 装备加成本质是「战斗单位属性计算的内部实现」，与"装备业务规则"
     * （生成/强化/分解/穿脱，已随 [EquipmentService] 删除）属不同关注点。
     * 保留在 ServiceCore 是因为它是 unitStatsFor 的私有辅助，不对外暴露。
     *
     * 返回 [EquipmentStatBonus]（含暴击字段）；[unitStatsFor] 只取主属性合并到 [UnitStats]。
     */
    private fun calculateEquipmentStats(characterId: String): EquipmentStatBonus {
        val save = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
            ?: return EquipmentStatBonus()
        
        var atk = 0
        var def = 0
        var hp = 0
        var spd = 0
        var critRate = 0.0
        var critDmg = 0.0
        
        // 收集所有装备的属性
        for (equipId in save.getEquippedIds()) {
            val equipment = saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipId }
                ?: continue
            
            // 主属性
            when (equipment.mainStat.statType) {
                StatValue.STAT_ATTACK -> atk += equipment.mainStat.value
                StatValue.STAT_DEFENSE -> def += equipment.mainStat.value
                StatValue.STAT_HP -> hp += equipment.mainStat.value
                StatValue.STAT_SPEED -> spd += equipment.mainStat.value
                StatValue.STAT_CRIT_RATE -> critRate += equipment.mainStat.value / 100.0
                StatValue.STAT_CRIT_DMG -> critDmg += equipment.mainStat.value / 100.0
                else -> {}
            }
            
            // 副属性
            for (subStat in equipment.subStats.filterNotNull()) {
                when (subStat.statType) {
                    StatValue.STAT_ATTACK -> atk += subStat.value
                    StatValue.STAT_DEFENSE -> def += subStat.value
                    StatValue.STAT_HP -> hp += subStat.value
                    StatValue.STAT_SPEED -> spd += subStat.value
                    StatValue.STAT_CRIT_RATE -> critRate += subStat.value / 100.0
                    StatValue.STAT_CRIT_DMG -> critDmg += subStat.value / 100.0
                    else -> {}
                }
            }
        }
        
        // 计算套装效果
        val setBonuses = calculateSetBonuses(characterId)
        var atkPct = 0f
        var defPct = 0f
        var hpPct = 0f
        for (bonus in setBonuses) {
            // R6-P1：isPercentage 必须按百分比记账；此前一律当 flat 加，「攻击力+15%」变成 +15 点。
            when (bonus.statType) {
                StatValue.STAT_ATTACK ->
                    if (bonus.isPercentage) atkPct += bonus.value / 100f else atk += bonus.value
                StatValue.STAT_DEFENSE ->
                    if (bonus.isPercentage) defPct += bonus.value / 100f else def += bonus.value
                StatValue.STAT_HP ->
                    if (bonus.isPercentage) hpPct += bonus.value / 100f else hp += bonus.value
                StatValue.STAT_SPEED -> spd += bonus.value
                StatValue.STAT_CRIT_RATE -> critRate += bonus.value / 100.0
                StatValue.STAT_CRIT_DMG -> critDmg += bonus.value / 100.0
                else -> {}
            }
        }

        return EquipmentStatBonus(
            atk = atk,
            def = def,
            hp = hp,
            spd = spd,
            critRate = critRate,
            critDmg = critDmg,
            atkPct = atkPct,
            defPct = defPct,
            hpPct = hpPct,
        )
    }
    
    /**
     * 计算套装效果（**战斗属性计算辅助**，[calculateEquipmentStats] 的子计算）。
     * 同 S3 KDoc 更新：非"装备业务规则"，是战斗单位属性计算的私有辅助。
     */
    private fun calculateSetBonuses(characterId: String): List<StatBonus> {
        val save = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }
            ?: return emptyList()
        
        // 统计套装数量
        val setCounts = mutableMapOf<String, Int>()
        for (equipId in save.getEquippedIds()) {
            val equipment = saveData.ownedEquipments.firstOrNull { it?.equipmentId == equipId }
                ?: continue
            val template = equipmentTemplates.firstOrNull { it.equipmentId == equipment.templateId }
                ?: continue
            
            if (template.setId.isNotEmpty()) {
                setCounts[template.setId] = (setCounts[template.setId] ?: 0) + 1
            }
        }
        
        // 计算套装加成
        val bonuses = mutableListOf<StatBonus>()
        for ((setId, count) in setCounts) {
            val setData = equipmentSets.firstOrNull { it.setId == setId }
                ?: continue
            
            if (count >= 2) {
                bonuses.addAll(setData.twoPieceBonus.statBonuses)
            }
            if (count >= 4) {
                bonuses.addAll(setData.fourPieceBonus.statBonuses)
            }
        }
        
        return bonuses
    }

    /** 当日 UTC 日序号字符串（存档内跨日比对键）。 */
    fun dayKey(): String = today().toString()

    // ── 公式转发（一律委托 EconomyFormulas，禁止就地写数字）──

    fun maxLevelForStage(stage: Int): Int = EconomyFormulas.maxLevelForStage(stage)
    fun levelCost(level: Int): Int = EconomyFormulas.levelCost(level)
    fun ascendFragments(stage: Int): Int = EconomyFormulas.ascendFragments(stage)
    fun ascendSoft(stage: Int): Int = EconomyFormulas.ascendSoft(stage)
    fun starUpFragments(stars: Int): Int = EconomyFormulas.starUpFragments(stars)

    // ── 天赋前置索引（内容加载时随 talentTrees 一次性构建，读者只读）──

    private var prereqIndex: Map<String, Map<String, List<String>>> = emptyMap()

    /**
     * 构建 nodeId → 前置节点列表 的映射，喂给 TalentEngine.canAllocate。
     *
     * R4-05（2026-08-30 审查修复）：旧实现用懒初始化的可变 HashMap 做缓存，是**全服务层
     * 唯一在无锁路径上写的共享可变状态**——canAllocateTalent（主线程，非 suspend 读接口）
     * 与 allocateTalent（writeMutex 内的后台线程）会并发 getOrPut，LinkedHashMap 并发写
     * 可能触发 resize 竞态 → 结构损坏甚至死循环（CPU 100% / ANR）；loadContent 置 null 与
     * `?.also{}` 之间还存在 check-then-act 竞态，会让内容重载后仍返回旧树的前置映射。
     * 现改为内容加载时构建不可变索引，读者只做查表，索引缺失时按传入树即时兜底。
     */
    fun prereqMap(tree: TalentTreeData): Map<String, List<String>> =
        prereqIndex[tree.treeId] ?: tree.nodes.associate { it.nodeId to it.prerequisiteNodeIds }

    // ─────────────────────────── 内容加载 ───────────────────────────

    /**
     * 加载内容：优先解析 data.json；空/损坏/无有效角色 → 回退 [GameContent] 兜底。
     * 过滤口径见各条注释；两条加载路径都会经过 [GameContent.enrich] 补齐派生字段（#31）。
     */
    fun loadContent(rawJson: String?) {
        // R4-05：前置索引不再需要手工作废——它由 talentTrees 的 setter 同步重建，
        // 任何加载路径（json / 兜底）都不会漏，也不存在 check-then-act 竞态。
        if (rawJson != null) {
            try {
                val root = ContentJson.decodeFromString<RootData>(rawJson)
                val validChars = root.characters
                    .filterNotNull()
                    .filter { it.characterId.isNotEmpty() }
                if (validChars.isNotEmpty()) {
                    characters = validChars
                    pools = root.pools.filterNotNull().filter { p ->
                        p.poolId.isNotEmpty()
                            && p.entries.isNotEmpty()
                            && p.entries.all { it.characterId.isNotEmpty() }
                            // P2-7：权重档数必须与 Rarity 枚举严格一致（==4）——>4 时 GachaEngine
                            // 会把溢出档位并入 UR 扭曲概率，<4 时低稀有度档静默缺失。
                            && p.rarityWeights.size == 4
                            && p.rarityWeights.sum() > 0
                            // P1-3 配套：开保底的池必须保证保底档（SSR=3 起）有候选角色，
                            // 否则保底掷出后被降档吞掉。
                            && (p.hardPity <= 0 || p.entries.any { it.rarityIndex >= Rarity.SSR.value })
                            // C1（2026-08-28）：非零权重档位必须有候选角色，否则该档权重会被
                            // resolveRarityWithCandidates 就近上抬，造成概率塌缩（UP 池饕餮独占 70%）。
                            // 权重为 0 属刻意设计，放行。
                            && p.rarityWeights.withIndex().all { (i, w) ->
                                w <= 0 || p.entries.any { it.rarityIndex == i + 1 }
                            }
                    }
                    // 丢弃空树（Nodes 为 null 的树会让养成界面静默空白）
                    talentTrees = root.talentTrees.filterNotNull().filter { it.nodes.isNotEmpty() }
                    
                    // 加载装备数据（如果存在）
                    loadEquipmentContent(rawJson)

                    // 与兜底路径一致：补齐全量角色字段（武器名/背景故事/语音等）
                    GameContent.enrich(characters)

                    // 卡池为空会让抽卡直接崩，补兜底；天赋树缺失按角色补全。
                    if (pools.isEmpty()) pools = GameContent.buildPools(characters)
                    if (talentTrees.isEmpty()) talentTrees = GameContent.buildTalentTrees(characters)

                    onTrace(
                        "content.loaded.from.json chars=${characters.size} " +
                            "pools=${pools.size} entries=${pools.firstOrNull()?.entries?.size ?: 0} " +
                            "trees=${talentTrees.size} " +
                            "equipments=${equipmentTemplates.size} sets=${equipmentSets.size}",
                    )
                    return
                }
                onTrace("content.load.failed: no valid characters")
            } catch (e: Exception) {
                // 内容 JSON 损坏 → 留痕后走兜底（不静默：便于事后定位）
                onTrace("content.load.failed: ${e.message}")
            }
        }
        onTrace("content.load.fallback")
        loadFallback()
    }

    private fun loadFallback() {
        characters = GameContent.buildCharacters()
        pools = GameContent.buildPools(characters)
        talentTrees = GameContent.buildTalentTrees(characters)
        // 与 data.json 路径一致：补齐派生字段（C# #31 要求两条路径口径一致）
        GameContent.enrich(characters)
        
        // 加载装备兜底数据
        loadEquipmentFallback()
    }
    
    /**
     * 从data.json加载装备数据。
     */
    private fun loadEquipmentContent(rawJson: String) {
        try {
            // 尝试解析装备数据（如果data.json包含装备部分）
            // 注意：当前data.json可能不包含装备数据，所以这里先用兜底数据
            loadEquipmentFallback()
        } catch (e: Exception) {
            onTrace("equipment.load.failed: ${e.message}")
            loadEquipmentFallback()
        }
    }
    
    /**
     * 加载装备兜底数据。
     */
    private fun loadEquipmentFallback() {
        // 定义基础装备模板
        equipmentTemplates = listOf(
            // 武器
            EquipmentData(
                equipmentId = "eq_weapon_r_001",
                displayName = "铁剑",
                description = "普通的铁剑",
                rarity = 1,
                type = "weapon",
                setId = "",
                baseStats = listOf(StatData(StatValue.STAT_ATTACK, 10, 15, false, 100)),
                subStatPool = listOf(
                    StatData(StatValue.STAT_HP, 20, 50, false, 100),
                    StatData(StatValue.STAT_DEFENSE, 5, 15, false, 100),
                    StatData(StatValue.STAT_CRIT_RATE, 1, 3, false, 50),
                ),
                maxLevel = 15,
                expPerLevel = listOf(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800),
                goldPerLevel = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000),
            ),
            EquipmentData(
                equipmentId = "eq_weapon_sr_001",
                displayName = "精钢剑",
                description = "精钢打造的长剑",
                rarity = 2,
                type = "weapon",
                setId = "set_attack",
                baseStats = listOf(StatData(StatValue.STAT_ATTACK, 20, 30, false, 100)),
                subStatPool = listOf(
                    StatData(StatValue.STAT_HP, 30, 80, false, 100),
                    StatData(StatValue.STAT_DEFENSE, 10, 25, false, 100),
                    StatData(StatValue.STAT_CRIT_RATE, 2, 5, false, 75),
                    StatData(StatValue.STAT_CRIT_DMG, 4, 10, false, 50),
                ),
                maxLevel = 15,
                expPerLevel = listOf(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800),
                goldPerLevel = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000),
            ),
            // SSR 武器（爬塔 ≥30 层 / 活动商店高价档）
            EquipmentData(
                equipmentId = "eq_weapon_ssr_001",
                displayName = "玄铁重剑",
                description = "山海秘境所出，剑脊隐有雷纹",
                rarity = 3,
                type = "weapon",
                setId = "set_attack",
                baseStats = listOf(StatData(StatValue.STAT_ATTACK, 40, 60, false, 100)),
                subStatPool = listOf(
                    StatData(StatValue.STAT_HP, 50, 120, false, 100),
                    StatData(StatValue.STAT_DEFENSE, 15, 35, false, 100),
                    StatData(StatValue.STAT_CRIT_RATE, 3, 7, false, 80),
                    StatData(StatValue.STAT_CRIT_DMG, 6, 14, false, 70),
                ),
                maxLevel = 15,
                expPerLevel = listOf(120, 180, 240, 300, 360, 420, 480, 540, 600, 660, 720, 780, 840, 900, 960),
                goldPerLevel = listOf(1200, 1800, 2400, 3000, 3600, 4200, 4800, 5400, 6000, 6600, 7200, 7800, 8400, 9000, 9600),
            ),
            // UR 武器（爬塔 ≥50 层）
            EquipmentData(
                equipmentId = "eq_weapon_ur_001",
                displayName = "烛龙之锋",
                description = "以烛龙鳞锻成，出鞘如见晨昏",
                rarity = 4,
                type = "weapon",
                setId = "set_attack",
                baseStats = listOf(StatData(StatValue.STAT_ATTACK, 70, 100, false, 100)),
                subStatPool = listOf(
                    StatData(StatValue.STAT_HP, 80, 180, false, 100),
                    StatData(StatValue.STAT_DEFENSE, 25, 50, false, 100),
                    StatData(StatValue.STAT_CRIT_RATE, 5, 10, false, 90),
                    StatData(StatValue.STAT_CRIT_DMG, 10, 20, false, 80),
                    StatData(StatValue.STAT_SPEED, 3, 8, false, 60),
                ),
                maxLevel = 15,
                expPerLevel = listOf(150, 220, 290, 360, 430, 500, 570, 640, 710, 780, 850, 920, 990, 1060, 1130),
                goldPerLevel = listOf(1500, 2200, 2900, 3600, 4300, 5000, 5700, 6400, 7100, 7800, 8500, 9200, 9900, 10600, 11300),
            ),
            // 头盔
            EquipmentData(
                equipmentId = "eq_head_r_001",
                displayName = "皮盔",
                description = "普通的皮质头盔",
                rarity = 1,
                type = "head",
                setId = "",
                baseStats = listOf(StatData(StatValue.STAT_HP, 50, 100, false, 100)),
                subStatPool = listOf(
                    StatData(StatValue.STAT_ATTACK, 5, 15, false, 100),
                    StatData(StatValue.STAT_DEFENSE, 5, 15, false, 100),
                    StatData(StatValue.STAT_CRIT_RATE, 1, 3, false, 50),
                ),
                maxLevel = 15,
                expPerLevel = listOf(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800),
                goldPerLevel = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000),
            ),
            // 铠甲
            EquipmentData(
                equipmentId = "eq_body_r_001",
                displayName = "皮甲",
                description = "普通的皮质铠甲",
                rarity = 1,
                type = "body",
                setId = "",
                baseStats = listOf(StatData(StatValue.STAT_DEFENSE, 10, 20, false, 100)),
                subStatPool = listOf(
                    StatData(StatValue.STAT_ATTACK, 5, 15, false, 100),
                    StatData(StatValue.STAT_HP, 20, 50, false, 100),
                    StatData(StatValue.STAT_CRIT_RATE, 1, 3, false, 50),
                ),
                maxLevel = 15,
                expPerLevel = listOf(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800),
                goldPerLevel = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000),
            ),
            // 饰品
            EquipmentData(
                equipmentId = "eq_accessory_r_001",
                displayName = "生命戒指",
                description = "增加生命值的戒指",
                rarity = 1,
                type = "accessory",
                setId = "",
                baseStats = listOf(
                    StatData(StatValue.STAT_HP, 30, 60, false, 100),
                    StatData(StatValue.STAT_ATTACK, 5, 10, false, 50),
                ),
                subStatPool = listOf(
                    StatData(StatValue.STAT_DEFENSE, 5, 15, false, 100),
                    StatData(StatValue.STAT_SPEED, 2, 5, false, 75),
                    StatData(StatValue.STAT_CRIT_RATE, 1, 3, false, 50),
                ),
                maxLevel = 15,
                expPerLevel = listOf(100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700, 750, 800),
                goldPerLevel = listOf(1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000),
            ),
        )
        
        // 定义套装效果
        equipmentSets = listOf(
            EquipmentSetData(
                setId = "set_attack",
                displayName = "攻击套",
                description = "2件套：攻击力+15%；4件套：暴击率+10%",
                twoPieceBonus = SetBonus(
                    description = "攻击力+15%",
                    statBonuses = listOf(StatBonus(StatValue.STAT_ATTACK, 15, true)),
                ),
                fourPieceBonus = SetBonus(
                    description = "暴击率+10%",
                    statBonuses = listOf(StatBonus(StatValue.STAT_CRIT_RATE, 10, true)),
                ),
            ),
            EquipmentSetData(
                setId = "set_defense",
                displayName = "防御套",
                description = "2件套：防御力+15%；4件套：生命值+20%",
                twoPieceBonus = SetBonus(
                    description = "防御力+15%",
                    statBonuses = listOf(StatBonus(StatValue.STAT_DEFENSE, 15, true)),
                ),
                fourPieceBonus = SetBonus(
                    description = "生命值+20%",
                    statBonuses = listOf(StatBonus(StatValue.STAT_HP, 20, true)),
                ),
            ),
        )
    }

    companion object {
        const val StarFragmentItemId = "item_star_fragment"

        /** 战票道具 id（无尽之塔门票；对齐 item_ 前缀惯例，走通用道具系统）。 */
        const val BattleTicketItemId = "item_battle_ticket"
    }
}
