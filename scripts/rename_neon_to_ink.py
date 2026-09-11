from pathlib import Path
import re

root = Path(r"C:\Users\Administrator\Downloads\work\milan\MilanKotlin\app\src\main\java\com\milan\game\ui")
count = 0
for f in root.rglob("*.kt"):
    if f.name == "ThemeButtons.kt":
        continue
    t = f.read_text(encoding="utf-8")
    if "NeonButton" not in t:
        continue
    o = t
    t = t.replace("NeonButton(", "InkButton(")
    # import path
    t = t.replace(
        "import com.milan.game.ui.components.NeonButton",
        "import com.milan.game.ui.components.InkButton",
    )
    if "InkButton" in t and "import com.milan.game.ui.components.InkButton" not in t and "fun InkButton" not in t:
        # same package or existing import of components
        if "import com.milan.game.ui.components." in t and "InkButton" not in o:
            pass
        if f.parent.name != "components" and "import com.milan.game.ui.components.InkButton" not in t:
            # add after other components imports
            m = re.search(r"(import com\.milan\.game\.ui\.components\.[^\n]+\n)", t)
            if m:
                t = t.replace(m.group(1), m.group(1) + "import com.milan.game.ui.components.InkButton\n", 1)
            else:
                t = re.sub(
                    r"(^package [^\n]+\n)",
                    r"\1\nimport com.milan.game.ui.components.InkButton\n",
                    t,
                    count=1,
                    flags=re.M,
                )
    if t != o:
        f.write_text(t, encoding="utf-8")
        count += 1
        print("renamed", f.name)
print("files", count)
