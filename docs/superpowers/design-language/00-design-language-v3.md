# Milan 设计语言 v3.0 ——「丹青典藏 · Gilded Codex」

> 版本：v3.0（2026-09-09）　状态：**提案待评审**
> 范围：全局视觉设计语言 + 设计系统 + 落地路线图。适配工程现状：Kotlin / Jetpack Compose / Material3（BOM 2026.06.01）/ AGSL 特效层（API 33+ 判定）。
> 上游依赖：立绘美术 v2.3（`docs/superpowers/art-direction/portraits-v2/06-card-art-composition.md`，写实卡面 + Marvel Snap 工艺方向）。**本文档的 UI 方向与该管线互为前提**。

## 0. 文档定位与废弃清单

本文档是 v3 设计语言的**单一事实来源**，取代以下互相矛盾的旧规范（内容仅作历史参考，勿再按其执行）：

| 旧文档 | 状态 | 冲突点 |
| --- | --- | --- |
| `docs/ui-redesign-plan.md`（宇宙星穹紫） | 已加废弃标注 | 旧紫色暗黑风 |
| `docs/plans/2026-08-04-twilight-visual-design.md`（诸神黄昏暗紫） | 已加废弃标注 | 旧紫色暗黑风 |
| `docs/superpowers/specs/2026-07-30-ui-redesign-genshin-style.md` | 已加废弃标注 | 旧原神风（effects 层硬编码稀有度色即其残留） |
| `docs/superpowers/specs/2026-08-01-twilight-rebrand.md` | 已加废弃标注 | 旧紫色暗黑风 |
| `docs/superpowers/art-direction/character-designs-*.md` / `visual-redesign-spec.md` | 废弃（AGENTS.md 已声明） | MAUI 时代 v1 立绘稿 |
| 现行代码注释「云海仙气·丹青录 v2（2026-09-05）」 | **被继承** | v3 是 v2 的范式升级，非推倒（见 §3.3 继承清单） |

---

## 1. 现状诊断（v2「云海仙气·丹青录」）

### 1.1 必须保留的资产（v3 继承项）

现状审计结论：v2 的识别度高于常见"暗紫抽卡模板"，以下资产是品牌积累，**重设计不推倒**：

1. **视觉母题**：朱印（SealStamp）、墨迹边框、笔触圆环、绫绢织纹卡背、墨溅按压、楷书品牌字——独特的东方识别度。
2. **稀有度语言已全站贯通**：发光/描边/演出强度分级贯穿抽卡-图鉴-详情-编队（CyberCharge/CyberBeam 的 UR 分级粒子）。
3. **动效骨架**：交错弹簧入场、视差、SharedElement、AGSL 流体背景 + API33 兜底、粒子池。
4. **组件复用度**：GlassPanel / PageBackground / GoldButton / NeonButton / GameNavBar 全站共享，特效独立 `ui/effects/` 层——**具备整体换芯条件**。
5. **工程意识**：色板带调用点注释、触觉分级、48dp 热区、tnum 等宽数字、WCAG 注释。

### 1.2 四大技术债（v3 P0 必清）

1. **四代设计语言叠层**：`ui/effects/MeshGradients.kt:42-63` 与 `ParticleSystem.kt:303-306` 硬编码旧"原神风"稀有度色（UR `#FFD700` / SSR `#9370DB` / SR `#4169E1` / R `#3CB371`），与现行 rarityColor（月白/石青/朱砂/金箔）冲突；theme 外硬编码 `Color(0x…)` 约 **188 处**。
2. **形状/间距 token 有名无实**：`AppTheme.Roundness` **0 引用**、`Spacing` 仅 4 处；`Theme.kt:110 GameShapes` 是另一套档位（6/10/14/16/20 vs 8/12/16/20/24 互相矛盾）；实际 **166 处**临时圆角随手写。
3. **排版未体系化**：**275 处**裸 `fontSize=` vs 83 处 `MaterialTheme.typography`；`res/font/noto_serif_sc_variable.ttf` 是死资源；页面标题与 headlineSmall 重复定义。
4. **主题入口单薄**：仅一套暗色 scheme；`AppTheme` 是静态 object 而非 CompositionLocal；三世界 `WorldPalette` 定义了但界面层几乎没用；`CyberPalette` 命名遗留；部分页面 emoji 当图标（HeroButtons "✦📖📋🎫❤️"）；`BattleReplay` 已建**未接线**——战斗表现是当前最薄弱环节。

