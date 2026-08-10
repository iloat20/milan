# Milan 体验留存与现代化改造 · 设计规格

> 日期：2026-08-09 · 状态：草案（设计已完整呈现，用户确认项见 §8）

针对 Kotlin/Compose 版（`MilanKotlin/`）的体验升级：对标 GitHub 热门安卓游戏项目（compose-tetris / osu-droid / android/games-samples 等）的共有功能与新技术，落地为两个阶段：
**阶段 1A 视觉/动画**（BOM 升级 + Shared Element 立绘过渡 + 抽卡演出增强 + 页面过渡）
**阶段 1B 本地留存**（本地成就 + 每日签到 + 统计面板，纯领域层）
**阶段 2 现代化/性能**（M3 Expressive + 补测试 + Game Mode API + JankStats）

约束：**纯本地**（用户已确认暂不做云存档/Play Games Services/在线排行榜）；不引入图片库等新依赖（除 BOM 升级必要的版本更新）；不动领域层现有数值与序列化键名。

---

## 1. 现状问题（动机）

| # | 问题 | 位置 | 对标参照 |
|---|---|---|---|
| P1 | 角色列表 → 详情为生硬页面硬切，立绘「360° 检视」卖点无过渡展示 | `ui/nav/`（MilanNavHost when 分支） | Shared Element Transitions 已是主流卡牌游戏标配 |
| P2 | 抽卡 reveal 仅 Crossfade，缺仪式感与稀有度分级演出 | `ui/gacha/GachaScreen.kt` | 主流抽卡均有召唤门/缩放弹出 |
| P3 | 无任何留存机制：无成就、无签到、无统计 | 全项目 | 调研的 7 个热门项目 5 个有成就/统计；Steam 数据：成就用户留存 +50%、时长 2.1× |
| P4 | 子页切换无过渡动画（硬切） | `ui/nav/` | compose-tetris 等均有页面过渡 |
| P5 | 技术债：Compose BOM 2025.05.00（1.8.x），无法使用 1.10+ 的 Shared Element 与可视化调试；UI 层零测试 | `gradle/libs.versions.toml`、`app/src/test/` | 现代化技术栈 |

**已验证事实**：MilanNavHost 是纯状态路由（`when` 分支 + `rememberSaveable`），非 Navigation Compose——**迁移 Navigation 3 无实际收益，不走**；立绘已有多档采样（Thumb/Full）+ LruCache 基础设施（上轮性能优化成果），动画增强必须沿用不重做。

---

## 2. 决策记录

