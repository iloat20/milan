#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
extract_prompts.py — 把 portraits-v2 4 份角色设计稿解析为结构化生图导出文件。

v2.1（2026-08）：导出时自动注入「卡牌立绘」构图/光影指令层并修订负面表。
v2.2（2026-08-23）：叙事优先——解禁完整脚部入镜；注入角色背景故事（data.json Lore）
作为最高优先级段，立绘与头像的一切视觉要素须可溯源到背景故事
（详见 06-card-art-composition.md；原角色稿内容不动）。

v2.3（2026-08-26）：全局写实化——注入 REALISM_DIRECTIVE（灵感：Marvel Snap 卡面工艺：
高饱和撞色、破格构图、物理材质渲染、小尺寸可读性），并清洗原稿赛璐璐措辞；
负面表追加 cel shading / flat anime coloring 等。

输出: portraits-v2/prompts-export.json
       portraits-v2/prompts-export.csv  (便于在表格/生图平台里批量粘贴)

每个角色提取:
  - id / rarity / name_cn / pinyin / title / world / element
  - anchors: {A,B,C} 识别锚点（铁律必含项）
  - canvas / aspect_ratio
  - prompt: 5 段式 {subject, pose, atmosphere, style, key_elements[]}
  - negative_prompt
  - full_prompt: 合并 5 段为单字符串（供 OpenAI / 单 prompt 生图 API 使用）

用法:
  python extract_prompts.py
  python extract_prompts.py --src <portraits-v2 目录>
