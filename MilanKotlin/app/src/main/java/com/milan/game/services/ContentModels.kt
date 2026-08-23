package com.milan.game.services

import com.milan.game.data.CharacterSaveState
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
    // 路径 B（2026-08）：角色级数据纳入快照，UI 从快照读替代 saveData 直读 firstOrNull。
    // pityByPool：poolId → 保底计数（GachaScreen 保底进度显示/余额预拦截）；
    // ownedSaves：characterId → 角色存档拷贝（CharacterSaveState 为可变字段，拷贝防快照持有陈旧引用）。
    val pityByPool: Map<String, Int> = emptyMap(),
    val ownedSaves: Map<String, CharacterSaveState> = emptyMap(),
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
