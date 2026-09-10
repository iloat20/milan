> ⚠️ **已废弃（2026-09-09）**：本文件为历史方案，仅作参考勿再执行。视觉规范已由 [docs/superpowers/design-language/00-design-language-v3.md](../superpowers/design-language/00-design-language-v3.md)（「丹青典藏 · Gilded Codex」v3）取代。

# Milan 视觉审美提升 — 暗夜神性·诸神黄昏 设计文档

> **版本**: v1.0 | **日期**: 2026-08-04 | **状态**: 设计定稿（已与用户确认方向）
> **北极星**: 暗夜神性·诸神黄昏（Twilight of Gods / 诸神黄昏·东方）
> **范围**: 全量统一收口（6 界面 + 全局组件 + Token 收口，分阶段）
> **立绘策略**: 原图保留，显示层统一色调处理

---

## 0. 设计哲学

### 核心命题
把现有「Obsidian & Gold」体系**拔高**为更克制、更电影感、唯一可溯源的视觉语言。
关键词：**克制的金 · 电影纵深 · 唯一调色板 · 节律化的字与动**。

### 四条语言级转变（本次改造的本质）
1. **克制的金** — 金从"大面积填充"降级为"线 / 点 / 字"的稀有强调；面一律用深紫黑玻璃承担。这是去俗气、显高级的关键。
2. **电影纵深** — 单一背光源 + 暗角 + 多层径向辉光，让立绘"浮"在光里，而不是贴在平面上。
3. **唯一调色板** — 删除 `UIHelper.cs` 中 cosmic dark / 中西融合 / Obsidian&Gold / Cosmic Nebula 四套重叠定义；世界主题改为同一套的**变体**，不再各自另起炉灶。
4. **节律化的字与动** — 固定字号阶梯 + 字重层级 + 统一缓动时长 + 入场编排，消除当前各界面"各写各的"的散乱感。

---

## 1. 统一调色板（唯一来源）

所有颜色从下表取值，禁止在 Activity 内 `Color.ParseColor` 硬编码（现有散落硬编码需在 Phase A 收口时改为引用 Token）。

| Token | 值 | 用途 |
|-------|-----|------|
| `--void` BgDeepest | `#0B0612` | 全局最底背景 |
| `--night` BgMid | `#160A26` | 次级背景 / 分区 |
| `--surface` | `#1E0E33` | 玻璃面底色（α 叠加） |
| `--gold` | `#E8B84B` | 熔金主色（线/点/字/当期强调） |
| `--gold-hi` | `#FFC857` | 熔金高光 |
| `--gold-deep` | `#C9962E` | 熔金收尾 / 按钮底边 |
| `--frost` | `#7FC4FF` | 霜蓝（次级操作 / 信息 / 导航图标） |
| `--frost-deep` | `#4A90D9` | 霜蓝深 |
| `--violet` | `#9A6BFF` | 暮紫（点缀 / 分隔 / 天赋节点 / 裂隙） |
| `--text-1` | `#F3ECFF` | 主文字 |
| `--text-2` | `#B7A6CF` | 次文字 |
| `--text-3` | `#6E5C8A` | 弱化 / 占位 |
| success / warn / danger | `#35D07F` / `#FFB020` / `#FF4D5E` | 语义色 |

**稀有度色（沿用，仅 UR 微调为熔金高光）**：R `#E8E2F2` / SR `#7FC4FF` / SSR `#C79BFF` / UR `#FFC857`。

### 废弃清单（Phase A 删除或折叠）
- `AppTheme.Background/Surface/SurfaceRaised/Accent/AccentAlt/Gold/...`（cosmic dark 旧系）
- `AppTheme.Jade/Vermilion/SealRed/Ink/CloudBlue`（中西融合母题，仅保留 SealRed 用于印章点缀，其余移入 WorldTheme 变体）
- `AppTheme.PanelGlass/PanelGlassLight/PanelBorder/PanelBorderGold/GoldPrimary/...`（Obsidian&Gold → 用 Twilight 同名重启）
- `AppTheme.CosmicBg*/CosmicPrimary*/...`（Cosmic Nebula → 折叠为 Twilight 的"星穹变体"）
- 保留：`Twilight*` 全部、`RarityColor`、`WorldTheme`（改为 Twilight 变体）、`ColorLong/ColorLongs` 铁律不变。

---

