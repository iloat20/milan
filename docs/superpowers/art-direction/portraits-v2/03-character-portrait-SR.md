# SR 角色立绘设计稿（7 位）— 中规格

> 适用总规范：`00-master-spec.md` v2.0
> 画布：**1024×1536 px**，PNG-24 + Alpha，2:3 竖版
> 头身比：5-6 头身，主姿态库 P4/P5/P6/P7/P8
> 阵营色：Shinwa `#9A6BFF`+`#E34234`+`#1FB6A6` / Aether `#C79BFF`+`#00E5FF`+`#E8E2F2` / Ironveil `#7A8A9A`+`#4A90D9`+`#C96A2E`
> 稀有度色：霜蓝 `#7FC4FF` 装备边缘 + 能量线

---

## 1. 狻猊 Suanni · 狮吼震魂 · Shinwa · Flame

### 角色卡片
- **ID**: `char_sr_suanni`
- **联动**: 漫威黑豹振金战甲 + DC 蝙蝠侠战术智慧
- **武器**: 狮吼震魂（声波武器，咆哮即是攻击）
- **氛围特效**: `pride_aura_flame`（王者烈焰气场）
- **关键改进**: **现有立绘是野猪穿臂铠（设计稿说狮面人身），完全脱节。v2 必须强制"狮面人身" + 振金战甲 + 披风。**

### 识别锚点 ×3
- **A 神话原型**: 狮面人身（**必须是狮头人身，金鬃环绕，**绝不**是猪**）
- **B 道具指纹**: 振金战甲（**黑豹风格 + 蝙蝠侠披风**）
- **C 解剖签名**: 金色鬃毛（**金黑条纹狮鬃，鬓角垂至肩**）

### 全身构图
- **主姿态**: P8 咆哮震慑 + 双臂张开 + 披风飞扬
- **动态参数**: 狮嘴张开咆哮（声波可视化同心圆）；披风向后大幅飞扬
- **三段式**:
  - 上段：狮头 + 金鬃飞扬 + 声波同心圆（半透明）
  - 中段：狮胸 + 振金战甲 + 双臂张开 + 蝙蝠披风扬起
  - 下段：双腿站姿 + 火焰气场 + 脚下光圈

### 视觉描述
- **物种**: 狮面人身（**狮头绝对不能错**），男性身形
- **体型**: 肌肉壮硕，6 头身
- **头部**: 狮头，金色与黑色条纹鬃毛，鬓角垂至肩；金色猫眼
- **振金战甲**: 黑豹风格，紫黑色 `#1A1A1A` 振金战甲，表面有金色电路纹路（`#FFD700`）
- **披风**: 蝙蝠侠风格深蓝披风 `#1E90FF`，内侧为深蓝，外侧为振黑
- **腰带**: 战术腰带（蝙蝠侠风格，多功能袋）
- **手部**: 狮爪手套（5 指金爪）
- **咆哮**: 嘴张开可见尖牙，声波同心圆从嘴部扩散

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#9A6BFF` + `#1FB6A6` |
| 元素色 | `#FF4D00` 熔岩橙 + `#FFD700` 金 |
| 稀有度色 | `#7FC4FF` 霜蓝（披风内侧、装备边缘） |
| 情绪色 | `#FFD700` 狮眼金 |

