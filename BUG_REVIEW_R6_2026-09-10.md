# Milan 全库 Bug 审查 R6 · 2026-09-10

> 方法：三路并行 Explore（core/data · UI/runtime · content/config）→ 主代理逐条取证复核。
> 范围：main@`5e33dff`（含第 8 节叙事/UI 与好感奖励刚合入代码）。
> 只记**有证据的真实缺陷**；设计取舍与「已验证干净」项另列。

---

## 执行摘要

| 级别 | 数量 | 一句话 |
|------|------|--------|
| **P0** | 4 | 新手引导全屏锁死 / 终极天赋列不可见 / 存档 main 丢失不走备份 / 策略战斗敌方回合可双击 |
| **P1** | 10 | 多处锁外 check-then-act 双发、回滚漏硬通货、升满溢出、套装百分比未生效等 |
| **P2** | 6 | 信任客户端胜利、动画残留、主线程 IO 等 |

**优先修**：P0-1 引导锁死（新玩家打不开核心循环）→ P0-2 终极天赋（养成卖点缺失）→ P0-3 存档备份链 → P0-4 战斗双击。

---

## P0

### P0-1 新手引导全屏吞点击，核心循环走不动

- **位置**：`MilanKotlin/app/src/main/java/com/milan/game/ui/tutorial/TutorialOverlay.kt:121-130`、`:178-186`
- **证据**：
  ```kotlin
  Box(Modifier.fillMaxSize().clickable(..., onClick = {}))  // 全屏吞触摸
  // 非 INTRO 步 CTA 仅 onNavigateTab，不完成步骤
  if (tab != null) onNavigateTab(tab) else onNavigateTab("home")
  ```
- **后果**：遮罩挡住抽卡/编队/战斗按钮，业务路径无法勾完成；唯一出路是「跳过」。与设计「非全屏锁死」相反。
- **叠加缺陷**：`FIRST_LEVEL` 的 `tabHint=null` 发 `"home"`，而 `NavItem.label` 是中文（`"主页"`/`"抽卡"`…），`MilanNavHost` 按 label 匹配 → 静默 no-op。

### P0-2 全部 31 棵天赋树的终极节点不可见

- **位置**：`data.json` 各树 `BranchIds` 仅 `branch_power/defense/utility`；节点有 `BranchId: "branch_ultimate"`（t13×31）
- **UI**：`ProgressionPanels.kt:449-485` 只渲染 `tree.branchIds` 列并按 `branchId` 过滤
- **引擎**：`TalentEngine` 无 `BRANCH_ULTIMATE` 常量，`talentMultipliers` 静默忽略未知分支
- **后果**：UR/SSR 终极天赋从 UI 无法展示与加点；养成长线卖点缺失。

### P0-3 主档丢失时备份链不生效（静默整档回默认）

- **位置**：`SaveManager.kt:36-37` + `AndroidSaveProvider.kt:56-58`
- **证据**：`save()` 先 `main→bak` rename，再 `tmp→main`。窗口内进程被杀 → `main` 不存在。下次 `load()`：
  ```kotlin
  if (!provider.exists()) SaveData.createDefault()  // 永不 loadBackup()
  ```
- **后果**：`.bak` 里有完整进度也不读，静默新号。注释写「靠 load 走备份链」，代码走不到。

### P0-4 策略战斗敌方回合 `delay(350)` 期间可再次出招

- **位置**：`StrategicBattleViewModel.kt:114-159`
- **证据**：末位玩家行动后 `advanceActor` 启动敌方回合协程，仅 `delay(350)`，无 `enemyTurn`/`busy` 门闩；技能栏仍可点（AOE 在 `selectSkill` 自动 `confirm()`）。
- **后果**：用旧 `st` 再执行一次玩家行动，随后敌方回合用捕获状态覆盖 `_ui`，状态错乱/双结算风险。

---

## P1

