# Milan 架构优化方案（2026-09-08）

> 评审对象：`MilanKotlin/`，Kotlin 2.4.10 + Compose BOM 2026.06.01，单 Activity + Navigation Compose。
> 本文所有规模数据均为 2026-09-08 实地测量（非估算），可在复核时重跑文末附录命令验证。

---

## 0. 结论摘要

当前架构**主干是健康的**：分层方向正确（UI → 门面 → 领域 → 数据）、领域层实测零 `android.*` 依赖、路由 19:19 无孤儿、聚合服务互不调用的铁律实测无违规、写事务模板 `withWriteLock` 已统一收口。这些都是真实资产，不需要推倒重来。

真正的瓶颈集中在三处，**按影响排序**：

1. **领域概念缺位（可扩展性）** — 一款卡牌游戏里没有 `Card`、没有 `Deck`、没有 `TurnContext` 的领域类型。卡牌 = `data.json` 的 `Characters` 条目 + `CharacterSaveState`；卡组 = `formation: List<String>` 纯 id 数组。核心玩法的语义无处承载。
2. **写放大与快照粗粒度（性能）** — 存档全量 + `prettyPrint=true` 序列化，任何一次货币变动都重写整个存档；而订阅端是 17 个 Screen 全量订阅同一个扁平 `GameSnapshot`，任一 revision 变化触发订阅作用域整体重组。
3. **门面过载 + UI 体量倒挂（可维护性）** — `GameService` 839 行 / 150 方法（68 个 suspend），17 个聚合服务的全部 API 在此手工转发；UI 层 17,354 行是领域层 2,160 行的 **8 倍**，而 33 个测试文件几乎全部覆盖在领域与服务层——改动最频繁的层恰好最无测试保护。

建议按 **P0 立地基 → P1 解耦主线 → P2 边界硬化 → P3 持续度量** 四阶段推进，全文第 5 节给出 13 项重构的优先级、动作与验收标准。

---

## 1. 现状测绘

### 1.1 规模分布

| 层 | 路径 | 行数 | 占比 | 文件数 |
|---|---|---:|---:|---:|
| UI | `ui/` | 17,354 | 55.6% | 70 |
| 服务 | `services/` | 7,816 | 25.1% | 23 |
| 存档 | `data/` | 2,205 | 7.1% | 22 |
| 基础设施 | `infrastructure/` | 1,214 | 3.9% | 8 |
| 领域 | `:shared` commonMain | 2,160 | 6.9% | 18 |
| **合计** | | **31,196** | 100% | **143** |

测试：33 文件 / 5,575 行，基线 **292/292 全绿**。

**一张表读出的问题**：领域层只占 6.9%，却承载着全部业务规则；UI 层占 55.6%，几乎全是渲染与动画。这是典型的「瘦领域、胖表现」，规则会持续向 UI 渗漏。

### 1.2 最大文件（TOP 10）

| 文件 | 行数 | 归属 |
|---|---:|---|
| `ui/gacha/GachaScreen.kt` | 849 | UI |
| `services/GameService.kt` | 839 | 门面 |
| `services/StoryService.kt` | 802 | 聚合服务 |
| `ui/home/HomeScreen.kt` | 800 | UI |
| `services/ServiceCore.kt` | 759 | 状态核心 |
| `ui/tower/TowerScreen.kt` | 752 | UI |
| `services/TowerService.kt` | 672 | 聚合服务 |
| `services/GameContent.kt` | 624 | 内容兜底 |
| `data/SaveData.kt` | 599 | 存档模型 |
| `shared/domain/battle/StrategicBattleSimulator.kt` | 565 | 领域 |

### 1.3 既有架构资产（应保留，勿重构掉）