---

## 2. 对标研究：流行手游的设计语言拆解

结论先行：**没有一个爆款是靠"配色好看"立住的，都是靠一条概念主张 + 极端一致性**。逐一拆解，标注"可借鉴 / 不照搬"：

### 2.1 Marvel Snap（卡牌对标，最高优先级）
- 设计语言核心：**"卡牌即物理收藏品"**。UI 一切为卡服务：卡面有真实材质（箔、浮雕、闪光），资源/按钮全部退后。牌桌是"桌面"，抽卡是"开包"，收藏是"展柜"。
- 关键机制：稀有度不用颜色堆砌，而用**工艺分级**（油墨→浮雕→全息箔→动态卡面）；点按卡牌有 tilt 光响应。
- 可借鉴：工艺分级稀有度范式、卡面光响应、收藏馆式陈列。**立绘管线 v2.3 已明确对标 Snap 卡面工艺，UI 必须跟上**。
- 不照搬：美漫题材、极短对局节奏。

### 2.2 宝可梦 TCG Pocket（2024-2026 收藏范式新标杆）
- 设计语言核心：**"数字卡牌的实体化仪式"**。拆包有撕膜、抽卡可把卡拿起来对着"光"看全息纹理、收藏册是可翻页的活页夹。
- 可借鉴：**拆包仪式的分幕编排**（对应 Milan 抽卡演出）、收藏册隐喻（对应 Deck 卡组页）、"持卡对光"交互（AGSL 可实现）。
- 不照搬：IP 原作卡牌规则的直译。

### 2.3 崩坏：星穹铁道（HSR）
- 设计语言核心：**"星穹剧院"**——深蓝基底 + 高可读玻璃面板 + 金线 + 星图/线路图母题，标题衬线化，5★ 出场金光。
- 可借鉴：玻璃面板的**可读性处理**（玻璃上文字永远有实底托底）、金线指示器语言（现行 NavBar 金箔指示线与之同构，保留）、衬线标题 + 无衬线正文的混排制度。
- 不照搬：太空题材符号（星轨/飞船），Milan 用「典藏/印/卷」符号系替代。

### 2.4 绝区零（ZZZ）
- 设计语言核心：**"街区杂志"**——黑白 + 荧光绿、粗野主义排版、斜切面板、网点印刷、漫画分镜。极端概念一致性使其两年不过时。
- 可借鉴：**概念承诺的勇气**——设计语言敢统一到"所有面板斜切、所有标题杂志化"的程度；动效有性格（故障/噪点）。
- 不照搬：整套街潮视觉——与山海经立绘资产完全冲突；且该风格已进入跟风末期（半衰期短，见 §3 趋势过滤）。

### 2.5 原神
- 设计语言核心：**"冒险手账"**——羊皮纸/博物图鉴式的题元化 UI（diegetic UI），一切界面像游戏世界里的物品。
- 可借鉴：**五年一致的题元化纪律**；物品/角色详情页"图鉴铭牌"式信息层级（Milan 详情页的立绘铭牌同构，强化）。
- 不照搬：暖亮色调（Milan 是暗色典藏馆基调）。

### 2.6 鸣潮
- 设计语言核心：**"极简科技 HUD"**——黑/白/青三色，大留白，战斗 HUD 极简克制。
- 可借鉴：**战斗层的克制**——HUD 只留必要信息、高对比；这直接回应 Milan 战斗"报表化"短板。
- 不照搬：末世科技符号。

### 2.7 明日方舟 / Endfield
- 设计语言核心：**"工业极简 + 排版驱动"**——黑白 + 单一强调色，信息密度高但靠字号/字重/等宽数字分层。
- 可借鉴：**排版驱动的信息设计**（战报 StrikeRow 的元素 glyph + 四档伤害色阶已同构，升格为规范）、等宽数字纪律。
- 不照搬：终端/LCD 母题。