### 生图 Prompt
```
Full-body character portrait of Suanni, a golden LION-HEADED beast warrior — his head is a true lion's head with round golden cat eyes and a thick golden mane with black stripes (never a wolf head, never a tiger, never a pig, no other animal snout) — fused with Black Panther vibranium armor and Batman tactical gear. Muscular male human body. Wearing Black Panther-style vibranium armor (#1A1A1A) with golden circuit lines, plus Batman-style dark blue cape (#1E90FF) and tactical utility belt. Lion claw gauntlets (5 golden claws). Dynamic roar pose: arms spread wide, cape billowing behind, lion mouth open roaring with visible fangs.

Shinwa world style: cel-shaded anime with ink-wash outlines, black and gold palette with blue cape accent. Dramatic warm lighting from upper left. Soft shadow edges. Rim light from behind-lower using SR frost blue #7FC4FF. High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet visible.

key_elements:
  - LION_HEAD_not_pig_not_wolf (mandatory, A-myth-archetype)
  - vibranium_armor_gold_circuits (mandatory, B-costume-signature)
  - batman_cape_dark_bown (mandatory, D-signature)
  - golden_lion_mane_black_stripes (mandatory, C-anatomy)

negative_prompt:
  "PIG HEAD, BOAR HEAD, WOLF HEAD, TIGER HEAD, BEAR HEAD,
   half-body, bust, cropped, white background, modern clothing,
   text, watermark, cute kawaii, human head, helmet"
```

### 透明背景处理要点
- 鬃毛边缘保留 1-2px 羽化（毛发感）
- 披风边缘保留 2-3px 重度羽化（飘动感）
- 振金战甲边缘硬切
- 声波同心圆**单独抠出**为可选层

---

## 2. 精卫 Jingwei · 衔石填海 · Aether · Wood

### 角色卡片
- **ID**: `char_sr_jingwei`
- **联动**: 漫威黑寡妇坚韧 + DC 猫女敏捷
- **武器**: 衔石填海（手中握石 + 鸟翼飞行）
- **氛围特效**: `sea_stone_mist`（海石雾）
- **关键改进**: 元素已改为 Wood，强化"衔石"动作的视觉表达。

### 识别锚点 ×3
- **A 神话原型**: 神鸟精卫（**人形 + 背后鸟翼，发梢有羽毛质感**）
- **B 道具指纹**: 填海之石（**手中紧握的青灰色巨石**）
- **C 解剖签名**: 黑长直发（**深蓝黑色长发，发梢羽毛质感渐变**）

### 全身构图
- **主姿态**: P4 飞行俯冲 + 身体水平前倾 25° + 双手握石准备投掷
- **动态参数**: 背后半透明鸟翼展开；发梢羽毛质感；衣摆飘带向后方
- **三段式**:
  - 上段：鸟翼展开 + 黑长直发（带羽毛质感）+ 投掷姿态
  - 中段：黑色紧身战斗服 + 双手握石 + 猫女护臂
  - 下段：长靴 + 脚下海石雾 + 飞行姿态

### 视觉描述
- **性别/体型**: 女性纤细灵巧，6 头身
- **发型**: 深蓝黑色长发，发梢渐变为羽毛质感（青色）
- **眼睛**: 深蓝紫色（`#4B0082`），坚定不屈
- **服饰**: 黑色紧身战斗服（黑寡妇风格），猫女风格护臂和长靴，肩部有羽翼装饰
- **鸟翼**: 背后半透明鸟翼（深蓝灰色）
- **双手**: 紧握填海之石（青灰色，拳头大）
- **周围**: 小石块漂浮环绕（投掷预备）

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#C79BFF` + `#00E5FF` |
| 元素色 | `#2E8B57` 翡翠绿（木）+ `#B9F6CA` 嫩芽 |
| 稀有度色 | `#7FC4FF` 霜蓝 |
| 情绪色 | `#1A1A2E` 深夜蓝（服饰主色） |

