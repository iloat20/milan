# 武器重做 + 抽卡界面重设计 · 设计文档

> 日期：2026-08-13 ｜ 触发：用户反馈「武器设计不符合人物背景故事，抽卡界面可以重新设计」
> 流程：brainstorming（方向已确认 → 本文档为执行前的最终设计确认）
> 已确认决策：
> 1. **武器**：全量重做 28 把，按角色 lore 定制。
> 2. **抽卡界面**：中西融合法阵升级（保留次元裂缝演出，视觉焕新为八卦/回纹/符箓 + 霓虹玻璃）。

---

## 1. 问题根因

当前 28 张武器图（`assets/weapons/<WeaponVfx>.webp`）由早期生图批量产出，**未忠实还原 `data.json` 的 `WeaponDesc` 叙事**，且 14 个 SR/R 角色根本没有武器描述字段，图与角色背景脱节。

- **14 个 UR/SSR** 已在 `data.json` 写明 `Weapon`（武器名）+ `WeaponDesc`（权威形态描述）。武器视觉的单一事实来源就是 `WeaponDesc`。
- **14 个 SR/R** 的 `Weapon`/`WeaponDesc` 为空 → 图只能凭 `WeaponVfx` 文件名臆测，必然泛化。

**修复策略**：以 `WeaponDesc` 为金标准重做高稀有度武器图；为 14 个 SR/R 补写 `Weapon`/`WeaponDesc`（数据层 additive，不改序列化结构）后再生图，使全 28 把武器皆有 lore 支撑。

---

## 2. 武器视觉风格规范（全 28 把统一）

| 维度 | 规范 |
|---|---|
| 构图 | **孤立武器单体**，居中、留白充足；**透明背景（RGBA）**；绝不出现角色、绝不叠覆立绘（红线：武器不得覆于角色立绘之上）。 |
| 美学 | **中西融合**：东方神话材质语言（青铜 / 玄铁 / 玉 / 水墨 / 符箓 / 云纹 / 八卦）+ 西式霓虹能量（发光刃缘、全息微光、元素能量流）。 |
| 元素配色 | Flame=熔金赤、Shadow=幽紫黑、Metal=庚金白/玄铁灰、Wood=青碧、Water=沧蓝、Earth=赭黄玄、Light=月白金、Thunder=雷紫电蓝。 |
| 光照 | 统一边缘光（rim-light）+ 内层发光；能量丝带暗示悬浮/蓄能（静态图）。 |
| 轮廓 | **禁用通用几何**。每把武器必须有独特剪影 + lore 母题（如烛龙=带竖瞳的昼夜神环，刑天=盾+斧齿轮复合，蚩尤=虎魄纹裂魂斧）。 |
| 画布 | **1024×1024 正方形 RGBA**，武器居中、占画面 70–80%，四周留透明边。与 `CharacterDetailScreen` 的 2x 采样加载匹配。 |
| 质量 | UR/SSR 用 high；SR/R 用 standard（控制积分）。 |

---

## 3. 28 角色武器概念映射

> 列：角色 / 稀有度·世界·元素 / 当前 WeaponVfx / 权威武器（来源）/ 生图要点
> 「★补」= 需回填 data.json 的 `Weapon`/`WeaponDesc`（SR/R）

### 3.1 高稀有度（UR×7 + SSR×7，已有 WeaponDesc，忠实还原）