### 2.8 恋与深空
- 设计语言核心：**"角色至上"**——一切界面为角色让路，角色与玩家有"对视时刻"（实时渲染立绘互动）。
- 可借鉴：**详情页"对视时刻"**——立绘光响应 + 微视差 + 触觉反馈的三重"活体感"（Milan 已有漂浮+视差，补光响应即可）。
- 不照搬：乙游交互范式。

### 2.9 Supercell 系（Squad Busters 等）
- 设计语言核心：软糖 3D、圆润亲和。
- 结论：**整体不采纳**（受众与写实卡面 v2.3 冲突），仅借鉴其"空状态也要有性格"（EmptyState 现状偏通用）。

---

## 3. 趋势过滤与方向决策

### 3.1 2026 趋势过滤（结构性 vs 审美潮流）

> 来源：Wandr Studio《Game UI Design Trends 2026》及 2026 UI 趋势综述（tubik / Orizon / Cleveroad / Fuselab）。核心结论：**审美潮流"跟上是重皮肤成本，错过几乎零成本"；结构性变化才进路线图。**

**结构性（进路线图）：**
1. **会话即设计单位**：重新进入只需一步；核心循环契合中位数会话时长。→ Milan 对策：主页首屏直达"今日可做"（抽卡/爬塔/补给），冷启动不打扰。
2. **无障碍成为分发问题**：字号选项、不以颜色为唯一信息载体。→ Milan 对策：稀有度色 + 纹样冗余编码（§5.2）、设置页字号档位。
3. **渐进式披露**：首个"有意义成功"限时。→ 新号首抽 ≤ 3 分钟可达。
4. **有意图的动效**：动效服务层级而非炫技。→ §5.5 动效纪律。
5. **排版驱动 + 手绘/个人触感**：大字号、可见结构、手作质感。→ §5.3 排印 + §5.2 印章/墨迹保留理由。

**审美潮流（只借不追）：**
- 玻璃拟态 / Liquid Glass：借"自适应透明"，但**玻璃上必须有可读性托底**（HSR 做法）。
- 霓虹发光 / 粗野字体 / 重噪点 / 斜角面板：只在仪式时刻用，不做全局皮肤。
- ⚠️ 原文警告：暗色高饱和长时间会视觉振动——**菜单可承载美学，持续游玩的 HUD 层不能**。这条写进 §5.7 铁律。

### 3.2 候选方向与决策

| 方向 | 一句话 | 判定 |
| --- | --- | --- |
| A. 街潮粗野（ZZZ 系） | 黑白荧光、杂志斜切 | ❌ 与 28 张山海经写实立绘资产完全冲突；风格半衰期短 |
| B. 软糖亲和（Supercell 系） | 圆润软 3D | ❌ 与 v2.3 写实卡面、暗色收藏基调冲突，受众错位 |
| **C. 丹青典藏（推荐）** | **"一座只陈列珍本的典藏馆"** | ✅ 继承全部 v2 资产与立绘管线投资，对标 Snap/TCG Pocket 收藏范式 |

### 3.3 新设计语言：「丹青典藏 · Gilded Codex」

**概念主张（一句话）**：整个 App 是一座**只陈列珍本的典藏馆**——卡牌是文物，界面是展陈系统，玩家是收藏家。

**范式转变**：v2 是"**水墨画在界面上**"（水墨作为装饰皮肤），v3 是"**界面向卡牌供奉**"（一切界面元素为卡牌让路，水墨从"装饰层"退为"馆内氛围层"）。这一字之差解决 v2 的根本矛盾：立绘已升级为写实卡面插画（v2.3），而 UI 还在水墨装饰里和它打架。

**六条设计原则（每条可判定，评审时逐条对照）：**
1. **卡牌即主角**：任何界面只有一个视觉焦点对象；面板、按钮、文字都是展厅的墙与射灯。
2. **材质代替装饰**：深度/状态/层级用材质（玻璃、纸、箔、光）表达，不叠装饰线；删掉一切"为了好看"的第三层描边。
3. **光是状态语言**：可交互=有光；聚焦=聚光；稀有度=光的工艺分级。色彩只是光的载体之一。
4. **稀有度即工艺**：R 印刷 → SR 上釉 → SSR 鎏金 → UR 全息箔（§5.2），四档工艺差异必须能在 3 米外/96px 缩略图分辨。
5. **菜单可浓妆，战斗须素读**：菜单页允许浓墨重彩；战斗 HUD 层执行对比度预算（§5.7），美观给可读性让路。
6. **动有重量，一次一动**：动效有物理感（弹簧、惯性、落章的"顿"）；同一时刻只有一个主对象做 emphasized 动画。

