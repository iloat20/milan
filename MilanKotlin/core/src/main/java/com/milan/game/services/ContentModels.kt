package com.milan.game.services

import com.milan.game.data.CharacterSaveState
import com.milan.game.domain.progression.TalentEffect
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 游戏内容数据模型（翻译 C# GameService.cs 尾部 data models 段）。
 *
 * 序列化契约：
 * - 键名用 @SerialName 对齐 data.json 的 PascalCase 键（C# 侧为公共字段 + IncludeFields 直写）；
 * - 顶层集合（Characters/Pools/TalentTrees）声明为可空元素列表，配合 [ContentJson] 的
 *   coerceInputValues：JSON 显式 null 落到非空字段 → 用默认值；数组内 null 元素 → 保留
 *   给加载路径的过滤逻辑（对齐 C# `c != null` 防御），坏条目丢弃而不是整体失败；
 * - 嵌套列表（Entries/Skills/Nodes/Voices）为正常非空元素：这些数据由内容方维护，
 *   出现 null 元素时整个内容解析失败并走兜底（比 C# 的静默 null 更安全）。
 */

/** 角色内容条目（C# CharacterDataEntry）。 */
@Serializable
data class CharacterDataEntry(
    @SerialName("CharacterId") var characterId: String = "",
    @SerialName("DisplayName") var displayName: String = "",
    @SerialName("Title") var title: String = "",
    @SerialName("World") var world: String = "Shinwa",
    @SerialName("Faction") var faction: String = "",
    @SerialName("Element") var element: String = "Flame",
    @SerialName("BaseRarity") var baseRarity: Int = 1,
    @SerialName("BaseStats") var baseStats: List<Int> = listOf(100, 80, 1000, 12),
    @SerialName("MaxStage") var maxStage: Int = 4,
    @SerialName("MaxStars") var maxStars: Int = 5,
    @SerialName("CanBreakthrough") var canBreakthrough: Boolean = false,
    @SerialName("Lore") var lore: String = "",
    @SerialName("Story") var story: String = "",
    @SerialName("TalentTreeId") var talentTreeId: String = "",
    @SerialName("Skills") var skills: List<SkillData> = emptyList(),
    @SerialName("Voices") var voices: List<String> = emptyList(),
    @SerialName("WeaponVfx") var weaponVfx: String = "",
    @SerialName("AmbientVfx") var ambientVfx: String = "",
    @SerialName("Weapon") var weapon: String = "",
    @SerialName("WeaponDesc") var weaponDesc: String = "",
)

/** 角色技能（C# SkillData）。Type 取值 Active / Passive / Ultimate。 */
@Serializable
data class SkillData(
    @SerialName("SkillId") var skillId: String = "",
    @SerialName("DisplayName") var displayName: String = "",
    @SerialName("Description") var description: String = "",
    @SerialName("Element") var element: String = "",
    @SerialName("Type") var type: String = "",
    @SerialName("Power") var power: Int = 0,
)

/** 卡池中的一个候选角色条目（C# GachaPoolEntry）。 */
@Serializable
data class GachaPoolEntry(
    @SerialName("CharacterId") var characterId: String = "",
    @SerialName("RarityIndex") var rarityIndex: Int = 1,
    @SerialName("Weight") var weight: Int = 100,
)

/** 卡池定义（C# GachaPoolDataEntry）。 */
@Serializable
data class GachaPoolDataEntry(
    @SerialName("PoolId") var poolId: String = "",
    @SerialName("DisplayName") var displayName: String = "",
    @SerialName("RarityWeights") var rarityWeights: List<Int> = listOf(400, 300, 200, 100),
    @SerialName("HardPity") var hardPity: Int = 90,
    @SerialName("SingleCost") var singleCost: Int = 100,
    @SerialName("TenCost") var tenCost: Int = 1000,
    /** UP 角色（空 = 无定轨；命中池内最高稀有度时 50/50，歪一次后下次必中，见 GachaEngine.pickFeatured）。 */
    @SerialName("FeaturedCharacterId") var featuredCharacterId: String = "",
    /** 元素 UP 过滤（空 = 不过滤；非空时 UP 仅从此元素的角色中选取，如 "Flame"）。 */
    @SerialName("FeaturedElement") var featuredElement: String = "",
    @SerialName("Entries") var entries: List<GachaPoolEntry> = emptyList(),
)