| 角色 | 稀有·界·元素 | 当前 vfx | 武器（权威） | 生图要点 |
|---|---|---|---|---|
| 烛龙 Zhulong | UR·Shinwa·Flame | sun_orb_flame | 阖辟神瞳·昼夜轮 | 神环：半面熔金烈焰（昼）、半面吞星幽暗（夜）；环心一只永不闭合的**竖瞳**。昼夜双色分明，竖瞳为视觉焦点。 |
| 虚无 Wuxu | UR·Aether·Shadow | void_rift_blade | 归墟之噬·无相刃 | 无柄无锷的**无相之刃**，刃身=被吞噬的星空（星云纹理）；光在刃前折返的扭曲感。幽紫黑 + 星点。 |
| 刑天 Xingtian | UR·Ironveil·Metal | gear_axe_storm | 干戚·不灭齿轮 | **盾+斧复合兵装**：巨斧横扫 + 齿轮驱动；胸口能量之眼。庚金白/玄铁，齿轮咬合机械感 + 不死意象。 |
| 桔梗 Kikyo | UR·Shinwa·Shadow | shadow_bow_arrow | 破魔灵弓·封魂 | 灵力凝成的破魔之弓；弓身缠**四魂之玉碎片**；箭带幽冥回响。幽紫 + 玉色微光。 |
| 刻晴 Keqing | UR·Aether·Thunder | lightning_dual_swords | 雷楔双剑·云来 | 一对雷楔为锷的双剑，剑身流转**璃月雷纹**；掷出雷楔标记。雷紫电蓝 + 云纹。 |
| 凤凰 Fenghuang | SSR·Shinwa·Flame | phoenix_wing_flame | 焚羽·涅槃翼 | 双翼兵装，每片翎羽=未冷的火；翼展千度、焦土生新芽。熔金赤羽 + 重生绿芽点缀。 |
| 相柳 Xiangliu | SSR·Aether·Shadow | venom_fang_whip | 九首·毒牙鞭 | 九节毒鞭，节节=饕餮之口、蠕动如活物；毒雾漫野。幽绿毒 + 暗紫。 |
| 雷神 Leishen | SSR·Ironveil·Thunder | mjolnir_hammer_arc | 轰霆·楔石锤 | 天楔为柄、雷云为锤头的巨锤；落点风云倒卷、万雷归宗。雷紫电蓝 + 锤头雷云。 |
| 飞廉 Feilian | SSR·Shinwa·Wood | wind_blade_dash | 裂空·疾风刃 | 双刃，刃过无声、唯余被割裂的气流；风墙成壁。青碧 + 风纹流线。 |
| 商羊 Shangyang | SSR·Aether·Light | star_oracle_sigil | 卜天·星谶盘 | 占盘，盘面流转星轨微光；引星辉为刃。月白金 + 星轨纹。 |
| 金乌 Jinwu | UR·Shinwa·Flame | solar_orb_bow | 曜日神弓·金乌 | 羽翼为弦、烈日为箭的神弓；拉弦点燃大气之氧，射凝缩恒星之火。熔金赤 + 日炎。 |
| 女娲 Nuwa | UR·Aether·Earth | five_color_stone_staff | 五色补天杖 | 杖首嵌最后一块**五色补天石**（青赤白黑黄五光）；点泥成生。赭黄玄 + 五色流转。 |
| 蚩尤 Chiyou | SSR·Ironveil·Metal | tiger_soul_cleaver | 虎魄·裂魂斧 | 败者之魂铸入斧刃的凶兵；斧面隐现**虎魄之纹**，饮血则啸。庚金白 + 虎魄赤纹。 |
| 白虎 Baihu | SSR·Shinwa·Metal | vibranium_tiger_claw | 庚金·虎啸爪 | 西方庚金之气凝成的虎爪；爪尖肃杀白芒，撕裂寒铁如腐木。庚金白 + 秋杀寒芒。 |

### 3.2 中低稀有度（SR×7 + R×7，★补 WeaponDesc 后生图）

| 角色 | 稀有·界·元素 | 当前 vfx | 拟补武器概念（★补） | 生图要点 |
|---|---|---|---|---|
| 狻猊 Suanni | SR·Shinwa·Flame | roar_shock_claw | 炎吼·焚音爪 ★补 | 龙子狻猊好烟好坐；火焰狮爪兵装，挥击迸发**音波烈焰环**。熔金赤 + 声波纹。 |
| 精卫 Jingwei | SR·Aether·Wood | wind_stone_projectile | 衔石·逐浪弹 ★补 | 精卫衔石填海；风缕编成的投石索，抛出晶石。青碧 + 飞石。 |
| 穷奇 Qiongqi | SR·Ironveil·Metal | regen_blast_cannon | 啮钢·噬魂炮 ★补 | 穷奇（食人翼虎）；以吞噬之钢锻造的兽口炮，喷吐金属碎片。玄铁灰 + 兽口。 |
| 旋龟 Xuanwu | SR·Shinwa·Earth | shell_barrier_earth | 负岳·玄甲盾 ★补 | 旋龟；活体玄武龟甲盾，甲面山脊纹。赭黄玄 + 山纹。 |
| 毕方 Bifang | SR·Aether·Thunder | thunder_feather_dive | 焚羽·惊雷翎 ★补 | 毕方（一足火鸟）；单根燃烧雷羽镖，蓄雷而投。雷紫 + 火羽。 |
| 花妖 Huayao | SR·Aether·Wood | petal_ribbon_blade | 缠丝·落花刃 ★补 | 花妖；花瓣缎带凝成旋转飞刃。青碧 + 花瓣缎带。 |
| 饕餮 Taotie | SR·Shinwa·Flame | bronze_greed_flame | 噬纹·贪鼎 ★补 | 饕餮（贪食）；青铜贪鼎，鼎口即焰噬之口。青铜 + 饕餮纹 + 焰。 |
| 狸力 LiLi | R·Shinwa·Earth | earth_burrow_strike | 掘地·裂壤爪 ★补 | 狸力（掌掘）；土系掘地爪锄。赭黄 + 壤纹。 |
| 钦原 Qinyuan | R·Aether·Metal | poison_stinger_swarm | 毒螫·群蜂针 ★补 | 钦原（毒蜂）；一簇剧毒蜂针。幽绿 + 针簇。 |
| 跂踵 SiShu | R·Ironveil·Shadow | shadow_wire_tangle | 缚影·缠魂丝 ★补 | 跂踵（招死之鸟）；影丝缠缚魂魄。幽紫黑 + 影线。 |
| 蠃鱼 Luoyu | R·Shinwa·Water | water_trident_surge | 涌潮·三叉戟 ★补 | 蠃鱼（鱼鸟）；水波三叉戟，戟身鱼形。沧蓝 + 鱼纹。 |
| 当康 Dangang | R·Aether·Wood | tusk_charge_wind | 獠突·冲岳牙 ★补 | 当康（瑞兽野猪）；獠牙冲撞兵装。青碧 + 獠牙。 |
| 山魈 Shanxiao | R·Ironveil·Earth | scrap_claw_mine | 拾荒·碎铁爪 ★补 | 山魈（山鬼）；拾荒废铁拼成的爪。玄铁灰 + 废铁。 |
| 夜叉 Yecha | R·Aether·Shadow | shadow_dagger_whisper | 喑杀·影刃 ★补 | 夜叉（捷鬼）；无声影刃，出鞘无音。幽紫黑 + 影刃。 |