| # | 决策 | 内容 | 状态 |
|---|---|---|---|
| D1 | BOM 升级 | Compose BOM `2025.05.00` → `2026.04.01`（Compose 1.11.0 stable，Shared Element 稳定 + 可视化调试） | ⏳ 待实施 |
| D2 | 导航方案 | **否决 Navigation 3 迁移**（纯状态路由够用），保留 MilanNavHost，外包 `SharedTransitionLayout` + 子页切换包 `AnimatedContent` | ✅ 已定 |
| D3 | Shared Element | 入口：`ListCard` 缩略立绘 ↔ 出口：`CharacterDetailScreen.HeroRegion` 全屏立绘；key 用 `characterId`。ContentScale 不自动动画 → 用 `sharedBounds` 实现（含缩放） | ⏳ 待实施 |
| D4 | 页面过渡 | 所有子页进出场 fade + 轻微 slide（顺带收益，替换硬切） | ⏳ 待实施 |
| D5 | 抽卡演出 | 结果卡从召唤门中心缩放弹出 + 光效扫过；稀有度越高出场延迟越长（SSR/UR 吊胃口）；**AGSL 召唤门背景特效为可选项**——BOM 升级成功后做，遇阻即砍 | ⏳ 待实施 |
| D6 | 成就架构 | `AchievementDef` + `AchievementTracker` 放 `domain/achievements/`（纯 Kotlin）；**事件经 GameService 转发**（业务完成 → tracker.record → 落盘 + publish `AchievementUnlocked`），domain 不反向依赖 infrastructure 的 EventBus | ⏳ 待实施 |
| D7 | 成就触发源 | 抽卡累计次数 / 获得 UR / 获得 SSR / 角色升级 / 角色突破 / 战斗胜利 / 图鉴收集数 / 签到天数 | ⏳ 待用户确认清单 |
| D8 | 每日签到 | 存档 `dailyLogin{lastClaimDate, streak}`（默认值兼容旧档）；奖励公式新增 `EconomyFormulas.DailyLoginReward(day)` —— **数值单一事实来源铁律** | ⏳ 待实施 |
| D9 | M3 Expressive | material3 → 1.5.0-alpha（OptIn `ExperimentalMaterial3ExpressiveApi`），**只替换系统组件层**（列表项/FilterChip/顶栏），自定义组件（GlassPanel/NeonButton）不动 | ⏳ 待实施 |
| D10 | Game Mode | `android.app.GameManager`（API 33+ framework，零依赖）：PERFORMANCE → 特效全开；BATTERY → 立绘降档（Thumb）+ 关 AGSL 特效。天然挂钩现有采样分档 | ⏳ 待实施 |
| D11 | 测试 | 新领域逻辑必配单测（JUnit4 + 注入 seed 确定性，遵循项目传统）；UI 层引入 Compose UI 测试（先覆盖导航路由 + 成就页） | ⏳ 待实施 |
| D12 | JankStats | 性能埋点验证 1A 动画流畅度（建议做，成本低） | ⏳ 可选 |
| D13 | 不做 | 云存档 / Play Games Services / 在线排行榜 / 社交分享（用户确认纯本地）；不引入图片库；不动序列化键名；不改领域现有数值 | ✅ 已定 |

---

## 3. 架构分层（改动落点）

| 改动 | 文件 | 层 |
|---|---|---|
| BOM / Kotlin 版本升级 | `gradle/libs.versions.toml`、`gradle.properties` | 构建 |
| SharedTransitionLayout + AnimatedContent 包裹路由 | `ui/MilanApp.kt` 或 `MainActivity.kt`（MilanNavHost 所在处） | UI |
| 列表项立绘 sharedBounds | `ui/character/CharacterListScreen.kt`（ListCard） | UI |
| 详情 HeroRegion 立绘 sharedBounds | `ui/character/CharacterDetailScreen.kt` | UI |
| 抽卡演出增强（缩放弹出 + 光效 + AGSL 可选） | `ui/gacha/GachaScreen.kt` | UI |
| 成就定义与判定（纯 Kotlin） | 新 `domain/achievements/AchievementDef.kt`、`AchievementTracker.kt` | 领域 |
| 成就事件转发 + 落盘 | `services/GameService.kt` | 服务 |
| 成就存档模型 | `data/`（`@Serializable`，新字段带默认值） | 数据 |
| 每日签到奖励公式 | `domain/progression/EconomyFormulas.kt`（**新增函数，不改现有函数**） | 领域 |
| 签到状态 + 领取逻辑 | `services/GameService.kt`、`data/` 存档 | 服务/数据 |
| 成就面板 / 签到卡片 / 统计 UI | `ui/`（新子页 + Home 入口卡片） | UI |
| 成就/签到/公式单测 | 新 `app/src/test/java/com/milan/game/domain/achievements/AchievementTrackerTest.kt` 等 | 测试 |
| Game Mode 监听与 UI 降级 | `ui/GameState.kt` 或新 `ui/GameModeController.kt` | UI |
| JankStats 埋点（可选） | 新 `infrastructure/JankStats.kt` 或 UI 层 | 基础设施 |

**约束（红线重申）**：
- `domain/`、`data/` 禁止 `import android.*` —— 成就/签到逻辑纯 Kotlin，Android 依赖只进接入层。
- `EconomyFormulas` 是数值单一事实来源：签到奖励、成就解锁奖励一律调用它，禁止就地写数字。
- **不动现有 `@Serializable` 键名**；新增字段全部带默认值（旧档无损加载，kotlinx.serialization 向后兼容）。
- EventBus：publish 只入队需宿主 dispatch；订阅传 `owner` 便于 `unsubscribeAll`。
- 货币/养成写操作保持事务范式（预算 → 改内存 → 落盘，失败回滚不广播事件）；成就/签到写操作沿用同一范式。

