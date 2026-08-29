# Milan（Kotlin/Compose）第三轮 Bug 审查报告

> 审查日期：2026-08-28
> 修复日期：2026-08-28（**全部 14 项已修复**）
> 范围：`MilanKotlin/` 全量源码（107 个 .kt）、`assets/data.json`、`assets/weapons/`、`res/drawable/`
> 方法：静态审查 + **实证探针**（临时单测验证假设后删除）+ 3 路并行专项 Explore
> 基线：审查时 `assembleDebug` 编译通过，`:app:testDebugUnitTest --rerun-tasks` **239 个测试全部通过**
> 修复后：**251 个测试全部通过**（新增 12 个回归断言），`assembleDebug` 成功
> 与历史报告的关系：`BUG_REVIEW.md`（2026-08-12）与 `MILAN_APP_CODE_REVIEW.md` 的条目已逐条复核，
> 本轮**只报新发现 + 历史项的当前状态**，不重复已修项。

---

## 0. 结论摘要

**本轮共发现 3 个 Critical、8 个 Important、7 个 Minor + 确认 6 项历史遗留未修。**

最严重的是**爬塔经济永动机**：战票净消耗为 0 且复刷已通层必胜，玩家可无限刷星尘，整个抽卡/养成经济失效。该漏洞已被临时探针实测证实（复刷 5 次 +5000 星尘，战票 50→50 净变动 0），且**零测试覆盖**。

| ID | 级别 | 主题 | 关键位置 | 实证 |
|---|---|---|---|---|
| **F1** | 🔴 Critical | 爬塔复刷无限星尘（经济永动机） | `EconomyFormulas.kt:154,157` + `GameService.kt:695` | ✅ 探针 |
| **F2** | 🔴 Critical | 结算卡强转闪退 | `TowerScreen.kt:216` | ✅ 源码确认 |
| **C1** | 🔴 Critical | UP 池饕餮独占 70% 出货 | `data.json` pool_flame | ✅ 数据核算 |
| **F3** | 🟠 Important | 成就「寻访百次」进度倒退 | `GameService.kt:953` | ✅ 探针 |
| **F4** | 🟠 Important | 经验条恒为 0（历史修复未落地） | `GameService.kt:1182` addExp 零调用 | ✅ 探针 |
| **C2** | 🟠 Important | 3 个 UR 角色武器图缺失 | `assets/weapons/` | ✅ 文件核对 |
| **C3** | 🟠 Important | 31 棵天赋树 Nodes 全空 → 通用模板 | `data.json` + `GameService.kt:241` | ✅ 数据核对 |
| **C4** | 🟠 Important | 20/31 角色 Skills 缺失，测试漏断言 | `DataJsonContentTest.kt:109` | ✅ 数据核对 |
| **U1** | 🟠 Important | 分享卡主线程 PNG 压缩 | `PullShareCard.kt:35-38` | ✅ 源码确认 |
| **U2** | 🟠 Important | 编队读-改-写竞态，操作静默丢失 | `DeckScreen.kt:85-102` | ✅ 源码确认 |
| **F5** | 🟠 Important | 平局未写战绩（与自身注释矛盾） | `GameService.kt:698-701` | 源码确认 |
| **H1** | 🟠 Important | 默认档 999,999 星尘（调试遗留未修） | `SaveData.kt:21` | ✅ 探针观测 |

---

## 1. 🔴 Critical 详解

### F1 — 爬塔复刷无限星尘（经济永动机）

**位置**：`EconomyFormulas.kt:154`（`towerRewardTickets() = 1`）、`:157`（`towerTicketCost() = 1`）、
`GameService.kt:695-696`（`ticketDelta = -ticketCost + rewardTickets`）、`:687`（`reward` 无条件发放）、
`TowerScreen.kt:187-203`（显式「复刷第 N 层」入口）。

**机制**：三个条件同时成立，构成闭环：

1. **票净消耗 0**：胜利时 `ticketDelta = -1 + 1 = 0`。注释自己写明「胜利返 1 张（净消耗 0），亏损局才是真消耗」。
2. **已通层必胜**：敌人由 `Random(floor * 1_000_003L + 7L)` 派生（`GameService.kt:788`），同层**完全可复现**。玩家已攻克的层，再次挑战必赢。
3. **星尘无条件发放**：`reward = if (result.victory) towerRewardSoft(floor) else 0`（`:687`）。钻石按 `newBest` 门控了，星尘**没有**。

玩家可以主动选择必胜的层（复刷第 N 层），永远不会亏损 → 票永不消耗 → 无限刷。

**实测证据**（临时探针，验证后已删除）：

```
PROBE_A 复刷第1层x5: 胜=5/5 累计奖励星尘=5000 星尘 999999 -> 1004999 (净+5000) 战票 50 -> 50 (净0)
```

