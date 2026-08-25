package com.milan.game.services

import com.milan.game.data.BattleRecord
import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.Rarity
import com.milan.game.data.SaveData
import com.milan.game.data.SaveManager
import com.milan.game.data.SaveProvider
import com.milan.game.domain.battle.BattleSimulator
import com.milan.game.domain.battle.TeamResonance
import com.milan.game.domain.battle.UnitStats
import com.milan.game.domain.gacha.GachaEngine
import com.milan.game.domain.gacha.PityCounter
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
 * 爬塔挑战结果（2026-08 无尽之塔；顶层类型，对齐 [WriteOutcome] / [PullOutcome] 风格）。
 */
sealed interface TowerOutcome {
    /**
     * 战斗已完成。[victory] 时 [rewardSoft] 为本次发放的星尘奖励；
     * [bestFloorAfter] 为结算后的历史最高层（刷新纪录或保持不变）。
     */
    data class Completed(
        val victory: Boolean,
        val turns: Int,
        val rewardSoft: Int,
        val bestFloorAfter: Int,
    ) : TowerOutcome

    /** 拒绝：floor 非法 / 编队为空 / 队伍构建失败。 */
    data object Rejected : TowerOutcome

    /** 落盘失败（奖励与纪录已整体回滚）。 */
    data object SaveFailed : TowerOutcome
}

/** 每日特惠类型（2026-08 每日商店）。 */
enum class DailyOfferKind { FREE_SUPPLY, DISCOUNT_PACK, TICKET_BUNDLE }

/**
 * 单个每日特惠槽位（展示数据；购买效果由 [DailyOffer.kind] 决定，价格已含折扣计算，
 * 数值全部出自 EconomyFormulas——同一天跨端/重开结果一致）。
 */
data class DailyOffer(
    /** 槽位下标（每槽每日限购一次）。 */
    val index: Int,
    val kind: DailyOfferKind,
    /** 折扣包档位（仅 DISCOUNT_PACK 有意义，其余为 0）。 */
    val pack: Int,
    val title: String,
    val detail: String,
    /** 星尘售价（免费补给为 0）。 */
    val costSoft: Int,
)

/** 成就条目的状态包（定义 + 实时解锁态 + 存档领取态）。 */
data class AchievementStatus(
    val def: com.milan.game.services.AchievementDef,
    val unlocked: Boolean,
    val claimed: Boolean,
)

/**
 * 游戏服务编排层（C# GameService.cs 翻译）：抽卡事务、货币/养成写操作、战绩、内容加载。
 *
 * 职责边界：
 * - 领域计算（升级/突破/升星公式、保底、天赋判定）一律委托给
 *   [EconomyFormulas] / [PityCounter] / [TalentEngine]（纯领域、单一事实来源、可单测），
 *   禁止在本类里重写数字——改数值请改 EconomyFormulas。
 * - 内容数据：data.json（由接入层读取后传入 [contentJson]）优先，失败/缺失回退
 *   [GameContent] 兜底；两条加载路径都会经过 [GameContent.enrich] 补齐派生字段（#31）。
 * - 所有写操作遵循 Pull 的事务范式：先预算/校验可支付 → 变更内存 → 落盘；
 *   落盘失败回滚本次内存改动，绝不让「内存与存档不一致」。回滚路径不广播事件。
 * - 事件通过 [EventBus] 发布并立即 [EventBus.dispatch]（与 C# Publish+Dispatch 同步派发一致）。
 *
 * @param contentJson data.json 原文（接入层从 Assets 读取）；null/损坏 → 兜底内容
 * @param rng 随机源，可注入种子便于确定性测试
 */