/** 单次抽卡结果（C# PullResult，仅内存传输，不序列化）。P3-3：不可变（val）。 */
data class PullResult(
    val success: Boolean = false,
    val characterId: String? = null,
    val characterName: String = "",
    val rarity: Int = 0,
    val isNew: Boolean = false,
    /** 重复角色时补偿的星魂碎片数量（新角色为 0）。 */
    val fragmentsAwarded: Int = 0,
)

/** 天赋树节点（C# TalentNodeData）。 */
@Serializable
data class TalentNodeData(
    @SerialName("NodeId") var nodeId: String = "",
    @SerialName("DisplayName") var displayName: String = "",
    @SerialName("Description") var description: String = "",
    @SerialName("BranchId") var branchId: String = "",
    @SerialName("Cost") var cost: Int = 1,
    @SerialName("PrerequisiteNodeIds") var prerequisiteNodeIds: List<String> = emptyList(),
    @SerialName("VisualLayerId") var visualLayerId: String = "",
    /** 节点效果列表（22种类型：属性加成/战斗机制/状态施加/必杀强化）。 */
    @SerialName("Effects") var effects: List<TalentEffect> = emptyList(),
)

/** 角色天赋树（C# TalentTreeData）。 */
@Serializable
data class TalentTreeData(
    @SerialName("TreeId") var treeId: String = "",
    @SerialName("BranchIds") var branchIds: List<String> = emptyList(),
    @SerialName("Nodes") var nodes: List<TalentNodeData> = emptyList(),
)

/** data.json 根对象（C# RootData）。 */
@Serializable
data class RootData(
    @SerialName("Characters") var characters: List<CharacterDataEntry?> = emptyList(),
    @SerialName("Pools") var pools: List<GachaPoolDataEntry?> = emptyList(),
    @SerialName("TalentTrees") var talentTrees: List<TalentTreeData?> = emptyList(),
)

/**
 * 进程内最新经济/拥有状态快照（2026-08 现代化：UI 订阅 [GameService.snapshot]，
 * 替代「EventBus 轻标记 + 手动重读」）。
 *
 * - [revision] 每次成功写操作后 +1：作为组合期读取的重组触发器（读快照字段则无需 revision）；
 * - 货币/碎片/拥有数为当前存档实时值（刷新时机 = 成功落盘 + 广播）。
 */
data class GameSnapshot(
    val revision: Long = 0,
    val softCurrency: Int = 0,
    val hardCurrency: Int = 0,
    val starFragments: Int = 0,
    val ownedCount: Int = 0,
    // I5：设置开关纳入快照，SettingsScreen 从快照派生（单一事实来源，去除 UI 本地镜像 + 手工回滚）。
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    /** 动效减弱（无障碍）。 */
    val reduceMotionEnabled: Boolean = false,
    // 路径 B（2026-08）：角色级数据纳入快照，UI 从快照读替代 saveData 直读 firstOrNull。
    // pityByPool：poolId → 保底计数（GachaScreen 保底进度显示/余额预拦截）；
    // ownedSaves：characterId → 角色存档拷贝（CharacterSaveState 为可变字段，拷贝防快照持有陈旧引用）。
    val pityByPool: Map<String, Int> = emptyMap(),
    val ownedSaves: Map<String, CharacterSaveState> = emptyMap(),
    // 2026-08 编队系统：出战编队 id 列表随每次写操作刷新（DeckScreen/TowerScreen 共用）。
    val formation: List<String> = emptyList(),
    // 2026-08 无尽之塔：历史最高层（TowerScreen 直接从快照读）。
    val towerBestFloor: Int = 0,
    // 2026-08 二期：战票余额（道具系统聚合值；商店礼包/爬塔门槛展示用）。
    val battleTickets: Int = 0,
    // 2026-08 三期：UP 定轨状态 poolId →「上次歪了」；true = 下次最高稀有度必中 UP。
    val featuredLostByPool: Map<String, Boolean> = emptyMap(),
)

