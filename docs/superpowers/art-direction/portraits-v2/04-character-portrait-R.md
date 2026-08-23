# R 角色立绘设计稿（7 位）— 标准规格

> 适用总规范：`00-master-spec.md` v2.0
> 画布：**832×1248 px**，PNG-24 + Alpha，2:3 竖版
> 头身比：4-5 头身（萌系 Q 版），主姿态库 P5/P6/P7/P8
> 阵营色：Shinwa `#9A6BFF`+`#E34234`+`#1FB6A6` / Aether `#C79BFF`+`#00E5FF`+`#E8E2F2` / Ironveil `#7A8A9A`+`#4A90D9`+`#C96A2E`
> 稀有度色：苍白 `#E8E2F2` 微光 + 点状高光

---

## 1. 狸力 LiLi · 遁地灵兽 · Shinwa · Earth

### 角色卡片
- **ID**: `char_r_lili`
- **联动**: 漫威蚁人缩放 + DC 原子侠原子操控
- **武器**: 蚁人腰带（缩放装置）+ 距爪（遁地工具）
- **氛围特效**: `dust_earth_burst`（尘土爆裂）
- **关键改进**: 现有立绘是好半身像但略显粗糙。v2 强化"可爱 + 灵性"双重气质。

### 识别锚点 ×3
- **A 神话原型**: 豚鼠形态（**豚鼠 + 狸猫混合，毛色棕黄**）
- **B 道具指纹**: 蚁人腰带（**腰部银色缩放装置腰带**）
- **C 解剖签名**: 距爪（**前爪粗壮有距，适合挖土**）

### 全身构图
- **主姿态**: P6 蹲伏潜行 + 半从地下探出 + 前爪举起张望
- **动态参数**: 上半身探出地面，下半身隐于土中；周围有泥土碎石
- **三段式**:
  - 上段：兽耳竖起 + 大眼睛 + 机警表情
  - 中段：棕色毛绒身躯 + 蚁人腰带 + 前爪举起
  - 下段：距爪后半身隐于土 + 泥土碎石 + 脚下土圈

### 视觉描述
- **物种**: 豚鼠 + 狸猫混合（小型，Q 版）
- **体型**: 圆胖可爱，5 头身比例（兽形按解剖）
- **毛色**: 棕黄色 + 浅黄色腹部
- **眼睛**: 大眼睛，棕色琥珀色（`#FFA500`），机灵
- **耳**: 圆形兽耳竖起
- **腰部**: 蚁人风格银色腰带（带缩放装置圆环）
- **前爪**: 粗壮有距（适合挖土），举起时显机警
- **身后**: 泥土隧道，碎石飞溅

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#9A6BFF` + `#1FB6A6` |
| 元素色 | `#795548` 大地褐 + `#FFD700` 蚁人银金 |
| 稀有度色 | `#E8E2F2` 苍白（腰带银边） |
| 情绪色 | `#00C853` 灵气绿（眼睛高光） |

### 生图 Prompt
```
Full-body character portrait of LiLi, the earth-burrowing spirit beast from Chinese mythology (Shanhaijing "looks like guinea pig with claws, sounds like dog bark") fused with Ant-Man size-shifting. Small cute creature, guinea pig + cat hybrid form (Q-version, 5-head proportion). Brown-yellow fur with light yellow belly, large amber eyes (#FFA500) alert and clever. Round beast ears upright. Ant-Man-style silver belt at waist (size-shifting device ring). Thick front paws with digging claws (distance claws), raised alertly. Half-emerging from underground, dirt and rubble around. Dynamic peek-out pose: upper body above ground, lower body still underground, front paws raised looking around.

Shinwa world style: cel-shaded anime with ink-wash outlines, earth tone palette with subtle gold tech accents. Soft underground lighting from above. Soft shadow edges (cute). Rim light from behind-lower using R pale white #E8E2F2. High detail especially on fur texture and belt. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body visible (even if half-emerging).

key_elements:
  - guinea_pig_cat_hybrid_form (mandatory, A-myth-archetype)
  - ant_man_silver_belt_size_device (mandatory, B-weapon-prop)
  - digging_claws_distance_feature (mandatory, C-anatomy)

negative_prompt: "human form, half-body, cropped, white background, modern clothing, sword, gun, text, watermark, scary, horror, dark palette"
```

