# 全面代码审查报告 — MauiMilan（.NET MAUI Android）

- **审查日期**：2026-08-03
- **审查范围**：全部 55 个 C# 源文件（Core 领域/服务、Infrastructure 存档/崩溃、UI 渲染与控件、Activities 各页面）
- **方法**：4 个并行只读审查代理（核心/存档/UI助手+页面/UI渲染）+ 针对着色器颜色类型与 OnDraw 守卫的定向 grep 复核；构建基线为 0 错误 / 0 警告（Release，无 AOT）
- **判定口径**：仅报告真实缺陷（空引用崩溃、逻辑错误、资源泄漏、并发、性能、边界）；明确的工程设计决策（`<Nullable>annotations</Nullable>`、武器仅 SSR/UR 闸门、武器立绘不叠加）不作为 bug 报告，但会标注其掩盖的**真实运行期风险**

---

## 一、严重程度统计

| 严重度 | 数量 | 说明 |
|---|---|---|
| 🔴 Critical | 2 | 必现时导致应用永久不可用 / 存档毁灭 |
| 🟠 High | 7 | 特定配置或交互下进程被杀、关键取证丢失、扣款无产出 |
| 🟡 Medium | 16 | 逻辑错误、泄漏、并发隐患、性能抖动 |
| 🟢 Low | 12 | 边界防护缺失、可优化项、死代码/拼写漂移 |

合计约 **37 项** 缺陷。

---

## 二、已确认合规（非问题，避免误报）

- **着色器/渐变颜色类型**：全部 `RadialGradient/LinearGradient/SweepGradient` 均走 `UI.ColorLong / UI.ColorLongs`（返回 `long` 的重载），无 `int`/`Color` 误传入 `long` 参数 → 无 `IllegalArgumentException: Invalid ID` 风险。`VfxRenderer.cs:1658` 一处用 `int[]`（`Color.Argb().ToArgb()`），命中 `int[]` 重载，合法。
- **OnDraw 守卫**：多数自定义 View 已加 `if (w==0||h==0) return;`；`RadialGradient` 调用点普遍有 `if(radius<=0) return;`。
- **UI 线程阻塞**：全仓无 `.GetResult()/.Result/.Wait()` 同步阻塞 async。
- **集合首元素**：全仓无裸 `First()`/`Single()`，均 `FirstOrDefault()` + null 检查。
- **JsonSerializer**：`GameService.ContentJsonOptions` 与 `SaveData.JsonOptions` 均含 `IncludeFields=true`。

---

## 三、优先级排序结果（Critical → Low）

