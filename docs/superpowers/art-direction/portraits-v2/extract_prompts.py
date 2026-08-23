#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
extract_prompts.py — 把 portraits-v2 4 份角色设计稿解析为结构化生图导出文件。

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


def parse_file(path: str, rarity: str):
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
                "prompt": prompt,
                "negative_prompt": prompt["negative_prompt"],
                "full_prompt": prompt["full_prompt"],
            }
        )
    return characters


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=os.path.dirname(os.path.abspath(__file__)),
                    help="portraits-v2 目录")
    args = ap.parse_args()
    src = args.src

    all_chars = []
    for rarity, fname in RARITY_FILES.items():
        fpath = os.path.join(src, fname)
        if not os.path.exists(fpath):
            print(f"[warn] 缺失 {fpath}", file=sys.stderr)
            continue
        chars = parse_file(fpath, rarity)
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