---

## 4. 详细设计

### 4.1 阶段 1A —— BOM 升级（D1）

- `libs.versions.toml`：`composeBom = "2026.04.01"`（Compose 1.11.0 stable）。
- **兼容性验证**（实施第一步，卡点）：Compose 1.11 编译器与 Kotlin 2.1.20 的版本匹配——Kotlin 编译器插件随 Kotlin 版本绑定，若 1.11 要求更高 Kotlin，同步升级 Kotlin（连带核对 AGP 8.10.1）。
- 升级后回归：5 tab + 全部子页手工过一遍；`:app:testDebugUnitTest` 全绿。

### 4.2 阶段 1A —— Shared Element 立绘过渡（D2/D3/D4）

**结构改造**（MilanNavHost 所在文件）：

```
SharedTransitionLayout {                       // 新外层
    AnimatedContent(targetState = route) {     // when 分支包进来，保留 rememberSaveable 路由状态
        when (route) { ... }                   // 现有分支不变
    }
}
```

**入口 → 出口配对**（key = characterId）：
- 入口：`CharacterListScreen` ListCard 缩略立绘 → `Modifier.sharedBounds(rememberSharedContentState(key = "portrait_${characterId}"), animatedVisibilityScope)`
- 出口：`CharacterDetailScreen` HeroRegion 全屏立绘 → 相同 key 的 `sharedBounds`
- 用 `sharedBounds` 而非 `sharedElement` 的原因：**ContentScale 不参与 sharedElement 动画**，`sharedBounds` 允许子内容独立缩放渲染，满足「缩略 → 全屏」的尺寸/裁切过渡。
- 返回时天然反向：立绘从全屏收缩归位到卡片（配合 D4 页面 fade 退出）。

**预测性返回（可选）**：子页 onBack 接入系统预测性返回（targetSdk 36 默认启用），返回动画与 Shared Element 反向过渡闭环。

**已知限制**（Compose 官方）：Dialog / ModalBottomSheet / popup 不参与 Shared Element；列表项需在组合树中稳定（LazyColumn 滚动离开视口会中断过渡——本场景列表短、风险低，标注即可）。

### 4.3 阶段 1A —— 抽卡演出增强（D5）

- 现状：reveal 阶段 Crossfade（Full 档立绘）。
- 增强：结果卡从召唤门中心**缩放弹出**（scale 0.4→1.0 + 旋转 2°→0）+ 光效扫过（Brush 渐变扫过条，`Animatable` 驱动）；稀有度 R<SR<SSR<UR 逐级加长出场延迟（R 0ms / SR 200ms / SSR 450ms / UR 700ms）。
- **AGSL portal（可选）**：`RenderEffect` 着色器做召唤门粒子背景（Compose 1.11 支持）。**降级开关**：BOM 升级失败或 Game Mode = BATTERY 时整体关闭。
- 性能红线：沿用现有采样档位（列表/池预览 Thumb、reveal Full），动画不改变图片加载档位；动画用 `rememberInfiniteTransition`/`Animatable` 组合而非逐帧重绘。

### 4.4 阶段 1B —— 本地成就系统（D6/D7）

**领域层**（纯 Kotlin，无 android.*）：

```kotlin
// domain/achievements/AchievementDef.kt
enum class AchievementRarity { BRONZE, SILVER, GOLD }

/** 成就定义：id 稳定（未来可平移到 Play Games 成就 ID），progress 由 tracker 维护。 */
data class AchievementDef(
    val id: String,               // 例 "gacha_total_100"
    val title: String,            // 中文名
    val description: String,
    val rarity: AchievementRarity,
    val condition: AchievementCondition,   // 见下
)

sealed interface AchievementCondition {
    data class Count(val event: AchievementEvent, val target: Int) : AchievementCondition
    data class CollectCharacters(val minCount: Int, val minRarity: Int? = null) : AchievementCondition
    data class DailyLogin(val day: Int) : AchievementCondition
}
```

