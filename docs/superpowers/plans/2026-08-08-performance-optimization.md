# 低端机性能优化（立绘采样解码 + 首屏异步化） Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 App 在低端机（2-3GB 内存、Android 10-12）上首屏不再同步解码 7 张全尺寸立绘（3.8MB/张）导致卡顿假死，改为 IO 线程采样解码 + LRU 缓存 + 占位→立绘过渡，并把 BGM 构建移出主线程。

**Architecture:** 新 `PortraitLoader.kt` 单文件承载全部图片加载逻辑：纯 Kotlin 部分（`PortraitTarget` 采样档位 / `computeInSampleSize` 采样率 / `PortraitKey` 缓存键 / `PortraitLruCache` 手写 LRU——可 JVM 单测）+ Android 接入部分（`PortraitLoader` object：`Dispatchers.IO` 采样解码 + 24MB 缓存）。`PortraitImage` 改为异步渲染（解码期间显示既有渐变+首字占位）。配套：HomeScreen 名录头像区 `Row`→`LazyRow`、`MilanAudio.playBgm` 移入后台线程。

**Tech Stack:** Kotlin 2.1.20 / Jetpack Compose（BOM 2025.05.00）/ BitmapFactory + inSampleSize / kotlinx-coroutines / JUnit4 + coroutines-test（现有测试惯例）

**执行环境事实（执行前必读）：**
- 工程在 `MilanKotlin/`，用 wrapper，无需本地 Gradle：`.\gradlew.bat :app:testDebugUnitTest`、`.\gradlew.bat :app:assembleDebug`、`.\gradlew.bat :app:assembleRelease`（需 JDK 17+，`gradle.properties` 已配 `org.gradle.java.home`）
- 单测目录：`MilanKotlin/app/src/test/java/com/milan/game/`（JUnit4）
- 红线：`domain/`、`data/`（模型）禁止 `import android.*`——本计划所有改动都在 `ui/components/`、`ui/home/`、`ui/characters/`、`ui/gacha/`、`infrastructure/`（均非 domain/data，安全）；不动 `@Serializable` 结构；不引入新依赖（`PortraitLruCache` 手写就是为规避 android.util.LruCache 在 JVM 单测不可用）
- 设计规格：`docs/superpowers/specs/2026-08-08-performance-optimization-design.md`（已提交，§4.3 分档表 2026-08-08 修正：GachaScreen 仅 reveal 为 Full，池预览 52dp 与 chip 42dp 为 Thumb）

---

### Task 1: PortraitLoader 纯 Kotlin 核心（采样档位/采样率/缓存键/LRU 缓存）——测试先行

**Files:**
- Create: `MilanKotlin/app/src/test/java/com/milan/game/ui/PortraitLoaderTest.kt`
- Create: `MilanKotlin/app/src/main/java/com/milan/game/ui/components/PortraitLoader.kt`（本任务只写纯 Kotlin 部分，Android 部分在 Task 2）

- [ ] **Step 1: Write the failing test**

```kotlin
package com.milan.game.ui

import com.milan.game.ui.components.PortraitKey
import com.milan.game.ui.components.PortraitLruCache
import com.milan.game.ui.components.computeInSampleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PortraitLoader 纯 Kotlin 核心单测（Android 接入部分无法在 JVM 单测，留给真机验证）。 */
class PortraitLoaderTest {

    @Test
    fun `computeInSampleSize 需求不小于原图时返回 1`() {
        assertEquals(1, computeInSampleSize(832, 1186, 832, 1186))
        assertEquals(1, computeInSampleSize(832, 1186, 2000, 2000))
    }

    @Test
    fun `computeInSampleSize 按 2 的幂下采样到满足需求`() {
        // Thumb 档位语义（58dp 头像）：832×1186 → 208×296（4x）
        assertEquals(4, computeInSampleSize(832, 1186, 208, 296))
        // Full 档位语义（全屏 Hero）：832×1186 → 416×593（2x）
        assertEquals(2, computeInSampleSize(832, 1186, 416, 593))
    }

    @Test
    fun `computeInSampleSize 极小块继续下采样`() {
        assertEquals(8, computeInSampleSize(832, 1186, 104, 148))
    }

    @Test
    fun `PortraitKey 不同采样档位是不同键`() {
        assertNotEquals(PortraitKey(1, 2), PortraitKey(1, 4))
        assertEquals(PortraitKey(1, 2), PortraitKey(1, 2))
    }

    @Test
    fun `PortraitLruCache 超容量时逐出最久未用`() {
        val cache = PortraitLruCache<Int>(maxBytes = 100, sizeOf = { it })
        cache.put(PortraitKey(1, 2), 60)
        cache.put(PortraitKey(2, 2), 60) // 120 > 100 → 逐出键 1
        assertNull(cache.get(PortraitKey(1, 2)))
        assertEquals(60, cache.get(PortraitKey(2, 2)))
    }

    @Test
    fun `PortraitLruCache get 命中会刷新访问序`() {
        val cache = PortraitLruCache<Int>(maxBytes = 100, sizeOf = { it })
        cache.put(PortraitKey(1, 2), 40)
        cache.put(PortraitKey(2, 2), 40) // 80 ≤ 100 不逐出
        cache.get(PortraitKey(1, 2))     // 刷新键 1 访问序
        cache.put(PortraitKey(3, 2), 40) // 120 > 100 → 逐出最久未用的键 2
        assertNull(cache.get(PortraitKey(2, 2)))
        assertEquals(40, cache.get(PortraitKey(1, 2)))
        assertEquals(40, cache.get(PortraitKey(3, 2)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.milan.game.ui.PortraitLoaderTest"`
