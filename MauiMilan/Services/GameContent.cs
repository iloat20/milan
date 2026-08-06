namespace Milan.Maui.Services;

// GameService 的「内置兜底内容」分部实现。
//
// 为什么单独一个文件：这段角色/剧情/语音/武器表原本有 420 行硬编码字面量，直接压在
// GameService 主体里，把真正的业务逻辑（抽卡事务、养成事务、存档回滚）淹没在数据中间，
// 任何一次内容改动都会让 GameService 的 diff 变得无法审阅。
//
// 定位：这里是 data.json 缺失/损坏时的<b>兜底副本</b>，不是内容的主来源。
// 内容主来源是 MauiMilan/Platforms/Android/Assets/data.json；新增角色请<b>先改 data.json</b>，
// 本文件仅在需要保证离线可玩时同步。两条加载路径最终都会经过 EnrichCharacters()
// 补齐派生字段（阵营/背景故事/语音/武器名与描述），保证字段口径一致。

public partial class GameService
{
    // ------------------------------------------------------------------ characters

    private void Add(string id, string name, string title, string world, string element,
        int rarity, int[] stats, int stars, bool breakthrough, string lore, string treeId, List<SkillData> skills)
    {
        Characters.Add(new CharacterDataEntry
        {
            CharacterId = id, DisplayName = name, Title = title, World = world, Element = element,
            BaseRarity = rarity, BaseStats = stats, MaxStage = 4, MaxStars = stars,
            CanBreakthrough = breakthrough, Lore = lore, TalentTreeId = treeId, Skills = skills
        });
    }

    private static SkillData Sk(string id, string name, string desc, string element, string type, int power) =>
        new() { SkillId = id, DisplayName = name, Description = desc, Element = element, Type = type, Power = power };

