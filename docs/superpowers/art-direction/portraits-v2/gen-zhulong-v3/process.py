"""烛龙 v3 单文件处理：去背结果 → UR 画布 (1536x2304) → final webp + QC 报告。"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).parent
CUT, FINAL, WORK = ROOT / "cut", ROOT / "final", ROOT / "work"
FINAL.mkdir(parents=True, exist_ok=True)

UR = (1536, 2304)


def fit_and_save(src: Path, dst: Path, tw: int, th: int) -> None:
    im = Image.open(src).convert("RGBA")
    sw, sh = im.size
    s = min(tw / sw, th / sh)
    nw, nh = max(1, int(round(sw * s))), max(1, int(round(sh * s)))
    res = im.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    canvas.paste(res, ((tw - nw) // 2, (th - nh) // 2), res)
    canvas.save(dst, "WEBP", lossless=True, method=6)
    print(f"  {dst.name}  src={im.size} -> {canvas.size}  bytes={dst.stat().st_size}")


def qc_one(path: Path) -> dict:
    im = Image.open(path).convert("RGBA")
    a = np.array(im.getchannel("A"))
    rgb = np.array(im.convert("RGBA"))
    h, w = a.shape

    opacity = float((a > 8).mean())
    ys, xs = np.where(a > 8)
    foot = (a[int(h * 0.88):, :] > 8).mean()
    if len(xs):
        bbox = {
            "y0": int(ys.min()), "y1": int(ys.max()),
            "x0": int(xs.min()), "x1": int(xs.max()),
        }
        height_frac = (ys.max() - ys.min()) / h
        cx_off = ((xs.min() + xs.max()) / 2 - w / 2) / w
    else:
        bbox = None
        height_frac = 0
        cx_off = 0

    issues = []
    if path.stat().st_size < 200_000:
        issues.append("WARN[B-size] bytes too small (<200K)")
    if path.stat().st_size > 2_500_000:
        issues.append("WARN[B-size] bytes too large (>2.5M)")
    if opacity < 0.12:
        issues.append(f"FAIL[C] opacity_ratio {opacity:.3f} < 0.12 (figure too small)")
    elif opacity > 0.55:
        issues.append(f"WARN[C] opacity_ratio {opacity:.3f} > 0.55 (bg residue)")
    if height_frac < 0.70:
        issues.append(f"FAIL[C] height_frac {height_frac:.3f} < 0.70 (bust, not full body)")
    if abs(cx_off) > 0.15:
        issues.append(f"FAIL[C] center_dx {cx_off:+.3f} > 0.15 (off-center)")
    if foot < 0.005:
        issues.append(f"WARN[C] foot_present ratio {foot:.3f} (tail may not touch bottom)")

    return {
        "path": str(path),
        "size": [w, h],
        "bytes": path.stat().st_size,
        "opacity_ratio": round(opacity, 3),
        "height_frac": round(height_frac, 3),
        "center_dx": round(cx_off, 3),
        "foot_present": round(foot, 3),
        "bbox": bbox,
        "issues": issues,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True, help="cut/<filename>")
    ap.add_argument("--out-name", default="char_ur_zhulong.webp")
    args = ap.parse_args()

    src = Path(args.src)
    out = FINAL / args.out_name
    print("== integrate ==")
    fit_and_save(src, out, *UR)
    print("== qc ==")
    rep = qc_one(out)
    print(json.dumps(rep, indent=2, ensure_ascii=False))
    (WORK / "qc.json").write_text(json.dumps(rep, indent=2, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()