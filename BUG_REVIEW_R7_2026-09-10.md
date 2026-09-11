# Milan 全库完整性与缺陷审查 R7 · 2026-09-10

> 方法：三路并行 Explore（core 服务层 · UI/战斗 · 内容配置）+ 主代理取证复核。
> 范围：`main` 当前树（含 R6 修复合入后）。
> 验证：`:app:testDebugUnitTest` 433/0 全绿；`:app:assembleDebug` 成功；`:checkArchitecture` **失败 1 处**。

---

## 执行摘要

| 级别 | 数量 | 一句话 |
|------|------|--------|
| **P0** | 4 | 启动竞态闪退 / 签到同日 7 连 / 深渊无限刷星尘 / 战斗 VM 旧协程覆盖新局 |
| **P1** | 12 | 状态天赋全失效、回滚漏货币、次日不重置锁死、架构违规、TOCTOU 残留等 |
| **P2** | 8 | 装备词条百分比、素材残留、文档漂移、取消契约不完整等 |

**构建/测试基线**：单测与 Debug 构建健康；**架构门禁已红灯**，说明组合根泄漏已知未修。

**R6 回归**：P0-1~4 均已修；P1 修了扫荡 hard 回滚、升满溢出、锁内复检（仅部分入口）、套装 isPercentage。

---

## P0（本轮新发现 / 仍致命）

### P0-1　MilanTheme 在 AppGraph 装配前读 service → 启动竞态闪退

- **位置**：`ui/theme/Theme.kt:106` + `MainActivity.kt:38-41` + `di/AppGraph.kt:49-50`
- **证据**：
  ```kotlin
  // MainActivity: Application 异步 init 尚未完成时就 setContent
  setContent { MilanTheme { MilanNavHost(...) } }

  // Theme.kt:106 —— 无 ready 门控，无 isInstalled 判断
  val fontScaleTier by com.milan.game.di.AppGraph.service.meta...

  // AppGraph.service getter
  get() = checkNotNull(serviceRef) { "AppGraph 未装配..." }
  ```
- **为何错**：`MilanNavHost` 有 `GameState.ready` 门控，但 `MilanTheme` 是**其父级**，首帧必组。`MilanApp` 在 `Dispatchers.IO` 异步 `ensureInitialized`，慢机/大存档时 `serviceRef` 仍为 null → `IllegalStateException` 闪退。
- **连带**：`:checkArchitecture` 明确报此项（UI 禁止 `AppGraph.service`）。
- **修复**：`if (!AppGraph.isInstalled) { MaterialTheme(默认 density) } else { 订阅 meta }`；或把 fontScale 订阅下移到 ready 之后的 CompositionLocal；或用 CompositionLocal 注入 tier。

### P0-2　每日签到同日可连签 7 次，刷满周期奖励

- **位置**：`DailyCheckInService.kt:83-131`（`signToday`）
- **证据**：
  ```kotlin
  // 简化判断：只拦「周期已签满」，没有 lastSignDay
  if (data.signedDaysInCycle >= CYCLE_LENGTH) return Rejected
  // getCheckInStatus.signedToday 同样错：
  signedToday = isCurrentCycle && data.signedDaysInCycle >= CYCLE_LENGTH
  ```
- **为何错**：同日调用 7 次可拿完 `DAILY_SOFT_REWARDS`（1000→5000，合计 18500 星尘）+ 第 7 日 100 钻 + 周期全勤 20000/300。UI 的 `signedToday` 只在第 7 天后才为 true，前端也会误判「今日未签」。
- **修复**：存 `lastSignDay: Long`（或 `signedDayIndexes`），锁内断言 `today != lastSignDay`；`getCheckInStatus.signedToday` 改为 `lastSignDay == today`。

### P0-3　深渊 `completeAbyssStage` 无首通/新星门控，可无限刷星尘

- **位置**：`DungeonService.kt:134-177`
- **证据**：
  ```kotlin
  val softReward = floor * 500 * clampedStars
  val hardReward = if (floor % 5 == 0 && clampedStars >= 2) floor * 10 else 0
  // mutate 内：楼层推进有条件，货币无条件发放
  if (softReward > 0) core.addCurrencyDelta(softReward, 0)
  ```
- **为何错**：可反复 `completeAbyssStage(1, 3)` 每次 +1500 星尘（及里程碑层钻石）。`challengeAbyss` 只加次数、不绑定结算。
- **修复**：仅在「本层首次通关」或「星数刷新纪录」时发奖；或 `completeAbyssStage` 校验 `challengeCount` 与 `currentFloor` 同步。

### P0-4　策略战斗 VM 复用 + 旧协程未取消 → 重进覆盖新局

- **位置**：`StrategicBattleViewModel.kt:52-62, 165-217, 220-233`；`TowerScreen`/`StrategicBattleScreen` 用 `viewModel()` 挂在 Tower 路由栈
- **证据**：`start()` 不 cancel；`viewModelScope.launch { delay(350); _ui.value = ... }` 无 Job 句柄；退出只 `showStrategic=false`，VM 不 clear。
- **后果**：上一局敌方回合/settle 完成后写回 `_ui`，覆盖新 `BattleState`/结算弹层。
- **修复**：`private var battleJob: Job?`；`start()` 先 `battleJob?.cancel()`；`settle`/敌方回合共用同一 Job。