### 透明背景处理要点
- 兽耳边缘保留 1-2px 羽化（毛发感）
- 飞溅碎石**单独抠出**为可选层
- 蚁人腰带硬切

---

## 2. 钦原 Qinyuan · 毒蜂刺羽 · Aether · Metal

### 角色卡片
- **ID**: `char_r_qinyuan`
- **联动**: 漫威黄蜂女蜂群战衣 + DC 黑金丝雀声波
- **武器**: 毒针手指 + 声波护镜
- **氛围特效**: `mechanical_bee_cloud`（机械蜂群）
- **改进重点**: 元素已是 Metal，强化"蜂形人"的灵巧与毒刺特征。

### 识别锚点 ×3
- **A 神话原型**: 鸟兽毒性（**蜂形人 + 触角头饰 + 蜂翼**）
- **B 道具指纹**: 毒针手指（**双手指尖为金色毒针**）
- **C 解剖签名**: 蜂纹黑黄条纹（**发色或服饰有黑黄蜂纹**）

### 全身构图
- **主姿态**: P4 悬停飞行 + 双手前伸（毒针准备刺出）
- **动态参数**: 身体悬浮；半透明机械蜂翼展开；周围小型蜂群环绕
- **三段式**:
  - 上段：触角头饰 + 复眼护镜 + 黑黄蜂纹发
  - 中段：蜂群战衣紧身衣 + 双手前伸 + 声波可视化
  - 下段：双腿悬浮 + 蜂翼 + 脚下蜂群

### 视觉描述
- **性别/体型**: 女性纤细，蜂形特征，5 头身
- **发型**: 深黄色 +黑色相间（蜂纹），有触角头饰（双触角）
- **眼睛**: 复眼护镜（黄蜂女风格），紫色或绿色
- **服饰**: 蜂群战衣紧身衣（黑黄条纹），半透明机械蜂翼
- **手指**: 毒针形（金色）
- **周围**: 小型机械蜂群环绕 + 声波可视化

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#C79BFF` + `#00E5FF` |
| 元素色 | `#FFD600` 蜂黄 + `#1A1A1A` 蜂黑 |
| 稀有度色 | `#E8E2F2` 苍白 |
| 情绪色 | `#8B0000` 毒红 |

### 生图 Prompt
```
Full-body character portrait of Qinyuan, the deadly stinging bird from Chinese mythology (Shanhaijing "stings animals to death, stings wood to wither") fused with Wasp swarm suit. Female figure 5-head proportion, slender, bee-like features. Deep yellow and black striped hair (bee pattern), antenna headpiece, compound-eye style goggles (Wasp style) in purple or green. Swarm suit style bodysuit with black-yellow stripes. Transparent mechanical bee wings. Fingers shaped as golden poison stingers. Small mechanical bee swarm surrounding, sound wave visualization. Dynamic hovering pose: body floating, hands forward (stingers ready to strike).

Aether world style: semi-realistic dark rendering, yellow-black bee palette with poison red accents. Dramatic dark lighting with yellow highlights. Soft shadow edges. Rim light from behind-lower using R pale white #E8E2F2. High detail especially on bee wing transparency and stinger. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet hovering visible.

key_elements:
  - antenna_headpiece_and_compound_eye_goggles (mandatory, A-myth-archetype)
  - golden_poison_stingers_fingers (mandatory, B-weapon-prop)
  - mechanical_bee_swarm_orbiting (mandatory, D-signature)

negative_prompt: "no antenna, no wings, half-body, cropped, white background, modern clothing, sword, gun, text, watermark, cute kawaii, plush toy"
```

### 透明背景处理要点
- 蜂翼边缘 2-3px 重度羽化（半透明）
- 触角边缘 1px 羽化
- 服饰边缘硬切

---

## 3. 跂踵 SiShu · 夜行游侠 · Ironveil · Shadow

### 角色卡片
- **ID**: `char_r_sishu`
- **联动**: 漫威蜘蛛侠蛛丝感应 + DC 夜翼杂技格斗
- **武器**: 蛛丝发射器 + 九尾（机械尾或布料尾）
- **氛围特效**: `night_city_shadows`（夜城阴影）
- **改进重点**: 现有立绘较粗糙。v2 强化"九尾 + 蛛丝 + 暗影"三重特征。

### 识别锚点 ×3
- **A 神话原型**: 鹊形九尾（**人形 + 九条尾巴**）
- **B 道具指纹**: 蛛丝发射器（**手腕双蛛丝发射器**）
- **C 解剖签名**: 复眼面罩（**蜘蛛侠风格的复眼面罩眼**）