### 生图 Prompt
```
Full-body character portrait of Jingwei, the mythical bird from Chinese mythology (Yandi's daughter drowned in Eastern Sea, became bird filling sea with stones) fused with Black Widow resilience and Catwoman agility. Female figure 6-head proportion, slender and agile, long dark blue-black hair with feather-texture tips (cyan gradient at tips), deep blue-purple determined eyes. Wearing black tactical bodysuit with Catwoman-style gauntlets and boots, shoulder feather decorations. Semi-transparent bird wings (dark blue-gray) on back. Holding a fist-sized stone tightly in both hands (sea-stone, blue-gray). Small floating stones orbiting around. Dynamic flying pose: body horizontal leaning forward 25 degrees, ready to throw.

Aether world style: semi-realistic dark rendering, deep blue and storm palette with green wood-element accents. Ethereal wind effects. Dramatic ocean lighting from upper left. Soft shadow edges. Rim light from behind-lower using SR frost blue #7FC4FF. High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet visible.

key_elements:
  - feather_tips_hair_blue_cyan_gradient (mandatory, A-myth-archetype)
  - sea_stone_held_tightly_both_hands (mandatory, B-weapon-prop)
  - semi_transparent_bird_wings (mandatory, D-signature)

negative_prompt: "no wings, no stone, half-body, cropped, white background, modern clothing, bright colors, warm tones, text, watermark, cute kawaii"
```

### 透明背景处理要点
- 鸟翼边缘保留 2-3px 羽化（半透明）
- 发梢羽毛渐变 1-2px 羽化
- 服饰边缘硬切
- 漂浮石块**单独抠出**为可选层

---

## 3. 穷奇 Qiongqi · 噬罪凶兽 · Ironveil · Metal

### 角色卡片
- **ID**: `char_sr_qiongqi`
- **联动**: 漫威死侍再生 + DC 丧钟精准射击
- **武器**: 机械利爪 + 背式武器架（枪械）+ 金属鞭尾
- **氛围特效**: `blood_metal_smoke`（血金属烟）
- **关键改进**: 现有立绘是狮头战士，需校正为"牛角 + 蝟毛 + 机械"的混合形态。

### 识别锚点 ×3
- **A 神话原型**: 牛身蝟毛（**牛角 + 全身金属尖刺蝟毛**）
- **B 道具指纹**: 武器架（**背部武器架多把枪械**）
- **C 解剖签名**: 丧钟瞄准镜（**右眼或左眼戴瞄准镜罩**）

### 全身构图
- **主姿态**: P2 战阵冲锋 + 机械利爪前伸 + 金属刺竖起
- **动态参数**: 牛角前顶，金属刺全部竖立；尾鞭横扫
- **三段式**:
  - 上段：牛角 + 瞄准镜眼罩 + 死侍风格红色面甲
  - 中段：半牛半机械躯干 + 武器架（枪械）+ 机械利爪
  - 下段：金属刺尾 + 金属鞭尾 + 脚下血雾

### 视觉描述
- **物种**: 半牛半机械兽形
- **头部**: 牛角（双角，向前弯），右眼戴丧钟瞄准镜罩，左眼红色光学传感器
- **面甲**: 死侍风格红色面甲（覆盖鼻以下）
- **身体**: 半牛半机械，肌肉发达 + 金属装甲覆盖
- **蝟毛**: 全身金属尖刺（"蝟毛"特征的金属化）
- **手部**: 机械利爪（4 指 + 1 拇指）
- **背部**: 武器架，多把枪械（手枪、步枪）
- **尾巴**: 金属鞭尾（多节可弯曲）

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#7A8A9A` + `#C96A2E` |
| 元素色 | `#4A4A5A` + `#FF4500` 死侍红 |
| 稀有度色 | `#7FC4FF` 霜蓝 |
| 情绪色 | `#FF4500` 瞄准镜红 |

