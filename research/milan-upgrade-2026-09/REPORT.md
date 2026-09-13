# Milan 项目升级点全景报告（2026-09-11）

> Generated 2026-09-11 · depth: deep · 6 并行角度 + 1 次本地门禁复核 · workspace: research/milan-upgrade-2026-09/

## Executive summary

1. **技术栈整体较新，不必恐慌升级。** lifecycle / activity-compose / core-ktx / coroutines / serialization / compileSdk 37 已是最新稳定；可低风险批量：Kotlin 2.4.20、Compose BOM 2026.09.00、AGP 9.3.2、Media3 1.11.1 [1][2][3][4][5][6][7]。
2. **R7「4 P0 + 12 P1」清单已过时。** 对照当前树核验：Theme 竞态、签到连刷、深渊无限刷星尘、战斗 VM 协程覆盖均已修，并有回归测试；真正残留的是 **深渊结算奖励在锁外计算（TOCTOU）** 与 **data.json 状态天赋 Chance/Duration 债务** [20][21]。
3. **`:app:checkArchitecture` 实测已通过**（2026-09-11 本地跑通），R7 记录的红灯不再是当前债；但门禁本身仍是正则级，未扫 `:core`/`:data`，也未挂进 Gradle `check` [22][23]。
4. **最大产品缺口不是缺新玩法，而是 360° 检视仍死功能。** `InspectionApi` 全量实现但 UI 零调用；角色详情明确未迁移旧版 Parallax3D 视差立绘——这与项目核心定位「抽→养→检视→战」直接冲突 [36][37]。相对地，剧情战役（ch01–09）、图鉴、竞技场、活动、装备已接线 [38][39][40]。
5. **资源包体约 72.5MB**（立绘 50.5MB + 武器 11.5MB + 字体 8MB），且 `data.json.bak`、`.bak/*.webp.20260906` 可能打进包；无 AAB 任务 [28][29]。
6. **冷启动收益未兑现**：baseline-prof 是 72 行手写桩（「生成工具待接入」），而 profileinstaller 已接好；`:benchmark` 变体未开 minify，测到的不是生产 R8 路径 [30][31][32]。
7. **测试全部堆在 `:app`**（63 类 / ~445 `@Test`），`:core`/`:data`/`:shared` commonTest / androidTest 为零；仅 5 个 Robolectric Compose 类盖 ~93 个 UI 文件，且强制 `sdk=34` [24][25][26]。**全仓库无 CI** [27]。
8. **模块半成品**：`:data` 声明为 Android library 却零 Android API；`:core` 把纯服务与 Media3/WorkManager/CrashReporter 搅在一起；`:shared` 只有 `jvm()` target；EventBus 生产侧零订阅仍双通道通知 [17]。
9. **推荐路线**：先「产品 keep/kill + 检视复活 + 包体/基线性能」三条主线并行，再做架构收口与 CI；技术栈 patch/minor 放最前当扫雷（成本最低）。
10. **不要按 2026-09-08 架构 proposal 的 B1–B4 诊断表开工**——GameService 已 406 行 + `by` 委托，BattleUnitState 已不可变，prettyPrint 已关，Deck 域模型已存在；从当前树出发 [17]。

## Background & scope

Milan 是 Kotlin/Compose 多模块 Android 抽卡游戏（`:app` `:core` `:data` `:shared` `:desktopApp` `:benchmark`）。本报告回答：在 2026-09-11 时点，整仓「哪里值得升级」——技术栈、架构、缺陷债、测试门禁、性能资源、产品完整度——并给出可执行优先级。基线版本读自 `MilanKotlin/gradle/libs.versions.toml`：AGP 9.3.0 · Kotlin 2.4.10 · Compose BOM 2026.08.00 · Gradle 9.5.0 · compileSdk/targetSdk 37 · minSdk 29。不产出代码修复；历史审查文档与当前树冲突时以代码为准。

---

## 1. 技术栈：分三批升，别一次梭哈

