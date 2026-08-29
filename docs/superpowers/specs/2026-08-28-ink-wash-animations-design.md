# 水墨动效设计规范 — Ink Wash Animation Spec

> **日期**：2026-08-28
> **状态**：Approved
> **范围**：Milan Kotlin/Compose Android — 前端页面水墨动效增强
> **方法**：分层递进（P1 转场 → P2 微交互 → P3 视差），每阶段独立可交付

---

## 背景

Milan 的水墨国风改造已完成色彩/主题/组件/立绘层面的全面替换。本次设计补齐**动效层**——让页面切换、按钮交互、列表滚动都呈现水墨画的自然韵律感。

**不包含**：书法字体升级（暂缓，等网络条件允许时单独处理）。

---

## P1 — 页面转场动画（墨汁泼洒）

### 目标

所有页面切换都有水墨画风格的过渡效果：新页面像墨汁在宣纸上泼洒展开，旧页面淡出如墨迹干涸。

### 实现位置

`ui/nav/InkTransitions.kt`（新建）+ `ui/nav/Routes.kt`（修改 NavHost transition 配置）。

### 转场类型

| 转场类型 | 进入动画 | 退出动画 | 时长 | 适用场景 |
|---|---|---|---|---|
| 子页进入（右→左） | 从右侧淡入 + 滑入 30dp + scale 0.97→1.0 | 向左淡出 + 滑出 30dp | 进 200ms / 出 150ms | 角色详情、养成页、神谱图鉴等子页 |
| Tab 切换 | 交叉淡入淡出 + scale 0.97→1.0 | 交叉淡出 | 180ms | 底部 5 个主 Tab（主页/抽卡/卡组/商店/设置） |
| 返回（左→右） | 向右淡入 | 向左淡出（墨迹干涸） | 进 150ms / 出 200ms | 系统返回 / Predictive Back |

### 视觉效果描述

- **进入**：新页面从 90% 不透明度淡入，同时轻微从右滑入（offset 30dp → 0），伴随 scale 0.97 → 1.0。整体感觉像墨汁在宣纸上缓缓铺开。
- **退出**：旧页面向左滑出 + 不透明度降到 0，感觉像墨迹被纸吸干。
- **子页 vs Tab**：子页有滑动方向感（右进左出），Tab 切换只有交叉淡入淡出（无方向感，因为 Tab 是平等切换）。

### API 设计

```kotlin
// InkTransitions.kt
object InkTransitions {
    /** 子页进入：右滑 + 淡入 + 微缩 */
    val slideInFromRight: EnterTransition =
        slideInHorizontally(tween(200)) { it / 3 } + fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.97f)

    /** 子页退出：左滑 + 淡出 */
    val slideOutToLeft: ExitTransition =
        slideOutHorizontally(tween(150)) { -it / 3 } + fadeOut(tween(150))

    /** 返回进入：右滑 + 淡入 */
    val slideInFromLeft: EnterTransition =
        slideInHorizontally(tween(150)) { -it / 3 } + fadeIn(tween(150))

    /** 返回退出：左滑 + 淡出（墨迹干涸） */
    val slideOutToRight: ExitTransition =
        slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(200))

    /** Tab 切换：交叉淡入淡出 + 微缩 */
    val tabEnter: EnterTransition = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.97f)
    val tabExit: ExitTransition = fadeOut(tween(180))
}
```

### 与现有代码的关系

- `Routes.kt` 中的 `NavHost` 需要为每个 `composable` route 添加 `enterTransition` / `exitTransition` 参数
- 当前 `NavHost` 使用类型安全路由（`@Serializable` 路由类），transition 在 route 级别配置
- Predictive Back 手势已在 `MainActivity` 启用，返回转场与之兼容

---

## P2 — 微交互动效

### 2.1 按钮墨溅效果

**新建** `ui/effects/InkSplash.kt`，提供 `Modifier.inkSplash()` 扩展函数。

- 点击时从触摸点向四周扩散一个半透明墨色圆形波纹
- 波纹颜色：`AppTheme.Gold.copy(alpha = 0.15f)`（金色墨溅，呼应印章色）
- 持续时间：300ms
- 使用 `Animatable` 控制半径从 0 → 80dp + alpha 1.0 → 0
- 应用范围：所有 `NeonButton` / 可点击卡片 / Tab 项

```kotlin
// InkSplash.kt
fun Modifier.inkSplash(): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    var radius by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    radius = 0f; alpha = 0.15f
                    Animatable(0f).animateTo(80.dp.toPx(), tween(300)) { radius = it }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    Animatable(0.15f).animateTo(0f, tween(200)) { alpha = it }
                }
            }
        }
    }

    this.pointerInput(Unit) {
        detectTapGestures(onPress = { offset ->
            interaction.tryEmit(PressInteraction.Press(offset))
            tryAwaitRelease()
            interaction.tryEmit(PressInteraction.Release)
        })
    }.drawBehind {
        if (alpha > 0f) {
            drawCircle(
                color = AppTheme.Gold.copy(alpha = alpha),
                radius = radius,
            )
        }
    }
}
```

