# Milan 低端机性能优化 · 设计规格

> 日期：2026-08-08 · 状态：草案（已获用户全部确认 §1-§6，待实施）

针对 Kotlin/Compose 版（`MilanKotlin/`）真机启动疑似崩溃/卡顿的性能优化：立绘采样解码 + 首屏异步化。
范围：**§2 核心 `PortraitImage` 改造 + §3 配套（AvatarStrip LazyRow、BGM 异步）+ §4-§6 边界/测试/不做清单**。
不动领域层、存档序列化、事件总线；不引入任何新依赖。

---

## 1. 现状问题（动机）

**崩溃证据链**（真机面包屑，2026-08-08 09:43）：`=== boot ===` → `app.oncreate` → `content.load.fallback` → `app.init.done` → `main.onCreate`(09:43:30.649) → `main.onCreate`(09:43:35.073)。推断：
- 无第二个 boot 头 = **同一进程 Activity 重建**（非进程死亡）；无托管异常 = 排除 Java/Kotlin 未捕获异常。
- 无 `home.oncreate.done` = **首帧组合未完成**；最可能是首屏组合期同步解码 7 张立绘 + 102MB APK 首次 IO + 浮动动画 → 低端机（2-3GB、Android 10-12）卡顿假死 → 用户退出重开。native 崩溃 / LMK / ANR 概率低（都会开新轮）。

| # | 问题 | 位置 |
|---|---|---|
| P1 | 首屏同步解码 7 张立绘：组合主线程 `painterResource` 同步 decode，每张 832×1186 全尺寸 ≈ 3.8MB 内存 | [PortraitImage.kt:44](MilanKotlin/app/src/main/java/com/milan/game/ui/components/PortraitImage.kt:44) |
| P2 | 58dp 头像也解码全尺寸 832×1186（3.8MB/张，显示仅需 208×296） | [AvatarStrip](MilanKotlin/app/src/main/java/com/milan/game/ui/home/HomeScreen.kt:341) |
| P3 | `AvatarStrip` 用 `Row + horizontalScroll` 全量渲染（含不可见项） | HomeScreen.kt:341 |
| P4 | `playBgm` 在 MainActivity.onCreate 主线程同步构建 ExoPlayer（低端机 ~100-300ms 阻塞） | [MilanAudio.kt:58](MilanKotlin/app/src/main/java/com/milan/game/infrastructure/MilanAudio.kt:58) |
| P5 | 卡顿期间无任何用户反馈（黑屏/冻结数秒） | 首屏组合期 |

**已验证事实**：28 张立绘全部 832×1186、8bit、解码约 3.8MB/张——无超大图，排除单图 OOM 炸弹；`PortraitImage` 已用 `remember(characterId)` 缓存 `getIdentifier` 探测结果（无重复探测问题）。

---

## 2. 决策记录

| # | 决策 | 内容 | 状态 |
|---|---|---|---|
| D1 | 技术路线 | **方案 A：手写采样解码**（`BitmapFactory` + `inSampleSize` + 子线程 + LruCache），否决 Coil（新依赖）与多尺寸资源（维护成本高、仅部分收益） | ✅ 已确认 |
| D2 | 采样档位 | `Full` = inSampleSize 2 → 416×593 ≈ 0.95MB；`Thumb` = inSampleSize 4 → 208×296 ≈ 0.24MB。原则：采样后像素 ≥ 显示像素 ×2（Retina 余量） | ✅ 已确认 |
| D3 | 缓存 | 进程级 `LruCache<Pair<Int resId, Int sample>, ImageBitmap>`，`sizeOf = byteCount`，上限 **24MB**（低端机友好） | ✅ 已确认 |
| D4 | AvatarStrip | `Row + horizontalScroll` → `LazyRow`（不可见项不组合 → 不解码） | ✅ 已确认 |
| D5 | BGM | ExoPlayer 构建移入后台 `thread`；`playBgm/stopBgm` 加 `synchronized` 防竞争；MainActivity 调用点不变 | ✅ 已确认 |
| D6 | 不做 | 不引入图片库、不做磁盘缓存、不做多尺寸资源、不动领域层/存档/事件总线、不改 GachaScreen 抽卡动画逻辑 | ✅ 已确认 |

---

## 3. 架构分层（改动落点）