    private void BuildCharacters()
    {
        Characters.Clear();
        // ========== UR 4★ ==========
        // 烛龙 Zhulong - 山海经"烛龙"，睁眼为昼闭眼为夜，漫威凤凰之力+DC火神
        Add("char_ur_zhulong", "烛龙 Zhulong", "昼夜之主", "Shinwa", "Flame", 4,
            new[] { 165, 95, 1250, 19 }, 7, true,
            "上古山海经所载烛龙，睁眼为白昼、闭眼为长夜，吐息化为天火。漫威凤凰之力与DC火神之威在其体内共鸣，觉醒之日，星辰为之焚尽。",
            "tree_zhulong", new() {
                Sk("zhulong_1", "烛照八荒", "以烛龙真火焚烧全体敌人，造成巨额火焰伤害", "Flame", "Ultimate", 95),
                Sk("zhulong_2", "昼夜轮转", "切换昼夜：昼间攻速+30%，夜间暴击+25%", "Flame", "Active", 70),
                Sk("zhulong_3", "不灭之焰", "受到致命伤害时保留1点生命并回复30%血量（每场一次）", "Flame", "Passive", 60),
            });
        // 虚无 Wuxu - 山海经"混沌"+漫威湮灭(DC反物质)
        Add("char_ur_wuxu", "虚无 Wuxu", "万象终焉", "Aether", "Void", 4,
            new[] { 155, 125, 1150, 17 }, 7, true,
            "山海经所载混沌之形，无面无相，万物归虚。漫威湮灭与DC反监视者的力量在此交汇，它来自维度裂隙深处，以星辰为食，所余唯有虚无。",
            "tree_wuxu", new() {
                Sk("wuxu_1", "湮灭奇点", "在敌阵制造黑洞，持续吸引并撕裂范围内所有目标", "Void", "Ultimate", 92),
                Sk("wuxu_2", "虚化", "进入虚无形态，闪避下一次攻击并回复能量", "Void", "Active", 68),
                Sk("wuxu_3", "存在抹消", "普攻有15%概率直接削减目标15%当前生命", "Void", "Passive", 65),
            });
        // 刑天 Xingtian - 山海经"刑天"，漫威金刚狼+DC毁灭日
        Add("char_ur_xingtian", "刑天 Xingtian", "不死战神", "Ironveil", "Metal", 4,
            new[] { 170, 155, 1500, 13 }, 7, true,
            "山海经刑天，断首仍以乳为目、以脐为口，执干戚而舞。漫威金刚狼的不愈与DC毁灭日的进化在其合金躯壳中重生，铁帷纪元最不屈的战士。",
            "tree_xingtian", new() {
                Sk("xingtian_1", "干戚狂舞", "挥舞巨斧横扫前方，造成范围伤害并击退", "Metal", "Ultimate", 90),
                Sk("xingtian_2", "不死之躯", "受到伤害时叠加狂暴层数，每层+8%攻击", "Metal", "Passive", 72),
                Sk("xingtian_3", "断首重生", "阵亡后复活一次，回复50%生命并获得霸体", "Metal", "Passive", 75),
            });
        // 桔梗 Kikyo ——《犬夜叉》孤独的巫女，封印四魂之玉的破魔之弓
        Add("char_ur_kikyo", "桔梗 Kikyo", "悲运的巫女", "Shinwa", "Shadow", 4,
            new[] { 158, 90, 1050, 20 }, 7, true,
            "《犬夜叉》传奇巫女桔梗，灵力高强、清冷孤高。曾奉命守护四魂之玉，与半妖犬夜叉相知相爱，却遭奈落阴谋所害，含恨而终。后以陶土复生，带着前世记忆与执念独行于乱世，以破魔之箭净化众生、封印邪祟。她是不被世间的巫女，行走于生死之间，以悲悯之心超度亡魂。",
            "tree_kikyo", new() {
                Sk("kikyo_1", "破魂之箭", "射出贯穿一切的破魔箭，对单体造成巨额真实伤害并封印其技能3秒", "Shadow", "Ultimate", 90),
                Sk("kikyo_2", "净化之阵", "展开净化结界，持续驱散队友负面状态并回复生命", "Shadow", "Active", 68),
                Sk("kikyo3", "孤独之念", "场上每有一名队友阵亡，自身攻击与暴击大幅提升", "Shadow", "Passive", 72),
            });
        // 刻晴 Keqing ——《原神》璃月七星之玉衡，坚信人可胜天的雷之剑士
        Add("char_ur_keqing", "刻晴 Keqing", "玉衡星", "Aether", "Thunder", 4,
            new[] { 162, 105, 1150, 21 }, 7, true,
            "《原神》璃月七星之玉衡星刻晴，勤勉正直、不信神明。她坚信人类的命运应由自己掌握，而非托付于仙家神力。身为雷元素使用者，她将雷电之力融入剑术，创造出独一无二的「云来剑法」。以凡人之躯比肩神明，以雷霆之势守护璃月。",
            "tree_keqing", new() {
                Sk("keqing_1", "天街游移", "化身雷霆穿梭敌阵，对路径上所有敌人造成多段雷伤并瞬移至终点", "Thunder", "Ultimate", 91),
                Sk("keqing_2", "雷楔瞬斩", "投掷雷楔标记敌人，瞬移至目标身后发动必定暴击的斩击", "Thunder", "Active", 72),
                Sk("keqing_3", "玉衡之誓", "普攻积攒「玉衡」层数，满层后下次技能伤害翻倍", "Thunder", "Passive", 70),
            });
        // ========== SSR 3★ ==========
        // 凤凰 Fenghuang - 山海经"凤凰"，漫威凤凰女+DC火星猎人
        Add("char_ssr_fenghuang", "凤凰 Fenghuang", "涅槃圣禽", "Shinwa", "Flame", 3,
            new[] { 125, 85, 1050, 16 }, 6, true,
            "山海经凤凰，五色而文，德义礼仁信。漫威凤凰女琴·葛蕾的念力与DC火星猎人的火焰在其羽翼中涅槃，浴火重生，不死不灭。",
            "tree_fenghuang", new() {
                Sk("fenghuang_1", "涅槃之火", "以凤凰真火焚烧全体，命中目标灼烧5秒", "Flame", "Ultimate", 82),
                Sk("fenghuang_2", "浴火重生", "阵亡时化为火卵，3秒后复活并回复40%生命", "Flame", "Passive", 70),
                Sk("fenghuang_3", "凤鸣朝阳", "鸣叫提升全队20%攻击力，持续8秒", "Flame", "Active", 60),
            });
        // 相柳 Xiangliu - 山海经"相柳"，漫威毒液+DC小丑
        Add("char_ssr_xiangliu", "相柳 Xiangliu", "九首毒厄", "Aether", "Void", 3,
            new[] { 118, 95, 1100, 14 }, 6, false,
            "山海经相柳，九首蛇身，所到之处化为毒泽。漫威毒液的共生体与小丑的剧毒在其血液中流淌，吐息即瘟疫，触碰即腐蚀。",
            "tree_xiangliu", new() {
                Sk("xiangliu_1", "九首噬天", "九首齐出撕咬前方，造成多段伤害并叠加中毒", "Void", "Ultimate", 80),
                Sk("xiangliu_2", "毒泽万里", "在地面制造毒沼，踏入的敌人持续掉血减速", "Void", "Active", 65),
                Sk("xiangliu_3", "腐蚀之血", "攻击附带中毒，中毒目标受到治疗量降低50%", "Void", "Passive", 58),
            });
        // 雷神 Leishen - 山海经"雷兽"，漫威雷神索尔+DC宙斯
        Add("char_ssr_leishen", "雷神 Leishen", "雷霆裁决", "Ironveil", "Thunder", 3,
            new[] { 130, 90, 1000, 17 }, 6, false,
            "山海经雷兽，龙身人头，腹中雷鸣。漫威雷神索尔的妙尔尼尔与DC宙斯的雷霆在其机械核心中锻造，以闪电审判一切。",
            "tree_leishen", new() {
                Sk("leishen_1", "雷霆万钧", "召唤巨型闪电劈向敌阵，主目标伤害翻倍", "Thunder", "Ultimate", 84),
                Sk("leishen2", "雷神之锤", "投掷雷霆之锤，命中后弹射至多3个敌人", "Thunder", "Active", 66),
                Sk("leishen_3", "静电充能", "每次受到攻击积累静电，下次技能伤害+25%", "Thunder", "Passive", 56),
            });
        // 飞廉 Feilian - 山海经"飞廉"，漫威快银+DC闪电侠
        Add("char_ssr_feilian", "飞廉 Feilian", "风驰电掣", "Shinwa", "Wind", 3,
            new[] { 122, 70, 880, 24 }, 6, false,
            "山海经飞廉，鹿身雀首，司掌风伯。漫威快银的神速与DC闪电侠的神速力在其血脉中奔袭，疾风迅雷，唯快不破。",
            "tree_feilian", new() {
                Sk("feilian_1", "神速连斩", "以超越视觉的速度连续斩击单体12次", "Wind", "Ultimate", 78),
                Sk("feilian_2", "疾风步", "瞬移至敌人身后发动背刺，必定暴击", "Wind", "Active", 62),
                Sk("feilian_3", "风之残影", "闪避后留下残影，残影爆炸对周围造成伤害", "Wind", "Passive", 55),
            });
        // 商羊 Shangyang - 山海经"商羊"，漫威X教授+DC命运博士
        Add("char_ssr_shangyang", "商羊 Shangyang", "预知神鸟", "Aether", "Star", 3,
            new[] { 115, 100, 1080, 15 }, 6, false,
            "山海经商羊，一足鸟身，预知风雨。漫威X教授的精神力与DC命运博士的纳布神盔赋予其预知未来的能力，以星象指引命运。",
            "tree_shangyang", new() {
                Sk("shangyang_1", "星轨预言", "揭示敌方弱点，全队暴击率+30%持续6秒", "Star", "Ultimate", 76),
                Sk("shangyang_2", "预知闪避", "预判下一次攻击，必定闪避并反击", "Star", "Active", 60),
                Sk("shangyang3", "命运织网", "战斗开始时随机标记一名敌人，其受到伤害+20%", "Star", "Passive", 54),
            });
        // ========== SR 2★ ==========
        // 狻猊 Suanni - 山海经"狻猊"，漫威黑豹+DC蝙蝠侠
        Add("char_sr_suanni", "狻猊 Suanni", "狮吼震魂", "Shinwa", "Flame", 2,
            new[] { 95, 75, 900, 14 }, 5, false,
            "山海经狻猊，狮形豹步，食虎豹。漫威黑豹的振金战甲与DC蝙蝠侠的战术智慧在其血脉中传承，以狮吼震慑敌魂。",
            "tree_suanni", new() {
                Sk("suanni_1", "狮王怒吼", "狮吼震慑前方敌人，造成伤害并降低其攻击", "Flame", "Active", 55),
                Sk("suanni_2", "烈焰扑击", "扑向目标撕咬，造成单体高额伤害", "Flame", "Active", 50),
                Sk("suanni_3", "兽王威严", "生命低于40%时攻击+25%", "Flame", "Passive", 40),
            });
        // 精卫 Jingwei - 山海经"精卫"，漫威黑寡妇+DC猫女
        Add("char_sr_jingwei", "精卫 Jingwei", "衔石填海", "Aether", "Wind", 2,
            new[] { 88, 72, 850, 16 }, 5, false,
            "山海经精卫，炎帝之女溺于东海，化为神鸟衔石填海。漫威黑寡妇的坚韧与DC猫女的敏捷赋予其不屈意志，以柔克刚。",
            "tree_jingwei", new() {
                Sk("jingwei_1", "衔石连射", "连续发射碎石攻击单体，每次伤害递增", "Wind", "Active", 52),
                Sk("jingwei_2", "填海之志", "每回合结束时未死亡则回复5%最大生命", "Wind", "Passive", 42),
                Sk("jingwei_3", "风翼庇护", "闪避后制造风盾，吸收下一次伤害", "Wind", "Passive", 38),
            });
        // 穷奇 Qiongqi - 山海经"穷奇"，漫威死侍+DC丧钟
        Add("char_sr_qiongqi", "穷奇 Qiongqi", "噬罪凶兽", "Ironveil", "Metal", 2,
            new[] { 100, 88, 980, 11 }, 5, false,
            "山海经穷奇，状如牛蝟毛，性噬恶人。漫威死侍的再生与DC丧钟的精准射击在其机械兽躯中融合，以暴制暴。",
            "tree_qiongqi", new() {
                Sk("qiongqi_1", "噬罪撕咬", "撕咬单体造成真实伤害，无视防御", "Metal", "Active", 56),
                Sk("qiongqi_2", "凶兽再生", "每次击杀回复20%最大生命", "Metal", "Passive", 44),
                Sk("qiongqi_3", "蝟毛反击", "受到普攻时反弹30%伤害", "Metal", "Passive", 40),
            });
        // 旋龟 Xuanwu - 山海经"旋龟"，漫威钢力士+DC钢骨
        Add("char_sr_xuanwu", "旋龟 Xuanwu", "玄甲守护", "Shinwa", "Earth", 2,
            new[] { 92, 115, 1100, 9 }, 5, false,
            "山海经旋龟，鸟首虺尾，其音如判木。漫威钢力士的钢躯与DC钢骨的机械防护化为玄龟坚甲，以守为攻。",
            "tree_xuanwu", new() {
                Sk("xuanwu_1", "玄甲护体", "为全队施加护盾，吸收伤害持续6秒", "Earth", "Active", 54),
                Sk("xuanwu_2", "龟缩防御", "进入龟壳形态，减伤60%但无法攻击", "Earth", "Active", 46),
                Sk("xuanwu3", "大地之根", "站立不动3秒后每秒回复4%生命", "Earth", "Passive", 38),
            });
        // 毕方 Bifang - 山海经"毕方"，漫威猎鹰+DC鹰女
        Add("char_sr_bifang", "毕方 Bifang", "焚羽烈鸟", "Aether", "Thunder", 2,
            new[] { 90, 70, 820, 18 }, 5, false,
            "山海经毕方，一足鹤身，见则讹火。漫威猎鹰的翼装与DC鹰女的 N金属羽翼化为雷电之翼，所过之处雷火交加。",
            "tree_bifang", new() {
                Sk("bifang_1", "焚羽俯冲", "自高空俯冲，对路径上敌人造成雷电伤害", "Thunder", "Active", 53),
                Sk("bifang_2", "雷羽散射", "散射雷电羽毛攻击随机3个敌人", "Thunder", "Active", 48),
                Sk("bifang3", "闪电之翼", "每次闪避后下次攻击附加雷电伤害", "Thunder", "Passive", 36),
            });
        // ========== R 1★ ==========
        // 狸力 LiLi - 山海经"狸力"，漫威蚁人+DC原子侠
        Add("char_r_lili", "狸力 LiLi", "遁地灵兽", "Shinwa", "Earth", 1,
            new[] { 72, 65, 750, 12 }, 4, false,
            "山海经狸力，状如豚有距，其音如狗吠。漫威蚁人的缩放与DC原子侠的原子操控赋予其遁地穿土之能，身形虽小，来去无踪。",
            "tree_lili", new() {
                Sk("lili_1", "遁地突袭", "潜入地下后从敌人脚下突袭，必定暴击", "Earth", "Active", 40),
                Sk("lili_2", "土遁闪避", "受到攻击时有25%概率遁地闪避", "Earth", "Passive", 30),
                Sk("lili_3", "掘地之爪", "普攻附带破甲，降低目标10%防御", "Earth", "Passive", 25),
            });
        // 钦原 Qinyuan - 山海经"钦原"，漫威黄蜂女+DC黑金丝雀
        Add("char_r_qinyuan", "钦原 Qinyuan", "毒蜂刺羽", "Aether", "Metal", 1,
            new[] { 76, 60, 700, 15 }, 4, false,
            "山海经钦原，蛰鸟兽则死，蛰木则枯。黄蜂女的蜂群战衣与黑金丝雀的声波在其机械蜂翼中融合，以毒针刺穿敌阵。",
            "tree_qinyuan", new() {
                Sk("qinyuan_1", "毒蜂连射", "连续发射毒针攻击单体3次", "Metal", "Active", 42),
                Sk("qinyuan_2", "蜂毒侵蚀", "毒针附带中毒，每秒掉血持续4秒", "Metal", "Passive", 32),
                Sk("qinyuan_3", "蜂翼振翅", "攻击有20%概率额外攻击一次", "Metal", "Passive", 28),
            });
        // 商羊 SiShu - 山海经"蟋蟀/跂踵"，漫威蜘蛛侠+DC夜翼
        Add("char_r_sishu", "跂踵 SiShu", "夜行游侠", "Ironveil", "Shadow", 1,
            new[] { 74, 62, 720, 16 }, 4, false,
            "山海经跂踵，状如鹊而九尾，见则其国多疫。蜘蛛侠的蛛丝感应与DC夜翼的杂技格斗在其暗影战衣中觉醒，夜行无声。",
            "tree_sishu", new() {
                Sk("sishu_1", "蛛丝束缚", "发射蛛丝缠绕单体，使其无法行动2秒", "Shadow", "Active", 41),
                Sk("sishu_2", "蜘蛛感应", "受到攻击前摇时自动闪避", "Shadow", "Passive", 30),
                Sk("sishu_3", "暗影打击", "从暗处攻击额外造成50%伤害", "Shadow", "Passive", 26),
            });
        // 蠃鱼 Luoyu - 山海经"蠃鱼"，漫威海王纳摩+DC水行侠
        Add("char_r_luoyu", "蠃鱼 Luoyu", "渊海游灵", "Shinwa", "Frost", 1,
            new[] { 70, 68, 780, 13 }, 4, false,
            "山海经蠃鱼，鱼身鸟翼，音如鸳鸯。漫威纳摩的深海之力与DC水行侠的亚特兰蒂斯之能在其鳞翼中流淌，御水而行。",
            "tree_luoyu", new() {
                Sk("luoyu_1", "寒流冲击", "喷射寒流造成伤害并减速目标30%", "Frost", "Active", 44),
                Sk("luoyu_2", "鳞甲水护", "受到攻击时生成水盾吸收伤害", "Frost", "Passive", 33),
                Sk("luoyu_3", "深渊低语", "生命低于50%时技能冷却-20%", "Frost", "Passive", 27),
            });
        // 当康 Dangang - 山海经"当康"，漫威野兽+DC火星猎人
        Add("char_r_dangang", "当康 Dangang", "丰穗瑞兽", "Aether", "Wind", 1,
            new[] { 78, 72, 820, 11 }, 4, false,
            "山海经当康，状如豚而有牙，其鸣自叫，见则天下大穰。漫威野兽的蛮力与DC火星猎人的兽性在其瑞兽之躯中苏醒，以丰收之名为战。",
            "tree_dangang", new() {
                Sk("dangang_1", "丰穗冲撞", "蓄力冲撞单体，造成伤害并击退", "Wind", "Active", 43),
                Sk("dangang_2", "瑞兽庇佑", "战斗开始时为全队施加5%生命护盾", "Wind", "Passive", 31),
                Sk("dangang_3", "丰收之愈", "每次击杀回复10%最大生命", "Wind", "Passive", 29),
            });

        // ========== 裂隙纪元新增角色 ==========
        // UR 金乌 Jinwu - Shinwa 太阳神鸟，与烛龙并肩对抗虚无
        Add("char_ur_jinwu", "金乌 Jinwu", "十日巡天", "Shinwa", "Flame", 4,
            new[] { 168, 92, 1220, 20 }, 7, true,
            "太阳的化身，山海经载其载日而行。裂隙纪元中，十日中的九日被虚无吞噬，仅剩金乌独自照耀神話残片。漫威太阳黑子的聚变之躯与DC火风暴的原子重构之力，在金乌羽翼中共鸣，使其每一次振翅都能点燃大气中的氧原子。",
            "tree_jinwu", new() {
                Sk("jinwu_1", "日轮天罚", "射出凝聚太阳核心的等离子箭矢，对单体造成巨额火焰伤害并灼烧周围敌人", "Flame", "Ultimate", 94),
                Sk("jinwu_2", "耀斑冲击", "释放太阳耀斑，对全体敌人造成火焰伤害并附加致盲", "Flame", "Active", 72),
                Sk("jinwu_3", "不灭烈日", "生命低于30%时进入烈日形态，攻击与暴击大幅提升", "Flame", "Passive", 68),
            });
        // UR 女娲 Nuwa - Aether 创世女神，浮岛守护者
        Add("char_ur_nuwa", "女娲 Nuwa", "泥塑苍天", "Aether", "Earth", 4,
            new[] { 150, 135, 1400, 16 }, 7, true,
            "创造人类的古神，以大地的五色石补天。裂隙纪元中，原初之环的碎片不断崩落，女娲以最后一块补天石为锚，在以太星空中托起一座浮岛，庇护流离的凡人。漫威凤凰女的生命念力与DC沼泽怪物的大地共鸣，使女娲能将泥土化为生命、将废墟重塑为壁垒。",
            "tree_nuwa", new() {
                Sk("nuwa_1", "五色补天", "以五色石重塑战场，为全队回复大量生命并清除负面状态", "Earth", "Ultimate", 88),
                Sk("nuwa_2", "泥塑众生", "召唤土灵协助战斗，土灵会嘲讽敌人并分担伤害", "Earth", "Active", 70),
                Sk("nuwa_3", "大地母神", "每回合结束时为生命最低的队友回复生命", "Earth", "Passive", 62),
            });
        // SSR 蚩尤 Chiyou - Ironveil 兵主魔神
        Add("char_ssr_chiyou", "蚩尤 Chiyou", "兵主魔神", "Ironveil", "Metal", 3,
            new[] { 135, 105, 1150, 15 }, 6, false,
            "上古战神，铜头铁额，八肱八趾。铁帷城邦在大崩解后挖掘出蚩尤残躯，以合金与能量核心将其复活，编入兵主军团。漫威绿巨人的无限愤怒与DC毁灭日的进化杀戮本能，在蚩尤体内形成永不熄灭的战意。",
            "tree_chiyou", new() {
                Sk("chiyou_1", "虎魄裂天", "挥舞虎魄魔刀劈出金属碎片风暴，对前方敌人造成范围伤害", "Metal", "Ultimate", 84),
                Sk("chiyou_2", "兵主狂血", "损失生命以换取攻击力提升，击杀敌人后回复生命", "Metal", "Active", 66),
                Sk("chiyou_3", "铜头铁额", "受到伤害时概率减免并反弹部分伤害", "Metal", "Passive", 58),
            });
        // SSR 白虎 Baihu - Shinwa 西方圣兽
        Add("char_ssr_baihu", "白虎 Baihu", "西方圣兽", "Shinwa", "Metal", 3,
            new[] { 132, 88, 1020, 22 }, 6, false,
            "四象之一，主杀伐与西方。在神話残片，白虎沉睡了千年，直到裂隙中的金属风暴撕裂山林，它才睁开金色的兽瞳。漫威黑豹的振金战甲与DC猫女的优雅致命，在白虎身上化为兼具力量与速度的金属圣兽。",
            "tree_baihu", new() {
                Sk("baihu_1", "西方白虎杀", "化作银色残影连续斩击单体，无视部分防御", "Metal", "Ultimate", 82),
                Sk("baihu_2", "金风破甲", "虎爪撕裂目标护甲，使其受到物理伤害增加", "Metal", "Active", 64),
                Sk("baihu_3", "圣兽之威", "对生命低于30%的敌人伤害提升", "Metal", "Passive", 56),
            });
        // SR 花妖 Huayao - Aether 千瓣灵魅
        Add("char_sr_huayao", "花妖 Huayao", "千瓣灵魅", "Aether", "Wind", 2,
            new[] { 85, 78, 860, 18 }, 5, false,
            "原是 Aether 浮空花园中一株千年灵植，因裂隙能量涌入而化形。漫威暴风女的大气操控与DC毒藤女的植物共鸣，使她可以呼唤风携带花瓣形成治愈或剧毒领域。",
            "tree_huayao", new() {
                Sk("huayao_1", "千瓣愈风", "召唤花瓣之风为全队回复生命并提升速度", "Wind", "Active", 52),
                Sk("huayao_2", "毒藤缠绕", "用毒藤束缚单体敌人，造成持续伤害并降低其攻击", "Wind", "Active", 48),
                Sk("huayao_3", "花语轻喃", "受到致命伤害时化为花瓣规避一次（每场一次）", "Wind", "Passive", 40),
            });
        // SR 饕餮 Taotie - Shinwa 贪食凶兽
        Add("char_sr_taotie", "饕餮 Taotie", "贪食无厌", "Shinwa", "Flame", 2,
            new[] { 98, 120, 1050, 10 }, 5, false,
            "山海经中的贪食凶兽，有首无身，永不餍足。裂隙纪元中，饕餮被神話阵营封印于青铜巨鼎内，只在最危急的战局中被放出。漫威毒液的吞噬渴望与DC所罗门·格兰迪的无穷饥饿，使饕餮能吞噬敌人的攻击并转化为自身烈焰。",
            "tree_taotie", new() {
                Sk("taotie_1", "贪食天地", "吞噬前方敌人，造成火焰伤害并回复自身生命", "Flame", "Active", 54),
                Sk("taotie_2", "青铜业火", "喷出青铜色烈焰，对全体敌人造成灼烧", "Flame", "Active", 50),
                Sk("taotie_3", "永不餍足", "受到伤害时概率将部分伤害转化为生命", "Flame", "Passive", 42),
            });
        // R 山魈 Shanxiao - Ironveil 机械林精
        Add("char_r_shanxiao", "山魈 Shanxiao", "机械林精", "Ironveil", "Earth", 1,
            new[] { 75, 68, 740, 17 }, 4, false,
            "本是山林小鬼，大崩解时被铁帷的机械风暴卷入工厂废墟，身体与废弃机械融合。漫威火箭浣熊的机械天赋与DC野兽小子的野性本能，让它成为能在钢铁丛林中快速穿行的小个子战士。",
            "tree_shanxiao", new() {
                Sk("shanxiao_1", "零件陷阱", "布置机械陷阱，触发时造成伤害并眩晕", "Earth", "Active", 40),
                Sk("shanxiao_2", "废土闪避", "受到攻击时概率遁入废墟闪避", "Earth", "Passive", 32),
                Sk("shanxiao_3", "拆解专家", "对机械敌人伤害提升", "Earth", "Passive", 28),
            });
        // R 夜叉 Yecha - Aether 裂隙低语
        Add("char_r_yecha", "夜叉 Yecha", "裂隙低语", "Aether", "Shadow", 1,
            new[] { 78, 58, 680, 19 }, 4, false,
            "Aether 裂隙中最常见的低等虚空生物，由迷失者的影子凝聚而成。漫威夜魔侠的感官增强与DC暗影侠的黑暗潜行，使夜叉能在阴影中无声移动，用低语瓦解敌人意志。",
            "tree_yecha", new() {
                Sk("yecha_1", "影袭", "从阴影中突袭单体，造成暗影伤害", "Shadow", "Active", 42),
                Sk("yecha_2", "恐惧低语", "降低单个敌人攻击并使其有概率混乱", "Shadow", "Active", 36),
                Sk("yecha_3", "群影战术", "场上每存在一个夜叉，自身伤害提升", "Shadow", "Passive", 26),
            });

        EnrichCharacters();
    }

