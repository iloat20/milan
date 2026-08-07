# AGENTS.md — Milan（Kotlin / Jetpack Compose 原生 Android 抽卡游戏）

> 项目愿景、玩法设计、分层架构详见 [CLAUDE.md](CLAUDE.md)。设计文档在 `docs/superpowers/specs/`。
> 2026-08-07 起从 .NET 10 版（MauiMilan/，已删除）全量重写为 Kotlin + Compose；旧版仅存于 git 历史。

## 构建与测试（环境事实）

- 工程在 `MilanKotlin/`（Gradle 8 系 + AGP 8.10.1 + Kotlin 2.1.20），用 wrapper，无需本地安装 Gradle：
  ```powershell
  .\gradlew.bat :app:assembleDebug          # 构建 Debug APK
  .\gradlew.bat :app:testDebugUnitTest      # 运行单测（JUnit4 + kotlinx-coroutines-test）
  ```
- 产物：`MilanKotlin/app/build/outputs/apk/debug/app-debug.apk`。需 JDK 17+（`gradle.properties` 里配了 `org.gradle.java.home` 则不用 PATH）。
- 版本号集中在 `MilanKotlin/gradle/libs.versions.toml`（AGP / Kotlin / Compose BOM 2025.05.00 / kotlinx-serialization 1.8.1 / coroutines 1.10.1）；`minSdk=29, targetSdk=36, compileSdk=36`，JVM target 17。

## 代码红线（动它们会破坏构建/运行）

- **领域层纯净性**：`domain/`、`data/`（模型）、`infrastructure/eventbus/` 是纯 Kotlin，**禁止 `import android.*`**。Android 依赖只允许出现在接入层：`MainActivity.kt`、`MilanApp.kt`、`data/AndroidSaveProvider.kt`、`ui/` 可绘制部分。SaveProvider 是接口，Android 实现注入。
- **`domain/progression/EconomyFormulas.kt` 是养成数值的单一事实来源**——任何「升级/突破/升星/重复碎片」公式必须调用它，禁止就地写数字（源码注释明示铁律）。
- 货币/养成写操作是事务范式：先预算/校验可支付 → 改内存 → 落盘；落盘失败回滚本次内存改动并返回 `false`（`SpendXxx`/`AddXxx` 返回值必须被处理），回滚路径**不广播事件**。
- `SaveManager` 载入**永不抛异常**：主档损坏先试 `.bak`/`.tmp` 备份，全失败才回默认档并走 `onTrace` 留痕（对应 CrashReporter）。`GameState` 初始化里抛异常 = App 永久打不开（MilanApp 的 catch 只是兜底）。
- **不要改动序列化结构**：`@Serializable` 存档模型与 `ContentModels` 的 `@SerialName` 对齐 data.json 的 PascalCase 键（对齐 C# 的 IncludeFields 语义），改键名 = 存档/内容全丢。
- EventBus `publish` 只入队，必须由宿主定期 `dispatch` 才真正派发（队列上限 512）；订阅时传 `owner` 以便 `unsubscribeAll` 批量退订；handler 抛异常走 `handlerException` 留痕，不炸线程。

## 架构要点

- **单 Activity**：`MainActivity` + `MilanNavHost`（Compose Navigation 的等价物，纯状态路由）。底部 5 tab：`Home 主页 / Gacha 抽卡 / Deck 卡组 / Shop 商店 / Settings 设置`（`ui/nav/GameNavBar.kt` 的 `NavItem` 枚举）。子页盖住 tab：神谱图鉴（占位）→ 我的角色（CharacterListScreen）→ 角色详情（CharacterDetailScreen）→ 角色养成（ProgressionScreen），顶栏返回逐层退出。
- `ui/GameState.kt` 是**进程级单例**（`ensureInitialized` 幂等、双检锁），持有唯一 `GameService`；访问 `service` 前必须先初始化。UI 从这里取最新状态，不要另建 service。
- `MilanApp`（Application）启动顺序敏感：`CrashReporter.install` → `beginBootTrace` → `GameState.ensureInitialized(saveProvider = AndroidSaveProvider, contentJson, onTrace)`。
- 分层：`ui/` → `services/`（GameService）→ `domain/`（battle/gacha/progression）+ `data/`（存档）→ `infrastructure/`（EventBus、CrashReporter）。
- 代码注释常带「C# 某某翻译」对照标注（从 .NET 版迁移而来），历史坑因注释请保留，改相关代码前先读。

## 测试（app/src/test/java/com/milan/game/）

- 覆盖：`SaveDataTest` / `SaveManagerTest` / `BattleSimulatorTest` / `GachaEngineTest` / `PityCounterTest` / `EconomyFormulasTest` / `ProgressionEngineTest` / `TalentEngineTest`（JUnit4 + coroutines-test）。
- 领域引擎都支持注入 seed（`kotlin.random.Random`）保证确定性；新增领域逻辑请配套单测。Service 层与 UI 层目前无测试。

## Android 注册与内容数据

- manifest（`MilanKotlin/app/src/main/AndroidManifest.xml`）声明 `MainActivity`（launcher）+ `MilanApp`（Application）；没有其他 Activity。
- 内容数据主来源：`MilanKotlin/app/src/main/assets/data.json`（打包为 asset，与旧版同构）；`services/GameContent.kt` 是**代码内兜底副本**（不是主来源），data.json 缺失/损坏时静默使用。**注意现状**：`MilanApp` 目前传 `contentJson = null`（data.json 尚未接入），实际内容走兜底；接上后两条加载路径都必须经过 `GameContent.enrich` 补派生字段（#31）。
- 立绘：`res/drawable/char_<rarity>_<拼音>.png`（R×7 / SR×8 / SSR×6 / UR×7，已从旧版全量迁入）；**武器图：`assets/weapons/<vfx>.png`**（28 张，已从旧版迁移）——`CharacterDetailScreen` 用 `context.assets.open("weapons/$weaponVfx.png")` 加载，缺失回退显示武器名。
- 稀有度 `R=1, SR=2, SSR=3, UR=4`；世界 `Shinwa / Aether / Ironveil`。重复抽卡补偿碎片走 `EconomyFormulas.FragmentsForRarity`（UR 50 / SSR 20 / SR 5 / R 1）。

## 其他约定

- 注释与 UI 文案全中文；代码内注释常含历史坑因（如 GameContent 的兜底语义、EventBus 的队列上限），改相关代码前先读。
- 主题色在 `ui/theme/`（`AppTheme.kt` / `ElementTheme.kt` / `Theme.kt`）。
- 仓库历史：Unity 版（`Assets/_Project/`）→ .NET 10 MAUI 版（`MauiMilan/` + `Tests/`）→ Kotlin/Compose 版（当前，`MilanKotlin/`）。前两版及其 .NET/Unity 残留（dotnet 脚本、build_*.log、weapon_assets 等）已于 2026-08-07 删除，需要对照旧逻辑查 git 历史。
- `docs/superpowers/`、`.superpowers/`、`.omo/`、`.omc/` 是规划产物，非源码。