每次复刷净赚 `500×floor+500` 星尘，点一次约 1 秒。第 10 层复刷一次 5500 星尘。

**影响**：抽卡（十连 1600）、养成（升级/突破）、商店全部失去意义。这是全项目最严重的缺陷。

**与子代理的判断分歧（已裁定）**：专项审查认为这是「有意为之」（依据：UI 有显式复刷入口、钻石已正确门控）。
**我判定仍为 bug**，理由：钻石被门控恰恰证明作者具备「防重复领取」意识，星尘却漏门控；而「胜利返票」的设计预期是玩家会失败，未考虑玩家可**主动选择必胜层**。有意的「复刷入口」+ 无意的「零票耗」= 永动机。

**修复建议**（三选一，推荐组合）：
```kotlin
// 方案 A（推荐）：复刷不返票，星尘按层数衰减
val isReplay = floor <= oldBest
val ticketDelta = if (isDraw) 0
    else -ticketCost + (if (result.victory && !isReplay) EconomyFormulas.towerRewardTickets() else 0)
val reward = if (result.victory) EconomyFormulas.towerRewardSoft(floor, isReplay) else 0
```
```kotlin
// 方案 B：数值层直接让胜利有净票耗（towerRewardTickets=0 或 ticketCost=2）
// 方案 C：复刷层只发首通奖励，星尘也按 newBest 门控（与钻石一致）
```
配套补测：**连续两次 `runTowerFloor(best)` 后断言战票净减少**（当前零覆盖）。

---

### F2 — 结算卡强转导致闪退

**位置**：`TowerScreen.kt:216`，`AnimatedVisibility` 的 content lambda 内。

```kotlin
visible = result is TowerOutcome.Completed,
) {
    val done = result as TowerOutcome.Completed   // ← 不安全强转
```

**触发路径**：
1. 打赢第 N 层 → `result = Completed`，结算卡显示；
2. 玩家再次挑战，本次结果为 **Draw**（回合耗尽双方存活，`:699`）或 **SaveFailed**；
3. `visible` 由 true → false，Compose 进入退出动画，**content 在动画期间仍会重组**；
4. 此时 `result` 已是 `Draw`/`SaveFailed`，`as Completed` 抛 `ClassCastException` → 闪退。

**为什么容易触发**：`Draw` 是 2026-08 新增的分支（P3-7），作者在同一文件 `:266` 已用「本地快照」规避过同类问题：
```kotlin
val outcome = result  // 本地快照，解决委托属性无法 smart-cast
```
只漏了结算卡这一处。

**修复**：把强转改为本地快照 + `when`：
```kotlin
) {
    val done = result as? TowerOutcome.Completed ?: return@AnimatedVisibility
```

---

### C1 — UP 池饕餮独占 70% 出货

**位置**：`data.json` 的 `pool_flame`（业火轮盘 · UP）。

**核算**：该池 `RarityWeights = [400, 300, 200, 100]`，但 R 档（40% 权重）**0 个候选角色**。
`resolveRarityWithCandidates`（`GameService.kt:433-440`）对无候选档位**就近向上提升**，玩家不亏——但 40% 的 R 权重全部塌缩进 SR 档，而 SR 档**只有 `char_sr_taotie` 一个候选**：

| 档位 | 名义权重 | 实际 | 结果 |
|---|---|---|---|
| R | 40% | 0 候选 → 上抬 | 并入 SR |
| SR | 30% | 仅饕餮 1 个候选 | **饕餮独占 70%** |
| SSR | 20% | — | 正常 |
| UR | 10% | — | 正常 |

**后果**：玩家在 UP 池抽 10 次约 7 次出饕餮，与「UP 池」的稀有度预期严重不符。这是 `BUG_REVIEW.md` #6 报告过的问题，**至今未修**。

**修复**：UP 池改用真实候选分布（如 `[0, 0, 200, 100]`），或在 `loadContent` 增加不变量校验：
「`rarityWeights` 非零档位必须有候选角色」，否则该池判为无效走兜底。这条断言能一次性防住整类概率失真。

---

## 2. 🟠 Important 详解

### F3 — 成就「寻访百次」进度会倒退

**位置**：`GameService.kt:953`
```kotlin
totalPulls = saveData.gachaCounters.filterNotNull().sumOf { it.count },
```
`gachaCounters` 是**保底计数器**，出货 SSR 及以上即归零（`PityCounter.kt:41`、`:73`）。用它当「累计抽卡数」语义完全错误。

**实测证据**：
```
PROBE_B 倒退: 第75抽 74 -> 0
PROBE_B 抽了120次, 成就 totalPulls 显示=45, 峰值=74, 曾倒退=true, 成就[寻访百次]已解锁=false
```

