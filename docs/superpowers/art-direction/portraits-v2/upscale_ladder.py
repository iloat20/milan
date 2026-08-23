#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
upscale_ladder.py — 将立绘放大到 v2 画布阶梯（Lanczos + 仅 RGB 轻锐化，alpha 单独缩放）。
UR 1536×2304 / SSR 1280×1920 / SR 1024×1536
用法: python upscale_ladder.py --src out/ingest --dst out/upscaled
"""
import argparse
import os
import sys
from PIL import Image, ImageFilter

sys.stdout.reconfigure(encoding="utf-8")

LADDER = {"ur": (1536, 2304), "ssr": (1280, 1920), "sr": (1024, 1536), "r": (832, 1248)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--dst", required=True)
    args = ap.parse_args()
    os.makedirs(args.dst, exist_ok=True)

    for f in sorted(os.listdir(args.src)):
        if not f.lower().endswith(".webp"):
            continue
        rarity = f.split("_")[1].lower()          # char_ur_zhulong.webp -> ur
        tw, th = LADDER[rarity]
        path = os.path.join(args.src, f)
        im = Image.open(path).convert("RGBA")

        r, g, b, a = im.split()
        rgb = Image.merge("RGB", (r, g, b)).resize((tw, th), Image.LANCZOS)
        alpha = a.resize((tw, th), Image.LANCZOS)
        # 轻锐化补偿插值软化（只作用于 RGB）
        rgb = rgb.filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))
        out = Image.merge("RGBA", (*rgb.split(), alpha))

        dst_path = os.path.join(args.dst, f)
        out.save(dst_path, "WEBP", quality=90, method=6)
        print(f"[ok] {f:<28} {im.size} -> {out.size} {os.path.getsize(dst_path)//1024}KB")


if __name__ == "__main__":
    main()
