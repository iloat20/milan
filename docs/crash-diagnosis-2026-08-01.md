# Milan 闪退诊断报告

日期：2026-08-01
症状：安装 Release APK 后点击图标 → 立即闪退回桌面 → **无任何错误对话框**

---

## 一、为什么之前查不出来

`HomeActivity.OnCreate` 里有完整的 try/catch，出错会弹「Milan 启动错误」对话框。
既然什么都没弹，说明崩溃发生在**这个 try/catch 覆盖不到的地方**。覆盖不到的有四类：

| 位置 | 是否被保护（修复前） |
|---|---|
| `MauiApp.OnCreate`（Application，早于所有 Activity） | ❌ 完全裸奔 |
| `OnCreate` 里 try 之前的 `SetContentView(BuildFallback(...))` | ❌ |
| `OnResume` / `OnConfigurationChanged` 等生命周期回调 | ❌ |
| **自绘 View 的 `OnDraw`**（在 OnCreate 返回之后才执行） | ❌ |

其中 `OnDraw` 最关键：它由渲染线程在 `OnCreate` 完成之后调用，
**任何异常都会直接杀死进程，而且不经过 Activity 的 try/catch**——症状就是「启动一闪就没了，没有对话框」。

上一轮把原因归给 AOT（XAGNM7009）并关掉了 AOT。那是**推测，没有现场证据**。
本次已确认当前 APK 里 `libaot* = 0`（AOT 确实已关），所以如果仍然闪退，AOT 就不是原因。

---

## 二、已确认的 Bug

### 🔴 Bug 1 — `HaloView.OnDraw` 缺零尺寸保护（首屏必经，最可能的闪退元凶）

`Activities/HomeActivity.cs` · `HaloView.OnDraw`

```csharp
var r = Math.Min(Width, Height) * 0.5f;
_paint.SetShader(new RadialGradient(cx, cy, r, ...));   // r <= 0 时抛异常
```

Android 的 `RadialGradient` 在**半径 ≤ 0 时抛 `IllegalArgumentException: radius must be > 0`**。

决定性证据：我扫描了项目里全部 17 处 `OnDraw`，
**其余每一个都写了 `if (w == 0 || h == 0) return;`，唯独 `HaloView` 漏了**
（连同一文件的 `PortraitView` 都专门写了 `if (radius <= 0) return;`）。
这是明显的疏漏，而 `HaloView` 正好在首页主视觉里、开屏第一帧就要绘制。

**已修复**：补上 `if (r <= 0f) return;`。

### 🔴 Bug 2 — `data.json` 从未被加载，20 个角色只剩 8 个

`MauiMilan.csproj`：

```xml
<AndroidAsset Include="Platforms\Android\Assets\**\*" />   <!-- 缺 Link -->
```

没有 `Link`，asset 会按完整相对路径打进包。实测解包 APK：

```
assets/Platforms/Android/Assets/data.json     ← 实际路径
```

而运行时读的是 `context.Assets.Open("data.json")` → 必然 `FileNotFoundException`
→ 被 catch 吞掉 → 静默退回 `LoadFallback()`（只有 **8** 个角色，data.json 里有 **20** 个）。

连带影响：首页「诸神名录」查 `char_ur_zhulong` / `ur_xingtian` 等 6 个 ID，
在 8 人兜底表里查不到就 `continue`，所以那一排头像**是空的**；
20 张立绘 PNG 里有 12 张根本没机会显示。

**已修复**：csproj 加 `Link="Assets\%(RecursiveDir)%(Filename)%(Extension)"`；
`GameService` 改为按 3 个候选路径依次尝试，且数据为空时才回退。
重新打包后验证：`assets/data.json` → Characters: **20**、Pools: 1。✅

### 🟠 Bug 3 — 主视觉高度被二次 dp 缩放

```csharp
inner.AddView(BuildHero(UI.Dp(430)));        // 传进去已是 px
...
FrameLayout BuildHero(int fixedH) {
    ... new LinearLayout.LayoutParams(MatchParent, UI.Dp(fixedH));   // 又乘一次
```

3x 密度屏上：430 × 3 × 3 = **3870px**。主视觉把整个首页顶出屏幕，
下面的按钮、名录、底部导航全部看不见。横屏的 `BuildHero(UI.Dp(400))` 同理。

