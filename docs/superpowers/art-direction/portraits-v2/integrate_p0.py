#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
integrate_p0.py — P0 立绘集成：重命名 + 缩放到 v2 画布 + 落盘到 final/。

输入：rembg 透明背景产物（out/） + p0-mapping.json（timestamp → canonical id）
输出：final/char_<rarity>_<pinyin>.webp（按 v2 画布梯子缩放，LANCZOS，透明背景）

缩放策略：
  v2 画布比例 0.6667（UR 1536x2304, SSR 1280x1920, SR 1024x1536, R 832x1248）
  生图源 832x1216（比例 0.6842）—— 直接缩放会拉伸。
  → fit-to-width（保持比例），透明画布居中粘贴（上下各 20-30px 透明边）。
  → 全身完整，比例正确，引擎按原始 Box 约束即可居中显示。

用法：
  python integrate_p0.py --in-dir ./out --mapping ./p0-mapping.json --out-dir ./final
"""
import argparse
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")


def parse_canvas(spec: str):
    w, h = spec.lower().split("x")
    return int(w), int(h)


def fit_and_paste(src_path: str, dst_path: str, tw: int, th: int):
    from PIL import Image

    im = Image.open(src_path).convert("RGBA")
    sw, sh = im.size
    # fit-to-width（保比例）；若 fit-to-height 更小则用 fit-to-height 居中
    s_w = tw / sw
    s_h = th / sh
    s = min(s_w, s_h)
    nw, nh = int(round(sw * s)), int(round(sh * s))
    resampled = im.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    ox = (tw - nw) // 2
    oy = (th - nh) // 2
    canvas.paste(resampled, (ox, oy), resampled)
    os.makedirs(os.path.dirname(dst_path) or ".", exist_ok=True)
    canvas.save(dst_path, "WEBP", lossless=True, method=6)
    return dst_path, (sw, sh), (nw, nh), (ox, oy)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in-dir", required=True, help="rembg 输出目录（透明背景 webp）")
    ap.add_argument("--mapping", required=True, help="p0-mapping.json")
    ap.add_argument("--out-dir", required=True, help="最终落盘目录")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    mapping = json.load(open(args.mapping, encoding="utf-8"))
    print(f"[integrate] {mapping['count']} P0 images: {args.in_dir} -> {args.out_dir}")
    for item in mapping["items"]:
        src_base = os.path.splitext(item["src"])[0]
        src = os.path.join(args.in_dir, src_base + ".webp")
        dst = os.path.join(args.out_dir, item["id"] + ".webp")
        tw, th = parse_canvas(item["canvas"])
        if not os.path.exists(src):
            print(f"  ✗ {item['id']:<22} MISSING {src}")
            continue
        if args.dry_run:
            print(f"  · {item['id']:<22} {tw}x{th}  <- {os.path.basename(src)}")
            continue
        out, src_size, fit_size, offset = fit_and_paste(src, dst, tw, th)
        size = os.path.getsize(out)
        print(f"  ✓ {item['id']:<22} {tw}x{th}  fit={fit_size} off={offset} src={src_size}  ({size/1024:.0f} KB)")
    print(f"[integrate] done -> {args.out_dir}")


if __name__ == "__main__":
    main()
