# Milan 主题重做规范 — 诸神黄昏·东方 (Twilight of Gods)

> **日期**: 2026-08-01 | **状态**: 设计原型定稿（见 `docs/prototype/homepage-twilight.html`）→ 待代码落地与立绘重绘
> **来源提示词**: 重新设计游戏首页 UI（压缩留白、立绘 3D/漂浮/光效、统一头像、立绘依据背景故事、主题由"次元裂缝"改为中西融合）
> **融合要素**: 山海经 + 中国上古神话 + 希腊神话（含现有超英融合阵容）

---

## 1. 主题身份（Theme Identity）

| 项 | 值 |
|----|----|
| 中文名 | **诸神黄昏 · 东方** |
| 英文名 | **TWILIGHT OF GODS** |
| 一句话 | 东西方神系在末世交汇、对峙而共生 |
| 氛围 | 悲壮、史诗、戏剧张力强 |
| 替代旧值 | `次元裂缝`（HomeActivity 资源栏副标题、`CLAUDE.md` 中"dimensional rift"定位） |

> 后续扩池建议引入真正希腊神祇（宙斯/雅典娜/哈迪斯/阿波罗）以补齐"希腊神话"权重；现阵容以"超英融合"间接呈现希腊脉络，暂保留。

---

## 2. 视觉配色 Token（落地到 `UI/UIHelper.cs` 的 `AppTheme`）

```csharp
// —— 诸神黄昏·东方 主题 ——
public static Color BgDeepest = Color.ParseColor("#0B0612"); // 暗紫夜·最深
public static Color BgMid     = Color.ParseColor("#160A26"); // 暗紫夜·中
public static Color PanelGlass= Color.ParseColor("#1E1033"); // 玻璃面板（带 alpha 使用）
public static Color GoldPrimary = Color.ParseColor("#E8B84B"); // 熔金
public static Color GoldBright  = Color.ParseColor("#FFC857"); // 熔金高光
public static Color Frost      = Color.ParseColor("#7FC4FF");  // 霜蓝
public static Color FrostDeep  = Color.ParseColor("#4A90D9");  // 霜蓝深
public static Color Twilight   = Color.ParseColor("#9A6BFF");  // 暮紫（点缀/分隔）
public static Color TextPrimary  = Color.ParseColor("#F3ECFF");
public static Color TextSecondary= Color.ParseColor("#B7A6CF");

// 稀有度配色（重新校准到黄昏统一调性）
public static Color RarityUR  = Color.ParseColor("#FFC857"); // 熔金
public static Color RaritySSR = Color.ParseColor("#C79BFF"); // 暮紫（原紫提亮）
public static Color RaritySR  = Color.ParseColor("#7FC4FF"); // 霜蓝
public static Color RarityR   = Color.ParseColor("#E8E2F2"); // 苍白
```

`AppTheme.RarityColor / RarityName` 维持枚举入口不变，仅改返回值。

---

## 3. 布局规则（首页留白压缩 & 比例优化）

对照当前 `HomeActivity.cs`：

| 元素 | 现状 | 目标 |
|------|------|------|
| 主体上 padding | `UI.Dp(38)`（状态栏区） | `UI.Dp(12)` |
| 主视觉高度 | `BuildHero(UI.Dp(230))` | `BuildHero(UI.Dp(430))` ≈ 屏高 55% |
| 功能入口 | 2 列网格 `BuildEntryGrid`（7 张卡片，大留白） | **移除**，改为单行「诸神名录」头像横滑 + 背景故事提示条 |
| 主视觉下方按钮 | 「前往召唤 / 查看角色」 | 「前往召唤 / 神谱图鉴」，间距收紧 |
| 资源栏副标题 | `次元裂缝` | `诸神黄昏 · 东方` / `TWILIGHT OF GODS` |

> 原则：**立绘为唯一视觉焦点**，移除一切与立绘争夺注意力的网格块；头像横滑承载导航，背景故事条承担叙事。

---

## 4. 立绘 3D 感 / 漂浮 / 光效（代码可落地部分）

原型用 CSS 演示了四类效果，对应 Android 实现方式：

1. **漂浮 float** — `PortraitView` 增加 `ObjectAnimator`/`ValueAnimator`：translationY 0→-12dp→0，6s 循环，配轻微 rotation。
2. **稀有度光晕脉动 halo** — 立绘背后叠一层 `RadialGradient` 径向光（颜色取 `RarityColor`），alpha 0.5→0.95 + scale 1→1.08，4.5s 循环。
3. **rim-light 扫光** — 顶层 `View` 用 `LinearGradient` 斜向高光带，`screen` 混合 + translateX 来回扫（5.5s）。
4. **伪 3D 视差** — `PortraitView` 监听 `Hover/点击拖拽`：按指针位移做 `rotationY/rotationX`（±10°），增强立体感（无重绘也能见效）。