| 资产 | 实测状态 |
|---|---|
| 领域纯净性 | `:shared` 全量 grep `android.` → **0 命中** |
| 聚合服务隔离 | 17 个服务构造函数仅接收 `(core[, rng])`，**无跨服务调用** |
| 路由完整性 | `Routes.kt` 19 个 `@Serializable` ↔ `MilanNavHost` 19 个 `composable<>`，**1:1** |
| 写事务模板 | 已统一 `ServiceCore.withWriteLock`（出锁后 dispatch，防 Mutex 重入死锁） |
| 快照订阅覆盖 | 19 个 Screen 中 17 个订阅 `snapshot`；仅 `StoryScreen`/`DialogueScreen` 一次性取值 |
| UI 调用纪律 | **无任何 Screen 直调聚合服务**，统一走 `GameState.service` |
| 存档容错 | `fromJson` 永不抛异常，`.bak`/`.tmp` 滚动恢复 |

---

## 2. 瓶颈诊断

编号 **B** 用于下文与第 5 节优先级表交叉引用。

### B1 — `GameService` 上帝门面（可维护性 / 解耦）

- **实测**：839 行、150 个公开方法（68 个 `suspend`），由 17 个 `internal` 聚合服务组成，几乎全为 `= xxxService.xxx()` 转发。
- **后果**：
  1. 新增一个服务方法必须改 2 处（服务 + 门面），且门面是所有特性分支的**合并冲突中心**；
  2. 门面已被迫承担编排职责（`GameService.onProgress` 内调 `dailyMissionService.reportProgress` + `monetizationService.grantBattlePassExp`），编排逻辑与转发逻辑混杂在同一文件；
  3. 无法形成模块边界——任何 Screen 经门面可触达任何 API，编译期无约束。

### B2 — 无 `Card` / `Deck` 领域模型（可扩展性，**本方案最实质的一条**）

- **实测**：全项目 grep `class Card|data class Card|CardData|DeckModel|class Deck` → **0 命中**。
- **现状替代物**：
  - 卡牌 = `data.json` 的 `Characters`（31 条）+ 存档侧 `CharacterSaveState`；
  - 卡组 = `GameSnapshot.formation: List<String>`（纯 id 列表）。
- **后果**：卡组的容量上限、唯一性、阵营/稀有度约束、禁用表、赛季轮换——这些规则**没有落点**，只能散在 Service 的 `if` 与 UI 的 `enabled =` 里。每新增一个玩法就要重新发明一遍约束。持久化形状直接充当领域形状，是后续所有扩展税的来源。

### B3 — 战斗状态范式混杂（可维护性 / 正确性）

- **实测**：`BattleState` 是 `data class`，但内部 `List<BattleUnitState>` 的元素带 `var hp`、`cooldowns: MutableMap`、`buffs: MutableList`；`executePlayerAction(state, action): BattleState` 返回新状态，而内部 `applyEffects(target, ...)` 直接 mutate 传入对象。
- **后果**：`copy()` 是浅拷贝，「不可变快照」名不副实——做撤销、战斗回放、AI 预演、服务端校验都会踩到共享可变子对象。
- **附带**：`StrategicBattleSimulator`（565 行，含完整的 `PLAYER_INPUT → ANIMATING → ENEMY_TURN → CHECK_RESULT` 状态机）**仅** `TowerService.kt:28` 一处实例化，`executePlayerAction` 在 `app/src/main` 零调用。交互式回合逻辑当前是死功能，实际跑的是一次性结算的 `BattleSimulator.simulate()`。

### B4 — 存档写放大（性能）

- **实测**：`SaveData.kt:569` 配置 `prettyPrint = true`，`SaveManager.save()` 走全量 `data.toJson()` + `AndroidSaveProvider` 原子写。
- **后果**：单次写操作的成本与**整个存档规模**（599 行模型，含 31 角色 + 全部子系统嵌套）线性相关，而触发它的可能只是一个货币字段自增。`prettyPrint` 额外放大体积（缩进+换行通常使 JSON 增大 30–50%）。十连抽、爬塔连刷等连续写场景下成本成倍放大，且写操作在 UI 协程里被 `await`。

### B5 — 读路径无锁 + 快照粒度粗（性能 / 正确性）

