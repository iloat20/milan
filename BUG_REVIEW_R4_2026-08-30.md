# Milan（Kotlin/Compose）第四轮代码审查报告

> 审查日期：2026-08-30
> 范围：`MilanKotlin/` 全量源码（118 个 .kt / 19.6k 行）、`assets/data.json`、`assets/weapons/`、`res/drawable/`、Gradle 配置、257 个单测
> 方法：四路并行只读专项审查 + **实证复核**（编译验证 / 数值模拟 / 数据对账 / 调用点 grep）
> 与历史报告的关系：`BUG_REVIEW_R3_2026-08-28.md` 的 14 项修复已逐条复核为生效，本轮**只报新发现**，不重复已修项
> 未改动任何生产代码

---

## 0. 结论摘要

| 级别 | 数量 | 主题 |
|---|---|---|
| 🔴 **阻断** | 1 | 工作区当前**编译失败**，`AppTheme` 重构丢色板，31 个 UI 文件挂 |
| 🟠 **严重** | 5 | 立绘缩放失效、兜底池概率塌缩、初始化失败永久卡死、天赋缓存并发竞态、事务异常不回滚 |
| 🟡 **中等** | 22 | 并发/事务、资源与生命周期、Compose 重组性能、数据一致性与测试盲区 |
| ⚪ **轻微** | 16 | 分配冗余、死代码、注释失真、构建卫生 |

**最需要先处理的三件事**：

1. **R4-01（阻断）**：`ui/theme/AppTheme.kt` 的未提交重构把水墨色板的 27 个 token 全部删除，新增 60 个 Material3 语义 token 却未同步调用方 → `compileDebugKotlin` 直接失败，**当前工作区无法构建任何产物**。
2. **R4-02（严重）**：`PortraitImage.kt` 水墨立绘计算了缩放系数 `scale` 却从未施加，全站立绘尺寸与位置全错。
3. **R4-03（严重）**：R3 修好的「UP 池饕餮独占 70%」只修了 `data.json`，**兜底副本 `GameContent.buildPools` 原样复现该缺陷**——离线/降级启动时抽卡概率是另一套。

### 验证状态图例

| 标记 | 含义 |
|---|---|
| ✅ **实测** | 本轮亲自动手验证（编译 / 数值模拟 / 数据对账 / 调用点检索） |
| 🔎 **源码确认** | 本人逐行读过相关代码 |
| 📋 **专项审查** | 由并行审查员提出、本轮未逐条复核的条目（保留并标注） |

### 修复进度（2026-08-30 当日已完成 P0 + P1 部分）

| 项 | 状态 | 改动 |
|---|---|---|
| **R4-01** 编译阻断 | ✅ **已修** | **水墨金箔色板恢复为权威**（产品确认）：25 个水墨 Color token 全部恢复原始色值，`rarityColor`/`rarityGlow`/`rarityGradient` 回到丹青录口径，`WorldTheme` 返回水墨世界调色板；新增的 60 个「未来色板」token 保留但**未启用**（零引用） |
| **R4-02** 立绘缩放 | ✅ **已修** | `PortraitImage.kt:225` 补 `scale(scale, scale)` |
| **R4-03** 兜底池塌缩 | ✅ **已修** | `GameContent.buildPools` 的 `pool_flame` 权重改 `[0,300,200,100]`、条目集对齐 data.json；测试守门扩到两路径 |
| **R4-04** 初始化失败永久卡死 | ✅ **已修** | `GameState` 增 `failure` StateFlow：构造失败由 `runCatching` 捕获（不逃逸、不发布 ready、成功或重试成功时置 null）；`MilanNavHost` 增失败分支 + 新增 `InkErrorScreen`（含「重试」，走完整 `ensureInitialized`） |
| **R4-05** 天赋缓存并发竞态 | ✅ **已修** | `prereqCache`（可变懒缓存，无锁写）→ `prereqIndex`（随 `talentTrees` setter 一次性构建的不可变索引），读者只查表；`loadContent` 不再手工作废缓存 |
| **R4-06** 事务异常不回滚 | ✅ **已修** | `transactionLocked` 用 try 包裹 `mutate`：回滚（回滚自身抛异常单独留痕）→ 留痕 `*.mutate.threw` → 原样上抛 |

**验证结果**：`compileDebugKotlin` BUILD SUCCESSFUL（零错误）；`testDebugUnitTest` **264/264 全绿**（257 原有 + 7 新增）；`assembleDebug` 产出 `app-debug.apk`（77MB）。

**新增测试（均按「先写失败测试 → 修复 → 转绿」落地）**

| 测试 | 例 | 红灯证据 |
|---|---|---|
| `ServiceCoreTransactionTest` | 3 | 修复前 2 例失败：rollback 未执行、无 `*.mutate.threw` 留痕（第 3 例「正常路径不受影响」始终通过，用于锁住无回归） |
| `GameStateInitFailureTest` | 1 | 修复前异常从 `ensureInitialized` 逃逸（`IllegalStateException` 直接打穿用例），修复后记录失败态且 ready 保持 false |
| `TalentPrereqIndexTest` | 3 | ⚠️ **回归网性质**：并发竞态是概率性的（需特定 resize 时序），单跑难以稳定复现红灯。确定性部分是「索引与树定义逐节点一致」和「内容重载（含切兜底）后索引随之重建」 |

> **关于色板方向的裁定（2026-08-30）**：初版修复用的是「旧名 → 新色板别名」方案，但那等于顺带完成了换肤
> （金箔 `0xD4A853` → 电光金 `0xFFD700`）。经产品确认「要的是水墨金箔 UI」，已改为**水墨色板恢复权威**。
>
> **对账结果**：25/25 个水墨 Color token 色值与 HEAD 逐位一致（0 差异、0 缺失）。
>
> ⚠️ **一处初版遗漏、本轮补上的隐蔽漂移**：`Success`（`0xFF5CB87A`→`0xFF00E676`）与 `Warning`（`0xFFD4A020`→`0xFFFFB300`）
> 属于**同名换色**——首轮对比只查了 token「有无」，未查「同名色值」，因此漏掉了它们；
> 这两个 token 有 3 处真实调用点（`WoWStatsPanel` ×2、`InfoPanels` ×1），会悄悄变色。已恢复水墨值并上移至权威区块。
> **教训**：色板/配置类重构的对账必须做「同名值比对」，不能只做「键名有无比对」。
>
> 未来若要启用新色板，正确姿势：把「未来色板」区块上移为权威 → 逐个把水墨 token 改为指向新色的别名 →
> 按屏幕迁移 31 个调用方（进度用 `grep -rn "AppTheme\.\(BgDeepest\|BgMid\|Gold\|Text1\|Text2\|Text3\|Stroke\|Frost\)" app/src` 统计）→ 归零后删除水墨区块。

---

## 1. 🔴 阻断（P0）

### R4-01 · `AppTheme` 重构丢失全部水墨色板 token，工作区无法编译

**验证**：✅ 实测 — `:app:compileDebugKotlin` 失败，`31` 个 UI 文件报 `Unresolved reference`

**位置**：`MilanKotlin/app/src/main/java/com/milan/game/ui/theme/AppTheme.kt`（工作区 `M` 状态，未提交）

**成因**：该文件正在被重构为 Material3 语义化色板（`Primary/Secondary/Tertiary/Surface0..3/TextPrimary..`），但**只改了定义侧，未改任何调用方**：

| | 内容 |
|---|---|
| 删除的 token（27） | `Surface` `SurfaceNested` `BgMid` `BgDeepest` `Gold` `GoldHi` `GoldDeep` `GoldTextOn` `Text1/2/3` `Stroke` `Frost` `FrostDeep` `Violet` `Danger` `SealRed` `ScrimTop/Bottom` `WoWPanelTop/Bottom` `WoWGreen` `WeaponStageBg` `Shinwa` `Aether` `Ironveil` |
| 新增的 token（60+） | `Primary` `OnPrimary` `Surface0..3` `TextPrimary/Secondary/Tertiary` `Outline` `RarityR/SR/SSR/UR/LR` `DurationShort..` `EasingStandard..` 等 |
| 受影响文件 | **31 个**（`GachaScreen` `HomeScreen` `ShopScreen` `TowerScreen` `GameNavBar` `PageComponents` `CharacterListScreen` `SettingsScreen` …） |

**编译输出摘录**：

