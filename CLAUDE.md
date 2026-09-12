# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project vision

**Milan** is a gacha-style mobile card game centered on a **360° card-inspection experience**. The core loop is **summon (gacha) → collect → progress → inspect → battle**, driven by a multi-world narrative and lore-driven talent trees. The project name is milan. Target platform is **Android**.

The summon art style is a **dimensional rift / portal** (characters cross over from different worlds). The broader art direction is a **mixed-rendering** style (cel-shaded vs. realistic per world).

## Implementation

The game is a **Kotlin / Jetpack Compose native Android** app in `MilanKotlin/` (Gradle 9.5.0 + AGP 9.3.2 + Kotlin 2.4.20 — AGP 9 has built-in Kotlin, no kotlin-android plugin — `com.milan.game`). Single-`Activity` architecture with type-safe Navigation Compose 2.9 routes (`@Serializable` route classes in `ui/nav/Routes.kt`), Material3 theming (BOM 2026.09.00 + material3 1.4.0), kotlinx.serialization for save/content JSON, coroutines for async work, **multi-module: `:shared` KMP domain + `:core` services + `:data` save models**. APK at `MilanKotlin/app/build/outputs/apk/debug/app-debug.apk`.

> This is the third implementation. A Unity C# version (`Assets/_Project/`) and a .NET 10 native-Android version (`MauiMilan/` + `Tests/`) used to live in the repo and were removed on 2026-08-07 in favor of the Kotlin rewrite. Old logic can be recovered from git history; code comments still carry "C# 某某翻译" cross-references — keep those.

### Build & run
```bash
./gradlew.bat :app:assembleDebug          # Debug APK
./gradlew.bat :app:testDebugUnitTest      # unit tests (JUnit4 + coroutines-test)
./gradlew.bat :desktopApp:run             # desktop simulator (reuses :shared engines)
```
JDK 17+ is required. Versions live in `MilanKotlin/gradle/libs.versions.toml`. `minSdk=29, targetSdk=37, compileSdk=37`. **In the DSH sandbox use `pwsh -NoProfile -File .\run-gradle.ps1 <args>`** (redirects GRADLE_USER_HOME/ANDROID_USER_HOME into the workspace; see AGENTS.md). CI: `.github/workflows/ci.yml` (architecture gate + unit tests + Debug APK on main push/PR).

