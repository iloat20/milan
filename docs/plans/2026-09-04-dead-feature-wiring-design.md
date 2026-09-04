# 死功能层接线设计（R5-I10 / I12）

> 研究日期：2026-09-04
> 范围：竞技场 / PvE / 活动 / 月卡充值 / 装备 / 社交 六大系统的 UI 接线与功能补完
> 前置：R5-C1~C8 + I1~I9 + I11 + T1~T4 + M2 已修，测试基线 292/292 全绿
> 性质：**本轮为设计文档，不含实现代码**。执行需另行排期。

---

## 0. 核心结论（先读这段）

R5 报告把六大系统统称为「死功能层 = 缺 UI 接线」。**经逐系统取证，这个判断只对了一半**：
其中 3 个系统确实只需补 UI，另外 3 个**服务层本身存在功能缺口，接线后会立刻暴露为可玩性缺陷或经济漏洞**。

| 系统 | 服务 API 数 | 真实死因 | 接线判定 |
|---|---|---|---|
| 竞技场 | 7 | 纯缺 UI（对手本地模拟生成，链路自洽） | ✅ 可直接接线 |
| PvE（深渊 + 日常本） | 8 | 纯缺 UI | ✅ 可直接接线 |
| 社交（好友 / 公会） | 13 | 缺 UI；数据为本地模拟，无真实联机 | ⚠️ 可接线，需标注「本地模拟」 |
| 月卡 / 充值 | 9 | 缺 UI；`charge` 为**模拟支付**，无 Google Play Billing | ⚠️ 有合规风险，见 §5 |
| 装备 | 7 | 缺 UI **且** `generateEquipment` **无入库路径** | ❌ 先补功能（C3） |
| 活动 | 9 | 缺 UI **且** `activeEvents` **无创建路径** **且** 活动代币体系缺失 | ❌ 先补功能（C1 + C2） |

**接线前必须先修三个阻塞项**：C1（活动代币体系）、C2（活动激活）、C3（装备入库）。
否则「活动商店」接线后会变成**用星尘购买、奖励也返星尘**的错配闭环（详见 §3.1）。

---

## 1. 接线模式（四件套，已实证）

项目现有 12 个子页全部遵循同一模式。以 `AffinityScreen` 为模板，新增一个子页需改 4 处：

### 1.1 `ui/nav/Routes.kt` —— 加路由对象

```kotlin
/** 竞技场（2026-09）：子页，盖住底部 tab，返回回主页。 */
@Serializable
data object ArenaRoute
```

### 1.2 `ui/nav/MilanNavHost.kt` —— 加 composable 块

```kotlin
// 竞技场（2026-09）：子页盖 tab，返回回主页
composable<ArenaRoute> {
    com.milan.game.ui.arena.ArenaScreen(
        onBack = { navController.popBackStack() },
    )
}
```

注意：主 tab 用 `enterTransition = { InkTransitions.tabEnter }`；子页无转场参数，直接用 `composable<XxxRoute>`。

### 1.3 `ui/<feature>/XxxScreen.kt` —— 新建屏幕（照 AffinityScreen 模板）

固定骨架（五项不可省）：

```kotlin
@Composable
fun XxxScreen(onBack: () -> Unit) {
    val service = GameState.service                       // ① 进程级单例
    val feedback = LocalFeedback.current                  // ② 统一反馈宿主
    val scope = rememberCoroutineScope()                  // ③ 写操作需协程
    val snapshot by service.snapshot.collectAsState()     // ④ 订阅快照刷新
    val data = remember(snapshot.revision) { /* 非快照字段随 revision 重算 */ }

    val onAction: () -> Unit = {                          // ⑤ WriteOutcome 三分
        scope.launch {
            try {
                when (service.xxx()) {
                    WriteOutcome.Success   -> feedback.show("成功")
                    WriteOutcome.Rejected -> feedback.show("条件不满足")
                    WriteOutcome.SaveFailed -> feedback.show("保存失败，请重试")
                }
            } catch (_: Exception) { feedback.show("操作异常，请重试") }
        }
    }

    Box(Modifier.fillMaxSize().background(AppTheme.BgDeepest)) {
        PageBackground {
            Column(Modifier.fillMaxSize()) {
                AppTopBar(title = "标题", onBack = onBack)
                LazyColumn(/* ... */) { /* GlassPanel 卡片 */ }
            }
        }
    }
}
```

**关键约束**：UI 刷新必须订阅 `snapshot.revision`。`AffinityScreen.kt:56-58` 的注释明确记录了反面案例——
`remember { }` 一次性取值会导致写操作后界面永不刷新（与 R5-C2 同模式缺陷）。