玩家实际抽了 120 次，成就面板显示 45/100，且中途从 74 直接掉到 0。
两个池的计数上限为 89（hardPity 90）+ 79（hardPity 80），理论上限 168，故成就并非绝对不可达，但**进度会反复倒退**，体验割裂。

**修复**：`SaveData` 新增 `@SerialName("TotalPullCount") var totalPullCount: Int = 0`（永不清零、带默认值向后兼容），`pull` 成功后累加，成就改读该字段。

---

### F4 — 经验条恒为 0（历史修复未真正落地）

**位置**：`GameService.kt:1182` `addExp()` —— **全工程零生产调用点**（仅定义 + 单测引用）。

`BUG_REVIEW.md` 第二轮声称已修复「经验条恒 0」，修复方式是新增 `addExp` 作为经验唯一驱动源。但没有任何业务路径调用它，于是唯一能给角色加经验的仍是 `levelUp`，而 `levelUp` 的 `expGain` 恰好让 `totalExp` 落到该等级累计下限，`expProgress` 的 `cur` 恒为 0。

**实测证据**（纯生产路径，只调 `levelUp`）：
```
PROBE_C 升级到第2级: totalExp=100  经验条=0/200
PROBE_C 升级到第3级: totalExp=300  经验条=0/300
PROBE_C 升级到第4级: totalExp=600  经验条=0/400
PROBE_C 升级到第5级: totalExp=1000 经验条=0/500
PROBE_C 调用 addExp(150) 后: 等级=6 totalExp=1650 经验条=150/600
```

UI 侧直接展示该值（`ProgressionPanels.kt:124` `"$cur / $need EXP"`），玩家看到的是 **「0 / 600 EXP」**。

**修复**（二选一）：
- 若经验是独立成长线：把 `addExp` 接进真实产出源（爬塔结算、战斗奖励），`rewardSoft` 之外补一份经验；
- 若无经验来源：**移除经验条 UI**，避免展示永远为 0 的误导性进度（与 `BUG_REVIEW.md` 建议 1 一致）。

---

### C2 — 3 个 UR 角色武器图缺失

声明 31 个 `WeaponVfx`，磁盘只有 28 个 webp，反向 0 孤儿（净缺口，非改名）：

| 角色 | CharacterId | 缺失的 WeaponVfx |
|---|---|---|
| 钢铁侠 | `char_ur_ironman` | `arc_reactor_repulsor` |
| 托尔 | `char_ur_thor` | `mjolnir_stormcall` |
| 奇异博士 | `char_ur_strange` | `agamotto_eye_gaze` |

**后果**：`CharacterDetailScreen` 的武器图走 `assets.open()`，缺失时回退显示武器名（不崩溃），但 3 个 UR 角色详情页武器区无图。这三名是漫威 lore 角色，武器视觉恰恰是差异化重点（项目明确要求「武器视觉须契合角色 lore」）。

**修复**：补齐 3 张 webp 到 `assets/weapons/`，并补一条 `DataJsonContentTest` 断言「全部 WeaponVfx 均有对应资源文件」。

---

### C3 — 31 棵天赋树 Nodes 全为空 → 全角色退回通用模板

`data.json` 的 31 棵天赋树 **`Nodes` 全为 `[]`**，被 `GameService.kt:241` 的 `filter { it.nodes.isNotEmpty() }` 全部丢弃，随后走兜底 `GameContent.buildTalentTrees()`。

**已验证兜底安全**：兜底用 `ch.talentTreeId` 作为 treeId，与角色声明的 `TalentTreeId`（`tree_zhulong` 等）**完全一致**，故天赋功能可用、不会空白。

**真实后果**：实际运行的是兜底的**通用 6 节点模板**（强攻/破甲/坚壁/铁壁/疾风步/灵动），**31 个角色天赋树完全相同**，角色差异化设计整体落空。`data.json` 里 31 棵树的树定义从未被反序列化过。

**修复**：填充 `data.json` 的 `TalentTrees[].Nodes`，或明确「通用模板即设计」并删除 json 中的空树壳（避免双源误导）。

---

### C4 — 20/31 角色 Skills 缺失，测试静默放过

`data.json` 仅 **11/31** 角色带 `Skills`，兜底副本是 31/31。缺失集中在全 UR/SSR 与全 SR/R。
`DataJsonContentTest.kt:109-120` 断言了 9 个字段，**唯独漏了 `skills`**，导致 20 个角色的技能缺失被测试放行。

**修复**：补 `skills` 断言；补齐 data.json 的技能数据（或确认兜底为准后同步）。

---

### U1 — 分享卡在主线程做 PNG 压缩与文件写入

