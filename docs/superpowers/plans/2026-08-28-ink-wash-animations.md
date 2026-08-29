# 水墨动效 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ink wash style page transitions, micro-interactions, and scroll parallax to all frontend pages.

**Architecture:** Three phases — P1 (page transitions via Navigation Compose 2.9 enterTransition/exitTransition), P2 (button ink splash Modifier + list item animate + title slide-in), P3 (background parallax + hero parallax + title float). Each phase is independently shippable.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose 2.9, Compose Animation API (Animatable, animateFloatAsState, rememberInfiniteTransition)

---

## Task 1: Create InkTransitions object

**Files:**
- Create: `app/src/main/java/com/milan/game/ui/nav/InkTransitions.kt`

- [ ] **Step 1: Create InkTransitions.kt with all transition constants**

```kotlin
package com.milan.game.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * 水墨国风页面转场动画常量。
 * - 子页：右进左出（墨汁泼入/干涸）
 * - Tab：交叉淡入淡出（平等切换）
 * - 返回：左进右出（Predictive Back 兼容）
 */
object InkTransitions {

    /** 子页进入：右滑 + 淡入 + 微缩 */
    val slideInFromRight: EnterTransition =
        slideInHorizontally(tween(200)) { it / 3 } +
                fadeIn(tween(200)) +
                scaleIn(tween(200), initialScale = 0.97f)

    /** 子页退出：左滑 + 淡出 */
    val slideOutToLeft: ExitTransition =
        slideOutHorizontally(tween(150)) { -it / 3 } +
                fadeOut(tween(150))

    /** 返回进入：右滑 + 淡入 */
    val slideInFromLeft: EnterTransition =
        slideInHorizontally(tween(150)) { -it / 3 } +
                fadeIn(tween(150))

    /** 返回退出：左滑 + 淡出（墨迹干涸） */
    val slideOutToRight: ExitTransition =
        slideOutHorizontally(tween(200)) { it / 3 } +
                fadeOut(tween(200))

    /** Tab 切换：交叉淡入淡出 + 微缩 */
    val tabEnter: EnterTransition =
        fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.97f)

    val tabExit: ExitTransition =
        fadeOut(tween(180))
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/milan/game/ui/nav/InkTransitions.kt
git commit -m "feat(ui): add InkTransitions — ink wash page transition constants"
```

---

## Task 2: Apply transitions to NavHost

**Files:**
- Modify: `app/src/main/java/com/milan/game/MainActivity.kt:185-192` (NavHost transition params)

- [ ] **Step 1: Add import for InkTransitions**

In `MainActivity.kt`, add after line 64 (`import com.milan.game.ui.nav.toNavRoute`):

```kotlin
import com.milan.game.ui.nav.InkTransitions
```

- [ ] **Step 2: Replace NavHost transition lambdas**

Replace lines 190-191 (the current `enterTransition` / `exitTransition`):

Current:
```kotlin
enterTransition = { fadeIn(tween(280)) + slideInVertically(initialOffsetY = { it / 24 }) },
exitTransition = { fadeOut(tween(200)) + slideOutVertically(targetOffsetY = { -it / 24 }) },
```

New:
```kotlin
enterTransition = { InkTransitions.slideInFromRight },
exitTransition = { InkTransitions.slideOutToLeft },
popEnterTransition = { InkTransitions.slideInFromLeft },
popExitTransition = { InkTransitions.slideOutToRight },
```

- [ ] **Step 3: Remove unused imports**

Remove `slideInVertically` and `slideOutVertically` imports (lines 11-12) since they are no longer used. Remove `tween` import (line 8) only if no other usages remain in the file — check first; `fadeIn`/`fadeOut` on line 190 will be gone too, so remove those imports as well.

After cleanup, imports should include only `InkTransitions` from this file's new code.

- [ ] **Step 4: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/milan/game/MainActivity.kt
git commit -m "feat(ui): apply ink wash transitions to NavHost — slide/fade/scale per route type"
```

---

## Task 3: Create InkSplash Modifier

**Files:**
- Create: `app/src/main/java/com/milan/game/ui/effects/InkSplash.kt`

- [ ] **Step 1: Create the InkSplash.kt file**

```kotlin
package com.milan.game.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.milan.game.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * 水墨墨溅效果 Modifier：点击时从触摸点扩散金色半透明圆形波纹。
 * 用于 NeonButton、可点击卡片、Tab 项。
 */