Expected: FAIL（`PortraitLoader.kt` 不存在 → 编译错误 `Unresolved reference: PortraitKey` 等）

- [ ] **Step 3: Write minimal implementation（纯 Kotlin 部分）**

`MilanKotlin/app/src/main/java/com/milan/game/ui/components/PortraitLoader.kt`：

```kotlin
package com.milan.game.ui.components

/**
 * 角色立绘加载（C# PortraitLoader 的 Kotlin 等价物，2026-08-08 低端机性能优化）。
 *
 * 立绘资源 832×1186 ≈ 3.8MB/张：头像场景全尺寸同步解码是首屏卡顿主因，
 * 统一按显示档位采样（Full=2x / Thumb=4x），单图内存降 16 倍。
 *
 * 分层：本文件前半部为纯 Kotlin（可 JVM 单测），Android 接入（object
 * PortraitLoader）在 Task 2 追加于文件后半部 —— 纯逻辑不得依赖 android.*。
 */
enum class PortraitTarget(val sample: Int) {
    /** 全屏/大图（Hero/详情/养成/reveal）：416×593 ≈ 0.95MB。 */
    Full(2),

    /** 小图（≤58dp 头像/chip）：208×296 ≈ 0.24MB。 */
    Thumb(4),
}

/**
 * 采样率计算（BitmapFactory 文档标准算法）：
 * 需求尺寸不小于原图 → 1；否则按 2 的幂下采样，直到半尺寸不满足需求为止。
 */
fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
    var sample = 1
    if (srcH > reqH || srcW > reqW) {
        val halfH = srcH / 2
        val halfW = srcW / 2
        while (halfH / sample >= reqH && halfW / sample >= reqW) {
            sample *= 2
        }
    }
    return sample
}

/** 缓存键：资源 ID × 采样档位（同一资源不同档位互不驱逐）。 */
data class PortraitKey(val resId: Int, val sample: Int)

/** 缓存容量上限（spec D3）：24MB，低端机友好。 */
const val PORTRAIT_CACHE_BYTES = 24 * 1024 * 1024

/**
 * 最小 LRU 缓存（Android LruCache 语义的纯 Kotlin 复刻，可 JVM 单测）。
 * 线程安全：全部操作 synchronized；accessOrder=true 保证 get 命中刷新访问序。
 */
class PortraitLruCache<V : Any>(
    private val maxBytes: Int,
    private val sizeOf: (V) -> Int,
) {
    private val map = LinkedHashMap<PortraitKey, V>(0, 0.75f, true)

    @Synchronized
    fun get(key: PortraitKey): V? = map[key]

    @Synchronized
    fun put(key: PortraitKey, value: V): V? {
        val previous = map.put(key, value)
        trimToSize()
        return previous
    }

    @Synchronized
    fun size(): Int = map.size

    /** 从最久未用开始逐出，直到总占用 ≤ 上限。 */
    private fun trimToSize() {
        while (true) {
            val total = map.entries.sumOf { sizeOf(it.value) }
            if (total <= maxBytes || map.isEmpty()) return
            map.remove(map.entries.iterator().next().key)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.milan.game.ui.PortraitLoaderTest"`
Expected: PASS（6 tests, 0 failures）

- [ ] **Step 5: Commit**

