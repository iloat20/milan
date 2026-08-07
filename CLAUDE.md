# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project vision

**Milan** is a gacha-style mobile card game centered on a **360° card-inspection experience**. The core loop is **summon (gacha) → collect → progress → inspect → battle**, driven by a multi-world narrative and lore-driven talent trees. The project name is milan. Target platform is **Android**.

The summon art style is a **dimensional rift / portal** (characters cross over from different worlds). The broader art direction is a **mixed-rendering** style (cel-shaded vs. realistic per world).

## Implementation

The game is a **Kotlin / Jetpack Compose native Android** app in `MilanKotlin/` (Gradle 8 + AGP 8.10.1 + Kotlin 2.1.20, `com.milan.game`). Single-`Activity` architecture with a pure-state router (`MilanNavHost`), Material3 theming, kotlinx.serialization for save/content JSON, coroutines for async work. APK at `MilanKotlin/app/build/outputs/apk/debug/app-debug.apk`.

> This is the third implementation. A Unity C# version (`Assets/_Project/`) and a .NET 10 native-Android version (`MauiMilan/` + `Tests/`) used to live in the repo and were removed on 2026-08-07 in favor of the Kotlin rewrite. Old logic can be recovered from git history; code comments still carry "C# 某某翻译" cross-references — keep those.

### Build & run
```bash
./gradlew.bat :app:assembleDebug          # Debug APK
./gradlew.bat :app:testDebugUnitTest      # unit tests (JUnit4 + coroutines-test)
```
JDK 17+ is required (gradle.properties pins `org.gradle.java.home`). Versions live in `MilanKotlin/gradle/libs.versions.toml`. `minSdk=29, targetSdk=36, compileSdk=36`.

### Project layout
```
MilanKotlin/app/src/main/java/com/milan/game/
├── MainActivity.kt        # single host Activity + MilanNavHost (pure-state routing)
├── MilanApp.kt            # Application: CrashReporter.install → bootTrace → GameState.ensureInitialized
├── data/                  # save models (@Serializable) · SaveManager · SaveProvider (interface)
│   └── AndroidSaveProvider.kt  # filesDir/save/save.json + .bak/.tmp atomic write (Android layer)
├── domain/                # pure game logic — NO android.* imports allowed
│   ├── gacha/             # GachaEngine · PityCounter
│   ├── progression/       # EconomyFormulas · ProgressionEngine · TalentEngine
│   └── battle/            # BattleSimulator · BattleUnits
├── infrastructure/        # CrashReporter · eventbus/ (EventBus, Events)
├── services/              # GameService (orchestration) · GameContent (fallback data) · ContentModels
└── ui/                    # GameState (process singleton) · screens/ · components/ · nav/ · theme/
```

## Architecture

Layered, systems decoupled through a central **EventBus**:

```
Presentation │ MainActivity + ui/ (Compose screens, GameState process singleton)
─────────────┼────────────────────────────────────────────
Services     │ GameService (gacha, progression, battle rewards, content)
─────────────┼────────────────────────────────────────────
Domain (pure)│ GachaEngine · PityCounter · ProgressionEngine · TalentEngine · BattleSimulator
─────────────┼────────────────────────────────────────────
Data         │ SaveData & models (@Serializable) · SaveManager · SaveProvider
─────────────┼────────────────────────────────────────────
Infrastructure│ EventBus · CrashReporter
```

Key design rule: **`domain/`, `data/` models and `infrastructure/eventbus/` must NOT import `android.*`.** This keeps gacha odds, progression math, battle sim and talent logic runnable anywhere with injected `kotlin.random.Random` seeds.

### Save system
- `SaveProvider` interface → `AndroidSaveProvider` (JSON in `filesDir/save/`): atomic writes via `.tmp` → replace, keeps a `.bak`; a killed process never truncates the main save.
- `SaveManager` **never throws on load**: corrupt main → try `.bak`/`.tmp` → only then fall back to default save, with a `CrashReporter` trace. A throw here during init = app permanently won't start.
- JSON via kotlinx.serialization. `@SerialName` keys align with data.json's PascalCase keys — do not rename (save/content loss).

### EventBus
- Queue-and-dispatch: `publish` only enqueues; the host must call `dispatch` for real delivery (queue cap 512). Subscribe with an `owner` for batch `unsubscribeAll`. Handler exceptions go to `handlerException` tracing, never crash the thread.

### Transaction paradigm
- Every currency/progression write: budget/validate → mutate in memory → persist; on persist failure roll back the in-memory change and return `false` — the **rollback path does not broadcast events**. Handle `SpendXxx`/`AddXxx` return values.

## Screen flow

Bottom bar with 5 tabs: `Home 主页 / Gacha 抽卡 / Deck 卡组 / Shop 商店 / Settings 设置` (see `ui/nav/GameNavBar.kt` `NavItem`). Sub-pages overlay the tab layer and back out step by step: 神谱图鉴 (placeholder) → 我的角色 (`CharacterListScreen`) → 角色详情 (`CharacterDetailScreen`) → 角色养成 (`ProgressionScreen`). `GameState` is the single source of truth shared by all screens.

## Testing

Unit tests live in `app/src/test/java/com/milan/game/` (JUnit4 + kotlinx-coroutines-test): `SaveDataTest` / `SaveManagerTest` / `BattleSimulatorTest` / `GachaEngineTest` / `PityCounterTest` / `EconomyFormulasTest` / `ProgressionEngineTest` / `TalentEngineTest`. Domain engines take injected `Random` seeds for determinism — add unit tests for new domain logic. **Service-layer and UI-layer behavior are untested.**

## Key design references

- `docs/superpowers/specs/2026-07-28-milan-gacha-design.md` — full game design (Chinese). Systems, progression, gacha algorithm, battle, inspection, data model, architecture, performance targets.
- `docs/superpowers/specs/2026-08-07-*` — Kotlin rewrite planning documents (13-task plan: Compose + coroutines + kotlinx.serialization).

## Notes for future sessions

- `.superpowers/`, `docs/superpowers/`, `.omo/`, `.omc/` are planning artifacts, not source.
- Content data main source: `MilanKotlin/app/src/main/assets/data.json` (packaged asset); `services/GameContent.kt` is the in-code fallback (not the source of truth) used silently when data.json is missing/corrupt. **Current state: `MilanApp` passes `contentJson = null`, so content actually comes from the fallback — wire the asset up and keep both paths flowing through `GameContent.enrich` (issue #31).**
- Portraits: `res/drawable/char_<rarity>_<pinyin>.png` (R×7 / SR×8 / SSR×6 / UR×7); weapon art: `assets/weapons/<vfx>.png` (28 files) — `CharacterDetailScreen` loads via `context.assets.open("weapons/$weaponVfx.png")`, falls back to the weapon name when missing.
- Rarity enum: `R=1, SR=2, SSR=3, UR=4`. Worlds: `Shinwa, Aether, Ironveil`.
- Gacha rules: duplicate pulls award star fragments via `EconomyFormulas.FragmentsForRarity` (UR 50 / SSR 20 / SR 5 / R 1). If a rolled rarity band has no candidates in the pool, the roll upgrades to the nearest higher band with candidates (never silently re-rolls the whole pool). Pity counter resets on any natural drop at/above the pity rarity.
- Comments and UI copy are all Chinese; comments often carry historical pitfall notes — read them before touching related code.
- Theme colors live in `ui/theme/` (`AppTheme.kt` / `ElementTheme.kt` / `Theme.kt`).
