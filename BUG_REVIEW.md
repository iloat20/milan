# Milan（Kotlin/Compose）静态 Bug 审查报告

> 审查日期：2026-08-12
> 范围：`MilanKotlin/app/src/main/java` 全部领域层、服务层、存档层、内容层、UI 关键屏（gacha/progression/home）、`GameState`/`CrashReporter` 单例。
> 方法：静态代码审查（沙箱无法编译，结论需在真实 Android 环境 `assembleDebug` + `testDebugUnitTest` 复核）。
> 红线依据：`AGENTS.md`。

---

## 一、确认无问题的部分（已核对）

- **领域层纯净性**：`domain/`、`data/`（模型）、`infrastructure/eventbus/` 均无 `import android.*`；唯一 android 依赖在 `data/AndroidSaveProvider.kt`（AGENTS 明文允许的接入层）。✅
- **UI 不越权写存档**：UI 仅读取 `GameState.service.saveData`（如 `softCurrency < cost` 比较、`ownedCharacters.firstOrNull`），养成/货币写操作全部走 `GameService` 事务范式。✅
- **事务回滚**：`pull` / `levelUp` / `ascend` / `starUp` / `spendSoft` / `recordBattle` 均为「预算→改内存→落盘→失败回滚且不广播」，单测 `pull_saveFailure_rollsBackNoBroadcast` 等已覆盖。✅
- **EventBus 线程安全**：所有读写在 `synchronized(gate)` 内；handler 调用放锁外且异常不中断链；UI 用 `DisposableEffect(Unit)+unsubscribeAll(owner)` 订阅，无泄漏。✅
- **CrashReporter**：全部 IO 包 try/catch，未捕获异常先落盘再委托系统默认处理器；`prev_boot_trace` 误报 bug 已在 `HomeScreen` 补 `home.oncreate.done` 埋点修复（见 HomeScreen.kt:470 注释）。✅
- **AndroidSaveProvider**：tmp→bak→main 原子写、失败清 tmp，符合「进程被杀不截断主档」。✅

---

## 二、发现的问题（按严重程度）

### 🔴 HIGH — `expProgress` 经验条恒为 0（逻辑/设计不一致）

- **位置**：`GameService.kt:328`（`expProgress`）+ `GameService.kt:361`（`levelUp`）；UI 调用点 `ProgressionScreen.kt:362`。
- **根因**：`levelUp` 把 `save.totalExp = EconomyFormulas.cumulativeExp(target)`——这是「到达该等级的累计经验下限」，而非「已积累经验」。而 `expProgress` 用 `cur = totalExp - cumulativeExp(level)` 算本层进度。两者配合的结果是：**每次升级后 `cur` 恒为 0**，经验进度条永远空。
- **影响**：`ProgressionScreen` 调 `expProgress` 渲染进度条，玩家看到的是永远空的条。更严重的是：当前代码**没有任何 `addExp` 路径**，所以 `totalExp` 只会被 `levelUp` 反复覆写为下限——若未来加入「战斗获得经验」功能，也会被 `levelUp` 直接清零。
- **现状**：该交互**未被任何单测覆盖**（仅 `EconomyFormulasTest` 测了 `cumulativeExp`/`expForLevel` 的内部一致性）。
- **建议**：
  1. 若经验是货币升级的副产物（无独立经验来源）：进度条应取 `0`，或干脆不显示经验条，避免误导；
  2. 若经验是独立成长线：把 `levelUp` 改为**累加** `save.totalExp += (cumulativeExp(target) - cumulativeExp(oldLevel))`，并补 `addExp(charId, amount)` 入口 + 单测；`expProgress` 保持不变即可正确显示。

### 🟠 MEDIUM — `SaveManager.load()` 的 `migrate()` 在 try 之外（违反红线）

- **位置**：`SaveManager.kt:60-62`：
  ```kotlin
  current = result
  migrate(result)   // ← 在 try/catch 之外
  return result
  ```
- **根因**：AGENTS 红线明确「**SaveManager 载入永不抛异常**」。当前 `migrate` 是空 stub 所以安全，但它一旦真正做事并抛异常，会冒泡到 `GameState.ensureInitialized` → `MilanApp.onCreate`（虽被外层 try 包住），仍违反红线语义，且可能让 App 在该分支「打不开」。
- **建议**：把 `migrate(result)` 移入 try，或单独 `try { migrate(result) } catch (e) { onTrace("save.migrate.failed"); current = SaveData.createDefault() }`。

