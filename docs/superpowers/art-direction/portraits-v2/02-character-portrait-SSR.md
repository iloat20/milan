# SSR 角色立绘设计稿（7 位）— 高规格

> 适用总规范：`00-master-spec.md` v2.0
> 画布：**1280×1920 px**，PNG-24 + Alpha，2:3 竖版
> 头身比：6-7 头身，主姿态库 P2/P3/P4
> 阵营色：Shinwa `#9A6BFF`+`#E34234`+`#1FB6A6` / Aether `#C79BFF`+`#00E5FF`+`#E8E2F2` / Ironveil `#7A8A9A`+`#4A90D9`+`#C96A2E`
> 稀有度色：暮紫 `#C79BFF` 脚下光圈 + 边框

---

## 1. 凤凰 Fenghuang · 涅槃圣禽 · Shinwa · Flame

### 角色卡片
- **ID**: `char_ssr_fenghuang`
- **联动**: 漫威凤凰女念力 + DC 火星猎人火焰
- **武器**: 焚羽·涅槃翼（巨翼融合双刃）
- **氛围特效**: `ember_rebirth_field`（余烬重生领域）
- **改进重点**: 现有立绘是上半身肖像，丢失羽翼与五色长发。v2 必须**全身 + 五色长发 + 巨大凤凰羽翼 + 双手合十**。

### 识别锚点 ×3
- **A 神话原型**: 五色羽（**赤/橙/黄/绿/蓝五色凤凰羽翼**，每根羽毛末端不同色火焰）
- **B 道具指纹**: 焚羽·涅槃翼（**羽翼融合双刃，可作翅可作刃**）
- **C 解剖签名**: 五色长发渐变（**红→橙→黄→绿→蓝，如火焰燃烧**）

### 全身构图
- **主姿态**: P3 法术咏唱 + 双手合十于胸前 + 悬浮于火焰莲花之上
- **动态参数**: 凤凰羽翼左右展开，每片羽毛燃烧不同色火焰；五色长发向上飘散；脚不触地
- **三段式**:
  - 上段：火焰冠冕 + 凤凰纹花钿（额头）+ 展开的凤凰羽翼
  - 中段：五色羽衣（红色为主）+ 合十双手 + 掌心白色炽热核心
  - 下段：火焰莲花裙摆 + 脚部悬浮 + 紫色稀有度光环

### 视觉描述
- **性别/体型**: 女性神祇，7 头身，端庄华贵
- **发型**: 长发及腰，五色渐变（红/橙/黄/绿/蓝），向上飘散如燃烧
- **眼睛**: 金橙（`#FFA500`），慈悲威严
- **羽翼**: 巨大凤凰羽翼展开（翼展约为身高 3 倍），每片羽毛末端有不同颜色火焰
- **服饰**: 五色羽衣（红色为主），裙摆如火焰般飘散
- **头部装饰**: 火焰冠冕（小红宝石镶嵌），额头凤凰纹花钿
- **双手**: 合十于胸前，掌心凝聚凤凰之火（白色炽热核心 + 五色外焰）
- **脚下**: 火焰莲花台（莲花瓣为五色），脚不触地

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#9A6BFF` + `#E34234` |
| 元素色 | `#FF4D00` + `#FFD700` + 五色 |
| 稀有度色 | `#C79BFF` 暮紫（脚下光环、星级边框） |
| 情绪色 | `#FFFFFF` 掌心炽热核心 |