---

## 4. 不变的骨架：信息架构与交互范式

设计语言换芯，**不动**以下内容（迁移风险控制）：
- 底部 5 tab（Home/Gacha/Deck/Shop/Settings）+ 子页返回栈、类型安全路由。
- 写操作事务范式、`GameService.snapshot` 订阅、`WriteOutcome`/`PullOutcome` 契约。
- 立绘 `PortraitLoader` 命名与加载管线、SharedTransition 立绘跨页过渡。
- 触觉分级（HapticEffects）、粒子池、AGSL + API33 兜底策略。

---

## 5. 核心系统规范

### 5.1 色彩体系

**总原则**：基底收敛为中性玄墨（给金箔让对比度），色彩只做三层：基底 / 材质面 / 强调。禁止"第四层氛围色"。

**基底（玄墨三阶）**——替代现行深靛蓝玻璃系（`AppTheme.kt:23-32`）：

| Token | 值 | 用途 |
| --- | --- | --- |
| `Ink0` | `#0A0D14` | 页面最深底（原 BgDeepest #0F1428 加深提中性） |
| `Ink1` | `#10141C` | 卡片/面板基底 |
| `Ink2` | `#161C27` | 嵌套面板基底 |
| `GlassVeil` | `白 5% + blur` | 玻璃面板（替代 `0x8C1E2848` 冰蓝玻璃，去色偏） |

**强调双色制**：
- **金箔 = 珍贵与度量**：Gold `#E0B860` / Hi `#F8D878` / Deep `#C09838`（沿用 v2）。只用于：货币数字、稀有度、主 CTA、聚焦光。**金不进正文**。
- **朱砂 = 行动与警示**：SealRed `#D04848` 升格为危险/UP/击杀专用行动色（v2 仅点缀）。

**文字三级**：Text1 `#F2EFE8`（微暖白，配金）/ Text2 `#B8BCC8` / Text3 `#767C90`；Text2 on Ink1 ≥ 4.5:1，Text3 仅限 Caption。

**稀有度四档（色沿用 v2，工艺行为重定义，见 §5.2）**：R 松烟 `#C8D0DC`（原月白微调）/ SR 石青 `#68B0A8` / SSR 朱砂 `#D86060` / UR 金箔 `#F0D060`。**全站唯一取色口：`AppTheme.rarityColor()`，effects 层一律引用，禁止硬编码**（修复 MeshGradients.kt:42-63 / ParticleSystem.kt:303-306）。

**元素八色**：沿用 `ElementTheme.kt:23-30` 国画矿物调（已达标，不改）。

**三世界 ambient（激活 WorldPalette）**——骨架 token 不变，只换氛围层：

| 世界 | 氛围层 | 氛围光 |
| --- | --- | --- |
| 神话 Shinwa | 宣纸纹理暗调 | 朱砂金 |
| 苍穹 Aether | 星图玻璃暗调 | 紫青 |
| 铁幕 Ironveil | 拉丝金属暗调 | 钢铜 |

切换时机：主页 Hero 卡、详情页随当前角色世界渐变过渡（800ms），其余页面保持默认玄墨。**世界换肤是氛围层特权，永不下渗到按钮/文字/形状**。

### 5.2 稀有度即工艺（v3 的招牌系统）

四档稀有度 = 四档装裱工艺，**色 + 纹 + 光 + 动**四维冗余编码（无障碍：色弱者靠纹样仍可分辨）：

