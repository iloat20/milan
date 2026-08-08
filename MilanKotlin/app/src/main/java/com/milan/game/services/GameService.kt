package com.milan.game.services

import com.milan.game.data.BattleRecord
import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.SaveData
import com.milan.game.data.SaveManager
import com.milan.game.data.SaveProvider
import com.milan.game.domain.gacha.GachaEngine
import com.milan.game.domain.gacha.PityCounter
import com.milan.game.domain.progression.EconomyFormulas
import com.milan.game.domain.progression.TalentEngine
import com.milan.game.infrastructure.eventbus.CurrencyChanged
import com.milan.game.infrastructure.eventbus.EventBus
import com.milan.game.infrastructure.eventbus.ProgressionChanged
import java.util.Random
import kotlinx.serialization.decodeFromString

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
    private val rng: Random = Random(),
) {
    private val saveManager = SaveManager(saveProvider, onTrace)
    private val gacha = GachaEngine(rng)
    private val talent = TalentEngine()

    /** prereqMap 结果缓存（treeId → map）；loadContent 时失效重建。 */
    private var prereqCache: MutableMap<String, Map<String, List<String>>>? = null
    private val pullLock = Any()

    /** 进程级存档引用（与 SaveManager.current 同一对象，写操作原地修改后 [save] 持久化）。 */
    val saveData: SaveData = saveManager.load()

    var characters: List<CharacterDataEntry> = emptyList()
        private set
    var pools: List<GachaPoolDataEntry> = emptyList()
        private set
    var talentTrees: List<TalentTreeData> = emptyList()
        private set

    init {
        loadContent(contentJson)
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
                            && p.rarityWeights.size >= 4
                            && p.rarityWeights.sum() > 0
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
     */
    fun pull(poolId: String, tenPull: Boolean): List<PullResult> = synchronized(pullLock) {
        val results = mutableListOf<PullResult>()
        val pool = pools.firstOrNull { it.poolId == poolId } ?: return@synchronized results

        // 空卡池一张牌也抽不出来。必须在扣款【之前】拦截，否则玩家的星尘会被静默吞掉。
        if (pool.entries.isEmpty()) return@synchronized results

        val count = if (tenPull) 10 else 1
        val cost = if (tenPull) pool.tenCost else pool.singleCost
        if (saveData.softCurrency < cost) return@synchronized results

        // 先算产出（不扣款）：展示稀有度与补偿碎片统一用抽中角色的真实稀有度（#11）。
        val pity = PityCounter(pool.hardPity).apply { counter = saveData.getGachaCounter(poolId) }
        val plan = mutableListOf<PlanItem>()
        for (i in 0 until count) {
            // 掷出的稀有度段在本池可能没有候选角色（如 UP 池没有 R 角色）：
            // 就近向上升档（保证玩家不亏），全部向上无候选再向下回退。
            val rolledRarity = pity.rollWithPity(rng, pool.rarityWeights.toIntArray(), 3).value
            val effectiveRarity = resolveRarityWithCandidates(pool, rolledRarity)
            val entries = pool.entries.filter { it.rarityIndex == effectiveRarity }
            val id = pickFromEntries(entries)
            if (id.isNullOrEmpty()) continue // 该稀有度无候选，跳过（不影响其它抽）

            val def = characters.firstOrNull { it.characterId == id }
            // 展示稀有度与补偿碎片必须口径一致：统一用抽中角色的真实稀有度（#11）。
            val rarity = def?.baseRarity ?: effectiveRarity
            val isNew = saveData.ownedCharacters.none { it?.characterId == id }
            val fragments = if (isNew) 0 else fragmentsForRarity(rarity)
            plan += PlanItem(id, def, isNew, fragments, rarity)
        }
        if (plan.isEmpty()) return@synchronized results // 没抽到任何东西，绝不扣款

        // 确认有产出后再扣款 + 落盘；落盘失败回滚本次扣款与发货（#5）。
        val originalCurrency = saveData.softCurrency
        val originalCounter = saveData.getGachaCounter(poolId)
        // 碎片条目在本次抽卡前是否已存在：决定回滚时是「减回数量」还是「整条移除」，
        // 否则首次抽到重复角色且落盘失败，会在存档里留下一条数量为 0 的幽灵道具。
        val fragItemExisted = saveData.items.any { it?.itemId == StarFragmentItemId }
        var fragDelta = 0
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
        if (!saveManager.save()) {
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
            onTrace("pull.save.failed: rolled back")
            return@synchronized emptyList()
        }
        // 扣费 + 落盘都成功后才广播经济变动；回滚分支不会跑到这里。
        publishCurrencyChanged()
        return@synchronized results
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
        EventBus.publish(CurrencyChanged)
        EventBus.dispatch()
    }

    // ── 货币增减 ──
    // 全部遵循与 Pull 一致的事务范式：先校验可负担 → 改内存 → 落盘 → 失败回滚 → 仅成功才广播。
    // 返回 false 表示未发生任何变更（余额不足或落盘失败），调用方应据此提示玩家。

    /** 扣除星尘。余额不足或落盘失败时不做任何变更并返回 false。 */
    fun spendSoft(amount: Int): Boolean = applyCurrencyDelta(-amount, 0)

    /** 增加星尘。落盘失败时回滚并返回 false。 */
    fun addSoft(amount: Int): Boolean = applyCurrencyDelta(amount, 0)

    /** 扣除钻石。余额不足或落盘失败时不做任何变更并返回 false。 */
    fun spendHard(amount: Int): Boolean = applyCurrencyDelta(0, -amount)

    /** 增加钻石。落盘失败时回滚并返回 false。 */
    fun addHard(amount: Int): Boolean = applyCurrencyDelta(0, amount)

    private fun applyCurrencyDelta(softDelta: Int, hardDelta: Int): Boolean {
        if (softDelta == 0 && hardDelta == 0) return false
        // 负余额是不可恢复的脏状态（UI 显示负数、所有可负担判定失效），必须前置拦截。
        if (saveData.softCurrency + softDelta < 0) return false
        if (saveData.hardCurrency + hardDelta < 0) return false

        val origSoft = saveData.softCurrency
        val origHard = saveData.hardCurrency
        saveData.softCurrency += softDelta
        saveData.hardCurrency += hardDelta
        if (!saveManager.save()) {
            saveData.softCurrency = origSoft
            saveData.hardCurrency = origHard
            return false
        }
        publishCurrencyChanged()
        return true
    }

    /** 立即落盘（设置项改 SaveData 字段后调用）。saveData 与 SaveManager.current 同一引用。 */
    fun save() {
        saveManager.save()
    }

    // ─────────────────────────────────────────────────────────── 战绩
    /** 读取战绩（最近在前）。空列表返回空实例，调用方无需判 null。 */
    fun getBattleRecords(): List<BattleRecord> = saveData.battleRecords.filterNotNull()

    /**
     * 追加一条战绩并落盘。落盘失败回滚本次追加（不广播事件，战绩非经济）。
     * 列表上限 50 条，超出丢弃最旧记录。
     */
    fun recordBattle(rec: BattleRecord?) {
        if (rec == null) return
        saveData.battleRecords = saveData.battleRecords + rec
        val maxRecords = 50
        if (saveData.battleRecords.size > maxRecords)
            saveData.battleRecords = saveData.battleRecords.drop(saveData.battleRecords.size - maxRecords)
        if (!saveManager.save())
            saveData.battleRecords = saveData.battleRecords.dropLast(1) // 回滚，避免「内存与存档不一致」
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

    /**
     * 升级 n 级（默认 1）。星尘不足或已达等级上限时尽可能少升；一级都升不了返回 false。
     * 每升 1 级 +1 天赋点。落盘失败回滚。
     */
    fun levelUp(charId: String, n: Int = 1): Boolean {
        val save = getSave(charId) ?: return false
        if (n <= 0) return false

        // 预算规划抽到纯领域（EconomyFormulas.planLevelUp），边界行为由单元测试锁死。
        val (gained, cost) = EconomyFormulas.planLevelUp(
            save.level, maxLevelForStage(save.stage), saveData.softCurrency, n,
        )
        if (gained <= 0) return false // 一级都升不了（资源不足 / 已满级）

        val target = save.level + gained
        val origSoft = saveData.softCurrency
        saveData.softCurrency -= cost
        save.level = target
        save.totalExp = EconomyFormulas.cumulativeExp(target) // 经验条跟随等级定位
        save.unspentPoints += gained
        if (!saveManager.save()) {
            saveData.softCurrency = origSoft
            save.level -= gained
            save.unspentPoints -= gained
            save.totalExp = EconomyFormulas.cumulativeExp(save.level)
            return false
        }
        publishCurrencyChanged()
        publishProgressionChanged()
        return true
    }

    /** 突破（Stage+1）。需未达 MaxStage 且星魂碎片 + 星尘充足。落盘失败回滚。 */
    fun ascend(charId: String): Boolean {
        val save = getSave(charId) ?: return false
        val def = characters.firstOrNull { it.characterId == charId } ?: return false
        if (save.stage >= def.maxStage) return false

        val frags = ascendFragments(save.stage)
        val soft = ascendSoft(save.stage)
        val have = getStarFragments()
        if (have < frags || saveData.softCurrency < soft) return false

        val origSoft = saveData.softCurrency
        val origFrags = have
        saveData.softCurrency -= soft
        val item = saveData.items.firstOrNull { it?.itemId == StarFragmentItemId }
        if (item != null) item.count -= frags
        save.stage += 1
        if (!saveManager.save()) {
            saveData.softCurrency = origSoft
            if (item != null) item.count = origFrags
            save.stage -= 1
            return false
        }
        publishCurrencyChanged()
        publishProgressionChanged()
        return true
    }

    /** 升星（Stars+1）。需未达 MaxStars 且星魂碎片充足。落盘失败回滚。每次仅 +1 星。 */
    fun starUp(charId: String): Boolean {
        val save = getSave(charId) ?: return false
        val def = characters.firstOrNull { it.characterId == charId } ?: return false
        if (save.stars >= def.maxStars) return false

        val cost = starUpFragments(save.stars)
        val have = getStarFragments()
        if (have < cost) return false

        val origFrags = have
        val item = saveData.items.firstOrNull { it?.itemId == StarFragmentItemId }
        if (item != null) item.count -= cost
        save.stars += 1
        if (!saveManager.save()) {
            if (item != null) item.count = origFrags
            save.stars -= 1
            return false
        }
        publishCurrencyChanged()
        publishProgressionChanged()
        return true
    }

    /** 取角色天赋树（含节点与前置关系）。无树返回 null。 */
    fun getTalentTree(charId: String): TalentTreeData? {
        val def = characters.firstOrNull { it.characterId == charId } ?: return null
        return talentTrees.firstOrNull { it.treeId == def.talentTreeId }
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
    private fun talentCheck(charId: String, nodeId: String): Triple<CharacterSaveState, TalentNodeData, TalentTreeData>? {
        val save = getSave(charId) ?: return null
        val tree = getTalentTree(charId) ?: return null
        val node = tree.nodes.firstOrNull { it.nodeId == nodeId } ?: return null
        if (save.talentPoints.contains(nodeId)) return null
        if (save.unspentPoints < node.cost) return null
        return Triple(save, node, tree)
    }

    /** 不落盘地预判某天赋节点当前是否可点亮（用于 UI 三态与按钮可用性）。 */
    fun canAllocateTalent(charId: String, nodeId: String): Boolean {
        // C# 在渲染路径上曾有 "TalentPoints": null 覆盖字段初始化器的 NRE（逐节点调用直接闪退），
        // 用 ??= 兜底；Kotlin 类型系统 + coerceInputValues 保证 talentPoints 非空，天然免疫。
        val (save, _, tree) = talentCheck(charId, nodeId) ?: return false
        return talent.canAllocate(nodeId, save.talentPoints.filterNotNull(), prereqMap(tree))
    }

    /** 点亮天赋节点：校验前置（TalentEngine）与天赋点余额，扣点并落盘。
     * 已点过 / 点不够 / 前置未满足 / 落盘失败均返回 false。 */
    fun allocateTalent(charId: String, nodeId: String): Boolean {
        val (save, node, tree) = talentCheck(charId, nodeId) ?: return false
        if (!talent.canAllocate(nodeId, save.talentPoints.filterNotNull(), prereqMap(tree))) return false

        val origPoints = save.unspentPoints
        save.unspentPoints -= node.cost
        save.talentPoints = save.talentPoints + nodeId
        if (!saveManager.save()) {
            save.unspentPoints = origPoints
            save.talentPoints = save.talentPoints.filterNot { it == nodeId }
            return false
        }
        publishProgressionChanged()
        return true
    }

    // ── 养成变动统一出口（EventBus 接线点）──
    // 事件是无载荷标记（订阅方自行重读当前角色），不带 charId 参数，
    // 避免「看起来会按角色过滤、实际全量广播」的误导性签名。
    private fun publishProgressionChanged() {
        EventBus.publish(ProgressionChanged)
        EventBus.dispatch()
    }

    companion object {
        const val StarFragmentItemId = "item_star_fragment"
    }

    /** Pull 内部计划条目（对齐 C# 的 plan 元组）。 */
    private data class PlanItem(
        val id: String,
        val def: CharacterDataEntry?,
        val isNew: Boolean,
        val fragments: Int,
        val rarity: Int,
    )
}
