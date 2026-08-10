# Milan 视觉重设计 — 美术方向与界面规范

> **版本**: v1.0 | **日期**: 2026-07-29 | **状态**: 设计定稿 → 生图 → 实现
> **目标**: 为三界20角色生成精美立绘，并重设计全部6个界面，使其符合世界观叙事。

---

## 0. 设计哲学

### 核心命题
**「山海经 × 漫威/DC — 东方神话与西方超英的次元融合」**

Milan 的视觉身份必须同时传达两层信息：
1. **东方神话的底蕴** — 山海经异兽、水墨意境、仙侠飘逸
2. **西方超英的张力** — 漫画分镜感、能量爆发、角色气场

### 视觉锚点（Visual Anchors）
- **「裂缝」** — 次元裂隙是核心视觉符号，贯穿抽卡演出、转场、UI装饰
- **「融合」** — 每个角色都是"神话原型 + 超英能力"的融合体，立绘要同时体现两者
- **「升维」** — 稀有度越高，角色越"突破次元壁"（R=平面插画 → UR=破壁而出）

### 立绘标准
- **尺寸**: 1024×1536 (2:3 竖版，适合手机屏幕)
- **格式**: PNG 透明背景（便于程序叠加特效）
- **风格底座**: 高质量动漫插画，细节丰富，适合做360°检视
- **统一性**: 所有角色保持同一插画水准，光影方向统一（左上方主光源）

---

## 1. 三界视觉语言（Mixed Rendering）

### 1.1 Shinwa 神话界 — 「水墨赛璐璐」

| 属性 | 规范 |
|------|------|
| **渲染风格** | Cel-shaded 赛璐璐 + 水墨边描 |
| **线条** | 粗细变化的墨线勾勒轮廓，关键结构处加粗 |
| **上色** | 平涂为主，局部渐变（服饰褶皱、光晕） |
| **质感** | 宣纸/绢本底纹，轻微墨晕效果 |
| **主色调** | 朱砂红 #C41E3A · 墨黑 #1A1A1A · 金箔 #D4AF37 · 玉白 #F5F0E8 |
| **辅助色** | 石青 #1E90FF · 石绿 #2E8B57 · 赭石 #8B4513 |
| **氛围元素** | 祥云、山水剪影、灯笼光、花瓣飘落 |
| **UI 边框** | 云纹/回纹装饰角，圆角矩形 |
| **粒子特效** | 墨点飞溅、金色符文、花瓣 |
| **代表角色** | 烛龙、凤凰、飞廉、狻猊、旋龟、狸力、蠃鱼 |

**视觉关键词**: 飘逸、仙侠、工笔重彩、敦煌壁画、吉卜力

### 1.2 Aether 虚空界 — 「暗彩写实」

| 属性 | 规范 |
|------|------|
| **渲染风格** | Semi-realistic 半写实 + 暗色渐变 |
| **线条** | 细线/无线条，依靠色块区分结构 |
| **上色** | 写实光影，暗部偏冷紫，亮部偏冷白 |
| **质感** | 宇宙深空、粒子消散、能量流动 |
| **主色调** | 虚空紫 #2D1B69 · 深渊黑 #0A0A14 · 星云蓝 #4A90D9 · 幽灵白 #E8E0F0 |
| **辅助色** | 暗红 #8B0000 · 毒绿 #32CD32 · 星金 #FFD700 |
| **氛围元素** | 星云、黑洞漩涡、漂浮碎片、能量裂隙 |
| **UI 边框** | 尖锐几何形，发光能量线 |
| **粒子特效** | 暗物质碎片、星光、能量脉冲 |
| **代表角色** | 虚无、相柳、商羊、精卫、毕方、钦原、当康 |

**视觉关键词**: 深邃、神秘、宇宙恐怖、暗黑奇幻

### 1.3 Ironveil 铁幕界 — 「机械硬表面」

| 属性 | 规范 |
|------|------|
| **渲染风格** | Hard-surface 机械 + 工业写实 |
| **线条** | 硬朗直线，结构分缝线，铆钉螺丝细节 |
| **上色** | 金属固有色 + 边缘高光 + 油污磨损 |
| **质感** | 拉丝金属、铸造装甲、焊接痕迹、发光能量管 |
| **主色调** | 钢铁灰 #4A4A5A · 枪铁 #2F2F3A · 铜 #B87333 · 警示橙 #FF6B00 |
| **辅助色** | 电光蓝 #00BFFF · 熔岩红 #FF4500 · 锈迹 #8B4513 |
| **氛围元素** | 齿轮、管道、全息屏、火花、蒸汽 |
| **UI 边框** | 铆钉角、切削面、发光能量槽 |
| **粒子特效** | 电火花、蒸汽、金属碎片、能量放电 |
| **代表角色** | 刑天、雷神、穷奇、跂踵 |

