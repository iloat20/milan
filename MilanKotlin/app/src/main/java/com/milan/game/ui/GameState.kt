package com.milan.game.ui

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.domain.battle.UnitStats
import com.milan.game.domain.progression.ProgressionEngine
import com.milan.game.domain.progression.StatsCalculator
import com.milan.game.domain.progression.TalentEngine
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.eventbus.EventBus
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GameService
import com.milan.game.services.GameSnapshot
import com.milan.game.services.TalentTreeData
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级游戏状态单例（C# GameState.cs 翻译）。
 *
 * 所有界面共享同一 [GameService]（从而同一存档 / 同一内容）：抽卡结果在列表页立即可见，
 * 存档只加载一次。UI 状态刷新经 [snapshot]（StateFlow）订阅，组合期直接读 service 亦可
 * （GameService 原地修改模型 + 成功写操作后推进快照 revision 触发重组）。
 *
 * 初始化：[ensureInitialized] 幂等（双检锁），Android 接入层（MainActivity / Application）
 * 注入存档提供者与内容 JSON；访问 [service] 前必须已初始化。
 */
object GameState {

    private val gate = Any()
    // @Volatile：双检锁的首次检查读的是非同步路径，必须保证另一线程写入 initialized 的
    // 可见性（否则极端并发下可能看到 initialized=true 但 serviceRef 未发布，getter 抛异常）。
    @Volatile
    private var initialized = false
    private var serviceRef: GameService? = null

    // 就绪信号（2026-08 启动异步化配套）：内容/存档初始化移入后台线程后，
    // UI 宿主（MilanNavHost）在 ready=true 前不组合任何直读 service 的屏幕。
    // StateFlow 提供可见性保证：读侧见到 true 必然可见 serviceRef 的发布。
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    // R4-04（2026-08-30）：初始化失败态。UI 宿主据此渲染错误页而非永久停留在加载动画——
    // 旧实现中初始化一旦抛异常，ready 永远停在 false，玩家每次打开只看到转圈，
    // 且初始化幂等、异常可复现，杀进程重进也一样（只能卸载重装或清数据）。
    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure.asStateFlow()

    private val talentEngine = TalentEngine()
    // P2-7：ProgressionEngine 无状态纯函数，进程级单例复用一份实例——
    // 此前 computeStatsAt 每次调用 new ProgressionEngine()，详情页/养成页每次重组重复分配。
    private val progressionEngine = ProgressionEngine()

    /** 共享服务实例（唯一 GameService，进程内只创建一次）。 */
    val service: GameService
        get() = checkNotNull(serviceRef) { "GameState 未初始化：先调用 ensureInitialized(...)" }

    /** 最新经济/拥有状态快照（StateFlow）：UI 用 collectAsStateWithLifecycle 订阅，
     *  替代「EventBus 轻标记 + 手动重读」（2026-08 现代化）。 */
    val snapshot: kotlinx.coroutines.flow.StateFlow<GameSnapshot>
        get() = service.snapshot

    /** 幂等初始化（C# EnsureInitialized 双检锁）。内容加载失败/缺失走 [GameService] 兜底。 */
    fun ensureInitialized(saveProvider: SaveProvider, contentJson: String?, onTrace: (String) -> Unit) {
        if (initialized) return
        synchronized(gate) {
            if (initialized) return
            // R4-04（2026-08-30 审查修复）：初始化失败不得逃逸，也不得静默吞掉。
            // 旧实现直接让异常穿透到宿主（MilanApp 的 catch 只留痕、不推进任何状态），
            // ready 永远停在 false → 玩家永久停留在加载动画；且初始化幂等、异常可复现，
            // 杀进程重进结果相同（只能卸载重装或清数据）。
            // 现行为：记录 failure（UI 渲染错误页）、ready 保持 false（不放行未就绪导航树），
            // 异常不逃逸（initialized 仍为 false，调用方可以安全重试）。
            val result = runCatching { GameService(saveProvider, contentJson, onTrace) }
            val service = result.getOrNull()
            if (service == null) {
                val cause = result.exceptionOrNull()
                _failure.value = cause?.message ?: cause?.let { it::class.java.simpleName } ?: "未知初始化失败"
                return
            }
            serviceRef = service
            initialized = true
            _failure.value = null // 首次成功或重试成功，清除失败态
            // 宿主装配 EventBus 留痕钩子 → CrashReporter（Core 层保持纯逻辑，不依赖 Android）。
            // P3-8：走 traceNonFatal 轻量留痕（只落 non_fatal.txt），此前接 write() 会把普通
            // handler 异常当崩溃处理（弹崩溃对话框 + 递增 crash_count + 归档），语义过重。
            if (EventBus.handlerException == null) {
                EventBus.handlerException = { type, ex ->
                    try { CrashReporter.traceNonFatal("EventBus.${type.name}", ex) } catch (_: Exception) { }
                }
            }
            // 最后发布就绪：保证读侧见到 true 时 serviceRef / 内容索引 / 存档全部可见
            _ready.value = true
        }
    }