| 稀有度 | 工艺隐喻 | 框 | 底纹 | 光行为 | 动效 |
| --- | --- | --- | --- | --- | --- |
| R | 素面印刷 | 1dp 素线 | 无 | 无 | 无 |
| SR | 上釉 | 1.5dp 双线 | 釉光斜向渐变 | 静态微光 | 入场一次 |
| SSR | 鎏金边 | 2dp 金边 + 回纹角标 | 织纹 | 呼吸光晕 3s | 扫光 1 次 |
| UR | 全息箔 | 2.5dp 流光边 | 箔面（AGSL 全息 shader） | 流光 4s 一圈 + 触摸光响应 | 常驻流光 + 粒子 |

现有 `CharacterCard` / `CyberCards` 双层描边 / 结果 chip 脉动 / `AuraHalo` 全部收编为**一个参数化组件 `CodexCard(tier)`**（§6.1）。

### 5.3 形状与材质

**圆角唯一档位**（收敛 166 处临时圆角，废弃 GameShapes 第二套）：

```
Roundness: xs 4 / sm 8 / md 12 / lg 16 / xl 24   （dp，卡片=md，面板=lg，弹层=xl）
```

**异形 Shape 只保留两种**：金 CTA 的 6dp 斜切角（GildedCut，保留 GoldButton 现有 Shape）；印章方角 2dp（Seal）。其余一律圆角档位。

**材质四件套**（按优先级使用，一个组件最多叠两层）：
1. 玄墨实底（Ink1/Ink2）——默认。
2. 玻璃（GlassVeil + 发丝线 `#20FFFFFF`）——仅"浮"在内容上的层（NavBar、弹层、胶囊）。
3. 纸纹（宣纸暗纹位图/AGSL 噪声）——典藏相关容器（卡组册页、详情卷轴）。
4. 箔（AGSL 全息/扫光 shader）——**仅稀有度 SSR/UR 与仪式时刻**。箔是特权材质，滥用即贬值。

### 5.4 排印体系

**字体三分**（解决 275 处裸 fontSize 与死资源问题）：

| 角色 | 字体 | 说明 |
| --- | --- | --- |
| 品牌 + 仪式标题 | 马善政楷书 | **仅 2 处**：「丹青录」Logo、抽卡仪式标题。不再作 headlineSmall |
| Display/Title | **Noto Serif SC Variable**（激活死资源） | 宋体骨架可变字重 500-700，现代衬线配典藏定位 |
| 正文/控件/数字 | 系统 Sans | 数字一律 `tnum` 等宽（已有纪律） |

**Type Scale（全站唯一，落地为 `MaterialTheme.typography`，UI 层禁止裸 `fontSize`）**：

| 档 | 规格 | 用途 |
| --- | --- | --- |
| displayLarge | 40/48 serif 600 tnum | 战力/资源大数字 |
| displaySmall | 28/36 serif 600 | 仪式数字（十连统计） |
| headlineMedium | 22/30 serif 600 | 页标题（替代 HomeScreen.kt:134 裸 22sp） |
| titleLarge | 18/26 serif 500 | 区块标题 |
| titleMedium | 16/24 sans 600 | 卡片标题 |
| bodyLarge | 15/23 sans 400 | 正文 |
| bodyMedium | 13/20 sans 400 | 次要信息 |
| labelLarge | 12/16 sans 500 +0.5sp | 按钮/chip |
| labelSmall | 11/14 sans 400 Text3 | 角注 |

**装饰排印**：区块标题可用**竖排**（竖排 + 衬线 = 丹青排版签名，2026 typography-driven 趋势的本土化表达）；Latin 数字与 CJK 之间加 0.25em 间隙（`Space(0.25.em)`）。

### 5.5 图标与图形语言

- **Icon 体系**：Material Symbols Outlined（NavBar 已用）为主，补齐功能位；**清除全部 emoji 图标**（HeroButtons "✦📖📋🎫❤️" → 对应线性 icon；EmptyState 同）。图标 2dp 圆头线、24dp 网格、选中态填充 + 金色。
- **元素 glyph**：保留汉字印章式，规格化为 8 个 vector asset（当前 Canvas 绘制代码收编为资源）。
- **稀有度纹章**：R 素环 / SR 双环 / SSR 回纹角标 / UR 流光角标（vector，随 CodexCard 下发）。
- **印章（Seal）**：品牌印章保留，但只出现在"官方/权威/确认"语义位（主页右上、抽卡结果落印、结算盖章），一屏最多一枚。