### Project layout
```
MilanKotlin/ 
├── shared/src/commonMain/kotlin/com/milan/game/   # KMP domain layer — NO android.* imports
│   ├── domain/gacha/          # GachaEngine · PityCounter (kotlin.random.Random)
│   ├── domain/progression/    # EconomyFormulas · ProgressionEngine · TalentEngine
│   ├── domain/battle/         # BattleSimulator · BattleUnits · StrategicBattleSimulator
│   ├── domain/deck|monetization|mission/
│   └── data/Rarity.kt
├── core/                      # GameService + 16 XxxApi + EventBus/Crash/Audio/Worker
├── data/                      # save models (@Serializable) · SaveManager · SaveProvider
│   └── AndroidSaveProvider.kt # filesDir/save/save.json + .bak/.tmp (Android layer)
├── desktopApp/                # desktop simulator demo reusing :shared (application plugin)
├── benchmark/                 # macrobenchmark (wired into settings; needs device for :benchmarkRelease)
└── app/src/main/java/com/milan/game/
    ├── MainActivity.kt        # single host Activity + MilanNavHost (type-safe routes)
    ├── MilanApp.kt            # Application: CrashReporter.install → bootTrace → GameState.ensureInitialized
    ├── di/AppGraph.kt         # composition root
    └── ui/                    # GameState · screens/ · components/ · nav/ · theme/
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

Key design rule: **the domain layer (`:shared` commonMain) and `data/` models and `infrastructure/eventbus/` must NOT import `android.*`.** This keeps gacha odds, progression math, battle sim and talent logic runnable anywhere (Android, desktop, future iOS) with injected `kotlin.random.Random` seeds — `desktopApp` is a live demo of that reuse.

### Save system
- `SaveProvider` interface → `AndroidSaveProvider` (JSON in `filesDir/save/`): atomic writes via `.tmp` → replace, keeps a `.bak`; a killed process never truncates the main save.
- `SaveManager` **never throws on load**: corrupt main → try `.bak`/`.tmp` → only then fall back to default save, with a `CrashReporter` trace. A throw here during init = app permanently won't start.
- JSON via kotlinx.serialization. `@SerialName` keys align with data.json's PascalCase keys — do not rename (save/content loss).

### EventBus
- Queue-and-dispatch: `publish` only enqueues; the host must call `dispatch` for real delivery (queue cap 512). Subscribe with an `owner` for batch `unsubscribeAll`. Handler exceptions go to `handlerException` tracing, never crash the thread.

### Transaction paradigm
- Every currency/progression write: budget/validate → mutate in memory → persist; on persist failure roll back the in-memory change and return a non-Success result — the **rollback path does not broadcast events**. Since 2026-08-13 writes return typed `WriteOutcome` (Success/Rejected/SaveFailed; `pull` returns `PullOutcome`) and go through the shared `GameService.transaction(tag, mutate, rollback, onCommit)` template. UI refresh prefers `GameService.snapshot` (StateFlow) over EventBus light markers.
- Since the suspend refactor: **all write operations are `suspend`** — mutation + persistence happen inside a serial `writeMutex` (Mutex) critical section, persistence runs on `Dispatchers.IO`, the main thread never blocks, and rollback is decided synchronously in the same critical section. UI calls them from `rememberCoroutineScope().launch` / `LaunchedEffect`; unit tests use `runTest`. Lock-holding paths (e.g. `pull`) use `transactionLocked` internally (Mutex is not reentrant).

### UI state (since 2026-08-13)
- `GameService.snapshot: StateFlow<GameSnapshot>` (revision + currency/fragments/owned count) is refreshed on every successful write; screens use `collectAsStateWithLifecycle()` (ResourceBar, ShopScreen, ProgressionScreen). EventBus remains for cross-screen commands (audio/haptics) and is still the test-asserted commit signal.

## Screen flow

Bottom bar with 5 tabs: `Home 主页 / Gacha 抽卡 / Deck 卡组 / Shop 商店 / Settings 设置` (see `ui/nav/GameNavBar.kt` `NavItem`). Sub-pages overlay the tab layer and back out step by step: 神谱图鉴 (placeholder) → 我的角色 (`CharacterListScreen`) → 角色详情 (`CharacterDetailScreen`) → 角色养成 (`ProgressionScreen`). `GameState` is the single source of truth shared by all screens.

## Testing

Unit tests live in `app/src/test/java/com/milan/game/` (JUnit4 + kotlinx-coroutines-test): `SaveDataTest` / `SaveManagerTest` / `BattleSimulatorTest` / `GachaEngineTest` / `PityCounterTest` / `EconomyFormulasTest` / `ProgressionEngineTest` / `TalentEngineTest` / `EventBusTest` / `DataJsonContentTest` / `PortraitLoaderTest` / `RoutesTest`. Domain engines take injected `Random` seeds for determinism — add unit tests for new domain logic. **Service-layer (`GameServiceTest`) is covered; `RoutesTest` covers the type-safe routes (`NavItem.toNavRoute()` mapping + `@Serializable` round-trips, pure Kotlin); Compose rendering remains untested.**

## Key design references

- `docs/superpowers/specs/2026-07-28-milan-gacha-design.md` — full game design (Chinese). Systems, progression, gacha algorithm, battle, inspection, data model, architecture, performance targets.
- `docs/superpowers/specs/2026-08-07-*` — Kotlin rewrite planning documents (13-task plan: Compose + coroutines + kotlinx.serialization).

## Notes for future sessions

- `.superpowers/`, `docs/superpowers/`, `.omo/`, `.omc/` are planning artifacts, not source.
- Content data main source: `MilanKotlin/app/src/main/assets/data.json` (packaged asset); `MilanApp` reads it at startup and passes it to `GameService`; `services/GameContent.kt` is the in-code fallback (not the source of truth) used silently when data.json is missing/corrupt/has no valid characters. **Both load paths flow through `GameContent.enrich` for derived fields (issue #31 done).**
- Portraits: `res/drawable/char_<rarity>_<pinyin>.webp` (R×7 / SR×8 / SSR×6 / UR×7, migrated from old versions and converted PNG→WebP); `PortraitImage` probes with `getIdentifier` and decodes off the main thread, rendering a rarity-gradient placeholder when missing. Weapon art: `assets/weapons/<vfx>.webp` (28 files, converted PNG→WebP) — `CharacterDetailScreen` loads via `context.assets.open("weapons/$weaponVfx.webp")` on an IO thread with 2x sampling, falls back to the weapon name when missing.
- Rarity enum: `R=1, SR=2, SSR=3, UR=4`. Worlds: `Shinwa, Aether, Ironveil`.
- Gacha rules: duplicate pulls award star fragments via `EconomyFormulas.FragmentsForRarity` (UR 50 / SSR 20 / SR 5 / R 1). If a rolled rarity band has no candidates in the pool, the roll upgrades to the nearest higher band with candidates (never silently re-rolls the whole pool). Pity counter resets on any natural drop at/above the pity rarity.
- Comments and UI copy are all Chinese; comments often carry historical pitfall notes — read them before touching related code.
- Theme colors live in `ui/theme/` (`AppTheme.kt` / `ElementTheme.kt` / `Theme.kt`).