    /**
     * 仅供测试：把单例重置回未初始化状态，使下一次 [ensureInitialized] 能重新注入。
     *
     * 背景（2026-09-02）：Robolectric 每个测试类都会实例化 MilanApp，其 onCreate 在
     * Dispatchers.IO 异步注入真实 data.json；若该注入先于测试类自己的注入完成，
     * ensureInitialized 幂等早退会使测试注入变 no-op（跨类单例内容竞态，曾致 G05 假失败）。
     * 触碰本单例的测试类在 @BeforeClass / @Before 中先 reset 再注入即可顺序无关。
     *
     * ⚠️ 生产代码禁止调用：进程级单例一经初始化即存活到进程结束（语义见文件头 KDoc）。
     */
    internal fun resetForTest() {
        synchronized(gate) {
            serviceRef = null
            initialized = false
            _ready.value = false
            _failure.value = null
        }
        // EventBus.handlerException 钩子属全局基础设施，不在此回滚（测试类自行管理）。
    }

    // ---- 便捷访问器（UI 用，对齐 C# 同名成员）----

    val currency: Int
        get() = service.saveData.softCurrency

    val currencyLabel: String
        get() = "星尘: $currency"

    /** 出战编队槽位上限（单一事实来源：SaveData.Companion.MAX_FORMATION_SIZE）。 */
    val maxFormationSize: Int
        get() = com.milan.game.data.SaveData.MAX_FORMATION_SIZE

    val ownedCount: Int
        get() = service.saveData.ownedCharacters.size

    /** 已拥有角色视图列表（存档 × 内容定义合并）。 */
    fun owned(): List<OwnedCharacterView> = service.saveData.ownedCharacters.mapNotNull { ch ->
        ch ?: return@mapNotNull null
        val def = service.character(ch.characterId)
        OwnedCharacterView(ch, def)
    }

    /**
     * 由基础值 + 养成推导实时战斗属性（详情页/养成页用）。
     *
     * ⚠️ TODO（BUG #4）：本方法不含装备属性加成，而 [ServiceCore.unitStatsFor] 包含。
     * 当前装备系统已禁用（EquipmentService 删除），无实际影响；若未来恢复装备系统，
     * 详情页/养成页显示的属性将与战斗实际使用的属性不一致。
     * 修复方向：将 [ServiceCore.calculateEquipmentStats] 下沉到 shared domain，
     * 或在本方法中调用 service 的装备计算接口。
     */
    fun computeStats(ch: OwnedCharacterView): UnitStats =
        computeStatsAt(ch, ch.save.level, max(1, ch.save.stage))

    /**
     * 在指定等级/阶段/星级下计算属性（用于养成页「下一级 / 下一阶 / 升星」预测值）。
     * 天赋加成按当前已点亮节点计算，不随等级/阶段/星级假设改变。
     * [stars] 缺省（<0）时取 [ch.save.stars] 的实时值。
     */
    fun computeStatsAt(ch: OwnedCharacterView, level: Int, stage: Int, stars: Int = -1): UnitStats {
        val def = ch.def
        val save = ch.save
        if (def == null)
            return UnitStats(atk = 0, def = 0, hp = 1, spd = 0, characterId = save.characterId)

        // stars 缺省（<0）时取实时星级；养成页「下一级/下一阶/升星」预测传显式值。
        val st = if (stars < 0) save.stars.coerceAtLeast(1) else stars
        // 天赋分支映射（TalentTreeData 为 app 侧内容类型，映射留在适配器层）。
        // 树为 null → 空分支列表 → 全 0，与旧实现行为一致。
        val branchIds = ch.talent?.nodes.orEmpty()
            .filter { save.talentPoints.contains(it.nodeId) }
            .map { it.branchId }

        // 构建节点 Effects 映射（新天赋效果系统）
        val allocatedNodes = ch.talent?.nodes.orEmpty()
            .filter { save.talentPoints.contains(it.nodeId) }
            .map { it.nodeId }
        val nodeEffectsMap = ch.talent?.nodes.orEmpty()
            .filter { it.effects.isNotEmpty() }
            .associate { it.nodeId to it.effects }

        // 属性公式单一事实来源下沉 shared domain（StatsCalculator）：
        // 桌面模拟器 / 未来战斗页与 App 同口径；此处仅做 app 类型 → 领域参数的适配。
        return StatsCalculator.compute(
            baseStats = def.baseStats,
            level = level,
            stage = stage,
            stars = st,
            branchIds = branchIds,
            characterId = save.characterId,
            progression = progressionEngine,
            talent = talentEngine,
            nodeEffectsMap = nodeEffectsMap.ifEmpty { null },
            allocatedNodes = allocatedNodes,
        )
    }
}

/** 角色视图：存档 + 内容定义合并（C# OwnedCharacterView）。 */
class OwnedCharacterView(
    val save: CharacterSaveState,
    val def: CharacterDataEntry?,
) {
    val name: String get() = def?.displayName ?: save.characterId
    val title: String get() = def?.title ?: ""
    val rarity: Int get() = def?.baseRarity ?: 1
    val world: String get() = def?.world ?: "Shinwa"
    val element: String get() = def?.element ?: "Flame"
    val lore: String get() = def?.lore ?: ""
    val canBreakthrough: Boolean get() = def?.canBreakthrough ?: false

    /** 角色天赋树（按 TalentTreeId 从服务内容中查；无定义返回 null）。 */
    val talent: TalentTreeData?
        get() = def?.talentTreeId?.let { GameState.service.talentTree(it) }
}