### 全身构图
- **主姿态**: P6 蹲伏潜行 + 准备跳跃 + 九尾在身后展开
- **动态参数**: 身体蹲伏于墙边或屋顶，重心下压 30°；九条尾巴扇形展开
- **三段式**:
  - 上段：复眼面罩 + 深蓝短发 + 蛛网头罩
  - 中段：暗影战衣 + 双腕蛛丝发射器 + 蛛网环绕
  - 下段：蹲伏姿态 + 九条尾巴扇形 + 脚下阴影

### 视觉描述
- **性别/体型**: 男性/中性修长，5 头身
- **发型**: 深蓝色或黑色短发，蛛网纹样头罩覆盖
- **眼睛**: 白色复眼面罩眼（蜘蛛侠风格）
- **服饰**: 暗影战衣（蜘蛛侠 + 夜翼混合），深紫黑 `#1A0033` + 电光蓝 `#00BFFF`
- **九条尾巴**: 九条机械/布料尾巴（深紫黑 + 电光蓝边），扇形展开于身后
- **手腕**: 双腕蛛丝发射器
- **周围**: 蛛网环绕

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#7A8A9A` + `#4A90D9` |
| 元素色 | `#0D0221` 暗紫黑 + `#00BFFF` 电光蓝 |
| 稀有度色 | `#E8E2F2` 苍白 |
| 情绪色 | `#FFFFFF` 蛛丝白 |

### 生图 Prompt
```
Full-body character portrait of SiShu, the plague bird from Chinese mythology (Shanhaijing "looks like magpie with nine tails, where seen country has many plagues") reimagined as Ironveil acrobatic vigilante. Male/neutral figure, slender 5-head proportion, agile. Deep blue or black short hair with spider-web pattern hood, white compound mask eyes (Spider-Man style). Shadow bodysuit (Spider-Man/Nightwing hybrid) in dark purple-black (#1A0033) with electric blue (#00BFFF) accents. NINE tails fanning out behind (mechanical or fabric, dark with blue edges). Web shooters on both wrists, spider webs surrounding body. Dynamic crouching pose: on rooftop or wall corner, weight lowered 30 degrees, ready to leap, nine tails spread fan-shaped behind.

Ironveil world style: dark mechanical design, shadow purple-black and electric blue palette with white web accents. Dramatic night lighting from upper left. Soft shadow edges. Rim light from behind-lower using R pale white #E8E2F2. High detail especially on compound eyes and tail texture. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet on edge visible.

key_elements:
  - NINE_tails_fan_shaped_behind (mandatory, A-myth-archetype)
  - web_shooters_both_wrists (mandatory, B-weapon-prop)
  - compound_eye_white_mask (mandatory, D-signature)

negative_prompt: "no tails, single tail, half-body, cropped, white background, modern casual clothing, sword, gun, text, watermark, cute kawaii, plush toy, normal eyes"
```

### 透明背景处理要点
- 九尾边缘 1-2px 羽化（布料感）
- 蛛丝 1px 羽化
- 战衣边缘硬切

---

## 4. 蠃鱼 Luoyu · 渊海游灵 · Shinwa · Water

### 角色卡片
- **ID**: `char_r_luoyu`
- **联动**: 漫威海王纳摩深海之力 + DC 水行侠亚特兰蒂斯
- **武器**: 三叉戟（或水球）
- **氛围特效**: `underwater_bubble_light`（水下光泡）
- **改进重点**: 元素 Water，强化"半人半鱼"的海洋气质。

### 识别锚点 ×3
- **A 神话原型**: 鱼身鸟翼（**半人半鱼 + 鸟翼**）
- **B 道具指纹**: 三叉戟（或水球）（**亚特兰蒂斯风格三叉戟**）
- **C 解剖签名**: 鱼尾（**人鱼尾 + 鱼鳍**）

### 全身构图
- **主姿态**: P4 游泳飞翔 + 身体旋转 + 鱼尾/鸟翼展开
- **动态参数**: 鱼尾 S 形摆动；半透明冰翼展开；水泡与冰晶环绕
- **三段式**:
  - 上段：人首 + 水蓝长发（瀑布）+ 水母/珊瑚装饰
  - 中段：亚特兰蒂斯鳞甲 + 鸟翼 + 双手持三叉戟
  - 下段：鱼尾 + 鱼鳍 + 水泡光圈