class GameService(
    saveProvider: SaveProvider,
    contentJson: String? = null,
    private val onTrace: (String) -> Unit = {},
    private val rng: Random = Random.Default,
    /** UTC 日序号提供器（每日商店按天重置；注入便于测试固定「今天」，默认系统时钟）。 */
    private val today: () -> Long = { System.currentTimeMillis() / 86_400_000L },
) {
    private val saveManager = SaveManager(saveProvider, onTrace)
    private val gacha = GachaEngine(rng)
    private val talent = TalentEngine()
    private val progression = ProgressionEngine()

    /** prereqMap 结果缓存（treeId → map）；loadContent 时失效重建。 */
    private var prereqCache: MutableMap<String, Map<String, List<String>>>? = null

    // ── 串行写锁（2026-08 主线程 IO 异步化）──
    // 所有写操作改为 suspend：内存变更与落盘在同一临界区内串行执行（Mutex），
    // 落盘经 withContext(Dispatchers.IO) 移出主线程；回滚仍在同一临界区内同步判定，
    // 保持事务范式（预算→变更→落盘→失败回滚→仅成功广播）不变。
    // 注意 Mutex 不可重入：持锁调用方必须走 transactionLocked（不重复加锁）。
    private val writeMutex = Mutex()

    // ── 状态快照（2026-08 现代化：UI 订阅 StateFlow，替代「EventBus 轻标记 + 手动重读」）──
    // 每次成功写操作后由 refreshSnapshot() 统一刷新；revision 是重组触发器，
    // 屏幕 collectAsStateWithLifecycle() 后直接读快照字段，或读 revision 触发重读存档。
    private val _snapshot = MutableStateFlow(GameSnapshot(0, 0, 0, 0, 0))
    val snapshot: StateFlow<GameSnapshot> = _snapshot.asStateFlow()

    private fun refreshSnapshot() {
        _snapshot.value = GameSnapshot(
            revision = _snapshot.value.revision + 1,
            softCurrency = saveData.softCurrency,
            hardCurrency = saveData.hardCurrency,
            starFragments = getStarFragments(),
            ownedCount = saveData.ownedCharacters.size,
            soundEnabled = saveData.soundEnabled,
            vibrationEnabled = saveData.vibrationEnabled,
            pushEnabled = saveData.pushEnabled,
            // 路径 B：角色级数据随每次写操作刷新——保底计数 + 角色存档拷贝
            //（显式构造拷贝：CharacterSaveState 为普通 class 无 copy()；拷贝防快照与存档
            //  共享可变引用，避免 resetSave 后快照持有陈旧对象）。
            pityByPool = pools.associate { it.poolId to saveData.getGachaCounter(it.poolId) },
            ownedSaves = saveData.ownedCharacters.filterNotNull().associate { it.characterId to it.toSnapshotCopy() },
            formation = saveData.getFormationIds(),
            towerBestFloor = saveData.towerBestFloor,
            battleTickets = itemCount(BattleTicketItemId),
        )
    }

    /** 快照拷贝：显式构造 CharacterSaveState 副本（普通 class 无 copy()；防快照持有可变存档引用，resetSave 后陈旧）。 */
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
     * 重置存档时整体替换为新档引用（见 [resetSave]）。
     */
    var saveData: SaveData = saveManager.load()
        private set

    /** 角色内容列表。赋值时同步重建 [charactersById] 索引（任何加载路径都不会漏）。 */
    var characters: List<CharacterDataEntry> = emptyList()
        private set(value) {
            field = value
            charactersById = value.associateBy { it.characterId }
        }
    var pools: List<GachaPoolDataEntry> = emptyList()
        private set
    /** 天赋树内容列表。赋值时同步重建 [talentTreesById] 索引。 */
    var talentTrees: List<TalentTreeData> = emptyList()
        private set(value) {
            field = value
            talentTreesById = value.associateBy { it.treeId }
        }

    /** characterId → 角色定义 索引（渲染路径查角色走这里，替代 O(n) firstOrNull）。 */
    private var charactersById: Map<String, CharacterDataEntry> = emptyMap()
    /** treeId → 天赋树 索引（渲染路径查天赋树走这里，替代 O(n) firstOrNull）。 */
    private var talentTreesById: Map<String, TalentTreeData> = emptyMap()

    /** 按 ID 查角色定义（O(1) 索引；未找到返回 null）。 */
    fun character(id: String): CharacterDataEntry? = charactersById[id]

    /** 按 ID 查天赋树（O(1) 索引；未找到返回 null）。 */
    fun talentTree(id: String): TalentTreeData? = talentTreesById[id]

    init {
        loadContent(contentJson)
        // 初始快照：载入存档后立即发布一次真实值，避免 UI 在首次写操作前看到全 0
        refreshSnapshot()
    }

    // ------------------------------------------------------------------ content loading

    /**
     * 加载内容：优先解析 data.json；空/损坏/无有效角色 → 回退 [GameContent] 兜底。
     * 对齐 C# Initialize 的过滤口径：
     * - 角色：非 null 且 CharacterId 非空；全部无效 → 回退；
     * - 卡池：PoolId 非空、Entries 非空且条目有效、RarityWeights ≥4 且总和 >0；
     * - 天赋树：Nodes 非空（Kotlin coerceInputValues 把 JSON null 转空列表，故按「非空树」过滤，
     *   对齐 C# 丢弃 Nodes==null 的树、避免养成界面空白）；
     * - 卡池/天赋树过滤后为空 → 用 [GameContent] 补全（防抽卡直接崩 / 养成界面空掉）。
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
                            // 会把溢出档位并入 UR 扭曲概率，<4 时低稀有度档静默缺失；此处置为无效池走兜底。
                            && p.rarityWeights.size == 4
                            && p.rarityWeights.sum() > 0
                            // P1-3 配套：开保底的池必须保证保底档（SSR=3 起）有候选角色，
                            // 否则保底掷出后被降档吞掉（resolveRarityWithCandidates 兜底语义保留给内容错误）。
                            && (p.hardPity <= 0 || p.entries.any { it.rarityIndex >= Rarity.SSR.value })
                    }
                    // 丢弃空树（对齐 C# 注释意图：Nodes 为 null 的树会让养成界面静默空白）
                    talentTrees = root.talentTrees.filterNotNull().filter { it.nodes.isNotEmpty() }

                    // 与兜底路径一致：补齐全量角色字段（武器名/背景故事/语音等），
                    // 保证两条加载路径数据一致（C# #31）。
                    GameContent.enrich(characters)

                    // 卡池为空会让抽卡直接崩，补一个兜底卡池；天赋树缺失按角色补全。
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

    // ------------------------------------------------------------------ pull

    /** 重复角色按稀有度补偿的星魂碎片数量。公式在 [EconomyFormulas]（纯领域、可单测）。 */
    fun fragmentsForRarity(rarity: Int): Int = EconomyFormulas.fragmentsForRarity(rarity)

    /**
     * 抽卡（单抽 / 十连），整体事务：
     * 先算出全部产出（不扣款、不改存档）——任何配置错误只导致「少抽」，
     * 绝不「扣了钱没东西」（#8）；确认有产出后再扣款 + 落盘，落盘失败回滚本次
     * 扣款与发货（含碎片幻影处理），让玩家可重试（#5）。成功才广播经济变动。
     *
     * 2026-08：suspend + 串行写锁——落盘在 IO 线程执行，主线程不阻塞；
     * 回滚与落盘判定在同一临界区内同步完成，事务语义不变。
     */
    suspend fun pull(poolId: String, tenPull: Boolean): PullOutcome = writeMutex.withLock {
        val results = mutableListOf<PullResult>()
        val pool = pools.firstOrNull { it.poolId == poolId } ?: return@withLock PullOutcome.Rejected

        // 空卡池一张牌也抽不出来。必须在扣款【之前】拦截，否则玩家的星尘会被静默吞掉。
        if (pool.entries.isEmpty()) return@withLock PullOutcome.Rejected

        val count = if (tenPull) 10 else 1
        val cost = if (tenPull) pool.tenCost else pool.singleCost
        if (saveData.softCurrency < cost) return@withLock PullOutcome.Rejected

        // 先算产出（不扣款）：展示稀有度与补偿碎片统一用抽中角色的真实稀有度（#11）。
        val pity = PityCounter(pool.hardPity).apply { counter = saveData.getGachaCounter(poolId) }
        val plan = mutableListOf<PlanItem>()
        // 本批已确认的新角色集合：同一次十连内同一未拥有角色重复出现时，
        // 第二次起按「重复角色」补偿碎片、发货只追加一条拥有条目——
        // 否则十连内撞重复新角色会白丢碎片，且 ownedCharacters 写入重复条目
        // （下次载入被 sanitize 去重，碎片永久丢失；历史 C# 行为继承的缺陷）。
        val batchNew = mutableSetOf<String>()
        for (i in 0 until count) {
            // 掷出的稀有度段在本池可能没有候选角色（如 UP 池没有 R 角色）：
            // 就近向上升档（保证玩家不亏），全部向上无候选再向下回退。
            // minRarityForPity 直接传 Rarity 类型（SSR），类型化后不再有 value/下标歧义，见 PityCounter KDoc。
            val rolledRarity = pity.rollWithPity(rng, pool.rarityWeights.toIntArray(), Rarity.SSR).value
            val effectiveRarity = resolveRarityWithCandidates(pool, rolledRarity)
            // P1-3：保底重置以「实际交付档位」判定——掷出保底档但该档无候选被降档时
            // 不重置计数，避免 90 抽保底被低稀有度产出吞掉（onNaturalPityOrAbove 见 PityCounter KDoc）。
            pity.onNaturalPityOrAbove(Rarity.fromValue(effectiveRarity) ?: Rarity.R, Rarity.SSR)
            val entries = pool.entries.filter { it.rarityIndex == effectiveRarity }
            val id = pickFromEntries(entries)
            if (id.isNullOrEmpty()) continue // 该稀有度无候选，跳过（不影响其它抽）

            val def = character(id)
            // 展示稀有度与补偿碎片必须口径一致：统一用抽中角色的真实稀有度（#11）。
            val rarity = def?.baseRarity ?: effectiveRarity
            val isNew = saveData.ownedCharacters.none { it?.characterId == id } && id !in batchNew
            if (isNew) batchNew += id
            val fragments = if (isNew) 0 else fragmentsForRarity(rarity)
            plan += PlanItem(id, def, isNew, fragments, rarity)
        }
        if (plan.isEmpty()) return@withLock PullOutcome.Rejected // 没抽到任何东西，绝不扣款

        // 确认有产出后再扣款 + 落盘；落盘失败回滚本次扣款与发货（#5）。
        val originalCurrency = saveData.softCurrency
        val originalCounter = saveData.getGachaCounter(poolId)
        // 碎片条目在本次抽卡前是否已存在：决定回滚时是「减回数量」还是「整条移除」，
        // 否则首次抽到重复角色且落盘失败，会在存档里留下一条数量为 0 的幽灵道具。
        val fragItemExisted = saveData.items.any { it?.itemId == StarFragmentItemId }
        var fragDelta = 0
        // 本函数已持 writeMutex：用 transactionLocked（不重复加锁，Mutex 不可重入）
        val outcome = transactionLocked(
            tag = "pull",
            mutate = {
                for (p in plan) {
                    results += PullResult(
                        success = true,
                        characterId = p.id,
                        characterName = p.def?.displayName ?: p.id,
                        rarity = p.rarity,
                        isNew = p.isNew,
                        fragmentsAwarded = p.fragments,
                    )
                    if (p.isNew) {
                        saveData.ownedCharacters = saveData.ownedCharacters + CharacterSaveState(characterId = p.id)
                    } else {
                        fragDelta += p.fragments
                    }
                }
                if (fragDelta > 0) {
                    val item = saveData.items.firstOrNull { it?.itemId == StarFragmentItemId }
                    if (item != null) item.count += fragDelta
                    else saveData.items = saveData.items + ItemSaveState(itemId = StarFragmentItemId, count = fragDelta)
                }
                saveData.softCurrency -= cost
                saveData.setGachaCounter(poolId, pity.counter)
            },
            rollback = {
                saveData.softCurrency = originalCurrency
                saveData.setGachaCounter(poolId, originalCounter)
                val newIds = plan.filter { it.isNew }.map { it.id }.toSet()
                // Set.contains 不接受可空参数：先判 null 再走 contains（Kotlin 类型系统要求）。
                saveData.ownedCharacters = saveData.ownedCharacters.filterNot { s -> s != null && s.characterId in newIds }
                // 碎片补偿也必须回滚：只退钱不退货会让玩家「存档没变但碎片凭空多出来」（可无限刷）。
                if (fragDelta > 0) {
                    if (fragItemExisted) {
                        val frag = saveData.items.firstOrNull { it?.itemId == StarFragmentItemId }
                        if (frag != null) frag.count = maxOf(0, frag.count - fragDelta)
                    } else {
                        saveData.items = saveData.items.filterNot { it?.itemId == StarFragmentItemId }
                    }
                }
            },
            onCommit = { publishCurrencyChanged() },
        )
        // 扣费 + 落盘都成功才返回产出；回滚分支不会跑到这里（rollback 不广播）。
        return@withLock when (outcome) {
            WriteOutcome.Success -> PullOutcome.Success(results)
            WriteOutcome.Rejected -> PullOutcome.Rejected // 防御：前面已拦截全部拒绝路径
            WriteOutcome.SaveFailed -> PullOutcome.SaveFailed
        }
    }

    /** 距 rolled 最近且有候选角色的稀有度档位（优先向上）。 */
    private fun resolveRarityWithCandidates(pool: GachaPoolDataEntry, rolled: Int): Int {
        if (pool.entries.any { it.rarityIndex == rolled }) return rolled
        for (r in (rolled + 1)..4)
            if (pool.entries.any { it.rarityIndex == r }) return r
        for (r in (rolled - 1) downTo 1)
            if (pool.entries.any { it.rarityIndex == r }) return r
        return rolled
    }

    private fun pickFromEntries(entries: List<GachaPoolEntry>): String? {
        if (entries.isEmpty()) return null
        return gacha.pickWeighted(entries.map { it.characterId }, entries.map { it.weight })
    }

    // ── 经济变动统一出口（EventBus 接线点）──
    // 任何改动星尘/钻石的地方都走这里，改动即广播，订阅方只刷受影响控件。
    private fun publishCurrencyChanged() {
        refreshSnapshot() // 状态快照先行：UI 的 collectAsStateWithLifecycle 立即收到新值
        EventBus.publish(CurrencyChanged)
        EventBus.dispatch()
    }

    // ── 货币增减 ──
    // 全部遵循与 Pull 一致的事务范式：先校验可负担 → 改内存 → 落盘 → 失败回滚 → 仅成功才广播。
    // 返回 WriteOutcome：Rejected=预算不足/非法请求（零变更）、SaveFailed=落盘失败（已回滚）。

    /**
     * 事务模板核心（2026-08 收敛：原 11 处手写「变更→落盘→回滚→仅成功广播」骨架）。
     * **调用方必须已持有 [writeMutex]**（Mutex 不可重入；单操作入口走 [transaction]，
     * 需要整段持锁的操作如 pull 走本函数）。落盘经 IO 线程执行，主线程不阻塞。
     * 契约：mutate 改内存 → 落盘成功 → onCommit（广播/推进状态快照）；
     * 落盘失败 → rollback 恢复内存（回滚路径不广播）→ 留痕 → 返回 [WriteOutcome.SaveFailed]。
     * @param tag 留痕前缀（如 "pull"/"currency"），产出 "pull.save.failed: rolled back" 这类可检索轨迹
     */
    private suspend inline fun transactionLocked(
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
    private suspend inline fun transaction(
        tag: String,
        mutate: () -> Unit,
        rollback: () -> Unit,
        onCommit: () -> Unit,
    ): WriteOutcome = writeMutex.withLock { transactionLocked(tag, mutate, rollback, onCommit) }

    /**
     * 扣除星尘。amount<=0（含误传负数）或余额不足或落盘失败时不做任何变更并返回非 Success。
     * 负数金额防御（P2-6）：`spendSoft(-50)` 若直传会变相加钱，一律拒绝。
     */
    suspend fun spendSoft(amount: Int): WriteOutcome =
        if (amount <= 0) WriteOutcome.Rejected else applyCurrencyDelta(-amount, 0)

    /** 增加星尘。amount<=0 或落盘失败时不做任何变更并返回非 Success。 */
    suspend fun addSoft(amount: Int): WriteOutcome =
        if (amount <= 0) WriteOutcome.Rejected else applyCurrencyDelta(amount, 0)

    /** 扣除钻石。amount<=0 或余额不足或落盘失败时不做任何变更并返回非 Success。 */
    suspend fun spendHard(amount: Int): WriteOutcome =
        if (amount <= 0) WriteOutcome.Rejected else applyCurrencyDelta(0, -amount)

    /** 增加钻石。amount<=0 或落盘失败时不做任何变更并返回非 Success。 */
    suspend fun addHard(amount: Int): WriteOutcome =
        if (amount <= 0) WriteOutcome.Rejected else applyCurrencyDelta(0, amount)

    private suspend fun applyCurrencyDelta(softDelta: Int, hardDelta: Int): WriteOutcome =
        writeMutex.withLock {
            if (softDelta == 0 && hardDelta == 0) return@withLock WriteOutcome.Rejected
            // 用 Long 预算校验：既拦截负余额，也拦截 Int 溢出（P2-5）——
            // 溢出为负会被当「不足」静默拒绝，溢出为正则会通过校验后破坏性改写余额并落盘。
            if (softDelta != 0) {
                val after = saveData.softCurrency.toLong() + softDelta
                if (after < 0 || after > Int.MAX_VALUE) return@withLock WriteOutcome.Rejected
            }
            if (hardDelta != 0) {
                val after = saveData.hardCurrency.toLong() + hardDelta
                if (after < 0 || after > Int.MAX_VALUE) return@withLock WriteOutcome.Rejected
            }

            val origSoft = saveData.softCurrency
            val origHard = saveData.hardCurrency
            transactionLocked(
                tag = "currency",
                mutate = {
                    saveData.softCurrency += softDelta
                    saveData.hardCurrency += hardDelta
                },
                rollback = {
                    saveData.softCurrency = origSoft
                    saveData.hardCurrency = origHard
                },
                onCommit = { publishCurrencyChanged() },
            )
        }

    /** 立即落盘（设置项改 SaveData 字段后调用）。saveData 与 SaveManager.current 同一引用。 */
    suspend fun save(): Boolean = withContext(Dispatchers.IO) { saveManager.save() }

    // ─────────────────────────────────────────────────────────── 设置与数据管理

    /**
     * 设置项持久化通用事务：先写入新值 → 落盘 → 失败回滚旧值。
     * （对齐存档事务范式：落盘失败回滚本次内存改动并返回 SaveFailed，回滚不广播事件。）
     */
    private suspend fun <T> persistSetting(read: () -> T, write: (T) -> Unit, newValue: T): WriteOutcome {
        val old = read()
        return transaction(
            tag = "setting",
            mutate = { write(newValue) },
            rollback = { write(old) },
            // 设置项无事件广播，但需刷新状态快照：SettingsScreen 从 GameSnapshot 派生开关，
            // 成功落盘后由 refreshSnapshot 推进，UI 立即反映新值（I5）。
            onCommit = { refreshSnapshot() },
        )
    }

    /** 音效开关持久化（UI 层负责同步 MilanAudio 音量）。 */
    suspend fun setSoundEnabled(enabled: Boolean): WriteOutcome = persistSetting(
        read = { saveData.soundEnabled },
        write = { saveData.soundEnabled = it },
        newValue = enabled,
    )

    /** 振动开关持久化（GachaScreen 演出震动读取该字段）。 */
    suspend fun setVibrationEnabled(enabled: Boolean): WriteOutcome = persistSetting(
        read = { saveData.vibrationEnabled },
        write = { saveData.vibrationEnabled = it },
        newValue = enabled,
    )

    /** 推送开关持久化（推送系统尚未接入，先存档留位）。 */
    suspend fun setPushEnabled(enabled: Boolean): WriteOutcome = persistSetting(
        read = { saveData.pushEnabled },
        write = { saveData.pushEnabled = it },
        newValue = enabled,
    )

    /**
     * 重置存档为新档：删除存档文件并重载默认档，整体替换 [saveData] 引用，
     * 成功后广播货币/养成变更（各页面据此刷新）。
     * 删除失败返回 false 且不动内存（对齐事务范式：失败不产生任何变更）。
     */
    suspend fun resetSave(): Boolean = writeMutex.withLock {
        val fresh = withContext(Dispatchers.IO) { saveManager.reset() } ?: return@withLock false
        saveData = fresh
        publishCurrencyChanged()
        publishProgressionChanged()
        true
    }

    // ─────────────────────────────────────────────────────────── 战绩
    /** 读取战绩（最近在前）。空列表返回空实例，调用方无需判 null。 */
    fun getBattleRecords(): List<BattleRecord> = saveData.battleRecords.filterNotNull()

    /**
     * 追加一条战绩并落盘。落盘失败回滚本次追加（不广播事件，战绩非经济）。
     * 列表上限 50 条，超出丢弃最旧记录。
     */
    suspend fun recordBattle(rec: BattleRecord?) {
        if (rec == null) return
        // 快照追加前列表：回滚时整体恢复。注意不能 dropLast——若「追加→超上限丢最旧→落盘失败」，
        // dropLast(1) 会把列表缩到 49 条，而存档仍是 50 条，内存与存档不一致（下次保存永久丢一条战绩）。
        val original = saveData.battleRecords
        transaction(
            tag = "battle",
            mutate = { appendBattleRecordCapped(rec) },
            rollback = { saveData.battleRecords = original }, // 整体回滚，避免内存与存档不一致
            onCommit = { /* 战绩非经济，无事件广播 */ },
        )
    }

    /** 战绩追加（须已在 [writeMutex] 临界区内调用；上限契约同上，禁止就地写 50）。 */
    private fun appendBattleRecordCapped(rec: BattleRecord) {
        saveData.battleRecords = saveData.battleRecords + rec
        if (saveData.battleRecords.size > SaveData.MAX_BATTLE_RECORDS)
            saveData.battleRecords =
                saveData.battleRecords.drop(saveData.battleRecords.size - SaveData.MAX_BATTLE_RECORDS)
    }

    // ─────────────────────────────────────────────────────────── 出战编队（2026-08 编队系统）

    /** 当前编队 characterId 列表（空槽已过滤；顺序即槽位顺序）。 */
    fun getFormation(): List<String> = saveData.getFormationIds()

    /**
     * 设置出战编队（2026-08 编队系统）。
     * 校验（任一不过返回 [WriteOutcome.Rejected]，不做任何变更）：去重后数量 ≤
     * [SaveData.MAX_FORMATION_SIZE]、全部角色已拥有；允许空列表 = 清空编队。
     * 事务范式：落盘失败回滚本次改动、不广播事件。
     */
    suspend fun setFormation(characterIds: List<String>): WriteOutcome = writeMutex.withLock {
        val ids = characterIds.distinct()
        if (ids.size > SaveData.MAX_FORMATION_SIZE) return@withLock WriteOutcome.Rejected
        val ownedIds = saveData.ownedCharacters.filterNotNull().mapTo(HashSet()) { it.characterId }
        if (ids.any { it !in ownedIds }) return@withLock WriteOutcome.Rejected

        val original = saveData.formation
        transactionLocked(
            tag = "formation",
            mutate = { saveData.formation = ids },
            rollback = { saveData.formation = original },
            onCommit = { refreshSnapshot() },
        )
    }

    // ─────────────────────────────────────────────────────────── 无尽之塔（2026-08 终局内容）

    /**
     * 挑战无尽之塔第 [floor] 层：
     * - 我方 = 当前编队（属性经 [StatsCalculator] 推导 + [TeamResonance] 共鸣加成）；
     * - 敌方 = 程序化生成（基础模板 × 层数缩放，seed 由 floor 派生 → 同层可复现、跨端一致）；
     * - 战斗为纯内存模拟（不触存档），胜利后的星尘奖励 / 最高层推进 / 战绩追加在同一事务内落盘，
     *   失败整体回滚且不广播。
     *
     * 门票门槛（2026-08 二期）：入场扣 [EconomyFormulas.towerTicketCost] 张战票，
     * 胜利返 [EconomyFormulas.towerRewardTickets] 张（净消耗 0，亏损局才是真消耗）——
     * 票源由每日商店免费补给兜底（[EconomyFormulas.dailyTicketGrant]），零票玩家不会死局。
     * 战票不足时在模拟前直接拒绝（对齐 pull 的「扣款前拦截」范式）。
     */
    suspend fun runTowerFloor(floor: Int): TowerOutcome = writeMutex.withLock {
        if (floor < 1) return@withLock TowerOutcome.Rejected
        val ticketCost = EconomyFormulas.towerTicketCost()
        val ticketsExisted = saveData.items.any { it?.itemId == BattleTicketItemId }
        val origTickets = itemCount(BattleTicketItemId)
        if (origTickets < ticketCost) return@withLock TowerOutcome.Rejected
        val teamIds = saveData.getFormationIds()
        if (teamIds.isEmpty()) return@withLock TowerOutcome.Rejected
        val myUnits = teamIds.mapNotNull { unitStatsFor(it) }
        if (myUnits.isEmpty()) return@withLock TowerOutcome.Rejected
        val team = TeamResonance.apply(myUnits).toTypedArray()
        val enemyTeam = buildTowerEnemies(floor)

        // 战斗 rng 从主 rng 派生：同 seed 注入下整个流程仍确定可复现。
        val result = BattleSimulator(Random(rng.nextLong())).simulate(team, enemyTeam, 50)

        val reward = if (result.victory) EconomyFormulas.towerRewardSoft(floor) else 0
        val newBest = if (result.victory && floor > saveData.towerBestFloor) floor else null
        // 战票净变动：入场 -cost；胜利 +rewardTickets（通常恰好抵消，净 0）
        val ticketDelta = -ticketCost +
            (if (result.victory) EconomyFormulas.towerRewardTickets() else 0)

        val originalSoft = saveData.softCurrency
        val originalBest = saveData.towerBestFloor
        val originalRecords = saveData.battleRecords

        val outcome = transactionLocked(
            tag = "tower",
            mutate = {
                if (newBest != null) saveData.towerBestFloor = newBest
                if (reward > 0) saveData.softCurrency += reward
                addItemDelta(BattleTicketItemId, ticketDelta)
                appendBattleRecordCapped(
                    BattleRecord(
                        enemyName = "无尽之塔·第${floor}层",
                        enemyElement = "",
                        victory = result.victory,
                        turns = result.turns,
                        remainingHp = result.remainingHp,
                        teamPower = team.sumOf { it.atk },
                    ),
                )
            },
            rollback = {
                saveData.softCurrency = originalSoft
                saveData.towerBestFloor = originalBest
                saveData.battleRecords = originalRecords
                restoreItemCount(BattleTicketItemId, ticketsExisted, origTickets)
            },
            onCommit = {
                if (reward > 0 || ticketDelta != 0) publishCurrencyChanged()
            },
        )

        when (outcome) {
            WriteOutcome.Success -> TowerOutcome.Completed(
                victory = result.victory,
                turns = result.turns,
                rewardSoft = reward,
                bestFloorAfter = newBest ?: saveData.towerBestFloor,
            )
            WriteOutcome.Rejected -> TowerOutcome.Rejected // 防御：前置校验已全部拦截
            WriteOutcome.SaveFailed -> TowerOutcome.SaveFailed
        }
    }

    /**
     * 由存档 + 内容定义构建战斗单位属性（[StatsCalculator] 单一事实来源；
     * 与 UI 侧 GameState.computeStats 同口径——App/爬塔/桌面模拟器共用一份公式）。
     * 角色未拥有或内容定义缺失返回 null（调用方跳过该角色，绝不让脏档炸战斗路径）。
     */
    private fun unitStatsFor(characterId: String): UnitStats? {
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

    /**
     * 程序化生成第 [floor] 层敌队：数量/缩放/基础模板全部走 EconomyFormulas（单一事实来源），
     * 元素按 floor 派生的 seed 随机分布——同层完全可复现，克制关系成为爬塔的策略维度。
     */
    private fun buildTowerEnemies(floor: Int): Array<UnitStats> {
        val towerRng = Random(floor * 1_000_003L + 7L)
        val scale = EconomyFormulas.towerEnemyStatScale(floor)
        val base = EconomyFormulas.towerEnemyBaseStats()
        val elements = listOf("Metal", "Wood", "Water", "Flame", "Earth", "Light", "Shadow", "Thunder")
        return Array(EconomyFormulas.towerEnemyCount(floor)) { i ->
            UnitStats(
                atk = (base[0] * scale).toInt(),
                def = (base[1] * scale).toInt(),
                hp = (base[2] * scale).toInt(),
                spd = base[3],
                characterId = "tower_f${floor}_e$i",
                element = elements[towerRng.nextInt(elements.size)],
            )
        }
    }

    // ─────────────────────────────────────────────────────────── 道具辅助（写锁临界区内使用）

    /** 道具数量（不存在视为 0）。须在 [writeMutex] 临界区内调用。 */
    private fun itemCount(itemId: String): Int =
        saveData.items.firstOrNull { it?.itemId == itemId }?.count ?: 0

    /** 道具数量增减（delta=0 无操作；条目缺失时以 delta 直接建档）。须在临界区内调用。 */
    private fun addItemDelta(itemId: String, delta: Int) {
        if (delta == 0) return
        val item = saveData.items.firstOrNull { it?.itemId == itemId }
        if (item != null) item.count += delta
        else saveData.items = saveData.items + ItemSaveState(itemId = itemId, count = delta)
    }

    /**
     * 回滚道具到事务前快照：此前不存在 → 整条移除（防幽灵零数量条目，对齐 pull 的
     * fragItemExisted 语义）；此前已存在 → 恢复原数量。须在临界区内调用。
     */
    private fun restoreItemCount(itemId: String, existedBefore: Boolean, originalCount: Int) {
        if (existedBefore) {
            saveData.items.firstOrNull { it?.itemId == itemId }?.count = originalCount
        } else {
            saveData.items = saveData.items.filterNot { it?.itemId == itemId }
        }
    }

    /** 当前战票数（UI 展示/门槛预判；权威判定在 runTowerFloor 临界区内复核）。 */
    fun battleTickets(): Int = itemCount(BattleTicketItemId)

    // ─────────────────────────────────────────────────────────── 每日商店（2026-08 二期）

    /** 当日 UTC 日序号字符串（存档内跨日比对键）。 */
    private fun dayKey(): String = today().toString()

    /**
     * 今日特惠槽位（确定性轮换：日期种子决定折扣包档位——同一天重开/跨端结果一致）。
     * 价格一律出自 [EconomyFormulas]（单一事实来源），禁止就地写数字。
     */
    fun dailyOffers(): List<DailyOffer> {
        val discountPack = if (today() % 2 == 0L) 2 else 1
        return listOf(
            DailyOffer(
                index = 0,
                kind = DailyOfferKind.FREE_SUPPLY,
                pack = 0,
                title = "每日补给",
                detail = "星尘 ${EconomyFormulas.dailyFreeSupplySoft()} ＋ 战票 ×${EconomyFormulas.dailyTicketGrant()}",
                costSoft = 0,
            ),
            DailyOffer(
                index = 1,
                kind = DailyOfferKind.DISCOUNT_PACK,
                pack = discountPack,
                title = "折扣碎片包 · ${if (discountPack == 2) "大" else "小"}",
                detail = "${EconomyFormulas.fragmentPackSize(discountPack)} 片星魂碎片 · 8 折",
                costSoft = EconomyFormulas.dailyDiscountPackCost(discountPack),
            ),
            DailyOffer(
                index = 2,
                kind = DailyOfferKind.TICKET_BUNDLE,
                pack = 0,
                title = "战票礼包",
                detail = "战票 ×${EconomyFormulas.dailyTicketBundleSize()}",
                costSoft = EconomyFormulas.dailyTicketBundleCost(),
            ),
        )
    }

    /** 今日已购槽位下标（存档日期与今天不一致 = 跨日未消费，返回空表）。 */
    fun dailyBoughtToday(): List<Int> =
        if (saveData.dailyShopDate == dayKey()) saveData.dailyShopBought.filterNotNull() else emptyList()

    /**
     * 购买每日特惠槽位 [index]：每槽每日限一次（跨日整体重置）；星尘不足 / 槽位非法 /
     * 已购过 → [WriteOutcome.Rejected]。效果与限购记录同一事务，落盘失败整体回滚。
     */
    suspend fun buyDailyOffer(index: Int): WriteOutcome = writeMutex.withLock {
        val offer = dailyOffers().firstOrNull { it.index == index }
            ?: return@withLock WriteOutcome.Rejected
        val key = dayKey()
        val rolledOver = saveData.dailyShopDate != key
        val bought = if (rolledOver) emptyList() else saveData.dailyShopBought.filterNotNull()
        if (index in bought) return@withLock WriteOutcome.Rejected

        // 效果参数（免费补给为正收入；付费档先扣星尘）
        var softDelta = -offer.costSoft
        var fragDelta = 0
        var ticketDelta = 0
        when (offer.kind) {
            DailyOfferKind.FREE_SUPPLY -> {
                softDelta = EconomyFormulas.dailyFreeSupplySoft()
                ticketDelta = EconomyFormulas.dailyTicketGrant()
            }

            DailyOfferKind.DISCOUNT_PACK -> fragDelta = EconomyFormulas.fragmentPackSize(offer.pack)

            DailyOfferKind.TICKET_BUNDLE -> ticketDelta = EconomyFormulas.dailyTicketBundleSize()
        }
        if (offer.costSoft > 0 && saveData.softCurrency < offer.costSoft) {
            return@withLock WriteOutcome.Rejected
        }

        val origSoft = saveData.softCurrency
        val ticketsExisted = saveData.items.any { it?.itemId == BattleTicketItemId }
        val origTickets = itemCount(BattleTicketItemId)
        val fragsExisted = saveData.items.any { it?.itemId == StarFragmentItemId }
        val origFrags = itemCount(StarFragmentItemId)
        val origDate = saveData.dailyShopDate
        val origBought = saveData.dailyShopBought

        transactionLocked(
            tag = "daily",
            mutate = {
                saveData.softCurrency += softDelta
                addItemDelta(StarFragmentItemId, fragDelta)
                addItemDelta(BattleTicketItemId, ticketDelta)
                saveData.dailyShopDate = key
                saveData.dailyShopBought = bought + index
            },
            rollback = {
                saveData.softCurrency = origSoft
                restoreItemCount(StarFragmentItemId, fragsExisted, origFrags)
                restoreItemCount(BattleTicketItemId, ticketsExisted, origTickets)
                saveData.dailyShopDate = origDate
                saveData.dailyShopBought = origBought
            },
            onCommit = { publishCurrencyChanged() },
        )
    }

    // ─────────────────────────────────────────────────────────── 成就（2026-08 二期）

    /** 全部成就的当前状态（解锁与否实时计算不落盘；claimed 以存档为准）。 */
    fun achievementStatuses(): List<AchievementStatus> {
        val claimed = saveData.claimedAchievementIds().toSet()
        val progress = achievementProgressSnapshot()
        return Achievements.ALL.map { def ->
            AchievementStatus(def, def.unlocked(progress), def.id in claimed)
        }
    }

    /** 成就进度快照（从存档推导，供定义侧纯函数判定；口径与 UI 展示一致）。 */
    private fun achievementProgressSnapshot(): Achievements.Progress = Achievements.Progress(
        ownedCount = saveData.ownedCharacters.count { it != null },
        totalPulls = saveData.gachaCounters.filterNotNull().sumOf { it.count },
        towerBestFloor = saveData.towerBestFloor,
        formationSize = saveData.getFormationIds().size,
        softCurrency = saveData.softCurrency,
        fullLeveledChars = saveData.ownedCharacters.filterNotNull()
            .count { it.level >= maxLevelForStage(it.stage) },
    )

    /**
     * 领取成就奖励：未知 id / 已领取 / 未解锁 → Rejected；
     * 奖励发放与领取标记同一事务（失败整体回滚、成功经 publishCurrencyChanged 刷快照）。
     */
    suspend fun claimAchievement(id: String): WriteOutcome = writeMutex.withLock {
        val def = Achievements.byId[id] ?: return@withLock WriteOutcome.Rejected
        if (id in saveData.claimedAchievementIds()) return@withLock WriteOutcome.Rejected
        if (!def.unlocked(achievementProgressSnapshot())) return@withLock WriteOutcome.Rejected

        val origSoft = saveData.softCurrency
        val ticketsExisted = saveData.items.any { it?.itemId == BattleTicketItemId }
        val origTickets = itemCount(BattleTicketItemId)
        val origClaimed = saveData.claimedAchievements

        transactionLocked(
            tag = "achievement",
            mutate = {
                if (def.rewardSoft > 0) saveData.softCurrency += def.rewardSoft
                if (def.rewardTickets > 0) addItemDelta(BattleTicketItemId, def.rewardTickets)
                saveData.claimedAchievements = saveData.claimedAchievements + id
            },
            rollback = {
                saveData.softCurrency = origSoft
                restoreItemCount(BattleTicketItemId, ticketsExisted, origTickets)
                saveData.claimedAchievements = origClaimed
            },
            onCommit = { publishCurrencyChanged() },
        )
    }

    // ─────────────────────────────────────────────────────────── 养成操作
    // 所有写操作遵循 Pull 的事务范式；回滚路径不广播事件。
    // 未拥有的角色（不在 OwnedCharacters）一律拒绝养成。

    // 以下四个公式方法保留为实例方法只是为了不破坏 UI 调用点；实现一律委托
    // EconomyFormulas（纯领域、单一事实来源）。禁止在此处重新写数字。

    /** 等级上限随突破阶段提高：Stage×20（Stage1→20 级，Stage4→80 级）。 */
    fun maxLevelForStage(stage: Int): Int = EconomyFormulas.maxLevelForStage(stage)

    /** 从 level 升到 level+1 的星尘消耗（随等级线性上升）。 */
    fun levelCost(level: Int): Int = EconomyFormulas.levelCost(level)

    /** stage→stage+1 突破所需星魂碎片（重复角色补偿货币）。 */
    fun ascendFragments(stage: Int): Int = EconomyFormulas.ascendFragments(stage)

    /** stage→stage+1 突破所需星尘。 */
    fun ascendSoft(stage: Int): Int = EconomyFormulas.ascendSoft(stage)

    /** 升星（Stars+1）所需星魂碎片（随当前星数线性上升：1★→2★ 耗 20，2★→3★ 耗 40…）。 */
    fun starUpFragments(stars: Int): Int = EconomyFormulas.starUpFragments(stars)

    /** 当前经验条进度（本等级内已积累 / 本级所需）。达到等级上限时返回 (need, need)。 */
    fun expProgress(charId: String): Pair<Int, Int> {
        val save = getSave(charId) ?: return 0 to 1
        val need = EconomyFormulas.expForLevel(save.level)
        val cur = (save.totalExp - EconomyFormulas.cumulativeExp(save.level)).coerceIn(0, need)
        if (save.level >= maxLevelForStage(save.stage)) return need to need
        return cur to need
    }

    /** 取角色存档；未拥有返回 null（养成操作应据此拒绝）。 */
    fun getSave(charId: String): CharacterSaveState? =
        saveData.ownedCharacters.firstOrNull { it?.characterId == charId }

    /** 当前持有的星魂碎片（重复角色补偿货币，以 Item 形式存储）。 */
    fun getStarFragments(): Int {
        val item = saveData.items.firstOrNull { it?.itemId == StarFragmentItemId }
        return item?.count ?: 0
    }

    // ── 商店 ──
    // 购买遵循与 Pull 一致的事务范式：预算校验 → 改内存 → 落盘 → 失败回滚 → 仅成功才广播。
    // 定价一律走 EconomyFormulas（单一事实来源），禁止就地写数字。

    /** 购买星魂碎片包（pack=1 小包 / 2 大包）。非法档位、星尘不足 → Rejected；落盘失败 → SaveFailed。 */
    suspend fun buyFragmentPack(pack: Int): WriteOutcome {
        val frags = EconomyFormulas.fragmentPackSize(pack)
        val cost = EconomyFormulas.fragmentPackCost(pack)
        if (frags <= 0 || cost <= 0) return WriteOutcome.Rejected
        if (saveData.softCurrency < cost) return WriteOutcome.Rejected

        val origSoft = saveData.softCurrency
        val item = saveData.items.firstOrNull { it?.itemId == StarFragmentItemId }
        return transaction(
            tag = "shop",
            mutate = {
                saveData.softCurrency -= cost
                if (item != null) item.count += frags
                else saveData.items = saveData.items + ItemSaveState(itemId = StarFragmentItemId, count = frags)
            },
            rollback = {
                // 回滚（不广播）：新增条目整体移除（对齐 Pull 的 fragItemExisted 语义），既有条目减回
                saveData.softCurrency = origSoft
                if (item != null) item.count -= frags
                else saveData.items = saveData.items.filterNot { it?.itemId == StarFragmentItemId }
            },
            onCommit = { publishCurrencyChanged() },
        )
    }

    /** 钻石兑换星尘。钻石不足 → Rejected；落盘失败 → SaveFailed。 */
    suspend fun buyDiamondExchange(): WriteOutcome {
        val cost = EconomyFormulas.diamondExchangeCost()
        val yield = EconomyFormulas.diamondExchangeYield()
        if (saveData.hardCurrency < cost) return WriteOutcome.Rejected

        val origHard = saveData.hardCurrency
        return transaction(
            tag = "shop",
            mutate = {
                saveData.hardCurrency -= cost
                saveData.softCurrency += yield
            },
            rollback = {
                saveData.hardCurrency = origHard
                saveData.softCurrency -= yield
            },
            onCommit = { publishCurrencyChanged() },
        )
    }

    /**
     * 升级 n 级（默认 1）。星尘不足或已达等级上限时尽可能少升；一级都升不了返回 Rejected。
     * 每升 1 级 +1 天赋点。落盘失败回滚（SaveFailed）。
     */
    suspend fun levelUp(charId: String, n: Int = 1): WriteOutcome {
        val save = getSave(charId) ?: return WriteOutcome.Rejected
        if (n <= 0) return WriteOutcome.Rejected

        // 预算规划抽到纯领域（EconomyFormulas.planLevelUp），边界行为由单元测试锁死。
        val (gained, cost) = EconomyFormulas.planLevelUp(
            save.level, maxLevelForStage(save.stage), saveData.softCurrency, n,
        )
        if (gained <= 0) return WriteOutcome.Rejected // 一级都升不了（资源不足 / 已满级）

        val target = save.level + gained
        // 货币升级等价于「买下已完成等级的累计经验」：累加而非覆写，
        // 保持 totalExp 与等级一致，且不抹掉 addExp 已积累的经验（#1 经验条恒 0 的根因）。
        val expGain = EconomyFormulas.cumulativeExp(target) - EconomyFormulas.cumulativeExp(save.level)
        val origSoft = saveData.softCurrency
        // 记录升级前的 totalExp 原值：回滚时直接恢复（而非按等级重算累计经验），
        // 否则 addExp 攒下的经验零头会在「落盘失败回滚」时从内存消失，
        // 与磁盘旧档分叉，下次成功保存即永久丢失（P1-1）。
        val origTotal = save.totalExp
        return transaction(
            tag = "levelUp",
            mutate = {
                saveData.softCurrency -= cost
                save.totalExp += expGain
                save.level = target
                save.unspentPoints += gained
            },
            rollback = {
                saveData.softCurrency = origSoft
                save.level -= gained
                save.unspentPoints -= gained
                save.totalExp = origTotal
            },
            onCommit = {
                publishCurrencyChanged()
                publishProgressionChanged()
            },
        )
    }

    /**
     * 增加经验（战斗/活动奖励的统一出口）。经验累计进 [CharacterSaveState.totalExp]，
     * 等级由 [ProgressionEngine.expToLevel] 派生并钳到本突破阶段上限——totalExp 是经验唯一真值
     * （与 expForLevel/cumulativeExp 同口径）。Kotlin 版的 expToLevel 此前未被调用，经验条因此死掉；
     * 本方法是经验条真正能推进的根因修复（#1）。
     *
     * 落盘失败回滚本次经验与可能的自动升级（不广播）。
     * @return 实际升的级数（0 = 仅积累经验、未升级；负数永不返回）
     */
    suspend fun addExp(charId: String, amount: Int): Int {
        if (amount <= 0) return 0
        val save = getSave(charId) ?: return 0
        val cap = maxLevelForStage(save.stage)

        val origTotal = save.totalExp
        val origLevel = save.level
        val origPoints = save.unspentPoints
        // 目标等级由「累计经验 + 本次奖励」纯推导（不在 mutate 内算，便于回滚只恢复原值）
        val derived = progression.expToLevel(save.totalExp + amount)
        val newLevel = if (derived > cap) cap else derived
        val gained = newLevel - origLevel

        val outcome = transaction(
            tag = "addExp",
            mutate = {
                save.totalExp += amount
                if (gained > 0) save.unspentPoints += gained
                save.level = newLevel
            },
            rollback = {
                // 回滚（不广播）：内存与存档保持一致
                save.totalExp = origTotal
                save.level = origLevel
                if (gained > 0) save.unspentPoints = origPoints
            },
            onCommit = { publishProgressionChanged() },
        )
        return if (outcome == WriteOutcome.Success) gained else 0
    }

    /** 突破（Stage+1）。需未达 MaxStage 且星魂碎片 + 星尘充足。落盘失败回滚（SaveFailed）。
     *  P2-12：突破后按已积累经验重推导等级（上限随阶段提高）——此前玩家带「银行经验」
     *  突破后等级停留在旧上限，再用星尘 levelUp 会为已经用经验换到的等级再付一次钱。 */
    suspend fun ascend(charId: String): WriteOutcome {
        val save = getSave(charId) ?: return WriteOutcome.Rejected
        val def = character(charId) ?: return WriteOutcome.Rejected
        if (save.stage >= def.maxStage) return WriteOutcome.Rejected

        val frags = ascendFragments(save.stage)
        val soft = ascendSoft(save.stage)
        val have = getStarFragments()
        if (have < frags || saveData.softCurrency < soft) return WriteOutcome.Rejected

        val origSoft = saveData.softCurrency
        val origFrags = have
        val origLevel = save.level
        val origPoints = save.unspentPoints
        val item = saveData.items.firstOrNull { it?.itemId == StarFragmentItemId }
        return transaction(
            tag = "ascend",
            mutate = {
                saveData.softCurrency -= soft
                if (item != null) item.count -= frags
                save.stage += 1
                // 突破后重推导：totalExp 是经验唯一真值，等级 = min(expToLevel(totalExp), 新上限)，
                // 差额级数补发天赋点（与 addExp 的升级语义一致）
                val derived = progression.expToLevel(save.totalExp)
                val newCap = maxLevelForStage(save.stage)
                val target = minOf(derived, newCap)
                if (target > save.level) {
                    save.unspentPoints += target - save.level
                    save.level = target
                }
            },
            rollback = {
                saveData.softCurrency = origSoft
                if (item != null) item.count = origFrags
                save.level = origLevel
                save.unspentPoints = origPoints
                save.stage -= 1
            },
            onCommit = {
                publishCurrencyChanged()
                publishProgressionChanged()
            },
        )
    }

    /** 升星（Stars+1）。需未达 MaxStars 且星魂碎片充足。落盘失败回滚（SaveFailed）。每次仅 +1 星。 */
    suspend fun starUp(charId: String): WriteOutcome {
        val save = getSave(charId) ?: return WriteOutcome.Rejected
        val def = character(charId) ?: return WriteOutcome.Rejected
        if (save.stars >= def.maxStars) return WriteOutcome.Rejected

        val cost = starUpFragments(save.stars)
        val have = getStarFragments()
        if (have < cost) return WriteOutcome.Rejected

        val origFrags = have
        val item = saveData.items.firstOrNull { it?.itemId == StarFragmentItemId }
        return transaction(
            tag = "starUp",
            mutate = {
                if (item != null) item.count -= cost
                save.stars += 1
            },
            rollback = {
                if (item != null) item.count = origFrags
                save.stars -= 1
            },
            onCommit = {
                publishCurrencyChanged()
                publishProgressionChanged()
            },
        )
    }

    /** 取角色天赋树（含节点与前置关系）。无树返回 null。 */
    fun getTalentTree(charId: String): TalentTreeData? {
        val def = character(charId) ?: return null
        return talentTree(def.talentTreeId)
    }

    /** 构建 nodeId → 前置节点列表 的映射，喂给 TalentEngine.canAllocate。 */
    fun prereqMap(tree: TalentTreeData): Map<String, List<String>> {
        val cache = prereqCache ?: mutableMapOf<String, Map<String, List<String>>>().also { prereqCache = it }
        return cache.getOrPut(tree.treeId) { buildPrereq(tree) }
    }

    private fun buildPrereq(tree: TalentTreeData): Map<String, List<String>> {
        val m = mutableMapOf<String, List<String>>()
        for (n in tree.nodes) m[n.nodeId] = n.prerequisiteNodeIds
        return m
    }

    /** 天赋加点前置校验：角色存在 + 树存在 + 节点存在 + 未点亮 + 点数足够；任一失败返回 null。 */
    private fun talentCheck(charId: String, nodeId: String): TalentCheck? {
        val save = getSave(charId) ?: return null
        val tree = getTalentTree(charId) ?: return null
        val node = tree.nodes.firstOrNull { it.nodeId == nodeId } ?: return null
        if (save.talentPoints.contains(nodeId)) return null
        if (save.unspentPoints < node.cost) return null
        return TalentCheck(save, node, tree)
    }

    /** 不落盘地预判某天赋节点当前是否可点亮（用于 UI 三态与按钮可用性）。 */
    fun canAllocateTalent(charId: String, nodeId: String): Boolean {
        // C# 在渲染路径上曾有 "TalentPoints": null 覆盖字段初始化器的 NRE（逐节点调用直接闪退），
        // 用 ??= 兜底；Kotlin 类型系统 + coerceInputValues 保证 talentPoints 非空，天然免疫。
        val check = talentCheck(charId, nodeId) ?: return false
        return talent.canAllocate(nodeId, check.save.talentPoints.filterNotNull(), prereqMap(check.tree))
    }

    /** 点亮天赋节点：校验前置（TalentEngine）与天赋点余额，扣点并落盘。
     * 已点过 / 点不够 / 前置未满足 / 落盘失败分别返回 Rejected / SaveFailed。 */
    suspend fun allocateTalent(charId: String, nodeId: String): WriteOutcome {
        val check = talentCheck(charId, nodeId) ?: return WriteOutcome.Rejected
        val save = check.save
        val node = check.node
        if (!talent.canAllocate(nodeId, save.talentPoints.filterNotNull(), prereqMap(check.tree))) {
            return WriteOutcome.Rejected
        }

        val origPoints = save.unspentPoints
        return transaction(
            tag = "talent",
            mutate = {
                save.unspentPoints -= node.cost
                save.talentPoints = save.talentPoints + nodeId
            },
            rollback = {
                save.unspentPoints = origPoints
                save.talentPoints = save.talentPoints.filterNot { it == nodeId }
            },
            onCommit = { publishProgressionChanged() },
        )
    }

    // ── 养成变动统一出口（EventBus 接线点）──
    // 事件是无载荷标记（订阅方自行重读当前角色），不带 charId 参数，
    // 避免「看起来会按角色过滤、实际全量广播」的误导性签名。
    private fun publishProgressionChanged() {
        refreshSnapshot() // 养成/货币改动同样推进状态快照（revision 触发 UI 重读存档）
        EventBus.publish(ProgressionChanged)
        EventBus.dispatch()
    }

    companion object {
        const val StarFragmentItemId = "item_star_fragment"

        /** 战票道具 id（无尽之塔门票；对齐星魂碎片的 item_ 前缀惯例，走通用道具系统）。 */
        const val BattleTicketItemId = "item_battle_ticket"
    }

    /** Pull 内部计划条目（对齐 C# 的 plan 元组）。 */
    private data class PlanItem(
        val id: String,
        val def: CharacterDataEntry?,
        val isNew: Boolean,
        val fragments: Int,
        val rarity: Int,
    )

    /** 天赋校验结果（P3-3：替代 Triple 元组，字段具名可读）。 */
    private data class TalentCheck(
        val save: CharacterSaveState,
        val node: TalentNodeData,
        val tree: TalentTreeData,
    )
}
