# UR 角色立绘设计稿（7 位）— 最高规格

> 适用总规范：`00-master-spec.md` v2.0
> 画布：**1536×2304 px**，PNG-24 + Alpha，2:3 竖版
> 头身比：7-8 头身，主姿态库 P1/P2/P3
> 阵营色：Shinwa `#9A6BFF`+`#E34234`+`#1FB6A6` / Aether `#C79BFF`+`#00E5FF`+`#E8E2F2` / Ironveil `#7A8A9A`+`#4A90D9`+`#C96A2E`
> 稀有度色：熔金 `#FFC857` 全屏光晕
>
> 每个角色 v2 角色稿结构：
> 1. 角色卡片（身份/武器/联动）
> 2. 识别锚点 ×3（铁律）
> 3. 全身构图（姿态编号 + 修饰参数）
> 4. 三段式分镜（上/中/下）
> 5. 视觉描述（细化到解剖级）
> 6. 配色系统（5 层叠加）
> 7. 生图 Prompt（5 段式 + 负面提示）
> 8. 透明背景处理要点

---

## 1. 烛龙 Zhulong · 昼夜之主 · Shinwa · Flame

### 角色卡片
- **ID**: `char_ur_zhulong`
- **联动**: 漫威凤凰之力 + DC 火神
- **武器**: 阖辟神瞳·昼夜轮（金黑双面圆轮）
- **氛围特效**: `day_night_cycle_glow`（昼夜光环呼吸）
- **背景叙事**: 烛龙与金乌是 Shinwa 双子图腾。烛龙司昼夜节律，金乌司光明强度。
- **关键改进点**: 现有立绘为半身纯黑底，丢失蛇尾与人面原型。v2 必须**全身入画 + 透明背景 + 蛇尾展开 + 异色瞳**。

### 识别锚点 ×3（必含项）
- **A 神话原型**: 人面蛇身（**蛇尾必须从腰部延伸，鳞片为赤金渐变**）
- **B 道具指纹**: 阖辟神瞳·昼夜轮（**半面熔金半面虚空的圆轮**，环心有永不闭合的竖瞳）
- **C 解剖签名**: 异色瞳（**左金瞳太阳纹，右银瞳月轮**）

### 全身构图
- **主姿态**: P1 神祇凌空 + 头部仰视 15° + 右手托举日轮
- **动态参数**: 蛇尾 S 形缠绕三圈悬浮于云层；左掌心向下展开黑夜漩涡；发梢向上飘散呈火焰形态
- **三段式**:
  - 上段：背后有"左白昼右星空"的双色裂隙幕景（不画实景，仅以光晕呈现）
  - 中段：人面 + 龙首肩甲 + 胸前烛龙之芯发光宝石 + 阖辟神瞳悬浮于右侧
  - 下段：蛇尾盘旋 3 圈，尾尖指向下方观众方向；脚下熔金光圈（稀有度色）

### 视觉描述
- **人面**: 男性青年面庞，剑眉星目，鼻梁高挺，嘴唇紧抿。**异色瞳**：左金瞳（瞳孔为太阳纹，7 道光纹放射），右银瞳（瞳孔为月轮，环绕月相阴影）
- **肤色**: 象牙白，皮肤有熔岩流动的裂纹（暗红→橙黄渐变），裂纹仅在面部+胸颈+手臂可见，腹部以下蛇身鳞片覆盖
- **发色**: 赤金渐变到白炽，发根暗红、发干熔金、发梢纯白
- **服饰**: 上古祭服残片，主色为暗朱砂红与墨黑；龙首肩甲（一肩一面，对称双龙首）；胸口悬挂烛龙之芯（拳头大椭圆形发光宝石，颜色跟随当前昼/夜状态）
- **手部**: 右手五指张开托举太阳火球（小型，拳头大）；左掌向下展开黑夜漩涡（直径约蛇尾 1/3）

### 配色系统
| 层 | 颜色 | 应用 |
|----|------|------|
| 底层氛围 | `#9A6BFF` 紫金渐变到 `#E34234` 朱砂红 | 蛇身鳞片主色 |
| 阵营色 | `#9A6BFF` 暗紫金 + `#E34234` 朱砂红 | 服饰主色、祭服 |
| 元素色 | `#FF4D00` 熔岩橙 + `#FFD700` 金黄 | 火球、皮肤裂纹、火焰发梢 |
| 稀有度色 | `#FFC857` 熔金 | 背后光晕、脚下光环、稀有度边框 |
| 情绪色 | `#FFFFFF` 纯白 | 月轮、右眼瞳孔 |

