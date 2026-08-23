# Milan 移动端（app）代码质量审查总报告

> 审查对象：`MilanKotlin/`（Kotlin + Jetpack Compose 原生 Android 抽卡游戏）
> 审查维度：架构设计 · 代码可读性 · 模块解耦 · 命名规范 · 错误处理 · 用户界面交互
> 审查方式：核心文件逐行 Read（GameState / GameService / SaveManager / EventBus / MainActivity / MilanApp / SaveData / HomeScreen / GachaScreen / GpuEffects / PortraitLoader / SettingsScreen）+ 两条专项并行 Explore（UI 层 19 文件、领域/内容/基础设施层 14 文件）+ 全部 Critical 发现回溯源码复核。
> 报告性质：只读审查，**未改动任何文件**。所有 `file:line` 均来自实际读取。

---

## 0. 审查结论（一句话 + 评级）

**总体评级：B+（架构分层优秀，领域层红线达标；短板集中在「性能治理与视觉野心失衡」「设计系统建而未用」「内容数据双份真相源」）。**

硬骨头（架构 / 事务范式 / 错误处理 / 领域纯净化）已经啃下且质量扎实；真正需要修的是**确定性的稳定性与功耗缺陷**（6 项 Critical 可直接导致闪退/发热/ANR），以及**一致性层面的知行不一**（设计系统有公共件却没人用、内容真相源双写已漂移、主题色双源）。这些问题不致命但会持续侵蚀体验与可维护性。

---

## 1. 架构与分层评价（正面事实，先立基线）

| 维度 | 结论 | 证据 |
|---|---|---|
| 分层清晰度 | ✅ 优秀 | UI 仅做渲染 + 事件转发；定价/属性推导/事务下沉到 `GameService`/`domain`（`ShopScreen.kt:89` 一律走 `EconomyFormulas`，无硬编码） |
| 事务范式 | ✅ 统一 | `GameService.transactionLocked`（GameService.kt:340）收敛了原 11 处手写骨架；预算→变更→落盘→失败回滚→仅成功广播，契约清晰 |
| 类型化结果 | ✅ 优秀 | `WriteOutcome` / `PullOutcome`（`WriteOutcome.kt`）替代裸 Boolean/空列表，UI 可精确提示原因 |
| 错误健壮性 | ✅ 优秀 | `SaveManager.load()` 永不抛（SaveManager.kt:34-69），主档损坏→备份→默认档，全程留痕 |
| 领域层红线 | ✅ 达标 | `shared/commonMain` 全树 `import android` 搜索 **0 命中**，数值单一事实来源 `EconomyFormulas` 落实到位 |
| 状态订阅现代化 | ✅ 到位 | `GameState.snapshot` StateFlow + `collectAsStateWithLifecycle`，`ShopScreen.kt:59`/`AppChrome.kt:100` 均正确使用 |
| 图片解码路径 | ✅ 专业 | 分档采样（↓16× 内存）+ LRU(24MB) + 单飞去重 + 内存压力回调 + 缺图占位，设计思路正确 |

**结论**：底层地基是好的，下面的问题属于"上层装修与治理"层面的欠账，修复成本远低于重写。

---

## 2. 关键发现汇总（按严重度）