### 🟠 MEDIUM — `GachaEngine.rollRarity` 用 `Rarity.entries[i]` 越界风险

- **位置**：`GachaEngine.kt:23-27`，`for (i in rarityWeights.indices) … Rarity.entries[i]`。
- **根因**：`Rarity` 只有 4 个枚举值（R/SR/SSR/UR，index 0–3），但 `loadContent` 对卡池只校验 `rarityWeights.size >= 4`，**不校验 `<= 4`**。若 `data.json` 某池 `RarityWeights` 有 5+ 项 → `Rarity.entries[4]` 抛 `IndexOutOfBoundsException`。该异常发生在 `pull` 的**规划阶段**（尚未改内存、尚未扣款），会直接冒泡到 UI 造成崩溃。
- **现状**：当前 `GameContent` 兜底池与测试均为恰好 4 项，故未触发；属**未防御的潜在崩溃**。
- **建议**：循环改为 `for (i in 0 until minOf(4, rarityWeights.size))`，或在 `loadContent` 把 `size >= 4` 改为 `size == 4`（与 `Rarity` 枚举强对齐）。

### 🟠 MEDIUM — 硬保底稀有度被硬编码为 SSR(3)

- **位置**：`GameService.kt:155` `pity.rollWithPity(rng, pool.rarityWeights.toIntArray(), 3)`。
- **根因**：`minRarityForPity = 3`（SSR）对所有卡池写死。含 UR 的 `pool_main` 触发硬保底时只保证 SSR，而非最高稀有度 UR。若设计意图是「保底必出最高稀有度」，则此处不符；且不可数据驱动。
- **建议**：在 `GachaPoolDataEntry` 增加 `hardPityRarity` 字段（默认 UR=4），`pull` 读取它而非硬编码 `3`；若确为有意「软保底到 SSR」，请在 `AGENTS.md`/注释中明确，避免后续维护者误改。

### 🟠 MEDIUM — `SaveData` 默认 `softCurrency = 999999`（疑似调试遗留）

- **位置**：`SaveData.kt:21`。
- **根因**：新玩家开局即拥有 999,999 星尘，抽卡/养成经济被彻底打穿。极可能是开发期调试值。
- **建议**：确认是否为调试遗留；若为生产，改为合理起始值（如 1600，够一次十连），并在 `DataJsonContentTest` 增加「默认档经济值合理」断言。

### 🟡 LOW-MED — UP 卡池 `pool_flame` 的 R/SR 权重档位被浪费（概率失真）

- **位置**：`GameContent.kt:358-362`。
- **说明**：该池 `entries` 仅含 UR/SSR 与 Flame SR，**没有 R 与大部分 SR 候选**，但 `rarityWeights` 仍是全档 `[400,300,200,100]`。`resolveRarityWithCandidates` 对无候选档位一律向上提升，导致 R(40%)+SR(30%) 全部塌缩为 SSR——实测约 **90% SSR / 10% UR**，R/SR 两档权重形同虚设，UP 池概率被严重扭曲。
- **建议**：UP 池改用真实候选分布（如 `[0,0,200,100]`），或在 `resolveRarityWithCandidates` 中支持「向下回退」以保留原始档位语义（当前向上符合「玩家不亏」意图，但牺牲了配置概率）。

### 🟡 LOW — `pickWeighted` 对负权重会崩溃

- **位置**：`GachaEngine.kt:34` `rng.nextInt(total)`，若 `weights` 含负值使 `total <= 0` 抛 `IllegalArgumentException`。
- **建议**：`loadContent` 校验 `entries.all { it.weight > 0 }`，或在 `pickWeighted` 内对权重 `coerceAtLeast(0)`。

### 🟡 LOW — `GameState.ensureInitialized` 双重检查锁缺 `@Volatile`

- **位置**：`GameState.kt:27,37-41`。`initialized` 非 volatile，极端并发下另一线程可能看到 `initialized=true` 但 `serviceRef` 未可见 → `service` getter `checkNotNull` 抛。
- **现状**：当前仅启动期单线程调用，实际风险极低，但属不正确写法。
- **建议**：`@Volatile private var initialized = false`，或把首次检查也移入 `synchronized(gate)` 内。