### 生图 Prompt
```
Full-body character portrait of Qiongqi, the evil-devouring beast from Chinese mythology (Shanhaijing "looks like ox, hedgehog quills, eats evil people") fused with Deadshot regeneration and Deathstroke precision. Half-bull half-mechanical beast form. Bull horns (two, curving forward) extending from head, head partially covered with metal armor, right eye covered by Deathstroke-style scope eyepatch, left eye red optical sensor. Deadshot-style red face mask covering below nose. Mechanical beast body with metal armor and sharp metal spikes (metal quills — hedgehog feature). Mechanical claws. Weapon rack on back (multiple firearms: pistol, rifle). Metal whip tail (multi-segment). Dynamic pounce pose: claws forward, metal spikes raised, attacking forward.

Ironveil world style: hard-surface mechanical design, gunmetal and weapon-red palette. Dramatic combat lighting from upper left. Hard shadow edges. Rim light from behind-lower using SR frost blue #7FC4FF. High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet on ground visible.

key_elements:
  - bull_horns_curving_forward (mandatory, A-myth-archetype)
  - metal_quills_hedgehog_feature (mandatory, A-myth-archetype)
  - weapon_rack_back_firearms (mandatory, B-weapon-prop)
  - scope_eyepatch_one_eye (mandatory, D-signature)

negative_prompt: "no horns, no quills, human form, half-body, cropped, white background, sword only, cute kawaii, plush toy, text, watermark"
```

### 透明背景处理要点
- 金属尖刺边缘硬切
- 血雾边缘保留 3-5px 重度羽化
- 武器架枪械边缘硬切

---

## 4. 旋龟 Xuanwu · 玄甲守护 · Shinwa · Earth

### 角色卡片
- **ID**: `char_sr_xuanwu`
- **联动**: 漫威钢力士钢躯 + DC 钢骨机械防护
- **武器**: 玄甲盾（龟壳形状）+ 虺蛇尾（机械蛇尾）
- **氛围特效**: `jade_shield_earth`（玉盾大地）
- **关键改进**: 元素是 Earth（大地褐），强化"守护"的视觉表达。

### 识别锚点 ×3
- **A 神话原型**: 龟身鸟首虺尾（**鸟首 + 龟壳 + 蛇尾三位一体**）
- **B 道具指纹**: 玄甲龟壳（**龟壳形状的巨型盾牌/背包，钢力士风格**）
- **C 解剖签名**: 蛇尾（**虺蛇形机械蛇尾**）

### 全身构图
- **主姿态**: P5 防御姿态 + 龟壳盾牌前举 + 蛇尾盘绕
- **动态参数**: 身体侧立，重心下沉；龟壳盾牌在前；蛇尾盘绕于腿侧
- **三段式**:
  - 上段：鸟首 + 冠羽 + 金属头盔
  - 中段：龟壳盾牌（巨型在前）+ 双手变形武器 + 战甲
  - 下段：蛇尾盘绕 + 脚下大地气场

### 视觉描述
- **物种**: 半龟半机械 + 鸟首 + 蛇尾
- **头部**: 鸟首特征（有冠羽），金属头盔覆盖
- **眼睛**: 翡翠绿（`#2E8B57`），沉稳
- **龟壳**: 巨型龟壳盾牌在前（钢力士风格金属质感），上有发光电路纹路
- **身体**: 部分机械装甲覆盖（钢骨风格）
- **蛇尾**: 虺蛇形机械蛇尾，绿色金属鳞片
- **手部**: 可变形武器（当前形态为重剑 + 盾）

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#9A6BFF` + `#1FB6A6` |
| 元素色 | `#795548` 大地褐 + `#00BFFF` 能量蓝 |
| 稀有度色 | `#7FC4FF` 霜蓝 |
| 情绪色 | `#2E8B57` 玄青 |

### 生图 Prompt
```
Full-body character portrait of Xuanwu, the black tortoise from Chinese mythology (Shanhaijing "bird head, snake tail, sound like chopping wood") fused with Colossus steel body and Cyborg mechanical defense. Half-tortoise half-mechanical figure, sturdy build. Bird-like head with crest feathers, metal helmet covering head, jade green (#2E8B57) calm eyes. Giant tortoise-shell shaped shield/backpack (Colossus style metal), shell with glowing blue circuit patterns. Body partially covered with mechanical armor (Cyborg style). Snake-like mechanical tail with green metal scales coiling around legs. Hands transformable weapons (currently heavy sword + small shield). Defensive stance: shell shield forward, body in 3/4 turn, weight lowered, standing firm.

Shinwa world style: cel-shaded anime with ink-wash outlines, earth tone and jade green palette with energy blue accents. Dramatic ground lighting from upper left. Soft shadow edges. Rim light from behind-lower using SR frost blue #7FC4FF. High detail especially on shell patterns and mechanical joints. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet on ground visible.

key_elements:
  - bird_head_crest_feathers_metal_helmet (mandatory, A-myth-archetype)
  - giant_tortoise_shell_shield_glowing_circuits (mandatory, B-weapon-prop)
  - mechanical_snake_tail_coiling (mandatory, A-myth-archetype)

negative_prompt: "no shell, no tail, normal human form, half-body, cropped, white background, modern clothing, sword only, gun only, text, watermark, cute kawaii"
```