### 生图 Prompt
```
Full-body character portrait of Fenghuang, the Chinese mythological phoenix of virtue. Female figure 7-head proportion, majestic and noble. Long hair in five-color gradient (red-orange-yellow-green-blue) flowing upward like flame. Giant phoenix wings spread (wingspan 3x body height), each feather tip burning in different colors. Wearing five-color feathered robes dominated by red, skirt hem dispersing like flames. Flame crown on head, phoenix-pattern huadian on forehead. Hands pressed together at chest, palms converging phoenix fire (white-hot core, five-color outer flames). Floating on five-color flame lotus, feet not touching ground. Expression: compassionate and majestic, divine presence.

Shinwa world style: cel-shaded anime with ink-wash outlines, five-color palette with gold accents, dramatic fire lighting from upper left. Soft shadow edges. Fill light from upper right using Shinwa vermilion #E34234. Rim light from behind-lower using SSR purple #C79BFF. High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet visible (hovering).

key_elements:
  - five_color_wings_each_feather_different_color (mandatory, A-myth-archetype)
  - five_color_long_hair_red_orange_yellow_green_blue (mandatory, C-anatomy)
  - phoenix_crown_huadian_forehead (mandatory, D-signature)
  - hands_pressed_chest_white_hot_core (mandatory, B-pose-signature)

negative_prompt: "half-body, bust, cropped, white background, single wing color, single hair color, modern clothing, text, watermark, multiple characters, cute kawaii"
```

### 透明背景处理要点
- 羽翼火焰边缘保留 2-3px 重度羽化
- 五色长发边缘 ≤2px 羽化
- 脚下火焰莲花**单独抠出**为可选层

---

## 2. 相柳 Xiangliu · 九首毒厄 · Aether · Shadow

### 角色卡片
- **ID**: `char_ssr_xiangliu`
- **联动**: 漫威毒液共生体 + DC 小丑剧毒
- **武器**: 九首·毒牙鞭（九首蛇颈即是武器）
- **氛围特效**: `poison_marsh_fog`（毒沼雾霭）
- **改进重点**: v1 设计描述详细但需强化九首的扇形攻击姿态的视觉冲击。

### 识别锚点 ×3
- **A 神话原型**: 九首蛇身（**九蛇颈呈扇形展开，每个蛇首表情不同**）
- **B 道具指纹**: 毒液共生体覆盖（**湿润光泽的暗紫黑色 + 绿色荧光血管**）
- **C 解剖签名**: 半人半蛇（**上半身人形 + 下半身蛇尾**）

### 全身构图
- **主姿态**: P2 战阵冲锋 + 九首向不同方向伸展扇形 + 主身狂笑
- **动态参数**: 九蛇颈从腰部延伸呈扇形攻击姿态；蛇尾呈 S 形
- **三段式**:
  - 上段：九蛇颈扇形 + 人形上半身（毒液覆盖）
  - 中段：人形躯干 + 双手为共生体巨爪
  - 下段：黑色鳞片蛇尾 + 脚下毒雾

### 视觉描述
- **性别/体型**: 男性上半身 + 蛇尾，6.5 头身
- **上半身**: 暗紫黑色皮肤湿润光泽（毒液质感），白色共生体漩涡纹样，绿色荧光血管
- **眼睛**: 白色（毒液风格），疯狂
- **头部**: 共生体覆盖短发（黑色 + 白色漩涡纹样）
- **双手**: 巨大共生体爪，爪尖有腐蚀性毒液滴落（绿色）
- **九蛇颈**: 从腰部延伸，每条蛇颈约身高 1/2 长，每个蛇首表情不同（愤怒/狂笑/低吼/嗜血/痛苦/冷酷/嘲讽/饥渴/狂喜），蛇信分叉
- **下半身**: 蛇尾，黑色鳞片，鳞片缝隙渗出绿色毒液

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#C79BFF` 暮紫 + `#00E5FF` 霓虹青 |
| 元素色 | `#0D0221` 暗紫黑 + `#32CD32` 毒液绿 |
| 稀有度色 | `#C79BFF` 暮紫 |
| 情绪色 | `#8B0000` 暗红（毒液血色） |