### 生图 Prompt（5 段式）
```
[1. 主体] Full-body character portrait of Zhulong, an ancient Chinese mythological dragon-god of day and night. Muscular young male upper body with ivory skin flowing magma cracks (dark red to orange-yellow). Massive serpent tail coiling three loops (coiled like number "6"), scales gradient from crimson to molten gold, tip pointing down at viewer. Heterochromatic eyes: left golden sun-eye with 7-ray solar pupil, right silver moon-eye with lunar phase pupil.

[2. 姿态] Dynamic pose P1 (deity hovering) + head tilted up 15 degrees + right hand holding sun sphere + left palm summoning night vortex below. Hair flowing upward like flame (dark red roots, molten gold middle, white-hot tips). Dragon-head shoulder pauldrons, glowing dragon-core gem at chest. Yibi-Shentong (Day-Night Wheel) floating at right side: half molten gold, half void black, vertical pupil at center.

[3. 氛围] Atmosphere: cosmic reverence, duality of creation. Background: TRANSPARENT (PNG alpha). No environment scene. Below feet: molten gold radial glow circle (UR rarity color). Behind: soft dual-halo glow split left-bright (daylight gold) right-dark (starlight purple). Floating particles: golden dragon-scale fragments, dark night-mist wisps. Mood: divine, eternal, omnipresent.

[4. 风格] Shinwa world style: cel-shaded anime with Chinese ink-wash outlines, flowing brush-stroke lines, gold leaf accents. Semi-realistic facial detail (eyes 7-tuple high detail). Dramatic lighting from upper left (7 o'clock 45 degrees). Fill light from upper right using Shinwa purple #9A6BFF. Rim light from behind-lower using UR molten gold #FFC857. High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet visible, 8-head proportion.

[5. 关键元素] key_elements:
  - snake_tail_coiling_three_loops (mandatory, A-myth-archetype)
  - day_night_wheel_half_gold_half_void (mandatory, B-weapon-prop)
  - heterochromatic_eyes_gold_sun_silver_moon (mandatory, C-anatomy)

negative_prompt:
  "half-body, bust, cropped, white background, colored background, 
   text, watermark, multiple characters, 3d render, photorealistic,
   bad anatomy, extra fingers, deformed hands, blurry, low quality,
   animal head, dragon head on human body (must be human face), 
   human legs (must be snake tail)"
```

### 透明背景处理要点
- 蛇尾鳞片边缘保留 ≤2px 羽化
- 发梢火焰边缘保留 ≤1px 羽化（透明渐隐到完全 alpha=0）
- 火球与漩涡边缘硬切
- 脚下氛围光圈**单独抠出**作为可选叠加层（便于 UI 切换稀有度光环）

---

## 2. 虚无 Wuxu · 万象终焉 · Aether · Shadow

### 角色卡片
- **ID**: `char_ur_wuxu`
- **联动**: 漫威湮灭 + DC 反监视者
- **武器**: 归墟之噬·无相刃（无柄无锷，刃身即被吞噬的星空）
- **氛围特效**: `void_devour_particles`（黑洞吞噬粒子）
- **背景叙事**: 虚无来自维度裂隙深处，以星辰为食，是 Aether 最恐惧的噩梦。
- **关键改进点**: 现有立绘黑底，信息量少。v2 必须**透明背景 + 无面镜面化 + 黑洞胸口 + 9 头身修长**。

### 识别锚点 ×3（必含项）
- **A 神话原型**: 无面无相（**面部为光滑暗色镜面，映照星空**）
- **B 道具指纹**: 胸口微型黑洞（**缓慢旋转，吞食周围光线**）
- **C 解剖签名**: 暗物质星云构成的身体（**边缘不断溶解为粒子**）

### 全身构图
- **主姿态**: P1 神祇凌空 + 身体前倾 20° 俯视观众 + 双臂微张
- **动态参数**: 整个身体像被拉扯进画面外，胸口黑洞为视觉中心点；星尘从衣摆不断飘落消散
- **三段式**:
  - 上段：头顶为星云漩涡状（非传统头发），缓慢旋转
  - 中段：无面镜面 + 胸口微型黑洞 + 长臂手指为星云漩涡
  - 下段：拖曳暗物质尾迹的下摆，星尘持续飘落

### 视觉描述
- **性别/体型**: 无固定性别，人形轮廓，9 头身超修长
- **面部**: 完全光滑的暗色镜面（黑色 `#0D0221`），反射周围的星云——**没有眼、没有鼻、没有嘴**
- **身体构成**: 由暗物质星云构成，边缘不断溶解为细小粒子（向四周飘散）
- **胸口**: 嵌入式微型黑洞（直径约胸腔 1/3），缓慢顺时针旋转，吞食周围光线形成吸积盘
- **手部**: 长指延伸至星云漩涡状指尖（指尖化为 6-7 圈星云旋涡）
- **服饰**: 破碎宇宙长袍，袍面为深空底色，织有黯淡星图（缓缓移动），长袍下摆不断有星尘飘落
- **脚下**: 拖曳暗物质尾迹（不接触地面），尾迹长度约为身高 30%

### 配色系统
| 层 | 颜色 | 应用 |
|----|------|------|
| 底层氛围 | `#0D0221` 虚空黑 | 全身主色 |
| 阵营色 | `#C79BFF` 暮紫 + `#00E5FF` 霓虹青 | 袍面星云、长袍边缘 |
| 元素色 | `#0D0221` 暗紫黑 + `#32CD32` 毒绿（仅胸口黑洞吸积盘） | 黑洞、漩涡 |
| 稀有度色 | `#FFC857` 熔金 | 胸口的吸积盘核心（一颗金点）、身后全屏光晕 |
| 情绪色 | `#FFFFFF` 冷白 | 星尘粒子、镜面反射高光 |

### 生图 Prompt
```
[1. 主体] Full-body character portrait of Wuxu, the primordial void entity from Chinese mythology. Genderless humanoid figure 9-head proportion, body composed of swirling dark matter nebula, edges constantly dissolving into particles. Face is a smooth dark mirror surface (no eyes, no nose, no mouth), reflecting faint distant starfield. Chest contains a miniature black hole slowly rotating clockwise with golden accretion disk. Long slender arms with nebula-vortex fingertips (6-7 swirling turns at each finger).

[2. 姿态] Dynamic pose P1 (deity hovering) + body leaning forward 20 degrees looking down at viewer + arms slightly spread. Tattered cosmic robe woven from starlight, hem continuously dispersing stardust. Dark matter trail trailing below feet (no ground contact), trail length 30% of body height. Top of head is a swirling nebula vortex (not hair).

[3. 氛围] Atmosphere: cosmic horror, existential dread, the end of all things. Background: TRANSPARENT (PNG alpha). Behind body: faint void-purple radial halo (UR rarity color tinted to purple). Floating particles: stardust fragments, void motes, faint cosmic strings. Mood: omniscient, indifferent, eternal hunger.

[4. 风格] Aether world style: semi-realistic dark rendering, deep purple and void black palette (#0D0221 base). Wet nebula texture on body surface. Ethereal particle effects. Cosmic horror atmosphere. Rim lighting from behind-lower using UR molten gold #FFC857 (subtle). Fill light from upper left with faint blue starlight #4A90D9. Dramatic silhouette. High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet visible (no ground).

[5. 关键元素] key_elements:
  - faceless_mirror_face_no_features (mandatory, A-myth-archetype)
  - miniature_black_hole_chest_rotating (mandatory, B-weapon-prop)
  - dark_matter_nebula_body_dissolving_edges (mandatory, C-anatomy)

negative_prompt:
  "human face, eyes visible, mouth, nose visible, 
   half-body, bust, cropped, white background,
   text, watermark, multiple characters, cute, kawaii,
   bright colors, warm tones, daylight, sunny"
```

