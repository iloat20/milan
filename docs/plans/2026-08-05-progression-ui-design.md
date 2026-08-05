# 养成系统 UI 设计方案（2026-08-05）

## 1. 目标与范围
把已有但未暴露的养成能力做成可玩界面。当前 `CharacterSaveState`(Level/Stage/Stars/TotalExp/UnspentPoints/TalentPoints)、`ProgressionEngine`、`TalentEngine`、天赋树数据、`Pull` 重复补偿星魂碎片均已就绪，缺的是**消费操作**与**全部 UI**。

本期范围（核心，必做）：
- **升级 (LevelUp)**：消耗星尘，提升等级，每升 1 级 +1 天赋点，提高等级上限随突破解锁。
- **突破 (Ascension / Stage)**：消耗星魂碎片 + 星尘，Stage 1→4，解锁更高等级上限与阶段倍率。
- **天赋加点 (AllocateTalent)**：消耗天赋点，按前置约束点亮天赋树节点。
- **独立全屏 `ProgressionActivity`**（twilight 风格），从详情页铭牌「养成 ▲」进入，支持左右切角色。

可选（按需，默认 deferred）：**升星 (Stars)** —— 已实现（2026-08-05）。消耗星魂碎片（成本 = 当前星 × 20），`Stars+1`，属性 +5%/星（并入 `GameState.ComputeStatsAt` 的 `starMul`，喂给 `StatAtLevel` 倍率槽）。UI 接入养成页「升星」面板。

## 2. 前置：基线收口
当前工作树有 8 个核心文件未提交（GameService +494 等），包含养成底层铺垫（重复补偿、天赋树补全、EventBus 扩展）。实现前必须先：
1. 以 `--no-incremental` Release 构建验证这批改动 **0 错 0 警**（PATH 无 dotnet，须用 `~/.dotnet/dotnet.exe`；非沙箱构建）。
2. 将基线改动与本文档一并提交，作为养成 UI 的干净地基。
_build 前若发现未提交改动编译不过，先修基线再叠加 UI。_

## 3. 领域层（GameService 新增操作）
经济常量（集中定义，便于调参）：
- `MaxLevelForStage(stage) = stage * 20`（Stage1→20 级，Stage4→80 级）。
- `LevelCost(level) = level * 50` 星尘（随等级指数上升）。
- `StageMultiplier(stage)` = {1:1.0, 2:1.15, 3:1.3, 4:1.5}（喂给 `StatAtLevel`）。
- 突破成本：`AscendCost(stage)` = 星魂碎片 `stage*20` + 星尘 `stage*500`。
- 天赋点：升级时 `UnspentPoints += 1`（封顶 = 树总点数）。

新增方法（均先校验可支付再变更，落盘失败回滚，遵循 `Pull` 的事务范式）：
- `bool LevelUp(charId, int n=1)`：循环校验星尘；扣星尘、Level++、TotalExp 累加至目标、`UnspentPoints++`；达上限返回 false。
- `bool Ascend(charId)`：校验 `Stage < MaxStage` 且资源足；扣星魂碎片+星尘、`Stage++`；解锁等级上限。
- `bool AllocateTalent(charId, nodeId)`：取该角色树，用 `TalentEngine.CanAllocate` 校验前置与 `UnspentPoints >= Cost`；满足则扣点、`TalentPoints.Add(nodeId)`。
- `IReadOnlyList<TalentNodeData> GetTalentTree(charId)` + `Dictionary<string,string[]> PrereqMap(tree)`。
- `int GetStarFragments()`：读 `SaveData.Items` 中 `item_star_fragment`。
- `StatsSnapshot ComputeStats(charId)`：对 `BaseStats[4]` 各调 `StatAtLevel(base, level, stage, StageMultiplier)`，叠加已点亮天赋的简易加成（MVP：攻/防/血/速各 +节点对应 %），返回当前值与下一级/下一阶预测值。

