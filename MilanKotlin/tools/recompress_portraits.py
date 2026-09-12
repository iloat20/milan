"""Re-encode portraits from masters with quality-first settings.

Aggressive 2026-09-11 pass (≤400KB / maxH=1024 / q≤72) destroyed soft
atmosphere and mid-alpha background layers. Current tiers:
- UR: master resolution (cap 1920), prefer lossless WebP
- SR/SSR/R: maxH=1248, high quality (~900KB target)
"""
from __future__ import annotations

from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/res/drawable-nodpi"
MASTER = ROOT / "tools/backup/portraits-original"
MAX_H = 1248
TARGET_BYTES = 900 * 1024
QUALITIES = (90, 86, 82, 78)
METHOD = 6
# UR 档：典藏立绘质量优先（2026-09-11 用户反馈）。母本分辨率、优先无损。
UR_MAX_H = 1920
UR_LOSSLESS_BUDGET = 2200 * 1024
UR_QUALITIES = (95, 92, 90)
UR_JPEG_LIKE_BUDGET = 1600 * 1024


def encode(im: Image.Image, *, ur: bool) -> bytes:
    if ur:
        if im.height > UR_MAX_H:
            nw = max(1, round(im.width * UR_MAX_H / im.height))
            im = im.resize((nw, UR_MAX_H), Image.Resampling.LANCZOS)
        buf = BytesIO()
        im.save(buf, format="WEBP", lossless=True, method=METHOD)
        data = buf.getvalue()
        if len(data) <= UR_LOSSLESS_BUDGET:
            return data
        best = None
        for q in UR_QUALITIES:
            buf = BytesIO()
            im.save(buf, format="WEBP", quality=q, method=METHOD)
            cand = buf.getvalue()
            if best is None or len(cand) < len(best):
                best = cand
            if len(cand) <= UR_JPEG_LIKE_BUDGET:
                break
        return best if best is not None else data
    if im.height > MAX_H:
        nw = max(1, round(im.width * MAX_H / im.height))
        im = im.resize((nw, MAX_H), Image.Resampling.LANCZOS)
    best = None
    for q in QUALITIES:
        buf = BytesIO()
        im.save(buf, format="WEBP", quality=q, method=METHOD)
        data = buf.getvalue()
        if best is None or len(data) < len(best):
            best = data
        if len(data) <= TARGET_BYTES:
            return data
    return best if best is not None else b""


def main() -> None:
    masters = sorted(MASTER.glob("char_*.webp"))
    if not masters:
        raise SystemExit(f"no masters in {MASTER}")
    print(f"masters={len(masters)}")
    total = 0
    for master in masters:
        dest = SRC / master.name
        im = Image.open(master)
        if im.mode != "RGBA":
            im = im.convert("RGBA")
        data = encode(im, ur=master.name.startswith("char_ur_"))
        dest.write_bytes(data)
        total += len(data)
        print(f"{master.name}: {master.stat().st_size/1024:.0f}KB -> {len(data)/1024:.0f}KB")
    print(f"TOTAL {total/1024/1024:.2f}MB")


if __name__ == "__main__":
    main()
