# AGENTS.md — Milan（Kotlin / Jetpack Compose 原生 Android 抽卡游戏）

> 项目愿景、玩法设计、分层架构详见 [CLAUDE.md](CLAUDE.md)。设计文档在 `docs/superpowers/specs/`。
> 2026-08-07 起从 .NET 10 版（MauiMilan/，已删除）全量重写为 Kotlin + Compose；旧版仅存于 git 历史。

## 构建与测试（环境事实）

- 工程在 `MilanKotlin/`（Gradle 9 系 + AGP 9.3.0 + Kotlin 2.4.10，AGP 9 内置 built-in Kotlin，不再应用 kotlin-android 插件），用 wrapper，无需本地安装 Gradle：
  ```powershell
  .\gradlew.bat :app:assembleDebug          # 构建 Debug APK
  .\gradlew.bat :app:assembleRelease        # 构建 Release APK（minify+shrinkResources）
  .\gradlew.bat :app:testDebugUnitTest      # 运行单测（JUnit4 + kotlinx-coroutines-test）
  ```
- 产物：`MilanKotlin/app/build/outputs/apk/debug/app-debug.apk`（~90MB）/ `release/app-release.apk`（~49MB）。需 JDK 17+（PATH 上有 Temurin 17 即可）。
- **DSH 沙箱环境专用**：`%USERPROFILE%\.gradle` 与 `%USERPROFILE%\.android` 不可写，必须用
  `pwsh -NoProfile -File .\run-gradle.ps1 <gradle 参数>`（内部把 GRADLE_USER_HOME / ANDROID_USER_HOME
  重定向到 workspace 内 `.gradle-home/`、`.android-home/`，两者已入根 .gitignore）。Kotlin daemon
  标记写入 `%LOCALAPPDATA%\kotlin\daemon` 被拒会自动回退 in-process 编译（有噪音，构建仍成功）。
- 版本号集中在 `MilanKotlin/gradle/libs.versions.toml`（AGP / Kotlin / Compose BOM 2026.06.01 / kotlinx-serialization 1.11.0 / coroutines 1.11.0 / navigation-compose 2.9.8 / media3 1.11.0 / glance 1.1.1 / lifecycle 2.11.0）；`minSdk=29, targetSdk=37, compileSdk=37`（compileSdk 37 为 BOM 2026.06.01 的 ui 1.12.0-alpha03 强制要求），JVM target 17。
- Release 构建开 minify + shrinkResources。`MilanKotlin/app/proguard-rules.pro` 除 kotlinx.serialization 规则外，**必须保留 WorkManager keep 规则**（`androidx.work.impl.WorkDatabase_Impl` + `ListenableWorker` 构造器）：AGP 9 R8 严格化会把反射实例化的 WorkDatabase_Impl 裁掉，导致 release 启动闪退 `Failed to create an instance of androidx.work.impl.WorkDatabase`（debug 正常；Google Issue 348590028，2026-02 社区 workaround）。

## Compose 编译陷阱（高频踩坑）

- **`withFrameNanos` import 必须是 `androidx.compose.runtime.withFrameNanos`**，不是 `kotlinx.coroutines.withFrameNanos`（后者不存在）。错误 import 不会立即报错，直到 LaunchedEffect 内调用才报 `Unresolved reference`。
- **GraphicsLayerScope 属性名遮蔽**：`graphicsLayer { scaleX = x }` 里的 `scaleX`/`scaleY`/`alpha`/`translationY` 是隐式 receiver 成员。如果外部有同名 `val`/`var`，Kotlin 隐式规则会优先绑定局部变量，导致 `'val' cannot be reassigned`。解决方案：重命名外部变量（如 `scaleX` → `animX`，`alpha` → `cardAlpha`）。
- **`val x by mutableFloatStateOf(0f)` 不能赋值**：委托属性用 `val` 声明时，即使委托有 `setValue`，编译器也会拒绝 `x = ...`。动画时间线等场景必须用 `var x by remember { mutableFloatStateOf(0f) }`。
- **`matchParentSize` 不需要 import**：它是 `BoxScope` 的成员修饰符，在 BoxScope 内部直接用 `Modifier.matchParentSize()`，import 反而报 `Unresolved reference`。