### 生图 Prompt
```
Full-body character portrait of Xiangliu, the NINE-HEADED serpent of Chinese mythology fused with Venom symbiote. NINE serpent necks fan out from his waist like a crown — exactly nine snake heads, each with a different expression (anger, maniacal laughter, growl, sadistic grin, etc.), forked tongues. Below the fan of necks: male humanoid torso with white symbiote spiral patterns on dark purple-black wet skin, green glowing veins. Hands are massive symbiote claws with corrosive green venom dripping. Lower body is a single black-scaled serpent tail instead of legs, green venom seeping between the scales.

Aether world style: semi-realistic dark rendering, deep purple and toxic green palette, wet symbiote texture. Dramatic green underlighting from below. Rim light from behind-lower using SSR purple #C79BFF. High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with serpent tail visible.

key_elements:
  - nine_serpent_necks_fan_shaped (mandatory, A-myth-archetype)
  - venom_symbiote_white_spiral_green_veins (mandatory, B-costume-signature)
  - corrosive_venom_dripping_claws (mandatory, B-weapon-signature)

negative_prompt: "single head, two heads, normal human, no serpent tail, half-body, cropped, white background, cute, kawaii, clean appearance, dry skin, text, watermark"
```

### 透明背景处理要点
- 九蛇颈边缘保留 ≤2px 羽化
- 毒液滴落部分保留 1-2px 羽化
- 蛇尾鳞片边缘硬切

---

## 3. 雷神 Leishen · 雷霆裁决 · Ironveil · Thunder

### 角色卡片
- **ID**: `char_ssr_leishen`
- **联动**: 漫威雷神索尔 + DC 宙斯
- **武器**: 轰霆·楔石锤（雷神之锤 + 闪电符文）
- **氛围特效**: `electric_coil_core`（电磁核心线圈）
- **改进重点**: 现有立绘是半身纯黑底，丢失机械龙尾与雷电符文。v2 必须**全身 + 龙尾 + 雷神之锤高举**。

### 识别锚点 ×3
- **A 神话原型**: 龙身人头（**人头上身 + 机械龙尾下身**）
- **B 道具指纹**: 雷神之锤（**锤面闪电符文 + 缠绕电弧**）
- **C 解剖签名**: 雷鼓腹部（**腹部圆形"雷鼓"，发光，可见内部闪电**）

### 全身构图
- **主姿态**: P1 神祇凌空 + 雷神之锤高举过头 + 左手召唤闪电
- **动态参数**: 身体悬浮被闪电环绕；龙尾 S 形盘于身后
- **三段式**:
  - 上段：龙角 + 雷神之锤高举 + 雷云
  - 中段：机械装甲上半身 + 雷鼓腹部（圆形发光圆盘）
  - 下段：机械龙尾（金属鳞片）+ 脚下电弧爆裂圈

### 视觉描述
- **性别/体型**: 男性壮硕，6.5 头身
- **头部**: 龙角从额头伸出（双角），头发为电光蓝白色竖立如闪电
- **眼睛**: 电光蓝（`#00BFFF`），威严
- **上半身**: 机械装甲胸部，腹部有圆形"雷鼓"（发光，可见内部闪电漩涡）
- **下半身**: 机械龙尾，金属鳞片覆盖（深灰 + 电光蓝），尾尖为闪电形
- **武器**: 雷神之锤·轰霆（右手高举），锤面有闪电符文，缠绕电弧；左手张开召唤闪电
- **肩甲**: 龙首造型（左右各一）
- **背部**: 机械翅膀（金属骨架 + 能量膜）

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#7A8A9A` + `#4A90D9` |
| 元素色 | `#FFD600` 雷电金 + `#00BFFF` 电光蓝 |
| 稀有度色 | `#C79BFF` 暮紫 |
| 情绪色 | `#FFFFFF` 闪电高光 |

