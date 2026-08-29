package com.milan.game.services

import com.milan.game.data.BattleRecord
import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveData
import com.milan.game.data.SaveManager
import com.milan.game.data.SaveProvider
import com.milan.game.domain.gacha.GachaEngine
import com.milan.game.domain.progression.ProgressionEngine
import com.milan.game.domain.progression.TalentEngine
import com.milan.game.domain.progression.EconomyFormulas
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 爬塔挑战结果（2026-08 无尽之塔；顶层类型，对齐 [WriteOutcome] / [PullOutcome] 风格）。
 */
sealed interface TowerOutcome {
    /**
     * 战斗已完成。[victory] 时 [rewardSoft] 为本次发放的星尘奖励（仅刷新纪录时为正）；
     * [rewardHard] 仅在首次攻克 5 的倍数层时>0（里程碑钻石）；
     * [bestFloorAfter] 为结算后的历史最高层；[log] 为逐回合攻击事件流（不落盘）。
     */
    data class Completed(
        val victory: Boolean,
        val turns: Int,
        val rewardSoft: Int,
        val rewardHard: Int = 0,
        val bestFloorAfter: Int,
        val log: List<com.milan.game.domain.battle.StrikeEvent> = emptyList(),
        /** F4：本次给出战编队每人发放的经验（0 = 未发放，如失败局）。 */
        val rewardExp: Int = 0,
        /**
         * 本次是否刷新了历史最高层（M1，2026-08-28 审查修复）。
         * 此前 UI 用 `bestFloorAfter >= nextFloor` 反推，而 nextFloor 在结算返回前已被
         * 快照刷新（best 已 +1），条件恒为 false → 「纪录推进至第 N 层」永远不会显示。
         * 服务层直接给出判定，避免在 UI 侧与快照时序赛跑。
         */
        val recordAdvanced: Boolean = false,
    ) : TowerOutcome

    /** 拒绝：floor 非法 / 编队为空 / 队伍构建失败。 */
    data object Rejected : TowerOutcome

    /** 落盘失败（奖励与纪录已整体回滚）。 */
    data object SaveFailed : TowerOutcome