| ID | 严重度 | 主题 | 关键文件 | 一句话 |
|---|---|---|---|---|
| C1 | Critical | 全屏逐帧无限动画无门控 | `GpuEffects.kt:173-180,210-217` + `HomeScreen.kt:89` | 主页常驻全屏 5-octave FBM 着色器满帧渲染，发热/掉帧 |
| C2 | Critical | 位图解码未捕获 OOM | `PortraitLoader.kt:136,152` | `catch(Exception)` 拦不住 `OutOfMemoryError`，低端机抽卡/图鉴闪退 |
| C3 | Critical | 单飞竞态返回伪 null | `PortraitLoader.kt:144-145` | `putIfAbsent` 返回值被丢弃后二次查表，命中返回 null→长期占位 |
| C4 | Critical | 主线程文件 IO | `SettingsScreen.kt:158-161,255` | `CrashReporter.exportAll()/crashCount()` 主线程同步读写，ANR 风险 |
| C5 | Critical | 内存回调重复注册 | `PortraitImage.kt:92-98` | 全局缓存生命周期挂叶子 composable，列表滚动时列表膨胀 |
| C6 | Critical | 预览层不响应系统返回 | `DeckScreen.kt:127-206` | 返回键直接退栈而非关浮层，交互相反 |
| C7 | Critical | 内容双份真相源 + 漂移 | `GameContent.kt:339-364` vs `data.json:1027` | 池名漂移「次元裂缝」≠「诸神黄昏」，UP 池仅兜底出现 |
| C8 | Critical | 保底类型「值/下标」语义陷阱 | `PityCounter.kt:39` / `GachaEngine.kt:21` | `Int` 传错下标静默保底到低一档，无异常 |
| I1 | Important | 三份角色卡模板逐字重复 | `DeckScreen.kt:214` / `CharacterListScreen.kt:147` / `CollectionScreen.kt:255` | ~180 行重复，已漂移（卡组页缺共享元素过渡） |
| I2 | Important | 设计系统公共件零调用 | `UIComponents.kt` / `ThemeButtons.kt` / `AuraHalo.kt` | `RarityChip`/`DangerButton`/`TabularText` 等 7 个件无人用 |
| I3 | Important | 主题色双源 | `Theme.kt:31` vs `AppTheme.kt:21` | 两个"熔金"不同色；`MaterialTheme.colorScheme` 0 引用 |
| I4 | Important | 反馈渠道四套写法 | `ShopScreen`/`SettingsScreen`/`GachaScreen`/`ProgressionScreen` | 0 处 Snackbar，Toast 与 `LaunchedEffect(toast)` 混用 |
| I5 | Important | 设置项无 StateFlow 字段 | `ContentModels.kt:122` / `GameService.kt:71` | SettingsScreen 本地镜像 + 手工回滚，状态双源 |
| I6 | Important | GameContent 职责过多 | `GameContent.kt:18-540` | 兜底 + build×3 + 原地 enrich 突变，540 行难拆 |
| I7 | Important | 列表 filterSort 无 remember | `CollectionScreen.kt:88` / `CharacterListScreen.kt:79` | 每次重组全量 56 角色 3×filter+sort |
| I8 | Important | LRU 容量核算 O(n²) | `PortraitLoader.kt:79-95` | 每次 put 全表 `sumOf`，驱逐 k 个时 k×n |
| I9 | Important | Glance widget 名不副实 | `GachaGlanceWidget.kt:19-32` | 今日运势永远静态、零联动、无刷新机制 |
| I10 | Important | OnDeviceAgent 命名误导 | `OnDeviceAgent.kt:22-44` | 名为 Agent 实为模板 Stub，易被误用 |
| I11 | Important | 防重入缺失 | `ShopScreen.kt:92-132` / `SettingsScreen.kt:96-107` | 快速双击连开两包，无 in-flight 态 |
| I12 | Important | 公共 Composable 缺 Modifier | `ShopScreen.kt:52` / `SettingsScreen.kt:55` / `PageComponents.kt:161,185` | 宿主无法注入 padding/testTag |

（Minor 共 20+ 项，见 §6 速览表。）

---

## 3. Critical 详解（必修，附修复）

### C1 — 全屏逐帧无限动画无门控（功耗/发热/掉帧，#1 优先级）
- **位置**：`GpuEffects.kt:173-180`（HolographicFoilOverlay）、`:210-217`（FluidBackground）；`HomeScreen.kt:89` 全屏挂载 `FluidBackground`；`GachaScreen.kt:414` `GpuRevealLayer(active = true, …)` 硬编码门失效。
- **问题**：三处 `LaunchedEffect(Unit) { while(true) withFrameNanos { time = … } }` 都**无条件永久自增 time**，每帧触发 `graphicsLayer`/Canvas 失效。主页 `FluidBackground` 的 AGSL 是 5 层 octave FBM（`GpuEffects.kt:97-101`，每像素 5× noise + 20× hash）→ 主 tab 常驻满帧全屏噪声着色器。
- **影响**：设备永不进入渲染空闲，功耗/发热/掉帧代价极高，且与该工程其它地方（立绘采样降 16× 内存）的低端机取向自相矛盾。
- **修复**：统一补生命周期 + 可见性门控，并给省电模式/低端机降级：
  ```kotlin
  private fun rememberShaderTime(active: Boolean): FloatState {
      var time by remember { mutableFloatStateOf(0f) }
      val lifecycle = LocalLifecycleOwner.current.lifecycle
      LaunchedEffect(active) {
          if (!active) return@LaunchedEffect
          lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
              val start = withFrameNanos { it }
              while (true) withFrameNanos { t -> time = (t - start) / 1_000_000_000f }
          }
      }
      return time
  }
  // FluidBackground(active = !powerSaveMode && homeVisible)
  ```
  `GpuRevealLayer` 的 `active` 门改为由调用方按 reveal 可见性传入，不要硬编码 `true`。

