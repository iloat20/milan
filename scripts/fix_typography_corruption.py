from pathlib import Path
import re

root = Path(r"C:\Users\Administrator\Downloads\work\milan\MilanKotlin\app\src\main\java\com\milan\game\ui")

# Fix Theme.kt corruptions
theme = root / "theme" / "Theme.kt"
t = theme.read_text(encoding="utf-8")
t = t.replace(
    """val BrandType = TextStyle(
    fontFamily = MaShanZheng,
    style = MaterialTheme.typography.headlineMedium,
    lineHeight = 34.sp,
    letterSpacing = 4.sp,
)""",
    """val BrandType = TextStyle(
    fontFamily = MaShanZheng,
    fontSize = 26.sp,
    lineHeight = 34.sp,
    letterSpacing = 4.sp,
)""",
)
t = t.replace(
    """        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.displayLarge, lineHeight = 48.sp, fontFeatureSettings = "tnum",
    ),""",
    """        fontFamily = NotoSerifSC, fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp, lineHeight = 48.sp, fontFeatureSettings = "tnum",
    ),""",
)
t = t.replace(
    """        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, lineHeight = 24.sp,
    ),""",
    """        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp,
    ),""",
)
theme.write_text(t, encoding="utf-8")

# Fix GpuEffects matchParentSize import
gpu = root / "effects" / "GpuEffects.kt"
g = gpu.read_text(encoding="utf-8")
g = g.replace("import androidx.compose.foundation.layout.matchParentSize\n", "")
g = g.replace("Modifier.matchParentSize()", "Modifier.fillMaxSize()")
gpu.write_text(g, encoding="utf-8")

# Fix double style= patterns: keep first style, merge second TextStyle features if needed
double_style = re.compile(
    r"style = MaterialTheme\.typography\.(\w+),\n(\s*)fontWeight = ([^,\n]+),\n(\s*)color = ([^,\n]+),\n\s*style = TextStyle\(([^)]*)\),",
    re.M,
)

def fix_double(m):
    token, ind, weight, ind2, color, ts_args = m.groups()
    extras = ts_args.strip()
    if extras:
        return (
            f"style = MaterialTheme.typography.{token}.copy({extras}),\n"
            f"{ind}fontWeight = {weight},\n"
            f"{ind2}color = {color},"
        )
    return (
        f"style = MaterialTheme.typography.{token},\n"
        f"{ind}fontWeight = {weight},\n"
        f"{ind2}color = {color},"
    )

fixed_files = 0
for f in root.rglob("*.kt"):
    text = f.read_text(encoding="utf-8")
    new = double_style.sub(fix_double, text)
    # also simpler: two consecutive style = lines
    new2 = re.sub(
        r"style = MaterialTheme\.typography\.(\w+),\n(\s*)style = TextStyle\(([^)]*)\),",
        lambda m: f"style = MaterialTheme.typography.{m.group(1)}.copy({m.group(3)})," if m.group(3).strip() else f"style = MaterialTheme.typography.{m.group(1)},",
        new,
    )
    if new2 != text:
        f.write_text(new2, encoding="utf-8")
        fixed_files += 1
        print("fixed", f.name)

# HomeScreen import
home = root / "home" / "HomeScreen.kt"
h = home.read_text(encoding="utf-8")
if "import androidx.compose.material3.MaterialTheme" not in h:
    h = h.replace(
        "import androidx.compose.material3.Text\n",
        "import androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.Text\n",
    )
    home.write_text(h, encoding="utf-8")
    print("home import")

print("done, double-style files:", fixed_files)