## 4. UI 层（ProgressionActivity 布局）
全屏 twilight，结构自上而下：
- **Hero 区**：复用 `Parallax3DPortraitView` + `PortraitGrade` 铺顶 ~50%；铭牌（名/稀有度/元素）+ 返回 + 左右切换（同详情页）；右下「养成 ▲」反向入口即为本页自身，故改为显示当前角色切换。
- **资源条**：紧凑版星尘（金）+ 星魂碎片（青），复用 `AppChrome.ResourceBar` 风格；订阅 `CurrencyChanged` 刷新。
- **玻璃面板（滚动）**，分块（复用 `UI.GlassPanel` / `UI.TitleWithOrnament` / `UI.Tabular`）：
  1. _等级/经验_：大号等级 + 上限；细进度条（`TotalExp` 在当前级内占比）；「升级 ×1 / ×5 / MAX」金按钮，按钮旁实时显示星尘消耗；不足置灰。
  2. _突破_：Stage 星标（★×Stage / ○×余）；「突破」霓虹按钮，显示星魂碎片+星尘成本；满阶锁定并提示。
  3. _属性_：HP/ATK/DEF/SPD 克制数据行（沿用详情页样式），右侧附「下一级/下一阶 →」预测值（青色）。
  4. _天赋树_：三列（power 红 / defense 蓝 / utility 绿）节点环；状态：已点=熔金辉光，可点（前置满足且有余点）=霜蓝描边，锁定=暗。点击 `UI.TapFeedback` 弹确认或直接加点；顶部显示「天赋点 ×N」。
- 按钮统一 `UI/ThemeButtons`（Gold/Neon/Danger），触摸走 `UI.TapFeedback`（勿手动 `IOnTouchListener`）。

## 5. 数据流与事件
- 新增 `ProgressionChanged`（`readonly struct` 轻标记，含 `charId`）于 `Core/Infrastructure/EventBus/Events.cs`。
- `GameService.PublishProgressionChanged(charId)` = `Publish + Dispatch`（领域层唯一出口），操作落盘成功后才广播；回滚路径不广播（同 `CurrencyChanged` 约定）。
- `ProgressionActivity`：`OnResume` 先 `Subscribe<ProgressionChanged>(OnProgChanged)` + `Subscribe<CurrencyChanged>(OnCurChanged)` 再直刷；`OnPause` `UnsubscribeAll(this)`。
- 详情页 `CharacterDetailActivity` 也订阅 `ProgressionChanged` 以刷新属性 Tab（若其在前台）。
- 资源栏刷新走既有 `CurrencyChanged`（星尘/钻石）；星魂碎片作为 `Item` 也经此事件刷新显示。

## 6. 错误处理与铁律
- 所有消费操作：**先算后扣**，资源不足直接 `return false` + `Toast` 提示，内存零变更；`_save.Save()` 失败则回滚本次内存改动（参照 `Pull` 的 original* 快照范式），避免存档与内存不一致。
- 严守既有铁律：渐变用 `UI.ColorLong/ColorLongs`（long 重载）；`OnDraw` 开头 `if(w==0||h==0) return;` 且 `RadialGradient` 加 `if(radius<=0) return;`；自绘动画继承 `AnimatedEffectView`；`OnTrimMemory` 释放 native 缓存；`dp` 只在最终使用点调一次；禁 UI 线程 `.GetResult()` 死锁。
- 天赋树数据来自 `data.json`，`Initialize` 已过滤 `Nodes==null` 的空树；UI 仍对空树/缺树做空检查，避免养成界面静默空白。

## 7. 构建与验证
- 实现后必须 `--no-incremental` Release 构建 **0 错 0 警**（约 2–3 分钟；全新构建勿中途 kill）。
- 无单测框架（MAUI Android 接 xUnit 成本高）。改用轻量验证：写临时 C# 脚本 `~/.dotnet/dotnet.exe run`（`#:property JsonSerializerIsReflectionEnabledByDefault=true`），对 `LevelCost`/`ExpToLevel` 往返、`CanAllocate` 前置链、`StageMultiplier` 做断言，验证数学正确后删除脚本。
- 手动走查：抽重复角色→得星魂碎片→进养成页→升级/突破/加点→返回详情页属性随动→重开 App 数据保留。

## 8. 风险与未决
- **升星 (Stars)**：已实现（2026-08-05）。成本 = 当前星 × 20 星魂碎片，属性 +5%/星，UI 接入养成页「升星」面板；满星由 `CharacterDataEntry.MaxStars`（UR7/SSR6/SR5/R4）决定。
- **天赋/升星实际战力影响**：已落地。`BattleSimulator.StrikeDamage`（atk-def/2）为战斗伤害单一事实来源，`BattleActivity` 出牌按其结算，攻方属性由 `GameState.ComputeStats` 生成（含等级/突破/天赋/升星加成），星级提升直接体现在每次伤害。设计稿原「需扩展 BattleSimulator 读 TalentPoints」已无需单独扩展——加成经由 ComputeStats 统一注入。
- **经验来源**：本期不引入经验药，升级直接耗星尘，规避无 farming 来源的死循环；如后续加经验本再改 `LevelUp` 为耗经验物。