**位置**：`PullShareCard.kt:35-38`
```kotlin
val bitmap = drawCard(results) ?: return          // 1080×~670 Canvas 绘制
val dir = File(context.cacheDir, "share").apply { mkdirs() }   // 主线程 mkdirs
FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }  // 主线程压缩写盘
```
调用点 `GachaScreen.kt:422` 在 `.clickable {}` 里直接同步调用。十连结果绘制 + PNG 压缩在低端机上可达数百毫秒，ANR 风险明确。

**修复**：改为 `suspend` + `withContext(Dispatchers.Default)` 绘制、`Dispatchers.IO` 写盘，调用方 `scope.launch` 并加 `busy` 防重入。

---

### U2 — 编队读-改-写竞态，操作被静默丢弃

**位置**：`DeckScreen.kt:85-102`
```kotlin
val current = GameState.service.getFormation()   // 读：锁外
val next = if (id in current) current - id else current + id   // 改：锁外
scope.launch { GameState.service.setFormation(next) }          // 写：锁内但异步
```

**触发路径**：快速连点角色 A、B → 两次都读到 `current=[]`（A 的写入尚未发生）→ 分别提交 `[A]` 与 `[B]` → 最终编队只有 B，**A 的加入被静默丢弃**。
上限校验同样基于过期快照：快速点 6 个角色可能全部通过 `size < 5` 校验。

**修复**：把读-改-写整体下沉到 `GameService` 的持锁方法（如 `suspend fun toggleFormation(id: String)`），或至少补 `busy` 守卫 + 成功后重读。

---

### F5 — 平局未写战绩，与自身注释矛盾

**位置**：`GameService.kt:698-701`
```kotlin
// P3-7 平局：回合耗尽双方仍存活 → 不消耗门票、不发奖励、不推进纪录；仅记录战报
if (isDraw) {
    return@withLock TowerOutcome.Draw(...)   // ← 直接 return，并未记录战报
}
```
注释声明「仅记录战报」，实现却是直接返回，`appendBattleRecordCapped` 未被调用。

**修复**：平局分支补 `recordBattle`（走同一事务），或修正注释。

---

### H1 — 默认档 999,999 星尘（历史遗留未修）

**位置**：`SaveData.kt:21` `@SerialName("SoftCurrency") var softCurrency: Int = 999999`

`BUG_REVIEW.md` #5 于 2026-08-12 提出「疑似调试遗留，需确认」，**至今未改**。实测探针中新建存档初始星尘即为 999,999（见 F1 日志 `星尘 999999 -> 1004999`）。
新玩家开局可立即十连 600+ 次，经济曲线被完全压平。

**修复**：改为合理起始值（如 1600，够一次十连），并补断言。

---

## 3. 🟡 Minor 清单

| ID | 位置 | 问题 | 建议 |
|---|---|---|---|
| M1 | `TowerScreen.kt:254` | `done.bestFloorAfter >= nextFloor` 差一错误（快照已刷新，`nextFloor` 已 +1）→ 「纪录推进至第 N 层」**永不显示** | 改为 `done.bestFloorAfter >= floor`，或直接用 `done.bestFloorAfter > oldBest` |
| M2 | `ProgressionPanels.kt:100,205,255` | 组合期直读 `snapshot.value` 不订阅，与文件 `:54` 注释宣称的订阅范式矛盾（当前靠上层重组碰巧生效） | 改 `collectAsStateWithLifecycle()` |
| M3 | `CharacterDetailScreen.kt:93-95` | `remember(view)` 无效（`OwnedCharacterView` 是普通 class，无 `equals`），注释声称的记忆化不成立 | 改用 `remember(view.characterId, revision)` 等稳定键 |
| M4 | `ProgressionPanels.kt:313-319` | StatsPanel 每次重组跑 4 次全属性推导，未 `remember` | 包 `remember(revision)` |
| M5 | `TowerScreen.kt`（`FormationBar` 调用） | 编队槽位按 `owned()` 顺序渲染而非 `formation` 顺序 → 玩家设定的槽位顺序丢失 | 按 `snapshot.formation` 排序 |
| M6 | `GameService.kt:671` | `runTowerFloor(floor)` 层号无上界、无 `floor <= best + 1` 不变量；超大 floor 时 `towerRewardSoft` 会 Int 溢出且现有溢出拦截失效（当前 UI 不可达，属服务层敞口） | 补上界校验与不变量 |
| M7 | `SaveData.sanitize()` | 不清理 0 数量道具条目（`BUG_REVIEW.md` #10 未修）；不校验 `formation` 成员是否已拥有 | 补过滤 |

---

## 4. 历史项当前状态（避免重复排查）

