using Android.Content;
using Milan.Domain.Gacha;
using Milan.Domain.Progression;
using Milan.Infrastructure.Save;
using System.Text.Json;

namespace Milan.Maui.Services;

public class GameService
{
    private readonly SaveManager _save;
    private readonly GachaEngine _gacha;
    private readonly ProgressionEngine _progression = new();
    private readonly Random _rng = new();

    public SaveData SaveData => _save.Current;
    public List<CharacterDataEntry> Characters { get; } = new();
    public List<GachaPoolDataEntry> Pools { get; } = new();
    public List<TalentTreeData> TalentTrees { get; } = new();

    public GameService()
    {
        _save = new SaveManager(new LocalSaveProvider("milan_save.json"));
        _save.Load();
        _gacha = new GachaEngine(_rng);
    }

    public async Task InitializeAsync(Context context)
    {
        try
        {
            using var stream = context.Assets.Open("data.json");
            using var reader = new StreamReader(stream);
            var json = await reader.ReadToEndAsync();
            var root = JsonSerializer.Deserialize<RootData>(json);
            if (root != null)
            {
                Characters.Clear(); Characters.AddRange(root.Characters);
                Pools.Clear(); Pools.AddRange(root.Pools);
                TalentTrees.Clear(); TalentTrees.AddRange(root.TalentTrees);
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"[Milan] data load failed: {ex.Message}");
            LoadFallback();
        }
    }

    public void LoadFallback()
    {
        BuildCharacters();
        BuildPools();
        BuildTalentTrees();
    }

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
        Add("ur_xingtian", "刑天 Xingtian", "不死战神", "Ironveil", "Metal", 4,
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
    }

    // ------------------------------------------------------------------ pools

    private void BuildPools()
    {
        Pools.Clear();
        GachaPoolEntry EntryFor(CharacterDataEntry c) => new()
        {
            CharacterId = c.CharacterId, RarityIndex = c.BaseRarity,
            Weight = c.BaseRarity == 4 ? 1 : c.BaseRarity == 3 ? 8 : c.BaseRarity == 2 ? 40 : 100
        };
        var all = Characters.Select(EntryFor).ToList();

        Pools.Add(new GachaPoolDataEntry
        {
            PoolId = "pool_main", DisplayName = "次元裂缝 · 常驻",
            RarityWeights = new[] { 400, 300, 200, 100 }, HardPity = 90,
            SingleCost = 160, TenCost = 1600, Entries = all
        });
        Pools.Add(new GachaPoolDataEntry
        {
            PoolId = "pool_flame", DisplayName = "业火轮盘 · UP",
            RarityWeights = new[] { 400, 300, 200, 100 }, HardPity = 80,
            SingleCost = 160, TenCost = 1600,
            Entries = Characters.Where(c => c.Element == "Flame" || c.BaseRarity >= 3).Select(EntryFor).ToList()
        });
    }

    // ------------------------------------------------------------------ talent trees

    private void BuildTalentTrees()
    {
        TalentTrees.Clear();
        foreach (var ch in Characters)
            TalentTrees.Add(BuildTree(ch));
    }

    private TalentTreeData BuildTree(CharacterDataEntry ch)
    {
        var branches = new List<string> { "branch_power", "branch_defense", "branch_utility" };
        var nodes = new List<TalentNodeData>
        {
            NewTalent(ch, "t1", "强攻", "branch_power", 1, "攻击力+10%"),
            NewTalent(ch, "t2", "破甲", "branch_power", 2, "无视敌方15%防御", "t1"),
            NewTalent(ch, "t3", "坚壁", "branch_defense", 1, "防御力+10%"),
            NewTalent(ch, "t4", "铁壁", "branch_defense", 2, "受到伤害-15%", "t3"),
            NewTalent(ch, "t5", "疾风步", "branch_utility", 1, "速度+8%"),
            NewTalent(ch, "t6", "灵动", "branch_utility", 2, "闪避率+10%", "t5"),
        };
        return new TalentTreeData { TreeId = ch.TalentTreeId, BranchIds = branches, Nodes = nodes };
    }

    private TalentNodeData NewTalent(CharacterDataEntry ch, string id, string name,
        string branch, int cost, string desc, string? prereq = null)
    {
        return new TalentNodeData
        {
            NodeId = ch.CharacterId + "_" + id,
            DisplayName = name,
            Description = desc,
            BranchId = branch,
            Cost = cost,
            PrerequisiteNodeIds = prereq != null ? new List<string> { ch.CharacterId + "_" + prereq } : new List<string>(),
            VisualLayerId = branch
        };
    }