## 2. 设计 Token 收口策略（代码层）

**文件**：`MauiMilan/UI/UIHelper.cs`（`AppTheme` 静态类）。

- 升格 `Twilight*` 为**主调色板**，重命名为语义化主名（`BgDeepest/BgMid/Surface/Gold/GoldHi/GoldDeep/Frost/FrostDeep/Violet/Text1/Text2/Text3`），旧 `Twilight*` 别名保留做 `Obsolete` 兼容或一次性重映射。
- 删除 §1 废弃清单中的重叠定义；各 Activity 内 `Color.ParseColor` 硬编码改为引用新 Token（grep 全仓 `Color.ParseColor` + `AppTheme.` 旧名，逐处替换）。
- `GlassPanel(radius, gold, nested)`：
  - 底色改用 `Surface` 半透明（默认 α≈0.55），叠加顶部 1px 内高光（白 α10%）；
  - `gold=true` 时描边用 `Gold` 发丝线（α45%），**仅选中/当期 UP 使用**；
  - 默认描边改为 `rgba(255,255,255,0.08)` 而非金线（金不再默认铺满）。
- `TitleWithOrnament`：金线降至 1px、菱形缩小、可选项 `frost` 副色；字距收紧。
- `RarityColor`：UR 改为 `#FFC857`（熔金高光），其余不变。

---

## 3. 全局组件刷新

| 组件 | 当前 | 改造 |
|------|------|------|
| 背景 | `CosmicBackground`（宇宙星穹） | **`TwilightBackground`**：深紫夜底 + 缓慢漂移星云 + 顶部熔金背光辉光 + 偶发星点；性能沿用既有渐变缓存（optimization-review P1） |
| 玻璃面板 | `UI.GlassPanel` | 见 §2，金线仅选中态 |
| 标题饰线 | `UI.TitleWithOrnament` | 1px 金线 + 小菱形 + 可选霜蓝副线 |
| 按钮 | `UI/ThemeButtons.cs` | 主=熔金渐变+发丝高光+按压回弹；次=霜蓝描边霓虹；危险=红；统一 `TapFeedback` |
| 底部导航 | `UI/GameNavBar.cs` | 霜蓝图标 + 选中金色下划线/光点；背景毛玻璃；去除满铺金 |
| 顶栏/资源栏 | `AppChrome` | 统一 twilight 玻璃；货币 pill 星尘=金 / 钻石=霜蓝 |
| 立绘色调 | （无） | **新增 `PortraitGrade`**（见 §4） |

---

## 4. 立绘统一色调处理（显示层）

原 20 张 PNG 不动，在**显示层**叠加统一色调，使原图融入 twilight 世界：

- **冷调去饱和**：叠加 `ColorMatrix` 降低饱和度 ~15%、轻微偏冷（蓝 + / 红 −）。
- **暗角（vignette）**：径向 `transparent → rgba(11,6,18,.85)`，让立绘边缘沉入夜色。
- **边缘光（rim light）**：从主光源侧（左/上）叠一层熔金/霜蓝低透明度高光，呼应"背光"母题。
- **细噪点（grain）**：极低透明度噪点纹理，去数码感、增胶片质感。
- 实现：新增 `PortraitGradeDrawable : LayerDrawable` 或在 `PortraitView/AnimatedPortrait/Parallax3DPortraitView` 的 `OnDraw` 末层叠加；复用既有 `OnTrimMemory` LRU 释放策略，不增加内存风险。
- 强度档位：默认"标准"（样张浓度）；可在设置页预留开关（轻/标准/重=破壁）。

---

## 5. 字体与字号系统

- 字阶（sp）：10 / 12 / 14 / 16 / 20 / 24 / 32（固定阶梯，禁止任意值）。
- 字重：标题 Heavy(700) / 正文 Regular(400) / 数字等宽（Roboto Mono / DIN 风格）。
- 层级：页面标题 24+Heavy · 区块标题 20+Heavy · 正文 14 · 辅助 12 · 微标 10。
- 当前各界面字号乱、字重不一致 → 统一引用 `UI.Text(..., sp, color, bold)` 的受限 sp 集合。

---

## 6. 动效系统