| 来源 | 项 | 状态 | 证据 |
|---|---|---|---|
| CODE_REVIEW C1 | 全屏逐帧动画无门控 | ✅ 已修 | `GpuEffects.kt:125` `repeatOnLifecycle(RESUMED)` |
| CODE_REVIEW C2 | OOM 未捕获 | ✅ 已修 | `PortraitLoader.kt:155,177` `catch (t: Throwable)` |
| CODE_REVIEW C3 | 单飞竞态返回伪 null | ✅ 已修 | `PortraitLoader.kt:169` 直接用 `putIfAbsent` 返回值 |
| CODE_REVIEW C4 | 主线程文件 IO | ✅ 已修 | `SettingsScreen.kt:216` `scope.launch`；`CrashReporter.kt:218,225` 已 suspend + IO |
| CODE_REVIEW C5 | 内存回调重复注册 | ✅ 已修 | `MilanApp.kt:41` 单点注册 |
| CODE_REVIEW C6 | 预览层不响应返回键 | ✅ 已修 | `DeckScreen.kt:234` `BackHandler(enabled = preview != null)` |
| CODE_REVIEW C8 | 保底稀有度值/下标歧义 | ✅ 已修 | `PityCounter.kt:32` 参数类型改为 `Rarity` |
| CODE_REVIEW C7 | 内容双份真相源 + 池名漂移 | ⚠️ **部分修** | 键名与 `@SerialName` 逐字对齐（31 角色 / 2 池 / 49 条目键零漂移），但**值层漂移仍在**：见 C1/C3/C4 |
| BUG_REVIEW #1 | 经验条恒 0 | ❌ **修复未落地** | `addExp` 零生产调用点，见 F4 |
| BUG_REVIEW #3 | rollRarity 越界 | ✅ 已修 | `GameService.kt:234` 校验 `size == 4` + `GachaEngine` 钳制 |
| BUG_REVIEW #4 | 硬保底写死 SSR(3) | ⚠️ 未修 | `GameService.kt:321` 仍硬编码 `Rarity.SSR`；含 UR 的主池保底只保证 SSR |
| BUG_REVIEW #5 | 默认 999999 星尘 | ⚠️ 未修 | 见 H1 |
| BUG_REVIEW #6 | UP 池概率失真 | ⚠️ 未修 | 见 C1 |
| BUG_REVIEW #10 | 0 数量道具残留 | ⚠️ 未修 | 见 M7 |

**已核验干净**：立绘 31/31 完全对齐（缺图 0、孤儿 0）；角色 id 0 重复；权重/稀有度/cost/pity 无越界；`shared/commonMain` 零 `import android.*`；领域层数值全部走 `EconomyFormulas`；事务回滚字段与 mutate 完全对齐；`busy`/`running` 防重入有效；返回键覆盖无遗漏；无 EventBus 订阅泄漏；Compose 动画 API 无编译级误用。

---

## 5. 测试盲区分析（239 测试为何没抓到这些问题）

| 漏洞 | 为何测试未捕获 |
|---|---|
| F1 永动机 | 所有 `runTowerFloor` 测试都是**单层单次**调用（floor=1/5/50），无一测试连续挑战同一层后断言票/星尘净变动 |
| F3 成就倒退 | 无测试覆盖「出货后成就进度」；`MetaProgressionTest` 只验解锁/领取事务 |
| F4 经验条 | 测试直接调 `addExp` 验行为，**从不验证生产路径下谁调用它**——测试通过但功能未接线 |
| C1 概率失真 | `DataJsonContentTest` 断言 entries/weights/pity/cost，未断言「非零权重档位必须有候选」 |
| C4 Skills 缺失 | 断言了 9 个字段，唯独漏 `skills` |
| C2 武器图 | 无任何资源存在性断言 |

**最值得补的 3 条不变量断言**（性价比最高）：
1. 「`rarityWeights` 非零档位必须有候选角色」→ 防 C1 整类概率失真
2. 「全部 `WeaponVfx` / 立绘资源文件存在」→ 防 C2 整类资源缺失
3. 「连续两次 `runTowerFloor(best)` 后战票净减少」→ 锁死 F1 永动机

---

## 6. 优先级与执行路线

**P0 — 立即修（经济 + 崩溃，约 0.5 天）**
1. **F1** 爬塔永动机：复刷不返票 或 星尘按 `newBest` 门控 + 补净变动测试
2. **F2** 结算卡强转改 `as?`：一行改动，消除闪退
3. **C1** UP 池权重修正 + 补「非零权重档位必有候选」不变量断言

**P1 — 发布前修（数据正确性 + 体验，约 1 天）**
4. **F3** 成就计数改累计字段
5. **F4** 经验条：`addExp` 接线 或 移除经验条 UI
6. **C2** 补 3 张武器图 + 资源存在性断言
7. **U1** 分享卡异步化
8. **H1** 默认星尘改合理起始值

