import json

base = r"C:\Users\Administrator\Downloads\work\milan\docs\superpowers\art-direction\portraits-v2"
src = json.load(open(base + r"\prompts-export.json", encoding="utf-8"))
chars = src["characters"]
want = ["char_ssr_baihu", "char_ssr_feilian", "char_ssr_shangyang", "char_ssr_chiyou",
        "char_sr_bifang", "char_sr_huayao", "char_sr_jingwei", "char_sr_qiongqi",
        "char_sr_taotie", "char_sr_xuanwu",
        "char_r_dangang", "char_r_lili", "char_r_luoyu", "char_r_qinyuan",
        "char_r_shanxiao", "char_r_sishu", "char_r_yecha"]
sel = [c for c in chars if c["id"] in want]
print("selected", len(sel))
out = {"count": len(sel), "characters": []}
for c in sel:
    out["characters"].append({
        "id": c["id"], "rarity": c["rarity"], "name_cn": c["name_cn"],
        "pinyin": c["pinyin"], "canvas": c["canvas"], "aspect_ratio": c["aspect_ratio"],
        "target": c["id"], "full_prompt": c["full_prompt"],
        "negative_prompt": c["negative_prompt"], "anchors": c.get("anchors"),
    })
json.dump(out, open(base + r"\p1-prompts.json", "w", encoding="utf-8"),
          ensure_ascii=False, indent=1)
print("wrote p1-prompts.json")
for c in sel:
    print(" ", c["rarity"], c["id"], "canvas", c["canvas"])