### 透明背景处理要点
- 暗物质溶解边缘保留 3-5px 重度羽化（粒子感）
- 镜面脸部的高光反射为冷白色硬边
- 胸口黑洞的吸积盘保留环形锐利边缘

---

## 3. 刑天 Xingtian · 不死战神 · Ironveil · Metal

### 角色卡片
- **ID**: `char_ur_xingtian`
- **联动**: 漫威金刚狼 + DC 毁灭日
- **武器**: 干戚·不灭齿轮（巨大战盾 + 艾德曼合金巨斧）
- **氛围特效**: `scrap_storm_ironveil`（金属碎屑风暴）
- **背景叙事**: 铁帷"兵主军团"第一代实验体，最稳定的头号战士。断首仍战斗，以乳为目以脐为口。
- **关键改进点**: 现有立绘为半身图，丢失无头战士核心标志。v2 必须**全身入画 + 胸口双眼 + 腹部格栅口 + 双武器完整**。

### 识别锚点 ×3（必含项）
- **A 神话原型**: 断首以乳为目以脐为口（**胸口双眼发光 + 腹部格栅口**）
- **B 道具指纹**: 干戚双武器（**左手巨盾"干" + 右手巨斧"戚"**）
- **C 解剖签名**: 颈部金属铸造截面（**战损划痕与焊接修补痕迹**）

### 全身构图
- **主姿态**: P2 战阵冲锋 + 头部仰视 25° + 巨斧高举过头
- **动态参数**: 身体前倾 30° 似要冲破画面；巨盾在左身侧、巨斧在右高举；脚下踩着破碎敌人装甲；背部机械脊柱能量管脉动
- **三段式**:
  - 上段：颈部截面 + 肩膀战损 + 巨斧刃光（艾德曼合金亮橙）
  - 中段：胸口双眼发光（橙红，毁灭日风格）+ 龙首肩甲 + 巨盾战痕
  - 下段：腹部格栅口（发声时脉动红光）+ 双腿机械装甲 + 脚下爆裂圈

### 视觉描述
- **体型**: 男性壮硕（UR 中等身材），肌肉线条分明但被机械装甲覆盖
- **颈部**: 截面为金属铸造面（深灰 `#4A4A5A`），可见焊接纹路 + 旧伤划痕（**绝对不能画人头**）
- **胸口双眼**: 嵌于胸部装甲，发光橙红光学传感器，毁灭日风格横瞳，愤怒时如烈日
- **腹部格栅口**: 圆形金属格栅，内置扬声器，发声时脉动红光
- **装甲**: 全身重型机甲，深灰 `#4A4A5A` 与紫铜 `#B87333` 双色，大量战损划痕、焊接修补、铆钉外露
- **左盾（干）**: 直径约身高 1/3 的圆形巨盾，盾面有金刚狼爪痕（3 道平行深痕）和毁灭日符文（蛇形图腾）
- **右斧（戚）**: 巨斧长度约身高 4/5，斧刃为艾德曼合金发光橙红 `#FF4500`，斧柄缠绕铁链
- **背部**: 机械脊柱外露 7 节，能量管脉动橙红光

### 配色系统
| 层 | 颜色 | 应用 |
|----|------|------|
| 底层氛围 | `#7A8A9A` 冷钢灰 | 装甲主色 |
| 阵营色 | `#7A8A9A` + `#4A90D9` 电弧蓝 | 装甲细节、电管线 |
| 元素色 | `#4A4A5A` 枪铁灰 + `#B87333` 紫铜 | 战痕、金属边 |
| 稀有度色 | `#FFC857` 熔金 | 斧刃高光、背后光晕 |
| 情绪色 | `#FF4500` 熔岩红 | 胸口眼、能量管、斧刃发光 |