### C2 — 位图解码未捕获 OutOfMemoryError（低端机闪退）
- **位置**：`PortraitLoader.kt:136`（`load` 的 `catch (e: Exception)`）、`:152`（`decodeOnce` 的 `catch (e: Exception)`）。
- **问题**：`BitmapFactory.decodeResource` 的典型失败是 `OutOfMemoryError`（**Error 非 Exception**），当前兜底完全拦不住，直接上抛→闪退，与文件自述"宁可难看也不能崩"（`:129`）相悖；且 `e` 未使用，失败不可观测（没接 `CrashReporter.traceNonFatal`）。
- **修复**：
  ```kotlin
  } catch (t: Throwable) {
      if (t is CancellationException) throw t
      CrashReporter.traceNonFatal("PortraitLoader.decode(${key.resId})", t)
      deferred.complete(null); null
  }
  ```
  （`load` 的 `catch` 同步改为 `Throwable`。）

### C3 — 单飞竞态返回伪 null（解码"成功"却显示占位）
- **位置**：`PortraitLoader.kt:144-145`
  ```kotlin
  val mine = inflight.putIfAbsent(key, deferred) == null
  if (!mine) return inflight[key]?.await()   // ← putIfAbsent 的返回值被丢弃后二次查表
  ```
- **问题**：`putIfAbsent` 已返回既有 deferred 却被丢弃；在 `!mine` 与 `inflight[key]` 之间，持有者协程可能已走完 `finally { inflight.remove(...) }`（`:157`），二次查表得 null → 返回 null → 上层判解码失败、长期占位。2 列网格快速滚动时正是本函数设计要覆盖的场景。
- **修复**（一行）：
  ```kotlin
  val existing = inflight.putIfAbsent(key, deferred)
  if (existing != null) return existing.await()
  ```

### C4 — 主线程文件 IO（ANR 风险）
- **位置**：`SettingsScreen.kt:158-161`（onClick 内 `CrashReporter.exportAll()` 同步多文件 read/write）、`:255`（`remember { CrashReporter.crashCount() }` 组合期同步读）。
- **问题**：本页其余写操作都已正确异步化（`:79 rememberCoroutineScope` + suspend service），唯独 CrashReporter 两处漏网；`:255` 还在组合期做 IO 副作用，且导出后计数永不刷新。
- **修复**：在 `CrashReporter` 补 `suspend fun exportAllAsync()/crashCountAsync()`（`withContext(Dispatchers.IO)`）；SettingsScreen 用 `LaunchedEffect(exportTick)` 重读计数。

### C5 — 内存回调按 composable 实例重复注册（Application 级列表膨胀）
- **位置**：`PortraitImage.kt:92-98`（`DisposableEffect(appContext){ registerComponentCallbacks(PortraitLoader.memoryCallbacks) }`）。
- **问题**：`ContextImpl` 内部是 `ArrayList`，同一单例实例注册 N 次就存 N 条，`onTrimMemory` 被调用 N 次；`unregisterComponentCallbacks` 只移除首个匹配项。图鉴/列表/卡组滚动时每个 item 进出可见区都在对 Application 级列表增删。
- **修复**：从 `PortraitImage` 移除该 `DisposableEffect`，改在 `MilanApp.onCreate()`（`MilanApp.kt:23` 附近，当前无任何 `registerComponentCallbacks`）注册一次。