## 代码红线（动它们会破坏构建/运行）

- **领域层纯净性（2026-08-13 KMP 下沉后）**：领域层已迁入 **`:shared` 模块的 `commonMain`**
  （`shared/src/commonMain/kotlin/com/milan/game/`：`domain/gacha|progression|battle` + `data/Rarity.kt`），
  跨端共用、**禁止 `import android.*`**；app 依赖 `implementation(project(":shared"))`。
  `data/`（存档模型 + SaveManager）、`infrastructure/eventbus/` 仍在 app 且同为纯 Kotlin。
  Android 依赖只允许出现在接入层：`MainActivity.kt`、`MilanApp.kt`、`data/AndroidSaveProvider.kt`、`ui/` 可绘制部分。
  SaveProvider 是接口，Android 实现注入。随机源一律 `kotlin.random.Random`（KMP 可移植；`java.util.Random` 已清除）。
- **`shared/.../domain/progression/EconomyFormulas.kt` 是养成数值的单一事实来源**——任何「升级/突破/升星/重复碎片」公式必须调用它，禁止就地写数字（源码注释明示铁律；桌面模拟器与 App 共用同一份）。
- 货币/养成写操作是事务范式：先预算/校验可支付 → 改内存 → 落盘；落盘失败回滚本次内存改动并返回非 Success（`WriteOutcome`，见 `services/WriteOutcome.kt`），回滚路径**不广播事件**。写操作统一走 `GameService.transaction(tag, mutate, rollback, onCommit)` 模板；`pull` 返回 `PullOutcome`。UI 刷新优先订阅 `GameService.snapshot`（StateFlow）而非 EventBus 轻标记。
- **写操作全部为 `suspend`（2026-08 主线程 IO 异步化）**：内存变更与落盘在 `writeMutex`（串行 Mutex）临界区内完成，落盘经 `Dispatchers.IO`，主线程不阻塞；回滚与落盘判定同临界区同步完成。UI 侧必须在协程（`rememberCoroutineScope().launch` / `LaunchedEffect`）中调用；单元测试用 `runTest`。持锁路径（如 `pull`）内部走 `transactionLocked`（Mutex 不可重入，勿再套 `transaction`）。
- `SaveManager` 载入**永不抛异常**：主档损坏先试 `.bak`/`.tmp` 备份，全失败才回默认档并走 `onTrace` 留痕（对应 CrashReporter）。`GameState` 初始化里抛异常 = App 永久打不开（MilanApp 的 catch 只是兜底）。
- **不要改动序列化结构**：`@Serializable` 存档模型与 `ContentModels` 的 `@SerialName` 对齐 data.json 的 PascalCase 键（对齐 C# 的 IncludeFields 语义），改键名 = 存档/内容全丢。
- EventBus `publish` 只入队，必须由宿主定期 `dispatch` 才真正派发（队列上限 512）；订阅时传 `owner` 以便 `unsubscribeAll` 批量退订；handler 抛异常走 `handlerException` 留痕，不炸线程。

## 架构要点

