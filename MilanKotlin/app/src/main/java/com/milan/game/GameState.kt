package com.milan.game

import com.milan.game.data.CharacterSaveState
import com.milan.game.data.SaveProvider
import com.milan.game.di.AppGraph
import com.milan.game.infrastructure.CrashReporter
import com.milan.game.infrastructure.eventbus.EventBus
import com.milan.game.services.CharacterDataEntry
import com.milan.game.services.GameService
import com.milan.game.services.GameSnapshot
import com.milan.game.services.TalentTreeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级游戏状态单例（C# GameState.cs 翻译）。
 *
 * **职责边界（2026-09 架构优雅化）**：本对象只负责
 * 1. 启动门控（[ready] / [failure]）与幂等初始化；
 * 2. 作为 [AppGraph] 的装配时机（组合根持有真正依赖）。
 *
 * UI **禁止**再经本对象读 service / 做属性计算——Screen 走 ViewModel（`AppGraph.factory`），
 * 属性推导走 [com.milan.game.ui.stats.CharacterStats]。
 * [service] 保留给 Application / Worker / 测试等非 Compose 接入点。
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

    /** 共享服务实例（唯一 GameService，进程内只创建一次）。Compose 层请走 AppGraph / VM。 */
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
            AppGraph.install(service)
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
            AppGraph.clear()
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

    /** 已拥有角色视图列表（存档 × 内容定义合并）；天赋树在构造时注入，视图不再回查单例。 */
    fun owned(): List<OwnedCharacterView> = service.saveData.ownedCharacters.mapNotNull { ch ->
        ch ?: return@mapNotNull null
        val def = service.character(ch.characterId)
        val talent = def?.talentTreeId?.let { service.talentTree(it) }
        OwnedCharacterView(ch, def, talent)
    }
}

/**
 * 角色视图：存档 + 内容定义合并（C# OwnedCharacterView）。
 *
 * [talent] 为构造时注入的天赋树（可为 null）——不再隐式 `GameState.service` 回查，
 * 保证视图可在无单例环境下构造与测试。
 */
class OwnedCharacterView(
    val save: CharacterSaveState,
    val def: CharacterDataEntry?,
    val talent: TalentTreeData? = null,
) {
    val name: String get() = def?.displayName ?: save.characterId
    val title: String get() = def?.title ?: ""
    val rarity: Int get() = def?.baseRarity ?: 1
    val world: String get() = def?.world ?: "Shinwa"
    val element: String get() = def?.element ?: "Flame"
    val lore: String get() = def?.lore ?: ""
    val canBreakthrough: Boolean get() = def?.canBreakthrough ?: false
}

/**
 * 由存档构建 [OwnedCharacterView] 并注入内容定义与天赋树（统一口径，VM/Screen 共用）。
 * 未拥有或内容缺失时对应字段为 null，调用方自行兜底。
 */
fun GameService.ownedView(save: CharacterSaveState): OwnedCharacterView {
    val def = character(save.characterId)
    val talent = def?.talentTreeId?.let { talentTree(it) }
    return OwnedCharacterView(save, def, talent)
}