```
e: TowerScreen.kt:348:76 Unresolved reference 'Surface'.
e: TowerScreen.kt:352:78 Unresolved reference 'BgMid'.
e: TowerScreen.kt:358:44 Unresolved reference 'Gold'.
e: TowerScreen.kt:367:67 Unresolved reference 'Text2'.
...
> Task :app:compileDebugKotlin FAILED
```

**影响**：任何构建（debug / release / 单测 / UI 测试）全部失败。当前工作区的 257 个测试也无法运行，等于**回归网失效**。

**修复方案**（推荐 B）：

```kotlin
// 方案 A（最快止损）：从 HEAD 恢复色板段，在原有 token 之上增量添加新 token
git show HEAD:MilanKotlin/app/src/main/java/com/milan/game/ui/theme/AppTheme.kt > /tmp/old.kt
# 把 old.kt 的 27 个水墨 token 整体搬回，两个体系并存，后续逐屏迁移

// 方案 B（推荐，一次到位）：保留新色板，用兼容别名承接旧名，
// 让 31 个文件零改动通过编译，再按屏幕逐个迁移、最后删除别名
object AppTheme {
    // —— 新色板（权威）——
    val Surface0 = Color(0xFF0E0C14)
    val TextPrimary = Color(0xFFF5F0E6)
    ...
    // —— 兼容别名（迁移期临时，全部标记 Deprecated 便于统计剩余量）——
    @Deprecated("改用 Surface0", ReplaceWith("Surface0")) val Surface get() = Surface0
    @Deprecated("改用 TextPrimary", ReplaceWith("TextPrimary")) val Text1 get() = TextPrimary
    @Deprecated("改用 TextSecondary", ReplaceWith("TextSecondary")) val Text2 get() = TextSecondary
    @Deprecated("改用 GoldAccent", ReplaceWith("GoldAccent")) val Gold get() = GoldAccent
    ...
}
```

配套门禁（防止重演）：

```yaml
# CI 增加一步，色板类重构不允许静默丢 token
- run: ./gradlew :app:compileDebugKotlin
```

---

## 2. 🟠 严重

### R4-02 · 水墨立绘计算了缩放系数却从未施加，全站立绘尺寸与位置全错

**验证**：🔎 源码确认（`PortraitImage.kt:214-227`）

**位置**：`MilanKotlin/app/src/main/java/com/milan/game/ui/components/PortraitImage.kt:222-226`

```kotlin
save()
val scale = maxOf(w / bitmap.width, h / bitmap.height)   // ← 算出了 Crop 缩放比
val dx = (w - bitmap.width * scale) / 2f                 // ← 按「缩放后」算偏移
val dy = (h - bitmap.height * scale) / 2f
translate(dx, dy)
drawBitmap(bitmap, 0f, 0f, portraitPaint)                // ← 1:1 绘制，scale 未施加
restore()
```

**成因**：水墨化重构把 `Image(bitmap, contentScale = ...)` 换成 Canvas 自绘时，漏掉了 `canvas.scale(scale, scale)`。`contentScale` 参数在函数体内**一次都没被使用**，等于完全失效。

**影响**：两个错误叠加（按放大后尺寸算偏移 + 实际 1:1 绘制），立绘既**缩水**又**偏移**。

| 场景 | 目标区域 | Bitmap | scale | 实际渲染 |
|---|---|---|---|---|
| 缩略图（`Thumb`，采样 4x → 208×312） | 585×480 | 208×312 | 2.81 | 只占 35% 宽度，且**上移 198px**（`dy<0`） |
| 首页 Hero（`Full`，采样 2x → 416×624） | 1170×1290 | 416×624 | 2.81 | 只占 36% 宽度，**上移 231px** |

覆盖面：首页主视觉、卡组/图鉴/角色列表网格、抽卡 chip、编队槽位、抽卡演出卡、详情页与养成页 Hero——**全站立绘**。

**修复方案**：

```kotlin
drawIntoCanvas { canvas ->
    canvas.nativeCanvas.apply {
        save()
        translate(dx, dy)
        scale(scale, scale)          // ← 补这一行
        drawBitmap(bitmap, 0f, 0f, portraitPaint)
        restore()
    }
}
```

> 更彻底的方案是把缩放交回 `Image(bitmap, contentScale = ...)`（原生支持 Crop/Fit），水墨滤镜通过 `colorFilter = ColorMatrixColorFilter(...)` 传入，暗角与噪点改用 `Modifier.drawWithCache` 叠加。这样 `contentScale` 参数重新生效，且消除全部自绘坐标计算。

---

### R4-03 · 兜底卡池复现已修复的 C1 概率塌缩（离线路径是另一套概率）

**验证**：✅ 实测 — 解析 `data.json` 与兜底过滤条件逐条对账

**位置**：`MilanKotlin/app/src/main/java/com/milan/game/services/GameContent.kt:387-393`

```kotlin
GachaPoolDataEntry(
    poolId = "pool_flame", displayName = "业火轮盘 · UP",
    rarityWeights = listOf(400, 300, 200, 100),   // ← data.json 已改为 [0, 300, 200, 100]
    hardPity = 80, singleCost = 160, tenCost = 1600,
    featuredCharacterId = "char_ur_zhulong",
    entries = characters.filter { it.element == "Flame" || it.baseRarity >= 3 }.map(::entryFor),
)
```

**实测对账**：

| 路径 | R 档权重 | R 档候选 | SR 档候选 | 结果 |
|---|---|---|---|---|
| `data.json`（R3 已修） | **0** | 0 | 1（饕餮） | 正常 |
| `GameContent` 兜底 | **400（40%）** | **0** | 2（饕餮 + 狻猊） | **塌缩** |

兜底过滤条件 `element == "Flame" || baseRarity >= 3` 选出的 19 个角色中，**R 档（rarity=1）候选数为 0**（实测稀有度分布 `{2:2, 3:7, 4:10}`）。40% 的掷档落入 R 槽后被 `resolveRarityWithCandidates` 就近上抬到 SR 档 → **SR 实际占比 70%**（饕餮 + 狻猊各 35%），UR 率从 16.7% 掉到 10%，且多放出 `char_sr_suanni`（`data.json` 的 UP 池没有它）。

**根因**：R3 的 C1 修复只改了 `data.json`，而 `ServiceCore.loadContent:280-297` 的「非零权重档必须有候选」校验**只作用于 JSON 路径**——兜底池由 `GameContent.buildPools` 直接生成，**绕过该过滤**。文件 KDoc（`:366-368`）声称「权重对齐 data.json 主来源防漂移」，与实现矛盾。

**影响**：`data.json` 缺失/损坏/`assets` 读取失败时走兜底（见 `MilanApp.kt:53` 的 `runCatching {...}.getOrNull()`），玩家在两种状态下抽到的是**两套概率**。虽然主路径打包在 assets 内、正常不会缺失，但这正是设计「双源一致性」要防的场景。

**修复方案**：

```kotlin
// GameContent.buildPools —— 与 data.json 逐字段对齐
GachaPoolDataEntry(
    poolId = "pool_flame", displayName = "业火轮盘 · UP",
    rarityWeights = listOf(0, 300, 200, 100), hardPity = 80,
    singleCost = 160, tenCost = 1600,
    featuredCharacterId = "char_ur_zhulong",
    entries = characters.filter { it.baseRarity >= 3 || it.characterId == "char_sr_taotie" }
        .map(::entryFor),
)
```

```kotlin
// ServiceCore.loadContent —— 兜底路径也过一遍池校验（治本）
private fun sanitizePools(pools: List<GachaPoolDataEntry>, knownIds: Set<String>) =
    pools.filter { p -> p.isValid(knownIds) }      // 两条路径共用同一套校验
```

```kotlin
// DataJsonContentTest —— 把守门断言扩到两条路径、全部池
js.pools.forEach { jp ->
    val fp = fb.pools.first { it.poolId == jp.poolId }
    assertEquals("${jp.poolId}.rarityWeights", jp.rarityWeights, fp.rarityWeights)
    assertEquals("${jp.poolId}.entries", jp.entries.map { it.characterId }.toSet(),
                                        fp.entries.map { it.characterId }.toSet())
}
pools.forEach { pool ->
    pool.rarityWeights.forEachIndexed { i, w ->
        if (w > 0) assertTrue("${pool.poolId} 档位 ${i+1} 无候选",
            pool.entries.any { it.rarityIndex == i + 1 })
    }
}
```

---