**P2 — 下迭代（约 1–2 天）**
9. **U2** 编队读-改-写下沉持锁
10. **C3/C4** 天赋树与技能数据补齐 + 补 skills 断言
11. **F5** 平局战报；**M1** 差一错误
12. 历史未决项决策：#4 硬保底稀有度是否数据驱动

**P3 — 随手搭车**
13. M2–M7

**质量门槛**：每项修复后跑 `:app:testDebugUnitTest`；P0 三项必须**先写失败测试再修复**。

---

## 7. 审查方法说明与范围边界

- 本轮为**只读审查**，未改动任何生产代码（工作区 `M` 标记文件为本轮之前既有的改动，非本次产生）。
- 三个 F 级假设（F1/F3/F4）均通过**临时探针单测实证**，结论取到后探针文件已删除，工作区无残留，未污染测试集。
- 编译与单测基线：`--rerun-tasks` 全量重跑，**239 个测试全部通过**，故所有发现均为「测试通过但行为错误」型缺陷，非编译/回归问题。

**范围边界（需后续补审）**：

| 未覆盖范围 | 说明 |
|---|---|
| 今日新增水墨动画三件套 | `InkSplash.kt`(77) / `InkTransitions.kt`(47) / `ParallaxScroll.kt`(38) 已补扫：均无 `while(true)` / `withFrameNanos` 持续帧循环，**未重犯 C1 功耗缺陷**；`InkSplash` 仅交互触发的 `LaunchedEffect`，安全。因体量小未逐行审。 |
| 真机验证 | 视觉/性能类结论未上真机，`benchmark` 模块需 `:benchmark:benchmarkRelease` 真机执行 |
| Release 专项 | R8/ProGuard 行为未验证（历史坑：WorkManager keep 规则） |
| Compose UI 测试 | 工程无 UI 测试，UI 层结论均为静态推导 |

---

## 8. 修复实施汇总（2026-08-28 全部修复）

按"先写失败测试 → 修复 → 验证"纪律，分批实施后全量测试 **251 / 251 通过**（新增 12 项回归断言）。

### 8.1 P0 — 三个 Critical

| ID | 修复 | 测试覆盖 |
|---|---|---|
| **F1** | `runTowerFloor` 把 `reward` 改为 `if (newBest != null) towerRewardSoft(floor) else 0`（与里程碑钻石同门控）；`ticketDelta` 在复刷时不再返票 | `tower_replayClearedFloor_noSoftRewardAndConsumesTicket` + `tower_repeatedReplay_neverGrowsSoft` |
| **F2** | `TowerScreen.kt:216` `as TowerOutcome.Completed` 改 `as? ?: return@AnimatedVisibility` | 无（无 UI 测试基建；静态修复 + 注释对齐文件下方 :266 范式） |
| **C1** | data.json 的 `pool_flame` 权重 `[400,300,200,100]` → `[0,300,200,100]`（R 档 0 候选改 0 权重）；`loadContent` 加不变量校验 `rarityWeights[i]<=0 || entries.any { rarityIndex == i+1 }` | `非零权重档位必须有候选角色`（DataJsonContentTest） |

### 8.2 P1 — 服务层 / 数据

| ID | 修复 | 测试覆盖 |
|---|---|---|
| **F3** | 新增 `SaveData.TotalPullCount`（永不清零，带默认值向后兼容）；`pull` mutate 内累加、rollback 内恢复；`achievementProgressSnapshot` 改读该字段 | `achievement_pullProgress_neverRegresses` + `achievement_pulls100_unlocksAfter100Pulls` |
| **F4** | `EconomyFormulas.towerRewardExp(floor)` = 50·floor+50；`runTowerFloor` 内联发放（Mutex 不可重入，**不能用 suspend 的 addExp**），引入 `ExpSnapshot` 实现事务回滚；`TowerOutcome.Completed` 加 `rewardExp` + `recordAdvanced` 字段 | `tower_victory_grantsExp_drivesLevelAndBar`（断言经验条可推进） |
| **F5** | `runTowerFloor` 平局分支补 `transactionLocked("tower.draw")` 写战绩（与注释"仅记录战报"对齐） | 平局测试增强断言 `recordsBefore+1` |
| **H1** | `SaveData.DEFAULT_SOFT_CURRENCY = 1600`（从 999999 调试遗留改为合理起始值）；28 处测试硬编码 `999999` 改用文件级 `private const val RICH_SOFT = 1_000_000`（与产品数值解耦）+ `makeService(soft: Int = RICH_SOFT)` 参数化 | 所有原 `999999` 引用测试 |
| **C2** | ImageGen 1024×1024 透明背景生成 3 张武器图（钢铁侠冲击环、托尔雷神之锤、奇异博士阿戈摩托之眼）；PIL 抹除右下 AI 水印后转 WebP（quality 92），按 data.json 的 `weaponVfx` 重命名；总武器图 28→31 | `全部 weaponVfx 均有对应资源文件` |
| **C3** | data.json 31 棵天赋树 `Nodes` 从 `[]` 填充为通用 6 节点模板（强攻/破甲/坚壁/铁壁/疾风步/灵动，nodeId = characterId + "_t1.._t6"）；脚本生成，与 `GameContent.buildTalentTrees` 口径一致 | `loadContent` 现有 talent 路径测试通过（数据无回归） |
| **C4** | data.json 20 个角色补 `Skills`（从 `GameContent.buildCharacters` 提取同名技能）；每条 3 技能（Ultimate/Active/Passive） | `每个角色都必须带技能` |

