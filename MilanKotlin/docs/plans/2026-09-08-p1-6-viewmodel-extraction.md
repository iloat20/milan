# P1-6 ViewModel 化抽取（2026-09-08）

> 前置：P1-5（16 聚合服务接口化 + GameService `by` 委托瘦身）已完成，GameService 现仅 27
> 显式公开成员 + 16 个 `XxxApi` 契约接口。P1-6 与此前的 2026-09-08 架构优化提案的「④ 抽
> ViewModel」同源，是 P0-4 快照切片剩余 UI 迁移与 `remember(revision)` 反模式重构的治本步。

## 目标

把 UI 屏从「组合期直连 `GameState.service.xxx` + `remember(snapshot.revision)` 触发重算」逐步
收敛为「ViewModel 持有派生状态 StateFlow，Composable 只订阅与回调」。每屏完成后：
- 业务状态读取/写动作全部在 VM，Composable 无服务直读（事件回调仍可直调 service，或经 VM 转发）；
- 消灭本屏的 `remember(snapshot.revision) { ... }`；
- VM 可脱离 Compose 做纯 JVM 单测（注入真实 `GameService`）。

**非目标**：不重构 GameService/快照/事件总线；不做跨屏共享 VM（保持一屏一 VM）；不在本阶段
引入 DI 框架或 Repository 层；UI 视觉零改动（纯行为搬移，逐屏 diff 只应是结构性）。

## 现状证据（2026-09-08 盘点）

- `GameState`（ui/GameState.kt）进程级 object，`service`/`snapshot` 全局直引，无 CompositionLocal。
- 每写操作成功 → `core.refreshSnapshot()` 推进 `revision` → 屏内 `remember(snapshot.revision)`
  重算派生读。这是当前的“内容失效”机制：**VM 化必须保留该语义**。
- `remember(revision)` 出现面（全部 ui/）：AffinityScreen、CollectionScreen、ArenaScreen、
  AchievementScreen、EventScreen、HomeScreen×2、BattlePassScreen、DailyMissionScreen×3、
  DeckScreen×2、CharacterListScreen×4、PullHistoryScreen、GachaScreen、ShopScreen×2、
  TowerScreen、ProgressionPanels、CharacterDetailScreen 等 ~17 处/16 文件。
- 依赖：已有 lifecycle-runtime-ktx/runtime-compose（collectAsStateWithLifecycle）；本次补
  `lifecycle-viewmodel-ktx` + `lifecycle-viewmodel-compose`（2.11.0）。

## 模式（每屏统一范式）

```kotlin
// ui/<feature>/XxxViewModel.kt
class XxxViewModel(
    private val service: GameService = GameState.service,   // 可注入便于 JVM 单测
) : ViewModel() {
    // 派生状态：快照（revision）每变化即重算——与 remember(revision) 语义等价。
    // ⚠️ 用「显式 init collect」而非 stateIn：WhileSubscribed 的共享启动时序在
    // 双 StandardTestDispatcher 测试环境下不可推进（stateIn Eagerly 亦不生效，
    // 试点实证）；显式 collect 语义等价、时序透明。
    private val _uiState = MutableStateFlow(initialState())               // 读操作全部经 service
    val uiState: StateFlow<XxxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            service.snapshot.collect { _uiState.value = buildState() }    // 只读 API 重算派生态
        }
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts: Flow<String> = _toasts.receiveAsFlow()

    fun doThing() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val r = service.xxx(...)) {           // 写动作
                    WriteOutcome.Success -> _toasts.send("…")
                    WriteOutcome.Rejected -> _toasts.send("…")
                    WriteOutcome.SaveFailed -> _toasts.send("…")
                }
            } finally { _busy.value = false }
        }
    }
}
```

⚠️ **语义红线（试点实证）**：`MutableStateFlow` 只在值**不等**（`==`）时发射——
派生流对「内容未变的重算」会跳过发射。这不是缺陷：比 `remember(revision)` 更优
（内容没变就不重绘）。但**测试必须用会真实改变派生内容的事件驱动**（如 owned 0→1
解锁某成就再触发刷新写），不能像旧 remember 那样「随便一个写提交」当触发器。

```kotlin
// ui/<feature>/XxxScreen.kt（Composable 只做三件事）
val vm: XxxViewModel = viewModel()                            // NavBackStackEntry 提供 owner
val ui by vm.uiState.collectAsStateWithLifecycle()
val busy by vm.busy.collectAsStateWithLifecycle()
LaunchedEffect(vm) { vm.toasts.collect { feedback.show(it) } } // 一次性提示转发 LocalFeedback
```

约定：
1. 文件：VM 与 Screen 同包（`ui/<feature>/`），名 `XxxViewModel`；不建 base/通用 VM。
2. **单例只做默认值**：VM 构造参数 `service: GameService = GameState.service`，生产零接线改动，
   测试显式注入 `GameService(MemoryProvider(), dataJson, ...)`（沿用 services 测试基建）。
3. 派生读用 `service.snapshot.map`（不是 slices）：语义 = 任何写提交都重算，与旧 `remember(revision)`
   严格等价，避免迁移中丢刷新。切切片（economy/roster/…）是**后续优化**，不在本阶段混入
   （切片粒度与逐屏依赖不同，混做会让「行为等价」难验证）。
4. busy 本地防连点/防重入留在 VM（`MutableStateFlow`），与原 `remember { mutableStateOf }` 等价。
5. 提示语/文案保持现状照搬进 VM（`_toasts`），UI 只透传 `LocalFeedback.show`——VM 不碰 Compose。
6. 交互式读屏（Tower 策略战斗等带瞬时状态机的）最后做；纯展示/读写屏先做。

## 试点（本批完成）

