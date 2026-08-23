#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
fix_checkerboard.py — 清除 AI 生图里被"画进去"的假透明棋盘格。
检测：中性灰双色（184±15 / 233±15）+ 指定区域掩码 + 连通域最小面积过滤，
命中像素置 alpha=0（真透明）。仅处理指定文件，原图备份为 *_orig.webp。
"""
import argparse
import os
import sys
import shutil
import numpy as np
from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

# (y0, y1, x0, x1) 伪影热区——来自全局扫描的 64px 网格聚类结果
REGIONS = {
    "Full_body_character_portrait_o_2026-08-13T03-40-39": [  # 凤凰
        (140, 420, 400, 640),
        (660, 980, 80, 360),
        (720, 1020, 460, 720),
    ],
}
GRAY_TARGETS = (184, 233)
TOL = 15
MIN_BLOB = 120      # 小于该面积的连通域保留（保护合法灰色高光）
DILATE = 2          # 膨胀半径，吃掉抗锯齿边缘


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image", help="待清理 webp/png")
    ap.add_argument("--out", required=True, help="输出路径")
    args = ap.parse_args()

    key = os.path.splitext(os.path.basename(args.image))[0]
    if key not in REGIONS:
        print(f"[error] 未定义 {key} 的伪影区域", file=sys.stderr)
        sys.exit(1)

    im = Image.open(args.image).convert("RGBA")
    a = np.array(im)
    h, w = a.shape[:2]

    rgb = a[..., :3].astype(int)
    color_hit = np.zeros((h, w), dtype=bool)
    for g in GRAY_TARGETS:
        ch = np.ones((h, w), dtype=bool)
        for c in range(3):
            ch &= np.abs(rgb[..., c] - g) < TOL
        color_hit |= ch

    inside = np.zeros((h, w), dtype=bool)
    for y0, y1, x0, x1 in REGIONS[key]:
        inside[max(0, y0):min(h, y1), max(0, x0):min(w, x1)] = True

    mask = color_hit & inside & (a[..., 3] > 100)
    print(f"[scan] 候选像素 {mask.sum()} ({mask.mean():.2%})")

    # 连通域过滤（4 邻接，纯 numpy BFS）
    from collections import deque
    lbl = np.zeros((h, w), dtype=np.int32)
    cur = 0
    for sy, sx in zip(*np.where(mask)):
        if lbl[sy, sx]:
            continue
        cur += 1
        q = deque([(sy, sx)])
        lbl[sy, sx] = cur
        blob = [(sy, sx)]
        while q:
            y, x = q.popleft()
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx_ = y + dy, x + dx
                if 0 <= ny < h and 0 <= nx_ < w and mask[ny, nx_] and not lbl[ny, nx_]:
                    lbl[ny, nx_] = cur
                    q.append((ny, nx_))
                    blob.append((ny, nx_))
        if len(blob) < MIN_BLOB:
            mask[lbl == cur] = False

    print(f"[scan] 过滤后命中 {mask.sum()} ({mask.mean():.2%})")

    # 膨胀吃掉边缘过渡带
    m = mask.copy()
    for _ in range(DILATE):
        grown = m.copy()
        grown[1:, :] |= m[:-1, :]; grown[:-1, :] |= m[1:, :]
        grown[:, 1:] |= m[:, :-1]; grown[:, :-1] |= m[:, 1:]
        m = grown
    a[m, 3] = 0

    Image.fromarray(a).save(args.out, "WEBP", quality=95, method=6)
    print(f"[done] 清除 {m.sum()} 像素 -> {args.out}")


if __name__ == "__main__":
    main()