fun Modifier.inkSplash(): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    var radius by remember { mutableFloatStateOf(0f) }
    var alpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    radius = 0f
                    alpha = 0.15f
                    scope.launch {
                        Animatable(0f).animateTo(80.dp.toPx(), tween(300)) {
                            radius = it.value
                        }
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    scope.launch {
                        Animatable(alpha).animateTo(0f, tween(200)) {
                            alpha = it.value
                        }
                    }
                }
            }
        }
    }

    this.pointerInput(Unit) {
        detectTapGestures(
            onPress = { offset ->
                interactionSource.tryEmit(PressInteraction.Press(offset))
                tryAwaitRelease()
                interactionSource.tryEmit(PressInteraction.Release)
            }
        )
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

- [ ] **Step 2: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/milan/game/ui/effects/InkSplash.kt
git commit -m "feat(ui): add inkSplash() Modifier — gold ink ripple on press"
```

---

## Task 4: Apply inkSplash to NeonButton

**Files:**
- Modify: `app/src/main/java/com/milan/game/ui/components/UIComponents.kt` (NeonButton function — locate and add `.inkSplash()`)

- [ ] **Step 1: Add import for inkSplash**

In `UIComponents.kt`, add after line 31 (`import com.milan.game.ui.theme.AppTheme`):

```kotlin
import com.milan.game.ui.effects.inkSplash
```

- [ ] **Step 2: Find NeonButton and add inkSplash**

Search for `NeonButton` in `UIComponents.kt`. Add `.inkSplash()` to its modifier chain. The exact line depends on the current NeonButton signature — typically after `.clickable` or at the end of the modifier chain.

Example (adjust to match actual NeonButton code):

```kotlin
// Before:
    .clickable(onClick = onClick)

// After:
    .inkSplash()
    .clickable(onClick = onClick)
```

- [ ] **Step 3: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/milan/game/ui/components/UIComponents.kt
git commit -m "feat(ui): apply inkSplash to NeonButton"
```

---

## Task 5: Add list item entry animation to CharacterListScreen

**Files:**
- Modify: `app/src/main/java/com/milan/game/ui/characters/CharacterListScreen.kt` (LazyVerticalGrid items)

- [ ] **Step 1: Find LazyVerticalGrid and add animateItem()**

In `CharacterListScreen.kt`, locate the `LazyVerticalGrid` or `LazyColumn`. Each item's outermost Modifier should have `.animateItem()` added.

`animateItem()` is a no-arg Compose API — just add it to the item's modifier:

```kotlin
// Before:
item { Card(...) }

// After:
item { Card(modifier = Modifier.animateItem()) { ... } }
```

If the items use `Modifier.fillMaxWidth()` etc., chain `.animateItem()` at the end.

- [ ] **Step 2: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/milan/game/ui/characters/CharacterListScreen.kt
git commit -m "feat(ui): add animateItem() to CharacterListScreen grid — ink brush stagger entry"
```

---

## Task 6: Add card press scale animation to CharacterCard

**Files:**
- Modify: `app/src/main/java/com/milan/game/ui/components/CharacterCard.kt` (card clickable modifier)

- [ ] **Step 1: Add press scale animation**

In `CharacterCard.kt`, find the clickable modifier on the card composable. Replace the simple `.clickable` with a press-scale animation:

```kotlin
// Add imports at top of file:
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer

// In the card composable, before the modifier chain:
var isPressed by remember { mutableStateOf(false) }
val scale by animateFloatAsState(
    targetValue = if (isPressed) 1.03f else 1f,
    animationSpec = tween(150),
    label = "cardPress"
)

// In the modifier chain, add graphicsLayer before clickable:
    .graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    .pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                isPressed = true
                tryAwaitRelease()
                isPressed = false
            },
            onTap = { /* existing click handler */ }
        )
    }
```

Adjust to match the actual CharacterCard click pattern (lambda parameter vs direct clickable).

- [ ] **Step 2: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/milan/game/ui/components/CharacterCard.kt
git commit -m "feat(ui): add press-scale animation to CharacterCard — ink press feel"
```

---

## Task 7: Add title slide-in animation to AppTopBar

**Files:**
- Modify: `app/src/main/java/com/milan/game/ui/nav/AppChrome.kt` (AppTopBar composable)

- [ ] **Step 1: Add title slide-in animation**

In `AppChrome.kt`, find the `AppTopBar` composable. Add a `LaunchedEffect` that animates the title text from left-offset to center:

```kotlin
// Add imports:
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer

// Inside AppTopBar, before the title Text:
val titleOffset = remember { Animatable(-20f) }
LaunchedEffect(Unit) {
    titleOffset.animateTo(0f, tween(250))
}

// On the title Text modifier:
    .graphicsLayer {
        translationX = titleOffset.value.dp.toPx()
        alpha = 1f + titleOffset.value / 20f  // 0.0 → 1.0 as offset goes -20 → 0
    }
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/milan/game/ui/nav/AppChrome.kt
git commit -m "feat(ui): add title slide-in animation to AppTopBar — brush stroke entry"
```

---

## Task 8: Add background parallax to PageBackground

**Files:**
- Modify: `app/src/main/java/com/milan/game/ui/components/UIComponents.kt` (PageBackground composable)
- Create: `app/src/main/java/com/milan/game/ui/effects/ParallaxScroll.kt` (scroll state holder)

- [ ] **Step 1: Create ParallaxScroll.kt with scroll state holder**

