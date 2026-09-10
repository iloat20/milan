package com.milan.game.services

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.ItemSaveState
import com.milan.game.data.PullLogEntry
import com.milan.game.data.Rarity
import com.milan.game.domain.gacha.PityCounter
import com.milan.game.domain.progression.EconomyFormulas

/**
 * 抽卡聚合服务（2026-08-28 P1 重构：自 [GameService] 拆出）。
 *
 * 负责：单抽/十连、保底与 UP 定轨、产出计划与发货、抽卡历史。
 * 边界：**不碰**养成/爬塔/商店/成就——那些分别属于其他四个聚合服务。
 *
 * 事务范式（AGENTS.md 红线）：先算产出（不扣款）→ 确认有产出才扣款 + 落盘
 * → 落盘失败回滚扣款与发货 → 仅成功广播经济变动。
 *
 * 2026-09 抽卡体验优化：
 * - 十连保底 SR+（第10抽未出 SR+ 时强制升档为 SR）
 * - 元素 UP 池（FeaturedElement 非空时，UP 候选仅限指定元素角色）
 * - 保底可视化数据（PityStatus，供 UI 展示当前保底进度）
 */
class GachaService(private val core: ServiceCore) : GachaApi {

    private val saveData get() = core.saveData

    /** 重复角色按稀有度补偿的星魂碎片数量。公式在 [EconomyFormulas]（纯领域、可单测）。 */
    override fun fragmentsForRarity(rarity: Int): Int = EconomyFormulas.fragmentsForRarity(rarity)

    /** 抽卡历史快照（时间正序，最旧在前；UI 自行倒序展示）。读操作：列表引用替换式更新，无撕裂风险。 */
    override fun pullHistory(): List<PullLogEntry> = saveData.pullHistoryEntries()

    // ─────────────────────────── 保底可视化 ───────────────────────────

    /**
     * 获取指定池的保底进度数据，供 UI 展示。
     * - counter: 距上次出 SSR 的累计抽数
     * - hardPity: 硬保底阈值（counter >= 此值时必出 SSR）
     * - softPityStart: 软保底起始（硬保底前 30 抽开始概率递增）
     * - isFeaturedGuaranteed: UP 定轨是否已歪一次（下次命中最高稀有度必中 UP）
     */
    @Deprecated("P2-11: UI层零调用", level = DeprecationLevel.WARNING)
    override fun getPityStatus(poolId: String): PityStatus {
        val pool = core.pools.firstOrNull { it.poolId == poolId }
        if (pool == null) return PityStatus()
        val counter = saveData.getGachaCounter(poolId)
        val hardPity = pool.hardPity
        val softPityStart = maxOf(0, hardPity - 30)
        return PityStatus(
            poolId = poolId,
            currentCount = counter,
            hardPity = hardPity,
            softPityStart = softPityStart,
            isFeaturedGuaranteed = saveData.isFeaturedGuaranteed(poolId),
            featuredCharacterName = pool.featuredCharacterId.let { id ->
                if (id.isEmpty()) null else core.character(id)?.displayName
            },
        )
    }