**视觉关键词**: 重工、机甲、柴油朋克、战锤40K、合金装备

---

## 2. 角色设计稿索引

完整设计稿（含每个角色的视觉描述、融合特征、色彩指定、生图Prompt）因篇幅较长，拆分为以下文件：

- `character-designs-UR.md` — 5位UR角色完整设计稿
- `character-designs-SSR.md` — 5位SSR角色完整设计稿
- `character-designs-SR.md` — 5位SR角色完整设计稿
- `character-designs-R.md` — 5位R角色完整设计稿

### 2.1 UR 角色一览

| 角色 | 世界 | 元素 | 称号 | 融合 |
|------|------|------|------|------|
| 烛龙 Zhulong | Shinwa | Flame | 昼夜之主 | 凤凰之力 + 火神 |
| 虚无 Wuxu | Aether | Void | 万象终焉 | 湮灭 + 反监视者 |
| 刑天 Xingtian | Ironveil | Metal | 不死战神 | 金刚狼 + 毁灭日 |
| 桔梗 Kikyo | Shinwa | Shadow | 悲运的巫女 | 犬夜叉IP |
| 刻晴 Keqing | Aether | Thunder | 玉衡星 | 原神IP |

### 2.2 SSR 角色一览

| 角色 | 世界 | 元素 | 称号 | 融合 |
|------|------|------|------|------|
| 凤凰 Fenghuang | Shinwa | Flame | 涅槃圣禽 | 凤凰女 + 火星猎人 |
| 相柳 Xiangliu | Aether | Void | 九首毒厄 | 毒液 + 小丑 |
| 雷神 Leishen | Ironveil | Thunder | 雷霆裁决 | 索尔 + 宙斯 |
| 飞廉 Feilian | Shinwa | Wind | 风驰电掣 | 快银 + 闪电侠 |
| 商羊 Shangyang | Aether | Star | 预知神鸟 | X教授 + 命运博士 |

### 2.3 SR 角色一览

| 角色 | 世界 | 元素 | 称号 | 融合 |
|------|------|------|------|------|
| 狻猊 Suanni | Shinwa | Flame | 狮吼震魂 | 黑豹 + 蝙蝠侠 |
| 精卫 Jingwei | Aether | Wind | 衔石填海 | 黑寡妇 + 猫女 |
| 穷奇 Qiongqi | Ironveil | Metal | 噬罪凶兽 | 死侍 + 丧钟 |
| 旋龟 Xuanwu | Shinwa | Earth | 玄甲守护 | 钢力士 + 钢骨 |
| 毕方 Bifang | Aether | Thunder | 焚羽烈鸟 | 猎鹰 + 鹰女 |

### 2.4 R 角色一览

| 角色 | 世界 | 元素 | 称号 | 融合 |
|------|------|------|------|------|
| 狸力 LiLi | Shinwa | Earth | 遁地灵兽 | 蚁人 + 原子侠 |
| 钦原 Qinyuan | Aether | Metal | 毒蜂刺羽 | 黄蜂女 + 黑金丝雀 |
| 跂踵 SiShu | Ironveil | Shadow | 夜行游侠 | 蜘蛛侠 + 夜翼 |
| 蠃鱼 Luoyu | Shinwa | Frost | 渊海游灵 | 海王纳摩 + 水行侠 |
| 当康 Dangang | Aether | Wind | 丰穗瑞兽 | 野兽 + 火星猎人 |

---

## 3. 界面重设计规范

### 3.1 全局设计系统

#### 字体
- 标题: 思源黑体 Heavy / Noto Sans SC Heavy
- 正文: 思源黑体 Regular
- 数字: DIN Pro / Roboto Mono（等宽，用于数值显示）

#### 间距系统（8dp基准）
- xs: 4dp / sm: 8dp / md: 16dp / lg: 24dp / xl: 32dp

#### 圆角
- 卡片: 16dp / 按钮: 24dp（全圆角）/ 标签: 8dp

#### 动效时长
- 微交互: 150ms / 转场: 300ms / 强调: 500ms / 抽卡演出: 1500ms

### 3.2 界面清单

| # | 界面 | 当前Activity | 重设计重点 |
|---|------|-------------|-----------|
| 1 | 主页 | HomeActivity | 英雄展示 + 导航卡片 |
| 2 | 抽卡 | GachaActivity | 裂缝演出 + 结果揭示 |
| 3 | 角色列表 | CharacterListActivity | 卡片网格 + 筛选 |
| 4 | 角色详情 | CharacterDetailActivity | 立绘展示 + 信息层次 |
| 5 | 收藏图鉴 | CollectionActivity | 收集进度 +  silhouettes |
| 6 | 360°检视 | InspectionActivity | 旋转交互 + 特效 |