### R4-04 · 初始化异常后 App 永久卡在加载动画，无错误态、无重试、杀进程无效

**验证**：🔎 源码确认（`MilanApp.kt:46-67` + `MainActivity.kt:142-148`）

**位置**：`MilanApp.kt:63-66`、`MainActivity.kt:143-148`

```kotlin
// MilanApp.kt —— 异常被吞，GameState.ready 永远停在 false
} catch (e: Exception) {
    CrashReporter.write("MilanApp.onCreate", e)
    // 不重抛：主界面会显示错误态，且 CrashReporter 已留痕。   ← 注释与实现不符
}
```
```kotlin
// MainActivity.kt —— 唯一的就绪分支，!ready 只有加载动画，没有 error / 超时分支
val ready by GameState.ready.collectAsStateWithLifecycle()
if (!ready) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InkLoadingIndicator() }
    return
}
```

**成因**：`ensureInitialized` 抛异常时 `initialized` 与 `_ready` 都不推进，而 UI 门控只有 `!ready → 加载动画` 一个分支。`MilanApp.kt:23-24` 与 `:65` 的注释声称「主界面会显示错误态」，该错误态**根本不存在**（过期注释）。

**影响**：触发概率低（`assets` 读取包了 `runCatching`，`GameService` 构造有兜底），但**后果不可逆**：玩家每次打开只看到转圈墨点，杀进程重进也一样（初始化幂等、异常可复现），只能卸载重装或清数据。AGENTS.md 明确写了「`GameState` 初始化里抛异常 = App 永久打不开」。

**修复方案**：

```kotlin
// GameState.kt —— 增加失败态
private val _failure = MutableStateFlow<String?>(null)
val failure: StateFlow<String?> = _failure.asStateFlow()

fun ensureInitialized(saveProvider: SaveProvider, contentJson: String?, onTrace: (String) -> Unit) {
    if (initialized) return
    synchronized(gate) {
        if (initialized) return
        runCatching { GameService(saveProvider, contentJson, onTrace) }
            .onSuccess { serviceRef = it; initialized = true; _ready.value = true }
            .onFailure { _failure.value = it.message ?: it::class.java.simpleName }
    }
}
```
```kotlin
// MainActivity.kt —— 在 !ready 分支前插入
val failure by GameState.failure.collectAsStateWithLifecycle()
if (failure != null) {
    InkErrorScreen(reason = failure!!, onRetry = { /* 清数据重建 or 引导反馈 */ })
    return
}
```

**附带修正**：`MilanApp.kt:64` 应改调 `CrashReporter.traceNonFatal(...)`。当前被 catch 的可恢复异常走了 `write()`（真崩溃语义：落 `last_crash.txt` + 归档 + 计数 + 弹窗），会① 让下次启动弹出「上次异常退出现场」误导玩家；② 让设置页暴露给玩家的「崩溃次数」虚高（见 `SettingsScreen.kt:334`）；③ 占满 `crash/history` 归档槽位。

---

### R4-05 · `prereqCache` 无锁并发读写，可破坏 HashMap 结构并导致死循环

**验证**：🔎 源码确认（`ServiceCore.kt:254-262`、`:271`）

**位置**：`MilanKotlin/app/src/main/java/com/milan/game/services/ServiceCore.kt:256-262`

```kotlin
private var prereqCache: MutableMap<String, Map<String, List<String>>>? = null

fun prereqMap(tree: TalentTreeData): Map<String, List<String>> {
    val cache = prereqCache ?: mutableMapOf<String, Map<String, List<String>>>().also { prereqCache = it }
    return cache.getOrPut(tree.treeId) { tree.nodes.associate { it.nodeId to it.prerequisiteNodeIds } }
}
```

**成因**：这是全服务层**唯一一个在无锁路径上写的共享可变状态**，三条访问路径分属不同线程：

| 调用方 | 位置 | 线程 | 操作 |
|---|---|---|---|
| `canAllocateTalent` | `ProgressionService.kt:213-218` → `ProgressionPanels.kt:482` | **主线程**（非 suspend 读接口） | `getOrPut` **写** |
| `allocateTalent` | `ProgressionService.kt:223-243` | `writeMutex` 内的后台线程 | `getOrPut` **写** |
| `loadContent` | `ServiceCore.kt:271` | 启动线程 | `prereqCache = null` **写** |

**影响**：
1. `LinkedHashMap.getOrPut` 并发写入可能触发 resize 竞态 → 链表成环 → **CPU 100% / ANR**；
2. `prereqCache = null` 与 `?.also{}` 的 check-then-act 竞态 → 内容重载后仍返回旧树的前置映射，天赋前置校验读到过期数据；
3. 字段非 `@Volatile`，跨线程可见性无保证。

**修复方案**（改为「内容加载时构建不可变索引，读者永不写」）：

```kotlin
private var prereqIndex: Map<String, Map<String, List<String>>> = emptyMap()

var talentTrees: List<TalentTreeData> = emptyList()
    set(value) {
        field = value
        talentTreesById = value.associateBy { it.treeId }
        prereqIndex = value.associate { t ->
            t.treeId to t.nodes.associate { n -> n.nodeId to n.prerequisiteNodeIds }
        }
    }

fun prereqMap(tree: TalentTreeData): Map<String, List<String>> =
    prereqIndex[tree.treeId] ?: tree.nodes.associate { it.nodeId to it.prerequisiteNodeIds }
```
并删除 `loadContent:271` 的 `prereqCache = null`。

---

### R4-06 · 事务模板未包裹 `mutate`，异常穿透时既不回滚也不留痕

**验证**：🔎 源码确认（`ServiceCore.kt:135-150`）

**位置**：`MilanKotlin/app/src/main/java/com/milan/game/services/ServiceCore.kt:135-150`

```kotlin
suspend inline fun transactionLocked(tag, mutate, rollback, onCommit): WriteOutcome {
    mutate()                                              // ← 抛异常即穿透
    val saved = withContext(Dispatchers.IO) { saveManager.save() }
    if (saved) { onCommit(); return WriteOutcome.Success }
    rollback()
    onTrace("$tag.save.failed: rolled back")
    return WriteOutcome.SaveFailed
}
```

**成因**：事务范式只覆盖了「落盘失败」一条失败路径，未覆盖「`mutate` 自身抛异常」。

**影响**：`mutate` 中途抛异常时（例如 `pull` 的十连循环跑到第 3 次时 `OutOfMemoryError`、或 R4-10 的 `expToLevel` 死循环之外的任何意外），内存已被**部分改写**（已加 3 个角色 / 已扣款或未扣款 / 历史半截），而 `rollback` 不执行、`save()` 不执行、`onTrace` 不留痕。锁随 `withLock` 的 finally 释放后，**残留的半截状态会被后续任意一次成功落盘持久化**，磁盘与内存静默分叉，且无任何排查线索。

**修复方案**：

```kotlin
suspend inline fun transactionLocked(tag, mutate, rollback, onCommit): WriteOutcome {
    try { mutate() } catch (t: Throwable) {
        try { rollback() } catch (rt: Throwable) { onTrace("$tag.rollback.threw: ${rt.message}") }
        onTrace("$tag.mutate.threw: ${t.message}")
        throw t
    }
    val saved = withContext(Dispatchers.IO) { saveManager.save() }
    if (saved) { onCommit(); return WriteOutcome.Success }
    rollback()
    onTrace("$tag.save.failed: rolled back")
    return WriteOutcome.SaveFailed
}
```

---

## 3. 🟡 中等

### 3.1 并发与事务

