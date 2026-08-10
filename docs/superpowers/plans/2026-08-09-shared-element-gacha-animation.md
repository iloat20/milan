# 阶段 1A：Shared Element 立绘过渡 + 抽卡演出增强 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Milan 的角色列表 → 详情从生硬页面硬切升级为 Shared Element 立绘放大过渡，子页切换加 fade 过渡，抽卡 reveal 加扫光与稀有度分级延迟。

**Architecture:** 在 `MilanNavHost`（`MainActivity.kt`，纯状态路由）外包 `SharedTransitionLayout`，路由分支包进 `AnimatedContent`（targetState 为路由快照 data class，值相等不重启过渡）。列表立绘与详情 Hero 立绘用**相同 key** 的 `sharedBounds`（`LocalSharedTransitionScope` 获取作用域）。抽卡演出沿用现有 `revealToken` 协程编排，只增强卡面动画与延迟。

**Tech Stack:** Kotlin 2.1.20（必要时升 2.2.x）/ Compose BOM 2026.04.01（Compose 1.11，Shared Element 稳定）/ AGP 8.10.1。

**前置条件：** 设计规格 `docs/superpowers/specs/2026-08-09-experience-retention-modernization-design.md` §2 决策 D1-D5、§4.1-4.3 已确认（默认建议，可随时否决）。

---

### Task 1: BOM 升级（Compose 1.8 → 1.11）

**Files:**
- Modify: `MilanKotlin/gradle/libs.versions.toml:7`
- Test: `MilanKotlin/app/src/test/java/com/milan/game/`（现有全部单测）

- [ ] **Step 1: 修改 BOM 版本**

`MilanKotlin/gradle/libs.versions.toml` 第 7 行：

```toml
composeBom = "2026.04.01"
```

- [ ] **Step 2: 构建验证 Kotlin 兼容性**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

若因 Compose 1.11 runtime 与 Kotlin 2.1.20 编译器冲突报错（形如 `Compose compiler requires Kotlin X`），升级 Kotlin：

`MilanKotlin/gradle/libs.versions.toml` 第 3 行：
```toml
kotlin = "2.2.0"
```
（`kotlin.plugin.compose` 与 `kotlin` 同版本引用，自动同步；AGP 8.10.1 兼容 Kotlin 2.2。）再跑 Step 2。

- [ ] **Step 3: 单测回归**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: 全部测试 PASS（现有测试：SaveDataTest / SaveManagerTest / BattleSimulatorTest / GachaEngineTest / PityCounterTest / EconomyFormulasTest / ProgressionEngineTest / TalentEngineTest / GameServiceTest）

- [ ] **Step 4: 提交**

```bash
git add MilanKotlin/gradle/libs.versions.toml MilanKotlin/gradle/
git commit -m "build: upgrade Compose BOM to 2026.04.01 (Compose 1.11)"
```

---

### Task 2: MilanNavHost 包 SharedTransitionLayout + AnimatedContent

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/MainActivity.kt:70-138`
- Test: 构建 + 手工（UI 动画无单测，见 Step 5）

- [ ] **Step 1: 新增路由快照 data class + 改 MilanNavHost**

`MainActivity.kt`：替换整个 `MilanNavHost` 函数体（第 70-138 行），新增 `NavRoute` 快照。**rememberSaveable 状态与所有回调签名不变**，只把 `when` 分支的判断源从局部变量换成 `AnimatedContent` 的 `targetState`：

```kotlin
/** 导航路由快照：data class 值相等则不重启过渡（每次重组新建实例，equals 判定）。 */
private data class NavRoute(
    val tab: NavItem,
    val collectionOpen: Boolean,
    val listOpen: Boolean,
    val detailId: String?,
    val progressionId: String?,
)