当前 pin 与 2026-09 官方最新稳定对照（本地 toml + Maven/GitHub 主源）：

| 组件 | 当前 | 最新稳定 (2026-09-11) | 建议 |
|------|------|----------------------|------|
| Kotlin | 2.4.10 | 2.4.20 (09-07) | **A 批 drop-in** [2] |
| Compose BOM | 2026.08.00 | 2026.09.00 (ui 1.12.1 / m3 1.4.0) | **A 批** [5] |
| AGP | 9.3.0 | 9.3.2 / 9.4.0 | **A：9.3.2**；B 再考虑 9.4.0 [4] |
| Media3 | 1.11.0 | 1.11.1 | **A 批** [7] |
| Gradle wrapper | 9.5.0 | 9.7.1 | **B 批**（Isolated Projects / 配置缓存）[3] |
| Navigation Compose | 2.9.8 | 2.10.1 | **B 批** [8] |
| WorkManager | 2.10.0 | 2.11.2 | **B 批**（保留 WorkDatabase_Impl keep）[9] |
| Glance | 1.1.1 | 1.2.0 | **B 批** [10] |
| Macrobenchmark | 1.4.1 | 1.5.0 | **B 批** [12] |
| Robolectric | 4.16.1 | 4.17 (SDK 37) | **C：先审测试再升**（breaking：AndroidVersions 移除）[11] |
| lifecycle / activity / core-ktx | 2.11.0 / 1.13.0 / 1.19.0 | 同左 | **无需动** [6] |
| coroutines / serialization | 1.11.0 / 1.11.0 | 同左（1.12.0 仅 RC） | **serialization 等稳定**（异常包裹语义变更）[13][14] |
| compileSdk/targetSdk | 37 | 37 | **本季度不动** [15] |

**推荐批次**
- **A（本周可做）**：Kotlin 2.4.20 + BOM 2026.09.00 + AGP 9.3.2 + Media3 1.11.1 → 全量单测 + assembleDebug。
- **B（下个迭代）**：Gradle 9.7.1 → AGP 9.4.0 → Nav 2.10.1 / Work 2.11.2 / Glance 1.2.0 / Benchmark 1.5.0；跑一次 SharedTransitionLayout 与 WorkManager R8 启动。
- **C（谨慎）**：Robolectric 4.17（解锁真 SDK 37 UI 测试）需先清 `AndroidVersions`/Shadow API；serialization 等 1.12.0 正式版。

未验证点：AGP 9.4.0 对内置 Kotlin/R8 的具体要求（developer.android.com 当时不可达）[16]。

---

## 2. 架构：半拆分的模块图 + 双通知通道

### 2.1 模块边界

- `:data` 是 `com.android.library`，但源码零 `android.*` —— 插件与内容不一致；`:core` 反把纯业务与 Media3/WorkManager/CrashReporter 混装 [17]。
- 2026-09-08 proposal 里的 `:core:content` / `:feature:*` 图从未落地；当前仍是扁平 `:core`+`:data` [17]。
- `:shared` 仅 `jvm()`，注释写「可后续追加 android/ios」——Android 实际以 JVM 产物消费领域层，iOS/JS 仍是空头支票 [17]。

**升级方向（按性价比）**
1. **先修文档与门禁**，再动依赖图：AGENTS.md 仍写「data/infrastructure 在 app」，是活的错误心智模型 [17]；`checkArchitecture` 只 grep `:shared` 的 android.*，不扫 `:core`/`:data`，不挂 `check` [22]。
2. **决定 EventBus 去留**：生产 `subscribe` 为 0，但写锁出口仍 publish+dispatch；UI 全在 StateFlow。保留则写明用途，否则删掉双通道税 [17]。
3. **ServiceCore 瘦身**：KDoc 写「不含业务规则」，实际有装备 roll/套装与 ~160 行硬编码模板——要么改 KDoc 承认，要么下沉到 `EquipmentEngine`（可进 `:shared`）[17]。
4. **ViewModel 收尾**：StoryScreen 仍 `remember(revision)`；这是抽取方案要消灭的反模式 [18]。
5. **中期模块化**：`:data` → 纯 JVM/KMP；`:core` 拆 `core-domain`（纯）与 `core-android`（音频/Worker/Crash）；`feature:*` 按导航目的地切，而不是一次做 16 个。