### 5.6 动效体系

**时长与曲线档位**（收敛各页自定时长）：

| 档 | 时长 | 曲线 | 用途 |
| --- | --- | --- | --- |
| instant | 80ms | — | 按压反馈 |
| quick | 160ms | 标准 cubic(0.2,0,0,1) | hover/选中态、chip |
| standard | 240ms | 标准 | 面板、页面元素入场 |
| emphasized | 400ms | cubic(0.05,0.7,0.1,1) | 转场主对象、SharedElement |
| ritual | 700ms+ | 分幕编排 | 抽卡/突破/UR 落印 |

**弹簧基准**：列表交错入场 dampingRatio 0.55（现状保留）、交互回弹 0.75/stiffness 400；禁止 linear（粒子轨迹除外）。

**纪律**：
1. **一次一动**：每次转场只有一个主对象 emphasized，其余元素 standard 以 40-60ms 交错、8 档封顶（CardMotion 现有纪律升格为全局规范）。
2. **动有重量**：所有 scale 动画成对出现（按下 0.96/回弹 1.0）；"落章"类确认动效末帧加 30ms 静止顿挫。
3. **光响应**：UR/SSR 卡面高光随触摸位置移动（AGSL，API33+ 以下静态渐变兜底）。
4. 触觉沿用 HapticEffects 分级，与稀有度绑定档位不新增。

### 5.7 可读性预算与无障碍（铁律）

1. **HUD 层对比度预算**：战斗/爬塔持续游玩界面——正文 ≥ 4.5:1、大数字 ≥ 3:1；金箔文字仅限 ≥ 18sp 或带 1dp 深色描边；玻璃上不放 12sp 以下浅金。
2. **色不单载**：稀有度 = 色+纹+光（§5.2）；伤害色阶保留现有四档 + 数字字号分级。
3. **字号档位**：设置页新增 标准/+10%/+20% 全局缩放（`MaterialTheme.typography` 集中生效——这是排印收敛的第二个理由）。
4. 触控热区 ≥ 48dp（现状保留）。

---

## 6. 组件规范（新旧映射）

### 6.1 新组件与收编

| v2 现状 | v3 | 关键改动 |
| --- | --- | --- |
| GlassPanel | `ArtifactPanel` | 参数化三态：实底/玻璃/纸纹；highlighted 态保留 |
| GoldButton | `GildedButton` | 保留斜切+扫光；补齐 disabled/secondary/hierarchy 档位 |
| NeonButton | `InkButton` | 降为次级文字按钮 |
| CharacterCard + CyberCards 卡框 + 结果 chip | `CodexCard(tier)` | §5.2 工艺分级唯一实现，全站共用 |
| PageBackground | `GalleryBackdrop` | 玄墨三阶 + 世界 ambient 层（WorldPalette 激活） |
| GameNavBar | 保留结构 | 材质改玻璃，金箔指示线保留（HSR 同构验证过） |
| HealthBar / BattleResultOverlay | 保留 | 过对比度预算校准 |
| BattleReplay（未接线） | `BattleReplayTimeline` | 升级为结算页「战局重演」时间轴（§7.4） |
| emoji 图标 / EmptyState | Icon 体系 | §5.5 |

### 6.2 弹层与反馈

GlassDialog / InkBottomSheet / InkSnackbar / InkSkeleton 保留，统一 xl 圆角 + 玻璃材质；InkSkeleton 保留水墨 shimmer（手作触感，符合 2026 趋势）。

---

## 7. 分页面设计

### 7.1 主页 = 展廊
- **焦点对象**：Hero 卡即"镇馆展品"——聚光顶光（径向光晕保留）+ 底座铭牌（稀有度/世界 chip 收进铭牌）+ 流光边框仅 UR 流。
- 长卷横滑保留（参观动线）；SealStamp 保留右上。
- HeroButtons 2×4 网格 → **首屏一排 3 个主行动**（抽卡/无尽之塔/每日补给，渐进式披露），其余入口收进"+"展柜抽屉。首屏信息量减 40%。
- 冷启动直达：有免费抽/补给未领时，对应按钮带呼吸光 + 数字角标（会话设计）。