### 透明背景处理要点
- 龟壳边缘硬切
- 蛇尾鳞片 1-2px 羽化
- 大地气场边缘重度羽化

---

## 5. 毕方 Bifang · 焚羽烈鸟 · Aether · Thunder

### 角色卡片
- **ID**: `char_sr_bifang`
- **联动**: 漫威猎鹰翼装 + DC 鹰女 N 金属羽翼
- **武器**: N 金属羽翼（同时是飞行器与武器）
- **氛围特效**: `storm_feather_spark`（羽暴风火）
- **关键改进**: 元素 Thunder，强化雷电 + 火焰的双重视觉。

### 识别锚点 ×3
- **A 神话原型**: 一足鹤身（**单足站立，另一足收拢**）
- **B 道具指纹**: N 金属羽翼（**金属质感羽翼 + 雷电缠绕**）
- **C 解剖签名**: 银白色短发（**羽毛质感短发**）

### 全身构图
- **主姿态**: P5 单足站立 + 羽翼半展 + 雷电交加
- **动态参数**: 单足立于岩石（或电塔），羽翼金属质感，火焰与电弧混合
- **三段式**:
  - 上段：银白短发 + 羽翼金属质感 + 雷电缠绕
  - 中段：翼装紧身衣 + 双臂张开 + 火焰电弧
  - 下段：单足站立 + 另一足收拢 + 脚下火雷圈

### 视觉描述
- **性别/体型**: 女性修长，鸟形特征，6 头身
- **发型**: 银白色短发，羽毛质感
- **眼睛**: 橙红色（`#FF4500`），锐利
- **服饰**: 翼装紧身衣（猎鹰风格，金属灰 `#455A64`）
- **羽翼**: N 金属羽翼（鹰女风格，金属质感 + 雷电缠绕 + 火焰边）
- **单足站立**: 左足踏于岩石（或电塔），右足收拢于身前
- **周围**: 火焰与电弧混合

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#C79BFF` + `#00E5FF` |
| 元素色 | `#FFD600` 雷电金 + `#FF4500` 火焰红 |
| 稀有度色 | `#7FC4FF` 霜蓝 |
| 情绪色 | `#FFFFFF` 雷电高光 |

### 生图 Prompt
```
Full-body character portrait of Bifang, the one-legged fire bird from Chinese mythology (Shanhaijing "one leg, crane body, where seen, fire") fused with Falcon wing-suit and Hawkgirl N-metal wings. Female figure 6-head proportion, slender, bird-like features. Silver-white short hair with feather texture, orange-red sharp eyes. Wing-suit style bodysuit (Falcon style) in metal gray (#455A64). N-metal wings (Hawkgirl style) with metal texture wrapped in lightning and flame edges. Standing on one leg on rock or electric tower, other leg retracted. Wings half-spread. Surrounded by flames and electric arcs. Dynamic one-leg stance with wings spread, lightning and fire mixing around.

Aether world style: semi-realistic dark rendering, metal gray and lightning gold palette with fire red accents. Dramatic storm lighting from upper left. Soft shadow edges. Rim light from behind-lower using SR frost blue #7FC4FF. High detail especially on wing metal texture and lightning. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with one foot planted visible.

key_elements:
  - one_leg_standing_other_leg_retracted (mandatory, A-myth-archetype)
  - N_metal_wings_lightning_fire_edges (mandatory, B-weapon-prop)
  - silver_white_feather_hair (mandatory, C-anatomy)

negative_prompt: "two legs standing equally, no wings, half-body, cropped, white background, modern clothing, text, watermark, cute kawaii, plush toy"
```