### 生图 Prompt
```
[1. 主体] Full-body character portrait of Xingtian, the headless immortal warrior from Chinese mythology reimagined as Ironveil mechanized berserker. NO HEAD — neck is a cast metal cross-section with weld marks and battle scratches. Eyes are TWO glowing orange-red optical sensors on the chest (Doomsday-style horizontal pupils). Mouth is a circular metal grille on the abdomen with pulsing red light. Muscular male body covered in heavy battle mecha armor, gunmetal gray (#4A4A5A) and copper (#B87333), covered in battle damage, weld repair, and impact dents. Exposed mechanical spine on back with 7 pulsing energy tubes.

[2. 姿态] Dynamic pose P2 (battle charge) + body leaning forward 30 degrees breaking frame + giant axe raised high overhead + shield held at left side. Left foot forward, right foot planted on broken enemy mecha debris. Axe length 4/5 of body height, blade glowing molten red (#FF4500) like adamantium. Shield diameter 1/3 body height, face carved with Wolverine claw marks (3 parallel gouges) and Doomsday runes. Axe handle wrapped in iron chain.

[3. 氛围] Atmosphere: battlefield fury, immortal rage. Background: TRANSPARENT (PNG alpha). Behind body: Ironveil rust-orange radial halo (UR rarity color). Floating particles: metal sparks, oil drops, steel fragments, dust from impact. Ground crack visible only beneath right foot (small explosion circle). Mood: unstoppable, wrathful, eternal.

[4. 风格] Ironveil world style: hard-surface mechanical design, industrial realism, gunmetal and copper palette, rivets and panel lines, sparks and steam. Semi-realistic rendering with cel-shading highlights on metal edges. Dramatic orange battle lighting from upper left. Rim light from behind-lower using UR molten gold #FFC857. Hard shadow edges (theatrical). High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with feet visible, 7-head proportion.

[5. 关键元素] key_elements:
  - no_head_neck_is_metal_cross_section (mandatory, A-myth-archetype)
  - chest_eyes_glowing_red + abdomen_mouth_grille (mandatory, A-myth-archetype)
  - shield_gan_with_claw_marks + axe_qi_with_molten_blade (mandatory, B-weapon-prop)

negative_prompt:
  "human head, face visible, normal eyes on head, normal mouth on face,
   half-body, bust, cropped, white background,
   text, watermark, multiple characters, cute, kawaii,
   pristine armor, clean metal, futuristic sleek"
```

### 透明背景处理要点
- 斧刃发光边缘保留 1-2px 羽化（橙红渐隐）
- 金属战痕边缘硬切
- 背后能量管脉动为外发光效果，不参与抠图
- 脚下破碎敌人装甲**不画**，由 UI 层另行处理

---

## 4. 桔梗 Kikyo · 悲运的巫女 · Shinwa · Shadow

### 角色卡片
- **ID**: `char_ur_kikyo`
- **联动**: 《犬夜叉》IP 联动巫女
- **武器**: 破魔灵弓·封魂（神木制 + 白色符咒缠绕）
- **氛围特效**: `soul_petal_drift`（灵魂花瓣飘散）
- **背景叙事**: 桔梗是 Shinwa 最具悲剧美感的角色，与女娲因"泥土重生"有精神共鸣。
- **关键改进点**: 现有立绘为半身纯红底，丢失巫女服、和弓、注连绳等所有标志性元素。v2 必须**全身入画 + 巫女服完整 + 拉弓姿态 + 透明背景**。

### 识别锚点 ×3（必含项）
- **A 联动原型**: 巫女标准服饰（**白色小袖 + 朱红袴裤 + 注连绳**）
- **B 道具指纹**: 破魔灵弓（**神木 + 白色符咒 + 净化光之箭**）
- **C 解剖签名**: 黑色长直发（**及腰、左侧一缕垂于胸前**）+ 深紫色清冷眼眸

### 全身构图
- **主姿态**: P2 战阵冲锋 + 拉弓满弦 + 身体侧立 3/4 角度
- **动态参数**: 左脚前右脚后弓步；左手持弓左推，右手拉弦至耳；箭矢指向画面外右上方
- **三段式**:
  - 上段：黑发飞扬 + 拉弓姿态 + 注连绳缠绕右臂
  - 中段：巫女服白小袖 + 朱红袴裤 + 破魔灵弓 + 灵力光点
  - 下段：赤足 + 脚下灵力光之花 + 木屐遗落在花旁

### 视觉描述
- **性别/体型**: 女性，修长优雅，7.5 头身
- **发型**: 黑长直发及腰，无刘海（露出额头），左侧一缕长垂于胸前，发梢微卷
- **眼睛**: 深紫色（`#4B0082`），清冷悲悯，半垂眼帘
- **巫女服**: 白色小袖上衣（kosode），朱红袴裤（hakama），腰间白色腰带系结；左肩破损护甲（铁片覆盖），右臂缠绕注连绳（神圣稻草绳，带白色纸垂）
- **弓**: 神木制（深褐色带绿），缠绕白色符咒（"封"字重复），弓身有自然弯曲弧度
- **箭矢**: 净化之箭（hamaya），箭头为光蓝色半透明锥形（`#4A90D9`），箭杆白色，箭羽浅蓝
- **足部**: 赤足踏于灵力光之花（莲花形光阵）
- **表情**: 清冷、悲悯、坚定——"已看透生死的眼神"

### 配色系统
| 层 | 颜色 | 应用 |
|----|------|------|
| 底层氛围 | `#FFFFFF` 巫女白 | 服装主色 |
| 阵营色 | `#9A6BFF` 暗紫金 + `#E34234` 朱砂红 | 袴裤、衣纹 |
| 元素色 | `#0D0221` 暗紫黑（暗影）+ `#4A90D9` 灵力蓝 | 头发、灵力光点 |
| 稀有度色 | `#FFC857` 熔金 | 身后光晕、脚下光环（不抢白色调） |
| 情绪色 | `#FFFFFF` 纯白 | 符咒、注连绳纸垂 |