```kotlin
// domain/achievements/AchievementTracker.kt
/**
 * 成就判定器：纯函数式累加，无 Android/IO 依赖。
 * 事件由 GameService 在业务完成后转发（domain 不反向依赖 EventBus）。
 * 入参用纯集合而非 data 层存档模型——domain 自包含，存档映射由 GameService 完成。
 */
class AchievementTracker(defs: List<AchievementDef>) {
    /** @param progress  各成就累计进度（id → 计数）
     *  @param unlocked  已解锁成就 id 集合
     *  @return 新解锁 id 列表 + 更新后的进度（由调用方落盘） */
    fun record(
        event: AchievementEvent,
        progress: Map<String, Int>,
        unlocked: Set<String>,
    ): RecordResult   // data class RecordResult(newlyUnlocked: List<String>, updatedProgress: Map<String, Int>)
}
```

**事件转发流**（GameService 负责，事务范式）：
1. 业务完成（如抽卡）→ 计算奖励、落盘成功后
2. `tracker.record(GachaPulled(totalCount), save.achievements)` → 返回新解锁列表
3. 有解锁 → 更新 `save.achievements`（解锁 id + progress）+ 落盘 → `publish(AchievementUnlocked(ids))`
4. **落盘失败回滚本次内存改动、不广播事件**（与现有 Spend/Add 范式一致）

**UI**：成就面板子页（Settings 入口）——分类列表（铜/银/金）、进度条（progress/target）、已解锁高亮；解锁横幅：顶部滑入 + 排队显示（`AnimatedVisibility` + 队列，可触觉反馈复用现有 Haptic）。

### 4.5 阶段 1B —— 每日签到（D8）

- 存档：`dailyLogin: { lastClaimDate: String? = null, streak: Int = 0 }`（`@Serializable` 带默认值，旧档兼容）。
- 公式（唯一数值来源）：

```kotlin
// domain/progression/EconomyFormulas.kt 新增（不改现有函数）
/** 每日签到奖励：第 day 天发放的货币量（连续签到阶梯）。 */
fun DailyLoginReward(day: Int): Int
```

- 领取逻辑（`GameService.claimDailyLogin()`）：校验 `lastClaimDate != today` → 可领；连续（昨天领过）→ `streak+1`，否则重置 `streak=1`；奖励走公式 + 现有货币事务范式；落盘失败回滚。
- **单机防作弊**：记录 `lastClaimEpochMillis`，跨设备改时间只影响本地——接受（纯本地游戏，明确不做云端校验）。
- **UI**：Home 页签到入口卡片（今天已领/可领状态、连续天数）；领取弹窗动画。

### 4.6 阶段 1B —— 统计面板（并入成就页）

- 只读展示，数据全部来自存档：抽卡总次数、UR/SSR 出货数、战斗胜率、图鉴完成度（已收集/总数）。
- 成就页顶部数据行，不新增页面。

### 4.7 阶段 2 —— M3 Expressive（D9）