```kotlin
package com.milan.game.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.os.Build

/**
 * Holds parallax scroll multiplier. Returns 1.0f on low-end devices (SDK < 31),
 * otherwise the configured multiplier.
 */
object ParallaxConfig {
    /** Returns multiplier or 0f if parallax should be disabled. */
    fun multiplier(factor: Float): Float {
        return if (Build.VERSION.SDK_INT >= 31) factor else 0f
    }
}
```

- [ ] **Step 2: Modify PageBackground to accept optional scroll offset**

In `UIComponents.kt`, update `PageBackground`:

```kotlin
// Add import:
import com.milan.game.ui.effects.ParallaxConfig
import androidx.compose.ui.graphics.graphicsLayer

// Updated signature:
@Composable
fun PageBackground(
    modifier: Modifier = Modifier,
    scrollOffset: Float = 0f,  // new parameter
    content: @Composable BoxScope.() -> Unit,
) {
    val parallax = ParallaxConfig.multiplier(0.3f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = scrollOffset * parallax
            }
            .background(
                Brush.verticalGradient(
                    listOf(
                        AppTheme.BgDeepest,
                        AppTheme.BgMid,
                        AppTheme.BgDeepest,
                    ),
                ),
            ),
        content = content,
    )
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/milan/game/ui/effects/ParallaxScroll.kt app/src/main/java/com/milan/game/ui/components/UIComponents.kt
git commit -m "feat(ui): add parallax support to PageBackground — background shifts at 0.3x"
```

---

## Task 9: Wire parallax into a screen (CharacterListScreen)

**Files:**
- Modify: `app/src/main/java/com/milan/game/ui/characters/CharacterListScreen.kt`

- [ ] **Step 1: Add scroll state and pass to PageBackground**

In `CharacterListScreen.kt`:

```kotlin
// Add import:
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

// Inside the composable, before LazyVerticalGrid:
val gridState = rememberLazyGridState()
val scrollOffset by remember {
    derivedStateOf { gridState.firstVisibleItemScrollOffset.toFloat() }
}

// Pass to PageBackground:
PageBackground(scrollOffset = scrollOffset) {
    // existing content...
}
```

If the screen uses `LazyColumn` instead, use `rememberLazyListState()`.

- [ ] **Step 2: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/milan/game/ui/characters/CharacterListScreen.kt
git commit -m "feat(ui): wire parallax into CharacterListScreen — background shifts on scroll"
```

---

## Task 10: Add hero portrait parallax to CharacterDetailScreen

**Files:**
- Modify: `app/src/main/java/com/milan/game/ui/characters/CharacterDetailScreen.kt`

- [ ] **Step 1: Add graphicsLayer parallax to Hero portrait**

In `CharacterDetailScreen.kt`, find the `SubPageHero` or hero portrait composable. Add a scroll-driven `graphicsLayer` translation:

```kotlin
// Add import:
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer

// Inside the composable, get scroll state:
val listState = rememberLazyListState()
val scrollOffset by remember {
    derivedStateOf { listState.firstVisibleItemScrollOffset.toFloat() }
}

// On the hero portrait / SubPageHero modifier:
    .graphicsLayer {
        translationY = scrollOffset * ParallaxConfig.multiplier(0.3f)
    }
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/milan/game/ui/characters/CharacterDetailScreen.kt
git commit -m "feat(ui): add hero parallax to CharacterDetailScreen — portrait at 0.7x scroll"
```

---

## Task 11: Add floating title animation

**Files:**
- Modify: `app/src/main/java/com/milan/game/ui/nav/AppChrome.kt` (AppTopBar)

- [ ] **Step 1: Add infinite float animation to AppTopBar title**

In `AppChrome.kt`, add a subtle sine-wave float to the title:

```kotlin
// Add imports:
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlin.math.sin

// Inside AppTopBar, after the slide-in LaunchedEffect:
val infiniteTransition = rememberInfiniteTransition(label = "titleFloat")
val floatPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 2f * Math.PI.toFloat(),
    animationSpec = InfiniteRepeatableSpec(
        animation = tween(4000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
    ),
    label = "titleFloatPhase"
)
val floatOffset = sin(floatPhase) * 3f  // ±3dp

// Combine with existing slide-in on the title Text:
    .graphicsLayer {
        translationX = titleOffset.value.dp.toPx()
        translationY = floatOffset.dp.toPx()
        alpha = 1f + titleOffset.value / 20f
    }
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/milan/game/ui/nav/AppChrome.kt
git commit -m "feat(ui): add floating title animation — ±3dp sine wave at 4s period"
```

---

## Task 12: Final build verification

**Files:** None (verification only)

- [ ] **Step 1: Full debug build**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleDebug 2>&1 | Select-Object -Last 15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Full release build**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:assembleRelease 2>&1 | Select-Object -Last 15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run existing tests**

Run: `cd MilanKotlin && pwsh -NoProfile -File run-gradle.ps1 :app:testDebugUnitTest 2>&1 | Select-Object -Last 15`
Expected: All tests pass (no UI logic changed, only visual layers)

- [ ] **Step 4: Commit all remaining changes**

```bash
git add -A && git commit -m "feat(ui): ink wash animations complete — transitions, splash, parallax, float"
```
