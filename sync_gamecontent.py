# -*- coding: utf-8 -*-
"""同步 GameContent.kt 兜底副本，使其与 data.json 新内容逐字段一致。

覆盖：displayName/title/lore/story/voices/weapon/weaponDesc/skills/faction + 设定注释清理。
"""
import json
import io
import re

DATA_JSON = "MilanKotlin/app/src/main/assets/data.json"
DATA_BAK = "MilanKotlin/app/src/main/assets/data.json.bak"
KT = "MilanKotlin/app/src/main/java/com/milan/game/services/GameContent.kt"


def kt_lit(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def main():
    old = json.load(io.open(DATA_BAK, encoding="utf-8"))
    new = json.load(io.open(DATA_JSON, encoding="utf-8"))
    old_by_id = {c["CharacterId"]: c for c in old["Characters"]}
    new_by_id = {c["CharacterId"]: c for c in new["Characters"]}

    kt = io.open(KT, encoding="utf-8").read()
    report = []

    def rep(old_s, new_s, tag):
        n = kt.count(old_s)
        if n == 0:
            report.append("!! 未匹配: " + tag + " -> " + old_s[:40])
            return kt
        return kt.replace(old_s, new_s)

    for cid, oc in old_by_id.items():
        nc = new_by_id[cid]
        for field, key in (("DisplayName", "name"), ("Title", "title"), ("Lore", "lore"),
                           ("Weapon", "weapon")):
            if oc[field] != nc[field]:
                kt = rep('"' + oc[field] + '"', '"' + nc[field] + '"', cid + "." + key)
        if oc["Story"] != nc["Story"]:
            kt = rep('"' + kt_lit(oc["Story"]) + '"', '"' + kt_lit(nc["Story"]) + '"', cid + ".story")
        if oc["WeaponDesc"] != nc["WeaponDesc"]:
            kt = rep('"' + kt_lit(oc["WeaponDesc"]) + '"', '"' + kt_lit(nc["WeaponDesc"]) + '"', cid + ".weaponDesc")
        if oc["Voices"] != nc["Voices"]:
            o = "listOf(" + ", ".join('"' + v + '"' for v in oc["Voices"]) + ")"
            n = "listOf(" + ", ".join('"' + v + '"' for v in nc["Voices"]) + ")"
            kt = rep(o, n, cid + ".voices")
        # Skills（按 SkillId 比对 name/desc）
        new_skills = {s["SkillId"]: s for s in nc["Skills"]}
        for so in oc["Skills"]:
            sn = new_skills.get(so["SkillId"])
            if not sn:
                continue
            if so["DisplayName"] != sn["DisplayName"]:
                kt = rep('"' + so["DisplayName"] + '"', '"' + sn["DisplayName"] + '"', cid + "." + so["SkillId"] + ".name")
            if so["Description"] != sn["Description"]:
                kt = rep('"' + so["Description"] + '"', '"' + sn["Description"] + '"', cid + "." + so["SkillId"] + ".desc")

    # 域外来客 faction：在 buildCharacters 的 return chars 前插入
    marker = "        return chars\n    }\n\n    // ------------------------------------------------------------------ talent trees"
    if marker in kt:
        insert = (
            "        // 域外来客阵营（2026-09-04 原创化）：跨三界但同属「域外」，对齐 data.json Faction\n"
            "        val outlanderIds = setOf(\n"
            '            "char_ur_kikyo", "char_ur_keqing", "char_ur_ironman", "char_ur_thor", "char_ur_strange",\n'
            "        )\n"
            "        chars.forEach { if (it.characterId in outlanderIds) it.faction = \"域外\" }\n"
            "        return chars\n    }\n\n    // ------------------------------------------------------------------ talent trees"
        )
        kt = kt.replace(marker, insert)
    else:
        report.append("!! 未找到 faction 插入标记")

    # 设定注释清理：21 个山海经角色删美漫能力引用
    kt = re.sub(r"，漫威.*$", "", kt, flags=re.M)
    kt = re.sub(r"\+漫威.*$", "", kt, flags=re.M)

    # 5 个 IP 角色注释精确改写
    comment_map = {
        "// 桔梗 Kikyo ——《犬夜叉》孤独的巫女，封印四魂之玉的破魔之弓": "// 青璃 Qingli —— 域外净世巫女",
        "// 刻晴 Keqing ——《原神》璃月七星之玉衡，坚信人可胜天的雷之剑士": "// 曜 Yao —— 域外雷部剑客",
        "// 钢铁侠 Iron Man —— 漫威托尼·斯塔克，裂隙坠入铁帷纪元，方舟反应堆×铁帷锻造共鸣": "// 公输玄 Gongshu Xuan —— 域外机巧偃师，坠入铁帷纪元",
        "// 托尔 Thor —— 漫威雷神，追猎裂隙恶魔时被放逐至神話天空，妙尔尼尔×山海雷兽共鸣": "// 苍霆 Cangting —— 域外雷神，追猎裂隙之兽时被放逐至神話天空",
        "// 奇异博士 Doctor Strange —— 漫威至尊法师，看穿米兰裂隙与多元宇宙裂缝同源": "// 玄微 Xuanwei —— 域外秘术师，看穿裂隙与多元宇宙裂缝同源",
        "// ========== 漫威联动 UR（2026-08-26 新增） ==========": "// ========== 域外来客 UR（2026-08-26 新增，2026-09-04 原创化） ==========",
        "// 漫威联动 UR 专属武器（与 data.json 同步）": "// 域外来客 UR 专属武器（与 data.json 同步）",
        "// 漫威联动 UR 专属武器描述（与 data.json 同步）": "// 域外来客 UR 专属武器描述（与 data.json 同步）",
    }
    for o, n in comment_map.items():
        kt = rep(o, n, "comment:" + o[:30])

    io.open(KT, "w", encoding="utf-8").write(kt)

    if report:
        print("=== 未匹配项（需人工核对）===")
        for r in report:
            print(r)
    else:
        print("全部字段同步成功，无未匹配项")


if __name__ == "__main__":
    main()
