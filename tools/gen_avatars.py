#!/usr/bin/env python3
"""从立绘批量裁切头像（2026-09-09）。

规范 portraits-v2：脸在画布高度约 12–28% 区间（顶部 8% 安全留白 + 头部 23%）。
头像取正方形：水平居中，垂直取画布上部 face_band，再放大到 256×256。

输出：drawable-nodpi/avatar_<characterId 去掉 char_ 前缀>.webp
     实际资源名 avatar_ur_zhulong.webp ← 对应 char_ur_zhulong.webp
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

SRC = Path(__file__).resolve().parents[1] / "MilanKotlin/app/src/main/res/drawable-nodpi"
OUT = SRC  # 同目录，avatar_ 前缀
SIZE = 256

# 脸部带：相对画布高的 [top, bottom]，按构图略有差异
# 全身/卡面构图下脸多在 8%–30%
FACE_TOP = 0.08
FACE_BOTTOM = 0.32


def crop_avatar(src: Path) -> Image.Image:
    im = Image.open(src).convert("RGBA")
    w, h = im.size
    top = int(h * FACE_TOP)
    bottom = int(h * FACE_BOTTOM)
    band_h = bottom - top
    # 正方形：以脸带高度为边，水平居中
    side = band_h
    cx = w // 2
    left = max(0, cx - side // 2)
    right = min(w, left + side)
    left = max(0, right - side)
    box = (left, top, right, top + side)
    face = im.crop(box)
    face = face.resize((SIZE, SIZE), Image.LANCZOS)
    return face


def main() -> int:
    portraits = sorted(SRC.glob("char_*.webp"))
    portraits = [p for p in portraits if not p.stem.endswith("_thumb")]
    if not portraits:
        print(f"no portraits in {SRC}", file=sys.stderr)
        return 1
    made = 0
    for p in portraits:
        # char_ur_zhulong → avatar_ur_zhulong
        out_name = "avatar_" + p.stem[len("char_") :] + ".webp"
        out = OUT / out_name
        face = crop_avatar(p)
        face.save(out, "WEBP", quality=90, method=6)
        made += 1
        print(f"{p.name} → {out_name}  {face.size}")
    print(f"done: {made} avatars")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