- **单 Activity**：`MainActivity` + Navigation Compose 2.9 类型安全路由（`NavHost` + `ui/nav/Routes.kt` 的 `@Serializable` 路由类，替代早期自研状态路由；`RoutesTest` 覆盖 `NavItem.toNavRoute()` 映射）。底部 5 tab：`Home 主页 / Gacha 抽卡 / Deck 卡组 / Shop 商店 / Settings 设置`（`ui/nav/GameNavBar.kt` 的 `NavItem` 枚举，Material 标准图标）。子页盖住 tab：神谱图鉴（占位）→ 我的角色（CharacterListScreen）→ 角色详情（CharacterDetailScreen）→ 角色养成（ProgressionScreen），顶栏返回/系统返回（Predictive Back）逐层退出。立绘共享元素过渡：`SharedTransitionLayout` 包 `NavHost`（作用域经 `ui/SharedTransitionLocals.kt` 的 `LocalSharedTransitionScope` 注入）。
- `ui/GameState.kt` 是**进程级单例**（`ensureInitialized` 幂等、双检锁），持有唯一 `GameService`；访问 `service` 前必须先初始化。UI 从这里取最新状态，不要另建 service。
- `MilanApp`（Application）启动顺序敏感：`CrashReporter.install` → `beginBootTrace` → `GameState.ensureInitialized(saveProvider = AndroidSaveProvider, contentJson, onTrace)`。
- 分层（2026-08-13 KMP 下沉后）：`ui/` → `services/`（GameService）→ **`:shared` commonMain**（`domain/` battle/gacha/progression + `data/Rarity`）→ `data/`（存档，app 内）+ `infrastructure/`（EventBus、CrashReporter）。桌面/未来 iOS 与 App 共用同一份领域实现（`desktopApp` 即演示）。
- 代码注释常带「C# 某某翻译」对照标注（从 .NET 版迁移而来），历史坑因注释请保留，改相关代码前先读。

## 测试（app/src/test/java/com/milan/game/ 与 shared/commonTest）

- 覆盖：`SaveDataTest` / `SaveManagerTest` / `BattleSimulatorTest` / `GachaEngineTest` / `PityCounterTest` / `EconomyFormulasTest` / `ProgressionEngineTest` / `TalentEngineTest` / `EventBusTest` / `DataJsonContentTest` / `PortraitLoaderTest` / `RoutesTest`（JUnit4 + coroutines-test）。领域类虽迁入 `:shared`，领域测试仍留在 app 测试集（经 `implementation(project(":shared"))` 解析），后续可逐步下沉 commonTest。
- 领域引擎都支持注入 seed（`kotlin.random.Random`）保证确定性；新增领域逻辑请配套单测。
- `GameServiceTest`（app/src/test/java/com/milan/game/services/GameServiceTest.kt）覆盖服务层（抽卡/货币/养成/战绩，写操作断言 `WriteOutcome`/`PullOutcome` 类型化结果）；`AffinityLoopServiceTest.kt` 覆盖好感度完整闭环（赠送净扣净加/满级拒绝/上限钳位/战斗胜利全员 +20 与回滚，净变动断言口径）；`FormationTowerServiceTest.kt` 覆盖编队 + 爬塔结算（胜负/奖励/回滚/经济永动机回归）；`RoutesTest` 覆盖类型安全路由的 `NavItem.toNavRoute()` 映射与 `@Serializable` 序列化往返（纯 Kotlin 逻辑）。UI 层 Compose 渲染测试（Robolectric + Compose UI Test，`@Config(sdk = [34])`）：`ComposeUiSmokeTest` / `TowerResultCardTest` / `GachaDeckScreenTest` 等。**⚠️ 单例竞态**：Robolectric 每个测试类都会实例化 MilanApp，其 `onCreate` 后台异步注入真实 data.json，会与测试 `@BeforeClass` 的注入竞争进程级单例 `GameState`——全量套件下真实内容先赢。**触碰 GameState 的测试类必须先在 @BeforeClass/@Before 调 `GameState.resetForTest()`（internal，仅测试可用）再注入，即可顺序无关**；文本断言仍建议内容无关（取 `GameState.service.pools.first().displayName` 做存在性断言），勿硬编码 testContent 字符串；CJK 文本在 Robolectric 字体度量下可能被量成近零宽，`assertIsDisplayed` 易误报。
- `:benchmark`（Macrobenchmark，2026-08-13 修复并接入 settings）需真机/模拟器执行 `:benchmark:benchmarkRelease`；`:app` 已配 `benchmark` buildType。