    private void EnrichCharacters()
    {
        var stories = new System.Collections.Generic.Dictionary<string, string>
        {
            ["char_ur_zhulong"] = "烛龙是 Shinwa 的最高图腾之一，被视为昼夜的化身。金乌视其为兄长与竞争对手——前者代表光明的节律，后者代表光明的强度。当虚无逼近时，烛龙主动睁眼超过七日，以白昼之力压制裂隙扩张，却也导致神話世界河流干涸、草木焦枯。",
            ["char_ur_wuxu"] = "虚无并非传统意义上的邪恶，它只是在执行一种宇宙规律：一切存在终将归于无。以太议会曾试图封印它，却反而让它学会了人类的恐惧与野心。",
            ["char_ur_xingtian"] = "刑天是铁帷兵主军团的第一代实验体，也是最稳定的一个。他与蚩尤并称双璧，但刑天更忠诚于保护普通民众，而非铁帷高层。他胸口的能量核心会随情绪变亮，愤怒时如烈日。",
            ["char_ur_kikyo"] = "裂隙纪元中，桔梗从神話残片的幽冥边界苏醒，发现四魂之玉的力量与裂隙能量同源。她开始猎杀被裂隙污染的亡灵，也逐渐理解：自己的复活本身可能就是一次裂隙实验。她与女娲有某种精神共鸣——两者都与泥土/陶土重生有关。",
            ["char_ur_keqing"] = "裂隙纪元中，刻晴是被 Aether 议会召唤的异界行者。她的到来让以太学者首次确信：裂隙连接的不仅是世界，还有不同的可能性。她与烛龙有过激烈争论：神明是否还应被敬畏？刻晴的答案是被研究，被超越。",
            ["char_ssr_fenghuang"] = "凤凰是 Shinwa 的祥瑞象征，也是金乌的眷属。当虚无吞噬九日，凤凰主动承担守护剩余光明种子的使命。她的每一次涅槃都会在空中留下新的星座，被 Aether 学者称为凤凰座。",
            ["char_ssr_xiangliu"] = "相柳是虚无的先驱者之一，却并非其仆从。它享受毁灭本身，与饕餮形成毒与焰的毁灭同盟。",
            ["char_ssr_leishen"] = "雷神是铁帷城邦的能量核心守护者，负责维持城市运转。他的机械躯体不断吸收裂隙中的电能，变得越来越强大，也越来越不稳定。他尊敬刑天，却嫉妒蚩尤——因为后者被允许释放全力，而他必须时刻控制功率。",
            ["char_ssr_feilian"] = "飞廉是神話的信使与斥候，速度让他能穿越未稳定的裂隙。他与花妖在 Aether 浮岛相识，一个是疾风，一个是轻风。",
            ["char_ssr_shangyang"] = "商羊是 Aether 议会首席预言者，它预见了虚无与女娲的最终对决，却无法确定结局。这让它既痛苦又着迷。",
            ["char_sr_suanni"] = "狻猊是 Shinwa 的守护者，也是白虎的远亲后辈。它梦想有一天能像白虎一样独当一面。",
            ["char_sr_jingwei"] = "精卫对裂隙造成的海洋污染深恶痛绝，她相信哪怕世界破碎，也能一粒一粒补回来。她与女娲因修补的理念而成为忘年之交。",
            ["char_sr_qiongqi"] = "穷奇是铁帷雇佣兵，只接惩恶的任务。它认为自己的残暴是正义的必需品。",
            ["char_sr_xuanwu"] = "旋龟是女娲浮岛的守护者，它的背上驮着一座微型神庙。",
            ["char_sr_bifang"] = "毕方崇拜凤凰，梦想成为下一位涅槃者。",
            ["char_r_lili"] = "狸力是神話阵营最好的地下情报员，能钻进任何缝隙。",
            ["char_r_qinyuan"] = "钦原是铁帷的小型无人机原型机，后来产生了自我意识。",
            ["char_r_sishu"] = "跂踵是 Ironveil 贫民窟的义警，只在夜间行动。",
            ["char_r_luoyu"] = "蠃鱼守护着被裂隙污染的海洋，与精卫一起清理废墟。",
            ["char_r_dangang"] = "当康是神話农民的守护神，战争爆发后才拿起武器。",
            ["char_ur_jinwu"] = "金乌与烛龙既是盟友也是镜像：烛龙司昼夜轮转，金乌司光明本身。当虚无逼近，金乌化作第二轮烈日，与烛龙并肩对抗终焉。",
            ["char_ur_nuwa"] = "她是虚无的反面：虚无吞噬存在，女娲创造存在。她与桔梗因泥土/陶土重生而精神共鸣，与精卫因修补理念成为忘年之交。",
            ["char_ssr_chiyou"] = "蚩尤与刑天并称铁帷双璧：刑天是不死的盾牌，蚩尤是毁灭的长矛。他的复活并不完美，时常在战斗中听见远古战鼓，分不清自己是被唤醒的神明，还是被操控的武器。",
            ["char_ssr_baihu"] = "白虎与狻猊有同族之谊：狻猊司震慑，白虎司裁决。它孤傲寡言，对邪恶绝不姑息，对伙伴却有隐秘的温柔。",
            ["char_sr_huayao"] = "花妖性格天真，却对生死有超越人类的理解。她认为花开花落与战争胜负一样，都是自然的呼吸。与飞廉相识于浮岛，一个是疾风，一个是轻风。",
            ["char_sr_taotie"] = "它与相柳并称两害：相柳以毒泽腐蚀大地，饕餮以贪焰吞噬一切。贪婪而直率，只要喂饱它，它会意外地忠诚。",
            ["char_r_shanxiao"] = "山魈喜欢用废旧零件布置陷阱，对体型巨大的敌人尤其兴奋。调皮、话痨、记仇，但关键时刻会为了保护同伴拼命。",
            ["char_r_yecha"] = "它们数量庞大、单体不强，但成群出现时能让整支军队陷入恐惧。阴郁、顺从、群体意识强，单独时怯懦，成群时残忍。",
        };

        var voices = new System.Collections.Generic.Dictionary<string, string[]>
        {
            ["char_ur_zhulong"] = new[] { "睁眼为昼，闭眼为夜——汝所见之光，皆由我裁断。", "天火燎原，不过是吐息之间。", "星辰会记得，是谁燃尽了它们。" },
            ["char_ur_wuxu"] = new[] { "万物终将归零，包括你的挣扎。", "存在本身，就是需要被修正的错误。", "听，维度在哭泣。" },
            ["char_ur_xingtian"] = new[] { "头颅可断，战意不灭。", "干戚在手，何惧神魔？", "每一次倒下，都只是进化的前奏。" },
            ["char_ur_kikyo"] = new[] { "四魂之玉的光芒，我守过一次，不会再让它熄灭。", "亡者的执念，也能成为破魔的箭。", "这世间，从不缺需要超度的灵魂。" },
            ["char_ur_keqing"] = new[] { "人类的命运，当由人类自己书写。", "雷霆不快，只是你跟不上我的剑。", "玉衡星的位置，我自己来争。" },
            ["char_ssr_fenghuang"] = new[] { "灰烬不是终点，是新生的温床。", "每一次坠落，都是为了更炽烈地燃烧。", "听，凤凰座在为我歌唱。" },
            ["char_ssr_xiangliu"] = new[] { "毒泽之上，连神明都不敢涉足。", "九个脑袋，九种杀你的方式。", "来，尝尝这杯瘟疫。" },
            ["char_ssr_leishen"] = new[] { "功率满格，审判开始。", "雷霆不会审判，它只是执行。", "我的核心在发烫——你最好躲远点。" },
            ["char_ssr_feilian"] = new[] { "风从不回头，我也是。", "等你看见我的时候，已经来不及了。", "fastest alive? 我只是懒得争辩。" },
            ["char_ssr_shangyang"] = new[] { "未来不是一条线，是无数裂隙交织的网。", "我看见你的结局了……但它还能改。", "星象从不撒谎，只会被误读。" },
            ["char_sr_suanni"] = new[] { "狮吼之下，敌魂皆碎。", "守护这件事，我从没打算交给别人。", "下次，换我当前锋。" },
            ["char_sr_jingwei"] = new[] { "一粒一粒，大海终会被填平。", "世界碎了多少，我就补多少。", "东海欠我的，我会一笔一笔讨回来。" },
            ["char_sr_qiongqi"] = new[] { "恶人？我最喜欢恶人了，嚼起来有嚼劲。", "正义需要牙齿，而我刚好有一口好牙。", "以暴制暴虽然老套，但好用。" },
            ["char_sr_xuanwu"] = new[] { "站到我身后。", "玄甲不破，尔等无忧。", "守，也是一种进攻。" },
            ["char_sr_bifang"] = new[] { "一足足以踏破苍穹。", "雷火交加，才是毕方的舞步。", "凤凰前辈看我的眼神，我很受用。" },
            ["char_r_lili"] = new[] { "小有小的好处，比如钻到你脚底下。", "遁地不是逃，是找角度。", "别看我小，挖洞我可是专业的。" },
            ["char_r_qinyuan"] = new[] { "嗡嗡——找到目标。", "一针就够，别浪费。", "蜂群从不多话，它们只行动。" },
            ["char_r_sishu"] = new[] { "夜晚是我的，你也是我的。", "蛛丝感应不会骗我，但人会。", "杂技不是表演，是杀人方式。" },
            ["char_r_luoyu"] = new[] { "深海之下，可没人听过你的祈祷。", "水是温柔的，除非我在里面。", "亚特兰蒂斯的回声，是我故乡的歌。" },
            ["char_r_dangang"] = new[] { "当康当康，丰收在望！", "瑞兽也要上战场？没办法，世界不太平。", "吃饱了，才有力气打架嘛。" },
            ["char_ur_jinwu"] = new[] { "十日虽陨，我一盏足矣。", "焚尽夜空的，从来不是恐惧，是光。", "记住这温度，它叫黎明。" },
            ["char_ur_nuwa"] = new[] { "天塌了，我再补一次。", "泥土记得所有生命最初的形状。", "你们活下来了，这就够了。" },
            ["char_ssr_chiyou"] = new[] { "兵主在此，谁敢接刀？", "虎魄饮血，越战越狂。", "这才是战场该有的味道。" },
            ["char_ssr_baihu"] = new[] { "西方的风，只吹向该死之人。", "这一爪，替你送行。", "下一个。" },
            ["char_sr_huayao"] = new[] { "风会把花瓣送到该去的地方。", "疼的话，就闻闻花香。", "看，伤口开花了。" },
            ["char_sr_taotie"] = new[] { "饿了。你们都别跑。", "这个，我吃了。", "还没饱……但你们先凑合。" },
            ["char_r_shanxiao"] = new[] { "嘿，大个子，脚下有东西哦。", "送你个小礼物——boom！", "捡破烂也能赢，气不气？" },
            ["char_r_yecha"] = new[] { "……影子来了。", "嘘，别回头。", "黑暗记得你。" },
        };

        var weaponVfx = new System.Collections.Generic.Dictionary<string, string>
        {
            ["char_ur_zhulong"] = "sun_orb_flame", ["char_ur_wuxu"] = "void_rift_blade", ["char_ur_xingtian"] = "gear_axe_storm",
            ["char_ur_kikyo"] = "shadow_bow_arrow", ["char_ur_keqing"] = "lightning_dual_swords", ["char_ssr_fenghuang"] = "phoenix_wing_flame",
            ["char_ssr_xiangliu"] = "venom_fang_whip", ["char_ssr_leishen"] = "mjolnir_hammer_arc", ["char_ssr_feilian"] = "wind_blade_dash",
            ["char_ssr_shangyang"] = "star_oracle_sigil", ["char_sr_suanni"] = "roar_shock_claw", ["char_sr_jingwei"] = "wind_stone_projectile",
            ["char_sr_qiongqi"] = "regen_blast_cannon", ["char_sr_xuanwu"] = "shell_barrier_earth", ["char_sr_bifang"] = "thunder_feather_dive",
            ["char_r_lili"] = "earth_burrow_strike", ["char_r_qinyuan"] = "poison_stinger_swarm", ["char_r_sishu"] = "shadow_wire_tangle",
            ["char_r_luoyu"] = "water_trident_surge", ["char_r_dangang"] = "tusk_charge_wind", ["char_ur_jinwu"] = "solar_orb_bow",
            ["char_ur_nuwa"] = "five_color_stone_staff", ["char_ssr_chiyou"] = "tiger_soul_cleaver", ["char_ssr_baihu"] = "vibranium_tiger_claw",
            ["char_sr_huayao"] = "petal_ribbon_blade", ["char_sr_taotie"] = "bronze_greed_flame", ["char_r_shanxiao"] = "scrap_claw_mine",
            ["char_r_yecha"] = "shadow_dagger_whisper",
        };

        var ambientVfx = new System.Collections.Generic.Dictionary<string, string>
        {
            ["char_ur_zhulong"] = "day_night_cycle_glow", ["char_ur_wuxu"] = "void_devour_particles", ["char_ur_xingtian"] = "scrap_storm_ironveil",
            ["char_ur_kikyo"] = "soul_petal_drift", ["char_ur_keqing"] = "thundercloud_city", ["char_ssr_fenghuang"] = "ember_rebirth_field",
            ["char_ssr_xiangliu"] = "poison_marsh_fog", ["char_ssr_leishen"] = "electric_coil_core", ["char_ssr_feilian"] = "wind_tunnel_speedlines",
            ["char_ssr_shangyang"] = "constellation_guidance", ["char_sr_suanni"] = "pride_aura_flame", ["char_sr_jingwei"] = "sea_stone_mist",
            ["char_sr_qiongqi"] = "blood_metal_smoke", ["char_sr_xuanwu"] = "jade_shield_earth", ["char_sr_bifang"] = "storm_feather_spark",
            ["char_r_lili"] = "dust_earth_burst", ["char_r_qinyuan"] = "mechanical_bee_cloud", ["char_r_sishu"] = "night_city_shadows",
            ["char_r_luoyu"] = "underwater_bubble_light", ["char_r_dangang"] = "harvest_wind_leaves", ["char_ur_jinwu"] = "broken_suns_inferno",
            ["char_ur_nuwa"] = "floating_island_aurora", ["char_ssr_chiyou"] = "ruin_battlefield_banners", ["char_ssr_baihu"] = "metal_storm_wasteland",
            ["char_sr_huayao"] = "floating_garden_petals", ["char_sr_taotie"] = "broken_bronze_ash", ["char_r_shanxiao"] = "ruin_jungle_parts",
            ["char_r_yecha"] = "rift_shadow_motes",
        };

        // UR 专属特色武器（依据各角色背景故事设计）
        var weaponName = new System.Collections.Generic.Dictionary<string, string>
        {
            ["char_ur_zhulong"] = "阖辟神瞳·昼夜轮",
            ["char_ur_wuxu"] = "归墟之噬·无相刃",
            ["char_ur_xingtian"] = "干戚·不灭齿轮",
            ["char_ur_kikyo"] = "破魔灵弓·封魂",
            ["char_ur_keqing"] = "雷楔双剑·云来",
            ["char_ur_jinwu"] = "曜日神弓·金乌",
            ["char_ur_nuwa"] = "五色补天杖",
            // SSR 专属特色武器（与 UR 平行，依据背景故事设计）
            ["char_ssr_fenghuang"] = "焚羽·涅槃翼",
            ["char_ssr_xiangliu"] = "九首·毒牙鞭",
            ["char_ssr_leishen"] = "轰霆·楔石锤",
            ["char_ssr_feilian"] = "裂空·疾风刃",
            ["char_ssr_shangyang"] = "卜天·星谶盘",
            ["char_ssr_chiyou"] = "虎魄·裂魂斧",
            ["char_ssr_baihu"] = "庚金·虎啸爪",
        };
        var weaponDesc = new System.Collections.Generic.Dictionary<string, string>
        {
            ["char_ur_zhulong"] = "由烛龙本瞳炼化的神环，半面熔金烈焰为昼、半面吞噬星辰的幽暗为夜。阖则白昼降临，睁则天火倾泻；环心藏有一只永不闭合的竖瞳，凝视之处，昼夜颠倒。",
            ["char_ur_wuxu"] = "以维度裂隙坍缩而成的无相之刃，无柄无锷，刃身即是被吞噬的星空。所触之物先从概念上归于「无」，再于现实里湮灭；连光也在刃前折返，寻不到落点。",
            ["char_ur_xingtian"] = "干为盾、戚为斧，铁帷齿轮驱动的复合兵装。虽无首，胸口的能量之眼代行目视，巨斧横扫如折麦秆；齿轮咬合间，每一次挥落都更接近「不死」的极致。",
            ["char_ur_kikyo"] = "守玉巫女的灵力凝成的破魔之弓，箭无虚发，穿邪祟、封怨念。每支箭都是一段未竟的祈祷，离弦时带着幽冥回响；弓身缠着四魂之玉的碎片，净化之同时封印着她的执念。",
            ["char_ur_keqing"] = "一对以雷楔为锷的双剑，剑身流转璃月雷纹。她掷出雷楔标记敌身，便化雷瞬至，于目标身后落下必中雷击——这套脱胎于「云来剑法」的打法，是她以凡人之躯比肩神明的证明。",
            ["char_ur_jinwu"] = "金乌以羽翼为弦、以烈日为箭的神弓。拉弦时大气中的氧被点燃，射出的并非凡箭，而是凝缩的恒星之火；九日已陨，此弓是唯一永不西沉的天。",
            ["char_ur_nuwa"] = "杖首嵌着最后一块五色补天石，流转青赤白黑黄五光。女娲以此杖为锚，于以太星空托起浮岛，点泥成生、化墟为壁；杖落处，破碎的世界重新有了形状。",
            // SSR 专属特色武器描述
            ["char_ssr_fenghuang"] = "由凤凰尾羽燃尽前凝成的双翼兵装，每一片翎羽都是一段未冷的火。持之可驭焚风、唤来灰烬中的重生；翼展千度，所过之处，焦土亦生新芽。",
            ["char_ssr_xiangliu"] = "相柳九首所化的一柄九节毒鞭，鞭身蠕动如活物，节节皆是一张饕餮之口。挥之则毒雾漫野、腐水成渊；被其缠住者，连神魂都被缓缓嚼碎。",
            ["char_ssr_leishen"] = "雷神以天楔为柄、以雷云为锤头的巨锤，落点之处风云倒卷、雷音贯耳。一击可令山川震颤、万雷归宗；锤风所至，邪祟无所遁形。",
            ["char_ssr_feilian"] = "飞廉驭风之术凝成的双刃，刃过无声，唯余一道被割裂的气流。疾如奔雷、轻若鸿毛；挥斩之间，风墙成壁、裂空成路。",
            ["char_ssr_shangyang"] = "商羊以星轨为纹、以卜辞为灵的占盘，盘面流转着未卜先知的微光。转动之间，可窥天命一角、引星辉为刃；所照之处，未来如掌中纹路般清晰。",
            ["char_ssr_chiyou"] = "蚩尤以败者之魂铸入斧刃的凶兵，斧面隐现虎魄之纹，饮血则啸。挥之如猛虎扑食、裂石断金；每一道斧光，都镌着上古战场的硝烟。",
            ["char_ssr_baihu"] = "西方庚金之气凝成的虎爪兵装，爪尖流转肃杀白芒，撕裂之处寒铁亦如腐木。白虎振爪则风雪俱寂、万兽伏首；一扑之威，可镇一方秋杀。",
        };

        foreach (var c in Characters)
        {
            if (string.IsNullOrEmpty(c.Faction)) c.Faction = c.World;
            if (string.IsNullOrEmpty(c.Story) && stories.TryGetValue(c.CharacterId, out var s)) c.Story = s;
            if (c.Voices == null || c.Voices.Count == 0) c.Voices = new List<string>(voices.GetValueOrDefault(c.CharacterId, new[] { "……" }));
            if (string.IsNullOrEmpty(c.WeaponVfx) && weaponVfx.TryGetValue(c.CharacterId, out var w)) c.WeaponVfx = w;
            if (string.IsNullOrEmpty(c.AmbientVfx) && ambientVfx.TryGetValue(c.CharacterId, out var a)) c.AmbientVfx = a;
            if (string.IsNullOrEmpty(c.Weapon) && weaponName.TryGetValue(c.CharacterId, out var wn)) c.Weapon = wn;
            if (string.IsNullOrEmpty(c.WeaponDesc) && weaponDesc.TryGetValue(c.CharacterId, out var wd)) c.WeaponDesc = wd;
        }
    }
}
