"""Subset Ma Shan Zheng to BrandType/RitualType glyph set.

Ritual copy is a closed set (logo + gacha reveal phrases + Latin rarity labels).
Keeps original at tools/backup/fonts-original/.
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/_py"))

from fontTools import subset  # noqa: E402
from fontTools.ttLib import TTFont  # noqa: E402

SRC = ROOT / "app/src/main/res/font/ma_shan_zheng_regular.ttf"
BACKUP_DIR = ROOT / "tools/backup/fonts-original"
BACKUP = BACKUP_DIR / SRC.name

# Closed ritual/brand strings observed in HomeScreen / CyberStage / CyberCards (2026-09-11).
RITUAL_TEXT = (
    "丹青录"
    "URSSR S R!"
    "古卷展开 · 浓墨蓄力"
    "金箔凝聚 · 丹青觉醒"
    "墨迹汇聚 · 灵犀涌动"
    "敕令開陣 · 充能中"
    "敕令开卷 · 万古回响"
    "敕令开卷 · 金光乍现"
    "敕令开卷"
    "十连 · 寻访"
    "敕"
    "琉璃 · 天工开物"
    "金箔 · 神谕降临"
    "朱砂 · 名士现世"
    "石青 · 灵犀一点"
    "松烟 · 墨迹初成"
    "·"
    "！"
)


def main() -> None:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    if not BACKUP.exists():
        shutil.copy2(SRC, BACKUP)
    # Always re-subset from the full original master.
    shutil.copy2(BACKUP, SRC)
    chars = sorted(set(RITUAL_TEXT))
    print(f"glyphs={len(chars)} chars={''.join(chars)}")
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.recalc_bounds = True
    options.canonical_order = True
    font = subset.load_font(str(SRC), options)
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(text="".join(chars))
    subsetter.subset(font)
    subset.save_font(font, str(SRC), options)
    before = BACKUP.stat().st_size
    after = SRC.stat().st_size
    print(f"{before/1024/1024:.2f}MB -> {after/1024:.0f}KB")


if __name__ == "__main__":
    main()