### 生图 Prompt
```
[1. 主体] Full-body character portrait of Kikyo, the legendary shrine maiden from Inuyasha. Female figure 7.5-head proportion, slender and elegant. Long straight black hair reaching waist, no bangs, one long strand falling over left chest. Deep purple eyes (#4B0082), cold and compassionate, half-lidded gaze. Classic miko attire: white kosode shirt + vermilion hakama pants (red #C41E3A) + white obi belt. Left shoulder has damaged iron armor plate. Right arm wrapped with shimenawa sacred rope (straw rope with white paper streamers).

[2. 姿态] Dynamic pose P2 (battle charge) + full draw bow stance + body in 3/4 turn to viewer. Left foot forward, right foot back in archer lunge. Left hand holding yumi bow extending left, right hand pulling bowstring to right ear. Arrow nocked and pointing up-right outside frame. Hamaya (purifying arrow) with light-blue (#4A90D9) glowing arrowhead. Bare feet standing on spiritual light lotus array.

[3. 氛围] Atmosphere: tragic beauty, moonlit solemnity, purifying power. Background: TRANSPARENT (PNG alpha). Behind body: faint moonlight halo + scattered firefly-like spiritual light points. Floating particles: white paper streamers (from shimenawa), soul-petal drifts, blue arrow glow trails. Mood: resolute, sorrowful, divine.

[4. 风格] Shinwa world style: cel-shaded anime with clean ink outlines, white and vermilion red palette with blue spiritual accents. Ethereal moonlight lighting from upper left. Soft shadow edges (graceful). Fill light from upper right using Shinwa purple #9A6BFF (faint). Rim light from behind-lower using UR molten gold #FFC857 (very subtle). High detail especially on face and eyes. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with bare feet visible.

[5. 关键元素] key_elements:
  - miko_attire_white_kosode_vermilion_hakama (mandatory, A-IP-archetype)
  - yumi_bow_with_white_ofuda_talismans (mandatory, B-weapon-prop)
  - long_straight_black_hair_waist_length (mandatory, C-anatomy)
  - shimenawa_rope_right_arm (mandatory, D-costume-signature)

negative_prompt:
  "half-body, bust, cropped, white background, colored background,
   modern clothing, kimono with bright patterns,
   text, watermark, multiple characters, weapon other than bow,
   shoes (must be bare feet), short hair, twintails"
```

### 透明背景处理要点
- 黑发丝边缘保留 ≤2px 羽化
- 巫女服硬切边缘
- 注连绳纸垂飘逸部分保留 1-2px 羽化
- 脚下莲花光阵**单独抠出**作为可选叠加层

---

## 5. 刻晴 Keqing · 玉衡星 · Aether · Thunder

### 角色卡片
- **ID**: `char_ur_keqing`
- **联动**: 《原神》璃月七星·玉衡星
- **武器**: 雷楔双剑·云来（匣中龙吟）
- **氛围特效**: `thundercloud_city`（雷云紫电）
- **背景叙事**: 刻晴是 Aether 议会从异界召唤的"可能性证据"，与烛龙有过激烈争论。
- **关键改进点**: 现有立绘为半身纯红底，丢失双马尾与璃月服饰细节。v2 必须**全身入画 + 双马尾完整 + 双剑拔出姿态 + 透明背景**。

### 识别锚点 ×3（必含项）
- **A 联动原型**: 双马尾发型（**深紫色长发双马尾，发尾有雷电尖**）
- **B 道具指纹**: 雷楔双剑（**双剑交叉背持或双持，紫色雷电缠绕剑身**）
- **C 解剖签名**: 紫色眼眸（`#7E57C2`，坚定自信，不信神明的神态）

### 全身构图
- **主姿态**: P2 战阵冲锋 + 双剑交叉斜指前方 + 身体微侧 3/4
- **动态参数**: 双剑在胸前交叉呈 V 字（防御反击姿态），身体微侧，左脚前右脚后；双马尾飘扬
- **三段式**:
  - 上段：双马尾飞扬 + 头顶雷元素符号虚影 + 双剑高举部分
  - 中段：璃月风格紫色金纹服饰 + 双剑剑柄 + 雷电缠绕
  - 下段：长筒靴 + 脚下电弧爆裂圈 + 紫色裙摆

### 视觉描述
- **性别/体型**: 少女，纤细但有力，7 头身
- **发型**: 双马尾发型（原作标志），深紫色长发，发尾渐变为电光蓝白色
- **眼睛**: 紫色（`#7E57C2`），坚定自信，斜视 15° 向上
- **服饰**: 改良版璃月风格，以紫色和金色为主。**保留原作核心：紫色金纹上衣 + 紫色短裙 + 白色连裤袜 + 长筒靴**
- **饰品**: 头顶雷元素符号虚影（紫色，悬浮发光），耳坠雷电形
- **双剑**: 雷楔·云来（匣中龙吟），单手剑 ×2。剑身细长，紫色剑刃，紫色雷电缠绕。剑格为雷电形
- **姿态**: 双剑在身体前方交叉呈 V 字（雷元素战斗起手式）
- **背景氛围**: 雷云与紫色闪电，远处群玉阁轮廓（**v2 不画实景，仅以光晕呈现**）

### 配色系统
| 层 | 颜色 | 应用 |
|----|------|------|
| 底层氛围 | `#7E57C2` 璃月紫 | 服饰主色 |
| 阵营色 | `#C79BFF` 暮紫 + `#00E5FF` 霓虹青 | 雷电、群玉阁虚影 |
| 元素色 | `#FFD600` 雷电金 + `#00BFFF` 电光蓝 | 雷电缠绕、闪电 |
| 稀有度色 | `#FFC857` 熔金 | 服饰金纹、背后光晕 |
| 情绪色 | `#FFFFFF` 纯白 | 雷电高光 |