### 1.4 `ui/home/HomeScreen.kt` + `MilanNavHost.kt` —— 挂主页入口

- `HomeScreen` 签名加 `onOpenXxx: () -> Unit = {}`（默认值保证既有调用点不破编译）
- `HeroButtons` 加按钮：`NeonButton("⚔️ 竞技场", Modifier.weight(1f), onOpenXxx)`
- `MilanNavHost` 的 `HomeScreen(...)` 调用加 `onOpenXxx = { navController.navigate(ArenaRoute) }`

⚠️ **主页入口容量见 §4，不可简单平铺。**

---

## 2. 六系统 API 清点（接线所需全部入口）

### 2.1 竞技场 `ArenaService`（可直接接线）

| API | 类型 | 说明 |
|---|---|---|
| `getArenaData()` | 读 | 返回 `ArenaSaveData` |
| `getOpponents()` | 读 | 本地模拟对手列表（R5-T4 已钳制属性下限） |
| `getArenaRank()` | 读 | `Pair<排名, 段位名>` |
| `getSeasonRewards()` | 读 | 赛季奖励列表 |
| `setDefenseTeam(ids)` | 写 | 设置防守队伍 |
| `challengeOpponent(opponent)` | 写 | 挑战对手 |

### 2.2 PvE `PvEService`（可直接接线）

| API | 类型 | 说明 |
|---|---|---|
| `getAbyssData()` / `getAbyssFloorRewards()` / `getAbyssStageInfo(f,s)` | 读 | 深渊（爬塔之外的高难副本） |
| `challengeAbyssStage(floor, stage)` | 写 | 深渊挑战 |
| `getDailyDungeonData()` / `getRemainingChallenges(type)` | 读 | 日常副本（按类型限次） |
| `challengeDailyDungeon(type, level)` | 写 | 日常副本挑战 |

### 2.3 社交 `SocialService`（可接线，需标注本地模拟）

好友：`getFriends` / `addFriend` / `removeFriend` / `giftStamina` / `claimGiftedStamina`
好友申请：`getFriendRequests` / `handleFriendRequest`
公会：`getGuildData` / `createGuild` / `donateToGuild` / `getGuildTasks` / `claimGuildTaskReward`

### 2.4 月卡 / 充值 `MonetizationService`（有合规风险）

月卡：`activateMonthlyCard(cost)` / `claimMonthlyCardReward()` / `getMonthlyCardDaysLeft()`
纪行：`purchaseBattlePass(cost)` / `addBattlePassExp` / `claimBattlePassReward(level)` / `getBattlePassRewards()`
（纪行已有 `BattlePassScreen`，此处仅需补**月卡**与**充值**）
充值：`charge(tierId, hardCurrency, costCents)` / `claimChargeMilestone` / `getChargeTiers` / `getChargeMilestones`

### 2.5 装备 `EquipmentService`（先补 C3）

`generateEquipment` / `getCharacterEquipmentStats` / `enhanceEquipment` / `equipToCharacter` /
`unequipFromCharacter` / `disassembleEquipment` / `getAllOwnedEquipments`

### 2.6 活动 `EventRhythmService`（先补 C1 + C2）

`getActiveEvents` / `getEventsByType` / `isEventActive` / `updateTaskProgress` /
`claimTaskReward` / `redeemShopItem` / `getShopItems` / `signIn` / `getSignInProgress` /
`getDefaultEventDefinitions`

---

## 3. 三个阻塞项（接线前必须修）

### 3.1 C1：活动代币体系完全缺失 —— Critical

**证据链**：

1. **无余额字段**。`EventRhythmSaveData`（`data/EventRhythmSaveData.kt`）全部字段为
   `activeEvents` / `completedEvents` / `shopRedemptions` / `eventTaskProgress` /
   `claimedTaskRewards` / `signInProgress` / `lastDailyReset` / `lastWeeklyReset`——
   **没有任何活动代币余额字段**。`EventCurrencyType` 枚举定义了 `EVENT_CURRENCY` /
   `ACTIVITY_POINTS` / `COLLABORATION_TOKENS` 三种代币，但无处存储。

2. **奖励发放错配**。`EventRhythmService.kt:103-107`：
   ```kotlin
   when (task.rewardType) {
       "HARD_CURRENCY"  -> core.addCurrencyDelta(0, task.rewardAmount)
       "SOFT_CURRENCY"  -> core.addCurrencyDelta(task.rewardAmount, 0)
       else             -> core.addCurrencyDelta(task.rewardAmount, 0)  // ← EVENT_CURRENCY 落到这里
   }
   ```
   `else` 分支把 `EVENT_CURRENCY` / `ACTIVITY_POINTS` / `COLLABORATION_TOKENS`
   **全部当作星尘发放**。