// ─────────────── 快照切片（2026-09-08 P0-4）───────────────
//
// 背景：[GameSnapshot] 是单一扁平结构，17 个 Screen 全量订阅。任何 revision 推进
// （哪怕只是 pushEnabled 变化）都会让订阅作用域内的 composable 全部重算。
//
// 切片按关注点拆分，且**内容未变则不发射**（[ServiceCore] 用 `!=` 门控写入）：
// 订阅 economy 的 Screen 不会因为编队变化而重组。
//
// 迁移策略：切片与全量 [GameSnapshot] 并存，逐个 Screen 迁移，全部迁完前勿删全量。

/** 经济切片：货币与道具余额。 */
data class EconomySlice(
    val softCurrency: Int = 0,
    val hardCurrency: Int = 0,
    val starFragments: Int = 0,
    val battleTickets: Int = 0,
    /**
     * 每日商店归属日与已购槽位（2026-09-10 P0 fan-out）：
     * ShopViewModel 订阅本切片重建特惠列表；跨日/已购变化必须随经济门控发射，
     * 否则只靠 currency 四元组会漏刷新「今日已购」与跨日重置。
     */
    val dailyShopDate: String = "",
    val dailyBought: List<Int> = emptyList(),
)

/**
 * 角色/编队切片：出战编队与持有数量。
 *
 * 刻意**不含** ownedSaves 明细：其元素是 [CharacterSaveState]（普通 class，equals 为引用相等），
 * 每次 [ServiceCore.refreshSnapshot] 都会重新拷贝，导致本切片恒被判定为「已变」、
 * 门控彻底失效。需要单角色详情的 Screen（Detail/Progression）继续订阅全量 [GameSnapshot]；
 * 列表类（卡组/图鉴/角色列表/主页）订阅本切片——指纹未变时不发射，货币写不再惊动列表重建。
 */
data class RosterSlice(
    val ownedCount: Int = 0,
    val formation: List<String> = emptyList(),
    /**
     * 角色养成内容指纹（id/level/stage/stars/exp/未点天赋点数）。
     * 仅货币/开关变化时指纹不变 → setIfChanged 不发射。
     */
    val ownedFingerprint: Long = 0,
)

/** 进度切片：爬塔/好感等活动进度。 */
data class ProgressSlice(
    val towerBestFloor: Int = 0,
    /** 好感经验总和（好感页只在成长时重建）。 */
    val affinityTotal: Int = 0,
    /** 已领取好感等级奖励档位总数（领取后才触发）。 */
    val affinityClaims: Int = 0,
)

/** 抽卡切片：保底计数与 UP 定轨状态。 */
data class GachaSlice(
    val pityByPool: Map<String, Int> = emptyMap(),
    val featuredLostByPool: Map<String, Boolean> = emptyMap(),
)

/** 设置切片：开关类偏好。 */
data class MetaSlice(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    val reduceMotionEnabled: Boolean = false,
)

/**
 * 内容 JSON 解析配置（对齐 C# ContentJsonOptions 的宽松度）：
 * - ignoreUnknownKeys：未知/新增字段不致命（System.Text.Json 默认忽略未知属性）；
 * - coerceInputValues：JSON 显式 null 落到非空字段 → 默认值而非抛异常（C# null 覆盖字段初始化器）；
 * - allowTrailingComma：宽容尾逗号（C# AllowTrailingCommas=true）。
 * 注意：data.json 无注释，无需 ReadCommentHandling.Skip。
 */
@OptIn(ExperimentalSerializationApi::class)
internal val ContentJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    allowTrailingComma = true
}