### C6 — 全屏预览遮罩不响应系统返回（交互正确性问题）
- **位置**：`DeckScreen.kt:127-206`（普通 `Box` 遮罩，仅 `.clickable { previewId = null }` 关闭，无 `BackHandler`）。
- **问题**：预览打开时按系统返回/侧滑直接触发 Navigation 退栈切走卡组页，而非关闭预览。项目内已有正确范式 `GachaScreen.kt:170` `BackHandler(enabled = showReveal) { skipReveal() }`，此处属遗漏。
- **修复**：`BackHandler(enabled = previewId != null) { previewId = null }`（更彻底改为 `Dialog` 一并获得焦点捕获与 a11y 语义）。

### C7 — GameContent 与 data.json 双份真相源（维护炸弹，最大冗余面）
- **位置**：`data.json:1027` 主池名 `"次元裂缝 · 常驻"` vs `GameContent.kt:353` `"诸神黄昏 · 常驻"`；`GameContent.kt:357-362` 还建了 `pool_flame`「业火轮盘 · UP」池，而 `data.json` 只有 1 个池。
- **问题**:
  1. **命名漂移已发生**：主/兜底池名不一致，`DataJsonContentTest.kt:92-98` 断言了 entries/weights/pity/cost 却**唯独没断言 displayName**，漂移被测试静默放过。
  2. **功能不对称**：兜底比主内容"功能更丰富"（多一个 UP 池），玩家正常包里永远看不到 UP 池——违背"兜底应是主内容严格子集"。
  3. **28 角色全量字段双写**：`GameContent.buildCharacters()`（:39-300）与 `enrich()`（:373-540 的 6 张 Map）把 data.json 已存在的剧情/语音/武器文案又硬编码一遍，任何文案修改需改两处。
- **影响**：内容运营改文案/加活动池极易只改一边，造成两条加载路径体验分裂；测试给假安全感。
- **修复**：
  - 决策并固化边界：要么 `data.json` 补 `pool_flame` 让主路径也有 UP 池，要么 `GameContent` 兜底下掉 `pool_flame`，使兜底成为主内容子集。
  - 池名统一（建议以 `data.json` 为准），并在 `DataJsonContentTest` 增加 `assertEquals(jsonMain.displayName, fallbackMain.displayName)`。
  - 更优：把"28 角色 + 池 + 天赋树"的唯一真相源收敛到 `data.json`，`GameContent` 仅保留"缺失时的极简占位"，由单测强制两者 id 集合与关键字段一致。

### C8 — PityCounter 保底稀有度「值/下标」语义陷阱（静默正确性问题）
- **位置**：`PityCounter.kt:39`（`Rarity.fromValue(minRarityForPity) ?: Rarity.R`），`GachaEngine.kt:21`（`rollRarity` 用 `Rarity.entries` 下标）。
- **问题**：`rollWithPity` 的 `minRarityForPity` 语义是 `Rarity.value`（SSR=3），而 `GachaEngine.rollRarity` 用 entries 下标（SSR=2）。作者注释称"0..3 内恰好重合"——但这只在数值巧合偏移下成立；一旦某处误传下标（如传 `2` 想表达 SSR），`fromValue(2)` 返回 **SR**，保底砸出低一档且绝不抛异常。
- **修复**：把参数类型从 `Int` 改为 `Rarity`（或 `@JvmInline value class RarityValue`），消除二义性：
  ```kotlin
  fun rollWithPity(rng: Random, rarityWeights: IntArray, minRarity: Rarity): Rarity
  ```
  调用方传 `Rarity.SSR` 而非 `Rarity.SSR.value`。

---

## 4. Important 详解（精选）+ 分类清单