    // ------------------------------------------------------------------ pull

    public List<PullResult> Pull(string poolId, bool tenPull)
    {
        var results = new List<PullResult>();
        var pool = Pools.FirstOrDefault(p => p.PoolId == poolId);
        if (pool == null) return results;

        int count = tenPull ? 10 : 1;
        int cost = tenPull ? pool.TenCost : pool.SingleCost;
        if (SaveData.SoftCurrency < cost) return results;
        SaveData.SoftCurrency -= cost;

        var pity = new PityCounter(pool.HardPity) { Counter = SaveData.GetGachaCounter(poolId) };
        for (int i = 0; i < count; i++)
        {
            var rarity = pity.RollWithPity(_rng, pool.RarityWeights, 3);
            var entries = GetEntriesForRarity(poolId, (int)rarity);
            string? id = PickFromEntries(entries);
            if (string.IsNullOrEmpty(id)) id = PickFromPool(pool);
            if (!string.IsNullOrEmpty(id))
            {
                bool isNew = !SaveData.OwnedCharacters.Exists(c => c.CharacterId == id);
                if (isNew) SaveData.OwnedCharacters.Add(new CharacterSaveState { CharacterId = id });
                var def = Characters.FirstOrDefault(c => c.CharacterId == id);
                results.Add(new PullResult
                {
                    Success = true, CharacterId = id,
                    CharacterName = def?.DisplayName ?? id,
                    Rarity = (int)rarity, IsNew = isNew
                });
            }
        }
        SaveData.SetGachaCounter(poolId, pity.Counter);
        _save.Save();
        return results;
    }

    private string? PickFromEntries(List<GachaPoolEntry> entries)
    {
        if (entries.Count == 0) return null;
        return _gacha.PickWeighted(entries.Select(e => e.CharacterId).ToArray(), entries.Select(e => e.Weight).ToArray());
    }

    private string? PickFromPool(GachaPoolDataEntry pool)
    {
        if (pool.Entries.Count == 0) return null;
        return _gacha.PickWeighted(pool.Entries.Select(e => e.CharacterId).ToArray(), pool.Entries.Select(e => e.Weight).ToArray());
    }

    private List<GachaPoolEntry> GetEntriesForRarity(string poolId, int rarity)
    {
        var pool = Pools.FirstOrDefault(p => p.PoolId == poolId);
        if (pool == null) return new List<GachaPoolEntry>();
        return pool.Entries.Where(e => e.RarityIndex == rarity).ToList();
    }
}

// --------------------------------------------------------------------- data models

public class CharacterDataEntry
{
    public string CharacterId = "";
    public string DisplayName = "";
    public string Title = "";
    public string World = "Shinwa";
    public string Element = "Flame";
    public int BaseRarity = 1;
    public int[] BaseStats = { 100, 80, 1000, 12 };
    public int MaxStage = 4;
    public int MaxStars = 5;
    public bool CanBreakthrough;
    public string Lore = "";
    public string TalentTreeId = "";
    public List<SkillData> Skills = new();
}

public class SkillData
{
    public string SkillId = "";
    public string DisplayName = "";
    public string Description = "";
    public string Element = "";
    public string Type = ""; // Active / Passive / Ultimate
    public int Power = 0;
}

public class GachaPoolEntry
{
    public string CharacterId = "";
    public int RarityIndex = 1;
    public int Weight = 100;
}

public class GachaPoolDataEntry
{
    public string PoolId = "";
    public string DisplayName = "";
    public int[] RarityWeights = { 400, 300, 200, 100 };
    public int HardPity = 90;
    public int SingleCost = 100;
    public int TenCost = 1000;
    public List<GachaPoolEntry> Entries = new();
}

public class PullResult
{
    public bool Success;
    public string? CharacterId;
    public string CharacterName = "";
    public int Rarity;
    public bool IsNew;
}

public class TalentNodeData
{
    public string NodeId = "";
    public string DisplayName = "";
    public string Description = "";
    public string BranchId = "";
    public int Cost = 1;
    public List<string> PrerequisiteNodeIds = new();
    public string VisualLayerId = "";
}

public class TalentTreeData
{
    public string TreeId = "";
    public List<string> BranchIds = new();
    public List<TalentNodeData> Nodes = new();
}

class RootData
{
    public List<CharacterDataEntry> Characters { get; set; } = new();
    public List<GachaPoolDataEntry> Pools { get; set; } = new();
    public List<TalentTreeData> TalentTrees { get; set; } = new();
}