- **读路径**：`DungeonService.getAbyssData:101`、`TowerService.getTowerStats:632`、`ArenaService.getArenaData`、`MetaService.getBattleRecords` 及各服务 `getXxxData()` —— 均无锁，且直接返回 `saveData` 的子引用。
- **快照粒度**：`GameSnapshot`（`ContentModels.kt:130`，13 个字段）是单一扁平 `data class`。17 个 Screen 全量订阅 → `pushEnabled` 变化也会让订阅作用域内所有 composable 重算。

### B6 — UI 体量倒挂且缺测试保护（可维护性）

- **实测**：UI 17,354 行 vs 领域 2,160 行（**8:1**）；GachaScreen 849 / HomeScreen 800 / TowerScreen 752 行；33 个测试文件集中在领域与服务层，UI 层仅有少量 Robolectric 冒烟。
- **后果**：大 Screen 内 `remember` 密集（HomeScreen 21 处），状态与渲染耦合，无法脱离 Android 环境单测。

### B7 — 内容双份定义（可维护性）

- `assets/data.json` 221 KB（31 角色 / 2 池 / 31 天赋树）为主来源，`services/GameContent.kt` 624 行硬编码同等规模副本为兜底，靠 `sync_gamecontent.py` **手工**同步。drift 只是时间问题，且 drift 表现为「真机与兜底行为不一致」这类难定位缺陷。

### B8 — 模块边界仅靠约定（可扩展性）

- 单 `:app` + 单 `:shared`。17 个聚合服务与 70 个 UI 文件共处一个 Gradle 模块，层间约束（如「UI 不得 import data 层」「领域不得 import android」）**无编译期或 CI 强制**。目前靠 code review 维持，随规模增长必然失效。

### B9 — 周期性重置分散触发（正确性）

- R5-I4 已采用「进页面 `LaunchedEffect` 触发 `ensureTodayReset()`」模式，方向正确，但触发点分散在每个需要重置的 Screen。**新增一个含跨日状态的 Screen 若忘记调用即产生缺陷**，属于靠纪律而非机制保障。

### B10 — 事件驱动未落地（解耦）

- `GameService.onProgress` 手工编排 6 处进度联动，是收敛后的缓解方案而非根治。新增系统需修改编排点，违反开闭原则。

### B11 — 无性能基线与回归网（性能）

- 有 `:benchmark`（Macrobenchmark），但需真机/模拟器；缺少「存档序列化耗时」「写操作 P95」「关键 Screen 重组次数」的自动化度量，性能优化无法验证收益、也无法防回归。

### B12 — 死功能层持续膨胀（可维护性）

- I10/I12 三阻塞项（活动代币缺失、活动激活无路径、装备无获取途径）未修；`StrategicBattleSimulator` 交互 API 零调用。每新增一个「服务层方法 + 无 UI 调用点」都在增加负债面。

---

## 3. 改进目标（可度量）

| 指标 | 现状 | 目标 | 测量方式 |
|---|---|---|---|
| 门面方法数 | 150 | ≤ 40（其余下沉特性接口） | `grep -c 'fun ' GameService.kt` |
| 领域层占比 | 6.9% | ≥ 15% | 分层行数统计 |
| 最大 Screen 行数 | 849 | ≤ 300 | `wc -l` |
| 单次写操作序列化体积 | 全量 + pretty | 增量桶 + 无 pretty | benchmark 计时 |
| 写操作 P95（中端机） | 未测 | ≤ 8 ms | Macrobenchmark / Trace |
| 快照变更引发的重组 Screen 数 | 17（全量） | ≤ 3（按特性切片） | Compose 重组计数 |
| 层间依赖违规 | 无强制 | CI fail-fast | 依赖规则检查 |
| 测试覆盖重心 | 领域/服务 | 新增 VM 层纯 JUnit 测试 | 测试分布统计 |

---

## 4. 推荐分层与模块划分

### 4.1 现状与目标对比

**现状**（4 层，单模块承载）：

