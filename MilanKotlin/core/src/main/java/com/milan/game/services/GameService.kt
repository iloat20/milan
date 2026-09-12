package com.milan.game.services

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveData
import com.milan.game.data.SaveManager
import com.milan.game.data.SaveProvider
import com.milan.game.domain.gacha.GachaEngine
import com.milan.game.domain.progression.ProgressionEngine
import com.milan.game.domain.progression.TalentEngine
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

/**
 * 跨系统进度事件（P2-10 B10 重构）。
 *
 * 门面 `onProgress` 唯一编排职责：领域写操作成功后上报每日任务进度 + 通行证经验。
 * 用密封接口建模 5 种事件，替代原先 `(type, amount, battlePassExp)` 三参裸调用，
 * 让调用点意图更清晰、类型更安全。
 */
sealed interface ProgressEvent {
    /** 每日任务类型。 */
    val missionType: com.milan.game.data.DailyMissionType
    /** 进度增量（默认 1）。 */
    val amount: Int get() = 1
    /** 通行证经验（0 = 不发放）。 */
    val battlePassExp: Int get() = 0

    /** 抽卡（抽数 = amount）。 */
    data class GachaPull(override val amount: Int) : ProgressEvent {
        override val missionType get() = com.milan.game.data.DailyMissionType.PULL_GACHA
        override val battlePassExp get() = com.milan.game.data.MonetizationSaveData.BP_DAILY_TASK_EXP
    }

    /** 角色升级（级数 = amount）。 */
    data class LevelUp(override val amount: Int) : ProgressEvent {
        override val missionType get() = com.milan.game.data.DailyMissionType.LEVEL_UP_CHARACTER
        override val battlePassExp get() = com.milan.game.data.MonetizationSaveData.BP_DAILY_TASK_EXP
    }

    /** 爬塔挑战。 */
    data object TowerBattle : ProgressEvent {
        override val missionType get() = com.milan.game.data.DailyMissionType.BATTLE_TOWER
        override val battlePassExp get() = com.milan.game.data.MonetizationSaveData.BP_DAILY_TASK_EXP
    }

    /** 竞技场挑战（无通行证经验）。 */
    data object ArenaChallenge : ProgressEvent {
        override val missionType get() = com.milan.game.data.DailyMissionType.CHALLENGE_ARENA
    }

    /** 剧情关卡完成。 */
    data object StoryComplete : ProgressEvent {
        override val missionType get() = com.milan.game.data.DailyMissionType.COMPLETE_STORY
        override val battlePassExp get() = com.milan.game.data.MonetizationSaveData.BP_DAILY_TASK_EXP
    }

    /** 领取好感等级奖励（2026-09-10：接通 CLAIM_AFFINITY 任务生产点）。 */
    data object ClaimAffinityReward : ProgressEvent {
        override val missionType get() = com.milan.game.data.DailyMissionType.CLAIM_AFFINITY
    }
}

/** 成就条目的状态包（定义 + 实时解锁态 + 存档领取态）。 */
data class AchievementStatus(
    val def: com.milan.game.services.AchievementDef,
    val unlocked: Boolean,
    val claimed: Boolean,
)

