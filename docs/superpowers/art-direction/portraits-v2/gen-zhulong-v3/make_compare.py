"""生成对比图：raw 原图 + 去背结果（灰底），并排缩略。"""
from PIL import Image, ImageDraw
from pathlib import Path

ROOT = Path(__file__).parent
RAW, CUT, WORK = ROOT / "raw", ROOT / "cut", ROOT / "work"
WORK.mkdir(exist_ok=True)

TH = 420  # 缩略图高度


def thumb(im: Image.Image, h: int = TH) -> Image.Image:
    w = max(1, int(im.width * h / im.height))
    return im.resize((w, h), Image.LANCZOS)


def on_gray(im: Image.Image) -> Image.Image:
    """RGBA 合成到浅灰底，便于肉眼检查抠图边缘。"""
    bg = Image.new("RGBA", im.size, (200, 200, 200, 255))
    return Image.alpha_composite(bg, im)


def main() -> None:
    raws = sorted(RAW.glob("*.png"))
    cuts = sorted(CUT.glob("*.png"))
    n = len(raws)
    W = sum(thumb(Image.open(f)).width for f in raws) + 16 * (n + 1)
    sheet = Image.new("RGB", (W, TH * 2 + 16 * 3), (245, 245, 245))
    d = ImageDraw.Draw(sheet)

    x = 16
    for f in raws:
        t = thumb(Image.open(f).convert("RGB"))
        sheet.paste(t, (x, 16))
        x += t.width + 16
    y = 16 + TH + 16
    x = 16
    for f in cuts:
        t = thumb(on_gray(Image.open(f).convert("RGBA")))
        sheet.paste(t, (x, y))
        x += t.width + 16

    out = WORK / "compare.png"
    sheet.save(out)
    print(f"saved {out} {sheet.size}")


if __name__ == "__main__":
    main()