| ID | 位置 | 问题 | 影响 | 修复要点 |
|---|---|---|---|---|
| **R4-07** | `ServiceCore.kt:163-174`（经 `:144` 的 `onCommit` 调用） | `EventBus.dispatch()` 在 `writeMutex` **临界区内**同步执行订阅方代码 | `writeMutex` 不可重入，任何订阅方 handler 回写服务层即**死锁**。当前 `CurrencyChanged`/`ProgressionChanged` 零订阅者故未触发，属「谁先订阅谁踩雷」 | 出锁后派发：新增 `suspend inline fun <T> withWriteLock(body)` 外壳，`finally { if (EventBus.pendingCount > 0) EventBus.dispatch() }`，并删掉 `publish*` 里的 `dispatch()` |
| **R4-08** | `ProgressionService.kt:67-70/157-160/188-190`、`MetaService.kt:70-71` | 双 publish 各调一次 `refreshSnapshot()` | 每次养成操作做 **2×N** 次角色状态拷贝（满图鉴 62 次分配）+ 2 次全队列派发；`revision` 每次 +2，放大 UI 重组 | 合并为 `publishCurrencyAndProgressionChanged()`（一次快照、一次派发） |
| **R4-09** | `ServiceCore.kt:179-188`、`EconomyService.kt:21`、`GameState.kt:82/92/95` | 全部只读 API 不加锁 | **撕裂读**（`applyCurrencyDelta` 先写 soft 再写 hard，可读到「新 soft + 旧 hard」；`pull` 十连循环可读到半截状态）；**幽灵读**（`mutate` 与 `rollback` 之间、含 `withContext(IO)` 挂起窗口，读者看到即将被回滚的数据，而回滚路径不广播） | UI 侧收敛到 `snapshot`（已是一致拷贝）；或提供 `suspend fun <T> readLock(block): T`。最低限度：KDoc 明确标注「非一致性读」 |
| **R4-10** | `EconomyFormulas.kt:33-37` + `ProgressionEngine.kt:17-21` | `cumulativeExp` **Int 溢出** → `expToLevel` 无界循环 | ✅ 实测：函数在 **level=4635** 处首次非单调（峰值仅 `1,073,466,100`，非 Int.MAX），此后返回值正负振荡；`expToLevel(totalExp)` 在 `totalExp ≥ 1.07e9` 时**死循环**（模拟 500 万次迭代未终止）。调用点 `:195`（爬塔，在 `writeMutex` 内）→ 全 App 写操作永久死锁 | `cumulativeExp` 改 `Long` 计算 + `coerceAtMost(Int.MAX_VALUE)`；`expToLevel` 加 `level < maxLevel()` 循环护栏；`sanitize()` 补 `totalExp` 上界。**定级说明**：正常玩法不可达（`towerRewardExp(999)=50,000`，需刷 2.1 万次），触发路径为**脏档/改档**，故定中等而非严重；但修复仅需 3 行，性价比极高 |
| **R4-11** | `ServiceCore.kt:280-297` | 卡池校验四处缺口 | 未校验 `rarityIndex ∈ 1..4`（升/降档找不到候选 → `continue` → **十连只发 3 件扣 10 件钱**）；未校验 `weight > 0`（负权重扭曲抽样）；未校验 `singleCost/tenCost >= 0`（`cost<0` 时 `softCurrency -= cost` 变相加钱 → **无限星尘**）；未校验 `characterId ∈ characters`（幽灵角色永久留存）。另：`loadContent` 非 suspend、无锁改写共享 `var`，与持锁写路径存在引用发布竞态 | 抽 `private fun GachaPoolDataEntry.isValid(knownIds: Set<String>)` 统一校验，两条加载路径共用（同时治 R4-03） |
| **R4-12** | `ServiceCore.kt:191-196`、`MetaService.kt:244-247` | 道具增减缺下溢护栏；`claimAchievement` 漏溢出校验 | `addItemDelta(id, -n)` 在 `delta<0` 且条目不存在时会**建档负数幽灵条目**；与 `applyCurrencyDelta`（`EconomyService.kt:47-54`）的 `Long` 预算校验口径不一致 | `check(cur.toLong() + delta >= 0)`；`claimAchievement` 补 `softCurrency.toLong() + def.rewardSoft > Int.MAX_VALUE → Rejected` |
| **R4-13** | `ServiceCore.kt:153-158` | `transaction()` 是**死代码**（全仓零调用） | 它会自动加锁，与「`writeMutex` 不可重入」红线存在误用风险 | 删除，或加 `@Deprecated("持锁调用方一律用 transactionLocked")` |
| **R4-14** | `ProgressionService.kt:84-113` vs `TowerService.kt:192-200` | 经验逻辑**两份实现且口径已分叉** | `addExp` 与爬塔内联版都要维护，且本次已确认存在口径差异：内联版有 `if (target > s.level)` 单调护栏，`addExp` 是**无条件 `save.level = newLevel`**（脏档可致等级倒退）。另：`addExp` 全工程**零生产调用点**（✅ grep 确认：仅 `GameService:243` 转发 + 3 个测试），KDoc 自称「统一出口」名不副实 | 抽 `ServiceCore.applyExpLocked(save, amount): Boolean` 纯函数供两处共用，删掉重复的 `ExpSnapshot` 逻辑 |

### 3.2 资源与生命周期

| ID | 位置 | 问题 | 影响 | 修复要点 |
|---|---|---|---|---|
| **R4-15** | `MilanAudio.kt:80-87` + `:198-203` | `AUDIOFOCUS_LOSS` 分支清空 `bgmTarget` | 永久失去音频焦点（打开外部音乐 App / 来电）后，`playBgm` 只在 `MainActivity.onCreate:107` 调用一次，唯一自动恢复入口 `resumeForeground` 依赖已被清空的 `bgmTarget` → **BGM 整会话再也不响**，设置页无开关可恢复，必须杀进程 | LOSS 分支只 `pause()` 并 `abandonFocus()`，**保留 `bgmTarget` 意图** |
| **R4-16** | `SpeechPlayer.kt:63-87` | TTS 初始化失败时反复新建引擎且从不 `shutdown()` | 无中文语音包的设备（`LANG_MISSING_DATA`）上，每点一次角色台词「▶」泄漏一个 `TextToSpeech` 实例与一条 ServiceConnection → 连续点击可致 `Too many bind requests` / OOM | 失败分支 `runCatching { tts?.shutdown() }; tts = null; pending = null` |
| **R4-17** | `MainActivity.kt`（全类无 `onNewIntent`），Manifest 未声明 `launchMode` | 小组件深链在 App 已运行时**静默失效** | `GachaGlanceWidget.kt:49-56` 用 `actionStartActivity` 携带 `EXTRA_NAVIGATE=gacha`，若系统投递给已存在实例走 `onNewIntent`，`onCreate` 不再执行 → 只切前台不跳转。而这恰是小组件最主要的触发场景，表现为「点了没反应」 | 重写 `onNewIntent`：`setIntent(intent)` + 把 extra 写入 `mutableStateOf` 供 UI 消费 |
| **R4-18** | `DailySupplyNotifier.kt:47-58` | 每日补给提醒**无启动期按存档重注册** | WorkManager 任务在「强行停止 / 备份还原 / 系统清理」后被清除，设置页开关（来自 `pushEnabled`）仍显示「已开启」，玩家无从察觉也无法修复 | `MilanApp` 初始化成功后 `if (saveData.pushEnabled) setEnabled(this, true)`（`KEEP` 策略天然幂等） |
| **R4-19** | `CrashReporter.kt:88-92` + `:322-353` | 崩溃现场对话框实际不可点 | 主线程崩溃时 `runOnUiThread` 内联执行 → 对话框刚 `show()` 就被 `previous.uncaughtException` 的 `killProcess` 蒸发；子线程崩溃时不等待绘制。玩家侧取证路径等于不存在 | 删除 `tryShowDialog`（只保留落盘 + 下次启动回显，`HomeScreen` 已实现），或仅对子线程崩溃 `latch.await(30s)` |
| **R4-20** | `DailySupplyWorker.kt:25` + `:41-44` | `GameState.ready.first { it }` 无超时 + `catch (_: Exception)` 吞异常 | 初始化失败时（见 R4-04）该 `first{}` 永不完成，Worker 挂到 WorkManager 10 分钟超时，且 `boot_trace.txt` 无任何记录 | `withTimeoutOrNull(20_000)` + 超时 `traceNonFatal` |
| **R4-21** | `MainActivity.kt:121-129` | 用 Activity 的 `onStop/onStart` 管理 BGM | 旋转、多窗口、折叠屏展开同样触发 `onStop` → 完整 `release()` + 重建 ExoPlayer，产生可闻的音乐中断与额外耗电（代码意图是应用级前后台） | 改用 `ProcessLifecycleOwner` 在 `MilanApp` 一次性注册 |
| **R4-22** | `CrashReporter.kt:59` | 镜像执行器线程非守护且从不 `shutdown` | 一旦 `mirror` 触发过即常驻，阻止 JVM 正常退出（影响 Robolectric / 多进程） | `Thread(r, "milan-crash-mirror").apply { isDaemon = true }` |
| **R4-23** | `EventBus.kt:20` | `handlerException` 非 `@Volatile` | 在 `Dispatchers.IO` 写入、在任意 `dispatch()` 线程读取（锁外），极端情况读到 `null` → handler 异常不落 `non_fatal.txt`，排障线索丢失（队列本身的并发实现是正确的） | `@Volatile var handlerException` |
| **R4-24** | `SaveData.kt:237-247` + `SaveManager.kt:39-54` | 存档解析失败**吞掉异常原因** | 玩家反馈「进度没了」时，`boot_trace.txt` 只有固定文案 `all sources corrupt`，无法区分字段类型不匹配 / 数字越界 / `sanitize()` 自身抛出 | `tryParse(json, onError: (Throwable) -> Unit)`，把 `it.message` 写进 trace |
| **R4-25** | `GameService.kt:166` | `save()` 是绕过 `writeMutex` 的无锁写路径（当前零调用者） | 未来任何人调用都会引入「内存已改、落盘半途被覆盖」的竞态，违反 `ServiceCore` 事务范式 | 删除，或改为 `core.writeMutex.withLock { withContext(IO) { saveManager.save() } }` |

