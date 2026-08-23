#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
bg_remove.py — 立绘透明背景自动化（rembg / SAM2 可插拔）。

把生图输出（可能带纯色/渐变背景）转为透明背景 PNG/WebP。命名严格遵循工程约定
char_<rarity小写>_<拼音>.webp，可直接落入 res/drawable-nodpi/ 被 PortraitLoader 加载。

Provider：
  rembg  默认 u2net；人形优先 isnet-general-use / u2net_human_seg。
         需：pip install rembg onnxruntime
  sam2   Segment Anything 2 自动掩码（取最大连通掩码为前景）。
         需：pip install segment-anything torch + 权重（--sam-checkpoint）

说明：
  - ImageGen 已支持 background=transparent，可直接 pass-through（仅转 WebP + 体积检查）；
    本脚本主要用于 Flux 等出不透明图的 API，或批量统一去背。
  - 透明背景是 v2 铁律（现状 0/28 → 目标 28/28），任何生图产物入 assets 前必须过本脚本。

用法：
  python bg_remove.py --in-dir ./gen --out-dir ./out --provider rembg --model isnet-general-use --fmt webp
  python bg_remove.py --in-dir ./gen --out-dir ./out --provider rembg --model u2net --alpha-matting
  python bg_remove.py --in-dir ./gen --out-dir ./out --provider sam2 --sam-checkpoint sam2_h.pt
  python bg_remove.py --check-only ./gen        # 仅报告哪些已透明，不处理
"""
import argparse
import os
import sys
from concurrent.futures import ThreadPoolExecutor

sys.stdout.reconfigure(encoding="utf-8")


def has_alpha(path: str) -> bool:
    from PIL import Image

    try:
        im = Image.open(path)
        return im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info)
    except Exception:
        return False


def remove_rembg(src, dst, sess, alpha_matting, fmt):
    from PIL import Image
    from rembg import remove

    im = Image.open(src).convert("RGBA")
    out = remove(im, session=sess, alpha_matting=alpha_matting)
    save(out, dst, fmt)
    return dst


def remove_sam2(src, dst, checkpoint, fmt):
    from PIL import Image
    import torch
    from sam2.build_sam import build_sam2
    from sam2.automatic_mask_generator import SAM2AutomaticMaskGenerator

    device = "cuda" if torch.cuda.is_available() else "cpu"
    sam2 = build_sam2("sam2_hiera_l.yaml", checkpoint, device=device)
    gen = SAM2AutomaticMaskGenerator(sam2)
    im = Image.open(src).convert("RGB")
    import numpy as np

    masks = gen.generate(np.array(im))
    # 取面积最大的掩码作为前景
    best = max(masks, key=lambda m: m["area"])["segmentation"]
    rgba = im.convert("RGBA")
    arr = np.array(rgba)
    arr[~best, 3] = 0
    out = Image.fromarray(arr, "RGBA")
    save(out, dst, fmt)
    return dst


def save(im, dst, fmt):
    os.makedirs(os.path.dirname(dst) or ".", exist_ok=True)
    if fmt == "webp":
        # WebP 支持 alpha；lossless 保边缘，质量仅影响有损部分
        im.save(dst, "WEBP", lossless=True, method=6)
    else:
        im.save(dst, "PNG")


def gather(in_dir):
    exts = (".png", ".jpg", ".jpeg", ".webp", ".bmp")
    return [os.path.join(in_dir, f) for f in sorted(os.listdir(in_dir)) if f.lower().endswith(exts)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in-dir", help="生图输出目录")
    ap.add_argument("--out-dir", help="去背后输出目录")
    ap.add_argument("--provider", default="rembg", choices=["rembg", "sam2"])
    ap.add_argument("--model", default="isnet-general-use", help="rembg 模型名")
    ap.add_argument("--alpha-matting", action="store_true", help="rembg 边缘细化（慢但更干净）")
    ap.add_argument("--sam-checkpoint", help="SAM2 权重路径")
    ap.add_argument("--fmt", default="webp", choices=["webp", "png"])
    ap.add_argument("--check-only", help="仅检查目录内已透明文件，不处理")
    ap.add_argument("--workers", type=int, default=4)
    args = ap.parse_args()

    if args.check_only:
        files = gather(args.check_only)
        for f in files:
            print(f"[{'OK' if has_alpha(f) else 'NO-ALPHA'}] {os.path.basename(f)}")
        return

    if not args.in_dir or not args.out_dir:
        print("[error] 需要 --in-dir 与 --out-dir", file=sys.stderr)
        sys.exit(1)

    files = gather(args.in_dir)
    print(f"[run] provider={args.provider} 处理 {len(files)} 张 -> {args.out_dir}")

    # 关键：session 只创建一次（在 pool 外）。rembg.download_models 不是线程安全，
    # 多 worker 并发 new_session 会争抢同一个 .onnx 文件触发 FileExistsError。
    sess = None
    if args.provider == "rembg":
        from rembg import new_session
        print(f"[run] loading rembg session (model={args.model}) ...", flush=True)
        sess = new_session(args.model) if args.model else new_session("u2net")
        print("[run] session ready", flush=True)

    def work(src):
        base = os.path.splitext(os.path.basename(src))[0]
        dst = os.path.join(args.out_dir, base + ("." + args.fmt))
        if os.path.exists(dst) and has_alpha(dst):
            return base, "skip(exists+alpha)"
        if args.provider == "rembg":
            remove_rembg(src, dst, sess, args.alpha_matting, args.fmt)
        else:
            remove_sam2(src, dst, args.sam_checkpoint, args.fmt)
        return base, "done" if has_alpha(dst) else "WARN:no-alpha"

    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        for base, status in ex.map(work, files):
            print(f"  {base}: {status}", flush=True)


if __name__ == "__main__":
    main()