| # | 位置 | 问题 |
|---|------|------|
| 1 | `StoryService.kt:76-82` | `isStageCompleted`/`canEnter` 在 `transaction` **锁外** → 并发双完成双发奖 |
| 2 | `DailyMissionService.kt:170+` | 活跃宝箱 `claimedChests.contains` 锁外 → 双领货币 |
| 3 | `MonetizationService.kt:186+` / `:237+` | 通行证奖励、首充翻倍判定锁外 → 双发 |
| 4 | `StoryService.kt:213-215` | 扫荡 rollback 只还原 `softCurrency`，**漏 hard** → 落盘失败可白嫖钻石 |
| 5 | `GameService.kt:323` + `ProgressionPanels.kt:187` | 「升满」传 `Int.MAX_VALUE`；`progress + MAX` 溢出为负，每日「升级」任务卡死 |
| 6 | `ServiceCore.kt:614-625` | 套装 `StatBonus.isPercentage` **未读**，`+15%` 被加成 +15 点 → 竞技/爬塔战力错 |
| 7 | `AffinityViewModel` / `AffinityScreen` | 赠礼/领取无 busy 防连点，按钮恒 `enabled=true` → 可双写 |
| 8 | `StrategicBattleScreen.kt:103-112,145` | 组合期写 `mutableStateMap`；`?: UnitStrikeFx()` 每帧新建 |
| 9 | `BattleStrikeFx.kt:89-93,210-221` | `graphicsLayer` 内 `syncFromAnims()` 写 snapshot state；死亡 `death=1` 不复位 |
| 10 | `DamageFloatingText.kt:52-54` | `events.map { remember {} }` 违反固定槽位；`events.size` 变化时动画错位 |

其他 P1 倾向：
- `MilanNavHost` 启动失败重试在**主线程**读 `data.json`（ANR 风险）
- 抽卡 Charge/Beam 阶段 `skipReveal` 在 `visible` 前 no-op，UR 路径不可跳过
- `data.json` 5 处 SkillId 缺下划线（`kikyo3`/`leishen2`/…），管道易踩

---

## P2

| # | 位置 | 问题 |
|---|------|------|
| 1 | `TowerService.settleStrategicBattle` | 信任调用方 `victory` 参数，不重演算 → API 可刷层奖励 |
| 2 | `ArenaService` 免费次数 | 上限检查锁外（同类 TOCTOU，影响小） |
| 3 | `BattleResultOverlay` | 全屏 dismiss 热区在首帧已生效，易误关 |
| 4 | `PortraitImage` 水墨 grain | 每帧全屏 `drawPoint` 循环，滚动易掉帧 |
| 5 | Tutorial | `AnimatedVisibility(visible=true)` + 提前 return，无退出动画；`lastFx` 写了未用 |
| 6 | battle SFX | 现为 RIFF/WAVE 占位（扩展名 `.ogg`）；换 ExoPlayer 路径会挂 |

---

## 已验证干净（本卷）

- 聚合服务 `withWriteLock` + `transactionLocked` **无重入死锁**（教程/好感/抽卡路径）
- `claimAffinityReward` / 教程步骤：锁内校验 + 完整 rollback（含 SaveFailed 测试）
- `GachaService.pull`：先扣后发、保底/UP/碎片全量回滚
- `resetSave` → `invalidateOwnedSavesCache` 正确
- WorkManager proguard keep 规则在
- `generate_gamecontent.py` 输出路径已指向 `:core`
- 无重复角色 id；池条目均指向存在角色；元素合法
- 触 `GameState` 的测试均先 `resetForTest()`

---

## 建议修复顺序

1. **P0-1** 引导：去掉全屏吞点击（或 `pointerInput` 只挡非热区）；CTA 用 `NavItem` 枚举而非中文 label 拼 `"home"`
2. **P0-2** 天赋：`data.json` 各树 `BranchIds` 补 `branch_ultimate`，或 UI 取 `BranchIds ∪ nodes.branchId`；`TalentEngine` 补常量
3. **P0-3** 存档：`!exists()` 时先试 `loadBackup()`
4. **P0-4** 战斗：敌方回合加 `enemyActing` 布尔门闩，技能栏 disable
5. **P1 批**：所有 `transaction` 外的 claimed/completed 检查移入锁内 mutate；扫荡 rollback 补 hard；升满上报实际升级数；套装百分比按基数计算

---

## 附录：本卷未修（仅记录）

内容/工具：SkillId 命名统一、`generate_gamecontent` 发射 `BranchIds`、真 Ogg SFX 替换、`PortraitImage` KDoc 过期文案。