3. **商店扣款错配**。`EventRhythmService.kt:151,156`：
   ```kotlin
   if (core.saveData.softCurrency < cost) return WriteOutcome.Rejected   // 余额预检查星尘
   ...
   core.saveData.softCurrency -= cost                                     // 扣星尘
   ```
   而 `EventShopItem.currencyType` 默认值是 `"EVENT_CURRENCY"`（`EventRhythmSaveData.kt:85`）。

**接线后的实际表现**：活动商店物品以「活动代币」标价（五星装备 3000、觉醒材料 200），
实际扣的是星尘；完成任务给的 `EVENT_CURRENCY 300` 也直接变成 300 星尘。
→ **星尘→买高价装备→任务再返星尘，经济闭环错配**，且物品定价（3000）是按活动代币规模设计的，
换成星尘后性价比失衡。

**修复方案**（二选一）：

| 方案 | 做法 | 成本 | 评价 |
|---|---|---|---|
| **A. 补真实代币**（推荐） | `EventRhythmSaveData` 增 `eventCurrencyBalances: Map<String, Int>`（代币类型→余额）；`claimTaskReward` 的 `else` 分支改为按 rewardType 入账对应代币；`redeemShopItem` 按 `shopItem.currencyType` 从对应余额扣减并预检 | 中 | 语义正确，与 `EventCurrencyType` 设计对齐；新增字段需走 `sanitize` 补默认值（按 R5-I5 铁律：懒创建改纯读） |
| B. 统一改成星尘 | 把定义里的 `EVENT_CURRENCY` 全改为 `SOFT_CURRENCY`，并重定价 | 低 | 改动小，但活动代币系统名存实亡，`EventCurrencyType` 与 `currencyType` 字段沦为死字段 |

> 推荐 A。方案 B 会留下「字段存在但永远只有一个取值」的僵尸设计，与 R5-I3「死模板剔除」的治理方向相悖。

### 3.2 C2：活动激活无路径（I12 本体）

**证据链**：

- `EventRhythmSaveData.kt:17`：`activeEvents: List<GameEvent?> = emptyList()`（默认空）
- 全项目 `activeEvents` 的**写入仅出现在测试**：`EconomyGuardRegressionTest.kt:86,121`
- `getDefaultEventDefinitions()`（`EventRhythmService.kt:222-261`）返回 3 个模板，
  在 `GameService.kt:692` 暴露，**但全项目零调用点**
- 因此 `getActiveEvents()` 恒返回空 → `signIn` / `claimTaskReward` / `redeemShopItem` /
  `updateTaskProgress` 四个写操作**首行 `?: return WriteOutcome.Rejected` 恒命中**

**这就是 R5-C1~C3 漏洞处于「休眠态」的根本原因**——不是被防住了，是从未激活过。

**修复方案**：新增 `EventDefinition → GameEvent` 实例化 + 事务化激活。

```kotlin
// EventRhythmService 新增
suspend fun ensureActiveEvents(): WriteOutcome {
    val data = getData()
    val now = System.currentTimeMillis()
    // 已存在未过期活动则不重建（幂等）
    if (getActiveEvents().isNotEmpty()) return WriteOutcome.Success

    val events = getDefaultEventDefinitions().map { def -> def.toGameEvent(now) }
    val origEvents = data.activeEvents
    return core.transaction(
        tag = "event.activate",
        mutate = { data.activeEvents = events },
        rollback = { data.activeEvents = origEvents },
        onCommit = { core.publishProgressionChanged() },
    )
}

private fun EventDefinition.toGameEvent(now: Long): GameEvent = GameEvent(
    eventId = "evt_${eventType.name.lowercase()}_$now",
    eventType = eventType.name,
    name = name,
    description = description,
    startTime = now,
    endTime = now + durationDays * 86_400_000L,
    tasks = tasks.map { t -> EventTask(
        taskId = "${eventType.name}_${t.taskType}",
        taskType = t.taskType,
        name = t.name,
        target = t.target,
        rewardType = t.rewardType,
        rewardAmount = t.rewardAmount,
    )},
    shopItems = shopItems.map { s -> EventShopItem(
        itemId = s.itemId, name = s.name, price = s.price,
        currencyType = "EVENT_CURRENCY",   // ← 定义模型缺此字段，见下
        maxRedemptions = s.maxRedemptions,
    )},
    signInDays = if (eventType == EventType.SIGN_IN) durationDays else 0,  // ← 定义模型缺此字段
    isActive = true,
)
```