### 3.3 Compose 性能

| ID | 位置 | 问题 | 影响 | 修复要点 |
|---|---|---|---|---|
| **R4-26** | `CharacterListScreen.kt:89-93` | 屏幕级读取 `firstVisibleItemScrollOffset` | 🔎 确认：滚动偏移量在**屏幕函数体**（而非 `graphicsLayer` lambda）读取 → 每滚动一帧整屏重组 → 6~8 张可见 `CharacterCard` 逐帧重组（每张含 `produceState` + `Crossfade` + 3 个 `Brush`）；`CompletionPanel:107` 每次重组跑 10 次 `roster.count{}` | `PageBackground` 的 `scrollOffset` 改 lambda（`() -> Float`）在绘制阶段读取；`CompletionPanel` 统计 `remember(roster, ownedIds)` |
| **R4-27** | `GachaScreen.kt:591-603` + `:624-644` | 结果 chip 常驻 60fps 无限动画 + 组合期逐帧新建 `Brush` | `glowA` 在组合期读取 → 每张 SSR/UR chip 每帧重组；`Brush.verticalGradient` 与两个 `listOf` 每次重建（Skia Shader 构造开销远高于普通对象）。十连出 5 张 SSR+ ≈ **每秒 300 次重组 + 300 个 Shader**，且展示完成后**永不停止** | 动画值下沉到 `graphicsLayer`/`drawWithCache`；`glowBrush` 用 `remember` 固化，只让 `alpha` 逐帧变；结果展示 5s 后降级为静态 |
| **R4-28** | `PortraitImage.kt:244-262` | 宣纸噪点是**确定性**的却每次绘制重算 | 逐像素双重循环：首页 Hero 区 1170×1290 → 单帧 ~7700 次迭代 + ~1300 次 `drawPoint`；放大器是 `HeroPortrait` 外层 `Modifier.offset`（`HomeScreen.kt:347`）浮动动画每帧改变节点位置 → **每帧重绘** → 该循环按 60fps 常驻 | 噪点缓存为 128×128 `ImageBitmap`（`remember`），绘制时一次 `drawImage` 平铺；暗角 9 层同心圆改 `remember` 的 `Brush.radialGradient` |
| **R4-29** | `UIComponents.kt:139-150` + `inkBorderPath():47-118` | `GlassPanel` 用 `drawBehind` 每次绘制重建墨迹边框 Path | 340dp 宽面板 ≈ 250 次 `lineTo` + 1000 次 `sin/cos` + 一个 Path 分配；滚动时每个可见面板**逐帧**重付（商店同屏 6 个 → ~6000 次三角函数/帧） | 改 `drawWithCache`（Path 仅在尺寸/密度变化时重建） |
| **R4-30** | `HomeScreen.kt:309-331` | `Halo` 用 `.alpha()/.scale()` 带参 Modifier + 组合期新建 `Brush.radialGradient` | 参数在组合期求值 → 每帧重组 + 每帧新建 Shader；3 秒往复动画永不停止，主页停留期间持续 60fps 重组 + GC 压力 | 合并进 `graphicsLayer`（延迟阶段读取）+ `drawWithCache` 缓存渐变 |
| **R4-31** | `CyberStage.kt:291-298`（`InkParticles`）、`:225-238` | 抽卡页待机枢纽常驻两路 60fps 循环 | `CyberHerald` 在 `GachaScreen.kt:411` **无条件组合**（非演出层），内部跑 2 个 `rememberInfiniteTransition` + `withFrameNanos` 死循环 + 8 颗墨点三角函数。玩家停在抽卡页不操作也满帧重绘（`time` 在绘制 lambda 内读取这点是对的，但重绘本身常驻） | 待机降帧（`delay(32)`）或按 `repeatOnLifecycle(RESUMED)` 启停——`GpuEffects.kt:125` 已有该范式，本文件未沿用 |
| **R4-32** | `TowerScreen.kt:274-285` | 战报展开**一次性组合全部条目**（非惰性） | `BattleSimulator` 僵局局可达 200~250 条 log，`Column { forEach }` 全部实例化 → 展开瞬间 ~750 个组合节点 + 250 次克制表查表，卡顿 200~500ms | 换 `LazyColumn` + `key`，或 `log.take(60)` 并提示「仅显示最近 60 条」 |
| **R4-33** | `PortraitImage.kt:95-98` + `PortraitLoader.kt:117-121` | 立绘资源 id 探测在**组合期同步**做反射查表 | `Resources.getIdentifier` 单次 1~10ms；`remember` 计算 lambda 在组合期同步跑，`identifierCache` 只掩盖重复调用，**每个角色首次出现仍付一次主线程代价**。滚到新一屏时 2~6 张新卡同时首次组合 → 单帧累积 10~40ms | 启动时 IO 线程 `PortraitLoader.prewarm(ids)` 预热，组合期只查 `ConcurrentHashMap` |
| **R4-34** | `ProgressionPanels.kt:292` | `StarPanel` 直读 `GameState.service.getStarFragments()`，未订阅快照 | R3 的 M2 修复只覆盖了 `ResourceBar`/`LevelPanel`/`AscendPanel`，**`StarPanel` 漏改** → 碎片变动后「升星」按钮可点态停留在旧值（够了仍灰 / 已花掉仍亮，点了才报错） | 与同文件其他面板统一：`val snap by GameState.snapshot.collectAsStateWithLifecycle(); val frags = snap.starFragments` |

### 3.4 数据一致性、测试与工程