## Android 注册与内容数据

- manifest（`MilanKotlin/app/src/main/AndroidManifest.xml`）声明 `MainActivity`（launcher）+ `MilanApp`（Application）；没有其他 Activity。
- 内容数据主来源：`MilanKotlin/app/src/main/assets/data.json`（打包为 asset，与旧版同构）；`MilanApp` 启动时读取并传入 `GameService`，`services/GameContent.kt` 是**代码内兜底副本**（不是主来源），data.json 缺失/损坏/无有效角色时静默回退。**两条加载路径都必须经过 `GameContent.enrich` 补派生字段（#31 已实现）。**
- 立绘：`res/drawable/char_<rarity>_<拼音>.webp`（R×7 / SR×8 / SSR×6 / UR×7，已从旧版全量迁入并转 WebP）；`PortraitImage` 用 `getIdentifier` 探测 + IO 线程解码，缺失渲染占位（稀有度渐变 + 首字）。**武器图：`assets/weapons/<vfx>.webp`**（28 张，已从旧版迁移并转 WebP）——`CharacterDetailScreen` 用 `context.assets.open("weapons/$weaponVfx.webp")` 在 IO 线程按 2x 采样加载，缺失回退显示武器名。
- 稀有度 `R=1, SR=2, SSR=3, UR=4`；世界 `Shinwa / Aether / Ironveil`。重复抽卡补偿碎片走 `EconomyFormulas.FragmentsForRarity`（UR 50 / SSR 20 / SR 5 / R 1）。
- 元素体系（2026-08-11 定稿）：金木水火土光暗电 8 种，键名沿用旧数据标识（Metal=金 / Wood=木 / Water=水 / Flame=火 / Earth=土 / Light=光 / Shadow=暗 / Thunder=电）；旧 Wind/Frost/Void/Star 数据值已并入 Wood/Water/Shadow/Light（data.json 与 GameContent 兜底同步收敛）。

## 其他约定

- 注释与 UI 文案全中文；代码内注释常含历史坑因（如 GameContent 的兜底语义、EventBus 的队列上限），改相关代码前先读。
- 主题色在 `ui/theme/`（`AppTheme.kt` / `ElementTheme.kt` / `Theme.kt`）。
- 仓库历史：Unity 版（`Assets/_Project/`）→ .NET 10 MAUI 版（`MauiMilan/` + `Tests/`）→ Kotlin/Compose 版（当前，`MilanKotlin/`）。前两版及其 .NET/Unity 残留（dotnet 脚本、build_*.log、weapon_assets 等）已于 2026-08-07 删除，需要对照旧逻辑查 git 历史。
- `docs/superpowers/`、`.superpowers/`、`.omo/`、`.omc/` 是规划产物，非源码。
- **立绘美术方案以 `docs/superpowers/art-direction/portraits-v2/` 为准（v2.2，2026-08-23 叙事优先卡牌化）**：`00-master-spec.md` 总规范 + `01~04-character-portrait-*.md` 四档角色稿（UR/SSR/SR/R 各 7 位，共 28）+ `05-quality-baseline.md` 现状基线 + **`06-card-art-composition.md` 卡牌立绘构图/光影/叙事保真规范（注入层据此生成；背景故事符合性为最高优先级，脚部入镜已解禁）**。`character-designs-*.md` 与 `visual-redesign-spec.md` 为 MAUI 时代 v1.0 旧稿，已废弃，勿按生图。`portraits-v2/extract_prompts.py` 解析稿为结构化 prompt，自动注入 LORE 保真段（默认读 `MilanKotlin/app/src/main/assets/data.json` 的 Lore）与卡牌化指令层，导出 `prompts-export.json/csv`；`generate_portraits.py` 可调用生图 API（mock/openai/flux）批量产出 `char_<rarity>_<拼音>.webp`（透明背景、阶梯画布，与 `PortraitLoader` 命名对齐）。