/** 导航宿主：tab 切换 + 子页覆盖。SharedTransitionLayout 提供立绘过渡作用域。 */
@Composable
private fun MilanNavHost() {
    var tab by rememberSaveable { mutableStateOf(NavItem.Home) }
    var collectionOpen by rememberSaveable { mutableStateOf(false) }
    var listOpen by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var progressionId by rememberSaveable { mutableStateOf<String?>(null) }

    val onBack = { collectionOpen = false; listOpen = false; progressionId = null; detailId = null }

    // 路由快照：AnimatedContent 的 targetState（值相等不重启过渡）
    val route = NavRoute(tab, collectionOpen, listOpen, detailId, progressionId)

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                // 子页进出场：fade + 轻微上滑（D4，替换硬切）
                (fadeIn(tween(280)) + slideInVertically(initialOffsetY = { it / 24 }))
                    .togetherWith(fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { -it / 24 }))
            },
            label = "nav",
        ) { r ->
            // 子页优先：角色养成 > 角色详情 > 角色列表 > 名录图鉴（快照判断，避免 smart-cast 问题）
            when {
                r.progressionId != null -> ProgressionScreen(
                    characterId = r.progressionId,
                    onBack = { progressionId = null },
                    onSwitchCharacter = { id -> progressionId = id },
                )
                r.detailId != null -> CharacterDetailScreen(
                    characterId = r.detailId,
                    onBack = onBack,
                    onOpenProgression = { id -> progressionId = id },
                    onSwitchCharacter = { id -> detailId = id },
                )
                r.listOpen -> CharacterListScreen(
                    onBack = onBack,
                    onOpenCharacter = { id -> detailId = id },
                )
                r.collectionOpen -> PlaceholderScreen(
                    title = "神谱图鉴",
                    onBack = onBack,
                    actionLabel = "我的角色",
                    onAction = { listOpen = true },
                )
                else -> when (r.tab) {
                    NavItem.Home -> HomeScreen(
                        onNav = { tab = it },
                        onOpenGacha = { tab = NavItem.Gacha },
                        onOpenCollection = { collectionOpen = true },
                        onOpenCharacter = { id -> detailId = id },
                    )
                    NavItem.Gacha -> GachaScreen(
                        onNav = { tab = it },
                        onOpenCharacter = { id -> detailId = id },
                    )
                    NavItem.Deck -> PlaceholderScreen(
                        title = "卡组",
                        onBack = { tab = NavItem.Home },
                        navItem = NavItem.Deck,
                        onNav = { tab = it },
                    )
                    NavItem.Shop -> PlaceholderScreen(
                        title = "商店",
                        onBack = { tab = NavItem.Home },
                        navItem = NavItem.Shop,
                        onNav = { tab = it },
                    )
                    NavItem.Settings -> PlaceholderScreen(
                        title = "设置",
                        onBack = { tab = NavItem.Home },
                        navItem = NavItem.Settings,
                        onNav = { tab = it },
                    )
                }
            }
        }
    }
}
```

新增 import（MainActivity.kt 顶部）：
```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SharedTransitionLayout
```

- [ ] **Step 2: 构建当前版本**

`CharacterListScreen` / `CharacterDetailScreen` 签名暂不新增参数（调用点保持原样），本步验证 AnimatedContent 过渡生效：

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`（此时 MilanNavHost 已包 AnimatedContent，但子屏无 sharedBounds，过渡为纯 fade/slide）