**AchievementScreen**（ui/achievement/）：最小（1 读 achievementStatuses + 1 写 claim +
busy + 2 处本地 feedback），无既有 Robolectric 用例 → 风险最低，作为范式样板。
交付：`AchievementViewModel.kt` + `AchievementScreen.kt` 改造 + 纯 JVM 单测
`AchievementViewModelTest`（真实 GameService + setMain）。
验收：`:app:compileDebugKotlin` + 全量 `:app:testDebugUnitTest` 绿；行为逐项等价（列表、
已解锁/已领取计数、领取反馈、busy 互斥）。

## 分批推进顺序（试点确认后）

| 批 | 屏 | 读源 | 写动作 | 备注 |
|---|---|---|---|---|
| A（试点） | Achievement | achievementStatuses | claim | 已完成样板 |
| B | BattlePass / Affinity | monetization/getCharacterAffinityData | 领取类 | 各自 1-2 写（已完成） |
| C | Shop / DailyMission / Event | dailyOffers、missions、events | 购买/上报/签到 | **已完成（2026-09-09）**：ShopViewModel / DailyMissionViewModel / EventViewModel + Screen 迁移 + 纯 JVM 单测 ×3；DailyMission 的 ensureDailyMissionReset 收敛进 VM init |
| D | Arena / Collection / CharacterList / Detail | 多读 + owned() | challenge/claim | **已完成（2026-09-09）**：ArenaViewModel / CollectionViewModel / CharacterListViewModel / CharacterDetailViewModel（按 characterId 建实例，viewModelFactory + key）+ Screen 迁移 + 纯 JVM 单测 ×4。⚠️ 测试坑：`getOpponents()` 每次随机生成模拟对手，**不可逐项比较**，断言须结构化（非空 + 字段有效） |
| E | Deck / Tower / Progression | formation、members、talent | 编队/爬塔/养成 | **已完成（2026-09-09）**：DeckViewModel / TowerViewModel / ProgressionViewModel + Screen 迁移 + 纯 JVM 单测 ×3（8 cases）。⚠️ 编队/养成/爬塔写事务的 VM 单测范式（课上修正）：**以服务层同步 await 结果为断言真源，动作后再建 VM 验证派生态重建**——规避 VM 的 viewModelScope.launch 写 + advanceUntilIdle 与 IO 落盘回续互抢 testScheduler 主调度器导致的时序抖动/跨测试 Main 碰撞 |
| F | Gacha / PullHistory / Home 组件 / AppChrome | pity/历史/home 卡 | pull | Home/Chrome 与全屏 revision 解耦最后 |

每批：compile + 全量单测绿 + 该屏既有 Robolectric 用例（如有）不回归。

## 测试策略与坑

- **VM JVM 单测**：`viewModelScope` 用 `Dispatchers.Main` → 必须 `Dispatchers.setMain(StandardTestDispatcher)`，
  且 `runTest { … }` 内 `advanceUntilIdle()` 后才断言；真实 `GameService` 注入 + 直接改
  `saveData` 可搭初始态（改完如需触发派生，走一次真实写操作或先读 service 纯函数断言）。
- **⚠️ 真实写提交的 IO 时序（C 批实测）**：事务落盘经真实 `Dispatchers.IO`
  （`ServiceCore.writeDispatcher`），**不在 testScheduler 虚拟时间内**——成功/写路径断言用
  「真实时间轮询 + 循环内 `advanceUntilIdle()`」兜底；且 VM init 里的 `ensure*` 写（跨日重置/
  懒激活）完成前，VM 派生态与 service 只读 API 可能短暂不同（先 mutate 后 collect 重建），
  收敛条件必须要求「非空 + 相等」，两侧同为空表时首轮即真会造成时序竞态。
  搭初始态改 `saveData` 要放在 VM init 的 `ensure*` 完成之后（重置会清掉先改的字段）。
- **⚠️ E 批增益（2026-09-09 实测）**：
  1. `awaitUntil` **必须无论条件是否首轮即真都先 `Thread.sleep(20)` + `advanceUntilIdle()` 一轮**——
     否则「前一写操作已挂起的 IO 落盘 → 回续被投递到 Main 调度器队列」的连续体滞留，测试体结束
     即 cancel → 下个测试 `Dispatchers.Main` 在 reset 后被访问（`UncaughtExceptionsBeforeTest`）。
  2. **避免「VM 异步写 + 紧接断言 VM 派生态」**：viewModelScope.launch 的写与
     `advanceUntilIdle`（主调度器排空）在 testScheduler 下会同周期竞争落盘回续，曾出现
     toggle 写被后续 collector 读旧快照覆盖的偶发。范式改为：写操作直接 `await` 服务层结果
     （确定性），随后**再构造 VM** 验证派生态重建；VM 单测由此退化为「构造读快照 + 服务层
     事务真值」两层断言，彻底消除时序抖动。
- **不触碰 GameState 单例**（VM 注入真实 service 即可），规避 resetForTest 竞态。
- 凡需在 Robolectric Compose 里组合改造后屏：`createComposeRule` 无 ViewModelStoreOwner →
  需给测试 content 包 `LocalViewModelStoreOwner`（可用
  `createAndroidComposeRule<ComponentActivity>` 替代）；如无既有用例则不加（试点屏无）。
- 派生读的文本断言：服务返回对象比对为主，不依赖 UI 文案。

## 完成定义（每屏）

- [ ] Screen 顶层无 `remember(snapshot.revision)`、无 `GameState.service.` 直读（事件回调允许 `vm.` 委托）
- [ ] VM 文件在 feature 包内、纯 Kotlin（除 GameService 外不 import ui.* 以外 Compose 类型）
- [ ] compile + 全量单测绿
- [ ] 对应屏交互手测点不变（行为搬移，不做视觉/文案改动）