### 生图 Prompt
```
Full-body character portrait of Leishen, the thunder beast from Chinese mythology reimagined as a mechanized storm god. His lower body is a mechanical dragon tail covered in gunmetal and electric-blue metal scales (no human legs), tail tip lightning-shaped; a circular "thunder drum" is embedded in his abdomen, glowing with visible lightning inside. His right hand raises a massive square-headed Mjolnir hammer overhead, its face covered in glowing lightning runes with electric arcs wrapping it. Muscular male upper body 6.5-head proportion with mechanical chest armor, dragon horns extending from forehead, hair electric blue-white standing upright like lightning. Left hand open summoning lightning. Shoulder pauldrons dragon-head shaped, back has mechanical wings (metal frame + energy membrane).

Ironveil world style: hard-surface mechanical design, gunmetal and electric blue palette. Dramatic lightning illumination from upper left. Hard shadow edges. Rim light from behind-lower using SSR purple #C79BFF. High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with dragon tail visible.

key_elements:
  - dragon_horns_forehead_electric_hair (mandatory, C-anatomy)
  - thunder_drum_abdomen_glowing (mandatory, A-myth-archetype)
  - mechanical_dragon_tail (mandatory, A-myth-archetype)
  - mjolnir_hammer_lightning_runes (mandatory, B-weapon-prop)

negative_prompt: "no horns, no dragon features, half-body, cropped, white background, sword, polearm, bow, gun, plain tail, text, watermark, cute kawaii"
```

### 透明背景处理要点
- 雷电边缘保留 1-2px 羽化（电光蓝渐隐）
- 龙鳞边缘硬切
- 翅膀能量膜保留 2-3px 羽化

---

## 4. 飞廉 Feilian · 风驰电掣 · Shinwa · Wood

### 角色卡片
- **ID**: `char_ssr_feilian`
- **联动**: 漫威快银 + DC 闪电侠神速力
- **武器**: 裂空·疾风刃（双风刃）
- **氛围特效**: `wind_tunnel_speedlines`（风洞速度线）
- **改进重点**: v1 元素已改为 Wood（青绿色），需更新配色。姿态强化"速度"的视觉表达。

### 识别锚点 ×3
- **A 神话原型**: 鹿身雀首（**保留人形，但有鹿角/雀翼元素**）
- **B 道具指纹**: 裂空·疾风刃（**双风刃弯曲如风带**）
- **C 解剖签名**: 雀翼（**背后半透明风构成的翅膀**）

### 全身构图
- **主姿态**: P4 高速冲刺 + 身体水平前倾 45° + 双风刃交叉前指
- **动态参数**: 速度线包裹全身；衣摆飘带向后方拉成长条；脚下无支撑（风托举）
- **三段式**:
  - 上段：鹿角（双角，分叉 3 次）+ 雀翼展开 + 风青色长发向后拉
  - 中段：轻便仙侠服饰（青白）+ 双风刃前指
  - 下段：风托举的双腿 + 风刃切割痕迹 + 脚下风圈

### 视觉描述
- **性别/体型**: 青年男性，6.5 头身，修长
- **发型**: 风青色长发向后飘扬，发梢化为风流
- **眼睛**: 风青色（`#00C853`），锐利
- **鹿角**: 头顶分叉鹿角 3 次（与鹿身雀首相符）
- **雀翼**: 背后半透明雀翼（青绿色 + 风构成）
- **服饰**: 轻便仙侠服饰（青白），衣摆和袖口有风刃切割的边缘
- **双腿**: 被风包裹，脚不踏地，有旋转气流
- **武器**: 双风刃·裂空（弯曲如风带，指尖有神速力青色闪电环绕）

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#9A6BFF` + `#1FB6A6` 青古铜 |
| 元素色 | `#2E8B57` 翡翠绿（木元素）+ `#00C853` 风青 |
| 稀有度色 | `#C79BFF` 暮紫 |
| 情绪色 | `#1E90FF` 神速蓝（速度线） |