### 2.2 组合根

AppGraph 优于裸 GameState，但仍是进程级 service locator；GameService 构造函数默认参数自装配 16 域，GameState 仍暴露 currency/owned()。双组合路径会继续诱捕 UI 直连——门禁应扩到编译期（Konsist）而非正则 [19][23]。

---

## 3. 缺陷债：R7 大部分已还清，只剩两颗雷

对 R7 全部 P0 + 主要 P1 逐条对照当前源码：

| R7 条目 | 现状 | 证据 |
|---------|------|------|
| P0-1 Theme 读 AppGraph | **已修**（LocalFontScaleTier + ready 门控） | Theme / MilanNavHost [18][20] |
| P0-2 签到同日 ×7 | **已修**（lastSignDay 锁内拒绝） | DailyCheckInService + 回归测试 [20] |
| P0-3 深渊无限刷 | **门控已修，奖励仍在锁外算** | DungeonService completeAbyssStage [21] |
| P0-4 战斗 VM 旧协程 | **已修**（battleJob cancel） | StrategicBattleViewModel [20] |
| P1 回滚缺口 / lastResetTime / 溢出 / TOCTOU 批 | **基本关闭** | 回归测试 + Long 预算 [20] |
| P1 状态天赋全失效 | **引擎侧兜底**（Chance→Value）；data.json 仍 403 处 Chance=0/Duration=0 | TalentEngine + data.json [20] |

**仍阻断升级的两项**

1. **`completeAbyssStage` TOCTOU**：`prevStars` / `shouldReward` / `softReward` 在 `writeMutex` 外算好再进锁直接加钱；锁内不复检星数。单进程顺序路径目前安全，但阻断「并发写 / 权威服 / 多入口结算」升级。修法：整段决策进 mutate 重读 stars [21]。
2. **内容 schema 债**：状态天赋概率仍写在 Value、Chance/Duration=0；`generate_gamecontent.py` 原样透传 → 重新生成内容会续债。阻断「内容冻结 / 内容流水线」升级（不阻断当前运行时）[20]。

次要：TowerService.sweepTower 经验仍 `Int*Int` 再转 Double（UI 已弃用，重开扫荡前先 Long 护栏）[20]。

**结论**：不要再按 R7 清单排期；把 R7 标为历史，新建 R8 只记 TOCTOU 残留 + 内容 schema + 新问题。

---

## 4. 测试与质量门禁：有量无网

| 维度 | 现状 | 升级 |
|------|------|------|
| 测试位置 | 63 类 / ~445 `@Test` 全在 `:app` src/test；`:core`/`:data`/`:shared`/androidTest = 0 [24] | 领域/经济用例下沉 `:shared` commonTest；服务测试迁 `:core` |
| UI 覆盖 | 5 个 Robolectric 类 vs ~93 UI 文件；`@Config(sdk=[34])`（JDK 17 卡住 35+）[25][26] | Robolectric 4.17 + 或升 JDK 21 → 真 37；关键屏 Roborazzi 金样 |
| 单例竞态 | 文档已写 `resetForTest`，仅 2 文件调用 [26] | 测试基类统一 `@Before` 重置；或测试专用 SaveProvider |
| 架构门禁 | 正则 checkArchitecture，**实测已绿**；不扫 core/data、不进 `check` [22] | Konsist 层规则 + `check.dependsOn` |
| 静态检查 | 无 detekt/ktlint/kover；仅 16 行 lint.xml [27] | detekt 基线接入 `check`；kover 报经济/领域覆盖 |
| CI | **全仓库无** [27] | 最小流水线：`testDebugUnitTest` + `checkArchitecture`/Konsist + assembleDebug |
| 属性测试 | 经济/保底只有示例测 | Kotest property：保底递增、回滚守恒、碎片公式 [27] |
| 截图 | 无 | Roborazzi（贴现有 Robolectric）优于再引 Paparazzi [27] |

