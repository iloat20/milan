"""最终对比：现网 vs 4 个候选（final/），灰底合成便于肉眼判断去背。"""
from PIL import Image, ImageDraw
from pathlib import Path

ROOT = Path(__file__).parent
FINAL = ROOT / "final"
WORK = ROOT / "work"
PROD = Path(r"C:\Users\Administrator\Downloads\work\milan\MilanKotlin\app\src\main\res\drawable-nodpi\char_ur_zhulong.webp")

TH = 360
TILE_W = 240


def thumb_rgba(im: Image.Image, h: int = TH) -> Image.Image:
    w = max(1, int(im.width * h / im.height))
    return im.resize((w, h), Image.LANCZOS)


def on_gray(im: Image.Image) -> Image.Image:
    bg = Image.new("RGBA", im.size, (210, 210, 210, 255))
    return Image.alpha_composite(bg, im)


def labeled(canvas: Image.Image, x: int, y: int, text: str) -> None:
    d = ImageDraw.Draw(canvas)
    d.text((x + 8, y + 6), text, fill=(0, 0, 0))


def main() -> None:
    files = [(PROD, "现网 (prod)")]
    for f in sorted(FINAL.glob("char_ur_zhulong_*.webp")):
        files.append((f, f.stem.replace("char_ur_zhulong_", "候选 ")))
    n = len(files)
    pad = 14
    W = TILE_W * n + pad * (n + 1)
    H = TH + 36 + pad * 2
    sheet = Image.new("RGB", (W, H), (245, 245, 245))
    for i, (f, name) in enumerate(files):
        im = Image.open(f).convert("RGBA")
        t = thumb_rgba(im)
        # pad to fixed width
        canvas = Image.new("RGBA", (TILE_W, TH), (210, 210, 210, 255))
        canvas.alpha_composite(t, ((TILE_W - t.width) // 2, 0))
        x = pad + i * (TILE_W + pad)
        sheet.paste(canvas.convert("RGB"), (x, pad))
        labeled(sheet, x, TH + pad + 4, name)
    out = WORK / "final_compare.png"
    sheet.save(out)
    print(f"saved {out} {sheet.size}")


if __name__ == "__main__":
    main()