| ID | 位置 | 问题 | 影响 | 修复要点 |
|---|---|---|---|---|
| **R4-35** | `shared/build.gradle.kts`（仅 12 行，无 `commonTest` sourceSet） | **`:shared` 领域层自身零测试接线** | 15 个领域测试文件全部寄生在 `app/src/test`，靠 Android 模块跑；`:shared` 与 `:desktopApp` **无 `test` 任务**，`./gradlew test` 不覆盖被标榜为「跨端单一事实来源」的那一层；将来加 iOS/JS target 时一个都跑不了 | `commonTest` sourceSet + `kotlin("test")`；迁移 `domain/**` 与 `data/{SaveData,SaveManager,SaveDataFormation}Test`（已实测零 Android 依赖） |
| **R4-36** | 见下表「零覆盖生产方法」 | 测试盲区 | 关键分支无回归网 | 按清单补测（+40 用例） |
| **R4-37** | `RoutesTest.kt:45`、`TeamResonanceTest.kt:65` | 两处 `assert(...)` 是**恒真空断言** | Kotlin `assert()` 需 JVM `-ea`，Gradle `Test` 默认 `enableAssertions=false` → 断言永不执行，测试仍「通过」，测试名与内容不符 | 改 JUnit `assertTrue/assertNotSame`；并在 `app/build.gradle.kts` 加 `tasks.withType<Test>().configureEach { enableAssertions = true }` 兜底 |
| **R4-38** | `app/build.gradle.kts:52-56` | `benchmark` buildType 未 `initWith(release)` | 只写了 `matchingFallbacks`，`isMinifyEnabled` 仍为 false → Macrobenchmark 测的是**未 R8、未裁剪资源**的 APK，与玩家手上的 release 包代码路径、资源体积、Baseline Profile 生效情况全不同，数据偏乐观且不可用于回归比较 | `initWith(getByName("release"))` |
| **R4-39** | `GameContent.kt:404-578` | `enrich` 每次调用重建 6 张大表 | 6 个 `mapOf` 写在函数体内 → 每次调用分配 6 个 `LinkedHashMap` + ~180 个 `Map.Entry` + 30 个 `List`（当前仅 init 调一次，故定中等） | 提升为 `object` 级常量，`enrich` 只保留循环 |
| **R4-40** | `GachaService.kt:68/77/52` | 每次掷骰重建 `IntArray` / `GachaEngine` | 十连 10 次冗余分配；`PityCounter.softAdjustedWeights` 内部还 `copyOf()` 一次 | 建池时预计算 `rarityWeights: IntArray`；复用 `core.gacha`（无状态壳） |
| **R4-41** | `MetaService.kt:84-98`、`TowerService.kt:141-158` | `recordBattle` 与平局分支**丢弃 `WriteOutcome`** | 与其余 20+ 个写接口「返回 `WriteOutcome`」的契约不一致，落盘失败被当成成功，UI 无从提示 | 改返回 `WriteOutcome` |
| **R4-42** | `desktopApp/.../Main.kt:21/25/27` | 桌面模拟器每抽新建 `Random`，`engine` 是死变量 | `PityCounter` 走 `gacha == null` 分支 → 每抽新建引擎；每抽传入 `Random(20260813L + i)` 是全新随机流 → 10 抽彼此独立，**演示不出保底、演示不出可复现**，而该文件唯一意义就是证明这两点 | 共享单一 `rng` + `PityCounter(threshold=90, gacha=engine)` + 跑 120 抽 |
| **R4-43** | `shared/build.gradle.kts:2`、`desktopApp/build.gradle.kts:4` | Kotlin 插件未进 version catalog，`shared` 未声明 `jvmToolchain(17)` | 靠根 `buildscript classpath` 隐式解析 2.4.10；`libs.versions.toml` 已写死 `kotlin = "2.4.10"` 却有两处不走它，升级会漏；`shared` 的 jvm target 与 `app`（17）可能不一致 | catalog 补 `kotlin-multiplatform`/`kotlin-jvm`，两模块 `jvmToolchain(17)` |
| **R4-44** | `TowerService.kt:111-112` + `:176-183` | 爬塔对定义缺失的编队成员**静默缩编**，但仍扣满票、仍发经验 | `unitStatsFor` 返回 null 的成员不参战，却占编队位、消耗全额门票、胜利后拿经验 | 扣票与经验发放统一基于 `myUnits` 对应 id 集合；`myUnits.size < teamIds.size` 时 `onTrace` 留痕 |
| **R4-45** | `GachaEngine.kt:80-86`（经 `GachaService.kt:74-82`） | UP 池「歪出」分支按**均匀**抽样，与加权抽取口径不一致 | 正常路径走 `pickWeighted`（按 entry weight），歪出分支走 `nextInt(size)`。现网两池 UR 档权重全为 1 故等价；一旦内容方给同档位配不同权重，实际分布即与公告不符 | `pickFeatured` 增加 `weights` 参数，歪出分支改走 `pickWeighted` |

### 零覆盖生产方法清单（R4-36 明细）

| 生产方法 | 位置 |
|---|---|
| `GameService.character(id)` / `talentTree(id)` / `prereqMap(tree)` / `getTalentTree(charId)` / `canAllocateTalent(...)` | `GameService.kt:157 / 160 / 192 / 252 / 255` |
| `StatsCalculator.deriveSecondary(s)` | `StatsCalculator.kt:65`（`WoWStatsPanel` 四处引用） |
| `towerEnemyStatScale / towerEnemyCount / towerEnemyBaseStats` | `EconomyFormulas.kt:122 / 125 / 131` |
| `towerMaxFloor / towerRewardTickets / towerTicketCost` | `EconomyFormulas.kt:158 / 168 / 171` |
| `dailyDiscountPermille` | `EconomyFormulas.kt:179` |
| `infrastructure/` + `ai/` + `AndroidSaveProvider`（991 行） | **0 测试** |

建议优先补：`canAllocateTalent` 五态（未拥有/无树/无节点/点数不足/前置未满足 → false）、`towerMaxFloor` 的 999/1000 边界、`expToLevel(Int.MAX_VALUE)` 不死循环（R4-10 回归）、`deriveSecondary` 的 `coerceIn` 上下界。

---

## 4. ⚪ 轻微

| ID | 位置 | 问题 | 修复 |
|---|---|---|---|
| R4-46 | `AppChrome.kt:96-116` | 子页顶栏标题常驻 3s 无限浮动（±1.5dp，收益极低），8 个子页停留即满帧重绘 | 删除，或改为新标题出现时播放 600ms 一次性动画 |
| R4-47 | `HomeScreen.kt:424` | `PickIds.toSet()` 写在 `filter` lambda 内 → 56 次 Set 构造 | 提到 lambda 外；`PickIds` 声明为 `Set` 常量 |
| R4-48 | `GachaScreen.kt:481` | 每次重组重切 `chunked(5)`；行 key 仅用行索引 | `remember(results) { results.chunked(5) }`；key 用 `row.firstOrNull()?.characterId` |
| R4-49 | `InkSplash.kt:29` | 使用已废弃的 `Modifier.composed`，全站约 30 处调用，每处物化一套组合期状态 | 迁移到 `Modifier.Node` API |
| R4-50 | `ProgressionScreen.kt:62-64`、`CharacterDetailScreen.kt:74-76` | `@Suppress("UNUSED_EXPRESSION") snap.revision` 冗余（`by` 委托 getter 已注册依赖）且脆弱 | 删除，注释说明「委托读取已建立订阅」 |
| R4-51 | `WeaponPanel.kt:142` | 武器图 LRU（8MB）未接入内存压力回调（`PortraitLoader` 已接） | 在 `PortraitLoader.memoryCallbacks.onTrimMemory` 里一并 `evictAll()` |
| R4-52 | `Format.kt:11-12` | `formatCount` 每次调用新建 `NumberFormat`（内部 `clone()` 深拷贝）；`ResourceBar.Chip` 被 `animateIntAsState` 每帧调 2 次 | 顶层常量化（注意非线程安全，仅主线程使用） |
| R4-53 | `GachaEngine.kt:29-31` | 跨平台层用 `println` 留痕，绕过 `onTrace`，且在每抽热路径 | 删除（该分支已被 `loadContent` 的 `size == 4` 校验覆盖为不可达） |
| R4-54 | `EconomyFormulas.kt:175-176` | `dailyOfferSlots()` 是死代码，`MetaService.dailyOffers()` 硬编码三个字面量 → 单一事实来源被架空 | 让 `dailyOffers()` 消费它，或删除并在调用侧注明 |
| R4-55 | `PityCounterTest.kt:44-52` | `pity.counter = 89` 跳过第 73~88 抽（软保底区间），测试名「恰好在阈值处触发」名不副实，off-by-one 无法被捕获 | 全量跑满 1..89，并断言软保底区间内权重被抬高 |
| R4-56 | `TeamResonanceTest.kt:67-71` | `.map { it.copy(element = "Light") }` 把混编意图抹掉，实际测的是纯同调队（与上一用例重复），`["", "Light", "Light"]` 场景仍零覆盖 | 去掉 `map`，断言空元素角色零加成、双星只加攻 |
| R4-57 | `DataJsonContentTest.kt:16-18 / :63`、`res/raw/keep.xml:5-10` | 三处注释描述**已修复的历史状态**（SR/R 武器为空、31 棵树 Nodes 全空、28 名角色）→ 误导后续维护者 | 更新注释；`:63` 断言改为对每棵树断言 `nodes.isNotEmpty()`（当前对「丢弃空树」逻辑零鉴别力） |
| R4-58 | 三份测试文件 | `GameServiceTest.kt:70-88` / `MetaProgressionTest.kt:65-81` / `FormationTowerServiceTest.kt:64-80` 各自定义 `FakeProvider`（能力不等价：后两者缺 `failDelete`、`loadBackup()` 返回 null） | 抽到 `app/src/test/.../FakeSaveProvider.kt` 统一 |
| R4-59 | `app/build.gradle.kts:38-39` | `versionCode = 1 / versionName = "1.0"`，四轮迭代后未推进；无 `mipmap-anydpi-v26` 自适应图标（targetSdk 37） | 推到 4 / "1.3.0"；补 `adaptive-icon` |
| R4-60 | `MilanAudio.kt:54-67` + `:242` | 切歌未先 `abandonFocus()`，`requestFocus` 每次新建 `AudioFocusRequest` 与监听器 → 监听器表持续增长 | `requestFocus()` 开头先 `abandonFocus()` |
| R4-61 | `SaveData.kt:100` | `sanitize` 道具合并只钳下界，未钳上界（两条 `count=2e9` 合并溢出为负 → 被 `filter { count > 0 }` 静默丢弃） | `coerceAtMost(Int.MAX_VALUE)` |