---

## 4. 抽卡界面重设计规范（中西融合法阵升级）

保留现有 `GachaScreen.kt` 的**结构与演出状态机不变**（标题→资源胶囊→卡池面板→预览横排→召唤法阵→单/十连→摘要→结果网格→导航；四阶段 reveal 编排、revealToken 作废、aura 光环均不动），仅焕新视觉语言。

| 区域 | 现状 | 重设计 |
|---|---|---|
| 召唤法阵 | 静态径向渐变圆 + 双/三环 + ✦ | **中西融合法阵**：外环=八卦（bagua）卦象、中环=回纹（meander）边饰、内环=符箓（talisman）glyph；紫金霓虹辉光，`riftSwell` 时整体缓旋 + 缩放。 |
| 卡池面板 / 资源胶囊 | GlassPanel(gold) | GlassPanel 保留玻璃质感，四角加**回纹角饰**；保底行维持霜蓝。 |
| 预览横排头像 | 圆形 PortraitImage | 不变（圆形裁切，刻意不启 aura，与全游戏一致）。 |
| 单抽/十连按钮 | NeonButton + GoldButton | 保留；按钮描边加**云纹/符箓**微元素，紫金主色不变。 |
| 翻牌演出层 | GpuRevealLayer + 稀有度光晕 + 大立绘卡 + 全息箔 | 揭晓卡边框升级为**符箓/云纹框**（UR 保留熔金爆发叠层）；稀有度白闪、aura 光环、跳过逻辑不变。 |
| 背景 | PageBackground | 维持现有暗底；法阵区域叠加极淡**祥云/八卦**暗纹（不抢主体）。 |

**不破坏项（红线）**：reveal 协程编排、`revealToken` 作废语义、`PortraitImage(aura=true)` 调用、`HolographicFoilOverlay`、保底进度读取、`CrashReporter` 留痕路径全部保留。

---

## 5. 执行计划与成本

1. **回填数据**（可选但建议）：为 14 个 SR/R 写 `Weapon`/`WeaponDesc`（★补列），与将生图一致；additive，不改序列化键。
2. **武器生图**：串行逐张（沿用 `p1-gen/<id>/` 模式，避免 ImageGen 并行丢图）；UR/SSR high、SR/R standard；rembg(u2netp) 透明化 → 1024² 画布适配 → 重命名 `assets/weapons/<WeaponVfx>.webp` 覆盖。
3. **QC**：`qc_portraits.py --strict` 改武器口径（alpha 必含、体积、几何居中、锚点=武器单体）。
4. **Gacha 屏改造**：仅改 `GachaScreen.kt` 的 Compose 视觉（法阵/边框/按钮装饰），不动状态机。
5. **验证**：真实环境 `assembleDebug` + `testDebugUnitTest` + 整机 `adb install` 冷启动（沙箱不编译，留真实环境闭环）。

**积分预估**：UR/SSR 14×~10 + SR/R 14×~5 ≈ **140–210 积分**（取 high 全量则 ~280）。本次设计/数据/代码为本地操作，零积分；仅生图消耗积分。

---

## 6. 风险与红线

- 武器图必须**透明背景 + 孤立单体 + 不叠覆立绘**（AGENTS.md 红线）。
- 不改 `data.json` 序列化键名（仅补 SR/R 空字段，additive）。
- 抽卡屏**不改演出状态机**，避免回归 reveal 逻辑。
- 生图沿用串行模式（并行会丢图，前序已验证）。
- 真机编译验证须真实环境（沙箱限制）。

---

## 待确认

- [ ] 武器视觉风格规范（§2）是否认可？
- [ ] 14 个 SR/R 的拟补武器概念（§3.2）是否认可（或需调整）？
- [ ] 是否回填 data.json 的 SR/R 武器字段（§5.1）？
- [ ] 抽卡界面重设计规范（§4）是否认可？
- [ ] 确认后：全量生图（约 140–280 积分）+ 改 Gacha 屏。