### 2.2 列表项入场动画

- 角色列表（CharacterListScreen）和卡组列表的 item 入场：每个 item 从 `alpha=0, translateY=20dp` 渐入
- 使用 `LazyVerticalGrid` / `LazyColumn` 的 `animateItem()` modifier
- 视觉效果：像毛笔一行行写下角色名
- `animateItem()` 是 Compose 1.7+ 内置 API，无需额外依赖

### 2.3 卡片翻转水墨晕散

- CharacterCard 点击进入详情时，卡片先做一个 `scale(1.0 → 1.03)` 微缩放 + 短暂 `elevation` 抬升
- 视觉上像手指轻按卡片，墨迹微微扩散
- 使用 `animateFloatAsState` 驱动 scale

### 2.4 页面标题墨迹飘入

- 每个页面顶部标题（AppTopBar）入场时：从左侧 `alpha=0, translateX=-20dp` 滑入
- 持续 250ms，感觉像毛笔起笔
- 使用 `LaunchedEffect(Unit)` + `Animatable` 一次性动画

---

## P3 — 滚动视差效果

### 目标

滚动时多层元素以不同速度移动，营造水墨画的纵深感——远景（背景纹理）缓动，中景（立绘）正常，近景（标题文字）微动。

### 3.1 背景宣纸纹理视差

- 每个页面底部有一层极淡的宣纸纹理背景（已在 `PageBackground` 中实现）
- 滚动时，这个背景以 **0.3x** 速度偏移（相对于列表的 1x 速度）
- 使用 `LazyColumn` 的 `nestedScroll` + `ScrollState.value` 驱动 `Modifier.offset(y = ...)`
- 效果：宣纸纹理微微随手指移动，产生"纸在桌面上滑动"的感觉

### 3.2 角色立绘视差

- CharacterDetailScreen 的 Hero 立绘：滚动时立绘以 **0.7x** 速度上移（比列表慢）
- 使用 `Modifier.graphicsLayer { translationY = scrollOffset * 0.3f }`
- 效果：角色像站在水墨画卷上，滚动时有层次感

### 3.3 标题墨迹飘动

- 页面顶部标题区域：滚动时标题文字微幅上下飘动（±3dp 正弦波）
- 使用 `rememberInfiniteTransition` 驱动一个极慢的 `animateFloat`（周期 4s）
- 效果：标题像悬挂在画卷上的墨字，随风微动

### 性能约束

- 所有视差效果使用 `graphicsLayer` 而非 `offset`，利用 RenderNode 硬件加速
- `ParallaxScroll` 的偏移量限制在 ±50dp 以内，避免过度偏移
- 低端机（`Build.VERSION.SDK_INT < 31`）禁用视差，降级为静态

---

## 文件清单

| 操作 | 文件路径 | 说明 |
|---|---|---|
| **新建** | `ui/nav/InkTransitions.kt` | 转场动画常量和工具函数 |
| **新建** | `ui/effects/InkSplash.kt` | 按钮墨溅 Modifier |
| **修改** | `ui/nav/Routes.kt` | NavHost 添加 transition 参数 |
| **修改** | `ui/components/UIComponents.kt` | NeonButton 添加 inkSplash |
| **修改** | `ui/characters/CharacterListScreen.kt` | 列表项 animateItem() |
| **修改** | `ui/characters/CharacterDetailScreen.kt` | Hero 立绘视差 |
| **修改** | `ui/components/PageComponents.kt` | PageBackground 视差 |
| **修改** | `ui/nav/AppChrome.kt` | AppTopBar 标题飘入 |

---

## 测试策略

- **视觉回归**：每个页面截图对比（before/after），确认动画不破坏布局
- **性能**：使用 `adb shell dumpsys gfxinfo` 确认动画帧率 ≥ 55fps
- **低端机降级**：`SDK_INT < 31` 时所有视差效果自动禁用
- **回归测试**：现有 `RoutesTest` / `GameServiceTest` 不受影响（纯 UI 层变更）

---

## 不做的事情（Scope Boundaries）

1. **书法字体**：暂缓，等网络条件允许时单独处理
2. **自定义 shader 转场**：性能风险高，用 Compose Animation API 足够
3. **页面骨架屏（Skeleton）**：不在本次范围
4. **手势驱动转场**：Predictive Back 已由系统处理，不额外实现