**质量栈建议（对照 nowinandroid 参考架构）**：JVM 单测 + Roborazzi 截图 + Macrobenchmark/基线档案 + 少量 instrumented；架构用 Konsist 表达层规则 [23][27]。

---

## 5. 性能与资源：包体和未兑现的基线档案

### 5.1 包体

- `drawable-nodpi` + `assets` 合计 **~72.5MB**；31 张立绘平均 **1.67MB**（最大 `char_ur_zhulong.webp` 3.2MB）；武器 ~11.5MB；字体 8.04MB（马善政 5.59MB 仅两处仪式字）[28]。
- 无 AAB 任务；`nodpi` 也不参与 density split。
- 可能打包装死重量：`assets/data.json.bak`（122KB）、`drawable-nodpi/.bak/char_ur_zhulong.webp.20260906`（~3.6MB）[29]。

**升级动作**：立绘离线重压到 ≤400KB/1024 边 → 字体子集化（fontTools）→ `bundleRelease` → 把 bak 移出 source set 并加打包守卫。

### 5.2 启动与运行时

- data.json/存档已在 IO 异步初始化（注释写 ~50KB，实测 **221KB**）——关键路径不在此 [35]。
- baseline-prof **手写 72 行**，注释「生成工具待接入」；profileinstaller 1.4.1 已在，但覆盖面极窄 → 冷启动收益基本没吃到 [30]。
- `:benchmark` **未开 minify**（只有 release 开）→ StartupBenchmark 非生产路径；且仅 StartupTimingMetric + 中文文案 wait [31][32]。
- 角色列表 `PortraitTarget.Full` 塞 LazyVerticalGrid（设计稿本意 Thumb）→ 解码量 4×，24MB LRU 易打穿 [33]。
- `InkWashPortrait` 每帧新建 RadialGradient + 最多 900 grain 点；Home hero 无限浮动驱动重绘 [34]。
- DamageFloatingText 已改并行，但仍每帧 measure + buildString [34]。
- 武器独立 LruCache(8) 不在 onTrimMemory 链路 [34]。
- 抽卡 idle 多条 `withFrameNanos`/infinite 循环常亮 [34]。

**优先序**：① benchmark 变体对齐 release minify → ② BaselineProfileGenerator 接入 → ③ 列表改 Thumb → ④ 绘制缓存（shader/grain/text measure）→ ⑤ 子集字体 + 重压立绘 + AAB。

---

## 6. 产品完整度：检视是核心缺口，Dungeon/付费要 keep/kill

| 系统 | 状态 | 升级含义 |
|------|------|----------|
| **360° 检视** | API 全实现，UI 零调用；未迁移 Parallax3D [36][37] | **最高优先产品债**——与核心定位冲突 |
| 剧情战役 | ch01–09 + CHOICE + 结局 + 好感外传，**已落地** [38][39] | 做内容量与演出，不是接线 |
| 神谱图鉴 / 活动 / 竞技场 / 装备 | 已接线（竞技场为本地假 PvP）[40] | 收口体验 |
| PvE / Social | **2026-09-06 有意删除** | 勿按 09-04 旧计划「再接线」[41] |
| Dungeon/深渊 | 服务在、UI 零调用，未随 PvE 一起删 [42] | **keep（做成中期 PvE）或 kill** |
| 月卡 / 充值 | `@Deprecated` 死 API；充值是模拟支付 [43] | 离线则接月卡；上架则删假计费 |
| 塔扫荡 / 塔统计 | 死 API [43] | 重开前先修溢出 |
| MediaPipe AI | 非默认 source set，硬禁用 [44] | 远期实验，勿当本季路线 |
| desktopApp | 控制台抽卡模拟器 | 证明 KMP，不是产品面 [44] |