### 生图 Prompt
```
[1. 主体] Full-body character portrait of Keqing, the Yuheng of the Liyue Qixing from Genshin Impact. Young female figure 7-head proportion, slender but powerful. Signature twin-tail hairstyle, deep purple long hair with tips fading to electric blue-white. Purple eyes (#7E57C2), determined and confident, gazing up 15 degrees. Improved Liyue-style outfit in purple (#7E57C2) and gold: purple and gold-trimmed top, purple short skirt, white tights, purple long boots. Gold trims with lightning pattern decorations. Thunder element symbol hologram floating above head (purple glow).

[2. 姿态] Dynamic pose P2 (battle charge) + dual swords crossed in V-shape in front of body + body in 3/4 turn to viewer + left foot forward. Twin-tails flowing backward. Two swords (Lei Qin Yun Lai) crossed at chest level, blades pointing diagonally up-forward, purple lightning wrapping each blade. Sword guards shaped like lightning bolts. Right hand forms sword-seal gesture near hip (signature pose) with electric arcs jumping between fingers.

[3. 氛围] Atmosphere: defiant confidence, storm-touched elegance. Background: TRANSPARENT (PNG alpha). Behind body: purple lightning radial halo (UR rarity color tinted purple). Floating particles: purple lightning fragments, white thunder sparks, storm clouds. Distant silhouette of Jade Chamber (Qun Yu Pavilion) only as faint purple light form (not detailed). Mood: determined, proud, "humanity governs its own fate".

[4. 风格] Aether world style: semi-realistic rendering with deep purple palette, electric blue and gold accents. Dramatic storm lighting with purple lightning flashes from upper left. Soft shadow edges (elegant). Rim light from behind-lower using UR molten gold #FFC857. High detail especially on face and sword blades. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with boots visible.

[5. 关键元素] key_elements:
  - twin_tail_hairstyle_purple_electric_blue_tips (mandatory, A-IP-archetype)
  - dual_swords_crossed_V_shape_lightning_wrapped (mandatory, B-weapon-prop)
  - purple_eyes_determined_upward_gaze (mandatory, C-anatomy)
  - thunder_symbol_hologram_above_head (mandatory, D-signature)

negative_prompt:
  "half-body, bust, cropped, white background,
   single sword, polearm, catalyst, bow (must be dual swords),
   text, watermark, multiple characters, cute chibi,
   modern clothing, school uniform, casual wear,
   different hair color, short hair, single tail, ponytail"
```

### 透明背景处理要点
- 双马尾发梢边缘保留 ≤2px 羽化
- 双剑雷电边缘保留 1px 羽化（紫色渐隐）
- 服饰边缘硬切
- 群玉阁虚影不画实景，仅保留轮廓光晕

---

## 6. 金乌 Jinwu · 十日巡天 · Shinwa · Flame

### 角色卡片
- **ID**: `char_ur_jinwu`
- **联动**: 漫威太阳黑子聚变之躯 + DC 火风暴原子重构
- **武器**: 曜日神弓·金乌（长弓，三足金乌羽翼化形）
- **氛围特效**: `broken_suns_inferno`（破碎十日残影）
- **背景叙事**: 裂隙纪元中十日的九日被虚无吞噬，仅金乌独自照耀 Shinwa。与烛龙是盟友也是镜像。
- **关键改进点**: 本角色**v1 无设计稿**，v2 从零开始。需注意"骄傲而悲悯"的双重情绪。

### 识别锚点 ×3（必含项）
- **A 神话原型**: 三足金乌（三足乌鸦/三足太阳鸟，**足部必须为三趾**）
- **B 道具指纹**: 曜日神弓（**长弓 + 太阳耀斑形弓梢 + 浓缩等离子箭**）
- **C 解剖签名**: 金橙白三色羽毛披肩（**双肩、背后展开凤凰火焰羽翼**）

### 全身构图
- **主姿态**: P1 神祇凌空 + 拉弓满弦 + 头部仰视 20°
- **动态参数**: 全身被金橙白三色火焰羽翼包裹；左脚单足踏在残日碎片上（仅一只脚触"地"，三足鸟特征），右脚悬浮
- **三段式**:
  - 上段：凤凰火焰羽翼展开 + 拉弓姿态 + 长弓太阳耀斑弓梢
  - 中段：金色战甲 + 三足金乌羽翼披肩 + 胸前太阳核心
  - 下段：三足（左二右一分别踏于残日碎片与悬浮光轮）+ 脚下熔金光圈

### 视觉描述
- **性别/体型**: 男性青年，修长 8 头身
- **发型**: 长发飞扬，金色为主，发梢为白炽白，发根赤红
- **眼睛**: 金色（`#FFD700`），骄傲且悲悯（眼中有泪光反射）
- **羽翼披肩**: 双肩与背后为凤凰火焰羽翼（半透明火焰），每根羽毛末端有金橙白三色渐变
- **战甲**: 黄金战甲，主色为熔金 `#FFC857` 与朱砂红 `#E34234`，胸前有太阳核心（圆形发光宝石）
- **长弓**: 曜日神弓（拉满弦），弓梢为太阳耀斑形（多瓣火焰花纹），弓弦为浓缩等离子体（金白发光），箭矢为浓缩等离子球
- **足部**: 三足金乌特征——双足（人腿）+ 一足（鸟爪），鸟爪为黑色三趾，爪尖金色
- **表情**: 骄傲而悲悯。视光明为责任，对黑暗从不宽恕，会为凡人灼伤双眼而落泪。

### 配色系统
| 层 | 颜色 | 应用 |
|----|------|------|
| 底层氛围 | `#FFC857` 熔金到 `#FF4D00` 熔岩橙渐变 | 全身光晕 |
| 阵营色 | `#9A6BFF` 暗紫金 + `#E34234` 朱砂红 | 战甲、披风内衬 |
| 元素色 | `#FF4D00` 熔岩橙 + `#FFD700` 金黄 + `#FFFFFF` 白热 | 羽翼、火焰 |
| 稀有度色 | `#FFC857` 熔金 | 全屏光晕 |
| 情绪色 | `#FFFFFF` 泪光 | 眼角高光 |