/**
 * 游戏服务**门面**（2026-08-28 P1 重构；2026-09-06 S2/S4 重组；2026-09-08 P1-5 接口化委托）。
 *
 * 聚合了十六个业务域，每个业务域一个契约接口（`XxxApi`）由对应聚合服务实现：
 *
 * | 契约接口 | 实现服务 | 职责 |
 * |---|---|---|
 * | [GachaApi] | GachaService | 抽卡、保底与 UP 定轨、抽卡历史 |
 * | [ProgressionApi] | ProgressionService | 升级/经验/突破/升星、天赋加点、角色好感度 |
 * | [TowerApi] | TowerService | 出战编队、无尽之塔结算、策略战斗、扫荡 |
 * | [EconomyApi] | EconomyService | 货币增减、商店购买 |
 * | [MetaApi] | MetaService | 设置、存档重置、战绩、每日商店、成就 |
 * | [ArenaApi] | ArenaService | PVP 竞技场结算 |
 * | [InspectionApi] | InspectionService | 角色检视、拍照记录 |
 * | [MonetizationApi] | MonetizationService | 月卡、通行证、充值档位 |
 * | [EventRhythmApi] | EventRhythmService | 活动运营（签到/任务/商店/代币） |
 * | [StoryApi] | StoryService | 剧情章节与关卡 |
 * | [DailyMissionApi] | DailyMissionService | 每日任务进度上报 |
 * | [DailyCheckInApi] | DailyCheckInService | 每日签到连续奖励 |
 * | [SeasonApi] | SeasonService | 赛季奖励 |
 * | [DungeonApi] | DungeonService | 日常副本 + 深渊 |
 * | [EquipmentApi] | EquipmentService | 装备穿戴/强化/分解 |
 * | [CollectionApi] | CollectionService | 图鉴收集 |
 *
 * **P1-5c 委托化**：除下列成员外，全部公开 API 通过 Kotlin `by` 委托直接落到聚合服务
 * （本类不再手工转发），UI 调用点零改动（`GameState.service.xxx` 仍有效）。本类仅保留：
 * 1. 构造 [ServiceCore]（共享状态：存档、写锁、快照、领域引擎）与聚合服务（构造参数注入，默认自建）；
 * 2. 状态/内容/公式直连内核的只读访问（快照流、切片、角色/卡池/天赋树内容、存档、公式）；
 * 3. **跨系统进度联动编排**（[onProgress]）——门面唯一的「编排」职责：
 *    `pull` / `levelUp` / `runTowerFloor` / `completeStoryStage` / `challengeOpponent` 五个写入口
 *    成功后统一上报每日任务进度 + 通行证经验，故必须显式 `override`（类内成员优先于委托）。
 *
 * 十六个聚合服务之间**互不调用**（已验证：所有方法只依赖 [ServiceCore]），
 * 因此不存在 [writeMutex] 重入问题——Mutex 不可重入，跨服务加锁会直接死锁。
 *
 * 约束（AGENTS.md 红线，拆分后保持不变）：
 * - 领域计算一律委托 `EconomyFormulas` / `AffinityFormulas` / `PityCounter` / `TalentEngine`，禁止就地写数字；
 * - 写操作事务范式：预算校验 → 改内存 → 落盘 → 失败回滚 → 仅成功广播；
 * - 内容数据 data.json 优先，失败回退 [GameContent] 兜底，两条路径都经 `GameContent.enrich`。
 */
