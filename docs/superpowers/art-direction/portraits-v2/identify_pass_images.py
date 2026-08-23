#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
identify_pass_images.py — 用主色调/亮度特征推断 out/ 中 11 张 PASS 图的角色身份。
背景透明，只统计 alpha>16 的前景像素。
预期色板来自各角色元素/设计稿关键词（近似 RGB）。
"""
import json
import os
import sys
from PIL import Image
import numpy as np

sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")

# 按时间戳排序的 PASS 图
FILES = sorted(f for f in os.listdir(OUT) if f.startswith("Full_body") and f.endswith(".webp"))

# P0 顺序（prompts-export.json 出现顺序 = generate_free.py 加载顺序）
P0_ORDER = [
    "char_ur_zhulong", "char_ur_wuxu", "char_ur_xingtian", "char_ur_kikyo",
    "char_ur_keqing", "char_ur_jinwu", "char_ur_nuwa",
    "char_ssr_fenghuang", "char_ssr_xiangliu", "char_ssr_leishen", "char_sr_suanni",
]

# 预期主导色板（RGB 近似，取自设计稿锚点）
EXPECTED = {
    "char_ur_zhulong":   ("赤金/熔岩橙红", (200, 90, 40)),
    "char_ur_wuxu":      ("虚空深紫黑", (25, 15, 45)),
    "char_ur_xingtian":  ("枪灰+铜+熔红", (110, 85, 70)),
    "char_ur_kikyo":     ("白上衣+朱红袴", (180, 80, 80)),
    "char_ur_keqing":    ("紫电紫发", (126, 87, 194)),
    "char_ur_jinwu":     ("金橙白羽毛", (220, 150, 60)),
    "char_ur_nuwa":      ("青绿山河+五色", (90, 140, 120)),
    "char_ssr_fenghuang": ("五色羽(赤橙黄绿蓝)", (190, 100, 90)),
    "char_ssr_xiangliu": ("暗紫黑+绿荧光血管", (45, 35, 65)),
    "char_ssr_leishen":  ("机械灰蓝+闪电白", (130, 140, 170)),
    "char_sr_suanni":    ("金鬃+黑色振金甲+蓝披风", (160, 120, 50)),
}

def analyze(path):
    im = Image.open(path).convert("RGBA")
    arr = np.array(im)
    fg = arr[..., 3] > 16
    if fg.sum() == 0:
        return None
    px = arr[fg][..., :3].astype(np.float32)
    mean = px.mean(axis=0).astype(int)
    # 亮度分位：暗部占比（wuxu/xiangliu 应极高）
    lum = px @ np.array([0.299, 0.587, 0.114])
    dark_frac = float((lum < 60).mean())
    bright_frac = float((lum > 200).mean())
    # 色相分布粗判（HSV）
    hsv = np.array(Image.fromarray(arr[fg][:, :3].astype(np.uint8)).convert("HSV"))
    h, s, v = hsv[..., 0].astype(float), hsv[..., 1] / 255.0, hsv[..., 2] / 255.0
    sat_px = s * v  # 有效饱和度
    red_frac = float(((h < 20) | (h > 235))[(sat_px > 0.3)].mean()) if (sat_px > 0.3).sum() else 0.0
    return {
        "size": im.size, "mean_rgb": tuple(mean),
        "dark_frac": round(dark_frac, 2), "bright_frac": round(bright_frac, 2),
        "red_frac": round(red_frac, 2), "fg_ratio": round(fg.mean(), 2),
    }

def dist(a, b):
    return int(np.sqrt(sum((x - y) ** 2 for x, y in zip(a, b))))

print(f"{'#':<3} {'文件(时间戳尾)':<14} {'尺寸':<11} {'均色RGB':<16} {'暗%':<5} {'亮%':<5} {'红%':<5} {'前景%':<6} 推断")
print("-" * 100)
results = []
for i, f in enumerate(FILES):
    r = analyze(os.path.join(OUT, f))
    ts = f.split("T")[-1].replace(".webp", "")
    guess = P0_ORDER[i] if i < len(P0_ORDER) else "?"
    exp_name, exp_rgb = EXPECTED.get(guess, ("?", (0, 0, 0)))
    d = dist(r["mean_rgb"], exp_rgb)
    results.append((f, guess, d, r))
    print(f"{i+1:<3} {ts:<14} {str(r['size']):<11} {str(r['mean_rgb']):<16} "
          f"{r['dark_frac']:<5} {r['bright_frac']:<5} {r['red_frac']:<5} {r['fg_ratio']:<6} "
          f"{guess} (距预期色 {d})")

# 保存映射建议供人工复核
mapping = {f: g for f, g, _, _ in results}
with open(os.path.join(OUT, "identity-guess.json"), "w", encoding="utf-8") as fp:
    json.dump(mapping, fp, ensure_ascii=False, indent=2)
print("\n-> identity-guess.json 已保存（仅为时序推断，入库前必须人工视觉复核）")