**触发时机**（三选一）：

| 时机 | 做法 | 评价 |
|---|---|---|
| **进活动页时懒激活**（推荐） | `EventScreen` 的 `LaunchedEffect(Unit) { service.ensureActiveEvents() }` | 符合项目「写操作 suspend + 事务」铁律；不拖慢启动；与 R5-I4 每日任务重置的 `LaunchedEffect` 触发模式一致 |
| 存档初始化时 | `sanitize` / `createDefault` 里写入 | ❌ 违反 R5-I5 铁律（纯读，不锁外写存档） |
| 每日重置时 | 挂到 `ensureTodayReset()` | 可行但耦合过重，且首次进游戏当天不触发 |

**两个模型缺口需一并补**（否则实例化语义不完整）：

1. `EventShopItemDefinition` 无 `currencyType` 字段 → 实例化时只能硬编码 `"EVENT_CURRENCY"`。
   建议给 `EventShopItemDefinition` 增 `val currencyType: String = "EVENT_CURRENCY"`。
2. `EventDefinition` 无 `signInDays` 字段 → 签到活动只能借 `durationDays` 顶替。
   建议给 `EventDefinition` 增 `val signInDays: Int = 0`。

> 两者都是纯 Kotlin `data class`（非 `@Serializable` 存档模型），**补充字段不影响存档兼容**，
> 但需提供默认值以免破坏既有测试中的构造调用。

### 3.3 C3：装备无获取途径

**证据链**：

- `EquipmentService.generateEquipment(templateId, level)`（:32）**返回** `EquipmentSaveState`,
  **但不写入** `saveData.ownedEquipments`
- 全项目 `ownedEquipments =` 的赋值仅出现在两处**移除/回滚**场景：
  - `EquipmentService.kt:385`（分解时移除）
  - `EquipmentService.kt:392`（回滚时还原）
- 读侧 `getCharacterEquipmentStats` / `enhanceEquipment` / `equipToCharacter` 全部依赖
  `saveData.ownedEquipments.firstOrNull { ... }`
- → `ownedEquipments` 恒为空（除非旧存档自带），**装备屏接了也是空列表**，
  且强化/装备/卸下三个 API 全部恒 `Rejected`

**与 C2 同模式**：工厂方法有了，入库路径缺失。

**修复方案**：新增事务化发放服务。

```kotlin
suspend fun grantEquipment(templateId: String, level: Int = 1): WriteOutcome {
    val template = core.equipmentTemplates.firstOrNull { it.equipmentId == templateId }
        ?: return WriteOutcome.Rejected
    val equipment = generateEquipment(templateId, level)
    val original = core.saveData.ownedEquipments.toList()
    return core.transaction(
        tag = "equipment.grant",
        mutate = { core.saveData.ownedEquipments = original + equipment },
        rollback = { core.saveData.ownedEquipments = original },
        onCommit = { core.publishProgressionChanged() },
    )
}
```

**并发安全**：`EquipmentService` 已有 `idSeq`（`AtomicLong`，R5-T3 引入）保证同毫秒批量发放不碰撞。

**发放来源**（需产品裁定，属设计缺口）：
爬塔首通 / 深渊奖励 / 活动商店兑换 / 日常副本掉落。当前**无任何产出点**，
`grantEquipment` 补上后仍需决定挂到哪几个奖励路径。

---

## 4. 主页入口容量约束（设计问题，需产品裁定）

**现状**（`HomeScreen.kt:402-449`）：`HeroButtons` 为 **2 列 × 4 行 = 8 个按钮**，
已占满首页主视觉下方；首页还有 Hero 主视觉（`heroHeight` 视差区）与 `AvatarStrip` 头像横滑。

新增 6 个系统入口 → 14 个按钮 → 7 行，**会挤压 Hero 与 AvatarStrip，破坏首页视觉重心**。

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A. 折叠面板「更多玩法」** | 现有 8 个保持不变，底部加一个「更多玩法」展开条，内含 6 个新入口 | 首页零膨胀；入口分层清晰 | 新功能曝光率低，需二次点击 |
| **B. 分区标题 + 网格** | 把按钮分组为「冒险」（塔/剧情/副本/PvE）、「竞技」（竞技场/深渊）、「社交」（好友/公会）、「成长」（成就/任务/纪行/好感/装备） | 结构清晰，可扩展 | 首页变长，需改为 LazyColumn 内分区 |
| **C. 横向滑动入口条** | 新增一行可横滑的入口 chip 条承载新系统 | 零垂直膨胀；契合移动端习惯 | 除首个外其余需滑动才可见，曝光最低 |
| D. 新增底部 tab | 底部加到 6 个 | 一级曝光 | ❌ 违反 Material 规范（底部导航上限 5）；且新系统不是同级主功能 |

