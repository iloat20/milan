"""31 张立绘纯数值体检（不含任何视觉判断）。

检查项（全部为可计算指标，不依赖人工/视觉）：
  A. alpha 通道存在性
  B. 文件体积（按稀有度阈值）
  C. 几何：opacity_ratio / height_frac / center_dx / foot_present
  D. 画布尺寸是否符合 v2 梯子（UR 1536x2304 / SSR 1280x1920 / SR 1024x1536 / R 832x1248）

输出：控制台表格 + qc_all.json + qc_all.csv
"""
import argparse
import csv
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

DRAWABLE = Path(r"C:\Users\Administrator\Downloads\work\milan\MilanKotlin\app\src\main\res\drawable-nodpi")

CANVAS = {
    "UR": (1536, 2304),
    "SSR": (1280, 1920),
    "SR": (1024, 1536),
    "R": (832, 1248),
}
SIZE_LIMITS = {
    "UR": (200_000, 2_500_000),
    "SSR": (150_000, 2_000_000),
    "SR": (120_000, 1_500_000),
    "R": (90_000, 1_200_000),
}
# 期望区间（源自 qc_portraits.py 的 C 项）
OPACITY_OK = (0.12, 0.55)
HEIGHT_OK = (0.70, 0.98)


def rarity_of(name: str) -> str:
    low = name.lower()
    for r in ("ur", "ssr", "sr", "r"):
        if f"char_{r}_" in low:
            return r.upper()
    return "R"


def check(path: Path) -> dict:
    name = path.name
    rar = rarity_of(name)
    im = Image.open(path)
    has_alpha = im.mode in ("RGBA", "LA") or "transparency" in im.info
    rgba = im.convert("RGBA")
    a = np.array(rgba.getchannel("A"))
    h, w = a.shape
    nbytes = path.stat().st_size

    opacity = float((a > 8).mean())
    ys, xs = np.where(a > 8)
    if len(xs):
        height_frac = float((ys.max() - ys.min()) / h)
        cx_off = float(((xs.min() + xs.max()) / 2 - w / 2) / w)
    else:
        height_frac, cx_off = 0.0, 0.0
    foot = float((a[int(h * 0.88):, :] > 8).mean())

    issues = []
    if not has_alpha:
        issues.append("FAIL[A] no alpha channel")
    lo, hi = SIZE_LIMITS[rar]
    if nbytes < lo:
        issues.append(f"WARN[B] bytes {nbytes} < {lo}")
    elif nbytes > hi:
        issues.append(f"WARN[B] bytes {nbytes} > {hi} (oversized)")
    exp = CANVAS[rar]
    if (w, h) != exp:
        issues.append(f"FAIL[D] canvas {w}x{h} != expected {exp[0]}x{exp[1]}")
    if opacity < OPACITY_OK[0]:
        issues.append(f"FAIL[C] opacity {opacity:.3f} < {OPACITY_OK[0]} (figure missing?)")
    elif opacity > OPACITY_OK[1]:
        issues.append(f"WARN[C] opacity {opacity:.3f} > {OPACITY_OK[1]} (possible bg residue)")
    if height_frac < HEIGHT_OK[0]:
        issues.append(f"FAIL[C] height_frac {height_frac:.3f} < {HEIGHT_OK[0]} (bust?)")
    if abs(cx_off) > 0.15:
        issues.append(f"FAIL[C] center_dx {cx_off:+.3f} > 0.15 (off-center)")
    if foot < 0.005:
        issues.append(f"WARN[C] foot_present {foot:.4f} (figure may not reach bottom)")

    return {
        "name": name,
        "rarity": rar,
        "canvas": f"{w}x{h}",
        "bytes": nbytes,
        "opacity": round(opacity, 3),
        "height_frac": round(height_frac, 3),
        "center_dx": round(cx_off, 3),
        "foot": round(foot, 3),
        "issues": issues,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=str(DRAWABLE))
    ap.add_argument("--out", default="qc_all")
    args = ap.parse_args()

    d = Path(args.dir)
    files = sorted(
        [p for p in d.glob("char_*.webp")],
        key=lambda p: (["UR", "SSR", "SR", "R"].index(rarity_of(p.name)), p.name),
    )
    rows = [check(p) for p in files]

    hdr = f"{'name':<28}{'rar':<5}{'canvas':<11}{'bytes':>9}{'opacity':>9}{'h_frac':>8}{'c_dx':>8}{'foot':>7}  issues"
    print(hdr)
    print("-" * len(hdr))
    for r in rows:
        print(
            f"{r['name']:<28}{r['rarity']:<5}{r['canvas']:<11}{r['bytes']:>9}{r['opacity']:>9.3f}"
            f"{r['height_frac']:>8.3f}{r['center_dx']:>8.3f}{r['foot']:>7.3f}  {'; '.join(r['issues'])}"
        )

    fails = [r for r in rows if any(i.startswith("FAIL") for i in r["issues"])]
    warns = [r for r in rows if not any(i.startswith("FAIL") for i in r["issues"]) and r["issues"]]
    print(f"\n总计 {len(rows)} 张 | FAIL {len(fails)} | WARN-only {len(warns)} | clean {len(rows)-len(fails)-len(warns)}")

    out_json = Path(args.out + ".json")
    out_csv = Path(args.out + ".csv")
    out_json.write_text(json.dumps(rows, indent=2, ensure_ascii=False), encoding="utf-8")
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        wcsv = csv.DictWriter(f, fieldnames=["name", "rarity", "canvas", "bytes", "opacity", "height_frac", "center_dx", "foot", "issues"])
        wcsv.writeheader()
        for r in rows:
            rr = dict(r)
            rr["issues"] = "; ".join(r["issues"])
            wcsv.writerow(rr)
    print(f"报告: {out_json} / {out_csv}")


if __name__ == "__main__":
    main()