```
MainActivity
  └─ ui/ (17,354 行, 70 文件)
       └─ GameState (进程级 object 单例)
            └─ GameService (839 行 / 150 方法) ─── 唯一门面
                 ├─ ServiceCore (759 行: writeMutex + _snapshot + saveData + 事务模板)
                 ├─ 17 个 internal 聚合服务 (互不调用)
                 └─ :shared domain (2,160 行)
                        └─ data/SaveData (599 行) → SaveManager 全量 prettyPrint 写
```

**目标**（6 层，边界由模块系统强制）：

```
L5  ui           纯渲染 + 导航；每个 Screen 配一个 ViewModel；不得 import data/*
L4  feature      按玩法切片：gacha / deck / battle / tower / shop / meta / progression
                 每片 = 公开 API 接口 + 内部实现 + ViewModel；片间禁止横向依赖
L3  state        Store：SaveData 归一化 + 事务 + 快照切片（拆出 ServiceCore 的状态职责）
L2  domain       :shared 纯 Kotlin —— Card / Deck / TurnContext 概念 + 规则引擎
L1  content      data.json 为唯一 SoT；兜底副本构建期生成 + CI 校验
L0  platform     Android 接入：Activity / Application / SaveProvider / 资源
```

### 4.2 Gradle 模块划分建议

| 模块 | 内容 | 依赖 |
|---|---|---|
| `:shared` | domain（battle/gacha/progression）+ `Card`/`Deck`/`Rarity` 模型 | 无（纯 Kotlin） |
| `:core:content` | `data.json` 解析、`enrich`、schema 校验 | `:shared` |
| `:core:state` | `SaveData` 归一化、Store、事务模板、快照切片 | `:shared`, `:core:content` |
| `:core:ui` | 主题、水墨金箔设计 token、共享组件 | `:shared` |
| `:feature:gacha` | 抽卡：引擎接入 + ViewModel + Screen | `:core:*` |
| `:feature:deck` | 卡组/编队/角色详情 | `:core:*` |
| `:feature:battle` | 战斗（回合状态机 + 结算 + 回放） | `:core:*`, `:shared` |
| `:feature:tower` `shop` `meta` `progression` | 同上 | `:core:*` |
| `:app` | 组装：MainActivity / NavHost / DI 图 / 模块编排 | 全部 |

**依赖规则（CI 强制）**：
- `:shared` 不得依赖任何 Android API（现有约束，加 CI 校验）；
- `:feature:*` 之间**禁止**互相依赖；跨特性跳转只经路由；跨特性数据只经 `:core:state` 的快照或 `:shared` 的领域类型；
- `ui` 层不得 import `:core:state` 的内部模型（只能消费快照切片与 ViewModel）。

### 4.3 三个核心建模建议

**① `Card` / `Deck`（补 B2）**

```
// :shared/domain/card/
CardDefinition  静态定义（来自 content）：id / name / rarity / baseStats / skills / element
CardInstance    玩家持有实例：definitionId / level / exp / star / equipment / affinity
Deck            卡组：slots: List<DeckSlot> / capacity / 约束校验 validate(): DeckViolation?
DeckRule        容量、唯一性、稀有度上限、阵营要求 —— 可组合的规则对象
```

要点：`Deck` 是**带约束的值对象**，不是 `List<String>`。约束以 `DeckRule` 组合表达，新增赛季规则 = 新增一个 rule 对象，不改 `Deck` 本体。

**② `TurnContext`（补 B3）**

```
BattleUnitState  →  全部字段改 val，集合改持久/只读类型
TurnContext      本回合上下文：turn / phase / actorQueue / rngSeed / log
BattleStep       sealed：PlayerAction | EnemyAction | Reaction | PhaseChange
reduce(state, step): BattleState   纯函数，返回深不可变新状态
```

要点：`StrategicBattleSimulator` 的内部 mutate 改为「读旧状态 → 产出 `BattleStep` 列表 → `reduce` 折叠出新状态」。副作用（伤害数字、动画事件）从 `StrikeEvent` 列表里出，不藏在对象 mutation 里。这样回放、撤销、AI 预演、未来服务端校验全部免费得到。

**③ 快照切片（补 B5）**