### 3.3 各界面设计要点

#### 主页 HomeActivity
- 顶部：游戏Logo（带微光动画）+ 货币显示
- 中部：当前最高稀有度角色FullBodyCharacter展示（带呼吸动画）
- 底部：3个导航卡片（抽卡/角色/收藏），带悬停动效
- 背景：CosmicBackground（宇宙星穹）

#### 抽卡 GachaActivity
- 核心：ParticleView裂缝演出（放大→闪光→揭示）
- 单抽/十连按钮：GlowButton（发光边框）
- 结果展示：ResultCard网格（5列），稀有度颜色边框
- 保底计数器显示

#### 角色列表 CharacterListActivity
- 2列网格，CharacterCard卡片
- 每张卡片：立绘缩略图 + 名称 + 稀有度星星 + 元素图标
- 顶部筛选栏（按世界/稀有度/元素）
- 空状态提示

#### 角色详情 CharacterDetailActivity
- 顶部：角色立绘（大）+ 基本信息
- 标签页切换：属性/技能/天赋/故事
- 底部操作按钮：升级/突破/360°检视
- 天赋树预览（3分支）

#### 收藏图鉴 CollectionActivity
- 收集进度条（已收集/总数）
- 3列网格，已拥有=彩色立绘，未拥有=剪影
- 点击查看详情

#### 360°检视 InspectionActivity
- 全屏角色立绘
- 手势：拖拽旋转 / 双指缩放
- 底部操作：旋转/缩放/技能/信息
- 背景随角色世界变化

---

## 4. 技术实现规范

### 4.1 资源结构
```
MauiMilan/
├── Resources/
│   ├── Drawable/           ← 新增：角色立绘PNG
│   │   ├── char_ur_zhulong.png
│   │   ├── char_ur_wuxu.png
│   │   └── ... (20个文件)
│   ├── Raw/data.json       ← 已有
│   └── mipmap-*/           ← 已有（应用图标）
```

### 4.2 立绘加载
- 使用 `BitmapFactory.DecodeStream` 从 `Resources/Drawable` 加载
- 按稀有度分级缓存（LRU）
- 异步加载 + 占位图

### 4.3 程序特效层
- 在立绘上叠加：粒子特效/光环/元素符号
- 天赋解锁时切换VisualLayer
- 爆发动画（Burst）在立绘上层播放

### 4.4 三界差异化
- 每个世界独立的Theme类（颜色/边框/粒子）
- 角色详情页背景随世界切换
- 抽卡结果框样式随世界变化

---

## 5. 实施路线图

### Phase 1: 设计定稿 ✅
- [x] 三界视觉语言规范
- [x] 角色设计稿索引
- [x] 界面重设计规范

### Phase 2: 角色设计稿（进行中）
- [ ] 5位UR完整设计稿 + 生图Prompt
- [ ] 5位SSR完整设计稿 + 生图Prompt
- [ ] 5位SR完整设计稿 + 生图Prompt
- [ ] 5位R完整设计稿 + 生图Prompt

### Phase 3: 美术资源生成
- [ ] AI生图（20角色 × 1张 = 20张立绘）
- [ ] 后处理（裁剪/调色/透明背景）
- [ ] 导入Resources/Drawable

### Phase 4: 界面代码实现
- [ ] 升级UIHelper（统一调色板）
- [ ] 实现立绘加载器
- [ ] 重设计6个Activity
- [ ] 添加动效和特效层

### Phase 5: 测试与优化
- [ ] 真机测试（Android）
- [ ] 性能优化（内存/加载速度）
- [ ] 视觉走查

---

## 6. 附录：生图Prompt模板

### Shinwa风格模板
```
Full-body character prompt. Shinwa world style: cel-shaded anime with Chinese ink-wash outlines, flowing brush-stroke lines, gold leaf accents. Epic scale, dramatic lighting from upper left, volumetric god rays. High detail, gacha game character art, transparent background, 2:3 aspect ratio.
```

### Aether风格模板
```
Full-body character prompt. Aether world style: semi-realistic dark rendering, deep purple and void black palette, ethereal particle effects, cosmic horror atmosphere. Dramatic rim lighting from behind, subtle blue starlight from upper left. High detail, gacha game character art, transparent background, 2:3 aspect ratio.
```

### Ironveil风格模板
```
Full-body character prompt. Ironveil world style: hard-surface mechanical design, industrial realism, gunmetal and copper palette, rivets and panel lines, sparks and steam. Dramatic orange battle lighting from upper left. High detail, gacha game character art, transparent background, 2:3 aspect ratio.
```