### 4.1 重复代码 / 设计系统（最大可维护性债）
- **I1 三份角色卡模板逐字重复**：`DeckCard` / `ListCard` / `CollectionCard` 外层稀有度描边 + 元素淡底立绘框 + 名称/称号行 ~85% 相同，差异仅「是否 sharedBounds / 是否未拥有蒙层 / 末行星级」。已漂移：`DeckCard` 缺星级行与 sharedBounds（导致卡组页点进详情无共享元素过渡）。→ 抽单一 `CharacterCard`（见 §5 R1）。
- **I2 设计系统公共件零调用**：`UIComponents.kt` 的 `TabularText`/`Avatar`/`IconCircle`/`RarityChip`/`TitleWithOrnament`、`ThemeButtons.kt` 的 `DangerButton`、`AuraHalo.kt:71` 的 `PortraitWithAura`（与 `PortraitImage(aura=true)` 逐字重复）均无调用点。结果：存在 `RarityChip` 却三处手写稀有度标签、存在 `DangerButton` 却用 `NeonButton` 染红。→ 删或回归调用点，并维护"组件—调用点"清单。
- **I3 主题色双源**：`Theme.kt:31` M3 `secondary`「熔金」`0xFFD9A95C` vs `AppTheme.kt:21` `AppTheme.Gold` `0xFFE8B84B` 两个不同"熔金"；全工程 0 处 `MaterialTheme.colorScheme`/`typography` 引用，`GameTypography` 是死令牌集。淡金强调色在弹窗与页面间肉眼漂移。→ `TwilightColors` 全部槽位改为引用 `AppTheme` 常量；`AlertDialog` 显式传 `containerColor`/`titleContentColor`。
- **I4 反馈四套写法**：`ShopScreen`/`SettingsScreen`（`toast` state + `LaunchedEffect(toast)` 两段逐字相同）、`GachaScreen`（直 `Toast.makeText`）、`ProgressionScreen`（局部 `fun toast`）。0 处 Snackbar。→ 统一 Snackbar 宿主（见 §5 R3）。
- **I5 设置项无 StateFlow 字段**：`GameSnapshot`（`ContentModels.kt:122`）只有 revision/货币/碎片/ownedCount，无开关字段，SettingsScreen 只能本地镜像 + 手工回滚（`:191-203` 把领域层默认 `true` 硬编码在 UI）。→ `GameSnapshot` 补 `soundEnabled/vibrationEnabled/pushEnabled`，`refreshSnapshot()` 同步填充，UI 改为纯派生。

### 4.2 命名 / 死代码 / 耦合
- **I6 GameContent 职责过多**（:18-540：兜底 + build×3 + 原地 enrich 突变）→ 拆分为 `CharacterContent`/`PoolContent`/`TalentContent`/`ContentEnricher`，`enrich` 改纯函数。
- **I9 Glance widget 名不副实**（`GachaGlanceWidget.kt:19-32` 永远静态文案、零联动、无刷新机制）→ 至少接 `OnDeviceAgent.current.fortune()` 并加 Glance 周期/事件刷新。
- **I10 OnDeviceAgent 命名误导**（`OnDeviceAgent.kt:22-44` 实为 Stub）→ 重命名为 `FortuneAgentRegistry`/`activeAgent` 并 ⚠️ 标注"当前为 Stub，非真实模型"。
- **Enums.kt 死代码**：`WorldType/ItemType/RelationType` 全源码 0 引用，且 `WorldType` 用 `SHINWA` 与全工程 `Shinwa` 字符串约定冲突 → 删除或真正接入并统一枚举契约。
- **魔数 `4` 双写**：`ContentModels.kt:31` 与 `GameContent.kt:30` 的 `maxStage = 4` 无单一来源；`GameContent.add` 形参 `stars` 实际传"最大星级"命名误导 → 提 `const val MAX_BREAKTHROUGH_STAGE = 4` 并改正参名。
- **MilanAudio 失败全静默**（`MilanAudio.kt:142,211,237` `catch(_){-1}`）→ `debug` 构建补 `CrashReporter.traceNonFatal` 便于排障。