| # | 严重度 | 文件 : 位置 | 类别 | 简述 |
|---|---|---|---|---|
| 1 | 🔴 Critical | Infrastructure/Save/SaveData.cs : FromJson | null | 存档 JSON 集合字段显式为 null → 反序列化覆盖初始化器 → 启动即 NRE、应用永久不可用 |
| 2 | 🔴 Critical | Infrastructure/Save/SaveManager.cs : Load | logic | 读取失败静默回退默认档并随后覆盖真档（.bak 从不读）→ 瞬时 IO 错误即永久清档 |
| 3 | 🟠 High | Infrastructure/CrashReporter.cs : Write | logic | Java 堆栈 `"..." + StackTraceElement[]` 拼接 → 落盘仅类型名，关键线索 100% 丢失（0 warning 编译通过） |
| 4 | 🟠 High | Infrastructure/CrashReporter.cs : ReadAndClear | logic | 展示前已删内部文件+外部镜像，StartActivity 失败被吞 → 最关键的崩溃现场蒸发 |
| 5 | 🟠 High | Infrastructure/Save/SaveManager.cs : Save / LocalSaveProvider.cs : Save | exception | 写盘链路零 try/catch，IOException 冒泡到抽卡 Click 处理器 → 扣款后闪退、未落盘 |
| 6 | 🟠 High | Services/GameService.cs : 84-85 Initialize | null | data.json 卡池 `RarityWeights` 为 null/长度<4 漏校验 → 进抽卡页/点抽即 NRE 杀进程 |
| 7 | 🟠 High | Services/GameService.cs : 85 Initialize | null | 卡池 `Entries` 含 null 元素漏校验 → Pull 时 NRE 闪退 |
| 8 | 🟠 High | Services/GameService.cs : 630-671 Pull | logic | RarityIndex 越界致 0 产出但仍扣款并落盘 → 玩家花钱一无所得 |
| 9 | 🟠 High | Activities/GachaActivity.cs : 327 ShowCharacterReveal | logic | `_revealContainer.Click +=` 每次演出叠加，从不移除 → 连抽 N 次后一次点击 StartActivity N 个详情页 |
| 10 | 🟡 Medium | UI/WeaponFxView.cs : 64-70 / CharacterDetailActivity.cs : 329-335 WeaponPreviewView.Tick | logic | 动画只在 OnAttached 调一次 Tick，Draw 内不再排帧 → `_phase` 恒为 0.045，武器光效完全静止 |
| 11 | 🟡 Medium | Services/GameService.cs : 654/665 Pull | logic | 展示用 `def.BaseRarity` 与补偿用 `FragmentsForRarity(effectiveRarity)` 口径不一 → 碎片多发/少发 |
| 12 | 🟡 Medium | Core/Domain/Gacha/PityCounter.cs : 19 RollWithPity | boundary | `Threshold<=0`（HardPity 可配 0）→ 每抽必触发保底，卡池变 100% SSR、UR 永远抽不到 |
| 13 | 🟡 Medium | Core/Domain/Inspection/VisualLayerComposer.cs : 23 IsFullTree | logic | `totalNodes==0` 判为满树（28 棵空树触发）→ 无天赋角色被误判满树解锁 VFX |
| 14 | 🟡 Medium | Core/Domain/Progression/TalentEngine.cs : 11 CanAllocate | logic+null | 根节点无前置 → `TryGetValue` 失败直接 return false → 整棵树锁死无法点天赋；value 为 null 时 NRE |
| 15 | 🟡 Medium | Core/Domain/Battle/BattleSimulator.cs : 35 Simulate | boundary | 空 teamB 排列在 a 之前 → `b.All(x=>x.Hp<=0)` 对空序列返回 true → 空队伍白拿胜利 |
| 16 | 🟡 Medium | Core/Infrastructure/EventBus/EventBus.cs : 8-9,24 | concurrency | 静态 Dictionary/Queue 无锁，并发写可成环死循环（100% CPU 卡死，无堆栈） |
| 17 | 🟡 Medium | Core/Infrastructure/EventBus/EventBus.cs : 24-25 | resource-leak | 全仓无 `Dispatch()` 调用，事件只入不出 → 静态队列无限增长 + handler 永不触发 |
| 18 | 🟡 Medium | UI/GameState.cs : 18,23-31 EnsureInitialized | concurrency | `_initialized` 非 volatile + 锁外读 → ARM64 弱内存序下读到半初始化集合 |
| 19 | 🟡 Medium | Services/GameService.cs : 14,618 等 Pull/Initialize | concurrency | 进程级单例无同步，`System.Random` 非线程安全 → 并发 Pull 双花/漏扣/损坏存档 |
| 20 | 🟡 Medium | Infrastructure/CrashReporter.cs : Boot | performance | 每条启动面包屑同步 ReadAllText 全量 trace + 镜像到外部存储 → 冷启动 O(n²) IO 放大、ANR 风险 |
| 21 | 🟡 Medium | Infrastructure/CrashReporter.cs : BeginBootTrace/Boot/Write | boundary | trace 文件跨启动无限增长无轮转 → 放大 IO 且超大 report 经 Binder 抛 TransactionTooLarge |
| 22 | 🟡 Medium | Infrastructure/CrashReporter.cs : Write/TryShowDialog | exception | Write 的 catch 不再落盘；主线程崩溃时对话框 Runnable 永不执行 → 崩溃无痕迹 |
| 23 | 🟡 Medium | Infrastructure/Save/LocalSaveProvider.cs : Save | boundary | temp-then-rename 无 fsync，断电可得空/短文件；.tmp/.bak 从不清理与校验 |
| 24 | 🟡 Medium | Activities/HomeActivity.cs : 308-313 BuildHero | resource-leak | Infinite ObjectAnimator 不 Cancel → 每次旋转/重建泄漏一棵树 + 永久动画，阻止 Activity GC |
| 25 | 🟡 Medium | Activities/HomeActivity.cs : 498-511 OnNav（及 4 个同类 Activity） | resource-leak | 导航未用 ClearTop/SingleTop → 任务栈无上限增长，返回键需按 N 次；叠加 #24 内存线性涨 |
| 26 | 🟡 Medium | Activities/BattleActivity.cs : 32-43 OnConfigurationChanged→Build | logic | 旋转屏幕时 `_enemyHp=100`、重随元素、清手牌 → 战斗中旋转直接重置战局 |
| 27 | 🟡 Medium | Activities/BattleActivity.cs : 42-43 Build | logic | 元素串 `"Aqua"/"Volt"/"Terra"` 不在 ElementTheme 合法键 → 75% 战斗元素名与配色不符 |
| 28 | 🟡 Medium | UI/UIHelper.cs : 307-330 TapFeedback | logic | `MotionEventActions.Up` 无条件 `onTap()`，手指移出控件仍触发 → 误跳转 |
| 29 | 🟡 Medium | Activities/CharacterDetailActivity.cs : 276-281 WeaponPreviewView | logic | 同 #10（动画静止） |
| 30 | 🟡 Medium | Services/GameService.cs : 87 Initialize | logic | TalentTrees 过滤只校验 `t!=null` 不看 `Nodes` → 随包 data.json 28 棵树静默变成空树 |
| 31 | 🟡 Medium | Services/GameService.cs : 82 vs 392 EnrichCharacters | logic | `EnrichCharacters()` 仅在 LoadFallback 路径调用，JSON 路径不 enrich → 两路径数据不一致 |
| 32 | 🟢 Low | Services/GameService.cs : 670 Pull → SaveManager.Save | performance | 每次抽卡主线程同步写盘（原子替换 + rename）→ 低端机卡顿/ANR 前兆 |
| 33 | 🟢 Low | Core/Domain/Progression/ProgressionEngine.cs : 21 StatAtLevel | boundary | `stage<=0` 未防护 → 全属性归零/负 HP；stage 既作裸乘又作 stageMultiplier 重复 |
| 34 | 🟢 Low | Core/Domain/Gacha/GachaEngine.cs : 33 PickWeighted | null | 只判 `ids==null` 漏判 `weights==null` → NRE（当前调用方不触发，潜在风险） |
| 35 | 🟢 Low | Services/GameService.cs : 97-100 Initialize catch | logic | data.json 解析失败仅 Debug.WriteLine，Release 下被裁 → 误报为"文件不存在" |
| 36 | 🟢 Low | Core/Infrastructure/EventBus/EventBus.cs : 13 Subscribe | logic | 同 handler 重复订阅不幂等 → 事件触发 N 次（发奖类会 N 倍发放） |
| 37 | 🟢 Low | UI/AnimatedPortrait.cs : 59/76/83/90、CharacterPortrait.cs : 66、FullBodyCharacter.cs : 90 | performance | 无节流 `Invalidate()` + 每帧 `new Paint()/GradientDrawable` → 十连同屏 10 视图 GC 抖动掉帧 |
| 38 | 🟢 Low | UI/RiftPortal.cs : 24/33/68 | performance | 0 尺寸分支满速空转；每帧 `new RadialGradient` + `new Path` 未复用 |
| 39 | 🟢 Low | UI/VfxRenderer.cs : 1761-1785 与 PortraitView.cs : 66-71 | resource-leak | 武器图 static 缓存无 LRU/无 TrimMemory 释放；ClearCache 的 Recycle 可能作用于正在绘制的 Bitmap |
| 40 | 🟢 Low | Infrastructure/Save/SaveManager.cs : Save/Current | null | 未 Load 直接 Save → `Current.ToJson()` NRE（当前不可达，潜伏陷阱） |
| 41 | 🟢 Low | Activities/CrashActivity.cs : 33-45 | logic | TextView 既 ScrollMovementMethod 又套 ScrollView → 长报告无法滚动查看 |
| 42 | 🟢 Low | Core/Domain/Progression/TalentEngine.cs : 15 TotalPoints | logic | 方法名暗示"已分配点数"实现却对所有节点 cost 求和 → 语义误导调用方 |
| 43 | 🟢 Low | MauiMilan.csproj : 39 AndroidAsset Link | logic | Link 前缀 `Assets\` 使资源实际落在 `assets/Assets/data.json`，GameService 首个候选恒定失败（注释与行为矛盾） |
| 44 | 🟢 Low | Activities/GachaActivity.cs : 481-489 HideReveal | logic | 淡出(300ms)与 Clickable=false(350ms)窗口间全屏层仍吞点击 |

---

## 四、重点缺陷详解（含修复建议）

### 🔴#1 存档反序列化后未做 null 净化（SaveData.FromJson）
- **触发**：存档 JSON 合法但含 `"OwnedCharacters":null`/`"GachaCounters":null`（外部编辑、截断补齐、异构 schema、云回写）。
- **表现**：`JsonSerializer` 不抛异常，但显式 null 覆盖字段初始化器 `= new()` → 得到 null 集合。`HomeActivity`/`CharacterListActivity`/`GachaActivity` 对其 `foreach`/`Find` → NRE；坏档常驻磁盘，每次启动必复现 → 应用永久不可用。
- **对照**：`GameService.Initialize` 对 data.json 明确做了"显式 null 覆盖初始化值"防护，存档侧却缺失，防护不对称。
- **修复**：`FromJson` 反序列化成功后 Sanitize：`d.OwnedCharacters ??= new(); d.OwnedSkins ??= new(); d.Items ??= new(); d.GachaCounters ??= new(); d.UserId ??= "";` 并 `RemoveAll(x => x == null || x.CharacterId == null)`。

### 🔴#2 读取失败静默回退并抹掉真档（SaveManager.Load）
- **触发**：存档损坏/截断/为空/瞬时 IO 错误（文件被占用、权限抖动）。
- **表现**：双层 catch 把"读失败"与"没档"混为一谈，一律 `CreateDefault()`（SoftCurrency=999999、角色全无）。随后第一次 `_save.Save()` 把这个默认档写正式档，`File.Replace` 把真档挤进 .bak；再存一次 .bak 也被覆盖 → **两代内原始存档彻底消失**。而 .bak 生成了却从不被读。
- **修复**：区分"文件不存在"（正常新号）与"存在但失败"（异常）；后者依次尝试 `.bak`、`.tmp`，全失败才回退默认并 `CrashReporter.Boot("save.load.fallback ...")` 留痕；回退状态下应先提示再允许覆盖写。

### 🟠#3 Java 堆栈取证失效（CrashReporter.Write）
- **触发**：任意 Java 侧异常（Inflate 失败、BadTokenException、JNI Throwable）。
- **表现**：`sb.AppendLine("Java 堆栈:\n" + jt.GetStackTrace())`，`GetStackTrace()` 返回 `StackTraceElement[]`，`string + 数组` 走 object 拼接调 `ToString()` → 落盘字面量 `Java.Lang.StackTraceElement[]`，**最关键线索 100% 丢失**，且 0 warning 编译通过、肉眼像是对的。
- **修复**：`Java.Lang.Throwable.GetStackTraceString(jt)`，或 `string.Join("\n", (jt.GetStackTrace() ?? []).Select(x => "  at " + x))`。

### 🟠#5/#8 抽卡扣款与写盘无事务保护（GameService.Pull / SaveManager.Save）
- **触发**：① RarityIndex 越界（data.json 手滑）→ 0 产出；② 磁盘满/文件被占用 → Save 抛 IOException。
- **表现**：`SaveData.SoftCurrency -= cost` 在循环前执行（#8 路径），10 连 1600 星尘被扣、0 产出、仍 `Save()` 落盘；或 Save 抛异常沿 Click 处理器冒泡 → 扣款后闪退、重进什么都没有，`_busy` 永久卡 true。
- **修复**：扣款移到"确认至少产出 1 个结果"之后；`SaveManager.Save` 包 try/catch 返回 bool，失败回滚本次扣款/发货并 `CrashReporter.Write`；或先算结果再统一扣款与发货。

### 🟡#10/#29 武器特效动画静止（WeaponFxView / WeaponPreviewView）
- **触发**：任何武器演出/详情页武器面板。
- **表现**：`Tick()` 仅 `OnAttachedToWindow` 调一次，内部 `_phase += 0.045f` 后 `PostInvalidateDelayed(33)`，但 **`Draw()` 内不再调用 Tick** → 动画只跑一帧，`_phase` 恒 0.045，背景光效完全静止（对比 PortraitView/CosmicBackground 在绘制末尾重新排帧）。
- **修复**：在 `Draw()/OnDraw()` 末尾加 `if(_running){ _phase += 0.045f; if(_phase>MathF.PI*2) _phase-=MathF.PI*2; PostInvalidateDelayed(33); }`，`OnAttached` 仅置 `_running=true; Invalidate();`。

### 🟡#16/#17 EventBus 死代码 + 并发无锁
- **触发**：任意线程并发 Subscribe/Unsubscribe/Publish；或开始使用 Publish。
- **表现**：静态 `Dictionary`/`Queue` 全程无锁 → 并发写可令桶链成环，后续读在 `Dictionary` 内部死循环（100% CPU、无堆栈、最难排查）；且全仓无 `Dispatch()` 调用，事件只入不出 → 队列无限增长、handler 永不触发（"发了但界面没反应"）。
- **修复**：用 `lock(_gate)` 包住所有读写（Dispatch 时锁内取快照、锁外调 handler）；在主循环固定调用 `Dispatch()`；`Subscribe` 重复时 `Delegate.Remove` 去重；Activity `OnDestroy` 强制 `UnsubscribeAll(this)`。

### 🟡#24/#25 导航与首页动画泄漏
- **触发**：底栏反复切换页面 / 横竖屏切换。
- **表现**：#24 `BuildHero` 的 Infinite `ObjectAnimator` 不 Cancel → 每次旋转/重建泄漏整棵视图树 + 永久动画，阻断 Activity GC；#25 五处 `OnNav` 未用 `ClearTop|SingleTop` → 任务栈无上限增长。叠加后内存线性上涨。
- **修复**：用字段保存动画并在 `OnConfigurationChanged`/`OnDestroy` 中 `Cancel()`；导航统一 `SetFlags(ClearTop|SingleTop)` + 主页面 `LaunchMode=SingleTop`；建议抽成 `AppChrome.Navigate(self, item)` 单点实现。

---

## 五、系统性风险总结

1. **"data.json / 存档是完全可信输入"的隐含假设**（最严重）：加载层只做了 3 个字段判空，下游抽卡/战斗/天赋直接索引这些数据，任何一次内容配置手滑都转化为 UI 线程未捕获 NRE（Click 处理器无 try/catch = 必闪退）。建议加载层做完整 schema 校验。
2. **"读强、写裸、回退静默"的防御不对称**：Load 多层 try/catch 但静默降级抹档；Save 完全裸奔；CrashReporter 取证链自身最脆弱（#3/#4/#20/#21）。建议给 Pull 加事务性（失败回滚扣款）、Save 加 try/catch、ReadAndClear 拆成"先展示后清除"。
3. **并发假设与现实不符**：`GameState.Service`、`EventBus._subs/_q`、`GameService._rng` 都是进程级共享可变状态，按"只有 UI 线程会碰"写，却无任何断言/线程亲和检查；`GameState.EnsureInitialized` 自己用了锁但标志位非 volatile（ARM64 不成立）。
4. **自定义动画视图缺乏统一基类**：至少 4 种排帧写法，#10/#37 本质是同一根因——未强制继承 `AnimatedEffectView`（已具备 30fps 节流 + detach/visibility 生命周期）。迁移过去可一次性消除并防复发。
5. **零单元测试**：`EventBus`/`TalentEngine`/`VisualLayerComposer`/`BattleSimulator` 五文件全仓零引用，缺陷（无 Dispatch 泵、根节点锁死、空树判满、空队伍判胜）目前不显形，一旦接线上 Medium 级逻辑错误会集中爆发。这批纯 .NET 引擎不依赖 Android，测试成本极低，应优先补单测。
6. **UI 线程磁盘 I/O 成惯例**：`Initialize` 启动期同步读资产（可接受），但 `Pull()` 每次抽卡同步写盘（#32）随存档膨胀逐步逼近 ANR 阈值。建议引入单线程写队列，计算在主线程、落盘在后台。

---

## 六、建议修复优先级

1. **立即（保命）**：#1 SaveData 反序列化 null 净化 → #2 Load 区分失败/无档并消费 .bak → #3 CrashReporter Java 堆栈 → #5/#8 Pull 事务性 + SaveManager.Save try/catch → #6/#7 data.json 卡池字段校验。
2. **高优（稳定）**：#9 Gacha Click 去重 → #10/#29 武器动画排帧 → #16/#17 EventBus 加锁+派发泵 → #18/#19 并发同步 → #24/#25 导航/动画泄漏。
3. **中优（正确）**：#11/#12/#13/#14/#15 抽卡/天赋/战斗逻辑边界 → #26/#27 BattleActivity 旋转重置与元素串 → #28 TapFeedback 边界 → #20/#21/#22 取证链健壮性。
4. **低优（打磨）**：剩余 Low 项与统一动画基类、补单元测试。