---

## 5. 审查判断偏差澄清（子代理结论经复核后的修正）

本轮四路并行审查共提交 60 条候选，经实证复核**修正 3 条、驳回 1 条**。记录于此，避免后续按错误结论行动：

| 原结论 | 复核结果 | 依据 |
|---|---|---|
| 🟠 **「`addExp` 无条件覆写 level，存量玩家等级从 50 掉到 2」** | ⬇️ **降级为中等（R4-14）** | ✅ grep 确认 `addExp` **全工程零生产调用点**（仅 `GameService:243` 转发 + 3 个测试）。生产路径是 `TowerService:192-200` 的内联版，它有 `if (target > s.level)` 护栏，**等级不会倒退**。真实问题是「两份实现 + `addExp` 无护栏」的可维护性风险 |
| 🟠 **「ShopScreen 四处 `busy` 防重入是竞态，双击会扣两次」** | ❌ **驳回** | `scope.launch` 走 `AndroidUiDispatcher` 投递到主线程队列，两次点击的协程体**按序**执行；`if (busy)` 与 `busy = true` 之间**无挂起点**，故 A 必然先设 `busy=true`，B 读到后 return。即使 dispatcher 是 `Main.immediate`，A 也会同步执行到第一个挂起点前完成赋值。写法与其他页面不一致（应统一为「同步判 + launch」），但**不是竞态** |
| 🔴 **「`cumulativeExp` 溢出致 `expToLevel` 死循环」** | ⬇️ **降级为中等（R4-10）** | ✅ 数值模拟确认死循环为真，但首次非单调点在 **level=4635**、阈值 `1,073,466,100`（非 Int.MAX）；`towerRewardExp(999) = 50,000`，需连续通关约 **2.1 万次**才可达 → **正常玩法不可达**，触发路径为脏档/改档。仍建议修（3 行成本） |
| 🔴 **「`AppTheme` 重构导致编译失败」** | ✅ **维持最高优先级** | ✅ `compileDebugKotlin` 实测失败，31 个文件报 `Unresolved reference`，丢失 27 个 token |

**方法论结论**：静态推理对「某方法是否真有生产调用点」「协程调度时序」「数值溢出阈值」三类问题的判断偏差率显著，必须靠 grep / 数值模拟 / 实机编译取证。这与 R3 总结的「探针实证法」一致。

---

## 6. 修复路线图

| 优先级 | 项 | 工作量 | 说明 |
|---|---|---|---|
| **P0（当天）** | R4-01 | 1–2h | 编译阻断，其他一切工作的前置。推荐「兼容别名」方案，一次性解封 31 个文件 |
| **P1（发布前）** | R4-02、R4-03、R4-04、R4-05、R4-06 | 约 1 天 | 立绘错乱 / 概率双轨 / 永久卡死 / 并发竞态 / 事务半截状态 |
| **P2（下迭代）** | R4-07 ~ R4-14（并发与事务收口） | 约 1 天 | 含 R4-07 的 `withWriteLock` 外壳改造（五个服务统一替换） |
| **P3** | R4-15 ~ R4-25（资源与生命周期） | 约 1 天 | 建议与「响应式重构」一并做 |
| **P4** | R4-26 ~ R4-34（Compose 性能） | 约 1 天 | 建议先上 Macrobenchmark 基线，按帧率数据排序 |
| **P5** | R4-35 ~ R4-45（测试与工程） | 约 1–2 天 | R4-35（领域测试下沉）建议优先，是后续重构的安全网 |
| **P6** | R4-46 ~ R4-61 | 随手搭车 | — |

**质量门槛**：P0 必须先恢复编译；P1 每项**先写失败测试再修复**；全部改动后跑 `:app:testDebugUnitTest`（当前因 R4-01 无法运行，修复后应恢复 257 全绿）。

**建议新增的三条不变量断言**（性价比最高，能一次性防住整类问题）：

1. 「两条加载路径的 `pools` 的 `rarityWeights` 与 `entries.toSet()` 必须逐池相等」→ 防 R4-03 整类双源漂移
2. 「非零权重档位必须有候选角色」（对两条路径各跑一次）→ 防概率塌缩复发
3. 「`expToLevel(Int.MAX_VALUE)` 必须在 1 秒内返回且 ≤ maxLevel」→ 锁死 R4-10 死循环

---

## 附录 A · 水墨金箔设计一致性核查（2026-08-30，产品问询后专项审计）

> 范围：`ui/` 42 个文件 + `theme/` 3 个文件 + `res/font/` + `res/`。只读核查，未改动。
> 结论：**配色层面基本达标，但有 2 处确凿缺口（字体、小组件）+ 3 处遗留（硬编码、命名、纹样）**。

### A.1 总览

| 维度 | 状态 | 证据 |
|---|---|---|
| 主题层（`Theme.kt` / `ElementTheme.kt`） | ✅ 达标 | `InkColors` = 墨色 + 金箔 + 朱砂 + 石青；8 元素走国画矿物颜料调性（金 `D4A853` / 火 `C84040` / 木 `4A8A50`） |
| 主页面配色 | ✅ 达标 | 34 个 UI 文件引用 `AppTheme.*` 水墨 token（Gacha 33 / Tower 27 / Progression 26 / Home 26 / WoWStats 24 / Shop 19 …） |
| 硬编码颜色 | ⚠️ **值对、路径错** | 仅 8 处，色值全部是水墨色，但未走 token |
| 水墨纹样 | ⚠️ **部分落地** | 已实现：墨迹边框、宣纸肌理、印章稀有度角标、三层水墨立绘滤镜；**未实现**：回纹 / 祥云 / 窗棂 |
| **水墨字体** | ❌ **未接入** | `res/font/` 有 `ma_shan_zheng_regular.ttf`（马善政毛笔楷书）与 `noto_serif_sc_variable.ttf`，**全项目零引用** |
| **桌面小组件** | ❌ **旧暗紫调残留** | `GachaGlanceWidget` 自持常量，文字色是紫调而非宣纸暖白 |
| 抽卡模块 | ⚠️ **值改、名与形未改** | `CyberPalette` 色值已水墨化，但 token 名与图形语汇仍是赛博 |

### A.2 缺口明细

**U-01 · 水墨字体零接入（最高优先级）**

`app/src/main/res/font/` 下两个字体文件已就位，但 `grep -rn "FontFamily\|ma_shan_zheng\|noto_serif" app/src/main` **零结果**——全部文字仍用系统默认 sans-serif。字体是国风观感的核心载体，缺它「水墨」只剩配色和纹样。

```kotlin
// ui/theme/Theme.kt —— 建议接法
private val MaShanZheng = FontFamily(Font(R.font.ma_shan_zheng_regular))
private val NotoSerifSC = FontFamily(Font(R.font.noto_serif_sc_variable))

val GameTypography = Typography(
    headlineSmall = TextStyle(fontFamily = MaShanZheng, fontSize = 22.sp, letterSpacing = 3.sp),
    titleLarge    = TextStyle(fontFamily = MaShanZheng, fontSize = 20.sp, letterSpacing = 0.5.sp),
    bodyLarge     = TextStyle(fontFamily = NotoSerifSC, fontSize = 14.sp),
    ...
)
```
⚠️ 马善政楷书仅含常用字，生僻字会回退系统字体，需真机抽查角色名/技能名是否出现字形混排（本项目有 31 个角色，含烛龙/饕餮/狻猊等生僻字，风险不低）。

**U-02 · 小组件仍是旧「暗紫神性」调性**

`GachaGlanceWidget.kt:85-87` 自持三个常量，与 `AppTheme` 水墨色板无关联：

| 常量 | 现值 | 水墨对应值 | 偏差 |
|---|---|---|---|
| `GOLD` | `0xFFE8B84B` | `AppTheme.Gold` `0xFFD4A853` | 偏亮偏黄 |
| `TEXT_MAIN` | `0xFFF3ECFF` | `AppTheme.Text1` `0xFFF0E8D8` | **偏冷紫**（旧暗紫调性残留） |
| `TEXT_SUB` | `0xFFB7A6CF` | `AppTheme.Text2` `0xFFB0A898` | **明显偏紫** |

