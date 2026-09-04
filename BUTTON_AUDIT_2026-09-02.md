# 按钮有效性全量审计 — 2026-09-02

> 范围：`MilanKotlin/app/src/main/java/com/milan/game/ui/**`（13 个页面 + 组件，约 14.2k 行）
> 方法：全局特征扫描 → 4 路并行子代理分组审查 → 主代理逐条取证复核（打开服务层源码交叉验证）
> 结论：**Critical 2 / Important 3 / Minor 3**，另附 2 项非按钮类缺口

## 一、总体结论

按钮的**显式缺陷已清零**：全仓无 `onClick = {}` 空 lambda、无 `TODO/FIXME`、无 `enabled = false`、无 `onClick = null`；17 个路由全部在 `MilanNavHost` 注册且参数类型一致，不存在"跳转未注册目标"导致无反应。

现存失效全部是**隐性失效**——按钮有 onClick、代码也执行了，但用户看不到任何变化。共 8 处，其中 2 处涉及**真实扣费/真实发放资源却零反馈**。

---

## 二、Critical（2 项）

### C1 — 首页主视觉「查看详情 ›」是完全空的按钮

| 项 | 内容 |
|---|---|
| 位置 | `ui/home/HomeScreen.kt:313-323` |
| 现象 | 首页最显眼的 CTA，点击**完全无反应**，无任何跳转或提示 |

```kotlin
androidx.compose.material3.TextButton(
    onClick = { /* onOpenCharacter 由外层处理 */ },   // ← 空实现
) { Text(text = "查看详情 ›", ...) }
```

**根因**：所在 `private fun Hero(fixedH, scrollProgress)`（:217）**签名里没有 `onOpenCharacter` 参数**，外层 `Box`（:226-233，modifier 链为 `fillMaxWidth/height/clip/border/background`）**也没有 clickable**。注释声称"由外层处理"，实际无处处理——`HomeScreen` 收到的 `onOpenCharacter`（:94）只传给了 `AvatarStrip`（:180），从未传给 Hero。注释误导性极强。

**修复方向**：给 `Hero` 增加 `onOpenCharacter: (String) -> Unit` 参数，`onClick = { onOpenCharacter(def.characterId) }`。

---

### C2 — 纪行页「购买豪华版 680💎」：真实扣钻，界面零变化

| 项 | 内容 |
|---|---|
| 位置 | `ui/battlepass/BattlePassScreen.kt:42-43`（数据）、:67-69（按钮） |
| 现象 | 点击后**真实扣除 680 钻石并落盘**，但界面完全不变：按钮不消失、豪华轨仍锁、等级不动 → 用户重复点击 |
| 二次危害 | 第二次点击命中 `if (data.battlePassPremium) return Rejected`，UI 层对 `Rejected` **无任何反馈**（无 Snackbar/Toast），完全静默 |

```kotlin
// BattlePassScreen.kt:42-43 —— 无 key 的 remember，值永久冻结在首次组合
val data = remember { service.getMonetizationData() }
val rewards = remember { service.getBattlePassRewards() }
```

**已取证的服务层**：`MonetizationService.kt:117` 确实执行 `core.saveData.hardCurrency -= cost` 并走 `core.transaction` 落盘（:114-130）。即钱是真的扣了，只是界面不知道。

**根因**：全页**没有任何 `collectAsStateWithLifecycle()` 订阅 `GameState.snapshot`**（对比 SettingsScreen、AchievementScreen 均正确订阅）。`onCommit` 里的 `publishCurrencyChanged()` / `publishProgressionChanged()` 发出的事件无人消费 → 页面永不重组。

**修复方向**：`val snap by GameState.snapshot.collectAsStateWithLifecycle()`，`data = remember(snap.revision) { service.getMonetizationData() }`；按钮 onClick 接 `WriteOutcome` 分支提示。

---

## 三、Important（3 项）

### I1 — 每日任务页活跃度宝箱：真实发放，UI 不刷新

- 位置：`ui/missions/DailyMissionScreen.kt:44-46`（三处无 key `remember`）、:76-78（宝箱 onClick）
- 机制：与 C2 同模式。`claimActivityChest` **真实发放**星尘/钻石（`DailyMissionService.kt:114-117`，含 transaction + 回滚 + 广播），但 `chestStatuses` / `data` 是冻结快照 → 宝箱点击后仍显示"可领取"；再点返回 `Rejected` 且无反馈。
- 修复方向：同 C2（订阅 snapshot + revision 作为 remember key + 分支反馈）。

