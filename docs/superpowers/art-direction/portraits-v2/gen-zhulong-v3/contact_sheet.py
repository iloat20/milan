"""31 张立绘全量 contact sheet（灰底合成），供人工目视检查。

用途：结构类缺陷（多头 / 多余肢体 / 面部异常）无法用数值 QC 检出，
必须由人目视。本脚本把所有立绘排成网格，一张图完成全量目视。
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.stdout.reconfigure(encoding="utf-8")

DRAWABLE = Path(r"C:\Users\Administrator\Downloads\work\milan\MilanKotlin\app\src\main\res\drawable-nodpi")
OUT = Path(r"C:\Users\Administrator\Downloads\work\milan\docs\superpowers\art-direction\portraits-v2\gen-zhulong-v3\work\contact_sheet.png")

COLS = 6
TW, TH = 190, 285      # 单格尺寸
LABEL_H = 22
PAD = 8


def rarity_order(p: Path) -> tuple:
    n = p.name.lower()
    for i, r in enumerate(("ur", "ssr", "sr", "r")):
        if f"char_{r}_" in n:
            return (i, p.name)
    return (9, p.name)


def main() -> None:
    files = sorted(DRAWABLE.glob("char_*.webp"), key=rarity_order)
    n = len(files)
    rows = (n + COLS - 1) // COLS
    W = COLS * TW + PAD * (COLS + 1)
    H = rows * (TH + LABEL_H) + PAD * (rows + 1)
    sheet = Image.new("RGB", (W, H), (238, 238, 238))
    d = ImageDraw.Draw(sheet)

    for i, f in enumerate(files):
        r, c = divmod(i, COLS)
        x = PAD + c * (TW + PAD)
        y = PAD + r * (TH + LABEL_H + PAD)
        im = Image.open(f).convert("RGBA")
        im.thumbnail((TW, TH), Image.LANCZOS)
        tile = Image.new("RGBA", (TW, TH), (205, 205, 205, 255))
        tile.alpha_composite(im, ((TW - im.width) // 2, TH - im.height))
        sheet.paste(tile.convert("RGB"), (x, y))
        d.text((x + 2, y + TH + 5), f.stem.replace("char_", ""), fill=(20, 20, 20))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"saved {OUT} {sheet.size} ({n} portraits)")


if __name__ == "__main__":
    main()