- [ ] **Step 3: 提交**

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/MainActivity.kt
git commit -m "feat(nav): wrap MilanNavHost in SharedTransitionLayout + AnimatedContent with fade transition"
```

---

### Task 3: 列表入口立绘 sharedBounds

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/characters/CharacterListScreen.kt:60-64, 130-131, 144-214`
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/MainActivity.kt:97-100`（补回传参）

- [ ] **Step 1: CharacterListScreen 新增参数并传给 ListCard**

`CharacterListScreen.kt`：

```kotlin
@Composable
fun CharacterListScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
```

调用 ListCard 处（第 131 行）：
```kotlin
itemsIndexed(visible) { _, ch ->
    ListCard(ch, animatedVisibilityScope) { onOpenCharacter(ch.save.characterId) }
}
```

- [ ] **Step 2: ListCard 立绘 Box 加 sharedBounds**

`CharacterListScreen.kt` ListCard 签名与立绘 Box：

```kotlin
@Composable
private fun ListCard(
    ch: OwnedCharacterView,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val rarityCol = AppTheme.rarityColor(ch.rarity)
    val elem = ElementTheme.forElement(ch.element)
    // Shared Element 作用域：AnimatedContent 提供（null 时退化为普通渲染，安全降级）
    val sharedScope = LocalSharedTransitionScope.current
    // 立绘 Box：sharedBounds（key 全局唯一 = "portrait_${characterId}"，与详情 Hero 同 key 配对）
    val portraitModifier = if (sharedScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "portrait_${ch.save.characterId}"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
            )
        }
    } else Modifier

    Column(
        modifier = Modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(rarityCol.copy(alpha = 110f / 255f), RoundedCornerShape(18.dp))
            .border(2.dp, rarityCol, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppTheme.Surface, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(elem.from.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    .then(portraitModifier),
            ) {
                PortraitImage(
                    characterId = ch.save.characterId,
                    rarity = ch.rarity,
                    name = ch.name,
                    modifier = Modifier.fillMaxSize(),
                    target = PortraitTarget.Thumb,
                )
            }
            // ... 其余内容不变（名称/职阶/稀有度行/星级）
        }
    }
}
```

新增 import：
```kotlin
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.LocalSharedTransitionScope
import androidx.compose.animation.rememberSharedContentState
```

**原理说明（写给执行者）**：`sharedBounds` 只动画「边界框」，框内内容各自独立渲染——正好规避 ContentScale 不参与动画的限制：入口缩略（Thumb 采样、Crop）→ 出口全屏（Full 采样、Crop），过渡期间框从 58dp 高放大到 heroHeight，内部立绘各自按自己 ContentScale 渲染，视觉上立绘平滑放大。

- [ ] **Step 3: MainActivity 补回传参**

`MainActivity.kt` 第 97-100 行 CharacterListScreen 调用点：
```kotlin
r.listOpen -> CharacterListScreen(
    onBack = onBack,
    onOpenCharacter = { id -> detailId = id },
    animatedVisibilityScope = this@AnimatedContent,
)
```

- [ ] **Step 4: 构建验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/ui/characters/CharacterListScreen.kt MilanKotlin/app/src/main/java/com/milan/game/MainActivity.kt
git commit -m "feat(shared-element): list card portrait sharedBounds entry"
```

---

### Task 4: 详情 Hero 立绘 sharedBounds（出口）

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/characters/CharacterDetailScreen.kt:79-86, 133-144, 187-208`
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/MainActivity.kt:91-96`（补回传参）

- [ ] **Step 1: CharacterDetailScreen 新增参数并传给 HeroRegion**

`CharacterDetailScreen.kt`：

```kotlin
@Composable
fun CharacterDetailScreen(
    characterId: String,
    onBack: () -> Unit,
    onOpenProgression: (String) -> Unit,
    onSwitchCharacter: (String) -> Unit,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
```

HeroRegion 调用点（第 133 行）加传参：
```kotlin
HeroRegion(
    view = view,
    owned = owned,
    rarityCol = rarityCol,
    eFrom = eFrom,
    eGlyph = eGlyph,
    heroHeight = heroHeight,
    animatedVisibilityScope = animatedVisibilityScope,
    onBack = onBack,
    onOpenProgression = onOpenProgression,
    onPrev = { switch(-1) },
    onNext = { switch(1) },
)
```

- [ ] **Step 2: HeroRegion 立绘加 sharedBounds（同 key）**

`CharacterDetailScreen.kt` HeroRegion 签名与立绘：