### 透明背景处理要点
- 雷电边缘 1-2px 羽化
- 火焰边缘 2-3px 羽化
- 羽翼金属部分硬切

---

## 6. 花妖 Huayao · 千瓣灵魅 · Aether · Wood

### 角色卡片
- **ID**: `char_sr_huayao`
- **联动**: 漫威暴风女大气操控 + DC 毒藤女植物共鸣
- **武器**: 千瓣飘带（花瓣凝聚的飘带与飞刃）
- **氛围特效**: `floating_garden_petals`（浮空花瓣）
- **关键改进**: 本角色**v1 无设计稿**，v2 从零开始。需强化"花精"的灵性与治愈/毒性双重气质。

### 识别锚点 ×3
- **A 神话原型**: 千年灵植化形（**头顶/发间有花瓣与藤蔓生长**）
- **B 道具指纹**: 千瓣飘带（**花瓣凝聚的飘带，可作治愈光带或毒刺飞刃**）
- **C 解剖签名**: 紫红毒刺纹身（**手臂有紫红色藤蔓刺青，战斗时浮现**）

### 全身构图
- **主姿态**: P7 祈祷/治愈 + 双手捧发光花苞 + 身体微微悬浮
- **动态参数**: 身后有藤蔓飘带环绕；脚下有花瓣光圈；空气中花瓣飞舞
- **三段式**:
  - 上段：花瓣头饰 + 黑长直发（发间有藤蔓）+ 紫红色眼影
  - 中段：花瓣裙（粉白紫渐变）+ 双手捧花苞 + 藤蔓纹身浮现
  - 下段：藤蔓飘带环绕 + 脚部悬浮 + 花瓣光圈

### 视觉描述
- **性别/体型**: 女性化灵体，6 头身，柔美
- **发型**: 黑长直发，发间生长白色与粉色花瓣；头顶花环（玫瑰、牡丹、莲花组合）
- **眼睛**: 紫红色瞳孔，眼影紫红色
- **服饰**: 花瓣裙（粉白紫渐变），上身轻纱，肩部有藤蔓披肩
- **纹身**: 双臂内侧有紫红色藤蔓刺青，**平时浅淡，战斗/治愈时浮现发光**
- **双手**: 捧发光花苞（粉色光，治疗时为粉白光，攻击时为紫红毒光）
- **飘带**: 千瓣飘带环绕身后，可作治愈或毒刃

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#C79BFF` + `#00E5FF` |
| 元素色 | `#2E8B57` 翡翠绿 + `#B9F6CA` 嫩芽 |
| 稀有度色 | `#7FC4FF` 霜蓝 |
| 情绪色 | `#FF69B4` 粉红（治愈）+ `#8B008B` 紫红（毒） |

### 生图 Prompt
```
Full-body character portrait of Huayao, the thousand-petal spirit plant from Aether mythology (plant spirit formed from ley energy in rift) fused with Storm atmospheric control and Poison Ivy plant communion. Feminine spirit figure 6-head proportion, soft and graceful. Long straight black hair with white and pink petals growing among strands, flower crown on head (rose + peony + lotus). Purple-red eye shadow, purple-red pupils. Petal dress (pink-white-purple gradient), light gauze top, vine shoulder cape. Vine tattoos on inner arms (purple-red, faint normally, glowing during combat). Hands holding glowing flower bud (pink light for healing, purple-red light for poison). Thousand-petal ribbons swirling behind body (healing light bands or poison blade ribbons). Dynamic healing pose: floating slightly, hands holding flower bud, ribbons swirling, petals floating in air.

Aether world style: semi-realistic rendering, jade green and purple-pink palette with petal accents. Soft ethereal lighting from upper left. Soft shadow edges (graceful). Rim light from behind-lower using SR frost blue #7FC4FF. High detail especially on flower textures and ribbons. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet hovering visible (no ground).

key_elements:
  - flower_crown_and_petals_in_hair (mandatory, A-myth-archetype)
  - thousand_petal_ribbons_swirling (mandatory, B-weapon-prop)
  - vine_tattoos_inner_arms_glowing (mandatory, D-signature)
  - glowing_flower_bud_in_hands (mandatory, B-pose-signature)

negative_prompt: "no flowers, no petals, normal dress, half-body, cropped, white background, modern clothing, weapon (sword/gun), text, watermark, cute kawaii, plush toy"
```