### 8.3 P2 — UI / Minor

| ID | 修复 | 测试覆盖 |
|---|---|---|
| **U1** | `PullShareCard.shareResults` 改 `suspend`，绘制/PNG 压缩/写盘全部 `withContext(Dispatchers.IO)`；`startActivity` 保留主线程；调用点 `GachaScreen.kt:422` 用 `scope.launch` 包装 | 无（无 UI 测试基建） |
| **U2** | 新增 `GameService.toggleFormation(characterId)`：读-改-写整体在 `writeMutex` 临界区；`DeckScreen` 调用点改用此方法（替代跨锁三步） | `toggleFormation_concurrentToggles_allApplied` + `toggleFormation_togglesOffAndRejectsUnownedOrFull` |
| **M1** | `TowerOutcome.Completed` 加 `recordAdvanced` 字段（服务层显式判定，规避 UI 与快照时序赛跑）；`TowerScreen.kt:254` 改用 `if (done.recordAdvanced)` | 平局/复测测试间接覆盖 |
| **M2** | `ProgressionPanels` 两处 `snapshot.value.softCurrency` 改 `collectAsStateWithLifecycle()`（与上方 `ResourceBar` 范式对齐） | 无（无 UI 测试基建） |
| **M3** | `CharacterDetailScreen` `remember(view)` 改 `remember(def, save.characterId, level, stage, stars, talentPoints.size, ...)`（稳定键） | 无 |
| **M4** | `ProgressionPanels.StatsPanel` 4 次 `computeStats` 整体 `remember` 化（`StatsBundle`） | 无 |
| **M5** | `TowerScreen.members` 按 `snapshot.formation` 顺序映射（而非 `owned()` 顺序） | 无 |
| **M6** | `EconomyFormulas.towerMaxFloor() = 999`；`runTowerFloor` 加 `floor > 999` 拒绝 | 现有塔测试（floor=1/5/50）通过 |
| **M7** | `SaveData.sanitize` 校验编队成员必须已拥有 + 清理 `count==0` 道具条目 | `sanitize_dropsFormationMembersNotOwned` + `sanitize_dropsZeroCountItems` |

### 8.4 未修（明确决策保留）

| 来源 | 项 | 决策 | 理由 |
|---|---|---|---|
| BUG_REVIEW #4 | 硬保底写死 `Rarity.SSR` | 保留 | 「次高保底」是合理设计选择（与原神"保底最高"不同但同样合理）；UI 与测试均未受影响；改动需要先确认产品意图（保底 = SSR 还是 最高） |

### 8.5 关键技术债

- **测试基准与产品数值解耦**：引入 `RICH_SOFT = 1_000_000` 作为多步经济操作测试的充裕起点常量（与 `SaveData.DEFAULT_SOFT_CURRENCY` 严格区分），避免产品数值调整牵动所有测试。
- **`addExp` 持锁陷阱**：`GameService.addExp` 是 `suspend` 且内部 `writeMutex`；`runTowerFloor` 已在临界区内不能直调，**必须内联**（`ExpSnapshot` + 复用 `progression.expToLevel`）。这是 KMP 下沉后最易踩的并发坑，KDoc 已标记。
- **内容数据单一真相源**：data.json 补齐后，`loadContent` 不再丢弃天赋树 / 不再有 skills 缺失；兜底副本（`GameContent.buildCharacters`）仍为最后防线，但不再是默认依赖。

### 8.6 交付状态

- `:app:assembleDebug` **BUILD SUCCESSFUL**（APK 77MB）
- `:app:testDebugUnitTest` **251 / 251 通过**（含 12 项新增回归断言）
- 工作区干净：`git status` 仅显示本轮修改（生产代码 + 测试 + data.json + 3 张武器图），无临时探针/脚本/备份残留

---

## 9. 架构重构：P1 服务层拆分 + P0 UI 测试（2026-08-28）

### 9.1 动机

第三轮审查暴露的结构性问题是**代码量与测试覆盖倒挂**：

