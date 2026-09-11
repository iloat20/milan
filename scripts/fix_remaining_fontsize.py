from pathlib import Path
import re

root = Path(r"C:\Users\Administrator\Downloads\work\milan\MilanKotlin\app\src\main\java\com\milan\game\ui")
repls = [
    (r'Text(text = "❤️", fontSize = 16.sp)', 'Text(text = "❤️", style = MaterialTheme.typography.titleMedium)'),
    (r'Text("我方", fontSize = 12.sp, color = AppTheme.Frost, fontWeight = FontWeight.Bold)', 'Text("我方", style = MaterialTheme.typography.labelLarge, color = AppTheme.Frost, fontWeight = FontWeight.Bold)'),
    (r'Text(text = "— 空 —", fontSize = 12.sp, color = AppTheme.Text3)', 'Text(text = "— 空 —", style = MaterialTheme.typography.labelLarge, color = AppTheme.Text3)'),
    (r'Text(en, fontSize = 10.sp, color = AppTheme.Text3, letterSpacing = 0.08.em)', 'Text(en, style = MaterialTheme.typography.labelSmall, color = AppTheme.Text3, letterSpacing = 0.08.em)'),
    (r'Text(ratesLabel(pool), fontSize = 11.sp, color = AppTheme.Text2, lineHeight = 15.sp)', 'Text(ratesLabel(pool), style = MaterialTheme.typography.labelMedium, color = AppTheme.Text2, lineHeight = 15.sp)'),
    (r'Text(text = reason, fontSize = 13.sp, color = AppTheme.Text3)', 'Text(text = reason, style = MaterialTheme.typography.bodyMedium, color = AppTheme.Text3)'),
    (r'Text(text = "×$count", fontSize = 9.sp, color = color, modifier = Modifier.padding(start = 2.dp))', 'Text(text = "×$count", style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(start = 2.dp))'),
    # PageComponents nameplate large number
    ("fontSize = 27.sp,", "style = MaterialTheme.typography.headlineMedium,"),
]
count = 0
for f in root.rglob("*.kt"):
    t = f.read_text(encoding="utf-8")
    o = t
    for a, b in repls:
        if a in t:
            t = t.replace(a, b)
            count += 1
    if t != o:
        if "androidx.compose.material3.MaterialTheme" not in t:
            t = re.sub(r"(^package [^\n]+\n)", r"\1\nimport androidx.compose.material3.MaterialTheme\n", t, count=1, flags=re.M)
        f.write_text(t, encoding="utf-8")
print("applied", count)