### 生图 Prompt
```
Full-body character portrait of Feilian, the wind deity from Chinese mythology (Shanhaijing says "deer body, sparrow head") fused with Speed Force powers. Young male figure 6.5-head proportion, slender, motion blur effect suggesting extreme speed. Hair wind-cyan (#00C853), streaming backward, tips dissolving into wind currents. Antlers branching 3 times on head (deer feature). Sparrow wings (semi-transparent, wind-formed) spread on back. Wearing light xianxia robes in wind-cyan and white, hems and cuffs with wind-blade cut edges. Legs wrapped in swirling wind, feet not touching ground. Right hand extended forward, fingertips surrounded by cyan Speed Force lightning. Dynamic high-speed pose: body horizontal leaning forward 45 degrees, speed lines surrounding.

Shinwa world style: cel-shaded anime with ink-wash outlines, wind-cyan and white palette with green wood-element accents. Dynamic speed-line effects. Soft shadow edges. Rim light from behind-lower using SSR purple #C79BFF. High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body visible (hovering, no ground).

key_elements:
  - antlers_3_branches_deer_feature (mandatory, A-myth-archetype)
  - sparrow_wings_wind_formed (mandatory, A-myth-archetype)
  - dual_wind_blades_curved_like_wind (mandatory, B-weapon-prop)

negative_prompt: "standing still, walking, no wings, no antlers, half-body, cropped, white background, sword, gun, modern clothing, text, watermark, cute kawaii"
```

### 透明背景处理要点
- 速度线羽化重度 3-5px（速度感）
- 雀翼边缘 2-3px 羽化（半透明感）
- 衣摆风化部分 2px 羽化

---

## 5. 商羊 Shangyang · 预知神鸟 · Aether · Light

### 角色卡片
- **ID**: `char_ssr_shangyang`
- **联动**: 漫威 X 教授精神力 + DC 命运博士纳布神盔
- **武器**: 卜天·星谶盘（星象法器）
- **氛围特效**: `constellation_guidance`（星座指引）
- **改进重点**: 旧元素是 Star，新版改为 Light（神圣金）。一足鸟特征必须保留。

### 识别锚点 ×3
- **A 神话原型**: 一足鸟身（**单足站立，另一足收拢**）
- **B 道具指纹**: 纳布神盔（**头盔覆盖头部，星象纹路发光**）
- **C 解剖签名**: 星空长袍（**袍面有缓慢移动的星座连线**）

### 全身构图
- **主姿态**: P7 祈祷 + 单足站立 + 双手捧发光星象球
- **动态参数**: 单足立于星光法阵之上；另一足收拢于身前；背后半透明精神力光环
- **三段式**:
  - 上段：纳布神盔 + 闭目 + 背后精神力光环（半透明脑波可视化）
  - 中段：星空长袍 + 双手捧星象球 + 星轨环绕
  - 下段：单足站立（一足鸟特征）+ 星象法阵 + 脚下光圈

### 视觉描述
- **性别/体型**: 女性神秘优雅，7 头身
- **头部**: 纳布神盔覆盖（DC 命运博士风格），头盔上有星象纹路发光（金色）
- **眼睛**: 闭着（使用心灵感应"看"）
- **服饰**: 星空长袍（深蓝底 `#1A237E`），袍面有星座连线（**会缓慢移动**），星轨在长袍表面浮动
- **单足站立**: 左足踩星象法阵（圆形法阵，8 层同心圆 + 12 星座符号），右足收拢于身前
- **双手**: 捧发光星象球（球面可见命运碎片）
- **背后**: 半透明精神力光环（脑波可视化，多层同心圆金色波纹）

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#C79BFF` + `#00E5FF` |
| 元素色 | `#FFD600` 神圣金（光元素）+ `#FFFAF0` 珍珠白 |
| 稀有度色 | `#C79BFF` 暮紫 |
| 情绪色 | `#FFFFFF` 星光高光 |