### 视觉描述
- **性别/体型**: 女性半人半鱼，5 头身（Q 版）
- **发型**: 水蓝色长发如水流般飘散，瀑布效果
- **眼睛**: 深蓝或海绿色（`#0097A7`），深邃
- **服饰**: 亚特兰蒂斯风格鳞甲（`#0097A7` 深海青 + `#B2EBF2` 冰霜白），鱼鳍装饰 + 鸟翼（半透明冰翼）
- **下半身**: 鱼尾（深青色鳞片）+ 鱼鳍
- **手部**: 持三叉戟（亚特兰蒂斯风格）

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#9A6BFF` + `#1FB6A6` |
| 元素色 | `#0097A7` 深海青 + `#B2EBF2` 冰霜白 |
| 稀有度色 | `#E8E2F2` 苍白 |
| 情绪色 | `#FFD700` 亚特兰蒂斯金 |

### 生图 Prompt
```
Full-body character portrait of Luoyu, the fish-bird spirit from Chinese mythology (Shanhaijing "fish body, bird wings, sounds like mandarin duck") fused with Namor deep-sea power and Aquaman Atlantis abilities. Female figure 5-head proportion, half-human half-fish, Q-version cute. Water-blue long hair flowing like waterfall, jellyfish and coral decorations. Deep blue or sea-green (#0097A7) profound eyes. Atlantis-style scale armor in deep sea cyan (#0097A7) and ice frost white (#B2EBF2) with fish fin decorations and bird wings (semi-transparent ice wings). Fish tail with deep cyan scales, fish fins. Holding Atlantis-style trident. Bubbles and ice crystals surrounding. Dynamic swimming pose: body spinning, tail/wings spread.

Shinwa world style: cel-shaded anime with ink-wash outlines, deep sea cyan and ice frost white palette with Atlantis gold accents. Soft underwater lighting from above. Soft shadow edges. Rim light from behind-lower using R pale white #E8E2F2. High detail especially on scale texture and ice wing transparency. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with tail visible.

key_elements:
  - FISH_TAIL_with_scales (mandatory, A-myth-archetype)
  - bird_wings_ice_transparent (mandatory, A-myth-archetype)
  - atlantis_trident_held (mandatory, B-weapon-prop)

negative_prompt: "human legs only, no tail, no wings, half-body, cropped, white background, modern clothing, sword, gun, text, watermark, cute kawaii"
```

### 透明背景处理要点
- 鱼尾鳞片边缘 1-2px 羽化
- 冰翼边缘 2-3px 重度羽化（半透明）
- 水泡边缘重度羽化

---

## 5. 当康 Dangang · 丰穗瑞兽 · Aether · Wood

### 角色卡片
- **ID**: `char_r_dangang`
- **联动**: 漫威野兽蛮力 + DC 火星猎人兽性
- **武器**: 麦穗（或丰收之角）
- **氛围特效**: `harvest_wind_leaves`（丰收风叶）
- **改进重点**: 元素 Wood，强化"瑞兽 + 丰收"的可爱气质。

### 识别锚点 ×3
- **A 神话原型**: 豚形有牙（**豚形瑞兽 + 上翘野猪牙**）
- **B 道具指纹**: 麦穗（或丰收之角）（**手中举起金色麦穗**）
- **C 解剖签名**: 金色鬃毛（**鬃毛为金色麦穗质感**）

### 全身构图
- **主姿态**: P8 站立姿态 + 一手举起麦穗 + 威严但温和
- **动态参数**: 后腿站立，前爪举麦穗；周围金色光粒
- **三段式**:
  - 上段：兽头 + 上翘野猪牙 + 棕色鬃毛
  - 中段：兽身 + 麦穗铠甲 + 前爪举麦穗
  - 下段：后腿站立 + 鬃毛金穗 + 脚下丰收光圈

### 视觉描述
- **物种**: 豚形瑞兽（小型，Q 版）
- **体型**: 圆润壮实，5 头身（兽形按解剖）
- **毛色**: 棕色 + 金色鬃毛（鬃毛为麦穗质感）
- **眼睛**: 绿色或金色（`#FFD700`），野性但温和
- **牙**: 上翘野猪牙（白色）
- **服饰**: 植物/丰收主题铠甲（金色麦穗装饰），野兽风格
- **手部**: 前爪举麦穗（或丰收之角）
- **周围**: 金色光粒

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#C79BFF` + `#00E5FF` |
| 元素色 | `#795548` 兽褐 + `#FFD700` 丰穗金 |
| 稀有度色 | `#E8E2F2` 苍白 |
| 情绪色 | `#2E8B57` 生命绿 |