- 缓动统一：`cubic-bezier(.2,.8,.2,1)`（ease-out 快出）。
- 时长：微交互 150ms · 转场 300ms · 强调 500ms · 抽卡演出 1500ms（沿用既有时长规范）。
- 入场编排（Home/Gacha 等）：背景淡入 → 立绘淡入+上浮 12dp → UI 层叠加错落（各元素 stagger 60ms）→ 导航落位。
- 按压/悬停：统一 `UI.TapFeedback` 缩放回弹（已存在，全量替换裸 `OnTouchListener`）。
- 常驻动画渐变缓存沿用 optimization-review P1–P5，避免每帧分配。

---

## 7. 六界面提升要点

| 界面 | 当前 Activity | 提升要点 |
|------|--------------|---------|
| 主页 | `HomeActivity` | `TwilightBackground` 背光辉光；立绘 `PortraitGrade` + 呼吸；三导航卡玻璃化 + 选中金下划线；去除满铺金 |
| 抽卡 | `GachaActivity` | 裂缝演出升级为"暮紫裂隙 + 熔金闪"；结果卡金线边框按稀有度（复用 `RarityColor`）；保底计数器霜蓝；武器演出维持既有下层逻辑 |
| 角色列表 | `CharacterListActivity` | 2 列玻璃卡网格；稀有度发丝边；hover 微浮；筛选栏 `ListFilterBar` 套 twilight |
| 角色详情 | `CharacterDetailActivity` | 立绘大图 `PortraitGrade`；标签页金线；天赋树暮紫节点；SSR/UR 武器面板金线闸门维持 |
| 收藏图鉴 | `CollectionActivity` | 进度条熔金渐变；未拥有剪影加冷调；已拥有套 `PortraitGrade` |
| 360° 检视 | `InspectionActivity` | 全屏立绘 `PortraitGrade` + 世界背景 twilight 变体 + 旋转光轨（暮紫/霜蓝） |

---

## 8. 分阶段实施路线

- **Phase A — Token 收口（地基）**：`UIHelper.cs` 升格 Twilight 为主、删 4 套重叠、`GlassPanel/TitleWithOrnament` 改造、全仓硬编码替换为 Token、`PortraitGrade` 新组件、`TwilightBackground` 替代 `CosmicBackground`。→ 构建 0 错 0 警。
- **Phase B — 全局组件**：`ThemeButtons`、`GameNavBar`、`AppChrome` 顶栏/资源栏套 twilight；字阶统一。
- **Phase C — 门面（Home + Gacha）**：落地 §7 两界面，含入场编排与抽卡演出升级。
- **Phase D — 其余四界面**：列表/详情/图鉴/检视统一。
- **Phase E — 动效打磨 + 走查**：统一缓动、stagger、真机视觉走查，修正偏差。

每阶段结束都必须 `--no-incremental` Release 构建 **0 错误 0 警告**（沿用既有关卡）。

---

## 9. 验收标准

- 构建：每阶段 `--no-incremental` Release 0 错误 0 警告。
- 一致性：全仓不再出现 §1 废弃清单中的旧 Token；无 `Color.ParseColor` 硬编码（除 `AppTheme` 定义处）。
- 视觉：六界面统一 twilight 调性；金仅作用于线/点/字与选中态；立绘融入暗角背光；入场动效一致。
- 稳定性：维持既有铁律 —— `ColorLong` 渐变、`OnDraw` 空尺寸/半径保护、`OnTrimMemory` 释放、AOT 关闭、沙箱外构建。

---

## 10. 风险与注意

- **不可破坏既有稳定性铁律**：Shader 渐变必须用 `long[]`（`UI.ColorLong/ColorLongs`）；`OnDraw` 开头 `if(w==0||h==0) return;` + `RadialGradient` 半径保护；武器图 LRU + `OnTrimMemory`；Release 必须 `-p:RunAotCompilation=false`；非沙箱构建。
- **回归面**：改 `AppTheme` 静态属性会被 10+ Activity 引用，Phase A 替换后必须全量构建验证。
- **性能**：`TwilightBackground` / `PortraitGrade` 必须复用既有渐变缓存与 LRU，避免每帧分配与内存上涨。
- **范围边界**：本次只动视觉语言与显示层；不改游戏逻辑、抽卡概率、养成数值、存档结构。

---

## 11. 附录：参考样张

`twilight-home-mockup.html`（工作区根目录）— Home 调性样张，已与用户确认方向（克制的金 / 电影纵深 / 唯一调色板）。后续各界面可在该框架内延展。