```bash
git add MilanKotlin/app/src/test/java/com/milan/game/ui/PortraitLoaderTest.kt MilanKotlin/app/src/main/java/com/milan/game/ui/components/PortraitLoader.kt
git commit -m "feat(ui): PortraitLoader 纯 Kotlin 核心（采样档位/采样率/LRU 缓存）+ 单测"
```

---

### Task 2: PortraitLoader Android 接入（IO 线程采样解码 + 24MB 缓存）

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/components/PortraitLoader.kt`（追加 Android 部分）

- [ ] **Step 1: Append Android access layer to PortraitLoader.kt**

在文件尾部追加（保留 Task 1 全部内容；imports 加在文件顶部原 package 之后）：

```kotlin
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- Android 接入（以下依赖 android.*，不可进 JVM 单测）----

object PortraitLoader {
    private val cache = PortraitLruCache<Bitmap>(PORTRAIT_CACHE_BYTES) { it.byteCount }

    /**
     * 异步解码：命中缓存直接返回；未命中 IO 线程按档位采样解码并入缓存；
     * 解码失败/资源损坏返回 null（调用方走占位，宁可难看也不能崩）。
     */
    suspend fun load(res: Resources, resId: Int, target: PortraitTarget): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = PortraitKey(resId, target.sample)
            cache.get(key) ?: runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = target.sample }
                BitmapFactory.decodeResource(res, resId, opts)?.also { cache.put(key, it) }
            }.getOrNull()
        }
}
```

注意：文件最终 import 顺序为 `com.milan.game.ui.components` 包声明 → android.* → kotlinx.coroutines.*（沿用 Kotlin 惯例：同包/第三方/Java 分组，具体顺序与文件内现有风格对齐即可，编译不校验顺序）。

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL（无编译错误；Android 接入无 JVM 单测，此处为编译门）

- [ ] **Step 3: Commit**

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/ui/components/PortraitLoader.kt
git commit -m "feat(ui): PortraitLoader Android 接入（IO 线程采样解码 + 24MB LRU 缓存）"
```

---

### Task 3: PortraitImage 异步采样解码（占位 → 立绘过渡）

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/components/PortraitImage.kt`（整文件替换）

- [ ] **Step 1: Replace PortraitImage.kt with async version**

整文件替换为（保留原有注释与 `PortraitFallback` 不变）：

```kotlin
package com.milan.game.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.milan.game.ui.theme.AppTheme

/**
 * 角色立绘（C# PortraitLoader 的 Compose 等价物）。
 *
 * 立绘资源名 = 角色 CharacterId（drawable/char_<rarity>_<pinyin>.png）。
 * data.json 有 56 个角色但 drawable 只有 28 张立绘 —— 缺图必须占位兜底，
 * 直接 painterResource 引用不存在的资源会抛 NotFoundException 闪退，
 * 故先用 getIdentifier 探测资源是否存在，缺失时渲染「稀有度渐变 + 角色名首字」。
 *
 * 性能（2026-08-08 低端机优化）：不再组合期同步解码全尺寸立绘（3.8MB/张
 * 会卡死首帧），改由 PortraitLoader 在 IO 线程按 [PortraitTarget] 采样解码，
 * 解码完成前渲染占位（渐变+首字），完成后替换 —— 占位即启动反馈。
 */
@Composable
fun PortraitImage(
    characterId: String,
    rarity: Int,
    name: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    target: PortraitTarget = PortraitTarget.Full,
) {
    val context = LocalContext.current
    // 资源名 = characterId；remember 缓存探测结果，避免重组反复查。
    // 探测成功直接得到资源 ID，失败返回 0。
    val portraitId = remember(characterId) {
        context.resources.getIdentifier(characterId, "drawable", context.packageName)
    }
    // 解码结果存组合状态；portraitId/target 变化时重建（初始 null → 占位）。
    var bitmap by remember(portraitId, target) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(portraitId, target) {
        if (portraitId != 0) {
            bitmap = PortraitLoader.load(context.resources, portraitId, target)
        }
    }

    val bmp = bitmap
    if (portraitId != 0 && bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = name ?: characterId,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        PortraitFallback(characterId, rarity, name, modifier)
    }
}