**产品侧三条决策题（需产品拍板，非工程自答）**
1. 检视：独立 Screen，还是并入角色详情做手势英雄舞台？
2. 深渊：作为 Story↔Tower 之间的中核 PvE 重做，还是删除？
3. 付费：长期离线（月卡本地权益）还是准备上架（假计费必须去掉）？

---

## 7. 推荐升级路线图（按依赖排序）

### Wave 0 — 当日可做（低风险、高杠杆）
- 技术栈 A 批（Kotlin 2.4.20 / BOM 2026.09.00 / AGP 9.3.2 / Media3 1.11.1）[1]–[7]
- 更新 AGENTS.md / CLAUDE.md 模块图与 R7 状态；R7 标历史 [17]
- 移出 `data.json.bak` 与 `.bak` 立绘 [29]
- 标注三产品决策题

### Wave 1 — 质量与可测性（1–2 周）
- 最小 CI：`testDebugUnitTest` + `assembleDebug` + `checkArchitecture`
- Konsist 替代/扩展正则门禁，挂进 `check` [22][23]
- detekt 基线；kover 覆盖 `EconomyFormulas`/`PityCounter`/回滚路径
- `resetForTest` 测试基类化；经济不变量属性测试 [26][27]
- 修 `completeAbyssStage` 锁内复检 + 并发回归测 [21]

### Wave 2 — 性能与包体（与 Wave 1 可并行）
- benchmark 变体 minify 对齐 release [31]
- BaselineProfileGenerator（macrobenchmark 旅程：冷启→Home→抽卡→列表）[30]
- CharacterCard → Thumb；InkWash/Damage 文本与 shader 缓存 [33][34]
- 立绘重压 + 字体子集 + `bundleRelease` [28]

### Wave 3 — 产品关键路径（依赖 Wave 0 决策）
- **检视复活**（手势视差/3D 或 Compose 等效）[36][37]
- Dungeon keep/kill 落地
- 内容流水线：生成器写真实 Chance/Duration，data.json 一次性归一 [20]

### Wave 4 — 架构收口
- EventBus 去留；ServiceCore 装备业务下沉 [17]
- StoryScreen VM 收尾；`:data` 去 Android 化；`:shared` 补 android target（若要做真 KMP）[17][18]
- 技术栈 B/C 批

---

## Comparison table（升级路线权衡）

| 路线 | 交付物 | 成本 | 风险 | 不做会怎样 |
|------|--------|------|------|------------|
| 仅刷依赖 A/B 批 | toml+wrapper  bump | 低 | 低 | 落后 1–2 个月安全补丁与工具链 |
| 只做 CI+Konsist | 门禁可重复 | 中 | 低 | 架构债靠人肉审查回潮 |
| 只做包体/基线 | AAB+子集+生成 profile | 中高 | 中（画质/字形） | 安装与冷启动长期垫底 |
| 检视复活 | 核心循环闭环 | 高 | 中（交互） | 产品定位空心化 |
| 大重构 feature 模块图 | `:core:*`/`:feature:*` | 很高 | 高（合并冲突） | 在无 CI 下重构不划算——**应排在 CI 之后** |

---

## Open questions

1. 是否有 Play/国内商店分发目标？决定 AAB、假计费合规与 baseline profile 交付形态。
2. 是否已有硬性性能预算（冷启 ms / 列表帧时间）？决定 Wave 2 验收阈值。
3. 检视视觉目标：回到 C# 时代 Parallax3D，还是 Compose 手势+着色器的「足够 3D」？需设计/技术联合拍板。
4. `checkArchitecture` 绿灯是何时修的、是否引入回归测试——本次仅确认当前为绿，未追溯提交。
5. AGP 9.4.0 官方 release notes 当时不可达，minor 升级前需在可达网络复核。

## Sources

### 本地主源（findings 取证路径）