- `material3` → `1.5.0-alpha`（需 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`）。
- 渐进替换系统组件层：Expressive 列表项（成就列表/角色列表）、FilterChip shape morphing（筛选 chip）、FlexibleTopAppBar（子页顶栏）。
- **自定义组件（GlassPanel/NeonButton/主题色）保持不动**——视觉基调不变，只升级系统交互组件。
- 风险：alpha 稳定性 → 放最后阶段、小步替换、每次替换跑回归。

### 4.8 阶段 2 —— 补测试（D11）

| 层级 | 新增测试 | 覆盖 |
|---|---|---|
| 领域 | `AchievementTrackerTest`、签到公式扩展 `EconomyFormulasTest` | 解锁判定、进度累加、边界（target 达成/超额）、签到阶梯 |
| 服务 | `GameServiceTest` 扩展 | 成就事件转发、签到领取事务（落盘失败回滚不广播） |
| UI（现状空白） | Compose UI 测试（`ui/` 首批） | 导航路由（5 tab + 子页进出）、成就页渲染/空态/解锁态 |

### 4.9 阶段 2 —— Game Mode API（D10）

- `android.app.GameManager`（API 33+，framework 零依赖）：`getGameMode()` + `GameStateListener` 监听玩家切换。
- 映射：`GAME_MODE_PERFORMANCE` → 特效全开；`GAME_MODE_BATTERY` → PortraitImage 降档 Thumb（沿用现有档位）+ 关闭 AGSL 特效；`UNSUPPORTED` → 默认。
- UI 状态经 `GameState` 暴露（进程级单例，与现有模式一致），`ui/` 消费。

### 4.10 阶段 2 —— JankStats（D12，可选）

- 埋点帧时长，验证 1A 动画在低端机不跳帧；数据落 CrashReporter 同款 trace 日志（不新增上报通道）。

---

## 5. 错误处理与边界

| 场景 | 行为 |
|---|---|
| BOM 升级后 Kotlin 编译失败 | 同步升级 Kotlin 版本（验证 AGP 兼容）；仍失败 → 回退 BOM 并砍 AGSL 特效（D5 降级路径） |
| Shared Element key 冲突 / 入口出口不配对 | 过渡退化为普通 AnimatedContent fade（SharedTransitionLayout 安全降级，不崩） |
| 列表项滚动离开视口中断过渡 | 过渡中断为正常动画结束（Compose 官方行为，本场景列表短风险低） |
| 成就落盘失败 | 回滚进度、不广播解锁（事务范式） |
| 重复领取签到 / 改系统时间 | lastClaimEpochMillis 校验；纯本地接受作弊（明确不做云端） |
| 旧存档加载 | 新字段默认值 → 成就空列表、签到未领、streak 0，无损 |
| AGSL 特效在低端机/省电模式 | Game Mode 降级关闭；构建失败关闭 |
| M3 alpha 组件行为异常 | 单组件回退到旧组件（小步替换保证可回退） |

---

## 6. 测试与验证

**单测**（JUnit4 惯例，确定性——领域引擎注入 seed，参照现有测试）：
- `AchievementTrackerTest`：事件序列驱动解锁；进度不越界；已解锁不重复触发；Collect/DailyLogin 条件。
- `EconomyFormulasTest` 扩展：`DailyLoginReward` 阶梯边界。
- `GameServiceTest` 扩展：签到领取（正常/已领/连续/重置）；成就落盘失败 → 回滚不广播。

**构建**：`:app:assembleDebug` + `:app:testDebugUnitTest` 全绿。

**真机验证**（设备上线后）：
1. 列表 → 详情：立绘放大过渡流畅无跳帧（`adb shell dumpsys gfxinfo` 检查 jank）。
2. 抽卡：SSR/UR 出场延迟节奏正确；BATTERY 模式特效关闭。
3. 签到：领/已领/连续天/改时间边界；重启后状态保持。
4. 成就：达成 → 横幅弹出；重启后解锁状态保持。
5. 旧存档升级：老档加载无异常，新字段默认值正确。

---

## 7. 明确不做（YAGNI）

- 不做云存档 / Play Games Services / 在线排行榜 / 成就云同步（用户已确认纯本地；成就 id 已预留未来平移能力）。
- 不迁移 Navigation 3（纯状态路由够用，D2）。
- 不引入 Coil/Glide 等图片库（延续零依赖传统）。
- 不动领域层现有数值与 `@Serializable` 键名。
- 不重做立绘加载（沿用上轮 PortraitLoader 采样/缓存成果）。
- 不做战斗系统新内容（成就触发源仅挂现有事件）。

---

## 8. 待用户确认项

| # | 项 | 默认建议 |
|---|---|---|
| C1 | 阶段划分（1A → 1B → 2） | 按序执行，1A/1B 可并行 |
| C2 | 成就触发源清单（D7：抽卡/UR/SSR/升级/突破/战斗/图鉴/签到） | 按清单实施，可增删 |
| C3 | AGSL 召唤门特效 | 保留为可选项（D5：BOM 成功后做，遇阻砍） |
| C4 | 统计面板并入成就页（§4.6） | 并入 |
| C5 | JankStats（D12） | 做 |