```kotlin
@Composable
private fun HeroRegion(
    view: OwnedCharacterView,
    owned: Boolean,
    rarityCol: Color,
    eFrom: Color,
    eGlyph: String,
    heroHeight: Dp,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpenProgression: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    // 与列表入口同 key：入口缩略 → 出口全屏 配对过渡
    val sharedScope = LocalSharedTransitionScope.current
    val portraitModifier = if (sharedScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "portrait_${view.save.characterId}"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
            )
        }
    } else Modifier

    Box(Modifier.fillMaxWidth().height(heroHeight)) {
        PortraitImage(
            characterId = view.save.characterId,
            rarity = view.rarity,
            name = view.name,
            modifier = Modifier.fillMaxSize().then(portraitModifier),
            contentScale = ContentScale.Crop,
        )
        // ... 其余覆盖层（渐隐遮罩/未拥有遮罩/铭牌/悬浮操作）不变
    }
}
```

新增 import：
```kotlin
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.LocalSharedTransitionScope
import androidx.compose.animation.rememberSharedContentState
```

- [ ] **Step 3: MainActivity 补回传参**

`MainActivity.kt` 第 91-96 行 CharacterDetailScreen 调用点：
```kotlin
r.detailId != null -> CharacterDetailScreen(
    characterId = r.detailId,
    onBack = onBack,
    onOpenProgression = { id -> progressionId = id },
    onSwitchCharacter = { id -> detailId = id },
    animatedVisibilityScope = this@AnimatedContent,
)
```

- [ ] **Step 4: 构建 + 单测回归**

Run: `.\gradlew.bat :app:assembleDebug && .\gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` + 全部测试 PASS

- [ ] **Step 5: 提交**

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/ui/characters/CharacterDetailScreen.kt MilanKotlin/app/src/main/java/com/milan/game/MainActivity.kt
git commit -m "feat(shared-element): hero portrait sharedBounds exit (paired with list entry)"
```

---

### Task 5: 抽卡演出增强（扫光 + 稀有度分级延迟）

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/gacha/GachaScreen.kt:170-187, 388-460`

- [ ] **Step 1: 演出编排稀有度分级延迟**

`GachaScreen.kt` `doPull` 内协程（第 184 行）`delay(1500)` 改为按稀有度分级：

```kotlin
// 阶段三：大立绘卡弹出（SSR/UR 重触觉 + reveal 音效）
cardIn = true
showReveal = true
MilanAudio.playSfx("gacha_reveal")
if (revealRarity >= 3) buzz(HapticFeedbackConstants.CONFIRM) else buzz(HapticFeedbackConstants.VIRTUAL_KEY)
// 稀有度分级停留：R 1.1s / SR 1.2s / SSR 1.6s / UR 1.9s（吊胃口）
val revealHold = when (revealRarity) { 4 -> 1900L; 3 -> 1600L; 2 -> 1200L; else -> 1100L }
delay(revealHold); if (token != revealToken) return@launch
// 阶段四：结果
finishReveal()
```

- [ ] **Step 2: reveal 卡面加扫光动画**

`GachaScreen.kt` reveal 层（第 388-460 行）：在 `cardScale`/`cardAlpha` 之后新增 `sweepX` 动画，并给立绘 Column 加扫光覆盖层：

```kotlin
if (showReveal) {
    val def = revealDef
    val rc = AppTheme.rarityColor(revealRarity)
    val cardScale by animateFloatAsState(
        targetValue = if (cardIn) 1f else 0.80f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f),
        label = "card",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (cardIn) 1f else 0f,
        animationSpec = tween(200),
        label = "cardAlpha",
    )
    // 扫光：卡面滑过的白色高光带（cardIn 后 900ms 完成，渐隐收尾）
    val sweep by animateFloatAsState(
        targetValue = if (cardIn) 1f else 0f,
        animationSpec = tween(900),
        label = "sweep",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .clickable(onClick = ::skipReveal),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(rc.copy(alpha = 0.50f), Color.Transparent))),
        )
        Column(
            modifier = Modifier
                .size(width = 220.dp, height = 312.dp)
                .graphicsLayer { scaleX = cardScale; scaleY = cardScale; alpha = cardAlpha }
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(rc.copy(alpha = 0.34f), AppTheme.BgDeepest, AppTheme.BgDeepest),
                    ),
                )
                .border(2.dp, rc.copy(alpha = 0.85f), RoundedCornerShape(18.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (def != null) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    PortraitImage(
                        characterId = def.characterId,
                        rarity = revealRarity,
                        name = def.displayName,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                    // 扫光覆盖层：从左到右划过的高光带
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val bandWidth = size.width * 0.9f
                                translationX = sweep * (size.width + bandWidth) - bandWidth
                                alpha = (1f - sweep).coerceIn(0f, 1f) * 0.45f
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                                ),
                            ),
                    )
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(AppTheme.rarityName(revealRarity), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = rc)
                }
            }
            // ... 底部铭牌/稀有度名不变
        }
        // ... 跳过按钮不变
    }
}
```