### 7.2 抽卡 = 开匣揭裱（仪式核心，投入最重）
- 现有 Cyber 三幕（蓄能→光柱→显卡）**保留骨架，置换材质语言**：蓄能粒子收进"匣"（绫绢卡背保留）；光柱收窄为聚光；显卡改**实体翻面**——卡从卡背揭裱翻出 +  tilt 光响应，UR 附加全息箔流光 + 12 金点迸发（保留）。
- 十连：翻牌节奏分级保留（UR 后置 500ms）；结果页落朱印「典藏」。
- 保底进度（软保底预警）保留，用金/朱双色度量条。

### 7.3 卡组 = 收藏册
- 2 列网格 → **活页册隐喻**：页眉世界标签 + 分隔脊线；CodexCard 四档工艺直接可见。
- 全屏预览层保留黑 80% 遮罩 + 稀有度光晕，卡框换 CodexCard + 光响应。
- SharedTransition 保留。

### 7.4 战斗/爬塔 = 对弈台（可读性优先页）
- 战力预览条（石青 vs 朱砂双渐变）保留，数值 displayLarge tnum。
- **接线 BattleReplay**：结算页新增「战局重演」时间轴（已建组件升格），逐回合 HP/伤害可视化 + 元素 glyph chip；HealthBar 换 CodexCard 配套配色。
- HUD 层执行 §5.7 对比度预算：战报 StrikeRow 保留四档色阶 + 字号分级，背景禁玻璃。

### 7.5 角色详情 = 立传卷轴
- SubPageHero 保留（56% 大立绘 + 渐隐 + SharedElement + 0.3x 视差）。
- 新增"对视时刻"：立绘触摸光响应（AGSL 高光跟随）+ 触觉微反馈。
- Tab 指示器金线保留；属性条形图保留 80ms 交错填充。

### 7.6 商店 / 设置（简）
- 商店 = 坊市：货架用 ArtifactPanel 实底，价格数字 tnum 金色，促销角标用朱砂（唯一红色促销位）。
- 设置：新增字号档位（§5.7）、动效减弱开关（ritual 动效可一键降为 quick，无障碍）。

---

## 8. 代码落地映射（工程视角）

| # | 工作项 | 涉及 | 阶段 |
| --- | --- | --- | --- |
| 1 | effects 层稀有度色统一引用 `AppTheme.rarityColor()` | `MeshGradients.kt:42-63`、`ParticleSystem.kt:303-306` | P0 |
| 2 | 圆角/间距收敛：删 GameShapes，166 处临时圆角机械替换为 Roundness token | `Theme.kt:110`、全 ui/ | P0 |
| 3 | emoji → 线性 icon | HomeScreen.kt HeroButtons、EmptyState | P0 |
| 4 | 排印收敛：`MaterialTheme.typography` 重写（含 Noto Serif 激活），275 处裸 fontSize 分批迁移 | `Theme.kt:118-149`、全 ui/ | P0→P1 |
| 5 | `AppTheme` object → CompositionLocal（`LocalInkColors` / `LocalWorldPalette`），静态值保留为默认 | AppTheme.kt、Theme.kt | P1 |
| 6 | `CodexCard(tier)` 组件 + AGSL 全息箔/光响应 shader（GpuEffects 扩展，API33 兜底） | components/、effects/ | P1 |
| 7 | `GalleryBackdrop` + WorldPalette 激活（DynamicTheme 扩展） | ui/effects/DynamicTheme.kt | P1 |
| 8 | 逐页换芯：主页→抽卡→详情→卡组→爬塔→商店/设置 | 各 Screen | P2 |
| 9 | BattleReplay 接线结算页 + 对比度校准 | TowerScreen、BattleReplay.kt | P2 |
| 10 | 仪式演出置换材质 + 落印/字号档位/动效减弱开关 | CyberStage 族、Settings | P3 |

**工程红线（不动）**：领域层纯净性、写操作事务范式、`@Serializable` 存档结构、EventBus 纪律、SaveProvider 注入——本次纯 UI 层改造，不触 `:shared` 与 `data/`。

**性能预算**：AGSL shader 仅 API33+；低端静态兜底（现有策略沿用）；常驻流光仅限 UR 且同屏最多 3 个实例；粒子沿用池化。