### I2 — 角色卡点击打开"错误的角色"（Compose 经典闭包陷阱）

- 位置：`ui/components/CharacterCard.kt:94-103`
- 机制：`pointerInput(Unit)` 的 key 是**常量 `Unit`**，协程永不重启，lambda 始终持有**首次组合时捕获的 `onClick`**。当列表因筛选/排序变化而在同一槽位换绑不同角色时（`CharacterListScreen` 接了 `ListFilterBar`），点击卡片会打开**切换前的那个角色**。
- 影响面：`CollectionScreen:143`、`CharacterListScreen:159`、`DeckScreen:152` 三处调用点共用此组件。

```kotlin
.pointerInput(Unit) {              // ← key 应为 characterId
    detectTapGestures(onPress = { ...; onClick() })
}
```

- 修复方向：`pointerInput(characterId)`（或改用 `clickable`）。

### I3 — 抽卡按钮动画期间"看着能点、点了没反应"

- 位置：`ui/gacha/GachaScreen.kt:459-461` + `:164`
- 机制：`NeonButton("单 抽")` / `GoldButton("十 连")` **未传 `enabled` 参数**（`ThemeButtons.kt:67/100` 默认 `enabled = true`），视觉上与常态完全一致；但 `doPull` 首行 `if (busy) return` 静默拦截。
- 时序窗口：`busy = true` 设于 :171，而全屏 `CyberRevealLayer`（自带 clickable 跳过）要到 :251 才置 `visible`。中间约 1s 的 Charge/Beam 阶段按钮仍可命中，点击静默丢弃。
- 注：防重入本身是**有效**的，不会重复扣费；问题仅在缺少禁用态与反馈。
- 修复方向：`GoldButton(..., enabled = !busy)`，或对 `busy` 态点击给一次轻震动/提示。

---

## 四、Minor（3 项）

| ID | 位置 | 问题 |
|---|---|---|
| M1 | `ui/progression/ProgressionScreen.kt:95-103`<br>`ProgressionPanels.kt:187-189/269/320/488` | 5 个养成写操作共享 `busy`，但按钮**未随 busy 禁用**。落盘 IO 期间点"突破"/"天赋"会命中 `if (busy) return` 静默丢弃。对比 `ShopScreen.kt:113/139/166/193` 正确传 `enabled = !busy`，属同源防护不对称。 |
| M2 | `ui/deck/DeckScreen.kt:118-120` | 编队条**空槽**点击 no-op（`if (onOpenDeckSlot != null) previewId = ...`），点了没反应。`TowerScreen.kt:143` 同场景会跳去组队页，两处行为不一致。 |
| M3 | `ui/story/DialogueScreen.kt:325-327` | 剧情分支选项未实现：`if (choice.nextStageId != null) { /* 暂不支持，直接退出 */ exiting = true }` —— 点分支直接结束整关，而非跳转目标关。注释已自认未完成。 |

---

## 五、相关缺口（非按钮失效，但影响"点了没用"的体感）

1. **好感度页无任何交互控件**（`ui/affinity/AffinityScreen.kt`）：说明卡写着"通过赠送礼物、出战战斗提升角色好感度，解锁专属剧情和奖励"，但整页只有角色卡跳转，**没有任何提升好感度的入口**；且 `affinityData` 为无 key `remember` 快照（:42），即便未来加了入口也不会刷新。
2. **剧情锁定项点击无反馈**（`ui/story/StoryScreen.kt:88, 221`）：`isUnlocked` / `canEnter` 为 false 时点击不改变 `expanded`、不触发 `onOpenStage`，静默无提示。另 :80-81/:208-209 亦为无 key `remember` 快照，因列表项随滚动重建故影响有限。

---

## 六、已核实有效（抽样取证，非问题项）