> 建议 **A + B 组合**：保留现有 8 个主入口不动，新增「更多玩法」折叠区，
> 内部按 B 的分组方式组织 6 个新系统。兼顾首页稳定与可扩展性。

⚠️ 按项目约定，**UI 变更须先走 `/brainstorming` 流程**，本节方案需用户确认后方可实施。

---

## 5. 充值系统的合规风险（需产品裁定）

`MonetizationService.charge(tierId, hardCurrency, costCents)`（:228）**是模拟支付**：
直接把 `hardCurrency` 加到存档，未经任何真实支付通道。

**风险**：
- 接线「充值」页 = 向玩家展示内购入口，点击后**直接到账**，无任何真实扣款
- 若未来上架 Google Play，此路径会与真实 Billing 冲突，且当前形态违反支付合规
- `costCents` 字段暗示设计意图是真实货币，但实现里只用于累充里程碑统计

**建议**：
- 若项目定位为**单机/离线游戏**且无上架计划 → 可接线，但需明确标注「模拟充值（测试用）」
- 若**有上架计划** → 充值页**不应接线**，应先接入 Google Play Billing，
  或改为「充值入口暂未开放」占位

此项需用户明确项目定位后决定，设计文档不作默认假设。

---

## 6. 分批实施计划（建议）

| 批次 | 内容 | 前置 | 风险 |
|---|---|---|---|
| **P0** | C1 活动代币体系 + C2 活动激活 + C3 装备发放 | 无 | 中（改存档模型，需补 `sanitize` 默认值 + 回归测试） |
| **P1** | 竞技场屏 + PvE 屏（深渊 / 日常副本） | 无 | 低（纯 UI，服务层自洽） |
| **P2** | 活动屏（签到 / 任务 / 商店） | P0 | 中（依赖 C1/C2，接线即激活 C1-C3 漏洞休眠态，**必须补回归测试**） |
| **P3** | 装备屏（背包 / 强化 / 穿脱 / 分解）+ 发放来源接入 | P0 | 中（依赖 C3） |
| **P4** | 月卡屏 | 无 | 低 |
| **P5** | 社交屏（好友 / 公会） | 无 | 低（本地模拟） |
| **P6** | 充值屏 | 产品裁定（§5） | 高（合规） |

**关键顺序约束**：P2 必须在 P0 之后。先接线活动屏而不修 C1，等于主动激活一个经济错配闭环。
同理 P3 必须在 C3 之后，否则装备屏恒为空。

**主页入口改造**随 P1 一起做（§4 方案确认后）。

---

## 7. 验收标准

每个批次完成后必须通过：

1. `testDebugUnitTest` 全绿（基线 292，新增功能需配套单测）
2. `assembleDebug` 编译通过
3. 新增子页需有对应的 Compose UI 渲染测试（`@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])`）
4. **经济类改动必做「净变动」断言**：单次调用看不出问题，必须连续调用断言净增减
   （R5 爬塔星尘永动机即由此漏网）
5. 触碰 `GameState` 的测试类先调 `GameState.resetForTest()`

---

## 附：取证文件清单

| 文件 | 取证点 |
|---|---|
| `services/EventRhythmService.kt:22-47, 82-178, 180-262` | 活动代币错配、激活缺失、定义模板 |
| `data/EventRhythmSaveData.kt` | 无代币余额字段、`activeEvents` 默认空 |
| `services/EquipmentService.kt:32-58, 371-395` | `generateEquipment` 无入库、分解是唯一写入点 |
| `data/SaveData.kt:50, 206-218` | `ownedEquipments` 定义与 sanitize |
| `ui/nav/Routes.kt` | 现有路由体系（5 tab + 12 子页） |
| `ui/nav/MilanNavHost.kt:163-327` | 接线模式实证 |
| `ui/home/HomeScreen.kt:90-100, 402-449` | 入口容量现状 |
| `ui/affinity/AffinityScreen.kt` | 屏幕模板（订阅刷新 + WriteOutcome 三分） |
| `app/src/test/.../EconomyGuardRegressionTest.kt:86,121` | 唯一构造 `activeEvents` 处（测试） |