| 改动 | 文件 | 层 |
|---|---|---|
| 采样率计算 + 缓存策略（纯 Kotlin，可单测） | 新 `ui/components/PortraitLoader.kt` | UI（无 Android 依赖部分） |
| Android 接入：异步解码 + 缓存读写 | `ui/components/PortraitLoader.kt`（`PortraitLoader` 加载函数） | UI |
| `PortraitImage` 采样档位参数 + 异步渲染 | [PortraitImage.kt](MilanKotlin/app/src/main/java/com/milan/game/ui/components/PortraitImage.kt:28) | UI |
| AvatarStrip → LazyRow | [HomeScreen.kt:341](MilanKotlin/app/src/main/java/com/milan/game/ui/home/HomeScreen.kt:341) | UI |
| BGM 异步化 | [MilanAudio.kt:58](MilanKotlin/app/src/main/java/com/milan/game/infrastructure/MilanAudio.kt:58) | 基础设施 |
| 采样率计算 + 缓存逐出单测 | 新 `app/src/test/java/com/milan/game/ui/PortraitLoaderTest.kt` | 测试 |

**约束（红线重申）**：`domain/`、`data/`（模型）禁止 `import android.*` —— `PortraitLoader` 的纯 Kotlin 部分（采样率、缓存键、容量策略）与 Android 部分（`BitmapFactory`、`Resources`）必须分离，纯逻辑不碰 Android 类；不动 `@Serializable` 结构；不引入新依赖。

---

## 4. 详细设计

### 4.1 `PortraitLoader`（新文件 `ui/components/PortraitLoader.kt`）

> 仿照 C# PortraitLoader 语义：宁可难看也不能崩。职责 = 采样参数计算 + 缓存管理 + 异步解码。

**纯 Kotlin 部分（可单测，不 import android.\*）：**

```kotlin
/** 采样档位（D2）。 */
enum class PortraitTarget(val sample: Int) {
    Full(2),   // 416×593 ≈ 0.95MB（全屏 Hero/详情/养成/抽卡结果）
    Thumb(4),  // 208×296 ≈ 0.24MB（58dp 头像/列表项）
}

/**
 * 采样率计算：请求目标尺寸 >= 原图尺寸 → 1；否则按 2 的幂下采样到 >= 需求（BitmapFactory 文档算法）。
 * 注：当前按档位静态采样（显示尺寸在组合期不可得），此函数保留给未来精确模式与单测。
 */
fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int

/** 缓存键：资源 ID × 采样档位（同一资源不同档位互不驱逐）。 */
data class PortraitKey(val resId: Int, val sample: Int)

/** 缓存容量策略：按字节计（byteCount），上限 PORTRAIT_CACHE_BYTES = 24MB。 */
const val PORTRAIT_CACHE_BYTES = 24 * 1024 * 1024
```

**Android 部分：**

