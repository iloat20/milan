package com.milan.game.services

import com.milan.game.data.BattleRecord
import com.milan.game.data.CharacterSaveState
import com.milan.game.data.EquipmentSaveState
import com.milan.game.data.SaveData
import com.milan.game.data.SaveManager
import com.milan.game.data.SaveProvider
import com.milan.game.data.StatValue
import com.milan.game.domain.gacha.GachaEngine
import com.milan.game.domain.progression.ProgressionEngine
import com.milan.game.domain.progression.TalentEngine
import com.milan.game.domain.progression.EconomyFormulas
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
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
 * 游戏服务**门面**（2026-08-28 P1 重构；2026-09-06 S2/S4 重组）。
 *
 * 原实现是 1385 行 / 46 个公开方法的上帝类，聚合了五个业务域。现已按**业务聚合**拆分为
 * **十一个聚合服务**（2026-09-06 S2 删除 PvE/Social/Equipment 三死功能层）：
 *
 * | 服务 | 职责 |
 * |---|---|
 * | [GachaService] | 抽卡、保底与 UP 定轨、抽卡历史 |
 * | [ProgressionService] | 升级/经验/突破/升星、天赋加点、角色好感度（S4 下沉） |
 * | [TowerService] | 出战编队、无尽之塔结算 |
 * | [EconomyService] | 货币增减、商店购买 |
 * | [MetaService] | 设置、存档重置、战绩、每日商店、成就 |
 * | [ArenaService] | PVP 竞技场结算 |
 * | [InspectionService] | 角色检视、拍照记录 |
 * | [MonetizationService] | 月卡、通行证、充值档位 |
 * | [EventRhythmService] | 活动运营（签到/任务/商店/代币） |
 * | [StoryService] | 剧情章节与关卡 |
 * | [DailyMissionService] | 每日任务进度上报 |
 *
 * 本类**不含任何领域业务规则**，只做四件事：
 * 1. 构造 [ServiceCore]（共享状态：存档、写锁、快照、领域引擎）与聚合服务；
 * 2. 转发全部公开 API——**UI 调用点零改动**，拆分对上层透明；
 * 3. 承载跨服务的公共契约说明；
 * 4. 跨系统进度联动编排（[onProgress]）：领域操作成功后统一上报每日任务进度 + 通行证经验
 *    ——这是门面唯一的「编排」职责，领域规则仍在聚合服务/领域层。
 *
 * 十一个聚合服务之间**互不调用**（已验证：所有方法只依赖 [ServiceCore]），
 * 因此不存在 [writeMutex] 重入问题——Mutex 不可重入，跨服务加锁会直接死锁。
 *
 * 约束（AGENTS.md 红线，拆分后保持不变）：
 * - 领域计算一律委托 `EconomyFormulas` / `AffinityFormulas` / `PityCounter` / `TalentEngine`，禁止就地写数字；
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
    private val arenaService = ArenaService(core, rng)
    private val inspectionService = InspectionService(core, rng)
    private val monetizationService = MonetizationService(core, rng)
    private val eventRhythmService = EventRhythmService(core, rng)
    private val storyService = StoryService(core, rng)
    private val dailyMissionService = DailyMissionService(core)

    init {
        loadContent(contentJson)
        // 初始快照：载入存档后立即发布一次真实值，避免 UI 在首次写操作前看到全 0
        core.refreshSnapshot()
    }

    // ─────────────────────────── 跨系统进度联动（门面唯一编排职责）───────────────────────────

    /**
     * 领域操作成功后上报每日任务进度 + 通行证经验（M2：收敛门面 6 处重复 if 判断）。
     *
     * 门面不含领域业务规则（规则在聚合服务/领域层），此处只是把「某领域操作成功」
     * 翻译成「进度系统的一次上报」——这是门面唯一的编排职责。
     *
     * @param type 每日任务类型
     * @param amount 进度增量（抽卡传抽数、升级传级数，其余默认 1）
     * @param battlePassExp 通行证经验（0 = 不发放；父操作副作用传 BP_DAILY_TASK_EXP）
     */
    private suspend fun onProgress(
        type: com.milan.game.data.DailyMissionType,
        amount: Int = 1,
        battlePassExp: Int = 0,
    ) {
        dailyMissionService.reportProgress(type, amount)
        if (battlePassExp > 0) {
            monetizationService.grantBattlePassExp(battlePassExp, broadcast = false)
        }
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

    /** 抽卡（单抽 / 十连），整体事务。成功后上报每日任务进度 + 通行证经验。 */
    suspend fun pull(poolId: String, tenPull: Boolean): PullOutcome {
        val result = gachaService.pull(poolId, tenPull)
        if (result is PullOutcome.Success) {
            onProgress(com.milan.game.data.DailyMissionType.PULL_GACHA, result.results.size, com.milan.game.data.MonetizationSaveData.BP_DAILY_TASK_EXP)
        }
        return result
    }

    // ─────────────────────────── 经济与商店 ───────────────────────────

    /** 扣除星尘（amount<=0 或余额不足拒绝）。 */
    suspend fun spendSoft(amount: Int): WriteOutcome = economyService.spendSoft(amount)

    /** 增加星尘（amount<=0 拒绝）。 */
    suspend fun grantSoft(amount: Int): WriteOutcome = economyService.grantSoft(amount)

    /** [grantSoft] 旧名兼容（2026-09-06 S6 渐进式收口，下次大版本删除）。 */
    @Deprecated("使用 grantSoft 代替", ReplaceWith("grantSoft(amount)"))
    suspend fun addSoft(amount: Int): WriteOutcome = grantSoft(amount)

    /** 扣除钻石（amount<=0 或余额不足拒绝）。 */
    suspend fun spendHard(amount: Int): WriteOutcome = economyService.spendHard(amount)

    /** 增加钻石（amount<=0 拒绝）。 */
    suspend fun grantHard(amount: Int): WriteOutcome = economyService.grantHard(amount)

    /** [grantHard] 旧名兼容（2026-09-06 S6 渐进式收口，下次大版本删除）。 */
    @Deprecated("使用 grantHard 代替", ReplaceWith("grantHard(amount)"))
    suspend fun addHard(amount: Int): WriteOutcome = grantHard(amount)

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

    /** 升级 n 级（默认 1）。成功后上报每日任务进度 + 通行证经验。 */
    suspend fun levelUp(charId: String, n: Int = 1): WriteOutcome {
        val result = progressionService.levelUp(charId, n)
        if (result == WriteOutcome.Success) {
            onProgress(com.milan.game.data.DailyMissionType.LEVEL_UP_CHARACTER, n, com.milan.game.data.MonetizationSaveData.BP_DAILY_TASK_EXP)
        }
        return result
    }

    /**
     * 增加经验。⚠️ 本方法自身持写锁——已在临界区内的调用方不可调用（Mutex 不可重入）。
     * @return 实际升的级数。
     */
    suspend fun grantExp(charId: String, amount: Int): Int = progressionService.grantExp(charId, amount)

    /** [grantExp] 旧名兼容（2026-09-06 S6 渐进式收口，下次大版本删除）。 */
    @Deprecated("使用 grantExp 代替", ReplaceWith("grantExp(charId, amount)"))
    suspend fun addExp(charId: String, amount: Int): Int = grantExp(charId, amount)

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

    /** 挑战无尽之塔第 [floor] 层。成功后上报每日任务进度 + 通行证经验。 */
    suspend fun runTowerFloor(floor: Int): TowerOutcome {
        val result = towerService.runTowerFloor(floor)
        if (result is TowerOutcome.Completed) {
            onProgress(com.milan.game.data.DailyMissionType.BATTLE_TOWER, battlePassExp = com.milan.game.data.MonetizationSaveData.BP_DAILY_TASK_EXP)
        }
        return result
    }

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

    // ─────────────────────────── 装备系统（已删除 2026-09-06 S2）───────────────────────────
    // EquipmentService 已随死功能裁撤一并删除。ServiceCore 的 calculateEquipmentStats /
    // calculateSetBonuses / unitStatsFor 保留——Arena/Tower 战斗系统依赖装备属性计算。
    // SaveData.ownedEquipments 字段保留（序列化兼容）。如需重新接线装备系统见
    // docs/plans/2026-09-04-dead-feature-wiring-design.md。

    // ─────────────────────────── 策略战斗系统 ───────────────────────────
    
    /** 初始化策略战斗状态。 */
    fun initializeStrategicBattle(floor: Int): com.milan.game.domain.battle.BattleState = 
        towerService.initializeStrategicBattle(floor)
    
    /** 执行玩家行动（策略战斗）。 */
    fun executeStrategicAction(
        state: com.milan.game.domain.battle.BattleState,
        action: com.milan.game.domain.battle.PlayerAction,
    ): com.milan.game.domain.battle.BattleState = 
        towerService.executeStrategicAction(state, action)
    
    /** 执行敌方回合（策略战斗）。 */
    fun executeStrategicEnemyTurn(
        state: com.milan.game.domain.battle.BattleState,
    ): com.milan.game.domain.battle.BattleState = 
        towerService.executeStrategicEnemyTurn(state)
    
    /** 更新回合状态（策略战斗）。 */
    fun updateStrategicTurnState(
        state: com.milan.game.domain.battle.BattleState,
    ): com.milan.game.domain.battle.BattleState = 
        towerService.updateStrategicTurnState(state)
    
    /** 检查战斗结果（策略战斗）。 */
    fun checkStrategicBattleResult(
        state: com.milan.game.domain.battle.BattleState,
    ): com.milan.game.domain.battle.BattlePhase = 
        towerService.checkStrategicBattleResult(state)
    
    /** 获取可用的玩家行动（策略战斗）。 */
    fun getStrategicAvailableActions(
        state: com.milan.game.domain.battle.BattleState,
        actorIndex: Int,
    ): List<com.milan.game.domain.battle.PlayerAction> = 
        towerService.getStrategicAvailableActions(state, actorIndex)
    
    /** 结算策略战斗奖励。 */
    suspend fun settleStrategicBattle(
        floor: Int,
        victory: Boolean,
        turns: Int,
    ): TowerOutcome = towerService.settleStrategicBattle(floor, victory, turns)
    
    // ─────────────────────────── PVP竞技场系统 ───────────────────────────
    
    /** 获取竞技场数据。 */
    fun getArenaData(): com.milan.game.data.ArenaSaveData = arenaService.getArenaData()
    
    /** 设置防守阵容。 */
    suspend fun setDefenseTeam(characterIds: List<String>): WriteOutcome = 
        arenaService.setDefenseTeam(characterIds)
    
    /** 获取可挑战的对手列表。 */
    fun getOpponents(): List<com.milan.game.data.ArenaOpponent> = arenaService.getOpponents()
    
    /** 挑战对手。成功后上报每日任务进度。 */
    suspend fun challengeOpponent(opponent: com.milan.game.data.ArenaOpponent): WriteOutcome {
        val result = arenaService.challengeOpponent(opponent)
        if (result == WriteOutcome.Success) {
            onProgress(com.milan.game.data.DailyMissionType.CHALLENGE_ARENA)
        }
        return result
    }
    
    /** 获取竞技场排名。 */
    fun getArenaRank(): Pair<Int, String> = arenaService.getArenaRank()
    
    /** 获取赛季奖励。 */
    fun getSeasonRewards(): List<com.milan.game.data.SeasonReward> = arenaService.getSeasonRewards()

    // ─────────────────────────── PVE 内容系统（已删除 2026-09-06 S2）───────────────────────────
    // PvEService（深渊 + 日常副本）已随死功能裁撤一并删除。SaveData.abyssData /
    // dailyDungeonData 字段保留（序列化兼容）。如需重新接线见
    // docs/plans/2026-09-04-dead-feature-wiring-design.md。

    // ─────────────────────────── 360°检视系统增强 ───────────────────────────
    
    /** 获取检视数据。 */
    fun getInspectionData(): com.milan.game.data.InspectionSaveData = inspectionService.getInspectionData()
    
    /** 记录角色检视。 */
    suspend fun recordInspection(characterId: String): WriteOutcome = 
        inspectionService.recordInspection(characterId)
    
    /** 获取角色检视次数。 */
    fun getInspectionCount(characterId: String): Int = inspectionService.getInspectionCount(characterId)
    
    /** 获取角色可用的互动动作。 */
    fun getAvailableActions(characterId: String): List<com.milan.game.data.CharacterAction> = 
        inspectionService.getAvailableActions(characterId)
    
    /** 保存拍照记录。 */
    suspend fun savePhoto(record: com.milan.game.data.PhotoRecord): WriteOutcome = 
        inspectionService.savePhoto(record)
    
    /** 获取拍照姿势列表。 */
    fun getPoses(): List<com.milan.game.data.PhotoPose> = inspectionService.getPoses()
    
    /** 获取拍照背景列表。 */
    fun getBackgrounds(): List<com.milan.game.data.PhotoBackground> = inspectionService.getBackgrounds()
    
    /** 获取拍照滤镜列表。 */
    fun getFilters(): List<com.milan.game.data.PhotoFilter> = inspectionService.getFilters()
    
    /** 检查是否触发隐藏互动。 */
    fun checkHiddenInteraction(characterId: String): Boolean = 
        inspectionService.checkHiddenInteraction(characterId)
    
    /** 解锁特殊动作。 */
    suspend fun unlockAction(actionId: String): WriteOutcome =
        inspectionService.unlockAction(actionId)

    // ─────────────────────────── 社交系统（已删除 2026-09-06 S2）───────────────────────────
    // SocialService（好友 + 公会）已随死功能裁撤一并删除。SaveData.socialData 字段保留
    // （序列化兼容）。如需重新接线见 docs/plans/2026-09-04-dead-feature-wiring-design.md。

    // ─────────────────────────── 变现模型 ───────────────────────────
    
    /** 获取变现数据。 */
    fun getMonetizationData(): com.milan.game.data.MonetizationSaveData = monetizationService.getData()
    
    /** 购买月卡。 */
    suspend fun activateMonthlyCard(cost: Int): WriteOutcome = 
        monetizationService.activateMonthlyCard(cost)
    
    /** 领取月卡每日奖励。 */
    suspend fun claimMonthlyCardReward(): WriteOutcome = 
        monetizationService.claimMonthlyCardReward()
    
    /** 月卡剩余天数。 */
    fun getMonthlyCardDaysLeft(): Int = monetizationService.getMonthlyCardDaysLeft()
    
    /** 购买豪华通行证。 */
    suspend fun purchaseBattlePass(cost: Int): WriteOutcome = 
        monetizationService.purchaseBattlePass(cost)
    
    /** 增加通行证经验。 */
    suspend fun grantBattlePassExp(amount: Int): WriteOutcome =
        monetizationService.grantBattlePassExp(amount, broadcast = true)

    /** [grantBattlePassExp] 旧名兼容（2026-09-06 S6 渐进式收口，下次大版本删除）。 */
    @Deprecated("使用 grantBattlePassExp 代替", ReplaceWith("grantBattlePassExp(amount)"))
    suspend fun addBattlePassExp(amount: Int): WriteOutcome = grantBattlePassExp(amount)
    
    /** 领取通行证等级奖励。 */
    suspend fun claimBattlePassReward(level: Int): WriteOutcome = 
        monetizationService.claimBattlePassReward(level)
    
    /** 获取通行证定义奖励列表。 */
    fun getBattlePassRewards(): List<com.milan.game.data.BattlePassReward> = 
        monetizationService.getBattlePassRewards()
    
    /** 充值。 */
    suspend fun charge(tierId: String, hardCurrency: Int, costCents: Int): WriteOutcome = 
        monetizationService.charge(tierId, hardCurrency, costCents)
    
    /** 领取累计充值里程碑奖励。 */
    suspend fun claimChargeMilestone(amountCents: Int): WriteOutcome = 
        monetizationService.claimChargeMilestone(amountCents)
    
    /** 获取充值档位列表。 */
    fun getChargeTiers(): List<com.milan.game.data.ChargeTier> = 
        monetizationService.getChargeTiers()
    
    /** 获取累计充值里程碑。 */
    fun getChargeMilestones(): List<com.milan.game.data.ChargeMilestone> = 
        monetizationService.getChargeMilestones()
    
    // ─────────────────────────── 活动运营 ───────────────────────────
    
    /** 获取活动数据。 */
    fun getEventRhythmData(): com.milan.game.data.EventRhythmSaveData = eventRhythmService.getData()
    
    /** 获取当前活跃活动列表。 */
    fun getActiveEvents(): List<com.milan.game.data.GameEvent> = eventRhythmService.getActiveEvents()
    
    /** 获取指定类型的活动。 */
    fun getEventsByType(type: com.milan.game.data.EventType): List<com.milan.game.data.GameEvent> = 
        eventRhythmService.getEventsByType(type)
    
    /** 检查活动是否在进行中。 */
    fun isEventActive(eventId: String): Boolean = eventRhythmService.isEventActive(eventId)
    
    /** 更新任务进度。 */
    suspend fun updateEventTaskProgress(eventId: String, taskId: String, progress: Int): WriteOutcome = 
        eventRhythmService.updateTaskProgress(eventId, taskId, progress)
    
    /** 领取任务奖励。 */
    suspend fun claimEventTaskReward(eventId: String, taskId: String): WriteOutcome = 
        eventRhythmService.claimTaskReward(eventId, taskId)
    
    /** 兑换活动商店物品。 */
    suspend fun redeemEventShopItem(eventId: String, itemId: String, amount: Int = 1): WriteOutcome = 
        eventRhythmService.redeemShopItem(eventId, itemId, amount)
    
    /** 获取活动商店物品列表。 */
    fun getEventShopItems(eventId: String): List<com.milan.game.data.EventShopItem> = 
        eventRhythmService.getShopItems(eventId)
    
    /** 签到。 */
    suspend fun signIn(eventId: String): WriteOutcome = eventRhythmService.signIn(eventId)
    
    /** 获取签到进度。 */
    fun getSignInProgress(eventId: String): Int = eventRhythmService.getSignInProgress(eventId)
    
    /** 获取默认活动定义列表。 */
    fun getDefaultEventDefinitions(): List<com.milan.game.data.EventDefinition> = 
        eventRhythmService.getDefaultEventDefinitions()

    /**
     * 确保已有活跃活动（R5-C2）：把默认活动模板实例化并事务化落盘。
     *
     * **所有活动 UI 必须在进入页面时先调用本方法**（`LaunchedEffect(Unit)`），
     * 否则活跃活动列表恒为空、签到/任务领取/商店兑换四个写操作恒 Rejected
     * （这正是 R5-C1~C3 经济漏洞长期休眠的根因）。幂等，重复调用安全。
     */
    suspend fun ensureActiveEvents(): WriteOutcome = eventRhythmService.ensureActiveEvents()

    /** 读指定活动代币余额（R5-C1）。未知类型或从未入账按 0 处理。 */
    fun getEventCurrencyBalance(currencyType: String): Int =
        eventRhythmService.getEventCurrencyBalance(currencyType)

    // ─────────────────────────── 剧情系统 ───────────────────────────

    /** 获取剧情存档数据。 */
    fun getStoryData(): com.milan.game.data.StorySaveData = storyService.getData()

    /** 获取所有剧情章节定义。 */
    fun getStoryChapters(): List<com.milan.game.data.StoryChapterDef> = storyService.getChapters()

    /** 获取指定章节定义。 */
    fun getStoryChapter(chapterId: String): com.milan.game.data.StoryChapterDef? = storyService.getChapter(chapterId)

    /** 检查剧情章节是否已解锁。 */
    fun isStoryChapterUnlocked(chapterId: String): Boolean = storyService.isChapterUnlocked(chapterId)

    /** 获取剧情章节完成进度。 */
    fun getStoryChapterProgress(chapterId: String): Float = storyService.getChapterProgress(chapterId)

    /** 检查剧情关卡是否已完成。 */
    fun isStoryStageCompleted(stageId: String): Boolean = storyService.isStageCompleted(stageId)

    /** 检查剧情关卡是否可进入。 */
    fun canEnterStoryStage(stageId: String): Boolean = storyService.canEnterStage(stageId)

    /** 查找剧情关卡定义。 */
    fun findStoryStageDef(stageId: String): com.milan.game.data.StoryStageDef? = storyService.findStageDef(stageId)

    /** 完成剧情关卡（发放奖励）。成功后上报每日任务进度 + 通行证经验。 */
    suspend fun completeStoryStage(stageId: String): WriteOutcome {
        val result = storyService.completeStage(stageId)
        if (result == WriteOutcome.Success) {
            onProgress(com.milan.game.data.DailyMissionType.COMPLETE_STORY, battlePassExp = com.milan.game.data.MonetizationSaveData.BP_DAILY_TASK_EXP)
        }
        return result
    }

    /** 领取剧情关卡奖励。 */
    suspend fun claimStoryReward(stageId: String): WriteOutcome = storyService.claimReward(stageId)

    // ─────────────────────────── 每日任务 ───────────────────────────

    /** 获取每日任务数据。 */
    fun getDailyMissionData(): com.milan.game.data.DailyMissionSaveData = dailyMissionService.getData()

    /** 获取今日任务列表。 */
    fun getTodayMissions(): List<com.milan.game.services.DailyMissionStatus> = dailyMissionService.getTodayMissions()

    /** 领取活跃度宝箱。 */
    suspend fun claimActivityChest(milestone: Int): WriteOutcome = dailyMissionService.claimActivityChest(milestone)

    /** 获取活跃度宝箱状态。 */
    fun getChestStatuses(): List<com.milan.game.services.ChestStatus> = dailyMissionService.getChestStatuses()

    /** 确保每日任务已跨日重置（进入每日任务页时调用）。 */
    suspend fun ensureDailyMissionReset(): WriteOutcome = dailyMissionService.ensureTodayReset()

    /** 上报每日任务进度。 */
    suspend fun reportDailyMissionProgress(type: com.milan.game.data.DailyMissionType, amount: Int = 1) =
        dailyMissionService.reportProgress(type, amount)

    // ─────────────────────────── 角色好感度（2026-09-06 S4 自门面下沉到 ProgressionService）───────────────────────────
    // 原 addCharacterAffinity / giftAffinity / getCharacterAffinityData 内联实现违反
    // "门面不含领域规则"自述，已整体下沉到 [progressionService]（养成语义相关）。
    // 业务口径不变：满级 Rejected + 钳位兜底、星尘不足 Rejected、事务原子扣减+加好感。

    /** 获取角色好感度数据。 */
    fun getCharacterAffinityData(): Map<String, Int> = progressionService.getCharacterAffinityData()

    /** 增加角色好感度（剧情选择 / 其它无消耗产出用）。 */
    suspend fun grantAffinity(characterId: String, amount: Int): WriteOutcome =
        progressionService.grantAffinity(characterId, amount)

    /** [grantAffinity] 旧名兼容（2026-09-06 S6 渐进式收口，下次大版本删除）。 */
    @Deprecated("使用 grantAffinity 代替", ReplaceWith("grantAffinity(characterId, amount)"))
    suspend fun addCharacterAffinity(characterId: String, amount: Int): WriteOutcome =
        grantAffinity(characterId, amount)

    /**
     * 赠送礼物（好感度主动培养入口，100 星尘 → +200 好感）。
     * 事务内原子完成「扣星尘 + 加好感」；Rejected 语义：星尘不足 或 已满级。
     */
    suspend fun giftAffinity(characterId: String): WriteOutcome =
        progressionService.giftAffinity(characterId)

    companion object {
        const val StarFragmentItemId = ServiceCore.StarFragmentItemId

        /** 战票道具 id（无尽之塔门票）。 */
        const val BattleTicketItemId = ServiceCore.BattleTicketItemId
    }
}