```
GameStore 暴露：
  economy:  StateFlow<EconomySlice>      // 货币/道具
  roster:   StateFlow<RosterSlice>       // 持有角色/编队/图鉴
  progress: StateFlow<ProgressSlice>     // 等级/经验/通行证/任务
  meta:     StateFlow<MetaSlice>         // 设置/战绩/统计
```

Screen 只订阅自己需要的切片；`revision` 仍在全局用于写操作结果判定。改造可渐进：先加切片 StateFlow 与全量 `snapshot` 并存，逐个 Screen 迁移后再移除全量。

---

## 5. 关键重构事项与优先级

图例：★ 收益/成本比最高项。

### P0 — 立地基（止血 + 确立领域概念，约 2–3 周）

| # | 事项 | 对应 | 关键动作 | 验收标准 |
|---|---|---|---|---|
| ★1 | 关闭 `prettyPrint` + 写合并 | B4 | `SaveData.kt:569` 改 `false`；`SaveManager.save()` 加 debounce（如 300 ms 合并窗口）+ 单写者调度器 `Dispatchers.IO.limitedParallelism(1)` | 存档体积下降 ≥30%；连续写场景序列化次数由 N 降为 1；292 测试仍全绿 |
| ★2 | `Card`/`Deck` 领域建模 | B2 | `:shared/domain/card/` 新增上述 4 个类型；`formation: List<String>` 迁移为 `Deck`，旧字段保留 `@Serializable` 兼容 | 新增 `DeckTest`（容量/唯一性/rule 组合）≥ 8 例；存档向后兼容（旧档可载入） |
| 3 | `BattleUnitState` 不可变化 | B3 | `var hp/energy` → `val`；`MutableMap/MutableList` → 只读 + 替换式更新；`applyEffects` 改纯函数 | 新增「同状态两次 `executePlayerAction` 结果一致」「原状态未被 mutate」2 组回归用例 |
| 4 | 快照切片化 | B5 | 新增 4 个切片 StateFlow；先让 Economy/Roster 相关 Screen 迁移 | 货币变更时 Deck/Shop Screen 重组次数为 0（Compose 重组计数验证） |

> P0 的顺序有硬约束：**1 可独立先行**（风险最低、收益立即可测）；**2 与 3 都要先有领域测试再动生产代码**（TDD 红→绿）；4 依赖 2 的切片划分结论。

### P1 — 解耦主线（约 3–5 周）

| # | 事项 | 对应 | 关键动作 | 验收标准 |
|---|---|---|---|---|
| ★5 | `GameService` 拆分 | B1 | 按特性抽出 `GachaApi`/`DeckApi`/`BattleApi`/`ShopApi`… 接口；`GameService` 保留为组合根 + `onProgress` 编排，方法数降至 ≤40 | 门面行数 ≤ 400；新增服务方法不再需要改门面（或仅注册一行） |
| ★6 | 抽 ViewModel，Screen 瘦身 | B6 | 每个 Screen 配 `XxxViewModel`（`StateFlow<XxxUiState>` + `onEvent`）；Screen 只做渲染 | 最大 Screen ≤ 300 行；VM 为纯 JUnit 可测（无 Robolectric）；新增 ≥ 3 个 VM 单测 |
| 7 | 内容单一 SoT | B7 | data.json 为唯一源；`GameContent.kt` 改为构建期生成的产物；CI 加 `sync` 校验脚本 fail-fast | 手工修改兜底副本 → CI 失败 |
| 8 | 时间边界守卫统一 | B9 | 在 `withWriteLock` 入口前置 `ensurePeriodicReset()`，替代各 Screen 的 `LaunchedEffect` 触发 | 移除 ≥4 处分散触发点；新增「跨日状态自动重置」测试 |

### P2 — 边界硬化与扩展（约 4–6 周）