### 透明背景处理要点
- 花瓣边缘保留 2-3px 重度羽化（飘散感）
- 飘带边缘 2px 羽化
- 纹身发光边缘 1-2px 羽化

---

## 7. 饕餮 Taotie · 贪食无厌 · Shinwa · Flame

### 角色卡片
- **ID**: `char_sr_taotie`
- **联动**: 漫威毒液吞噬渴望 + DC 所罗门·格兰迪无穷饥饿
- **武器**: 巨口烈焰（吞食一切化为青铜焰）
- **氛围特效**: `broken_bronze_ash`（碎青铜灰烬）
- **关键改进**: 本角色**v1 无设计稿**，v2 从零开始。需强化"有首无身、永不餍足"的贪食气质。

### 识别锚点 ×3
- **A 神话原型**: 有首无身（**只有巨大兽头，没有身体**——以青铜巨口为视觉中心）
- **B 道具指纹**: 青铜巨口（**巨口含青铜齿与青铜色火焰**）
- **C 解剖签名**: 饕餮纹（**青铜鼎上的饕餮纹浮雕装饰**）

### 全身构图
- **主姿态**: P8 咆哮震慑 + 巨口张开到极致 + 青铜色火焰喷出
- **动态参数**: 巨口占据画面 60% 宽度；青铜色火焰漩涡从口内喷出；无身体，仅以火焰漩涡为"身"
- **三段式**:
  - 上段：兽头 + 饕餮纹角 + 青铜鼎浮雕装饰
  - 中段：巨口 + 青铜齿 + 青铜色火焰（吞食特效：漩涡吸收）
  - 下段：火焰漩涡身体 + 脚下碎青铜灰烬

### 视觉描述
- **物种**: 兽首无身（山海经原文："有首无身"）
- **头部**: 巨大兽头（直径约为身高 1/2），青铜色 `#B87333` + 古铜绿 `#1FB6A6` 双色
- **角**: 饕餮纹角（青铜色，分叉 2 次）
- **眼睛**: 贪婪金色（`#FFD700`），无底洞般的黑色瞳孔
- **巨口**: 占据头部 70% 面积，张开到极致；青铜齿（`#B87333`）；口内有青铜色火焰漩涡
- **"身体"**: 由青铜色火焰漩涡组成，从口部向外延伸
- **周围**: 漂浮的青铜碎片（被吞噬的青铜器残骸）
- **脚下**: 碎青铜灰烬漂浮

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#9A6BFF` + `#E34234` |
| 元素色 | `#FF4D00` 熔岩橙 + `#B87333` 紫铜 |
| 稀有度色 | `#7FC4FF` 霜蓝 |
| 情绪色 | `#FFD700` 贪婪金 |

