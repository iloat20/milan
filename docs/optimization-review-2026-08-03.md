# 深入优化审查报告（2026-08-03）

## 方法
全仓静态扫描 + 热点定位（渲染循环、每帧分配、序列化频率、同步 IO、线程模型、设置持久化、空状态）。
基线数据：`data.json` 49KB / 14 张武器 PNG / **0 个后台线程** / 26 个帧驱动点 / 39 处 `Invalidate()` 调用。

---

## 一、性能（按严重度排序）

### 🔴 P0 高 — 常驻动画每帧分配（持续 GC 抖动）
- **P1 `CosmicBackground` 每帧 `new LinearGradient`**
  - 位置：`UI/CosmicBackground.cs:101`（`OnDraw` 内），且 `HomeActivity.cs:190` / `InspectionActivity.cs:51` 把它作为**主背景**，`Start()` 后 `_running=true` 持续 `Invalidate`。
  - 表现：主背景永不停止，每帧分配一次渐变并上传 GPU → 持续 GC 抖动、可能掉帧。这是全 app 最常驻的分配源。
  - 修复：尺寸/配色不变时缓存 `LinearGradient`（仅在 `OnSizeChanged` 或配色变化时重建），复用单个实例。
- **P2 `CharacterTurntableView` 自动旋转每帧 `new RadialGradient` ×2**
  - 位置：`UI/CharacterTurntableView.cs:202,215`（`OnDraw` 内）；`_autoSpin=true` 默认开启，`PostInvalidateDelayed(16)` 持续旋转（`:130`）。
  - 表现：360° 立绘是主打展示功能，常驻旋转 → 每帧 2 次渐变分配。
  - 修复：渐变参数（圆心/半径/颜色）固定，缓存为字段，尺寸变化时重建即可。

### 🟡 P1 中 — 动画期分配 / 空转 / UI 线程 IO
- **P3 `CardEffects` 每帧 `new RectF` / `new Path()`**
  - 位置：`UI/CardEffects.cs:154,156,262`（翻转/微光动画期间每帧）。修复：复用 `_rect`/`_path` 字段（同 PortraitView 已做的范式）。
- **P4 `CharacterPortrait` OnDraw 0 尺寸空转**
  - 位置：`UI/CharacterPortrait.cs:66` `if (w == 0 || h == 0) { Invalidate(); return; }`。
  - 表现：view 尚未布局或被隐藏（尺寸 0）时**无限空转** `Invalidate`，空耗 CPU。之前对 PortraitView/RiftPortal 已按此修，这里漏了。
  - 修复：0 尺寸直接 `return`，不 `Invalidate`（与已修文件一致）。
- **P5 `CrashReporter` 在 UI 线程同步文件 IO**
  - 位置：`Infrastructure/CrashReporter.cs:49,119,124,168,173,181,226,292`（`File.WriteAllText`/`ReadAllText`，含启动 `Boot` 写、每次 `Write` 的 `Mirror` 双写）。
  - 表现：崩溃取证/面包屑在 UI 线程落盘，偶发掉帧；启动时若磁盘慢会拖慢首屏。
  - 修复：写盘走单线程写队列（后台 `Task`/Handler），或至少 fire-and-forget，不在 UI 线程阻塞。

### 🟢 P2 低 — 扩展性风险
- **P6 `Parallax3DPortraitView.BuildBlurredBackground` 每次 set 重建模糊位图**
  - 位置：`UI/Parallax3DPortraitView.cs:356-361`（`Bitmap.CreateScaledBitmap` + `Bitmap.CreateBitmap`）。
  - 表现：非每帧，但每次源图变化都重算 → 可缓存 + 仅源/dest 变化时重建 + 复用 `Paint`。
- **P7 全项目零后台线程**
  - 表现：`Gacha Pull`（GachaEngine 计算）与启动 `data.json` 解析均在 UI 线程同步跑；当前数据小（49KB）尚可，但养成/批量计算若加重会卡。
  - 注意：本项目曾因"UI 线程 `.GetResult()` 死锁"踩坑，若引入异步须严格 `ConfigureAwait(false)` 且绝不 `.GetResult()`。

---

## 二、架构

- **A1 EventBus 仅货币接线，列表/网格仍 `OnResume` 全量重建**
  - `CharacterListActivity.RefreshOwned()` / `CollectionActivity.BuildGrid()` 整页重建会**丢失滚动位置**。已有 `CurrencyChanged` 范式可照抄，把列表增量刷新（仅增/改受影响项）也接 EventBus。
- **A2 设置项不持久化（功能缺陷）**
  - `SettingsActivity.cs:56-57` 的 `GoldToggle`（音效/震动）`CheckedChanged` **只弹 Toast，不写任何 `SaveData`/文件**；`vib` 默认 `IsChecked=true` 但无对应存储。用户改的设置重启即丢。
  - 修复：在 `SaveData` 加设置字段（或 `SettingsJson`），toggle 时持久化 + 启动时读回。
- **A3 5 个 Activity 仍各自 `OnCreate→SetContentView(BuildLayout())` 大段样板**
  - GameNavBar/ResourceBar/玻璃面板已抽取，重复度可接受，优先级低。

---

## 三、UX

- **U1 设置"假开关"**：同 A2，点了无效果/不保存，体验差 → 与 A2 一并修。
- **U2 抽卡演出不可跳过**：`WeaponFxView` 演出约 2s+，无点击跳过 → 建议加"点击跳过"。
- **U3 列表无排序/筛选/搜索**：`CharacterListActivity` 仅按拥有展示，无按稀有度/元素排序。
- **U4 资源不足反馈**：仅 Toast（已有），可接受。

---

## 建议落地顺序（ROI）
1. **P1 + P2**（gradient 缓存）：最高 ROI，几行改动，消除主背景 + 360° 展示的常驻 GC 抖动。
2. **P4**（0 尺寸空转）：一行，消除 CPU 空转隐患。
3. **P5**（CrashReporter 异步写）：中等，消除偶发掉帧与首屏拖慢。
4. **A2 + U1**（设置持久化）：功能正确性，修"假开关"。
5. **P3 / P6 / A1 / U2 / U3**：按资源与需求推进。

> 注：本次为静态审查，本机无 adb/真机，未做实机帧率/内存采样；上述为代码级确定性证据。