"""
import argparse
import csv
import json
import os
import re
import sys
from datetime import datetime, timezone

sys.stdout.reconfigure(encoding="utf-8")

RARITY_FILES = {
    "UR": "01-character-portrait-UR.md",
    "SSR": "02-character-portrait-SSR.md",
    "SR": "03-character-portrait-SR.md",
    "R": "04-character-portrait-R.md",
}

# 画布阶梯（与 00-master-spec.md §1.1 对齐）
CANVAS = {"UR": "1536x2304", "SSR": "1280x1920", "SR": "1024x1536", "R": "832x1248"}

# ── v2.2 卡牌立绘注入层（单一事实来源：06-card-art-composition.md）──
# 目标：卡面插画范式 + 背景故事最高优先级。景别不再硬性规定——叙事优先，
# 全身（脚部可入镜）与腰上皆可，选最能讲出该角色故事的构图。

CARD_DIRECTIVE = (
    "CARD ART COMPOSITION (v2.2): dynamic card-game illustration. "
    "COMPOSITION SERVES THE STORY: choose full-body (feet may be visible) OR waist-up framing — "
    "whichever best tells this character's backstory; "
    "subject fills 75-85% of frame height either way. "
    "Slight 5-degree low-angle hero shot, three-quarter view preferred, "
    "at least one diagonal energy line (weapon trail / cape / hair / tail flow), "
    "weapon or hand may subtly break the frame edge, "
    "head large and readable at thumbnail size, face never cropped by frame, "
    "core identity anchors kept inside the central 60% safe zone. "
)

CARD_LIGHTING = (
    "LIGHTING: single strong key light from upper-left with sharp shadow terminators, "
    "deep volumetric shadows shaping the figure, "
    "strong rim light in the character's element color from back-left separating silhouette from background, "
    "crisp specular highlights on metal and wet surfaces, soft diffuse on skin and cloth, "
    "natural light falloff toward frame edges so the figure reads inside a card frame. "
)

# ── v2.3 全局写实化（单一事实来源：06-card-art-composition.md §写实化）──
# 灵感溯源（2026-08 互联网调研，Marvel Snap 卡面工艺）：
#   ① 高饱和撞色 + 小屏强对比可读（插画师 Alberto Dal Lago 访谈：卡面极小，
#      场景必须清晰、忌灰暗，强对比与鲜艳配色是硬要求）；
#   ② framebreak 破格 + 分层思维便于卡面 3D 动效（Trent Kaniuga 出图流程拆解）；
#   ③ 物理材质区分渲染：金属镜面反射环境 / 皮肤次表面散射 / 织物纹理；
#   ④ 色彩情绪学：背景主色呼应角色元素色（Cosmic Ghost Rider 案例的星云绿呼应金属反光）。
# 写实 ≠ 灰暗：饱和度是卡面生命线。
REALISM_STYLE = (
    "REALISM DIRECTIVE (v2.3, highest style priority, overrides any anime/stylization wording below): "
    "photorealistic-painterly card illustration, true human anatomy and proportions, "
    "realistic skin texture with pores and subsurface scattering, physically-based material rendering "
    "(brushed metal reflects environment, leather scuffs, fabric weave visible, hair strands catch rim light), "
    "cinematic movie-poster depth of field, faces stay expressive and recognizable at thumbnail size. "
    "Keep saturated high-contrast palette — realism never means washed-out gray; "
    "silhouette must read clearly at 96px avatar size. "
)

# 稀有度光效档（追加在指令块尾部，对齐 00-master-spec §4.2）
RARITY_LIGHT_TIER = {
    "UR": "UR tier: epic molten-gold god-ray accents, ultra-fine detail density, subtle holographic sheen on armor.",
    "SSR": "SSR tier: violet twilight glow accents, rich saturated palette, arcane particle motes.",
    "SR": "SR tier: clean heroic lighting, balanced contrast, confident readability.",
    "R": "R tier: soft clean lighting, approachable and bright, gentle contrast.",
}

# 负面表（v2.2）：脚部已解禁；新增「脸被画框裁切」禁令保障安全区铁律
# v2.3 追加写实化禁令：禁赛璐璐/平涂动漫/粗描边/塑料皮肤
CARD_NEGATIVE = (
    "tiny distant figure, wide establishing shot, head cropped by frame, "
    "flat even lighting, washed-out low contrast, static symmetrical pose, "
    "cel shading, flat anime coloring, thick black outlines, plastic doll skin, "
)
_CONFLICTING_NEG_TOKENS = re.compile(r"(half-body|bust|cropped)\s*,?\s*", re.I)

# 原稿正文清洗：仅替换开头句为卡面插画口径（feet/8-head 清洗已随 v2.2 解禁移除）
# v2.3 追加：原稿赛璐璐/半写实措辞让位于写实指令层（残留的动漫字样由
# REALISM_STYLE 的 overrides 声明兜底，不做全文激进替换以免误伤锚点描述）
_BODY_SCRUB = [
    (re.compile(r"Full-body character portrait of"), "Card-game illustration of"),
    (re.compile(r"full-body character portrait of"), "card-game illustration of"),
    (re.compile(r"cel-shaded anime with Chinese ink-wash outlines", re.I),
     "photorealistic-painterly rendering with subtle Chinese ink-wash accents"),
    (re.compile(r"Semi-realistic facial detail", re.I), "fully realistic facial detail"),
]

# 背景故事符合性段（v2.2 最高优先级：置于 full_prompt 最前）
_LORE_TEMPLATE = (
    "LORE FIDELITY (HIGHEST PRIORITY): every visible element must be traceable to this character's "
    "backstory. Backstory (Chinese, translate its meaning): \"{lore}\" "
    "The expression and gaze must convey the personality described there — this face will also be used "
    "as the avatar thumbnail, so the emotion must read clearly. Costume, props, pose and light effects "
    "need narrative justification from that story; power resonances mentioned in it must become "
    "VISIBLE dual-source motifs, not generic glow."
)


def apply_card_art_directive(prompt: dict, rarity: str, lore: str | None = None) -> dict:
    """把 v2.3 指令层套到单个角色的 prompt 结构上：
    [LORE 保真段（若有）] → [写实化+卡牌构图+光影+稀有度档] → [清洗后的原稿正文]；
    负面表统一重写。原角色稿 md 文件不改动。"""
    parts: list[str] = []
    if lore:
        parts.append(_LORE_TEMPLATE.format(lore=" ".join(lore.split())))
    parts.append(
        REALISM_STYLE + CARD_DIRECTIVE + CARD_LIGHTING
        + RARITY_LIGHT_TIER.get(rarity.upper(), "")
    )
    body = prompt["full_prompt"]
    for pat, repl in _BODY_SCRUB:
        body = pat.sub(repl, body)
    parts.append(body)
    prompt["full_prompt"] = "\n\n".join(parts)
    neg = _CONFLICTING_NEG_TOKENS.sub("", prompt.get("negative_prompt", ""))
    prompt["negative_prompt"] = CARD_NEGATIVE + neg
    return prompt


def gcd(a, b):
    while b:
        a, b = b, a % b
    return a


def aspect(canvas: str) -> str:
    w, h = (int(x) for x in canvas.lower().split("x"))
    g = gcd(w, h)
    return f"{w // g}:{h // g}"


def file_canvas(text: str, rarity: str) -> str:
    """从文件头部 '> 画布：**1536×2304 px**' 提取，缺省回退到阶梯表。"""
    m = re.search(r"画布[:：]\s*\*\*([\d×xX]+)", text)
    if m:
        return m.group(1).replace("×", "x")
    return CANVAS[rarity]


def parse_heading(line: str):
    """'烛龙 Zhulong · 昼夜之主 · Shinwa · Flame' -> 各字段"""
    parts = [p.strip() for p in line.split("·")]
    if len(parts) < 4:
        return {}, line
    name_part = parts[0]
    tokens = name_part.split()
    name_cn = tokens[0] if tokens else name_part
    pinyin = tokens[1] if len(tokens) > 1 else ""
    return {
        "name_cn": name_cn,
        "pinyin": pinyin,
        "title": parts[1],
        "world": parts[2],
        "element": parts[3],
    }, line


def parse_anchors(block: str):
    """识别锚点 ×3（必含项）段: - **A 神话原型**: 文本"""
    anchors = {}
    for m in re.finditer(r"^\s*-\s*\*\*([ABC])\s*([^*]+?)\*\*:\s*(.+)$", block, re.M):
        key, label, desc = m.group(1), m.group(2).strip(), m.group(3).strip()
        anchors[key] = f"{label}: {desc}"
    return anchors


def parse_prompt_block(block: str):
    """提取 ``` 代码块，兼容两种写法：
       A) UR 式: 含 [1. 主体]...[5. 关键元素] 段标记
       B) SSR/SR/R 式: 纯段落 + key_elements: 列表 + negative_prompt:
    统一输出 5 段（缺失段留空）+ key_elements[] + negative_prompt + full_prompt。
    """
    cb = re.search(r"```[^\n]*\n(.*?)```", block, re.S)
    if not cb:
        return None
    raw = cb.group(1).strip()

    # key_elements 列表（两种格式都含 'key_elements:' 行）
    ke_section = re.search(r"key_elements:\s*\n(.*?)(?=\nnegative_prompt:|\Z)", raw, re.S)
    key_elements = []
    if ke_section:
        for km in re.finditer(r"-\s*([A-Za-z0-9_]+)\s*\(", ke_section.group(1)):
            key_elements.append(km.group(1))

    neg = re.search(r"negative_prompt:\s*\"(.*?)\"", raw, re.S)
    negative_prompt = neg.group(1).strip() if neg else ""

    has_segments = bool(re.search(r"\[\d\.\s*(主体|姿态|氛围|风格)", raw))
    if has_segments:
        def seg(name):
            m = re.search(rf"\[{name}\]\s*(.*?)(?=\n\[|\nnegative_prompt:|\Z)", raw, re.S)
            return m.group(1).strip() if m else ""

        subject = seg(r"1\.\s*主体")
        pose = seg(r"2\.\s*姿态")
        atmosphere = seg(r"3\.\s*氛围")
        style = seg(r"4\.\s*风格")
        full_parts = [subject, pose, atmosphere, style]
        if key_elements:
            full_parts.append("Key elements: " + ", ".join(key_elements) + ".")
        full_prompt = "\n\n".join(p for p in full_parts if p)
    else:
        # 格式 B：key_elements 之前的全部正文即完整生图 prompt
        body = raw.split("key_elements:", 1)[0].strip()
        paragraphs = [p.strip() for p in re.split(r"\n\s*\n", body) if p.strip()]
        subject = paragraphs[0] if paragraphs else body
        style = paragraphs[-1] if len(paragraphs) > 1 else ""
        pose = ""
        atmosphere = ""
        full_prompt = body

    return {
        "subject": subject,
        "pose": pose,
        "atmosphere": atmosphere,
        "style": style,
        "key_elements": key_elements,
        "negative_prompt": negative_prompt,
        "full_prompt": full_prompt,
    }


def parse_file(path: str, rarity: str, lore_map: dict | None = None):
    text = open(path, encoding="utf-8").read()
    canvas = file_canvas(text, rarity)
    asp = aspect(canvas)
    # 按 '## N. ' 切分角色块（保留 N. 之后的内容）
    chunks = re.split(r"(?m)^##\s+\d+\.\s+", text)
    characters = []
    for chunk in chunks[1:]:
        first_line = chunk.split("\n", 1)[0].strip()
        if not first_line:
            continue
        meta, _ = parse_heading(first_line)
        if not meta:
            continue
        idm = re.search(r"\*\*ID\*\*:\s*`([^`]+)`", chunk)
        cid = idm.group(1) if idm else f"char_{rarity.lower()}_{meta.get('pinyin','').lower()}"
        anchors = parse_anchors(chunk)
        prompt = parse_prompt_block(chunk)
        if not prompt:
            continue
        # v2.2 卡牌化注入（06-card-art-composition.md；原稿内容不动）
        lore = (lore_map or {}).get(cid)
        prompt = apply_card_art_directive(prompt, rarity, lore)
        characters.append(
            {
                "id": cid,
                "rarity": rarity,
                "name_cn": meta.get("name_cn", ""),
                "pinyin": meta.get("pinyin", ""),
                "title": meta.get("title", ""),
                "world": meta.get("world", ""),
                "element": meta.get("element", ""),
                "canvas": canvas,
                "aspect_ratio": asp,
                "anchors": anchors,
                "lore": lore or "",
                "prompt": prompt,
                "negative_prompt": prompt["negative_prompt"],
                "full_prompt": prompt["full_prompt"],
            }
        )
    return characters


def default_lore_json(src: str) -> str:
    """自动探测仓库内 data.json（portraits-v2 向上 4 级为仓库根）。不存在返回空串。"""
    cand = os.path.normpath(
        os.path.join(src, "..", "..", "..", "..",
                     "MilanKotlin", "app", "src", "main", "assets", "data.json")
    )
    return cand if os.path.exists(cand) else ""


def load_lore_map(path: str) -> dict:
    """data.json → {CharacterId: Lore}（v2.2 背景故事权威源，与运行时详情页同文）。"""
    data = json.load(open(path, encoding="utf-8"))
    return {c.get("CharacterId", ""): (c.get("Lore") or "").strip()
            for c in data.get("Characters", [])}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=os.path.dirname(os.path.abspath(__file__)),
                    help="portraits-v2 目录")
    ap.add_argument("--lore-json", default="",
                    help="data.json 路径（默认自动探测仓库内 MilanKotlin assets；传 'none' 禁用 Lore 注入）")
    args = ap.parse_args()
    src = args.src

    lore_path = args.lore_json
    if lore_path.lower() == "none":
        lore_path = ""
    elif not lore_path:
        lore_path = default_lore_json(src)

    lore_map: dict = {}
    if lore_path:
        lore_map = load_lore_map(lore_path)
        print(f"[lore] 注入源 {lore_path}（{len(lore_map)} 条）")
    else:
        print("[warn] 未找到 data.json——跳过 LORE FIDELITY 注入（--lore-json 可手动指定）", file=sys.stderr)

    all_chars = []
    for rarity, fname in RARITY_FILES.items():
        fpath = os.path.join(src, fname)
        if not os.path.exists(fpath):
            print(f"[warn] 缺失 {fpath}", file=sys.stderr)
            continue
        chars = parse_file(fpath, rarity, lore_map)
        missing = [c["id"] for c in chars if not c["lore"]]
        if lore_map and missing:
            print(f"[warn] {rarity}: 无 Lore 的角色 {'、'.join(missing)}（已跳过 LORE 段）")
        print(f"[ok] {rarity}: 解析 {len(chars)} 个角色 -> {fname}")
        all_chars.extend(chars)

    out = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": "docs/superpowers/art-direction/portraits-v2",
        "canvas_ladder": CANVAS,
        "count": len(all_chars),
        "characters": all_chars,
    }

    json_path = os.path.join(src, "prompts-export.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)

    # CSV（便于批量粘贴到生图平台 / 表格）
    csv_path = os.path.join(src, "prompts-export.csv")
    with open(csv_path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "rarity", "name_cn", "pinyin", "title", "world", "element",
                    "canvas", "aspect_ratio", "anchor_A", "anchor_B", "anchor_C",
                    "key_elements", "full_prompt", "negative_prompt"])
        for c in all_chars:
            a = c.get("anchors", {})
            w.writerow([
                c["id"], c["rarity"], c["name_cn"], c["pinyin"], c["title"],
                c["world"], c["element"], c["canvas"], c["aspect_ratio"],
                a.get("A", ""), a.get("B", ""), a.get("C", ""),
                "; ".join(c["prompt"]["key_elements"]),
                c["full_prompt"], c["negative_prompt"],
            ])

    print(f"\n[done] 共 {len(all_chars)} 个角色")
    print(f"  -> {json_path}")
    print(f"  -> {csv_path}")


if __name__ == "__main__":
    main()