### 4.3 性能 / 交互
- **I7 列表 filterSort 无 remember**（`CollectionScreen.kt:88`/`CharacterListScreen.kt:79`，`ListFilterBar.kt:48-74` 内 3×filter+sort+拷贝）→ 每次重组全量重算；包进 `remember(filters…)`。
- **I8 LRU 容量核算 O(n²)**（`PortraitLoader.kt:79-95` 每次 put 全表 `sumOf`，驱逐 k 个时 k×n）→ 维护运行时字节计数器（标准 `LruCache` 做法）。
- **I11 传感器非生命周期感知 + 每事件装箱**（`GpuEffects.kt:231-259` `DisposableEffect(Unit)` 仅组合退出才注销，App 切后台仍采样；每次回调 `Pair` 触发重组）→ `repeatOnLifecycle(RESUMED)` 内注册 + 独立 `mutableFloatStateOf`。
- **I12 超长 Composable**：`SettingsScreen`(162 行)/`DeckScreen`(155)/`CollectionCard`(102) 应拆分（Section/Audio/Data/About/ResetDialog；DeckPreviewOverlay）。
- **I11(补充) `Resources.getIdentifier` 每滚入反射查表**（`PortraitImage.kt:99-101` 主线程组合期，Lazy 网格每次重查）→ 提到 `PortraitLoader` 做进程级 `ConcurrentHashMap` 记忆化。
- **I4(补充) 无障碍**：用全角空格模拟字距（TalkBack 逐字断读，如 `DeckScreen.kt:65 "卡 组"`）、可点区缺 `Role.Button`/48dp（ListFilterBar chip）、`UIComponents.kt:183 (size.value*0.42f).sp` 把 dp 当 sp 用 → 改 `letterSpacing`、补 `semantics{role=Role.Button}`+`minimumInteractiveComponentSize()`、字号传 `TextUnit`。
- **I13 防重入缺失**（I11 上）：购买/兑换/开关只按余额禁用，无 in-flight 态 → 快速双击连开两包；补 `busy` 状态。

---

## 5. 重复代码与公共组件重构建议（一次性消除 ~250 行重复）

| 重构 | 涉及 | 处置 |
|---|---|---|
| **R1** `CharacterCard` | `DeckScreen.kt:214` / `CharacterListScreen.kt:147` / `CollectionScreen.kt:255` | 抽单一 `CharacterCard(characterId,name,title,rarity,element, locked, animatedVisibilityScope, onClick, footer)`，差异走参数；`DeckCard` 删除并补回 sharedBounds，`ListCard/CollectionCard` 收敛 |
| **R2** `MainTabScaffold` | 5 处 `GameNavBar` 挂载（`DeckScreen:119`/`ShopScreen:135`/`SettingsScreen:175`/+Home/Gacha） | 上提到 `MainActivity.MilanNavHost` 的 `Scaffold`，一并解决底栏参与页面过渡问题；统一 `padding(10.dp,6.dp)` |
| **R3** `MilanFeedback` + `WriteOutcome.toMessage` | 4 处 Toast 写法 | `LocalFeedback` CompositionLocal 提供一次 SnackbarHost（配 AppTheme 配色）；`fun WriteOutcome.toMessage(success,rejected)` 收口三处 `when` |
| **R4** 其它 | `rarityName`×3（AppTheme/Collection/List）、`RarityChip`/`DangerButton`/`TabularText` 零调用、空态块×3、千分位×2 口径（`ShopScreen:224` vs `AppChrome:135`） | 删 / 回归调用点 / 抽 `EmptyState` `formatCount` |

---

## 6. Minor 速览（低风险，可随手搭车）

