# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project vision

**Milan** is a gacha-style mobile card game centered on a **360° card-inspection experience**. The core loop is **summon (gacha) → collect → progress → inspect → battle**, driven by a multi-world narrative and lore-driven talent trees. The project name is milan. Target platform is **Android**.

The summon art style is a **dimensional rift / portal** (characters cross over from different worlds). The broader art direction is a **mixed-rendering** style (cel-shaded vs. realistic per world).

## Implementation

The game is a **.NET 10 native Android** app in `MauiMilan/` (TFM `net10.0-android`). Despite the folder name, this is a **plain native-Android** project (SDK `Microsoft.NET.Sdk`), not MAUI UI — it uses `Android.App.Activity` and `Android.Widget` directly, which keeps the package small and the build fast. It builds a native APK at `MauiMilan/bin/Release/net10.0-android/com.milan.game-Signed.apk`.

> A Unity C# implementation used to live in `Assets/_Project/` but has been removed. The pure, UnityEngine-free game core (Domain engines, EventBus, Enums) was moved into `MauiMilan/Core/` so the project is now self-contained.

### Build & run
```bash
dotnet build MauiMilan/MauiMilan.csproj -c Release
```
A .NET 10 SDK with the Android workload is required (the runtime alone is not enough). The initial currency is set high (999999) for testing — pulls should feel unlimited.

### Project layout
```
MauiMilan/
├── Activities/        # Android Activities (screens)
│   ├── HomeActivity           — hub: title, currency, nav cards (gacha / characters / collection)
│   ├── GachaActivity          — single/ten-pull, full results in a rarity-colored grid
│   ├── CharacterListActivity  — owned characters as rarity cards (2-col grid)
│   ├── CharacterDetailActivity— stats, talent placeholder, 360° inspect button
│   └── CollectionActivity     — collection progress bar + full roster (owned vs silhouette)
├── UI/                # Shared design system
│   ├── UIHelper.cs            — Theme palette + rarity colors + rounded button/card/gradient helpers
│   └── GameState.cs           — process-wide singleton owning the shared GameService/SaveManager
├── Services/
│   └── GameService.cs         — gacha/pull logic, progression, content load (data.json + fallback)
├── Infrastructure/Save/       # ISaveProvider · LocalSaveProvider · SaveData · SaveManager (System.Text.Json)
├── Core/              # Pure game logic — NO Android/UnityEngine dependency
│   ├── Domain/        # GachaEngine · PityCounter · ProgressionEngine · TalentEngine · BattleSimulator · VisualLayerComposer
│   ├── Infrastructure/EventBus/  # EventBus · GameEvents
│   └── Data/Enums.cs
├── Platforms/Android/ # MainActivity, AndroidManifest.xml, Assets/data.json (content)
└── Resources/         # app icons, Raw/data.json
```

## Architecture

Modular layered architecture, systems decoupled through a central **EventBus**:

```
Presentation  │ Android Activities / screens (MauiMilan/Activities + UI)
──────────────┼────────────────────────────────────────────
Services      │ GameService (gacha, progression, content)
──────────────┼────────────────────────────────────────────
Domain (pure) │ GachaEngine · PityCounter · ProgressionEngine · TalentEngine · BattleSimulator · VisualLayerComposer
──────────────┼────────────────────────────────────────────
Data          │ Enums · JSON DTOs (CharacterDataEntry, GachaPoolDataEntry) · local save
──────────────┼────────────────────────────────────────────
Infrastructure│ EventBus · Save system
```

Key design rule: **`Core/` (Domain/Data/EventBus) must NOT contain `UnityEngine` or Android usings.** This keeps gacha odds, progression math, battle sim, and talent logic runnable anywhere with injected `System.Random` seeds.

### Save system
- `ISaveProvider` → `LocalSaveProvider` (JSON file in the app's internal storage).
- `SaveManager` wraps a provider; holds `SaveData` (owned characters, items, gacha pity counters, currencies, plus reserved `UserId`/`ServerSyncStatus` for future online).
- JSON via `System.Text.Json`. `SaveData.FromJson` **must fall back to `CreateDefault()` on null/empty/corrupt input** — a null `Current` crashes the game.

### EventBus
- Static, struct-typed, queue-and-dispatch. `Publish` enqueues; `Dispatch` runs each frame — events are NOT instantaneous.
- `UnsubscribeAll(target)` and `ClearQueue()` exist for cleanup.

## Screen flow

`HomeActivity` (launcher) → `GachaActivity` / `CharacterListActivity` / `CollectionActivity`; tapping a character card opens `CharacterDetailActivity`. Each Activity rebuilds its view in `OnResume` so currency and owned-count stay fresh after navigating back. `GameState` is the single source of truth shared across all of them.

## Testing

There are no automated tests currently. (The Unity EditMode tests were removed with the Unity implementation.) The pure domain engines are structured to be unit-testable — `GachaEngine`, `PityCounter`, `BattleSimulator`, etc. take injected `System.Random` seeds for determinism — and are the natural place to add NUnit/xUnit tests. **Service-layer behavior (currency deduction, fragment-on-duplicate, battle rewards) is untested.**

## Key design references

- `docs/superpowers/specs/2026-07-28-milan-gacha-design.md` — full game design (Chinese). Systems, progression, gacha algorithm, battle, inspection, data model, architecture, performance targets.
- `docs/superpowers/plans/2026-07-28-milan-mvp.md` — the original MVP implementation plan (TDD-style tasks, written for the Unity build). Useful as design intent, but the Unity-specific steps no longer apply.

## Notes for future sessions

- The `.superpowers/` and `docs/superpowers/` directories are planning artifacts, not source. The HTML brainstorm outputs in `.superpowers/brainstorm/` are gitignored scratch.
- Content data lives in `MauiMilan/Platforms/Android/Assets/data.json` (and `Resources/Raw/data.json`); `GameService.LoadFallback()` mirrors the same data in code as a fallback if the asset is missing. Keep these in sync when adding characters/pools.
- Rarity enum: `R=1, SR=2, SSR=3, UR=4`. Worlds: `Shinwa, Aether, Ironveil`.
- Gacha rules: duplicate pulls award star fragments (`item_star_fragment`, UR 50 / SSR 20 / SR 5 / R 1 — see `GameService.FragmentsForRarity`). If a rolled rarity band has no candidates in the pool, the roll upgrades to the nearest higher band with candidates (never silently re-rolls the whole pool). Pity counter resets on any natural drop at/above the pity rarity.
- Save integrity: `SaveData.FromJson` swallows corrupt JSON and returns `CreateDefault()`; `LocalSaveProvider.Save` writes atomically via a `.tmp` + `File.Replace` (keeps a `.bak`). Do not regress either.
- The `UI.Theme` static class holds the full palette and rarity colors — change the look of every screen there.