> 以上 1–4 在**现有立绘**上即可生效，是"不重绘资源也能升级观感"的关键收益。
> **真正 3D 立体感 + 景深 + 体积光**仍需下一阶段重绘立绘（2:3 透明底、带体积光与主光方向）。

---

## 5. 统一头像系统（新组件 `UI/UnifiedAvatarView.cs`）

替代各界面现有的方形卡头像，全游戏复用：

- 圆形裁切（`CircleOutlineProvider` 或 `clipToOutline` + `Outline`）
- 外环：`conic-gradient` 等效——用 `SweepGradient` 画稀有度渐变环（UR 熔金 / SSR 暮紫 / SR 霜蓝 / R 苍白），缓慢旋转
- 内描边：`inset` 暗色描边增强对比
- 尺寸：列表 56dp、抽卡结果 72dp、图鉴 48dp

复用点：CharacterList、Gacha 结果网格、Collection、首页「诸神名录」横滑、详情页头像。

---

## 6. 立绘依据背景故事（美术方向约束）

每个角色立绘**必须**回溯其神话原型，禁止脱离设定的"通用帅哥美女"。原型已沉淀在 `docs/superpowers/art-direction/character-designs-*.md`，重绘时在其基础上叠加「诸神黄昏·东方」的统一光照与 3D 要求。示例映射（原型已验证）：

| 角色 | 神话原型 | 立绘关键符号 |
|------|----------|--------------|
| 烛龙 Zhulong (UR) | 山海经·人面蛇身，睁眼为昼闭眼为夜 | 半人半蛇、异色瞳（左金日/右银月）、凤凰火翼 |
| 刑天 Xingtian (UR) | 中国神话·断首以乳为目以脐为口 | 无头机甲、胸口光学目、干（盾）戚（斧） |
| 凤凰 Fenghuang (SSR) | 山海经·五色而文，涅槃重生 | 五色焰发、火焰莲台、合十凤凰之火 |
| 毕方 Bifang (SR) | 山海经·火鸟 | 单足、火焰羽、风助火势 |
| 精卫 Jingwei (SR) | 中国神话·填海鸟 | 衔石、海浪、执念之翼 |
| 雷神 Leishen (SSR) | 山海经·龙身人头腹中雷鸣 | 雷鼓腹甲、龙角、妙尔尼尔锤 |

---

## 7. 下一阶段重绘：示例生图 Prompt（烛龙，已注入主题光照）

在原有 `character-designs-UR.md` 的 Prompt 基础上，追加 3D / 黄昏光照 / 漂浮感约束：

```
Full-body character portrait of Zhulong, ancient Chinese dragon-god of day and night,
reimagined for "Twilight of Gods": half-human upper body with muscular build and a massive
serpent tail, heterochromatic eyes (left golden sun / right silver moon). Volumetric 3D
rendering with deep depth-of-field, dramatic rim light from upper-left in molten gold
(#FFC857), cool frost-blue (#7FC4FF) fill from below, swirling phoenix-fire wings with
semi-transparent embers drifting upward (floating effect). Twilight atmosphere: dark
violet-night background split between daylight mountains and starry void, divided by a
glowing rift. Gacha game key visual, transparent background, 2:3 ratio, ultra detail,
cinematic god-ray lighting.
```

> 该模板（原始原型 + 体积光 + 黄昏配色 + 漂浮粒子）适用于全部 25 张立绘与对应头像重绘。

---

## 8. 落地阶段（实现顺序）

1. **原型确认**（本步）— HTML 原型 + 本规范，与用户对齐方向。
2. **代码重品牌**（不重绘资源）：
   - `UIHelper.AppTheme` 注入 §2 token；`ThemeButtons` 熔金/霜蓝化。
   - `HomeActivity`：改主题名、压留白、放大主视觉、入口网格→头像横滑+故事条。
   - `PortraitView`：加漂浮/光晕/扫光/伪3D。
   - 新增 `UnifiedAvatarView` 并在各列表接入。
3. **立绘重绘**（出图，消耗积分，分批）：
   - 第 1 批：首页 featured + 抽卡 featured 的 4–5 张 UR/SSR（验证新美术方向）。
   - 第 2 批：其余 SSR/SR/R 共约 20 张 + 全套头像。
4. **跨界面统一**：Gacha 演出、CharacterList、Collection、Detail 一并切到黄昏主题与统一头像。

---

## 9. 待用户确认 / 开放问题

- [ ] 主题名「诸神黄昏·东方」是否定稿？或偏好「万界神约 / 山海异闻录」方向？
- [ ] 第二阶段是否立即执行代码重品牌（复用现有立绘 + 新光照）？
- [ ] 希腊神权重：保持现状（超英融合）还是计划真正引入宙斯/雅典娜等？
- [ ] 立绘重绘是否委托我在第三阶段批量出图（将消耗较多积分）？