| 模块 | 核实结论 |
|---|---|
| 底部 5 tab | `GameNavBar` → `NavItem.toNavRoute()`，`launchSingleTop + restoreState` 标准切换 ✔ |
| 路由完整性 | `Routes.kt` 17 个路由 ↔ `MilanNavHost.kt` 全部注册，参数名/类型一致，**无未注册目标** ✔ |
| 抽卡核心链路 | `GachaService.pull` 真实 `softCurrency -= cost` + 落盘 + 写历史 + `publishCurrencyChanged`；余额不足走 `feedback.show("星尘不足")` ✔ |
| 抽卡防重入 | `if (busy) return` 有效阻止连点重复扣费 ✔ |
| 养成 5 项 | 升级×1/×5/升满、突破、升星、天赋：均真实扣资源，不足/已满级有反馈 ✔ |
| 商店 4 类 | 每日补给/碎片包/碎片兑换/钻石兑换：真实改币 + `enabled = !busy` + 三态反馈 ✔ |
| 爬塔 | 挑战/复刷扣票、胜利返票、结算卡+战报齐全，`running` 正确禁用 ✔ |
| 设置页 | 音效/振动/推送开关订阅 `snap` 并走 `persistSetting` 持久化 ✔ |
| 成就 | `claimAchievement` 三种结果均有 `feedback.show` 提示 ✔ |
| 剧情推进 | 下一步/跳过/自动：末句点按置 `exiting = true` 触发 `onStageComplete`，**不卡死** ✔ |
| 角色详情左右切换 | `MilanNavHost.kt:239/248` → `::switchCharacter` 已接线 ✔ |
| 空状态 CTA | `EmptyState` "前往寻访" → `onNav(NavItem.Gacha)` ✔ |

---

## 七、修复优先级建议

| 批次 | 内容 | 说明 |
|---|---|---|
| P0 | C1、C2 | C1 是首页门面；C2 涉及真实扣费，风险最高 |
| P1 | I1、I2、I3 | 同模式批量修（`remember` 快照 3 处可一并收口） |
| P2 | M1、M2、M3 | 一致性与未完成功能 |
| P3 | 好感度交互入口、剧情锁定反馈 | 属功能缺口，需设计决策 |

**模式级根因**：C2 / I1 同源——`remember { service.getXxx() }` 无 key + 页面未订阅 snapshot。全仓共 13 处该写法（`grep 'remember\s*\{\s*service\.'`），其中 `GachaScreen:110`（卡池定义表，静态无害）、`CharacterDetailScreen:104`（角色定义表，静态无害）、`CollectionScreen:70`（展示为主）可豁免，其余建议统一收口为 `remember(snap.revision) { ... }`。

---

## 八、修复状态（2026-09-02 Agent 轮，全部落地）

| 项 | 状态 | 落点 |
|---|---|---|
| C1 首页 Hero 查看详情 | ✅ 已修（另一开发轮，核验在位） | HomeScreen.kt（onOpenCharacter 参数已接线） |
| C2 纪行豪华版扣钻不刷新 | ✅ 已修（另一开发轮，核验在位） | BattlePassScreen.kt（snapshot + remember(revision) + busy） |
| I1 每日任务宝箱 | ✅ 已修（另一开发轮，核验在位） | DailyMissionScreen.kt（同模式收口） |
| I2 角色卡开错角色 | ✅ 已修（另一开发轮，核验在位） | CharacterCard.kt（pointerInput(characterId)） |
| I3 抽卡按钮禁用态 | ✅ 已修（另一开发轮，核验在位） | GachaScreen.kt（enabled = !busy）+ ThemeButtons.kt（disabled alpha 0.45） |
| M1 养成 busy 禁用 | ✅ 已修（另一开发轮，核验在位） | ProgressionScreen/Panels（enabled = canX && !busy） |
| M2 编队空槽 no-op | ✅ 本轮修复 | DeckScreen.kt：空槽点击 → feedback 引导「点下方角色卡片加入编队」 |
| M3 剧情分支直接退出 | ✅ 本轮修复 | DialogueScreen.kt 上抛 onNavigateStage + MilanNavHost.kt 接线（完结当前关→跳目标关；目标失效 popBackStack 防黑屏） |
| P3 功能缺口 ×2 | ⏸ 设计决策项 | Affinity 交互入口、Story 锁定反馈 |

**顺带修复（预存，非本审计项）**：ListFilterBar.kt / InkSplash.kt 的坏 import（`foundation.interaction.remember` 不存在）导致工作区 compileDebugKotlin 失败；local.properties 缺失导致 SDK 定位失败。

**验证**：全量 `:app:testDebugUnitTest` 274/274 通过（BUILD SUCCESSFUL）→ 附 `assembleDebug` APK 构建；P3 闭环落地后基线升至 **285/285**（见 九）。

**测试修复备注**：GachaDeckScreenTest.G05（全量套件挂、单类绿）实为 Robolectric 实例化 MilanApp 引发的 GameState 单例内容竞态（后台异步注入真实 data.json 抢先于 @BeforeClass 的 testContent）——G05 已改内容无关断言；**基建级根治已落地**（非另议）：`GameState.resetForTest()`（test-only internal 钩子）+ 所有触碰单例的测试类在注入前先 reset，消除跨类执行顺序耦合（GachaDeckScreenTest / GameStateInitFailureTest 两处）。