**扫光原理**：`sweep` 从 0→1 时，高光带 `translationX` 从带外左侧（`-bandWidth`）移到带外右侧（`size.width`），同时 alpha 线性衰减——即「光带滑过 + 渐隐收尾」一次性效果。`graphicsLayer` 只做变换，不触发重绘逐帧。

- [ ] **Step 3: 构建验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/ui/gacha/GachaScreen.kt
git commit -m "feat(gacha): rarity-tiered reveal hold + card sweep light effect"
```

---

### Task 6: 整体回归

**Files:**
- Test: 全部（构建 + 单测 + 真机）

- [ ] **Step 1: 完整构建 + 单测**

Run: `.\gradlew.bat :app:assembleDebug && .\gradlew.bat :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` + 全部测试 PASS

- [ ] **Step 2: 手工验证清单（真机或模拟器）**

1. 主页 → 我的角色 → 点任意角色卡：立绘从 58dp 缩略位置**平滑放大**到全屏 Hero 区域，页面同时 fade 进场。
2. 详情页点「‹ 返回」：立绘从全屏**收缩归位**到列表卡片，页面 fade 退出。
3. 详情页左右切换角色（GlassArrow）：立绘过渡不卡顿，切换后 key 变化 → 下一角色无配对时正常 fade（不崩）。
4. 抽卡：单抽 → 法阵脉冲 → 白闪 → 大立绘卡弹出带**扫光划过**；SSR/UR 停留明显长于 R（1.6s/1.9s vs 1.1s）。
5. 抽卡 reveal 期间点整屏跳过：立即出结果，无残留动画状态。
6. 5 个 tab 来回切换：fade 过渡正常，无黑屏/闪烁。
7. 低端机（若有）：立绘过渡无跳帧（`adb shell dumpsys gfxinfo com.milan.game` 无长 jank）。
8. 旋转屏幕：路由状态 rememberSaveable 保留（tab/详情位置不丢），过渡不异常。

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "feat(animation): regression pass for shared element + gacha reveal"
```

---

### Task 7（可选，遇阻即砍）: AGSL 召唤门背景特效

**前置：** BOM 升级成功（Task 1）且 Task 5 完成。若 Task 1 需升级 Kotlin 或构建环境异常，**整体砍掉本 Task**（spec D5 降级路径），不影响 1A 交付。

**Files:**
- Create: `MilanKotlin/app/src/main/java/com/milan/game/ui/gacha/PortalEffect.kt`
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/gacha/GachaScreen.kt:283-331`（召唤法阵）

- [ ] **Step 1: 创建 AGSL 着色器（旋转光晕 + 粒子漂移）**

`PortalEffect.kt`（AGSL 字符串着色器，Compose `ShaderBrush` 使用）：

```kotlin
package com.milan.game.ui.gacha

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush

/** 召唤门动态背景：随时间旋转的金紫光晕 + 向上漂移的光点（AGSL，降级时整文件删除）。 */
private const val PORTAL_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform float uAlpha;
    half4 main(float2 xy) {
        float2 p = (xy - resolution * 0.5) / resolution.y;
        float r = length(p);
        float ang = atan(p.y, p.x) + time * 0.35;
        float swirl = sin(ang * 4.0 + r * 14.0 - time * 1.6) * 0.5 + 0.5;
        half3 gold = half3(1.0, 0.78, 0.30);
        half3 violet = half3(0.55, 0.35, 0.85);
        float ring = smoothstep(0.42, 0.40, r) * smoothstep(0.34, 0.36, r);
        half3 col = mix(violet, gold, swirl);
        col *= ring * 0.85;
        // 光点漂移
        float sparkle = 0.0;
        for (int i = 0; i < 6; i++) {
            float ph = float(i) * 0.618;
            float2 sp = float2(
                sin(ph * 7.13) * 0.36,
                fract(ph + time * 0.12) * 0.9 - 0.45
            );
            float d = distance(p, sp);
            sparkle += exp(-d * 34.0) * (0.5 + 0.5 * sin(time * 3.0 + ph * 9.0));
        }
        col += gold * sparkle * 0.5;
        return half4(col, uAlpha);
    }
"""

/** 供 GachaScreen 使用：返回随时间更新的 ShaderBrush（配合 rememberInfiniteTransition 驱动 time）。 */
fun portalShaderBrush(runtimeShader: RuntimeShader, time: Float, alpha: Float): Brush {
    runtimeShader.setFloatUniform("time", time)
    runtimeShader.setFloatUniform("uAlpha", alpha)
    return ShaderBrush(runtimeShader)
}
```

- [ ] **Step 2: GachaScreen 召唤法阵接入**

`GachaScreen.kt`：新增状态 + 替换法阵背景（第 284-331 行的 Box 内容里，把最外层径向渐变背景换成 portalShaderBrush）。

**AGSL + Compose 接入要点**：`RuntimeShader` 的 uniform 无法用 `Modifier.drawWithCache` 更新，必须在每帧绘制前设置——用 `Canvas`（每帧回调）实现：

```kotlin
// 状态（doPull 上方声明区新增）：
val portalShader = remember { RuntimeShader(PORTAL_SHADER) }
val portalTime by rememberInfiniteTransition(label = "portal").animateFloat(
    initialValue = 0f, targetValue = 1000f,
    animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing)),
    label = "portalTime",
)

// 法阵最外层背景替换（原 Brush.radialGradient 处）——Canvas 每帧绘制前设置 uniform：
Canvas(Modifier.fillMaxSize()) {
    portalShader.setFloatUniform("resolution", size.width, size.height)
    portalShader.setFloatUniform("time", portalTime)
    portalShader.setFloatUniform("uAlpha", 1f)
    drawRect(ShaderBrush(portalShader))
}
```

用上述 Canvas 替换法阵最外层 Box 的 background。法阵内环（双环边框 + ✦）保持现有结构不变，叠在 Canvas 之上（Canvas 作为第一层背景）。

- [ ] **Step 3: 构建 + 真机验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`；真机抽卡页召唤法阵区域呈旋转金紫光晕 + 光点漂移动画。

AGSL 在 API 26+ 可用；minSdk=29 无需兼容分支。若设备黑屏/异常 → 删除 PortalEffect.kt 引用回退（Task 5 的静态法阵仍保留）。

- [ ] **Step 4: 提交**

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/ui/gacha/PortalEffect.kt MilanKotlin/app/src/main/java/com/milan/game/ui/gacha/GachaScreen.kt
git commit -m "feat(gacha): AGSL portal background shader (optional)"
```

---

## 明确不做（本阶段）

- 不做成就/签到/统计（阶段 1B 独立计划）；不做 M3 Expressive / Game Mode / JankStats（阶段 2 独立计划）。
- 不迁移 Navigation 3（spec D2 已否决）。
- 不改立绘加载（沿用 PortraitLoader 采样/缓存；sharedBounds 只动画边界框，不改变采样档位）。
- 不动领域层、存档序列化、事件总线。
- 预测性返回（spec 标可选）留待阶段 2 与系统返回键统一处理，本阶段不做。