---

## P1

### 内容与引擎契约

| # | 位置 | 问题 |
|---|------|------|
| 1 | `data.json` 81 处状态天赋 Effects | `Chance=0, Duration=0`，`TalentEngine` 以 `chance` 为唯一门槛 → **全部状态天赋静默失效**（Burn/Poison/Bleed/Stun/Chill/Disarm/Stiff/Taunt，live=0） |
| 2 | `data.json` 31 棵树 `BranchIds` | 全部漏 `branch_ultimate`；UI 已用并集兜住，但主路径 `tree.branchIds` 仍不完整，未来只读分支声明的代码会再踩坑 |
| 3 | `tools/check_gamecontent_sync.ps1:19` | 路径仍指 `app/.../GameContent.kt`（实际在 `core/`）→ 同步守门脚本失效 |

### 回滚不完整（落盘失败 → 幽灵货币/道具）

| # | 位置 | 缺失回滚项 |
|---|------|------------|
| 4 | `DailyCheckInService.ensureTodayReset:68-73` | 全勤奖 soft/hard |
| 5 | `SeasonService` 赛季结算 rollback | soft/hard |
| 6 | `EquipmentService.dismantle` rollback | 星魂碎片 `restoreItemCount` |

### 功能锁死 / 经济雷

| # | 位置 | 问题 |
|---|------|------|
| 7 | `PveSaveData.lastResetTime` / `DailyDungeonSaveData.lastResetTime` | 字段存在，**全项目零重置逻辑** → 深渊每日 3 次、日常副本每类 3 次用完后永久 Rejected |
| 8 | `StoryService.sweep*` / `TowerService.sweepTower` | `times * cost` 可 Int 溢出为负；`addCurrencyDelta(-负)=加钱`（虽 @Deprecated，仍是 public API） |
| 9 | `MonetizationService.charge:246` | `hardCurrency * 2` 可溢出 → 首充标记已扣、到账 0 |

### 架构 / TOCTOU

| # | 位置 | 问题 |
|---|------|------|
| 10 | `Theme.kt:106` | 架构门禁失败（见 P0-1） |
| 11 | Monetization / Story / Event / Season / Collection / Equipment / Dungeon / Tower / Inspection / Arena 约 18 处 | 「锁外校验 → 锁内变更」未统一迁到 `withWriteLock` 内复检（R6 只修了 4 处） |
| 12 | `EventRhythmService.redeemEventShopItem` | 锁外余额预检 + `addCurrencyDelta` 对下溢是钳 0 而非 Rejected |

### UI

| # | 位置 | 问题 |
|---|------|------|
| 13 | `DamageFloatingText.kt:54-65` | 串行 `delay+animateTo`，30 条日志 ≈25s 卡结算 |
| 14 | `GachaViewModel.doPull` 等 6 处 | `catch (Exception)` 吞 `CancellationException` |
| 15 | 抽卡 Charge 阶段 | `visible=false` → ChargeCore 不可达、蓄能段无画面且无法 skip |

---

## P2

1. `ServiceCore` 装备主/副词条忽略 `isPercentage`（套装已修，主副仍 flat）。
2. `assets/data.json.bak`（~122KB）打进 APK；`drawable-nodpi` 残留 `.webp.20260906` / `.bak`。
3. `pool_flame` 名实不符 + 生成器不透传 `FeaturedElement`。
4. `:shared` 序列化版本硬编码，未走 version catalog。
5. AGENTS.md / 注释漂移：角色数 28→31、GameContent 路径、模块列表。
6. 装备内容不在 data.json，始终走代码 fallback。
7. `PortraitLoader` 取消路径不 complete deferred。
8. `DialogueRoute` stage 为空时黑屏无返回。

---

## 已验证健康

- 单测 433 全绿；`assembleDebug` 成功；ProGuard WorkManager keep 完整。
- R6 P0：引导非阻塞、终极天赋 UI 并集、存档主档缺失走备份链、战斗 `enemyActing` 门闩。
- `withWriteLock` 无重入死锁；抽卡/养成事务回滚（主路径）完整。
- 立绘 31/31、武器图 31/31；SkillId 命名规范；@SerialName 对齐。
- 31 角 / 31 树 / 池引用无悬空。

---

## 建议修复顺序

1. **P0-1** Theme：`isInstalled` 守卫或 CompositionLocal 注入 fontScale（消红灯 + 防闪退）。
2. **P0-2** 签到：`lastSignDay` + `signedToday` 语义修正。
3. **P0-3** 深渊：首通/新星才发奖。
4. **P0-4** 战斗 VM：battleJob cancel。
5. **P1-1** 状态天赋：JSON 补 Chance/Duration，或引擎回退 Value；加单测。
6. **P1-7** 深渊/副本跨日重置。
7. **P1-4/5/6** 回滚镜像 restore（可写统一 helper 扫描 `addCurrencyDelta/addItemDelta` 调用点）。
8. 扫荡 `times` 用 Long 预算；charge `*2` 用 Long。
9. data.json 31 树补 `branch_ultimate`；修 sync 脚本路径；删 bak 残留。
