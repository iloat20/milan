# Milan 项目深度代码审查报告

日期：2026-07-30 ｜ 范围：MauiMilan/ 全部约 30 个源文件（Core / Services / Save / Activities / UI）+ 构建配置
方式：逐行审查 + 交叉核验（所有行号指控均已在源码中二次确认）

## 总体评价

架构分层清晰（Activities → GameService → 纯 Core 域引擎 → Save），**Core/ 确认无任何 Android/UnityEngine 依赖**，符合设计约束；构建通过（0 错误），两份 data.json 内容一致。但存在 **2 条可导致 App 永久无法启动的存档崩溃链**、**2 个抽卡经济/概率正确性 bug**、**1 处真实内存泄漏**，且 EventBus 整套是死代码。整体成熟度：MVP 可玩，距可发布还有明确差距。

---

## P0 — 严重（会崩溃 / 玩家可感知的错误）

### 1. 存档损坏 → App 永久无法启动（崩溃链）
- `SaveData.cs:24-25`：`FromJson` 只处理了 `null` 返回值，`JsonSerializer.Deserialize` 对空串/损坏 JSON 抛出的 `JsonException` 未捕获，违反 CLAUDE.md 中"损坏必须回退 CreateDefault"的硬规则。
- `SaveManager.cs:20`：`Load()` 不捕获异常。
- `GameState` 静态初始化即调用 `Load()` → 异常变成 `TypeInitializationException`，**每次启动都崩，用户无法自救**。

**修复**：`FromJson` 整体 try/catch 回退 `CreateDefault()`；`SaveManager.Load` 再兜底一层并备份坏档（`save.json.bak`）。

### 2. 非原子写档，进程被杀即坏档
- `LocalSaveProvider.cs:22`：`File.WriteAllText` 直接覆盖。写到一半进程被杀 → 文件截断 → 触发问题 1。

**修复**：写 `save.json.tmp` → `File.Replace`/`File.Move` 原子替换。

### 3. 抽卡稀有度回退错乱（概率与显示都错）
- `GameService.cs:344-347`：`pool_flame` 的 `RarityWeights` 含 40% R 段，但该池没有 R 角色。`GetEntriesForRarity` 返回空 → 回退 `PickFromPool` **全池加权抽**（可能抽出 UR/SSR），而 `PullResult.Rarity` 仍标记为 R。公示概率、结果展示、保底统计全部失真。

**修复**：回退时按"就近升一档稀有度"取候选，且 `Rarity` 以实际抽中角色的稀有度为准；加载时校验"每个权重段必须有候选角色"。

### 4. 重复角色零补偿（经济漏洞）
- `GameService.cs:350-358`：`isNew == false` 时直接丢弃——不发碎片、不退款。设计文档中的"重复转星魂/碎片"完全缺失，十连出重复等于白扣钱。

**修复**：重复时按稀有度发放 `StarFragment` 道具（Items 里已有 `ItemSaveState` 结构可用）。

### 5. Burst 粒子无界增长（真实内存泄漏）
- `UI/PortraitView.cs:155-174, 303-336`：`TriggerBurst` 每次加 12 个 `IsBurst` 粒子，但绘制循环里 `if (p.IsBurst) continue;` 导致它们**永不更新、永不移除**，每次点技能列表就变长。

**修复**：burst 粒子加生命周期，过期移除。

---

## P1 — 设计隐患（尽快处理）

