import json

P = r"C:\Users\Administrator\Downloads\work\milan\MilanKotlin\app\src\main\assets\data.json"

# 14 个 SR/R 角色补写 Weapon + WeaponDesc（与设计并存稿 §3.2 一致；additive，不改键名）
fill = {
    "char_sr_suanni": ("炎吼·焚音爪", "龙子狻猊好烟好坐，以火焰狮爪为兵，挥击迸发音波烈焰环，吼声所及烟焰俱燃。"),
    "char_sr_jingwei": ("衔石·逐浪弹", "精卫衔石填海之志凝为兵，风缕编成投石索，抛出晶石如浪，矢志不停。"),
    "char_sr_qiongqi": ("啮钢·噬魂炮", "穷奇食人之翼虎，以吞噬之钢锻造兽口炮，喷吐金属碎片，所噬之魂无所归。"),
    "char_sr_xuanwu": ("负岳·玄甲盾", "旋龟负岳而行，甲壳化为活体玄铁盾，山脊纹路流转，御敌如山镇。"),
    "char_sr_bifang": ("焚羽·惊雷翎", "毕方一足火鸟，取燃烧雷羽为镖，蓄雷而投，落处火起雷鸣。"),
    "char_sr_huayao": ("缠丝·落花刃", "花妖以花瓣缎带凝成旋转飞刃，落红成刃，缠缚斩切皆宜。"),
    "char_sr_taotie": ("噬纹·贪鼎", "饕餮贪食，青铜鼎口即其噬焰之口，所吞之物尽化青烟，永不满足。"),
    "char_r_lili": ("掘地·裂壤爪", "狸力掌掘如犁，土系掘地爪锄破壤而行，藏于地脉。"),
    "char_r_qinyuan": ("毒螫·群蜂针", "钦原毒蜂之躯，一簇剧毒蜂针离手则追魂，螫处溃烂。"),
    "char_r_sishu": ("缚影·缠魂丝", "跂踵招死之鸟，吐影丝缠缚魂魄，中招者如陷永夜。"),
    "char_r_luoyu": ("涌潮·三叉戟", "蠃鱼鱼鸟之形，水波三叉戟涌潮而出，戟身鱼纹流转。"),
    "char_r_dangang": ("獠突·冲岳牙", "当康瑞兽野猪，以獠牙为冲撞兵装，突进如山岳之倾。"),
    "char_r_shanxiao": ("拾荒·碎铁爪", "山魈山鬼，拾荒废铁拼成利爪，杂乱中藏凶性。"),
    "char_r_yecha": ("喑杀·影刃", "夜叉捷鬼，无声影刃出鞘无音，一闪取命。"),
}

d = json.load(open(P, encoding="utf-8"))
cnt = 0
for c in d["Characters"]:
    cid = c.get("CharacterId", "")
    if cid in fill:
        w, desc = fill[cid]
        c["Weapon"] = w
        c["WeaponDesc"] = desc
        cnt += 1
        print("filled", cid, "->", w)
json.dump(d, open(P, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print("TOTAL filled:", cnt)