[1] `MilanKotlin/gradle/libs.versions.toml` + `gradle/wrapper/gradle-wrapper.properties` — 基线版本（accessed 2026-09-11）

[2] Kotlin releases — https://kotlinlang.org/docs/releases.html (published 2026-09-07, accessed 2026-09-11)

[3] Gradle releases — https://github.com/gradle/gradle/releases (published 2026-08-19, accessed 2026-09-11)

[4] AGP maven-metadata — https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml (published 2026-09-10, accessed 2026-09-11)

[5] Compose BOM maven-metadata + POM — https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml (published 2026-09-09, accessed 2026-09-11)

[6] lifecycle / activity-compose / core-ktx maven-metadata — https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/maven-metadata.xml (accessed 2026-09-11)

[7] Media3 maven-metadata — https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml (accessed 2026-09-11)

[8] Navigation Compose maven-metadata — https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/maven-metadata.xml (accessed 2026-09-11)

[9] WorkManager maven-metadata — https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/maven-metadata.xml (published 2026-08-12, accessed 2026-09-11)

[10] Glance maven-metadata — https://dl.google.com/dl/android/maven2/androidx/glance/glance-appwidget/maven-metadata.xml (accessed 2026-09-11)

[11] Robolectric releases — https://github.com/robolectric/robolectric/releases (published 2026-09-10, accessed 2026-09-11)

[12] Macrobenchmark maven-metadata — https://dl.google.com/dl/android/maven2/androidx/benchmark/benchmark-macro-junit4/maven-metadata.xml (accessed 2026-09-11)

[13] kotlinx.coroutines releases — https://github.com/Kotlin/kotlinx.coroutines/releases (published 2026-05-08, accessed 2026-09-11)

[14] kotlinx.serialization releases — https://github.com/Kotlin/kotlinx.serialization/releases (published 2026-09-04, accessed 2026-09-11)

[15] Robolectric compatibility（SDK 37 支持）— https://github.com/robolectric/robolectric/releases (accessed 2026-09-11)

[16] developer.android.com AGP release notes — **本环境不可达**（dead end，记入 open questions）

[17] `AGENTS.md` · `docs/plans/2026-09-08-architecture-optimization-proposal.md` · `MilanKotlin/settings.gradle.kts` · `shared/build.gradle.kts` · `core/.../ServiceCore.kt` · `core/.../EventBus.kt` · `core/.../GameService.kt` · `app/.../di/AppGraph.kt` · `app/.../GameState.kt` · `app/.../story/StoryScreen.kt` — 架构取证（accessed 2026-09-11）

[18] `app/.../story/StoryViewModel.kt` + StoryScreen `remember(revision)` — VM 抽取残留（accessed 2026-09-11）

[19] GameService 构造默认参数自装配 16 域 — `core/.../GameService.kt`（accessed 2026-09-11）

[20] R7 缺陷逐条核验：`core/.../DailyCheckInService.kt` `DungeonService.kt` `MonetizationService.kt` `StoryService.kt` `TowerService.kt` `shared/.../TalentEngine.kt` `app/.../StrategicBattleViewModel.kt` `app/.../theme/Theme.kt` `app/src/test/.../R7P0RegressionTest.kt` + `BUG_REVIEW_R7_2026-09-10.md`（accessed 2026-09-11）

[21] `DungeonService.completeAbyssStage` 锁外奖励决策 — 锁外 TOCTOU 残留（accessed 2026-09-11）

[22] `MilanKotlin/build.gradle.kts` checkArchitecture 任务实现（accessed 2026-09-11）+ 本地执行 `pwsh -NoProfile -File .\run-gradle.ps1 checkArchitecture` → `通过（领域纯净 / VM 接线 / UI 无业务单例直连）` BUILD SUCCESSFUL（2026-09-11）

[23] Konsist — https://github.com/LemonAppDev/konsist (accessed 2026-09-11)