### 生图 Prompt
```
Full-body character portrait of Dangang, the harvest boar spirit from Chinese mythology (Shanhaijing "looks like guinea pig with tusks, calls its own name, where seen the world has great harvest") fused with Beast strength. Male spirit beast form, sturdy Q-version 5-head proportion (anatomical for beast), rounded and stout. Brown fur with golden mane (mane texture like wheat sheaves). Green or golden (#FFD700) wild but gentle eyes. White upturned boar tusks. Plant/harvest themed armor with golden wheat decorations, beast-style armor. Wild boar tusks feature, holding wheat sheaf or cornucopia in one front paw, golden light particles surrounding. Dynamic standing pose: hind legs standing, front paw raising wheat sheaf, majestic but gentle.

Aether world style: semi-realistic rendering, beast brown and harvest gold palette with life green accents. Warm golden sunlight lighting from upper left. Soft shadow edges. Rim light from behind-lower using R pale white #E8E2F2. High detail especially on wheat texture and fur. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet on ground visible.

key_elements:
  - guinea_pig_form_with_upturned_tusks (mandatory, A-myth-archetype)
  - golden_wheat_mane_texture (mandatory, C-anatomy)
  - wheat_sheaf_or_cornucopia_in_paw (mandatory, B-weapon-prop)

negative_prompt: "human form, half-body, cropped, white background, modern clothing, sword, gun, text, watermark, scary, horror, dark palette"
```

### 透明背景处理要点
- 鬃毛麦穗边缘 1-2px 羽化
- 麦穗**单独抠出**为可选层
- 兽身轮廓硬切

---

## 6. 山魈 Shanxiao · 机械林精 · Ironveil · Earth

### 角色卡片
- **ID**: `char_r_shanxiao`
- **联动**: 漫威火箭浣熊机械天赋 + DC 野兽小子野性本能
- **武器**: 改装机械爪 + 地雷（陷阱）
- **氛围特效**: `ruin_jungle_parts`（废墟丛林零件）
- **关键改进**: 本角色**v1 无设计稿**，v2 从零开始。需强化"机械 + 野性"双重气质。

### 识别锚点 ×3
- **A 神话原型**: 山林小鬼（**灵长类小型兽，猴子特征**）
- **B 道具指纹**: 机械爪（**右爪被改装为机械爪**）
- **C 解剖签名**: 藤蔓缠绕机械（**身体有藤蔓与废弃机械融合的痕迹**）

### 全身构图
- **主姿态**: P6 蹲伏潜行 + 调皮表情 + 机械爪前伸
- **动态参数**: 蹲伏于废墟机械上，重心下压；机械爪伸向镜头方向
- **三段式**:
  - 上段：猴面 + 调皮表情 + 眼罩（一侧机械义眼）
  - 中段：小型身躯 + 机械爪 + 藤蔓缠绕机械
  - 下段：蹲伏姿态 + 尾 + 脚下陷阱地雷

### 视觉描述
- **物种**: 山林小鬼（灵长类小型兽，猴子特征，Q 版）
- **体型**: 小巧灵活，5 头身
- **毛色**: 红棕色猴毛（山魈真实毛色）
- **眼睛**: 一侧为正常眼（黄色），另一侧为机械义眼（红色发光）
- **面**: 猴面 + 调皮表情（咧嘴）
- **右爪**: 改装机械爪（铁帷合金）
- **身体**: 局部有藤蔓缠绕与废弃机械融合（背部有一块锈铁板嵌入）
- **尾**: 长尾（猴尾）
- **周围**: 地雷（陷阱） + 废墟零件

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#7A8A9A` + `#4A90D9` |
| 元素色 | `#795548` 大地褐 + `#C96A2E` 锈橙 |
| 稀有度色 | `#E8E2F2` 苍白 |
| 情绪色 | `#FF4500` 义眼红 |