### 生图 Prompt
```
Full-body character portrait of Shangyang, the one-legged prophetic bird from Chinese mythology (Shanhaijing "one leg, bird body") fused with psychic powers. Female figure 7-head proportion, mysterious and elegant, eyes closed (seeing through telepathy). Wearing Nabu helmet (Doctor Fate style) with glowing star-map patterns (#FFD600) on surface. Cosmic robe deep blue base (#1A237E) with moving constellation lines on fabric. Standing on one leg (left foot on astrological circle — 8 concentric rings + 12 zodiac symbols), right leg retracted at front. Hands holding glowing astrolabe sphere showing fragments of future. Semi-telepathic halo radiating from back (multi-layer concentric gold waves).

Aether world style: semi-realistic rendering, deep blue and nebula purple palette, ethereal starlight. Dramatic cosmic lighting from upper left. Soft shadow edges. Rim light from behind-lower using SSR purple #C79BFF. High detail especially on helmet star-map and astrolabe sphere. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with one foot planted visible.

key_elements:
  - one_leg_standing_other_leg_retracted (mandatory, A-myth-archetype)
  - nabu_helmet_with_star_map_glowing (mandatory, B-costume-signature)
  - cosmic_robe_with_moving_constellations (mandatory, D-costume-signature)

negative_prompt: "two legs standing equally, eyes open, normal helmet, plain robe, half-body, cropped, white background, modern clothing, text, watermark, cute kawaii"
```

### 透明背景处理要点
- 精神力光环边缘保留 3-5px 重度羽化
- 星象法阵**单独抠出**为可选层
- 头盔硬切边缘（保留锐度）

---

## 6. 蚩尤 Chiyou · 兵主魔神 · Ironveil · Metal

### 角色卡片
- **ID**: `char_ssr_chiyou`
- **联动**: 漫威绿巨人无限愤怒 + DC 毁灭日进化杀戮
- **武器**: 虎魄·裂魂斧（合金重刃）
- **氛围特效**: `ruin_battlefield_banners`（废土战旗）
- **改进重点**: 本角色**v1 无设计稿**，v2 从零开始。需强化"铜头铁额、八肱八趾"的解剖特征。

### 识别锚点 ×3
- **A 神话原型**: 铜头铁额八肱八趾（**头部金属覆盖（铜色）+ 四臂四腿兽形**）
- **B 道具指纹**: 虎魄·裂魂斧（**合金重刃，斩击附带金属碎片风暴**）
- **C 解剖签名**: 兵主战旗（**背后多面破碎铁帷战旗**）

### 全身构图
- **主姿态**: P2 战阵冲锋 + 巨斧横扫 + 身体前倾 35°
- **动态参数**: 四臂（前两持双斧，后两张开如兽翼）；脚下踩着断裂的机械巨像残骸
- **三段式**:
  - 上段：铜头铁额（金属覆盖头部）+ 双角 + 巨斧高举
  - 中段：四臂持械 + 兵主战甲 + 多面破碎战旗
  - 下段：四足（或双腿 + 机械义足）+ 脚下爆裂圈

### 视觉描述
- **性别/体型**: 男性，6 头身，壮硕（铁帷最强战士）
- **头部**: 头部上半为铜色金属覆盖（"铜头铁额"），可见铆钉与焊缝；双角伸出
- **眼睛**: 红色（`#8B0000`），暴烈狂怒
- **四臂**: 前两臂持双斧（虎魄·裂魂斧），后两臂张开如兽翼（每臂有金属尖刺）
- **战甲**: 铁帷合金战甲，深灰 `#4A4A5A` + 锈橙 `#C96A2E`，胸甲有兽面浮雕
- **战旗**: 背后多面破碎铁帷战旗（金属旗杆 + 撕裂金属旗面）
- **下半身**: 双腿（或四腿"八趾"），机械义足
- **脚下**: 踩着断裂的机械巨像残骸

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#7A8A9A` + `#C96A2E` 锈橙 |
| 元素色 | `#4A4A5A` 枪铁灰 + `#B87333` 紫铜 |
| 稀有度色 | `#C79BFF` 暮紫 |
| 情绪色 | `#FF4500` 熔岩红（战意） |