[24] 测试清单：`app/src/test/**/*Test*.kt` = 63；`:core`/`:data`/`:shared` commonTest/`=0`（inventory 2026-09-11）

[25] `ComposeUiSmokeTest.kt` `@Config(sdk=[34])` 注释（JDK 17 限制）（accessed 2026-09-11）

[26] AGENTS.md 单例竞态节 + `GachaDeckScreenTest.kt` resetForTest 注释（2026-09-02）（accessed 2026-09-11）

[27] Now in Android testing — https://github.com/android/nowinandroid#testing · Roborazzi https://github.com/takahirom/roborazzi · Paparazzi https://github.com/cashapp/paparazzi · Kotest property https://kotest.io/docs/proptest/property-based-testing.html · detekt https://detekt.dev/docs/gettingstarted/gradle · ktlint https://github.com/ktlint/ktlint · Robolectric best practices https://robolectric.org/best-practices/（accessed 2026-09-11）；本地无 CI/无 kover 的盘点同日

[28] 本地量测 `app/src/main/res/drawable-nodpi` + `assets/`：TOTAL ~72.5MB；立绘 31 张 50.5MB；字体 8.04MB（2026-09-11）

[29] `assets/data.json.bak` · `drawable-nodpi/.bak/char_ur_zhulong.webp.20260906`（accessed 2026-09-11）

[30] `app/src/main/baseline-prof.txt`（手写 72 行，「生成工具待接入」）+ profileinstaller 依赖（accessed 2026-09-11）

[31] `app/build.gradle.kts`：`release { isMinifyEnabled = true }` vs `benchmark` 无 minify（accessed 2026-09-11）

[32] `benchmark/.../StartupBenchmark.kt`（accessed 2026-09-11）

[33] `CharacterCard.kt` `PortraitTarget.Full` vs `docs/superpowers/specs/2026-08-08-performance-optimization-design.md` Thumb 档（accessed 2026-09-11）

[34] `PortraitImage.kt` InkWashPortrait · `DamageFloatingText.kt` · `WeaponPanel.kt` LruCache · `CyberCharge.kt`/`CyberStage.kt` withFrameNanos（accessed 2026-09-11）

[35] `MilanApp.kt` 异步 init 注释 vs 实测 data.json 221.4KB（accessed 2026-09-11）

[36] `core/.../InspectionApi.kt`「P2-11 死功能：检视/拍照系统 UI 层零调用」（accessed 2026-09-11）

[37] `CharacterDetailScreen.kt`「P2 未迁移：Parallax3DPortraitView…」（accessed 2026-09-11）

[38] `docs/superpowers/specs/2026-09-11-story-campaign-design.md`「状态：已落地」（published 2026-09-11）

[39] `DialogueScreen.kt` 好感选项落账注释（2026-09-02）（accessed 2026-09-11）

[40] `CollectionScreen.kt` · `ArenaScreen.kt` · `EventScreen.kt` · `EquipmentApi.kt`（accessed 2026-09-11）

[41] `MilanNavHost.kt`「PvE 副本 composable 块已删除（2026-09-06 S2）」（accessed 2026-09-11）

[42] `DungeonApi.kt`「副本/深渊系统 UI 层零调用」（accessed 2026-09-11）

[43] `MonetizationApi.kt` / `TowerApi.kt` `@Deprecated` 死 API 标记（accessed 2026-09-11）

[44] `app/src/mediapipe/java/.../OnDeviceAgentMediaPipe.kt`（非默认 source set）+ `desktopApp` 现状（accessed 2026-09-11）

### 取证说明

- findings 明细：`research/milan-upgrade-2026-09/findings/F1.md` … `F6.md`（79 条结构化 claims）。
- 本报告编号在 first-mention 顺序上合并了六份 findings 的 URL/路径；同一路径去重。
- WebSearch 在部分子代理会话不可用；F1/F4 以 WebFetch 主源补强，F2/F3/F5/F6 以本地主源为主。developer.android.com 多次 transport error，已记入 Open questions。