---

## 9. 资产管线

- **卡框分层资产**（配合 portraits v2.3）：`CodexCard` 所需 vector：框线 ×4 档、角标纹章 ×4、织纹/箔纹 tile ×2、宣纸暗纹 ×1——全部 vector/tile，不加位图整框。
- **立绘**：沿用 portraits-v2 管线（`extract_prompts.py` / `generate_portraits.py`），v2.3 写实卡面 + 本文 §5.2 工艺分级卡框组合即成品卡。
- **武器图**：28 张 WebP 沿用，详情页铭牌化呈现。
- **卡背**：绫绢织纹 + 朱砂落印保留（已成品牌资产）。
- **音效**：抽卡/落印/翻卡三组仪式音与稀有度档位绑定（现状分级保留，补仪式音）。

---

## 10. 路线图与验收

**P0 清债（1 周）**：§8 表 1-3 + 旧规范文档头加废弃标注。验收：全 ui/ 无 effects 层硬编码稀有度色；圆角枚举收敛至 token；构建与既有测试全绿（`ComposeUiSmokeTest` 等 Robolectric 套件注意 `GameState.resetForTest()` 纪律）。
> ✅ **2026-09-09 已完成**：新建 `ui/effects/EffectPalettes.kt` 统一取色口（MeshGradients/ParticleSystem/DynamicTheme 三处旧色表全部改为从 AppTheme/ElementTheme 派生）；圆角 token 补 xxs/xs 档并全站替换 163 处字面量（GameShapes 改由 Roundness 派生）；EmptyState 换 ImageVector、HeroButtons 清除 emoji（其余屏幕彩色 emoji 归 P2 逐页清理）；四份旧规范已加废弃标注。另提前完成 §6.1 `CodexCard(tier)` 组件（纯 Compose 实现，AGSL 全息箔留待后续增强）+ `CodexCardTest`。全量 389 测试通过。

**P1 换芯（2 周）**：§8 表 4-7。验收：MaterialTheme.typography 接管全部文字；`CodexCard` 单测 + 截图基线（Robolectric 渲染测试，文本断言内容无关）；compositionLocal 化后 21 处服务写入口回归绿。
> 🟡 **2026-09-10 大部分落地**：全 ui/ 约 250+ 处裸 `fontSize` 收敛至 `MaterialTheme.typography`（仪式标题 `RitualType`/按钮参数化字号/印章计算式等刻意保留）；`GalleryBackdrop`/`ArtifactPanel`/`GildedButton`/`InkButton` 接入并保留旧名兼容；`LocalWorldPalette` 已接主页 Hero 与角色详情氛围层；`FluidBackground` 叠世界 glow；ParticleSystem/BattleStrikeFx/CyberCards 残留硬编码改走 `AppTheme`；**抽卡单抽/十连卡面已接 `CodexCard`**；UR 叠 **AGSL 全息箔 `HolographicFoilOverlay`**（API33+，旧机 Compose sweep 兜底，触摸跟手）。编译 + `testDebugUnitTest` 全绿。**未完**：`AppTheme` object→CompositionLocal 全量、六页 §7 逐页 judge 截图验收、BattleReplay 接线、仪式音资产。

**P2 页面（3 周）**：§8 表 8-9，每页合入后跑 judge 视觉验收（渲染 PNG 对照本规范 §7 逐条）。验收：六页全部达标 + 对比度预算 checklist 全过。
> 🟡 **2026-09-10 关键路径落地**：`BattleReplayTimeline` 新建并接入 `BattleResultOverlay`（回合柱 + 元素 glyph 明细）；角色详情「对视时刻」触摸光响应 + SSR/UR 箔叠层；卡组页收藏册页眉。**待真机截图验收**与逐页像素级对照。

**P3 演出（1-2 周）**：§8 表 10。验收：首抽 ≤ 3 分钟可达（新号埋点）；UR 全息箔在 96px 缩略图可辨（§5.2 原则 4）；动效减弱开关生效。

**长期度量**：首抽完成时长、抽卡完成率、角色详情停留时长、（如有）截图分享率；每版本对照，防止"审美漂移"回潮。
