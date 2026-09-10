package com.milan.game.services

import com.milan.game.data.*

/**
 * 图鉴收集系统服务。
 *
 * 职责：
 * - 角色图鉴（首次获得解锁，含属性加成）
 * - 收集进度追踪
 * - 收集里程碑奖励（永久属性加成）
 * - 图鉴查询（角色详情/收集率/成就联动）
 */
class CollectionService(
    private val core: ServiceCore,
) : CollectionApi {

    private val saveData get() = core.saveData

    /**
     * 角色是否已解锁图鉴。
     *
     * **2026-09-08 概念收敛（方案 B）**：解锁态改由「是否拥有」派生（`ownedCharacters`
     * 含该角色存档即视为解锁）——`unlockedCharacters` 旧字段的唯一写入方 `recordCharacterObtained`
     * 早已无人调用（GachaService 从未接线），读路径恒空导致收集率/里程碑永不推进。
     * 拥有即首获（重复抽卡不新增存档，`ownedCharacters` 按 charId 唯一），语义等价。
     */
    fun isCharacterUnlocked(characterId: String): Boolean =
        saveData.ownedCharacters.any { it?.characterId == characterId }

    /** 获取已解锁角色数（契约名 [CollectionApi.getCollectionUnlockedCount]）。 */
    override fun getCollectionUnlockedCount(): Int = saveData.ownedCharacters.count { it != null }

    /** 图鉴存档（仅里程碑领取态 [CollectionSaveData.claimedMilestones] 仍读写此数据）。 */
    private fun getData(): CollectionSaveData =
        saveData.collectionData ?: CollectionSaveData()

    /** 获取全部角色总数（从内容定义推导）。 */
    fun getTotalCount(): Int = core.characters.size

    /** 收集完成率（0.0~1.0）。 */
    override fun getCollectionProgress(): Float {
        val total = getTotalCount()
        if (total == 0) return 0f
        return getCollectionUnlockedCount().toFloat() / total
    }

    /** 获取收集里程碑状态。 */
    override fun getCollectionMilestones(): List<CollectionMilestoneStatus> {
        val data = getData()
        val unlocked = getCollectionUnlockedCount()
        return COLLECTION_MILESTONES.map { milestone ->
            CollectionMilestoneStatus(
                required = milestone.required,
                softBonus = milestone.softBonus,
                unlocked = unlocked >= milestone.required,
                claimed = data.claimedMilestones.contains(milestone.required),
            )
        }
    }

    /**
     * 领取收集里程碑奖励（契约名 [CollectionApi.claimCollectionMilestone]）。
     */
    override suspend fun claimCollectionMilestone(required: Int): WriteOutcome {
        val data = getData()
        if (data.claimedMilestones.contains(required)) return WriteOutcome.Rejected
        if (getCollectionUnlockedCount() < required) return WriteOutcome.Rejected

        val origClaimed = data.claimedMilestones.toList()
        val origSC = saveData.softCurrency

        return core.transaction(
            tag = "collection.claimMilestone",
            mutate = {
                data.claimedMilestones = data.claimedMilestones + required
                val milestone = COLLECTION_MILESTONES.firstOrNull { it.required == required }
                if (milestone != null && milestone.softBonus > 0) {
                    core.addCurrencyDelta(milestone.softBonus, 0)
                }
            },
            rollback = {
                data.claimedMilestones = origClaimed
                saveData.softCurrency = origSC
            },
            onCommit = { core.publishCurrencyChanged() },
        )
    }

    /**
     * 获取角色图鉴详情。
     */
    fun getCharacterEntry(characterId: String): CollectionCharacterEntry? {
        val def = core.character(characterId) ?: return null
        val unlocked = isCharacterUnlocked(characterId)
        val save = saveData.ownedCharacters.firstOrNull { it?.characterId == characterId }

        return CollectionCharacterEntry(
            characterId = characterId,
            displayName = def.displayName,
            rarity = def.baseRarity,
            element = def.element,
            world = def.world,
            unlocked = unlocked,
            owned = save != null,
            level = save?.level ?: 0,
            stage = save?.stage ?: 0,
        )
    }

    /** 获取全部图鉴条目（契约名 [CollectionApi.getCollectionEntries]）。 */
    override fun getCollectionEntries(): List<CollectionCharacterEntry> =
        core.characters.mapNotNull { getCharacterEntry(it.characterId) }

    companion object {
        /** 收集里程碑（解锁 N 个角色 → 永久星尘加成）。 */
        val COLLECTION_MILESTONES = listOf(
            CollectionMilestone(5, 2000),
            CollectionMilestone(10, 5000),
            CollectionMilestone(15, 10000),
            CollectionMilestone(20, 20000),
            CollectionMilestone(25, 30000),
        )
    }
}

// ── 数据模型 ──

data class CollectionMilestone(val required: Int, val softBonus: Int)

data class CollectionMilestoneStatus(
    val required: Int,
    val softBonus: Int,
    val unlocked: Boolean,
    val claimed: Boolean,
)

data class CollectionCharacterEntry(
    val characterId: String,
    val displayName: String,
    val rarity: Int,
    val element: String,
    val world: String,
    val unlocked: Boolean,
    val owned: Boolean,
    val level: Int,
    val stage: Int,
)