6. **扣币与存档非事务**（`GameService.cs:338-362`）：先扣钱、循环抽完才 `Save()`；中途进程被杀则整次抽卡不落盘（钱和角色一起丢，尚可接受，但与 pity 计数结合可能不一致）。建议 Pull 结束前的任何 early-return 路径都不留下半状态。
7. **EventBus 全套死代码**：`Subscribe/Publish/Dispatch` 在应用代码中零调用（仅 docs 提及）。要么接线（Activity 靠事件刷新而非 OnResume 全量重建），要么删掉，别留“看起来在工作”的假架构。另有隐患：lambda 订阅无法退订（泄漏）、handler 异常仅 Debug 输出被吞、Dispatch 内再 Publish 会同轮消费可能死循环（`EventBus.cs:11-41`）。
8. **进阶系统未接线**：`ExpToLevel`/`StatAtLevel` 等 ProgressionEngine 方法无任何调用方；养成数据没有持久化路径。
9. **动画无限重绘且不随生命周期暂停**：`CosmicBackground.cs:105`、`ParticleView.cs:152`、`AnimatedPortrait.cs:90`、`CharacterPortrait.cs:92`、`PortraitView.cs:238`、`CardEffects.cs:64/86`、`FullBodyCharacter.cs:90` 都在 `OnDraw` 尾部无条件 `Invalidate()`，全项目无 `OnDetachedFromWindow` 暂停。后台/滚出屏幕仍烧电。建议统一做一个带 start/stop 的 `AnimatedViewBase`。
10. **GachaActivity 动画期回调风险**（`GachaActivity.cs:185-207, 244-253`）：4 个 `PostDelayed`（最长 1800ms）捕获 Activity，动画中按返回/旋转会导致结果不显示（钱已扣）。需在 `OnDestroy` 中 `RemoveCallbacks`，抽卡按钮加连点保护。
11. **保底语义待确认**（`PityCounter.cs:19-23` + `GameService.cs:344`）：硬保底传的是 rarity 3（SSR）而非 UR；且非保底出金不重置计数器。若与设计文档意图不符需修正。
12. **静态 Bitmap 缓存无上限**（`PortraitView.cs:15` PortraitLoader）：`ClearCache` 从未被调用，也不响应 `OnTrimMemory`。

---

## P2 — 质量改进（有空就做）

13. **文档漂移**：CLAUDE.md 写 ".NET 8 / net8.0-android"，csproj 实际是 **net10.0-android**；产物路径也应更新。obj/bin 下同时残留 net8/net9/net10 三代产物，建议清理。
14. **硬编码数据与内容脱节**：`CollectionActivity.cs:72` 收集总数写死 20；`GachaActivity.cs:71-72` 概率文案写死——都应从 `GameState.Service` 动态取。
15. **OnDraw 每帧分配对象**：多处在 OnDraw 里 `new LinearGradient/Path/RectF`（`CosmicBackground.cs:72`、`CharacterPortrait.cs:97/161`、`PortraitView.cs:250-299` 等），60fps 下 GC 抖动。预分配复用。
16. **重复/死代码**：稀有度配色三处重复（`CharacterCard.cs:19-30`、`UIHelper.cs` AppTheme、`CollectionActivity.cs:141-143`）；`ResultCard` 与 `WorldDecoration.EnhancedResultCard` 近似重复且 ResultCard/GachaChip/FullBodyCharacter 疑似未引用。
17. **健壮性小项**：`GachaEngine.cs:18` 权重和为 0 时抛异常；`ElementTheme.cs:24` 未知元素静默回落 Flame 掩盖数据错误；`GameState.cs:28` `GetAwaiter().GetResult()` 同步阻塞主线程做文件 IO。
18. **零测试**：Core 引擎全部注入 `System.Random`、天然可测，却没有一个测试。至少给 GachaEngine（概率分布）、PityCounter（保底触发/重置）、SaveData（损坏回退）补上 xUnit——问题 1/3/11 本来都能被测试拦住。
19. **360° 检视名不副实**（`InspectionActivity.cs:107-119`）：只有 `RotationY` 平面旋转，转到侧面就是一条线。作为核心卖点，建议至少做多角度立绘切换或引入 OpenGL/Filament 真 3D。

---

## 建议执行顺序

| 批次 | 内容 | 理由 |
|---|---|---|
| ① 立即 | P0-1/2（存档崩溃链）| 一旦发生用户永久流失 |
| ② 本周 | P0-3/4（抽卡正确性）+ P1-10（连点/回调） | 核心玩法正确性 |
| ③ 本周 | P0-5 + P1-9（动画治理，统一 AnimatedViewBase） | 性能与耗电 |
| ④ 下周 | P2-18 补测试 → 再做 P1-7/8（EventBus 接线或删除、进阶接线） | 先有安全网再动架构 |
| ⑤ 随手 | P2-13/14/16（文档、硬编码、去重） | 低成本高收益 |
