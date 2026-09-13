# 东方新中式 · 水墨进阶（设计语言 v4）

> 2026-09 重做。替换 v3.1「织环 · Ringweave」的玄墨+金箔 chrome 过密方案。
> 问题诊断：颜色发闷、金线描边滥用、按钮斜切+环痕过载、导航选中态过重、标题浮动动画噪音、信息挤。

## 定位

界面是**夜色中的砚台与宣纸**——安静、精密、有分寸的东方博物馆，不是环痕装饰展。
立绘是唯一主角；chrome 退后；朱砂只给行动，金箔只给稀有度。

## 色彩 Token（AppTheme 权威）

| Token | Hex | 用途 |
|-------|-----|------|
| BgDeepest | `#0E100F` | 页面底（微青墨，非纯黑） |
| BgMid | `#161918` | 卡片/面板 |
| SurfaceNested | `#1E2220` | 嵌套面 |
| Surface | `#FFFFFF0D` | 浮层玻璃 |
| ZhuSha | `#C4453A` | **主 CTA / 行动**（原 Gold 主强调退位） |
| ZhuShaHi | `#E06A5A` | 朱砂高光/按下 |
| Gold | `#C9A96A` | **仅稀有度/珍贵标识**，不进按钮 chrome |
| GoldDeep | `#A88848` | 金箔收尾 |
| Frost | `#6B9E92` | 次级操作（青瓷） |
| Violet | `#8A7AB8` | 点缀 |
| Text1 | `#E8E4DC` | 主文字（宣纸暖白） |
| Text2 | `#A0A39C` | 次文字 |
| Text3 | `#6A6E68` | 弱化 |
| Stroke | `#FFFFFF1A` | 发丝线（极淡，少用） |
| SealRed | 同 ZhuSha | 行动/危险共用朱砂 |
| Danger | `#C4453A` | 与朱砂一致 |

稀有度：R `#9AA39A` / SR `#6B9E92` / SSR `#C4453A` / UR `#C9A96A`（与 chrome 解耦）。

**禁止**：第四层氛围色下渗按钮；金线包一切面板；按钮斜切角+环痕饰。

## 排印

| 角色 | 字体 | 用法 |
|------|------|------|
| Brand | 马善政楷书 22sp | 仅 Logo「织环」二字 |
| Display | Noto Serif SC 600 | 大数字、仪式大标题（克制） |
| Title | Noto Serif SC 500 | 页面标题、角色名 |
| Body/Label | 系统无衬线 | 正文、控件、导航 |

废除：标题金影 `Shadow`、透明度呼吸浮动、letterSpacing 过宽。

## 形状与间距

- 圆角：xs=4 / sm=8 / md=12 / lg=16 / xl=20 / xxl=24（沿用 token）
- 间距：xs=4 / sm=8 / md=12 / lg=16 / xl=24 / xxl=32
- 主 CTA：圆角 md，**纯色朱砂**，无描边无切角
- 次按钮：描边 1dp Frost/Text3，无内环
- 面板：实底 Ink，**默认无金边**；选中才 Stroke 加强

## 组件规范

### 按钮
- `GoldButton` / `GildedButton`：朱砂实底 + Text1 字（兼容 API 名，视觉换芯）
- `InkButton`：单线描边，去内环
- 按压：scale 0.97 + 触觉，无墨迹 splash 必装（可保留可选）

### 导航
- 底栏：BgMid 实底，顶边 1dp Stroke；选中 = 朱砂 icon+label + 顶部 2dp 朱砂短线
- 无选中面板渐变、无底部圆环

### 顶栏
- 返回：`‹` 或图标，无金影；标题 **静态**，无浮动动画
- ResourceBar：无边框胶囊，字形+数值

### 面板 / 卡片
- 默认：BgMid 实底 + 可选极淡 Stroke
- 选中：Stroke 提高对比或朱砂 1dp
- 去掉：默认金边、纸纹经纬线可选极淡、顶部内高光可留（2%）

### 角色卡
- 画心为视觉中心；金线内框仅 UR/SSR，SR/R 用稀有度色 0.35α
- 标签区克制：稀有度小 chip + 元素单字

## 签名时刻

1. **朱砂印**：主 CTA 如盖印——干净色块、果断按压
2. **画心留白**：角色立绘周围呼吸感，chrome 最小化
3. **冷暖对比**：墨青底 × 宣纸字 × 朱砂点，三色定调

## 禁止清单（全站扫）

- [ ] 面板默认金边 `Gold.copy(alpha=0.3)`
- [ ] 按钮 CutShape / drawRingEndcaps / drawInnerRing
- [ ] 导航选中金渐变面板 + 底部圆环
- [ ] 标题 Shadow 金影 + sin 呼吸
- [ ] 裸 `fontSize` 新代码；新装饰性 Canvas 环纹
- [ ] Frost 当装饰色滥用

## 迁移策略

1. 换 AppTheme / Theme Token 值 + 排印（API 名保留，全站跟色）
2. 重写 ThemeButtons / GameNavBar / AppChrome（去装饰）
3. 软化 ArtifactPanel / GalleryBackdrop 边框逻辑
4. 主页减装饰、加留白
5. 子页：扫硬编码色与多余 border/glow
