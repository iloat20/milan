"""Re-encode portraits from masters with quality-first settings.

Aggressive 2026-09-11 pass (≤400KB / maxH=1024 / q≤72) destroyed soft
atmosphere and mid-alpha background layers. This pass keeps masters,
targets ~900KB and maxH=1248 at high WebP quality so translucent art survives.
"""
from __future__ import annotations

import shutil
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/res/drawable-nodpi"
MASTER = ROOT / "tools/backup/portraits-original"
MAX_H = 1248
TARGET_BYTES = 900 * 1024
# Prefer quality; only step down if over target.
QUALITIES = (90, 86, 82, 78)
METHOD = 6


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
        w, h = im.size
        if h > MAX_H:
            nw = max(1, round(w * MAX_H / h))
            im = im.resize((nw, MAX_H), Image.Resampling.LANCZOS)
        best: bytes | None = None
        for q in QUALITIES:
            buf = BytesIO()
            im.save(buf, format="WEBP", quality=q, method=METHOD)
            data = buf.getvalue()
            if best is None or len(data) < len(best):
                best = data
            if len(data) <= TARGET_BYTES:
                best = data
                break
        assert best is not None
        dest.write_bytes(best)
        total += len(best)
        print(f"{master.name}: {master.stat().st_size/1024:.0f}KB -> {len(best)/1024:.0f}KB")
    print(f"TOTAL {total/1024/1024:.2f}MB")


if __name__ == "__main__":
    main()
