"""Normalize status-talent Chance/Duration in data.json (R7-P1 content debt).

Engine contract (TalentEngine.statusChanceOf):
  Chance > 0 wins; else Value in (0,1] is probability; Value in (1,100] is percent.
  Duration <= 0 defaults to 2 turns.

This script materializes Chance/Duration so content freeze does not depend on
the engine fallback. Safe to re-run (idempotent for already-normalized nodes).
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "app/src/main/assets/data.json"
STATUS = {"Burn", "Poison", "Bleed", "Stun", "Chill", "Disarm", "Stiff", "Taunt"}
DEFAULT_DURATION = 2


def chance_from(value: float) -> float:
    if value <= 0:
        return 0.0
    if value <= 1.0:
        return float(value)
    if value <= 100.0:
        return float(value) / 100.0
    return 0.0


def normalize_effect(eff: dict) -> bool:
    if eff.get("Type") not in STATUS:
        return False
    changed = False
    value = float(eff.get("Value") or 0)
    chance = float(eff.get("Chance") or 0)
    if chance <= 0:
        derived = chance_from(value)
        if derived > 0:
            eff["Chance"] = derived
            changed = True
    duration = int(eff.get("Duration") or 0)
    if duration <= 0:
        eff["Duration"] = DEFAULT_DURATION
        changed = True
    return changed


def walk(node) -> int:
    changed = 0
    if isinstance(node, dict):
        if "Type" in node and ("Value" in node or "Chance" in node):
            if normalize_effect(node):
                changed += 1
        for v in node.values():
            changed += walk(v)
    elif isinstance(node, list):
        for v in node:
            changed += walk(v)
    return changed


def main() -> None:
    text = DATA.read_text(encoding="utf-8")
    data = json.loads(text)
    n = walk(data)
    DATA.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"normalized status effects: {n}")


if __name__ == "__main__":
    main()