### 生图 Prompt
```
Full-body character portrait of Chiyou, the war god from Chinese mythology (Shanhaijing "copper head, iron forehead, eight arms eight toes") reimagined as Ironveil war machine. Male figure 6-head proportion, massive and ferocious. Head upper half covered in copper-colored metal armor (visible rivets and weld seams), two horns extending. Four arms: front two holding double axes (Hu Po Lie Hun axes), back two spread like beast wings with metal spikes. Ironveil alloy armor in gunmetal (#4A4A5A) and rust orange (#C96A2E), chest plate with beast-face relief. Behind body: multiple torn Ironveil war banners on metal poles. Legs mechanical prosthetic. Standing on broken mecha colossus debris.

Ironveil world style: hard-surface mechanical design, industrial realism, gunmetal and copper palette, rivets and panel lines, sparks. Dramatic orange battle lighting from upper left. Hard shadow edges. Rim light from behind-lower using SSR purple #C79BFF. High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet on debris visible.

key_elements:
  - four_arms_double_axes_back_arms_spread (mandatory, A-myth-archetype)
  - copper_metal_head_with_horns (mandatory, A-myth-archetype)
  - hu_po_lie_hun_axes_alloy_blades (mandatory, B-weapon-prop)
  - torn_ironveil_war_banners (mandatory, D-signature)

negative_prompt: "two arms only, normal human head, no horns, no banners, half-body, cropped, white background, sword, gun, bow, modern clothing, text, watermark, cute kawaii, clean armor"
```

### 透明背景处理要点
- 战旗边缘保留 1-2px 羽化（撕裂金属感）
- 斧刃边缘硬切
- 脚下机械残骸**单独抠出**为可选层

---

## 7. 白虎 Baihu · 西方圣兽 · Shinwa · Metal

### 角色卡片
- **ID**: `char_ssr_baihu`
- **联动**: 漫威黑豹振金战甲 + DC 猫女优雅致命
- **武器**: 庚金·虎啸爪（双爪延伸振金利刃）
- **氛围特效**: `metal_storm_wasteland`（金属风暴荒原）
- **改进重点**: 本角色**v1 无设计稿**，v2 从零开始。需强化"四象之一"的西方金属气质。

### 识别锚点 ×3
- **A 神话原型**: 白虎形态（**白色虎身 + 黑色条纹 + 金色兽瞳**）
- **B 道具指纹**: 庚金·虎啸爪（**双前爪延伸出振金振动的利刃**）
- **C 解剖签名**: 西方金属风暴（**背景为细碎刀刃金属碎屑的风暴**）

### 全身构图
- **主姿态**: P2 战阵冲锋 + 虎扑姿态 + 前爪前伸
- **动态参数**: 巨大白虎跃起扑击；双前爪前伸延伸振金利刃；尾巴横扫
- **三段式**:
  - 上段：虎头俯视 + 金色兽瞳 + 额间"王"字纹
  - 中段：白色虎身 + 黑色条纹 + 双前爪利刃 + 胸前金属胸甲
  - 下段：后腿蹬地 + 尾巴横扫 + 脚下金属风暴圈

### 视觉描述
- **物种**: 巨大白虎（肩高约人体身高 1.5 倍）
- **毛色**: 雪白底色 + 黑色条纹（虎纹）
- **眼睛**: 金色兽瞳（`#FFD700`），竖瞳，孤傲
- **额纹**: 额间"王"字纹（黑色加深）
- **爪**: 双前爪延伸出振金振动的利刃（5 爪，指尖为金属利刃）
- **胸甲**: 胸前佩戴振金胸甲（黑豹风格，`#1A1A1A` 振黑 + `#FFD700` 金纹）
- **尾巴**: 长尾横扫，尾尖金属化
- **背景**: 西方荒原 + 金属风暴（细碎刀刃碎屑飞舞）

### 配色系统
| 层 | 颜色 |
|----|------|
| 阵营色 | `#9A6BFF` + `#1FB6A6` 青古铜 |
| 元素色 | `#4A4A5A` 枪铁灰 + `#B87333` 紫铜 |
| 稀有度色 | `#C79BFF` 暮紫 |
| 情绪色 | `#FFD700` 金色（兽瞳、胸甲纹） |