### 生图 Prompt
```
[1. 主体] Full-body character portrait of Jinwu, the three-legged golden crow sun deity from Chinese mythology (Shanhaijing). Young male figure 8-head proportion, slender and godlike. Long flowing hair, gold (#FFD700) roots transitioning to red, white-hot tips. Golden eyes (#FFD700), proud yet sorrowful, with tear-light reflection in pupils. Golden battle armor in molten gold (#FFC857) and vermilion (#E34234), with sun-core glowing gem. Phoenix flame wings (semi-transparent fire) spreading from shoulders and back, each feather tip gradient gold-orange-white. THREE LEGS visible: two human legs + one bird leg with three black claws (golden claw tips).

[2. 姿态] Dynamic pose P1 (deity hovering) + bow drawn fully + head tilted up 20 degrees. Body floating, left foot planted on broken sun fragment (half-circle remnant of destroyed sun), right foot hovering on golden light disc. Three-legged crow feature: bird leg curling slightly behind. Long bow (Yao Ri Shen Gong) drawn at full: bow-tips shaped like solar flares (multi-petal flame), bowstring is condensed plasma (gold-white glowing), arrow is condensed plasma sphere.

[3. 氛围] Atmosphere: tragic glory, sole survivor of ten suns. Background: TRANSPARENT (PNG alpha). Behind body: massive molten gold radial halo (full screen, UR rarity color). Floating particles: golden sun-flare fragments, broken sun remnants (9 silhouettes), ember sparks. No detailed environment — only light. Mood: proud, sorrowful, eternal duty.

[4. 风格] Shinwa world style: cel-shaded anime with Chinese ink-wash outlines, flowing brush-stroke lines, gold leaf accents. Semi-realistic facial detail with golden eye glow. Dramatic lighting from upper left (multiple overlapping light sources simulating solar radiance). Fill light from upper right using Shinwa vermilion #E34234 (faint). Rim light from behind-lower using UR molten gold #FFC857 (strong). High detail, gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with three legs visible.

[5. 关键元素] key_elements:
  - three_legs_two_human_one_bird_with_three_claws (mandatory, A-myth-archetype)
  - long_bow_with_solar_flare_bowtips (mandatory, B-weapon-prop)
  - phoenix_flame_wings_gold_orange_white_gradient (mandatory, C-anatomy)

negative_prompt:
  "two legs only, four legs, human legs only (must have bird leg),
   half-body, bust, cropped, white background,
   text, watermark, multiple characters, cute kawaii,
   dark palette, gloomy, shadow (must be golden bright),
   bow without solar flare tips, modern weapon"
```

### 透明背景处理要点
- 羽翼火焰边缘保留 2-3px 重度羽化（火焰半透明感）
- 弓梢太阳耀斑边缘硬切（保留锐度）
- 三足触"地"的残日碎片单独抠出为可选层

---

## 7. 女娲 Nuwa · 泥塑苍天 · Aether · Earth

### 角色卡片
- **ID**: `char_ur_nuwa`
- **联动**: 漫威凤凰女生命念力 + DC 沼泽怪物大地共鸣
- **武器**: 五色补天杖（大地法杖，掌中五色石悬浮）
- **氛围特效**: `floating_island_aurora`（浮岛极光）
- **背景叙事**: 以最后一块补天石为锚，在以太星空中托起浮岛，庇护流离的凡人。虚无的反面。
- **关键改进点**: 本角色**v1 无设计稿**，v2 从零开始。需强化"母神 + 创世"的双重气质。

### 识别锚点 ×3（必含项）
- **A 神话原型**: 人首蛇身（**上半身人形 + 下半身蛇尾**，与烛龙同构但更柔和）
- **B 道具指纹**: 五色石（**掌心悬浮青/赤/黄/白/黑五色补天石**）
- **C 解剖签名**: 大地母神长袍（**袍面绣有山河纹样，会缓慢移动**）

### 全身构图
- **主姿态**: P1 神祇凌空 + 双手合十于胸前（托举五色石）+ 头部微微低俯
- **动态参数**: 蛇尾 S 形盘旋于浮岛残片之上（浮岛由脚下氛围圈代替）；长袍下摆山河纹样缓慢浮动
- **三段式**:
  - 上段：长发散开（黑色带青赤黄白黑五色挑染）+ 大地母神冠冕 + 身后极光光环
  - 中段：人首 + 五色补天长袍 + 合十双手 + 掌心五色石
  - 下段：蛇尾（白色 + 青赤黄黑四色鳞片相间）+ 脚下浮岛光圈

### 视觉描述
- **性别/体型**: 女性神祇，端庄母性，8 头身
- **发型**: 黑长直发及腰，挑染五色（青/赤/黄/白/黑各一缕），头顶大地母神冠冕（五色石镶嵌）
- **眼睛**: 深褐色（`#5C3317`），沉静、慈祥、不容亵渎
- **人首**: 女性青年面庞，鹅蛋脸，杏眼垂视，嘴唇微闭
- **长袍**: 大地母神风格长袍，主色为翡翠绿 `#2E8B57` 与赭石 `#8B4513`，袍面绣有山河纹样（**纹样会在长袍表面缓慢浮动**），领口与袖口为五色丝线绣边
- **双手**: 合十于胸前，掌心悬浮五色石（五颗，每颗拇指大）：青（`#0097A7`）、赤（`#FF4D00`）、黄（`#FFD700`）、白（`#FFFAF0`）、黑（`#0D0221`）
- **蛇尾**: 白色底 + 青/赤/黄/黑四色鳞片相间，从腰部延伸，S 形盘旋 3 圈，尾尖指向前方观众

