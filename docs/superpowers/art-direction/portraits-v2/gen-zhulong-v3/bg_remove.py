"""去背：rembg 单 session，输出 RGBA PNG。

用法:
  python bg_remove.py --src raw --dst cut [--model u2netp|u2net|isnet-general-use]
"""
import argparse
import sys
from pathlib import Path

from PIL import Image
from rembg import new_session, remove


def run(src: Path, dst: Path, model: str) -> None:
    dst.mkdir(parents=True, exist_ok=True)
    files = sorted(src.glob("*.png")) + sorted(src.glob("*.jpg")) + sorted(src.glob("*.webp"))
    if not files:
        print("no source images", file=sys.stderr)
        return

    # 单 session：禁止在 worker 内 new_session，否则并发下载 onnx 会 os.rename 竞态
    print(f"loading rembg session: {model}")
    sess = new_session(model)

    for f in files:
        out = dst / (f.stem + ".png")
        if out.exists():
            print(f"skip (exists) {out.name}")
            continue
        im = Image.open(f).convert("RGBA")
        res = remove(im, session=sess)
        res.save(out, "PNG")
        a = res.getchannel("A")
        opaque = sum(1 for p in a.getdata() if p > 8) / (a.width * a.height)
        print(f"{out.name}  size={res.size}  opacity_ratio={opaque:.3f}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--dst", required=True)
    ap.add_argument("--model", default="u2netp")
    a = ap.parse_args()
    run(Path(a.src), Path(a.dst), a.model)


if __name__ == "__main__":
    main()