```kotlin
object PortraitLoader {
    private val cache = object : LruCache<PortraitKey, Bitmap>(PORTRAIT_CACHE_BYTES) {
        override fun sizeOf(key: PortraitKey, value: Bitmap) = value.byteCount
    }

    /** 异步解码：命中缓存直接返回；未命中 IO 线程 decode + 入缓存；失败返回 null（调用方走占位）。 */
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

要点：
- `decodeResource` 线程安全（Resources 可跨线程读）；`LruCache` 本身线程安全。
- **OOM 双保险**：采样（单图内存降 16 倍）+ 缓存上限（24MB 封顶自动逐出）。
- 解码失败/损坏资源 → `runCatching` 吞掉返回 null → 调用方保持占位，绝不崩溃。

### 4.2 `PortraitImage` 改造

```kotlin
@Composable
fun PortraitImage(
    characterId: String,
    rarity: Int,
    name: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    target: PortraitTarget = PortraitTarget.Full,   // 新增：采样档位
) {
    val context = LocalContext.current
    val portraitId = remember(characterId) {
        context.resources.getIdentifier(characterId, "drawable", context.packageName)
    }
    var bitmap by remember(portraitId, target) { mutableStateOf<Bitmap?>(null) }

    // 探测成功后异步解码；解码期间渲染占位（渐变+首字），完成后替换。
    LaunchedEffect(portraitId, target) {
        if (portraitId != 0) bitmap = PortraitLoader.load(context.resources, portraitId, target)
    }

    val bmp = bitmap
    if (portraitId != 0 && bmp != null) {
        Image(bitmap = bmp.asImageBitmap(), contentDescription = name ?: characterId,
              modifier = modifier, contentScale = contentScale)
    } else {
        PortraitFallback(characterId, rarity, name, modifier)   // 占位逻辑不变（P5 启动反馈）
    }
}
```

- **数据流**：探测（remember 缓存）→ `LaunchedEffect` + `Dispatchers.IO` 解码 → 占位 → `Image` 替换。
- **启动反馈（P5）**：首帧立即渲染占位（渐变+首字），立绘异步填充——低端机「占位 → 立绘」有即时反馈，不再冻结。
- 失败路径：`portraitId == 0`（无资源）或 `load` 返回 null（解码失败）→ 均走 `PortraitFallback`。

### 4.3 调用点分档（8 处）

| 调用点 | 档位 | 理由 |
|---|---|---|
| HomeScreen HeroPortrait（全屏大图） | Full | 显示 ~430dp 高，2x 采样足 |
| HomeScreen AvatarCircle（58dp） | Thumb | 显示 58dp，4x 采样足 |
| CharacterListScreen ListCard 立绘框（58dp） | Thumb | 58dp 头像 |
| CharacterDetailScreen 全屏立绘 | Full | 全屏显示 |
| ProgressionScreen 全屏立绘 | Full | 全屏显示 |
| GachaScreen 池角色预览（52dp） | Thumb | 52dp 圆形头像 |
| GachaScreen 抽卡 reveal 结果大图 | Full | 全屏展示 |
| GachaScreen GachaChip（42dp） | Thumb | 42dp 小立绘 |

### 4.4 AvatarStrip → LazyRow（P2/P3）

- `Row + horizontalScroll` → `LazyRow` + `items(Picks)`；Picks 共 6 项，不可见项不组合 → 其 `PortraitImage` 不触发解码。
- 首屏仅解码可见 2-3 个头像（每张 0.24MB）。
- 滚动语义不变（LazyRow 原生可横滑）。

### 4.5 BGM 异步化（P4）

- `MilanAudio.playBgm`：ExoPlayer 构建（`ExoPlayer.Builder(c).build()` + `setMediaItem` + `prepare`）移入 `thread { }`；`play()` 保持。
- `playBgm`/`stopBgm` 加 `synchronized`（后台构建与 UI 线程 release 防竞争）。
- `MainActivity.onCreate` 调用点不变（`playBgm` 内部异步）。

---

## 5. 错误处理与边界

| 场景 | 行为 |
|---|---|
| 资源不存在（`getIdentifier == 0`） | 占位（现状不变） |
| 解码失败 / 损坏资源 | `runCatching` → null → 占位，绝不崩 |
| 缓存 OOM / 容量压力 | LruCache 自动逐出 + 24MB 上限双保险 |
| 缓存键冲突 | `(resId, sample)` 组合键，不同档位互不干扰 |
| 快速重进页面 | `remember` 键一致 → 跳过重复加载；缓存命中 → 立即可用 |

---

## 6. 测试与验证

**单测**（新 `PortraitLoaderTest.kt`，JUnit4 惯例，只测纯 Kotlin 部分）：
- `computeInSampleSize` 边界：需求 ≥ 原图 → 1；需求远小于原图 → 按 2 的幂；等比例边界。
- `PortraitKey` 唯一性：同 resId 不同 sample 为不同键。
- 缓存容量逐出：超 24MB 上限后最久未用被逐出（LruCache 语义）。

**构建**：`.\gradlew.bat :app:assembleRelease` + `.\gradlew.bat :app:testDebugUnitTest` 全绿。

**真机验证**（设备上线后）：
1. 安装新 APK → 启动 → 面包屑出现 `home.oncreate.done`（此前缺失的标记，证明首帧组合完成）。
2. `adb logcat -d -s Choreographer` 观察无长主线程阻塞（跳帧大幅减少）。
3. `adb shell dumpsys meminfo com.milan.game` 确认立绘内存回落（峰值 27MB → <3MB）。
4. 截图确认立绘正常显示、占位→立绘过渡正常。
5. 顺带补崩溃取证复核：`adb logcat -b crash` + `adb pull /sdcard/Android/data/com.milan.game/files/crash/`（验证 §1 推断）。

---

## 7. 明确不做（YAGNI）

- 不引入 Coil/Glide（零依赖传统，D1 已否决）。
- 不做磁盘缓存（asset 本地读取已够快）。
- 不做多尺寸资源（方案 C 否决）。
- 不动领域层/存档/事件总线；不改 GachaScreen 抽卡动画逻辑（只受益于统一优化）。
- 不新增加载态 UI/动画（占位→立绘替换即反馈，过度设计不做）。
