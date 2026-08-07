# EventBus 接入：资源栏（星尘 / 钻石）事件驱动刷新

日期：2026-08-03
范围：把"项目内仍无业务接线（UI 走 OnResume 全量重建）"这一已记录问题，先用**资源栏**这一最小、零风险场景落地事件驱动，
顺带把"事件类型 + Dispatch 时机 + 生命周期订阅/退订"三件套约定定齐，后续其他场景照抄。

## 背景
- `Core/Infrastructure/EventBus/EventBus.cs` 已实现完整能力（Publish 入队、Dispatch 锁外派发、Subscribe/Unsubscribe 幂等、
  UnsubscribeAll(target) 按 Target 清、队列上限 512）。但全仓无任何 Publish/Subscribe 调用，是死代码。
- 现状刷新：`OnResume` 全量重建（Home/Gacha 重设 `_soft/_hard/_dust/_gems` 文本；CharacterList/Collection 重 build 网格）。
  单前台 Activity 模型下逻辑正确，但不够精细、且列表场景丢失滚动位置。

## 方案（A：领域层统一出口）
### ① 事件类型
- `Core/Infrastructure/EventBus/Events.cs`：`public readonly struct CurrencyChanged { }`（轻标记，无负载）。

### ② 领域层封装 = 唯一 Publish 点（GameService）
- 新增私有 `PublishCurrencyChanged()` = `EventBus.Publish(new CurrencyChanged()); EventBus.Dispatch();`
- 新增薄封装（供未来商店/奖励等单点改动复用）：
  - `SpendSoft(int)` / `AddSoft(int)` → 改 `SaveData.SoftCurrency` 后 `PublishCurrencyChanged()`
  - `SpendHard(int)` / `AddHard(int)` → 改 `SaveData.HardCurrency` 后 `PublishCurrencyChanged()`
- `Pull` 是事务：先 `SaveData.SoftCurrency -= cost`，再 `_save.Save()`；**只在落盘成功后** `PublishCurrencyChanged()`，
  回滚分支不广播，避免 UI 显示"已扣但存档未变"的陈旧值。

### ③ Activity 订阅 / 退订（HomeActivity + GachaActivity）
- 抽取 `RefreshCurrency()`：把 OnResume 里刷新文本的逻辑提炼成实例方法（带 null 检查）。
- `void OnCurrencyChanged(CurrencyChanged _) => RefreshCurrency();`（实例方法，Target=this，配合 `UnsubscribeAll(this)`）
- `OnResume`：`EventBus.Subscribe<CurrencyChanged>(OnCurrencyChanged); RefreshCurrency();`（先订阅再直刷 = 回页面兜底）
- `OnPause`：`EventBus.UnsubscribeAll(this);`（不可见即摘掉处理器防泄漏；幂等订阅 + 暂停退订保证同页至多一个有效订阅）
- Gacha 的 Pull 按钮处保留对 `_dust/_gems` 的即时直刷（与事件并行，幂等无害），作为动作点兜底。

### ④ 错误处理与验证
- `EventBus.Dispatch` 已对每个 handler try/catch，单点故障不传染；Home 端 `OnResume` 保留外层 try/catch。
- 构建：`--no-incremental` Release 仍须 0 错误 / 0 警告。
- 手动验证：① 抽卡后本页星尘立即递减；② 跳 Home → OnResume 兜底最新；③ 反复进出不重复刷新 / 不泄漏。

## 范围外（后续）
- CharacterList / Collection 列表增量刷新（保留滚动位置）
- GameNavBar 顶栏 chip 的事件化（当前随页重建取一次值，已正确）
