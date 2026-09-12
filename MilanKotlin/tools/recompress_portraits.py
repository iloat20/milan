"""Recompress oversized portrait WebP under res/drawable-nodpi.

Source masters stay in tools/backup/portraits-original/. Target: ~400KB or less
per file while keeping alpha and enough resolution for Full (2x) decode.
"""
from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/res/drawable-nodpi"
BACKUP = ROOT / "tools/backup/portraits-original"
# Full decode samples 2x from source; 1024px height still covers UI Full/Thumb.
MAX_H = 1024
MAX_BYTES = 400 * 1024
QUALITIES = (72, 64, 56)
METHOD = 4


def ensure_backup(src: Path) -> None:
    BACKUP.mkdir(parents=True, exist_ok=True)
    dest = BACKUP / src.name
    if not dest.exists():
        shutil.copy2(src, dest)


def recompress(path: Path) -> tuple[int, int]:
    original = path.stat().st_size
    if original <= MAX_BYTES:
        return original, original
    ensure_backup(path)
    im = Image.open(path)
    if im.mode != "RGBA":
        im = im.convert("RGBA")
    w, h = im.size
    if h > MAX_H:
        nw = max(1, round(w * MAX_H / h))
        im = im.resize((nw, MAX_H), Image.Resampling.LANCZOS)
    best = path.read_bytes()
    best_size = len(best)
    for q in QUALITIES:
        from io import BytesIO

        buf = BytesIO()
        im.save(buf, format="WEBP", quality=q, method=METHOD)
        data = buf.getvalue()
        if len(data) < best_size:
            best = data
            best_size = len(data)
        if best_size <= MAX_BYTES:
            break
    if best_size < original:
        path.write_bytes(best)
    return original, best_size


def main() -> None:
    files = sorted(SRC.glob("char_*.webp"), key=lambda p: p.stat().st_size, reverse=True)
    print(f"portraits={len(files)}")
    total_before = total_after = 0
    oversize = []
    for p in files:
        before, after = recompress(p)
        total_before += before
        total_after += after
        flag = "OK" if after <= MAX_BYTES else "OVER"
        print(f"{flag} {p.name}: {before/1024:.0f}KB -> {after/1024:.0f}KB")
        if after > MAX_BYTES:
            oversize.append(p.name)
    print(
        f"TOTAL {total_before/1024/1024:.2f}MB -> {total_after/1024/1024:.2f}MB "
        f"(-{(1-total_after/total_before)*100:.0f}%)"
    )
    if oversize:
        print("still over budget:", ", ".join(oversize))


if __name__ == "__main__":
    main()