    /** P3-7 平局：回合耗尽双方仍存活，门票不消耗，无奖励。 */
    data class Draw(
        val turns: Int,
        val log: List<com.milan.game.domain.battle.StrikeEvent> = emptyList(),
    ) : TowerOutcome
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
 * 游戏服务**门面**（2026-08-28 P1 重构）。
 *
 * 原实现是 1385 行 / 46 个公开方法的上帝类，聚合了五个业务域。现按**业务聚合**拆分为：
 *
 * | 服务 | 职责 |
 * |---|---|
 * | [GachaService] | 抽卡、保底与 UP 定轨、抽卡历史 |
 * | [ProgressionService] | 升级/经验/突破/升星、天赋加点 |
 * | [TowerService] | 出战编队、无尽之塔结算 |
 * | [EconomyService] | 货币增减、商店购买 |
 * | [MetaService] | 设置、存档重置、战绩、每日商店、成就 |
 *
 * 本类**不含任何业务规则**，只做三件事：
 * 1. 构造 [ServiceCore]（共享状态：存档、写锁、快照、领域引擎）与五个聚合服务；
 * 2. 转发全部公开 API——**UI 调用点零改动**，拆分对上层透明；
 * 3. 承载跨服务的公共契约说明。
 *
 * 五个聚合服务之间**互不调用**（已验证：所有方法只依赖 [ServiceCore]），
 * 因此不存在 `writeMutex` 重入问题——Mutex 不可重入，跨服务加锁会直接死锁。
 *
 * 约束（AGENTS.md 红线，拆分后保持不变）：
 * - 领域计算一律委托 `EconomyFormulas` / `PityCounter` / `TalentEngine`，禁止就地写数字；
 * - 写操作事务范式：预算校验 → 改内存 → 落盘 → 失败回滚 → 仅成功广播；
 * - 内容数据 data.json 优先，失败回退 [GameContent] 兜底，两条路径都经 `GameContent.enrich`。
 */
class GameService(
    saveProvider: SaveProvider,
    contentJson: String? = null,
    private val onTrace: (String) -> Unit = {},
    private val rng: Random = Random.Default,
    /** UTC 日序号提供器（每日商店按天重置；注入便于测试固定「今天」，默认系统时钟）。 */
    private val today: () -> Long = { System.currentTimeMillis() / 86_400_000L },
) {
    private val core = ServiceCore(
        saveManager = SaveManager(saveProvider, onTrace),
        gacha = GachaEngine(rng),
        talent = TalentEngine(),
        progression = ProgressionEngine(),
        rng = rng,
        onTrace = onTrace,
        today = today,
    )

    private val gachaService = GachaService(core)
    private val progressionService = ProgressionService(core)
    private val towerService = TowerService(core)
    private val economyService = EconomyService(core)
    private val metaService = MetaService(core)

    init {
        loadContent(contentJson)
        // 初始快照：载入存档后立即发布一次真实值，避免 UI 在首次写操作前看到全 0
        core.refreshSnapshot()
    }

    // ─────────────────────────── 状态与内容（直连内核）───────────────────────────

    /** UI 订阅的状态快照（revision 为重组触发器）。 */
    val snapshot: StateFlow<GameSnapshot> get() = core.snapshot

    /** 进程级存档引用（与 SaveManager.current 同一对象）。重置存档时整体替换为新档引用。 */
    val saveData: SaveData get() = core.saveData

    /** 角色内容列表。 */
    val characters: List<CharacterDataEntry> get() = core.characters

    /** 卡池内容列表。 */
    val pools: List<GachaPoolDataEntry> get() = core.pools

    /** 天赋树内容列表。 */
    val talentTrees: List<TalentTreeData> get() = core.talentTrees

    /** 按 ID 查角色定义（O(1) 索引；未找到返回 null）。 */
    fun character(id: String): CharacterDataEntry? = core.character(id)

    /** 按 ID 查天赋树（O(1) 索引；未找到返回 null）。 */
    fun talentTree(id: String): TalentTreeData? = core.talentTree(id)

    /** 加载内容：优先解析 data.json；空/损坏/无有效角色 → 回退 [GameContent] 兜底。 */
    fun loadContent(rawJson: String?) = core.loadContent(rawJson)

    /** 立即落盘（设置项改 SaveData 字段后调用）。 */
    suspend fun save(): Boolean = withContext(Dispatchers.IO) { core.saveManager.save() }

    // ─────────────────────────── 公式转发（单一事实来源）───────────────────────────

    /** 等级上限随突破阶段提高：Stage×20（Stage1→20 级，Stage4→80 级）。 */
    fun maxLevelForStage(stage: Int): Int = core.maxLevelForStage(stage)

    /** 从 level 升到 level+1 的星尘消耗（随等级线性上升）。 */
    fun levelCost(level: Int): Int = core.levelCost(level)

    /** stage→stage+1 突破所需星魂碎片。 */
    fun ascendFragments(stage: Int): Int = core.ascendFragments(stage)

    /** stage→stage+1 突破所需星尘。 */
    fun ascendSoft(stage: Int): Int = core.ascendSoft(stage)

    /** 升星（Stars+1）所需星魂碎片。 */
    fun starUpFragments(stars: Int): Int = core.starUpFragments(stars)

    /** 当前持有的星魂碎片。 */
    fun getStarFragments(): Int = core.getStarFragments()

    /** 取角色存档；未拥有返回 null。 */
    fun getSave(charId: String): CharacterSaveState? = core.getSave(charId)

    /** 构建 nodeId → 前置节点列表 的映射，喂给 TalentEngine.canAllocate。 */
    fun prereqMap(tree: TalentTreeData): Map<String, List<String>> = core.prereqMap(tree)

    // ─────────────────────────── 抽卡 ───────────────────────────

    /** 重复角色按稀有度补偿的星魂碎片数量。 */
    fun fragmentsForRarity(rarity: Int): Int = gachaService.fragmentsForRarity(rarity)

    /** 抽卡历史快照（时间正序，最旧在前；UI 自行倒序展示）。 */
    fun pullHistory(): List<com.milan.game.data.PullLogEntry> = gachaService.pullHistory()

    /** 抽卡（单抽 / 十连），整体事务。 */
    suspend fun pull(poolId: String, tenPull: Boolean): PullOutcome = gachaService.pull(poolId, tenPull)

    // ─────────────────────────── 经济与商店 ───────────────────────────

    /** 扣除星尘（amount<=0 或余额不足拒绝）。 */
    suspend fun spendSoft(amount: Int): WriteOutcome = economyService.spendSoft(amount)

    /** 增加星尘（amount<=0 拒绝）。 */
    suspend fun addSoft(amount: Int): WriteOutcome = economyService.addSoft(amount)

    /** 扣除钻石（amount<=0 或余额不足拒绝）。 */
    suspend fun spendHard(amount: Int): WriteOutcome = economyService.spendHard(amount)

    /** 增加钻石（amount<=0 拒绝）。 */
    suspend fun addHard(amount: Int): WriteOutcome = economyService.addHard(amount)

    /** 当前战票数。 */
    fun battleTickets(): Int = economyService.battleTickets()

    /** 购买星魂碎片包（pack=1 小包 / 2 大包）。 */
    suspend fun buyFragmentPack(pack: Int): WriteOutcome = economyService.buyFragmentPack(pack)

    /** 钻石兑换星尘。 */
    suspend fun buyDiamondExchange(): WriteOutcome = economyService.buyDiamondExchange()

    /** 星魂碎片兑换星尘（碎片回收阀门）。 */
    suspend fun exchangeFragmentsForSoft(): WriteOutcome = economyService.exchangeFragmentsForSoft()

    // ─────────────────────────── 养成 ───────────────────────────

    /** 当前经验条进度（本等级内已积累 / 本级所需）。 */
    fun expProgress(charId: String): Pair<Int, Int> = progressionService.expProgress(charId)

    /** 升级 n 级（默认 1）。 */
    suspend fun levelUp(charId: String, n: Int = 1): WriteOutcome = progressionService.levelUp(charId, n)

    /**
     * 增加经验。⚠️ 本方法自身持写锁——已在临界区内的调用方不可调用（Mutex 不可重入）。
     * @return 实际升的级数。
     */
    suspend fun addExp(charId: String, amount: Int): Int = progressionService.addExp(charId, amount)

    /** 突破（Stage+1）。 */
    suspend fun ascend(charId: String): WriteOutcome = progressionService.ascend(charId)

    /** 升星（Stars+1）。 */
    suspend fun starUp(charId: String): WriteOutcome = progressionService.starUp(charId)

    /** 取角色天赋树（含节点与前置关系）。 */
    fun getTalentTree(charId: String): TalentTreeData? = progressionService.getTalentTree(charId)

    /** 预判某天赋节点当前是否可点亮（不落盘）。 */
    fun canAllocateTalent(charId: String, nodeId: String): Boolean =
        progressionService.canAllocateTalent(charId, nodeId)

    /** 点亮天赋节点。 */
    suspend fun allocateTalent(charId: String, nodeId: String): WriteOutcome =
        progressionService.allocateTalent(charId, nodeId)

    // ─────────────────────────── 编队与爬塔 ───────────────────────────

    /** 当前编队 characterId 列表（顺序即槽位顺序）。 */
    fun getFormation(): List<String> = towerService.getFormation()

    /** 设置出战编队（整体替换）。 */
    suspend fun setFormation(characterIds: List<String>): WriteOutcome = towerService.setFormation(characterIds)

    /** 切换单个角色的入队状态（读-改-写在锁内原子完成，防连点竞态）。 */
    suspend fun toggleFormation(characterId: String): WriteOutcome = towerService.toggleFormation(characterId)

    /** 挑战无尽之塔第 [floor] 层。 */
    suspend fun runTowerFloor(floor: Int): TowerOutcome = towerService.runTowerFloor(floor)

    // ─────────────────────────── 元进度 ───────────────────────────

    /** 音效开关持久化。 */
    suspend fun setSoundEnabled(enabled: Boolean): WriteOutcome = metaService.setSoundEnabled(enabled)

    /** 振动开关持久化。 */
    suspend fun setVibrationEnabled(enabled: Boolean): WriteOutcome = metaService.setVibrationEnabled(enabled)

    /** 推送开关持久化。 */
    suspend fun setPushEnabled(enabled: Boolean): WriteOutcome = metaService.setPushEnabled(enabled)

    /** 重置存档为新档（删除失败返回 false 且不动内存）。 */
    suspend fun resetSave(): Boolean = metaService.resetSave()

    /** 读取战绩（最近在前）。 */
    fun getBattleRecords(): List<BattleRecord> = metaService.getBattleRecords()

    /** 追加一条战绩并落盘（上限 50 条）。 */
    suspend fun recordBattle(rec: BattleRecord?) = metaService.recordBattle(rec)

    /** 今日特惠槽位（确定性轮换）。 */
    fun dailyOffers(): List<DailyOffer> = metaService.dailyOffers()

    /** 今日已购槽位下标。 */
    fun dailyBoughtToday(): List<Int> = metaService.dailyBoughtToday()

    /** 购买每日特惠槽位。 */
    suspend fun buyDailyOffer(index: Int): WriteOutcome = metaService.buyDailyOffer(index)

    /** 全部成就的当前状态。 */
    fun achievementStatuses(): List<AchievementStatus> = metaService.achievementStatuses()

    /** 领取成就奖励。 */
    suspend fun claimAchievement(id: String): WriteOutcome = metaService.claimAchievement(id)

    companion object {
        const val StarFragmentItemId = ServiceCore.StarFragmentItemId

        /** 战票道具 id（无尽之塔门票）。 */
        const val BattleTicketItemId = ServiceCore.BattleTicketItemId
    }
}