Glance 用 `ColorProvider`，无法直接复用 Compose Color，但应把常量值对齐水墨色板并在注释注明来源，避免再次漂移。

**U-03 · 抽卡模块「赛博」命名与图形语汇**

`CyberPalette`（`CyberStage.kt:83-93`）色值已换为水墨色，但 token 名是霓虹语义：

```kotlin
val Cyan       = Color(0xFFBF3A3A)   // 名字叫「青」，值是朱砂红
val Magenta    = Color(0xFFD4A853)   // 名字叫「品红」，值是金箔
val VioletGlow = Color(0xFF7EBAB1)   // 名字叫「紫光」，值是石青
```

配套图形语汇也仍是赛博：`CyberCards.kt:52` 注释「深底 + 网格 + 品红能量环」、`:66-71` 绘制网格线、`:75` 能量环；`ThemeButtons.kt:91` `NeonButton`（描边 + 内发光，DeckScreen 用 3 处）。

**色值水墨化了，但「形」没有**——网格线与内发光是霓虹语汇，水墨对应物应是宣纸纹理 / 回纹边框 / 墨晕扩散。

**U-04 · 8 处硬编码颜色（视觉无偏差，属可维护性缺口）**

| 位置 | 硬编码 | 应改为 |
|---|---|---|
| `CyberStage.kt:83,85,87,89,91,93` | 朱砂 / 金箔 / 石青 / 浓墨 / 宣纸白 ×2 | `AppTheme.SealRed` / `Gold` / `Frost` / `BgDeepest` / `Text1` |
| `ProgressionPanels.kt:155` | `Color(0x3C0A0A0F)` | `AppTheme.BgDeepest.copy(alpha = 0.24f)` |
| `PullShareCard.kt:78` | `0xFF0A0A0F` | `AppTheme.BgDeepest`（Canvas 取 `toArgb()`） |

**U-05 · 回纹 / 祥云 / 窗棂未实现**

设计方向（「中西融合」）列了五种中式纹样，实际只落地「墨迹」一种（`UIComponents.inkBorderPath`）。印章已用于稀有度角标（`CharacterCard.kt:138`）。三种几何纹样尚无实现。

### A.2b 修复进度（2026-08-30 当日）

| 项 | 状态 | 说明 |
|---|---|---|
| **U-02** 小组件 | ✅ 已修（编译已验证） | 三常量对齐水墨，注释标注来源与「改色板须同步」 |
| **U-04** 硬编码 | ✅ 已修（编译已验证） | 共 **13 处**（初报 8 处；复核发现 `PullShareCard` 另有 5 处 `.toInt()` 模式被漏统计） |
| **U-01** 字体 | ⚠️ 代码完成，**构建验证被环境阻塞** | 字体文件已修复替换、接入代码已写，编译验证受阻，原因见文末 |

#### U-01 的关键发现：两个字体文件都是截断损坏的

| 文件 | 工作区大小 | 表目录指向 | 判定 |
|---|---|---|---|
| `ma_shan_zheng_regular.ttf` | 1,212,416 B | `post` 表止于 5,857,936 B | 原始 20.7%，**截断** |
| `noto_serif_sc_variable.ttf` | 2,572,096 B | `gvar` 表止于 25,001,894 B | 约原始 10%，**截断** |

- `git cat-file -s` 显示 blob 与工作区同大小 → **提交时就是坏的**（2026-08-28 引入），非检出 / autocrlf 问题。
- fontTools 解析直接抛 `AssertionError: assert len(data) == self.length`。
- 已从上游 `raw.githubusercontent.com/google/fonts/main/ofl/mashanzheng/MaShanZheng-Regular.ttf` 下载完整版替换（5,857,936 B / 6763 汉字 = GB2312 全集，表目录校验通过，SHA256 前 16 位 `6d2546bb189c732a`）。

**覆盖率实测 99.6%（1433 / 1439）**，缺 6 字 `槃 蝟 話 跂 開 陣`（后三个是繁体，马善政仅含 GB2312 简体），由系统字体自动回退。

接入方式（`Theme.kt`）：

```kotlin
private val MaShanZheng = FontFamily(Font(R.font.ma_shan_zheng_regular))

val GameTypography = Typography(
    headlineSmall = TextStyle(fontFamily = MaShanZheng, fontSize = 22.sp, letterSpacing = 3.sp),
    titleLarge    = TextStyle(fontFamily = MaShanZheng, fontSize = 20.sp, letterSpacing = 0.5.sp),
    titleMedium   = TextStyle(fontFamily = MaShanZheng, fontSize = 17.sp, letterSpacing = 0.5.sp),
    titleSmall    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),  // 小字不用楷书
    ...
)
```

两个刻意的设计决策：

1. **只给大字号用楷书**——≤15sp 时笔画粘连、可读性下降，正文与标签沿用系统无衬线；
2. **标题去掉 `FontWeight.Bold`**——楷书是单一 Regular 字重，设 Bold 会触发合成伪粗体，把笔锋糊成一团。

> ⚠️ 未接入 `noto_serif_sc_variable.ttf`：variable 版原始约 25MB，体积不划算；其损坏文件保留未删（未引用，但会打进 debug APK，建议删除）。

### A.3 建议修复顺序

| 优先级 | 项 | 成本 | 收益 |
|---|---|---|---|
| 1 | U-01 接入水墨字体 | ~0.5h | 观感提升最大，国风识别度的主要来源 |
| 2 | U-02 小组件色值对齐 | ~15min | 消除唯一的紫调残留 |
| 3 | U-04 硬编码走 token | ~20min | 可维护性，防未来色板迁移漏改 |
| 4 | U-03 抽卡模块改名 + 换图形语汇 | 2–4h | 抽卡是核心页面，但改动面大，需先做设计确认 |
| 5 | U-05 补齐三种纹样 | 待设计 | 属创意工作，需先对齐稿 |

> U-03 / U-05 属视觉创意改动，按项目约定应先走 `/brainstorming` 流程再动手。

---

## 7. 方法与边界

- 本轮为**只读审查**，未改动任何生产代码。工作区唯一的 `M` 标记文件（`AppTheme.kt`）是本轮之前既有的在制改动，非本次产生。
- 实证手段：`:app:compileDebugKotlin` 编译验证、Python 32 位整数语义模拟（溢出与循环终止）、`data.json` 结构化对账、`grep` 调用点检索、关键文件逐行阅读。
- 复核边界：标注 📋 的条目来自并行审查员报告，本轮未逐条复核（主要集中在 A 组的 `MilanAudio`/`SpeechPlayer`/`CrashReporter` 内部细节与 C 组的分配热点计数），建议实施前按第 5 节的方法补一次快速取证。
- 编译与测试基线：**当前工作区编译失败**（R4-01），因此无法给出「测试全绿」的基线声明；HEAD（`e37bda3`）状态为 257 测试全绿。

### 附：U-01 构建验证受阻说明（2026-08-30 收尾时）

U-02 / U-04 已通过 `compileDebugKotlin` 验证（BUILD SUCCESSFUL）。U-01 的代码与字体文件改动也已完成，但**最终编译验证未能执行**——构建环境在当天第 10 次左右 gradle 调用后发生连锁故障：

1. `app/build/intermediates/**` 出现 `AccessDeniedException`；
2. 继而 `.gradle/9.5.0/fileChanges/last-build.bin` 无法更新、daemon 通信失败（`Could not receive a message from the daemon`）；
3. 最终卡在 `.gradle-home/wrapper/dists/gradle-9.5.0-bin/*/gradle-9.5.0-bin.zip.lck`（拒绝访问），`:app:clean` 与 `--stop` 均无效。

**根因**：沙箱内反复运行 gradle 累积破坏了文件 ACL（与 MAUI 项目「必须在非沙箱环境构建」是同一类问题，记忆中已有记录）。

**恢复方式**（需人工执行）：删除 `MilanKotlin/app/build` 与 `MilanKotlin/.gradle-home` 两个目录后重跑
`pwsh -NoProfile -File .\run-gradle.ps1 :app:testDebugUnitTest :app:assembleDebug`（建议带非沙箱执行）。
删除请求在本次会话中已被拒绝，故未执行；重跑后即可验证 U-01 并确认 264 测试仍全绿。