### 生图 Prompt
```
Full-body character portrait of Shanxiao, the mountain spirit from Chinese mythology (Shanhaijing "mountain monkey spirit, mischievous") reimagined as Ironveil mechanical jungle sprite fused with Rocket Raccoon mechanical talent. Small agile primate-like spirit beast, monkey-like features, Q-version 5-head proportion. Reddish-brown monkey fur, yellow normal eye on one side and red glowing mechanical prosthetic eye on other side. Monkey face with mischievous grin (showing teeth). Right paw modified as mechanical claw (Ironveil alloy). Body partially has vines winding with scrap metal fused (rust iron plate embedded in back). Long monkey tail. Land mines (traps) and scrap parts around. Dynamic crouching pose: on scrap machinery in ruins, weight lowered, mechanical claw reaching toward viewer, mischievous expression.

Ironveil world style: hard-surface mechanical design, gunmetal and rust orange palette with vine accents. Dramatic ruin lighting from upper left. Soft shadow edges (mischievous). Rim light from behind-lower using R pale white #E8E2F2. High detail especially on mechanical claw and scrap fusion. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet on scrap visible.

key_elements:
  - monkey_face_mischievous_grin (mandatory, A-myth-archetype)
  - mechanical_claw_right_paw (mandatory, B-weapon-prop)
  - vines_and_scrap_metal_fused_back (mandatory, C-anatomy)
  - mechanical_prosthetic_eye_one_side (mandatory, D-signature)

negative_prompt: "human form, half-body, cropped, white background, modern clothing, sword, gun, bow, text, watermark, cute kawaii in plush way, no mechanical parts"
```

### 透明背景处理要点
- 猴毛边缘 1-2px 羽化
- 藤蔓边缘 1-2px 羽化
- 机械爪硬切
- 陷阱地雷**单独抠出**为可选层

---

## 7. 夜叉 Yecha · 裂隙低语 · Aether · Shadow

### 角色卡片
- **ID**: `char_r_yecha`
- **联动**: 漫威夜魔侠感官增强 + DC 暗影侠黑暗潜行
- **武器**: 影子短刃
- **氛围特效**: `rift_shadow_motes`（裂隙阴影微粒）
- **关键改进**: 本角色**v1 无设计稿**，v2 从零开始。需强化"影子凝聚生物"的诡异 + 群体感。

### 识别锚点 ×3
- **A 神话原型**: 影子凝聚（**身体由影子凝聚，可半透明**）
- **B 道具指纹**: 影子短刃（**手中握影子短刃**）
- **C 解剖签名**: 紫光瞳孔（**面部只有紫色光点的双眼，其余为影子**）

### 全身构图
- **主姿态**: P6 蹲伏潜行 + 短刃前指 + 阴影中现身
- **动态参数**: 半蹲于阴影中，身体由阴影凝聚；紫色光点双眼
- **三段式**:
  - 上段：影子头部 + 紫色光点双眼 + 阴影面具
  - 中段：影子躯干（半透明）+ 影子短刃 + 紫色光带
  - 下段：蹲伏姿态 + 影子拖尾 + 脚下紫色光圈

### 视觉描述
- **物种**: 影子凝聚生物（半透明人形 + 恶魔角）
- **体型**: 修长阴郁，5 头身
- **身体**: 由暗物质/影子凝聚，半透明（可见背后的裂隙光）
- **头部**: 影子头盔 + 短角 + 紫色光点双眼（其余面部漆黑）
- **服饰**: 阴影披风（半透明影子） + 紫光纹边
- **手部**: 握影子短刃（半透明，刃缘紫色发光）
- **拖尾**: 身后拖曳影子尾迹（半透明）
- **脚下**: 紫色光圈

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#C79BFF` + `#00E5FF` |
| 元素色 | `#0D0221` 暗紫黑 + `#32CD32` 毒绿（紫光瞳孔偏紫） |
| 稀有度色 | `#E8E2F2` 苍白 |
| 情绪色 | `#7B2CBF` 裂隙紫 |