### 生图 Prompt
```
Full-body character portrait of Taotie, the gluttonous beast from Chinese mythology (Shanhaijing "has head but no body, never satisfied with food") fused with Venom consuming desire and Solomon Grundy endless hunger. Giant beast head only (no body, diameter ~1/2 figure height), bronze color (#B87333) and antique copper green (#1FB6A6) dual-tone. Taotie-pattern horns (bronze, branching 2 times). Greedy golden eyes (#FFD700), bottomless black pupils. Giant mouth occupies 70% of head area, gaping to extreme; bronze teeth (#B87333); bronze-colored fire vortex inside mouth. "Body" composed of bronze fire vortex extending from mouth outward. Floating bronze fragments (consumed bronze vessel remains). Floating broken bronze ash below. Dynamic devouring pose: mouth wide open, bronze fire vortex spewing, greedy and eternal.

Shinwa world style: cel-shaded anime with ink-wash outlines, bronze and antique copper palette with flame accents. Dramatic fire lighting from upper left. Hard shadow edges (theatrical). Rim light from behind-lower using SR frost blue #7FC4FF. High detail especially on bronze teeth and fire texture. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full "body" with flame vortex tail visible.

key_elements:
  - GIANT_BEAST_HEAD_ONLY_no_body (mandatory, A-myth-archetype)
  - GIANT_MOUTH_70%_of_head_bronze_teeth (mandatory, A-myth-archetype)
  - bronze_fire_vortex_inside_mouth (mandatory, B-weapon-signature)
  - taotie_pattern_horns (mandatory, D-signature)

negative_prompt: "full body, human body, normal proportions, half-body, cropped, white background, cute kawaii, plush toy, modern style, text, watermark, small mouth"
```

### 透明背景处理要点
- 青铜色火焰漩涡边缘保留 3-5px 重度羽化
- 兽头轮廓硬切
- 漂浮碎片**单独抠出**为可选层

---

## SR 总览对照表

| 角色 | 世界 | 元素 | 主姿态 | 识别锚点（3 项） | 关键改进 |
|------|------|------|--------|------------------|----------|
| 狻猊 | Shinwa | Flame | P8 咆哮震慑 | **狮头（不是猪！）** / 振金战甲 / 金鬃 | 强制锚点，纠正历史错误 |
| 精卫 | Aether | Wood | P4 飞行俯冲 | 羽毛发梢 / 填海之石 / 鸟翼 | 元素改 Wood，强化投石 |
| 穷奇 | Ironveil | Metal | P2 战阵冲锋 | 牛角 / 金属蝟毛 / 武器架 | 校正为牛形，金属刺 |
| 旋龟 | Shinwa | Earth | P5 防御姿态 | 鸟首 / 龟壳盾 / 蛇尾 | 三位一体强化 |
| 毕方 | Aether | Thunder | P5 单足站立 | 一足 / N 金属羽翼 / 银白羽发 | 强化雷电火焰 |
| 花妖 | Aether | Wood | P7 祈祷 | 花冠 / 千瓣飘带 / 藤蔓纹身 | 新角色，治愈毒双性 |
| 饕餮 | Shinwa | Flame | P8 咆哮震慑 | **有首无身** / 青铜巨口 / 饕餮纹角 | 新角色，无身体火焰身 |

## SR 通用 QC 备注
- **狻猊必须是狮头**——生图后用 ImageMagick 自动检测面部特征 + 人工二次确认
- **饕餮必须没有身体**——仅巨兽头 + 火焰漩涡"身"
- 头身比 5-6 头身，比 SSR 更萌
- 稀有度色霜蓝 `#7FC4FF` 出现在装备边缘与能量线，不抢阵营色
- 主光源与 UR/SSR 一致（左上方 7 点钟）

---

## 生图批量顺序（建议）

1. **狻猊**（最高优先级——历史错误必须纠正，先打 10+ 个 seed 确认狮头）
2. **饕餮**（构图最难——无身体的设计挑战）
3. **花妖**（花瓣细节多，需要高分辨率）
4. **旋龟**（三位一体复合形态需要分图层合成）
5. **穷奇**（牛 + 蝟毛 + 机械 + 武器架 = 复杂组合）
6. **毕方**（单足 + N 金属羽翼，需要金属质感强）
7. **精卫**（投石姿态 + 鸟翼，相对常规）