### 生图 Prompt
```
Full-body character portrait of Baihu, the White Tiger of the West (one of Four Symbols in Chinese mythology), the metallic holy beast. Giant white tiger form, shoulder height 1.5x human height. Snow white base fur with black tiger stripes, golden beast eyes (#FFD700) with vertical pupils, "king" character mark on forehead (deepened black). Two front paws extending vibranium vibrating blades (5 claws each, metal blade tips). Chest wearing vibranium chest armor (Black Panther style, #1A1A1A vibranium black + #FFD700 gold circuit lines). Long tail sweeping with metal tip. Dynamic pounce pose: body leaping forward, front paws extended with blades out, back legs pushing off ground, attacking down at viewer.

Shinwa world style: cel-shaded anime with ink-wash outlines, white and black palette with gold accents, dramatic metallic storm lighting. Sharp edges for blades, soft fur rendering. Rim light from behind-lower using SSR purple #C79BFF. High detail especially on fur texture and blade vibration. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body tiger visible (no human form).

key_elements:
  - white_tiger_form_with_black_stripes (mandatory, A-myth-archetype)
  - vibranium_vibrating_blade_claws (mandatory, B-weapon-prop)
  - vibranium_chest_armor_gold_circuits (mandatory, D-signature)
  - golden_beast_eyes_vertical_pupils (mandatory, C-anatomy)

negative_prompt: "human form, half-body, cropped, white background, normal tiger without armor, sword, polearm, gun, bow, text, watermark, cute kawaii, plush toy style"
```

### 透明背景处理要点
- 虎毛边缘保留 1-2px 羽化（毛发感）
- 振金利刃边缘硬切
- 金属风暴碎屑**单独抠出**为可选层

---

## SSR 总览对照表

| 角色 | 世界 | 元素 | 主姿态 | 识别锚点（3 项） | 关键改进 |
|------|------|------|--------|------------------|----------|
| 凤凰 | Shinwa | Flame | P3 法术咏唱 | 五色羽 / 五色长发 / 凤凰冠 | 全身 + 双手合十 + 火焰莲花 |
| 相柳 | Aether | Shadow | P2 战阵冲锋 | 九蛇颈扇形 / 共生体 / 半人半蛇 | 九首表情差异化 |
| 雷神 | Ironveil | Thunder | P1 神祇凌空 | 龙角 / 雷鼓腹 / 龙尾 | 全身 + 雷神之锤高举 |
| 飞廉 | Shinwa | Wood | P4 高速冲刺 | 鹿角 / 雀翼 / 双风刃 | 元素改 Wood + 速度线强化 |
| 商羊 | Aether | Light | P7 祈祷 | 一足 / 纳布神盔 / 星空袍 | 元素改 Light + 法阵单独抠出 |
| 蚩尤 | Ironveil | Metal | P2 战阵冲锋 | 四臂 / 铜头 / 虎魄斧 | 新角色，强化八肱八趾 |
| 白虎 | Shinwa | Metal | P2 战阵冲锋 | 白虎 / 振金爪 / 振金胸甲 | 新角色，强化四象金属气 |

## SSR 通用 QC 备注
- 头身比严格 6-7 头身，比 UR 略萌化
- 主光源与 UR 一致（左上方 7 点钟）
- 阵营色 + 元素色 + 稀有度色三色叠加清晰可辨
- 透明背景在浅色 UI 上无"贴纸感"

---

## 生图批量顺序（建议）

1. **凤凰**（最复杂，Shinwa Flame 五色处理）
2. **白虎**（新角色，兽形立绘需先打小稿）
3. **蚩尤**（新角色，四臂机械需测试）
4. **相柳**（九蛇颈扇形构图挑战）
5. **商羊**（一足站立法阵抠图层）
6. **雷神**（机械龙尾接续人身的解剖难点）
7. **飞廉**（速度线最容易出效果）