    /**
     * 抽卡（单抽 / 十连），整体事务：
     * 先算出全部产出（不扣款、不改存档）——任何配置错误只导致「少抽」，
     * 绝不「扣了钱没东西」（#8）；确认有产出后再扣款 + 落盘，落盘失败回滚本次
     * 扣款与发货（含碎片幻影处理），让玩家可重试（#5）。成功才广播经济变动。
     *
     * 2026-08：suspend + 串行写锁——落盘在 IO 线程执行，主线程不阻塞；
     * 回滚与落盘判定在同一临界区内同步完成，事务语义不变。
     *
     * 2026-09：十连保底 SR+——十连内若全部低于 SR（稀有度 < 2），最后一抽强制升档为 SR。
     * 元素 UP——池声明 FeaturedElement 时，UP 仅从该元素角色中选取。
     */
    override suspend fun pull(poolId: String, tenPull: Boolean): PullOutcome = core.withWriteLock {
        val results = mutableListOf<PullResult>()
        val pool = core.pools.firstOrNull { it.poolId == poolId } ?: return@withWriteLock PullOutcome.Rejected

        // 空卡池一张牌也抽不出来。必须在扣款【之前】拦截，否则玩家的星尘会被静默吞掉。
        if (pool.entries.isEmpty()) return@withWriteLock PullOutcome.Rejected

        val count = if (tenPull) 10 else 1
        val cost = if (tenPull) pool.tenCost else pool.singleCost
        if (saveData.softCurrency < cost) return@withWriteLock PullOutcome.Rejected

        // 先算产出（不扣款）：展示稀有度与补偿碎片统一用抽中角色的真实稀有度（#11）。
        val pity = PityCounter(pool.hardPity, core.gacha).apply { counter = saveData.getGachaCounter(poolId) }
        // UP 定轨（2026-08 三期）：池声明了 UP 角色时，命中池内最高稀有度走 50/50（歪一次必中）。
        // guaranteedNext 以存档标记为初值、随本批逐抽演进——十连内歪了再出最高稀有度同样吃必中。
        val topRarity = pool.entries.maxOfOrNull { it.rarityIndex } ?: 0
        val featuredId = pool.featuredCharacterId.ifEmpty { null }
        // 元素 UP：FeaturedElement 非空时，UP 候选池限定为该元素角色（从 entries 按元素过滤）
        val featuredElement = pool.featuredElement.ifEmpty { null }
        var guaranteedNext = saveData.isFeaturedGuaranteed(poolId)
        val plan = mutableListOf<PlanItem>()
        // 本批已确认的新角色集合：同一次十连内同一未拥有角色重复出现时，
        // 第二次起按「重复角色」补偿碎片、发货只追加一条拥有条目——
        // 否则十连内撞重复新角色会白丢碎片，且 ownedCharacters 写入重复条目
        // （下次载入被 sanitize 去重，碎片永久丢失；历史 C# 行为继承的缺陷）。
        val batchNew = mutableSetOf<String>()
        // 十连 SR+ 保底：记录本批是否有人 SR+（稀有度 >= 2）
        var hasSrOrAbove = false
        for (i in 0 until count) {
            // 掷出的稀有度段在本池可能没有候选角色（如 UP 池没有 R 角色）：
            // 就近向上升档（保证玩家不亏），全部向上无候选再向下回退。
            var rolledRarity = pity.rollWithPity(core.rng, pool.rarityWeights.toIntArray(), Rarity.SSR).value

            // 十连保底 SR+：第 10 抽（i==9）时若仍无 SR+，强制升档到 SR（rarity=2）
            val isTenPullLast = tenPull && i == 9
            if (isTenPullLast && !hasSrOrAbove) {
                rolledRarity = maxOf(rolledRarity, Rarity.SR.value)
            }

            val effectiveRarity = resolveRarityWithCandidates(pool, rolledRarity)
            // 跟踪本批是否已出 SR+（稀有度 >= 2）
            if (effectiveRarity >= Rarity.SR.value) hasSrOrAbove = true

            // P1-3：保底重置以「实际交付档位」判定——掷出保底档但该档无候选被降档时
            // 不重置计数，避免 90 抽保底被低稀有度产出吞掉。
            pity.onNaturalPityOrAbove(Rarity.fromValue(effectiveRarity) ?: Rarity.R, Rarity.SSR)
            val entries = pool.entries.filter { it.rarityIndex == effectiveRarity }
            var pickedId: String? = pickFromEntries(entries)
            if (effectiveRarity == topRarity && featuredId != null && entries.isNotEmpty()) {
                // 定轨掷选：命中 UP 或歪出其他候选；pickedId=null 表示池无有效 UP，回退普通加权抽取
                // 元素 UP 过滤：如果池指定了 FeaturedElement，只从该元素的角色中选 UP 候选
                val upEntries = if (featuredElement != null) {
                    entries.filter { e ->
                        core.character(e.characterId)?.element == featuredElement
                    }.ifEmpty { entries } // 若该元素无候选则回退全池
                } else entries
                val pick = core.gacha.pickFeatured(upEntries.map { it.characterId }, featuredId, guaranteedNext)
                if (pick.pickedId != null) {
                    pickedId = pick.pickedId
                    guaranteedNext = pick.guaranteedNext
                }
            }
            val id = pickedId
            if (id.isNullOrEmpty()) continue // 该稀有度无候选，跳过（不影响其它抽）

            val def = core.character(id)
            // 展示稀有度与补偿碎片必须口径一致：统一用抽中角色的真实稀有度（#11）。
            val rarity = def?.baseRarity ?: effectiveRarity
            val isNew = saveData.ownedCharacters.none { it?.characterId == id } && id !in batchNew
            if (isNew) batchNew += id
            val fragments = if (isNew) 0 else fragmentsForRarity(rarity)
            plan += PlanItem(id, def, isNew, fragments, rarity)
        }
        if (plan.isEmpty()) return@withWriteLock PullOutcome.Rejected // 没抽到任何东西，绝不扣款

        // 确认有产出后再扣款 + 落盘；落盘失败回滚本次扣款与发货（#5）。
        val originalCurrency = saveData.softCurrency
        val originalCounter = saveData.getGachaCounter(poolId)
        // 抽卡历史与定轨状态随事务落盘：整体替换式列表，回滚恢复原引用即可
        val originalPullHistory = saveData.pullHistory
        val originalFeaturedLost = saveData.gachaFeaturedLost
        // F3：累计抽数一同纳入事务（永不清零，成就「寻访百次」的唯一口径）
        val originalPullCount = saveData.totalPullCount
        // 碎片条目在本次抽卡前是否已存在：决定回滚时是「减回数量」还是「整条移除」，
        // 否则首次抽到重复角色且落盘失败，会在存档里留下一条数量为 0 的幽灵道具。
        val fragItemExisted = saveData.items.any { it?.itemId == ServiceCore.StarFragmentItemId }
        var fragDelta = 0
        // 本函数已持 writeMutex：用 transactionLocked（不重复加锁，Mutex 不可重入）
        val outcome = core.transactionLocked(
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
                    val item = saveData.items.firstOrNull { it?.itemId == ServiceCore.StarFragmentItemId }
                    if (item != null) item.count += fragDelta
                    else saveData.items = saveData.items + ItemSaveState(
                        itemId = ServiceCore.StarFragmentItemId,
                        count = fragDelta,
                    )
                }
                saveData.softCurrency -= cost
                saveData.setGachaCounter(poolId, pity.counter)
                // 定轨状态随事务落盘（本批逐抽演进后的终值）
                saveData.setFeaturedGuaranteed(poolId, guaranteedNext)
                // 抽卡历史：成功才留痕；时间戳仅展示用。
                val now = System.currentTimeMillis()
                for (r in results) {
                    saveData.appendPullHistory(
                        PullLogEntry(
                            poolId = poolId,
                            characterId = r.characterId.orEmpty(),
                            characterName = r.characterName,
                            rarity = r.rarity,
                            isNew = r.isNew,
                            fragmentsAwarded = r.fragmentsAwarded,
                            timestamp = now,
                        ),
                    )
                }
                // F3：累计抽数随出货同步累加（与实际发货条数一致，永不清零）
                saveData.totalPullCount += plan.size
            },
            rollback = {
                saveData.softCurrency = originalCurrency
                saveData.setGachaCounter(poolId, originalCounter)
                val newIds = plan.filter { it.isNew }.map { it.id }.toSet()
                // Set.contains 不接受可空参数：先判 null 再走 contains（Kotlin 类型系统要求）。
                saveData.ownedCharacters =
                    saveData.ownedCharacters.filterNot { s -> s != null && s.characterId in newIds }
                // 碎片补偿也必须回滚：只退钱不退货会让玩家「存档没变但碎片凭空多出来」（可无限刷）。
                if (fragDelta > 0) {
                    if (fragItemExisted) {
                        val frag = saveData.items.firstOrNull { it?.itemId == ServiceCore.StarFragmentItemId }
                        if (frag != null) frag.count = maxOf(0, frag.count - fragDelta)
                    } else {
                        saveData.items = saveData.items.filterNot { it?.itemId == ServiceCore.StarFragmentItemId }
                    }
                }
                // 历史与定轨标记同样回滚：只退钱不撤记录会让「历史页显示出货但角色没到账」
                saveData.pullHistory = originalPullHistory
                saveData.gachaFeaturedLost = originalFeaturedLost
                saveData.totalPullCount = originalPullCount
            },
            onCommit = { core.publishCurrencyChanged() },
        )
        // 扣费 + 落盘都成功才返回产出；回滚分支不会跑到这里（rollback 不广播）。
        return@withWriteLock when (outcome) {
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
        return core.gacha.pickWeighted(entries.map { it.characterId }, entries.map { it.weight })
    }

    /** 抽卡内部计划条目（对齐 C# 的 plan 元组）：先算产出、再统一扣款发货。 */
    private data class PlanItem(
        val id: String,
        val def: CharacterDataEntry?,
        val isNew: Boolean,
        val fragments: Int,
        val rarity: Int,
    )
}

/** 保底进度可视化数据（供 UI 展示）。 */
data class PityStatus(
    val poolId: String = "",
    val currentCount: Int = 0,
    val hardPity: Int = 90,
    val softPityStart: Int = 60,
    val isFeaturedGuaranteed: Boolean = false,
    val featuredCharacterName: String? = null,
) {
    /** 距硬保底还差几抽。 */
    val pullsToHardPity: Int get() = maxOf(0, hardPity - currentCount)
    /** 是否处于软保底区间（概率递增）。 */
    val inSoftPity: Boolean get() = currentCount >= softPityStart
}