### 配色系统
| 层 | 颜色 | 应用 |
|----|------|------|
| 底层氛围 | `#2E8B57` 翡翠绿到 `#0097A7` 深海青渐变 | 全身光晕 |
| 阵营色 | `#C79BFF` 暮紫 + `#00E5FF` 霓虹青 | 长袍袖口、极光光环 |
| 元素色 | `#795548` 大地褐 + 五色石 | 蛇身鳞片、五色石 |
| 稀有度色 | `#FFC857` 熔金 | 头顶冠冕金边、背后光晕 |
| 情绪色 | `#FFD700` 神圣金 | 掌心光晕、温和高光 |

### 生图 Prompt
```
[1. 主体] Full-body character portrait of Nuwa, the creator goddess from Chinese mythology who mended the sky with five-color stones. Female deity figure 8-head proportion, dignified and maternal. Black long straight hair reaching waist with five-color highlights (cyan, vermilion, yellow, white, black). Earth-mother crown on head with five-color stones inlaid. Deep brown eyes (#5C3317), serene and compassionate. Earth-mother style long robe, jade green (#2E8B57) and ochre (#8B4513), with embroidered mountain-river patterns on robe surface (subtle moving pattern), five-color silk embroidery on collar and cuffs.

[2. 姿态] Dynamic pose P1 (deity hovering) + hands pressed together at chest holding five stones + head slightly bowed looking down at viewer + body floating. Serpent tail S-shape coiling three loops (white base with cyan-vermilion-yellow-black scale pattern in alternating rows), tail tip pointing forward at viewer. Floating island aura beneath feet (radial glow only, no solid island).

[3. 氛围] Atmosphere: maternal creation, cosmic restoration, life-giving force. Background: TRANSPARENT (PNG alpha). Behind body: massive five-color aurora halo (cyan + vermilion + yellow + white + black vertical bands, soft). Floating particles: five-color stone fragments, floating petal motes, life-spirit wisps. No solid environment — only light and particles. Mood: serene, eternal, protective.

[4. 风格] Aether world style: semi-realistic rendering with jade green and earth-tone palette, five-color stone accents. Soft ethereal lighting. Gentle gradients. Fill light from upper right using Aether purple #C79BFF (faint). Rim light from behind-lower using UR molten gold #FFC857 (strong). High detail especially on face and five stones. Gacha game character art, 2:3 vertical aspect ratio, transparent background, full body with serpent tail visible.

[5. 关键元素] key_elements:
  - serpent_tail_with_five_color_scales (mandatory, A-myth-archetype)
  - five_color_stones_in_palms_cyan_vermilion_yellow_white_black (mandatory, B-weapon-prop)
  - mountain_river_embroidery_on_robe (mandatory, D-costume-signature)
  - earth_mother_crown (mandatory, D-signature)

negative_prompt:
  "human legs only, no serpent tail,
   half-body, bust, cropped, white background,
   text, watermark, multiple characters, cute kawaii,
   dark palette, gloomy, single robe color,
   five stones missing or wrong colors"
```

### 透明背景处理要点
- 蛇尾鳞片边缘保留 ≤2px 羽化
- 极光光环边缘重度羽化（5-8px）
- 五色石硬切边缘
- 头顶冠冕的金属边硬切

---

## UR 总览对照表

| 角色 | 世界 | 元素 | 主姿态 | 识别锚点（3 项） | 关键改进 |
|------|------|------|--------|------------------|----------|
| 烛龙 | Shinwa | Flame | P1 神祇凌空 | 蛇尾三圈 / 昼夜轮 / 异色瞳 | 全身入画 + 透明背景 |
| 虚无 | Aether | Shadow | P1 神祇凌空 | 无面镜面 / 胸口黑洞 / 星云身体 | 9 头身修长 + 透明背景 |
| 刑天 | Ironveil | Metal | P2 战阵冲锋 | 无头胸眼 / 干戚双武器 / 焊痕颈部 | 全身 + 双武器完整 |
| 桔梗 | Shinwa | Shadow | P2 战阵冲锋 | 巫女服 / 破魔灵弓 / 黑长直发 | 巫女服 + 拉弓姿态 |
| 刻晴 | Aether | Thunder | P2 战阵冲锋 | 双马尾 / 雷楔双剑 / 紫眼 | 双剑交叉 V 字 |
| 金乌 | Shinwa | Flame | P1 神祇凌空 | 三足 / 太阳耀斑弓 / 凤凰火焰羽翼 | 新角色，悲悯骄傲双重情绪 |
| 女娲 | Aether | Earth | P1 神祇凌空 | 蛇尾五色鳞 / 五色石 / 山河纹长袍 | 新角色，母神创世气质 |

## UR 通用 QC 备注
- 7 张立绘必须统一"主光来自左上方 7 点钟 + 阵营色补光"——交付前比对
- 蛇尾（烛龙/女娲）必须 S 形盘旋 ≥3 圈，禁止直线下垂
- 武器（昼夜轮/无相刃/干戚/灵弓/双剑/曜日弓/补天杖）必须完整可见，禁止截断
- 透明背景最终验证：每张图在黑色 + 白色 + 阵营色三种底色上各看一次，确认无硬黑切边

---

## 生图批量作业顺序（建议）

1. **先做烛龙与金乌**（同为 Shinwa Flame，可对比统一阵营色处理）
2. **再做女娲**（Shinwa 蛇尾母神，与烛龙共享"蛇尾"技法）
3. **做桔梗**（Shinwa 巫女服，参考原神/犬夜叉角色已有的高水准立绘）
4. **做刻晴**（原神联动，对照官方立绘风格避免侵权式"过像"）
5. **做刑天**（Ironveil 复杂机甲，需先打小稿测试"无头战士"的可读性）
6. **最后做虚无**（Aether 最复杂，需多打几个 seed 选最佳"暗物质溶解"质感）

每张生图前先以**512×768 低分辨率 + 简单姿态**测试三段式构图与识别锚点，达标后再 hires fix 到 1536×2304。