/** 缺图占位：稀有度径向渐变 + 首字（C# 占位语义：宁可难看也不能崩）。 */
@Composable
private fun PortraitFallback(
    characterId: String,
    rarity: Int,
    name: String?,
    modifier: Modifier,
) {
    val c = AppTheme.rarityColor(rarity)
    val initial = (name?.take(1) ?: characterId.take(1)).uppercase()
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                listOf(c.copy(alpha = 0.30f), Color.Transparent),
                radius = 900f,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = c.copy(alpha = 0.85f),
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL（`painterResource` import 已移除；全部现有调用点用默认参数 `Full` 仍编译通过）

- [ ] **Step 3: Commit**

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/ui/components/PortraitImage.kt
git commit -m "feat(ui): PortraitImage 异步采样解码（占位→立绘过渡，默认 Full 档）"
```

---

### Task 4: 调用点分档（4 处小图改 Thumb 采样）

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/home/HomeScreen.kt:383`（AvatarCircle 内）
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/characters/CharacterListScreen.kt:171`（ListCard 内）
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/gacha/GachaScreen.kt:261`（池角色预览）与 `:521`（GachaChip）

其余 4 处（HomeScreen HeroPortrait / CharacterDetailScreen HeroRegion / ProgressionScreen HeroRegion / GachaScreen reveal :427）保持默认 `Full`，**不改**。

- [ ] **Step 1: HomeScreen.kt — AvatarCircle 加 Thumb**

先确认文件顶部已有 `import com.milan.game.ui.components.PortraitImage`，在其旁边加一行：

```kotlin
import com.milan.game.ui.components.PortraitTarget
```

将 `AvatarCircle` 内的调用改为：

```kotlin
        PortraitImage(
            characterId = def.characterId,
            rarity = def.baseRarity,
            name = def.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            target = PortraitTarget.Thumb,
        )
```

- [ ] **Step 2: CharacterListScreen.kt — ListCard 加 Thumb**

确认 `import com.milan.game.ui.components.PortraitImage` 已存在，旁边加：

```kotlin
import com.milan.game.ui.components.PortraitTarget
```

将 `ListCard` 内的调用（原 171-176 行）改为：

```kotlin
                PortraitImage(
                    characterId = ch.save.characterId,
                    rarity = ch.rarity,
                    name = ch.name,
                    modifier = Modifier.fillMaxSize(),
                    target = PortraitTarget.Thumb,
                )
```

- [ ] **Step 3: GachaScreen.kt — 池角色预览（52dp）加 Thumb**

确认 `import com.milan.game.ui.components.PortraitImage` 已存在，旁边加：

```kotlin
import com.milan.game.ui.components.PortraitTarget
```

将池预览调用（原 261-266 行）改为：

```kotlin
                            PortraitImage(
                                characterId = entry.characterId,
                                rarity = entry.rarityIndex,
                                name = def?.displayName,
                                modifier = Modifier.size(52.dp).clip(CircleShape),
                                target = PortraitTarget.Thumb,
                            )
```

- [ ] **Step 4: GachaScreen.kt — GachaChip（42dp）加 Thumb**

将 `GachaChip` 内调用（原 521-526 行）改为：

```kotlin
        PortraitImage(
            characterId = r.characterId ?: "",
            rarity = r.rarity,
            name = r.characterName,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
            target = PortraitTarget.Thumb,
        )
```

- [ ] **Step 5: Verify it compiles**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/ui/home/HomeScreen.kt MilanKotlin/app/src/main/java/com/milan/game/ui/characters/CharacterListScreen.kt MilanKotlin/app/src/main/java/com/milan/game/ui/gacha/GachaScreen.kt
git commit -m "perf(ui): 立绘调用点分档（头像/列表/chip 共 4 处改 Thumb 采样）"
```

---

### Task 5: 首页名录头像区 Row → LazyRow（只组合可见项）

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/ui/home/HomeScreen.kt:341-370`（AvatarStrip 整函数替换）

- [ ] **Step 1: Replace AvatarStrip body**

将 `AvatarStrip` 整函数替换为：

```kotlin
@Composable
private fun AvatarStrip(onOpenCharacter: (String) -> Unit) {
    // LazyRow：只组合可见项 → 不可见头像不触发立绘解码（2026-08-08 低端机优化）
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(Picks) { (id, name, src) ->
            val def = GameState.service.characters.firstOrNull { it.characterId == id }
                ?: return@items
            Column(
                modifier = Modifier
                    .clickable { onOpenCharacter(id) }
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvatarCircle(def, Modifier.size(58.dp))
                Text(
                    text = name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Text1,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(text = src, fontSize = 8.sp, color = AppTheme.Text2)
            }
        }
    }
}
```

- [ ] **Step 2: Update imports**

imports 调整：
- 新增：`import androidx.compose.foundation.lazy.LazyRow`、`import androidx.compose.foundation.lazy.items`
- 删除：`import androidx.compose.foundation.horizontalScroll`、`import androidx.compose.foundation.rememberScrollState`（这两个函数在 HomeScreen.kt 中仅 AvatarStrip 使用；若编译报错说明别处也在用，改回保留）

- [ ] **Step 3: Verify it compiles**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/ui/home/HomeScreen.kt
git commit -m "perf(ui): 首页名录头像区改 LazyRow（只组合可见项）"
```

---

### Task 6: BGM 后台线程构建 ExoPlayer（不阻塞主线程）

**Files:**
- Modify: `MilanKotlin/app/src/main/java/com/milan/game/infrastructure/MilanAudio.kt:58-83`（playBgm/stopBgm 改造）

- [ ] **Step 1: Add import**

在文件顶部（现有 imports 之后）加：

```kotlin
import kotlin.concurrent.thread
```

- [ ] **Step 2: Replace playBgm and stopBgm**

将 `playBgm`（原 57-77 行）与 `stopBgm`（原 79-83 行）替换为：

```kotlin
    /**
     * 播放背景乐（循环）；同名重复调用不重启，先 stop 再播。
     * 2026-08-08 优化：ExoPlayer 构建（100-300ms）移入后台线程，不阻塞
     * MainActivity.onCreate 主线程；playBgm/stopBgm 经同步块串行化。
     */
    fun playBgm(name: String) {
        val c = appContext ?: return
        thread(name = "milan-bgm") {
            synchronized(this) {
                if (bgmName == name) return@thread
                stopBgmLocked()
                // 资源缺失检查：静默跳过，不创建空播放器
                try {
                    c.assets.open("audio/bgm/$name.ogg").close()
                } catch (_: Exception) {
                    return@thread
                }
                val player = ExoPlayer.Builder(c).build().apply {
                    setMediaItem(MediaItem.fromUri("asset:///audio/bgm/$name.ogg"))
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = bgmVolume
                    prepare()
                    play()
                }
                bgmPlayer = player
                bgmName = name
            }
        }
    }

    fun stopBgm() {
        synchronized(this) { stopBgmLocked() }
    }

    /** 已持锁内部实现；同步块可重入（Java monitor），release() 内调用 stopBgm 安全。 */
    private fun stopBgmLocked() {
        bgmPlayer?.release()
        bgmPlayer = null
        bgmName = null
    }
```

注意：`release()`（原 89-95 行）内调 `stopBgm()` 不变——synchronized 可重入，无需改动。

- [ ] **Step 3: Verify it compiles**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add MilanKotlin/app/src/main/java/com/milan/game/infrastructure/MilanAudio.kt
git commit -m "perf: BGM 后台线程构建 ExoPlayer（playBgm/stopBgm 同步化，不阻塞主线程）"
```

---

### Task 7: 全量验证（单测 + 构建）

**Files:** 无（验证任务）

- [ ] **Step 1: Run full unit test suite**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL，全部测试通过（既有 8 个测试类 + 新增 PortraitLoaderTest 6 用例；若出现既有测试失败，先排查是否本计划改动引起——本计划未动 domain/data/service，理论上不应影响）

- [ ] **Step 2: Build release APK**

Run: `.\gradlew.bat :app:assembleRelease`
Expected: BUILD SUCCESSFUL，产物 `MilanKotlin/app/build/outputs/apk/release/app-release.apk`

- [ ] **Step 3: Summary commit（如有计划外小修）**

若 Step 1/2 有需要修正的编译/测试问题，修正后统一提交；无则跳过本步。

---

## 真机验证清单（设备上线后，阻塞项）

设备 `3B15AB01H4V00000` 当前离线；上线后按 spec §6 执行：
1. 安装 release APK → 启动 → 面包屑出现 `home.oncreate.done`（此前缺失的首帧完成标记）。
2. `adb logcat -d -s Choreographer`：无长主线程阻塞（跳帧大幅减少）。
3. `adb shell dumpsys meminfo com.milan.game`：立绘内存峰值回落（27MB → <3MB）。
4. 截图确认立绘正常显示、占位→立绘过渡正常（无模糊/变形——Crop 后 416px 宽在 ~1080p 屏上放大显示，若观感模糊则把 Full 档 sample 从 2 改为 1，代价是内存翻倍，需权衡）。
5. 崩溃取证复核：`adb logcat -b crash` + `adb pull /sdcard/Android/data/com.milan.game/files/crash/`，验证 spec §1 推断（首帧组合未完成 → 卡顿退出）。