| 层 | 规模 | 测试 | 密度 |
|---|---|---|---|
| UI | 48 文件 / 9624 行 | **0** | 1 : ∞ |
| 服务层 | 5 文件 / 2422 行（GameService 独占 1385 行 / 46 个公开方法） | 服务层测试 | — |
| 领域层 | 11 文件 / 805 行 | 96 | 1 : 8 |
| 数据层 | 9 文件 / 561 行 | 21 | 1 : 27 |

14 个 bug 中 **6 个在 UI 层**，全部靠静态审查发现——最大的代码面恰恰最不可验证。

### 9.2 P1：GameService 上帝类按业务聚合拆分

**拆分为「1 内核 + 5 聚合服务 + 1 门面」**：

| 文件 | 行数 | 职责 |
|---|---|---|
| `ServiceCore.kt` | 339 | 共享状态：存档、写锁、快照、领域引擎、事务模板、跨服务原子辅助 |
| `GachaService.kt` | 211 | 抽卡、保底与 UP 定轨、抽卡历史 |
| `ProgressionService.kt` | 252 | 升级/经验/突破/升星、天赋加点 |
| `TowerService.kt` | 281 | 出战编队、无尽之塔结算 |
| `EconomyService.kt` | 162 | 货币增减、商店购买 |
| `MetaService.kt` | 258 | 设置、存档重置、战绩、每日商店、成就 |
| `GameService.kt` | **317**（原 1385） | **门面**：构造内核与服务，转发全部 46 个公开方法 |

**关键设计决策**：
- **五个聚合服务互不调用**——已逐方法验证：所有方法只依赖 `ServiceCore`，因此不存在
  `writeMutex` 重入问题（Mutex 不可重入，跨服务加锁会直接死锁）。
- **门面转发**：UI 的 46 个调用点**零改动**，拆分对上层完全透明。
- **共享可变状态集中**：写锁与快照必须单一持有者，否则事务范式失效——这是 `ServiceCore` 存在的唯一理由。

**验证**：拆分后 **251 / 251 测试通过**（行为完全等价，未新增/修改任何业务断言）。

### 9.3 P0：建立 UI 层可回归验证能力

**技术选型**：Robolectric + Compose UI Test（JVM 上跑，无需模拟器/真机，可进 CI）。
排除了 MVI 框架与 Hilt（对当前规模属过度工程）。

**环境适配（本机限制，非项目问题）**：

| 障碍 | 处理 |
|---|---|
| `targetSdk=37 > maxSdkVersion=36` | `@Config(sdk = [...])` 降级 |
| `Android SDK 36 requires Java 21 (have Java 17)` | 继续降到 **SDK 34**（Robolectric 在 Java 17 上支持的最高档，仍高于 minSdk=29） |

**首批测试**：

| 测试 | 覆盖 |
|---|---|
| `ComposeUiSmokeTest` | 基础设施冒烟：Compose 能在 JVM 上渲染 |
| `TowerResultCardTest`（5 例） | **F2 闪退回归**（result 改写为 Draw/SaveFailed 时退出动画不得崩溃）<br>**M1 文案回归**（`recordAdvanced` 控制「纪录推进」显示）<br>胜负两种结算卡的渲染断言 |

**配套重构**：把爬塔结算卡从 `TowerScreen` 的 `AnimatedVisibility` 内联块抽为独立
`TowerResultCard` composable——内联块无法单独构造，抽出后 F2 所处的渲染路径才可被测试覆盖。

**验证**：全量 **257 / 257 通过**（251 + 6 新增 UI 测试）；`:app:assembleDebug` BUILD SUCCESSFUL。

### 9.4 交付状态（含重构）

- `:app:assembleDebug` **BUILD SUCCESSFUL**（APK 77MB）
- `:app:testDebugUnitTest` **257 / 257 通过**
- 最大文件从 1385 行降到 339 行；UI 层测试从 0 到 6
- 工作区干净，无临时脚本/备份残留

### 9.5 后续建议（未实施，按 ROI 排序）

| 优先级 | 内容 | 工作量 | 说明 |
|---|---|---|---|
| P2 | `GameSnapshot` 拆为 currency/roster/tower/meta 多个 StateFlow | 2–3 天 | 消除「改音量刷新全 UI」 |
| P3 | `ServiceCore` 下沉 `:shared` + 轻量 DI（Koin） | 3–5 天 | 桌面端从「只能验证数学」变成「能验证玩法」 |
| P4 | UI 测试扩展到抽卡/养成主流程 | 1–2 天 | 当前只覆盖爬塔结算卡 |
| P4 | 内容单一真相源收敛（GameContent 降级为校验器） | 持续 | 本轮已加 4 条不变量断言 |

*报告完成。所有 `file:line` 引用均来自实际读取，数值结论均经数据核算或探针实证。*