**已修复**：调用方改传 dp 原值。

### 🟠 Bug 4 — `FeaturedCharacter()` 可能返回 null 或抛异常

```csharp
if (owned.Count > 0) return owned.OrderByDescending(c => c.Rarity).First().Def!;
return GameState.Service.Characters.First();
```

- `.Def!` 只是压制编译器警告，**运行时真的可能是 null**（存档里的角色在当前内容表查不到时），
  下游 `def.BaseRarity` 直接 NRE。Bug 2 让内容表退化成 8 人，正好制造这个条件。
- `Characters.First()` 在内容表为空时抛 `InvalidOperationException`。

**已修复**：过滤掉 `Def == null`，改用 `FirstOrDefault`，并加占位角色兜底。

### 🟠 Bug 5 — 错误对话框自身失败会吃掉真正的错误

`OnCreate` 的 catch 里 `RunOnUiThread(...)` 中调用 `AlertDialog.Show()`。
这个 lambda 里再抛异常（BadTokenException 等）同样是未捕获 → 进程静默死亡，
**真正的错误反而被掩盖**。

**已修复**：整个 lambda 包 try/catch。

### 🟡 Bug 6 — 生命周期回调无保护

`OnResume` 和 `OnConfigurationChanged` 都没有 try/catch。
`OnResume` 在 `OnCreate` 的 try **之外**执行，这里抛异常同样表现为「启动后立刻闪退无对话框」；
`OnConfigurationChanged` 则是「一转屏就闪退」。

**已修复**：均加保护。

### 🟡 Bug 7 — 其余 9 个 Activity 的 OnCreate 全都没有 try/catch

只有 `HomeActivity` 有。其他任意一个出错都是静默闪退。
现已由全局崩溃处理器兜底记录（见下）。

---

## 三、新增的取证机制（关键）

设备不在构建机上、`adb devices` 为空，抓不到 logcat。
所以改成**让 App 自己把现场留在磁盘上**：`Infrastructure/CrashReporter.cs`

1. **`last_crash.txt`** — 挂载 `AndroidEnvironment.UnhandledExceptionRaiser`、
   `AppDomain.UnhandledException`、`TaskScheduler.UnobservedTaskException`，
   记录完整托管堆栈 + Java 侧 Throwable 堆栈 + 机型/系统/ABI。
2. **`boot_trace.txt`** — 启动阶段面包屑，逐步追加：
   `app.oncreate.begin → app.ui.init.ok → home.oncreate.begin → home.gamestate.init
   → home.buildlayout → home.oncreate.done`
3. **下次启动自动回显** —— 弹窗展示上次崩溃现场，带「复制」按钮。

**这套机制能直接区分两类根因**（这正是之前缺的判据）：

| 现象 | 结论 |
|---|---|
| 弹出崩溃报告，有托管堆栈 | 托管层异常，堆栈直接指出位置 |
| 弹「未捕获到托管异常，但上次启动未走完流程」+ 面包屑 | **native 层崩溃**（AOT / 运行时问题），面包屑停在哪就是崩在哪 |
| 什么都不弹 | 启动链路完整，问题不在启动期 |

---

## 四、结论

**最可能的根因是 Bug 1**（`HaloView` 的 `RadialGradient` 半径 ≤ 0，在 `OnDraw` 中抛出、
绕过所有 try/catch、静默杀死进程），叠加 Bug 2 造成的数据退化放大了 Bug 4 的触发概率。

但我不把它当作已证实的结论——**没有 logcat 就没有铁证**。
因此本次同时交付了取证机制：装上新 APK 后，
- 如果不再闪退 → 上述修复命中；
- 如果仍闪退 → 再启动一次，弹窗会直接给出堆栈或指明是 native 崩溃，届时可一击定位。

## 五、待办

- [ ] 装新 APK 验证，若仍闪退请把弹窗内容复制回来
- [ ] 给其余 9 个 Activity 的 OnCreate 加统一保护（建议抽 `BaseActivity`）
- [ ] 立绘 PNG 是 color type 2（无 alpha 通道），`ExtractAlpha` 做的剪影辉光实际是个矩形，视觉上无效
- [ ] 20 张立绘全缓存约 53MB，低端机有 OOM 风险，建议按需 `InSampleSize` 降采样