| # | 事项 | 对应 | 关键动作 | 验收标准 |
|---|---|---|---|---|
| 9 | Gradle 多模块 + 依赖规则 | B8 | 按 4.2 拆模块；加依赖规则检查（自定义 Gradle 任务或 konsist/binary-compat 类工具），CI 强制 | 违规依赖 → 构建失败 |
| 10 | 领域事件替代手工编排 | B10 | 定义 `ProgressEvent` 等领域事件 + 订阅表；`onProgress` 编排点改为事件发布 | 新增一个进度联动系统**不改** `GameService` |
| 11 | 死功能层清算 | B12 | 与 I10/I12 合并推进：先修三阻塞项（活动代币 / 活动激活 / 装备获取），再接线；未排期接线的服务层 API 明确标注或直接移除 | 每个服务层 public 方法都有生产调用点或测试覆盖，无第三态 |

### P3 — 持续度量（长期）

| # | 事项 | 对应 | 关键动作 |
|---|---|---|---|
| 12 | 性能基线与回归网 | B11 | 为「存档序列化」「单次写操作」「关键 Screen 重组」各建 benchmark；CI 设阈值告警 |
| 13 | 只读 API 一致性收口 | B5 | 所有 `getXxxData()` 改为从快照切片派生，消除无锁直读可变子引用 |

---

## 6. 风险与不适用项

| 风险 | 说明 | 缓解 |
|---|---|---|
| **存档兼容性** | B2 的 `Deck` 迁移、B4 的序列化格式变更都触碰存档。变更 `prettyPrint` 不影响 schema（安全）；`Deck` 属结构性变更 | 保持 `@Serializable` 旧字段名，新增字段给默认值；`fromJson` 已有 `.bak`/`.tmp` 回退链；每次变更后跑一次真机升级验证 |
| **`writeMutex` 不可重入** | 任何把「已持锁路径」改造成「调用另一个 suspend 服务方法」的重构都会死锁 | P0/P1 所有重构遵守既有铁律：临界区内需发奖励时**内联实现**，不调其他服务的 suspend 方法 |
| **多模块拆分与 AGP 9 / R8** | 拆模块后 `proguard-rules.pro` 的 WorkManager keep 规则需按模块重新确认 | 每次拆模块后必跑 `assembleRelease` + 冷启动验证（该规则缺失会导致 release 启动闪退） |
| **Edit phantom edit** | 同文件多处编辑偶发不落盘（本项目已踩 5 次） | 同文件多处改动**串行** Edit，每处改后 Grep/Read 复核 |
| **沙箱构建 ACL** | 高频跑 gradle 会破坏文件 ACL（约 10 次后 AccessDenied） | 批量重构期间控制构建频次；一见 AccessDenied 立即停手 |
| **不建议做** | 全面重写为 MVI/Redux 单 Store；引入 Koin/Hilt 全量 DI 改造；把 `:shared` 再拆更细 | 当前 `withWriteLock` 事务模型已能保障一致性，全量范式切换收益不抵回归风险；DI 可在模块拆分时按需局部引入 |

---

## 附录：复核命令

```bash
cd C:/Users/Administrator/Downloads/work/milan/MilanKotlin

# 分层规模
for d in app/src/main/java/com/milan/game/ui app/src/main/java/com/milan/game/services \
         app/src/main/java/com/milan/game/data app/src/main/java/com/milan/game/infrastructure \
         shared/src/commonMain/kotlin/com/milan/game; do
  printf "%-55s %s\n" "$d" "$(find $d -name '*.kt' -exec cat {} + | wc -l)"; done

# 门面规模
grep -cE "^\s*(suspend )?fun " app/src/main/java/com/milan/game/services/GameService.kt

# 领域纯净性（应为空）
grep -rn "android\." --include=*.kt shared/src/commonMain

# Card/Deck 领域模型（当前应为空）
grep -rniE "class Card|data class Card|DeckModel|class Deck" --include=*.kt app/src/main shared/src

# 战斗交互 API 生产调用点（当前仅 TowerService:28 实例化）
grep -rn "StrategicBattleSimulator" --include=*.kt app/src/main

# 路由完整性：应两者相等
grep -c "@Serializable" app/src/main/java/com/milan/game/ui/nav/Routes.kt
grep -c "composable<"   app/src/main/java/com/milan/game/ui/nav/MilanNavHost.kt
```