## 九、P3 功能缺口决策收口（2026-09-02 决策轮，全量验证 285/285）

好感度此前是「**宣称有系统、实际零接线**」的展示层：页面信息卡宣称「赠送礼物、出战战斗提升」，但无任何入口，服务层 `addCharacterAffinity` 零生产调用点，好感恒为 0。与故事锁定项（点击静默）同属「点了没反应」体感缺口，本轮按用户拍板闭环。

| 决策项 | 用户拍板 | 落点 |
|---|---|---|
| 好感度修复范围 | **完整闭环：入口 + 自动产出** | 见下三处接线 |
| 剧情页锁定反馈方式 | **Snackbar 轻提示** | StoryScreen.kt |
| 赠送定价 | **100 星尘 → +200** | AffinityFormulas.kt（GIFT_COST_SOFT / GIFT_AFFINITY_AMOUNT） |
| 战斗胜利产出范围 | **出战全员 +20/场** | AffinityFormulas.kt（BATTLE_WIN_AFFINITY）→ TowerService.runTowerFloor |

**实现清单（7 文件）**：

1. **AffinityFormulas.kt**（新增）：好感数值单一事实来源——`EXP_PER_LEVEL=1000 / MAX_LEVEL=10 / MAX_AFFINITY=10000 / GIFT_COST_SOFT=100 / GIFT_AFFINITY_AMOUNT=200 / BATTLE_WIN_AFFINITY=20` + `levelOf/expInLevel`。替代 AffinityScreen 内硬编码 1000/10（与养成走 EconomyFormulas 同款纪律）。
2. **ServiceCore.kt**：好感成为多服务共享字段（赠送/战斗/剧情三处写）→ 临界区内辅助 `affinityOf` + `addAffinityDelta`（钳位 MAX_AFFINITY、delta<=0 无操作），聚合服务禁止就地散落修改。
3. **GameService.kt**：`addCharacterAffinity` 加满级封顶（原无上限，超 Lv.10 后 UI 恒显 Lv.10 但经验继续涨）；新增 `giftAffinity`（锁内预算校验 → 原子扣 100 星尘 + 加 200 好感 → 单事务落盘；不足/满级返回 Rejected 由 UI 提示原因）。
4. **TowerService.kt**：`runTowerFloor` 胜利分支给编队全员 +20（复刷已通层照发——F1 门控只针对星尘，好感按「每场胜利」）；失败/平局不发；事务快照+回滚覆盖好感；onCommit 条件补 `result.victory` 保证快照刷新。
5. **DialogueScreen.kt**：剧情选项 `affinityBonus` 落账（此前只渲染从不记录）——选项点击时给说话者 `addCharacterAffinity`（narrator 跳过）。
6. **AffinityScreen.kt**：赠送入口 UI——每卡「🎁 赠送」按钮（满级徽章「❤️ 已满级」替代）；禁用仍可点走 Rejected 原因 Snackbar；`remember(snapshot.revision)` 重算好感（修 C2 同款无 key remember 不刷新缺陷）。
7. **StoryScreen.kt**：锁定章节/关卡点击不再静默——Snackbar 点名解锁条件（章节 →「通关「上一章标题」全部关卡后解锁」；关卡 →「通关「前置关卡名」后解锁」，经 `findStoryStageDef` 取前置关卡标题）。

**回归测试**：`AffinityLoopServiceTest.kt`（新增 11 用例，净变动断言口径）：赠送净扣/净加、余额不足、满级拒绝、近上限钳位、落盘失败整体回滚；addCharacterAffinity 封顶与回滚；爬塔胜利全员 +20（含复刷照发）、失败零产出、钳位、SaveFailed 回滚好感+门票。

**验证（沙箱内 3 次构建）**：全量 `:app:testDebugUnitTest` **285/285 通过**（BUILD SUCCESSFUL 42s）→ `:app:assembleDebug` BUILD SUCCESSFUL。编译期修 3 处：两文件漏 `import kotlinx.coroutines.launch`、StoryScreen 误用内部方法名 `findStageDef`（门面实为 `findStoryStageDef`）。

**明确延后（独立系统设计任务，非本缺口）**：好感等级奖励领取机制——信息卡宣称 1/3/5/8/10 级解锁语音/剧情/头像框/皮肤/称号，但无领取入口，任务类型 `CLAIM_AFFINITY` 存在却空循环。本轮只闭环「好感数值增长」，奖励领取需单独排期（涉及奖励箱/占用位设计）。