@Suppress("DEPRECATION")
class GameService constructor(
    saveProvider: SaveProvider,
    contentJson: String? = null,
    private val onTrace: (String) -> Unit = {},
    private val rng: Random = Random.Default,
    /** UTC 日序号提供器（每日商店按天重置；注入便于测试固定「今天」，默认系统时钟）。 */
    private val today: () -> Long = { System.currentTimeMillis() / 86_400_000L },
    private val core: ServiceCore = ServiceCore(
        saveManager = SaveManager(saveProvider, onTrace),
        gacha = GachaEngine(rng),
        talent = TalentEngine(),
        progression = ProgressionEngine(),
        rng = rng,
        onTrace = onTrace,
        today = today,
    ),
    private val gachaService: GachaService = GachaService(core),
    private val progressionService: ProgressionService = ProgressionService(core),
    private val towerService: TowerService = TowerService(core),
    private val economyService: EconomyService = EconomyService(core),
    private val metaService: MetaService = MetaService(core),
    private val arenaService: ArenaService = ArenaService(core, rng),
    private val inspectionService: InspectionService = InspectionService(core, rng),
    private val monetizationService: MonetizationService = MonetizationService(core, rng),
    private val eventRhythmService: EventRhythmService = EventRhythmService(core, rng),
    private val storyService: StoryService = StoryService(core, rng),
    private val dailyMissionService: DailyMissionService = DailyMissionService(core),
    private val dailyCheckInService: DailyCheckInService = DailyCheckInService(core),
    private val seasonService: SeasonService = SeasonService(core),
    private val dungeonService: DungeonService = DungeonService(core, rng),
    private val equipmentService: EquipmentService = EquipmentService(core, rng),
    private val collectionService: CollectionService = CollectionService(core),
) : GachaApi by gachaService,
    EconomyApi by economyService,
    ProgressionApi by progressionService,
    TowerApi by towerService,
    MetaApi by metaService,
    ArenaApi by arenaService,
    InspectionApi by inspectionService,
    MonetizationApi by monetizationService,
    EventRhythmApi by eventRhythmService,
    StoryApi by storyService,
    DailyMissionApi by dailyMissionService,
    DailyCheckInApi by dailyCheckInService,
    SeasonApi by seasonService,
    DungeonApi by dungeonService,
    EquipmentApi by equipmentService,
    CollectionApi by collectionService {

    /**
     * 深渊策略战斗初始化（2026-09-12）：敌队走 [EconomyFormulas.floorEnemyStatScale]
     * / [EconomyFormulas.floorEnemyCount] 深渊档；塔层仍用 [initializeStrategicBattle]。
     */
    fun initializeAbyssStrategicBattle(floor: Int): com.milan.game.domain.battle.BattleState =
        towerService.initializeFloorBattle(floor, isAbyss = true)

    init {
        loadContent(contentJson)
        // 初始快照：载入存档后立即发布一次真实值，避免 UI 在首次写操作前看到全 0
        core.refreshSnapshot()
    }

    // ─────────────────────────── 跨系统进度联动（门面唯一编排职责）───────────────────────────

    /**
     * 领域操作成功后上报每日任务进度 + 通行证经验（P2-10：改用 [ProgressEvent] 类型安全事件）。
     *
     * 门面不含领域业务规则（规则在聚合服务/领域层），此处只是把「某领域操作成功」
     * 翻译成「进度系统的一次上报」——这是门面唯一的编排职责。
     */
    private suspend fun onProgress(event: ProgressEvent) {
        dailyMissionService.reportDailyMissionProgress(event.missionType, event.amount)
        if (event.battlePassExp > 0) {
            monetizationService.grantBattlePassExpInternal(event.battlePassExp, broadcast = false)
        }
    }

    // ─────────────────────────── 状态与内容（直连内核）───────────────────────────

    /** UI 订阅的状态快照（revision 为重组触发器）。 */
    val snapshot: StateFlow<GameSnapshot> get() = core.snapshot

    // ── 快照切片（2026-09-08 P0-4）──
    // 与全量 snapshot 并存：Screen 按关注点订阅，**内容未变则不发射**，
    // 避免「改了个开关导致货币 UI 重组」这类无谓重算。全部 Screen 迁完前勿删全量 snapshot。
    val economy: StateFlow<EconomySlice> get() = core.economy
    val roster: StateFlow<RosterSlice> get() = core.roster
    val progressSlice: StateFlow<ProgressSlice> get() = core.progress
    val gachaSlice: StateFlow<GachaSlice> get() = core.gachaSlice
    val meta: StateFlow<MetaSlice> get() = core.meta

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

    // ─────────────────────────── 编排入口（委托之上的显式 override）───────────────────────────

    /** 抽卡（单抽 / 十连），整体事务。成功后上报每日任务进度 + 通行证经验。 */
    override suspend fun pull(poolId: String, tenPull: Boolean): PullOutcome {
        val result = gachaService.pull(poolId, tenPull)
        if (result is PullOutcome.Success) {
            onProgress(ProgressEvent.GachaPull(result.results.size))
            // 新手引导：首次成功抽卡自动勾完成（幂等，旧档/已完成档无副作用）
            metaService.completeTutorialStep(com.milan.game.data.TutorialSteps.FIRST_PULL)
        }
        return result
    }

    /** 升级 n 级（默认 1）。成功后上报每日任务进度 + 通行证经验。 */
    override suspend fun levelUp(charId: String, n: Int): WriteOutcome {
        val result = progressionService.levelUp(charId, n)
        if (result == WriteOutcome.Success) {
            onProgress(ProgressEvent.LevelUp(n))
            metaService.completeTutorialStep(com.milan.game.data.TutorialSteps.FIRST_LEVEL)
        }
        return result
    }

    /** 挑战无尽之塔第 [floor] 层。成功后上报每日任务进度 + 通行证经验。 */
    override suspend fun runTowerFloor(floor: Int): TowerOutcome {
        val result = towerService.runTowerFloor(floor)
        if (result is TowerOutcome.Completed) {
            onProgress(ProgressEvent.TowerBattle)
            if (result.victory) {
                metaService.completeTutorialStep(com.milan.game.data.TutorialSteps.FIRST_BATTLE)
            }
        }
        return result
    }

    /** 挑战对手（PVP 竞技场）。成功后上报每日任务进度。 */
    override suspend fun challengeOpponent(opponent: com.milan.game.data.ArenaOpponent): ArenaChallengeOutcome {
        val result = arenaService.challengeOpponent(opponent)
        if (result is ArenaChallengeOutcome.Completed) {
            onProgress(ProgressEvent.ArenaChallenge)
        }
        return result
    }

    /** 完成剧情关卡（发放奖励）。成功后上报每日任务进度 + 通行证经验。 */
    override suspend fun completeStoryStage(stageId: String): WriteOutcome {
        val result = storyService.completeStoryStage(stageId)
        if (result == WriteOutcome.Success) {
            onProgress(ProgressEvent.StoryComplete)
        }
        return result
    }

/** 设置编队。成功非空编队时勾新手引导「组队」步。 */
    override suspend fun setFormation(characterIds: List<String>): WriteOutcome {
        val result = towerService.setFormation(characterIds)
        if (result == WriteOutcome.Success && characterIds.any { it.isNotBlank() }) {
            metaService.completeTutorialStep(com.milan.game.data.TutorialSteps.FORM_TEAM)
        }
        return result
    }

    /** 切换单位上/下阵。成功且编队非空时勾「组队」步。 */
    override suspend fun toggleFormation(characterId: String): WriteOutcome {
        val result = towerService.toggleFormation(characterId)
        if (result == WriteOutcome.Success && core.saveData.getFormationIds().isNotEmpty()) {
            metaService.completeTutorialStep(com.milan.game.data.TutorialSteps.FORM_TEAM)
        }
        return result
    }

    /** 策略战斗结算。胜利时勾新手引导「首战」步（与自动爬塔口径一致）。 */
    override suspend fun settleStrategicBattle(
        floor: Int,
        victory: Boolean,
        turns: Int,
        battleLog: List<com.milan.game.domain.battle.StrikeEvent>,
    ): TowerOutcome {
        val result = towerService.settleStrategicBattle(floor, victory, turns, battleLog)
        if (result is TowerOutcome.Completed && result.victory) {
            metaService.completeTutorialStep(com.milan.game.data.TutorialSteps.FIRST_BATTLE)
        }
        return result
    }

    /** 领取好感等级奖励。成功后上报 CLAIM_AFFINITY 每日任务进度。 */
    override suspend fun claimAffinityReward(characterId: String, level: Int): WriteOutcome {
        val result = progressionService.claimAffinityReward(characterId, level)
        if (result == WriteOutcome.Success) {
            onProgress(ProgressEvent.ClaimAffinityReward)
        }
        return result
    }

    companion object {
        const val StarFragmentItemId = ServiceCore.StarFragmentItemId

        /** 战票道具 id（无尽之塔门票）。 */
        const val BattleTicketItemId = ServiceCore.BattleTicketItemId
    }
}