### 生图 Prompt
```
Full-body character portrait of Yecha, the rift whisper from Aether mythology (low-level void creature formed from lost souls' shadows in rifts) fused with Daredevil sensory enhancement and Shadowhawk dark stealth. Shadow-condensed being, slender gloomy 5-head proportion. Body composed of dark matter/shadow, semi-transparent (rift light visible behind). Shadow helmet with short horns, only purple light dots for eyes (rest of face pitch black). Shadow cape (semi-transparent) with purple light pattern edges. Holding shadow short blade (semi-transparent, purple glowing edge). Shadow trail extending behind. Purple light circle below feet. Dynamic lurking pose: half-crouching in shadow, short blade pointing forward, emerging from shadow, group consciousness sense.

Aether world style: semi-realistic dark rendering, shadow purple-black palette with purple-green accents. Dramatic dark lighting with purple highlights from upper left. Soft shadow edges (ethereal). Rim light from behind-lower using R pale white #E8E2F2. High detail especially on shadow transparency and purple light edges. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with crouching feet visible.

key_elements:
  - shadow_condensed_body_semi_transparent (mandatory, A-myth-archetype)
  - shadow_short_blade_purple_edge (mandatory, B-weapon-prop)
  - purple_light_dot_eyes_only (mandatory, C-anatomy)
  - shadow_trail_extending_behind (mandatory, D-signature)

negative_prompt: "fully solid body, normal eyes visible, half-body, cropped, white background, modern clothing, gun, sword, text, watermark, cute kawaii, plush toy, bright palette"
```

### 透明背景处理要点
- 身体阴影保留 3-5px 重度羽化（半透明）
- 短刃紫光边缘 1-2px 羽化
- 紫光瞳孔硬切（保留锐度）

---

## R 总览对照表

| 角色 | 世界 | 元素 | 主姿态 | 识别锚点（3 项） | 关键改进 |
|------|------|------|--------|------------------|----------|
| 狸力 | Shinwa | Earth | P6 蹲伏潜行 | 豚鼠形 / 蚁人腰带 / 距爪 | 强化可爱灵性 |
| 钦原 | Aether | Metal | P4 悬停飞行 | 触角头饰 / 毒针 / 蜂群 | 蜂形人完整化 |
| 跂踵 | Ironveil | Shadow | P6 蹲伏潜行 | 九尾 / 蛛丝器 / 复眼 | 三特征强化 |
| 蠃鱼 | Shinwa | Water | P4 游泳飞翔 | 鱼尾 / 冰翼 / 三叉戟 | 海洋气质 |
| 当康 | Aether | Wood | P8 站立姿态 | 豚形有牙 / 金麦鬃 / 麦穗 | 瑞兽可爱化 |
| 山魈 | Ironveil | Earth | P6 蹲伏潜行 | 猴面 / 机械爪 / 藤蔓机械融合 | 新角色，调皮机械 |
| 夜叉 | Aether | Shadow | P6 蹲伏潜行 | 影子凝聚 / 影子短刃 / 紫光瞳孔 | 新角色，诡异群体感 |

## R 通用 QC 备注
- 头身比 4-5 头身（Q 版萌系）—— 比 SSR 更萌，但不要失去"成年感"
- 兽形角色（狸力/当康/蠃鱼半鱼）按生物解剖学处理，不强制头身比
- 稀有度色苍白 `#E8E2F2` 仅作微光与点状高光，不抢阵营色与元素色
- 主光源与 UR/SSR/SR 一致（左上方 7 点钟）
- 透明背景在浅色 UI 上要"轻"——R 角色本身就要轻盈不抢戏

---

## 生图批量顺序（建议）

1. **山魈**（新角色，调皮表情 + 机械爪容易"过萌"或"过硬"，需多 seed）
2. **夜叉**（新角色，影子半透明处理是技术挑战）
3. **蠃鱼**（半鱼半人，鱼尾解剖需测试）
4. **跂踵**（九尾 + 蛛丝 + 蹲伏，复合特征）
5. **钦原**（蜂翼透明度 + 触角头饰）
6. **狸力**（豚鼠形 + 蚁人腰带）
7. **当康**（瑞兽可爱化，麦穗质感）

---

## 7 个稀有度档位总对照（最终检查用）

| 稀有度 | 画布 | 头身比 | 稀有度色 | 主姿态库 |
|--------|------|--------|----------|----------|
| UR | 1536×2304 | 7-8 头身 | 熔金 `#FFC857` | P1/P2/P3 |
| SSR | 1280×1920 | 6-7 头身 | 暮紫 `#C79BFF` | P2/P3/P4 |
| SR | 1024×1536 | 5-6 头身 | 霜蓝 `#7FC4FF` | P4/P5/P6/P7/P8 |
| R | 832×1248 | 4-5 头身 | 苍白 `#E8E2F2` | P5/P6/P7/P8 |

**5 + 5 + 5 + 5 = 20 角色覆盖（v1 旧版）**  
**7 + 7 + 7 + 7 = 28 角色覆盖（v2 当前）** — 完整！