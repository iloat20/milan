package com.milan.game.services

import com.milan.game.data.BattleRecord
import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.Rarity
import com.milan.game.data.SaveData
import com.milan.game.data.SaveManager
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
internal class ServiceCore(
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

    // ── 状态快照（UI 订阅 StateFlow，替代「EventBus 轻标记 + 手动重读」）──
    private val _snapshot = MutableStateFlow(GameSnapshot(0, 0, 0, 0, 0))
    val snapshot: StateFlow<GameSnapshot> = _snapshot.asStateFlow()

    /** 每次成功写操作后统一刷新快照；revision 是重组触发器。 */
    fun refreshSnapshot() {
        _snapshot.value = GameSnapshot(
            revision = _snapshot.value.revision + 1,
            softCurrency = saveData.softCurrency,
            hardCurrency = saveData.hardCurrency,
            starFragments = getStarFragments(),
            ownedCount = saveData.ownedCharacters.size,
            soundEnabled = saveData.soundEnabled,
            vibrationEnabled = saveData.vibrationEnabled,
            pushEnabled = saveData.pushEnabled,
            // 角色级数据随每次写操作刷新——保底计数 + 角色存档拷贝
            //（显式构造拷贝：CharacterSaveState 为普通 class 无 copy()；拷贝防快照与存档
            //  共享可变引用，避免 resetSave 后快照持有陈旧对象）。
            pityByPool = pools.associate { it.poolId to saveData.getGachaCounter(it.poolId) },
            ownedSaves = saveData.ownedCharacters.filterNotNull().associate { it.characterId to it.toSnapshotCopy() },
            formation = saveData.getFormationIds(),
            towerBestFloor = saveData.towerBestFloor,
            battleTickets = itemCount(BattleTicketItemId),
            // UP 定轨状态：GachaScreen 展示「下次必中」标记
            featuredLostByPool = pools.associate { it.poolId to saveData.isFeaturedGuaranteed(it.poolId) },
        )
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
     */
    suspend inline fun transactionLocked(
        tag: String,
        mutate: () -> Unit,
        rollback: () -> Unit,
        onCommit: () -> Unit,
    ): WriteOutcome {
        mutate()
        val saved = withContext(Dispatchers.IO) { saveManager.save() }
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
    ): WriteOutcome = writeMutex.withLock { transactionLocked(tag, mutate, rollback, onCommit) }

    // ─────────────────────────── 事件发布 ───────────────────────────

    /** 经济变动统一出口：状态快照先行，再广播事件。 */
    fun publishCurrencyChanged() {
        refreshSnapshot()
        EventBus.publish(CurrencyChanged)
        EventBus.dispatch()
    }

    /** 养成变动统一出口（事件无载荷，订阅方自行重读当前角色）。 */
    fun publishProgressionChanged() {
        refreshSnapshot()
        EventBus.publish(ProgressionChanged)
        EventBus.dispatch()
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
     */
    fun unitStatsFor(characterId: String): UnitStats? {
        val save = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId } ?: return null
        val def = charactersById[characterId] ?: return null
        val branchIds = def.talentTreeId.let { talentTreesById[it] }
            ?.nodes?.filter { node -> save.talentPoints.contains(node.nodeId) }
            ?.map { it.branchId }.orEmpty()
        return StatsCalculator.compute(
            baseStats = def.baseStats,
            level = save.level,
            stage = save.stage,
            stars = save.stars,
            branchIds = branchIds,
            characterId = characterId,
            progression = progression,
            talent = talent,
        ).copy(element = def.element)
    }

    /** 当日 UTC 日序号字符串（存档内跨日比对键）。 */
    fun dayKey(): String = today().toString()

    // ── 公式转发（一律委托 EconomyFormulas，禁止就地写数字）──

    fun maxLevelForStage(stage: Int): Int = EconomyFormulas.maxLevelForStage(stage)
    fun levelCost(level: Int): Int = EconomyFormulas.levelCost(level)
    fun ascendFragments(stage: Int): Int = EconomyFormulas.ascendFragments(stage)
    fun ascendSoft(stage: Int): Int = EconomyFormulas.ascendSoft(stage)
    fun starUpFragments(stars: Int): Int = EconomyFormulas.starUpFragments(stars)

    // ── 天赋前置映射缓存（内容重载时由 [loadContent] 作废）──

    private var prereqCache: MutableMap<String, Map<String, List<String>>>? = null

    /** 构建 nodeId → 前置节点列表 的映射，喂给 TalentEngine.canAllocate。 */
    fun prereqMap(tree: TalentTreeData): Map<String, List<String>> {
        val cache = prereqCache ?: mutableMapOf<String, Map<String, List<String>>>().also { prereqCache = it }
        return cache.getOrPut(tree.treeId) { tree.nodes.associate { it.nodeId to it.prerequisiteNodeIds } }
    }

    // ─────────────────────────── 内容加载 ───────────────────────────

    /**
     * 加载内容：优先解析 data.json；空/损坏/无有效角色 → 回退 [GameContent] 兜底。
     * 过滤口径见各条注释；两条加载路径都会经过 [GameContent.enrich] 补齐派生字段（#31）。
     */
    fun loadContent(rawJson: String?) {
        prereqCache = null // 内容重载 → 前置映射可能变化，缓存作废
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

                    // 与兜底路径一致：补齐全量角色字段（武器名/背景故事/语音等）
                    GameContent.enrich(characters)

                    // 卡池为空会让抽卡直接崩，补兜底；天赋树缺失按角色补全。
                    if (pools.isEmpty()) pools = GameContent.buildPools(characters)
                    if (talentTrees.isEmpty()) talentTrees = GameContent.buildTalentTrees(characters)

                    onTrace(
                        "content.loaded.from.json chars=${characters.size} " +
                            "pools=${pools.size} entries=${pools.firstOrNull()?.entries?.size ?: 0} " +
                            "trees=${talentTrees.size}",
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
    }

    companion object {
        const val StarFragmentItemId = "item_star_fragment"

        /** 战票道具 id（无尽之塔门票；对齐 item_ 前缀惯例，走通用道具系统）。 */
        const val BattleTicketItemId = "item_battle_ticket"
    }
}
