#!/usr/bin/env python3
"""v3 排印收口：把裸 fontSize = N.sp 收敛到 MaterialTheme.typography 档位。

只处理「简单字面量」：fontSize = 12.sp / 16.sp …
跳过：计算式 ((x * 0.45f).sp)、已有 style = 的 Text 块、非 ui/ 目录。
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(r"C:\Users\Administrator\Downloads\work\milan\MilanKotlin\app\src\main\java\com\milan\game\ui")

# 字号 → typography 档（v3 §5.4）
# 与 fontWeight 组合时保留 fontWeight 作为 override
MAP = {
    40: "displayLarge",
    36: "displayLarge",
    34: "displayMedium",
    32: "displayMedium",
    30: "displayMedium",
    28: "displaySmall",
    27: "headlineMedium",
    26: "headlineMedium",
    24: "headlineMedium",
    22: "headlineMedium",
    20: "titleLarge",
    18: "titleLarge",
    17: "titleMedium",
    16: "titleMedium",
    15: "bodyLarge",
    14: "bodyMedium",
    13: "bodyMedium",
    12: "labelLarge",
    11: "labelMedium",
    10: "labelSmall",
    9: "labelSmall",
    8: "labelSmall",
    7: "labelSmall",
}

# 允许 skip 的特殊装饰：品牌/仪式/印章已走 BrandType/RitualType 或计算式

simple_fs = re.compile(r"fontSize\s*=\s*(\d+)\.sp\b")
text_call = re.compile(r"\bText\s*\(")
style_present = re.compile(r"style\s*=")


def ensure_imports(src: str) -> str:
    if "androidx.compose.material3.MaterialTheme" in src:
        return src
    # 插在 package 之后第一个 import 前
    m = re.search(r"(^package [^\n]+\n)", src, re.M)
    if not m:
        return src
    insert_at = m.end()
    imp = "\nimport androidx.compose.material3.MaterialTheme\n"
    return src[:insert_at] + imp + src[insert_at:]


def process_file(path: Path) -> tuple[int, list[str]]:
    text = path.read_text(encoding="utf-8")
    original = text
    notes: list[str] = []
    replacements = 0

    # 逐个 Text( 调用块做粗扫描（到下一个顶层或简单括号平衡）
    # 简化：按行处理，仅当同行或前后 8 行内无 style=
    lines = text.splitlines(keepends=True)
    for i, line in enumerate(lines):
        if "fontSize" not in line:
            continue
        m = simple_fs.search(line)
        if not m:
            continue
        size = int(m.group(1))
        token = MAP.get(size)
        if not token:
            notes.append(f"{path.name}:{i+1}: size {size} 无档位映射，跳过")
            continue
        # 邻域是否已有 style=
        window = "".join(lines[max(0, i - 12) : min(len(lines), i + 12)])
        # 找最近的 Text( 上文
        before = "".join(lines[max(0, i - 20) : i + 1])
        if "style =" in before or "style=" in before:
            # 已有 style，只删 fontSize 行（若整行仅是 fontSize）
            stripped = line.strip()
            if re.fullmatch(r"fontSize\s*=\s*\d+\.sp,?", stripped):
                lines[i] = ""
                replacements += 1
                notes.append(f"{path.name}:{i+1}: 已有 style，删除裸 fontSize={size}")
            continue
        # 替换本行 fontSize = N.sp → style = MaterialTheme.typography.XXX
        new_line = simple_fs.sub(
            f"style = MaterialTheme.typography.{token}",
            line,
        )
        if new_line != line:
            lines[i] = new_line
            replacements += 1

    new_text = "".join(lines)
    if replacements and "MaterialTheme.typography" in new_text:
        new_text = ensure_imports(new_text)
    if new_text != original:
        path.write_text(new_text, encoding="utf-8")
    return replacements, notes


def main() -> int:
    total = 0
    all_notes: list[str] = []
    files = sorted(ROOT.rglob("*.kt"))
    for f in files:
        n, notes = process_file(f)
        if n:
            total += n
            all_notes.extend(notes)
    print(f"replacements={total}")
    for note in all_notes[:80]:
        print(note)
    if len(all_notes) > 80:
        print(f"... +{len(all_notes)-80} more notes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