| # | 文件:行 | 问题 | 建议 |
|---|---|---|---|
| M1 | DeckScreen:60 / SettingsScreen:63 | `remember` 而非 `rememberSaveable`，旋转丢浮层 | 改 `rememberSaveable` |
| M2 | DeckScreen:129-135 | 遮罩 `.clickable{}` 未传 `indication=null`，整屏水波纹 | `clickable(interactionSource, indication=null)` |
| M3 | ShopScreen:202,215 | 参数名 `yield` 与 `SequenceScope.yield` 同名 | 改 `gain` |
| M4 | ShopScreen:157 | 内联全限定 `androidx.compose.ui.graphics.Color` | 补 import |
| M5 | ShopScreen:86-117 | 两次手写 `FragmentPackCard(1/2)`、`fragmentPackCost` 重算 | `for(pack in 1..2)` + `remember` |
| M6 | ShopScreen:178 | `GlassPanel(gold = pack==2)` 样式绑业务 | 改 `highlighted: Boolean` |
| M7 | AppChrome:135 vs ShopScreen:224 | 数字格式化两口径 | 抽 `formatCount()` |
| M8 | GameNavBar:9,166 | 已 import `Arrangement` 却用全限定 | 直接用 |
| M9 | GpuEffects:32,19-21 | 未用 import `kotlin.math.max`、import 顺序 | 清理 |
| M10 | GpuEffects:144,179,216 | `catch(_:Exception)` 吞 `CancellationException` | 改 `Throwable` 并 rethrow cancel |
| M11 | GpuEffects:52 | AGSL 死变量 `uv` | 删除 |
| M12 | AuraHalo:50-61 | `rotate(0f,…)` 零度空转 + 全限定内联 | 去掉/补 import |
| M13 | ListFilterBar:157-161 | `ChipRow` 纯转发壳且 `rarity` 参数未用 | 删除直接用 `FilterChip` |
| M14 | PageComponents:85 | `contentDescription` 靠字形反推 | 显式参数 |
| M15 | PageComponents:91 / UIComponents:49 | 硬编码色绕过 `AppTheme` | 提 `AppTheme.Scrim…` |
| M16 | Routes:43 | `toNavRoute(): Any` 放弃编译期校验 | 收 `sealed interface TabRoute` |
| M17 | AppChrome:70 | `AppTopBar` 标题无 `maxLines` | 补 |
| M18 | GachaScreen:179 | composable 内直读 `saveData.softCurrency` 可变快照未订阅 | 经 `snapshot` 读取 |
| M19 | ProgressionEngine:30 / EconomyFormulas | `expForLevel` 可改为 `cumulativeExp(l+1)-cumulativeExp(l)` 彻底 DRY | 可选 |
| M20 | PullResult:81 `rarity:Int=0` 哨兵 | 用 `Rarity?` 或显式 `Rarity.R` | 对齐领域枚举 |

---

## 7. 优先级排序与执行路线（建议）

**P0 — 稳定性与功耗（本迭代必修，约 1–2 天）**
1. **C1** 动画门控 + 低端机/省电降级（影响面最广、可感知最强）
2. **C2** OOM 兜底（`Throwable` + traceNonFatal）
3. **C4** CrashReporter 两处主线程 IO 异步化
4. **C3** 单飞竞态一行修
5. **C5** 内存回调注册上移 `MilanApp`
6. **C6** 预览层 `BackHandler`

**P1 — 架构收敛（下迭代，收益最高，约 3–5 天）**
7. **C7 + I6** 内容单一真相源：决策主/兜底边界 + `displayName` 断言 + GameContent 职责拆分
8. **C8** PityCounter 保底类型改 `Rarity`
9. **R1 + I1 + I2** 抽 `CharacterCard`，清 `rarityName` 双副本，删/回归死公共件
10. **R3 + I4 + I13** 统一 Snackbar 宿主 + `WriteOutcome.toMessage` + in-flight 防重入
11. **I3 + I5** 主题单一色源 + `GameSnapshot` 补设置字段
12. **R2 + I12 部分** `MainTabScaffold` 上提底栏

**P2 — 质量与可维护性（约 3–5 天）**
13. **I7** 列表 `filterSort`/聚合补 `remember`
14. **I8 + I11** LRU 字节计数器 + `getIdentifier` 进程级记忆化
15. **I9(补充) + I10** 传感器生命周期感知 + 去装箱
16. **I9/I10/Enums** Glance 联动、OnDeviceAgent 重命名、Enums 死代码决断
17. **I12** 拆分超长 Composable

**P3 — 清理项（随手搭车）**
18. M1–M20 命名/import/死变量/无障碍/`rememberSaveable` 补齐等

---

## 8. 下一步建议

1. **先修 P0 的 6 项 Critical**（全部为确定性稳定性/功耗缺陷，多数一行到数十行改动，风险低收益高），建议在真机（尤其低端机 + ColorOS/Android 旧版本）跑整机验证。
2. **P1 的 C7（内容双份真相源）是长期维护炸弹**，需在决策"主内容 vs 兜底边界"后一次性收敛，最好配单测强制 parity。
3. 修复后跑质量门槛：`gradlew :app:testDebugUnitTest` + 关键模块 lint clean，确保不引入回归。
4. 本次审查为只读，未改动任何文件；如需我直接落地 P0 修复，请确认，我将按"先写失败测试再修复"的纪律分批实施。

---

*审查完成。所有结论均基于实际读取的源码与并行专项 Explore 复核，file:line 引用真实可查。*