### 🟡 LOW — `GameService.save()` 公共方法吞掉失败

- **位置**：`GameService.kt:280-282` `fun save() { saveManager.save() }` 返回 `Unit`，忽略 `Boolean`。
- **建议**：返回 `Boolean`（或 `saveManager.save()` 结果），让设置项等调用方感知落盘失败。

### 🟡 LOW — `GachaScreen.doPull` 对 pull 返回空统一提示「卡池数据异常」

- **位置**：`GachaScreen.kt:163-168`。`pull` 返回空也可能是「余额不足」或「落盘失败回滚」，UI 却只给「卡池数据异常」一种提示，误导玩家。
- **建议**：让 `pull` 返回 `sealed` 结果（Success/InsufficientFunds/SaveFailed/PoolEmpty），UI 给对应提示。

### 🟡 LOW — `ascend`/`starUp` 扣到 0 的星魂碎片条残留 0 数量 item

- **位置**：`GameService.kt` 碎片扣减处；`SaveData.sanitize()` 不清理 0 数量 item。
- **说明**：功能无碍，但存档残留脏数据。
- **建议**：`item.count` 归零时移除该条目（`saveData.items = saveData.items.filterNot { it?.itemId == StarFragmentItemId && it.count <= 0 }`）。

---

## 三、优先级行动建议

| 优先级 | 项 | 工作量 |
|---|---|---|
| 立即修 | #1 exp 条恒 0（决定进度条是否显示/累加） | 中 |
| 立即修 | #2 migrate 移入 try（红线） | 小 |
| 立即修 | #3 rollRarity 越界防御（潜在崩溃） | 小 |
| 发布前确认 | #5 默认 999999 星尘是否调试遗留 | 小 |
| 设计确认 | #4 硬保底写死 SSR、#6 UP 池概率失真 | 中 |
| 跟进 | #7–#11 防御性/健壮性 | 小–中 |

> 注：所有结论为静态分析。沙箱环境无法编译（AGENTS 已记录），建议在真实 Android Studio 环境运行 `.\gradlew.bat :app:testDebugUnitTest` 与 `assembleDebug` 复核，并补充 #1/#3 相关单测后再合入。

---

## 四、已修复记录（2026-08-12 第二轮）

| 项 | 改动文件 | 修复方式 |
|---|---|---|
| #1 经验条恒 0（根因：totalExp 被覆写 + 无经验来源） | `GameService.kt` | ① `levelUp` 改为**累加**已完成等级的累计经验（`save.totalExp += cumulativeExp(target)-cumulativeExp(oldLevel)`），不再覆写为下限；② 新增 `addExp(charId, amount)` 作为经验唯一驱动源，经验累计进 `totalExp`、等级由 `ProgressionEngine.expToLevel` 派生并钳到突破阶段上限（此前 `expToLevel` 是从未被调用的死代码）。经验条自此可随奖励推进/自动升级。 |
| #2 migrate 在 try 之外 | `SaveManager.kt` | `migrate(data)` 移入 `load()` 的 try 块内，迁移异常与 IO 异常同样走兜底默认档，满足「载入永不抛异常」红线。 |
| #3 rollRarity 越界 | `GachaEngine.kt` | 遍历权重时以下标 `i.coerceAtMost(Rarity.entries.lastIndex)` 钳制，权重数组 >4 时溢出权重归并到最高稀有度，避免 `IndexOutOfBounds` 崩溃；末路回退 `Rarity.entries[maxIdx]`。 |

**新增/扩展单测**（配套锁定行为）：
- `GameServiceTest`：`levelUp_keepsTotalExpConsistent`、`addExp_accumulatesAndFillsBar`、`addExp_autoLevelsAndCapsAtStageMax`、`addExp_saveFailure_rollsBack`。
- `GachaEngineTest`：`rollRarity_extraWeightsClampToHighestRarity_noCrash`。

**未改动（留待确认）**：#4 硬保底写死 SSR、#5 默认 `softCurrency=999999`、#6 UP 池概率失真、#7–#11。这些需产品/设计确认，未在本轮改动。

> 沙箱仍无法编译，上述修改需在真实环境跑 `assembleDebug` + `testDebugUnitTest` 复核；逻辑已逐处比对 `EconomyFormulas`/`ProgressionEngine` 口径（`expForLevel`/`cumulativeExp`/`expToLevel` 三者自洽）。

