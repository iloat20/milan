#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
qc_portraits.py — 生图 QC 自动验证（锚点检测 + 通道检查 + 文件体积 + 几何）。

对生图/去背产物目录逐一检查，输出 JSON 报告 + 控制台 PASS/WARN/FAIL 汇总。

检查项：
  [A] alpha 通道     必须含透明通道（v2 铁律）。无 -> FAIL
  [B] 文件体积       按稀有度阈值（ur/ssr 大、sr/r 小）。过小=空洞，过大=未压缩 -> WARN/FAIL
  [C] 几何
        - opacity_ratio  非透明像素占比，期望 0.12~0.55（太低=角色过小，太高=背景未去净）
        - height_frac   前景 bbox 高度 / 画布高度，期望 0.70~0.98（全身入画；过小=半身）
        - center_dx     前景 bbox 中心相对画布中心的水平偏移，期望 |dx| <= 0.15
        - foot_present  画布底部 12% 区域存在非透明像素（脚落地）否则 WARN（可能半身）
  [D] 锚点清单        从 prompts-export.json 读该 id 的 3 锚点 + key_elements，
                      标注 [人工确认]（视觉特征无法机器判定，列出供核对）

依赖：pip install pillow numpy
输出：qc-report.json + 控制台汇总

用法：
  python qc_portraits.py --dir ./out --export prompts-export.json
  python qc_portraits.py --dir ./out --export prompts-export.json --strict   # 任一 FAIL 即非零退出
"""
import argparse
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

# 稀有度体积阈值（字节）下限/上限，按 res 实际收敛后微调
SIZE_LIMITS = {
    "UR": (200_000, 2_500_000),
    "SSR": (150_000, 2_000_000),
    "SR": (120_000, 1_500_000),
    "R": (90_000, 1_200_000),
}


def rarity_of(name: str):
    for r in ("ur", "ssr", "sr", "r"):
        if f"char_{r}_" in name.lower():
            return r.upper()
    return "R"


def load_export(path):
    if not path or not os.path.exists(path):
        return {}
    d = json.load(open(path, encoding="utf-8"))
    return {c["id"]: c for c in d.get("characters", [])}


def analyze_geometry(path):
    from PIL import Image
    import numpy as np

    im = Image.open(path).convert("RGBA")
    w, h = im.size
    a = np.array(im)[..., 3]
    total = w * h
    fg = a > 16  # 近不透明算前景
    opacity = float(fg.sum()) / total
    if fg.sum() == 0:
        return {"opacity_ratio": 0.0, "height_frac": 0.0, "center_dx": 1.0,
                "foot_present": False, "w": w, "h": h}
    ys, xs = np.where(fg)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    bw, bh = (x1 - x0 + 1), (y1 - y0 + 1)
    cx = (x0 + x1) / 2 / w - 0.5
    foot = bool(fg[int(h * 0.88):, :].sum() > 0)
    return {
        "opacity_ratio": round(opacity, 3),
        "height_frac": round(bh / h, 3),
        "center_dx": round(cx, 3),
        "foot_present": foot,
        "w": w, "h": h,
    }


def check_one(path, export_map, strict):
    name = os.path.basename(path)
    cid = os.path.splitext(name)[0]
    rarity = rarity_of(cid)
    size = os.path.getsize(path)
    lo, hi = SIZE_LIMITS.get(rarity, (90_000, 1_500_000))

    issues = []
    # [A] alpha
    try:
        from PIL import Image

        _im = Image.open(path)
        # 覆盖三种透明: RGBA/LA(真 alpha) + Palette+P-mode('transparency' 在 info) + RGB+tRNS(单色透明)
        has_a = _im.mode in ("RGBA", "LA") or ("transparency" in _im.info)
    except Exception:
        has_a = False
    if not has_a:
        issues.append("FAIL:no-alpha-channel")

    # [B] size
    if size < lo:
        issues.append(f"WARN:size-too-small({size}<{lo})")
    elif size > hi:
        issues.append(f"WARN:size-too-large({size}>{hi})")

    # [C] geometry
    geom = {}
    try:
        geom = analyze_geometry(path)
        if geom["opacity_ratio"] < 0.12:
            issues.append(f"WARN:opacity-low({geom['opacity_ratio']})")
        elif geom["opacity_ratio"] > 0.55:
            issues.append(f"WARN:opacity-high-bg?({geom['opacity_ratio']})")
        if geom["height_frac"] < 0.70:
            issues.append(f"WARN:half-body?({geom['height_frac']})")
        if abs(geom["center_dx"]) > 0.15:
            issues.append(f"WARN:off-center({geom['center_dx']})")
        if not geom.get("foot_present"):
            issues.append("WARN:no-foot(可能半身)")
    except Exception as e:
        issues.append(f"WARN:geom-error({e})")

    # [D] anchors (人工确认项)
    anchors = export_map.get(cid, {}).get("anchors", {})
    key_elems = export_map.get(cid, {}).get("prompt", {}).get("key_elements", [])

    level = "FAIL" if any(i.startswith("FAIL") for i in issues) else ("WARN" if issues else "PASS")
    return {
        "id": cid, "rarity": rarity, "size": size, "alpha": has_a,
        "geometry": geom, "issues": issues, "level": level,
        "anchors_human_check": anchors, "key_elements_human_check": key_elems,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True, help="生图/去背产物目录")
    ap.add_argument("--export", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "prompts-export.json"))
    ap.add_argument("--strict", action="store_true", help="任一 FAIL 即退出码 1")
    args = ap.parse_args()

    export_map = load_export(args.export)
    exts = (".png", ".webp")
    files = [os.path.join(args.dir, f) for f in sorted(os.listdir(args.dir)) if f.lower().endswith(exts)]
    if not files:
        print("[error] 目录无图片", file=sys.stderr)
        sys.exit(1)

    results = [check_one(f, export_map, args.strict) for f in files]
    out = {"dir": args.dir, "count": len(results),
           "summary": {lv: sum(1 for r in results if r["level"] == lv) for lv in ("PASS", "WARN", "FAIL")},
           "items": results}
    rep = os.path.join(args.dir, "qc-report.json")
    json.dump(out, open(rep, "w", encoding="utf-8"), ensure_ascii=False, indent=2)

    print(f"[qc] 共 {len(results)} 张  PASS={out['summary']['PASS']} WARN={out['summary']['WARN']} FAIL={out['summary']['FAIL']}")
    for r in results:
        flag = "✓" if r["level"] == "PASS" else ("!" if r["level"] == "WARN" else "✗")
        print(f"  {flag} {r['id']:<22} alpha={r['alpha']} size={r['size']:>9} {r['issues'] or 'ok'}")
        if r["anchors_human_check"]:
            print(f"      锚点[人工确认]: {list(r['anchors_human_check'].values())}")
    print(f"  -> {rep}")
    if args.strict and out["summary"]["FAIL"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
