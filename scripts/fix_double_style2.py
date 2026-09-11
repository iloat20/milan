from pathlib import Path
import re

root = Path(r"C:\Users\Administrator\Downloads\work\milan\MilanKotlin\app\src\main\java\com\milan\game\ui")

# Find Text blocks with two style = and merge
pat = re.compile(
    r"(style\s*=\s*MaterialTheme\.typography\.\w+(?:\.copy\([^)]*\))?)(,\s*\n)(\s*)(style\s*=\s*[^,\n]+),",
    re.M,
)

count = 0
for f in root.rglob("*.kt"):
    t = f.read_text(encoding="utf-8")
    o = t

    def repl(m):
        global count
        count += 1
        first, comma_nl, ind, second = m.groups()
        # second is style = X
        second_val = second.split("=", 1)[1].strip()
        if second_val.startswith("TextStyle(") or second_val.startswith("Tabular") or second_val.startswith("androidx.compose.ui.text.TextStyle"):
            # drop second if only tnum or merge
            if "tnum" in second_val or second_val == "Tabular":
                return first + comma_nl  # remove second style line entirely? keep comma after first
            return first  # leave for manual
        return first + comma_nl  # drop second style for now if unknown

    # Better targeted fixes per file content we know
    t2 = t
    # HealthBar shield text
    t2 = t2.replace(
        """                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )""",
        """                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Frost,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                )""",
    )
    # AppChrome double style
    t2 = t2.replace(
        """                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Text1,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    // 水墨呼吸：透明度在 0.84~1.0 间波动，替代原 translationY 浮动
                    // （translate 浮动会让标题与底栏重叠产生闪烁，alpha 更稳定）
                    alpha = 0.92f + sin(phase) * 0.08f
                },
                style = TextStyle(""",
        """                style = MaterialTheme.typography.titleLarge.copy(""",
    )
    # TowerScreen power
    t2 = t2.replace(
        """                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.Frost,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )""",
        """                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = AppTheme.Frost,
            )""",
    )
    # ProgressionPanels Lv
    t2 = t2.replace(
        """                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                    style = Tabular,
                )""",
        """                    style = MaterialTheme.typography.displayMedium.merge(Tabular),
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.Gold,
                )""",
    )
    # BattleResultOverlay if still broken
    t2 = re.sub(
        r"style = MaterialTheme\.typography\.(\w+),\n(\s*)fontWeight = FontWeight\.Bold,\n(\s*)color = ([^,\n]+),\n\s*style = TextStyle\(fontFeatureSettings = \"tnum\"\),",
        r"style = MaterialTheme.typography.\1.copy(fontFeatureSettings = \"tnum\"),\n\2fontWeight = FontWeight.Bold,\n\3color = \4,",
        t2,
    )
    t2 = re.sub(
        r"style = MaterialTheme\.typography\.(\w+),\n(\s*)fontWeight = FontWeight\.Bold,\n(\s*)color = ([^,\n]+),\n\s*style = Tabular,",
        r"style = MaterialTheme.typography.\1.merge(Tabular),\n\2fontWeight = FontWeight.Bold,\n\3color = \4,",
        t2,
    )

    if t2 != t:
        f.write_text(t2, encoding="utf-8")
        print("patched", f.name)

# Fix AppChrome remaining TextStyle close paren - read file
app = root / "nav" / "AppChrome.kt"
text = app.read_text(encoding="utf-8")
if text.count("style =") > 1 and "titleLarge.copy(" in text:
    # find the broken block
    print("--- AppChrome style blocks ---")
    for i, line in enumerate(text.splitlines(), 1):
        if "style" in line or "Shadow" in line or "shadow" in line:
            if 115 <= i <= 160:
                print(f"{i}: {line}")
