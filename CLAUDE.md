# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This repository is in **pre-development**. There is no source code, build configuration, or tests yet — only design-direction artifacts in `.superpowers/brainstorm/`. Before writing code, a tech stack and art direction must be confirmed (or re-confirmed) with the user; the brainstorm outputs below represent the *recommended* directions, not final decisions.

## Project vision

A gacha-style mobile card game centered on a **360° card-inspection experience**. The core loop is summon (gacha) → collect → inspect cards in 3D. The project name is **milan**.

## Direction captured from brainstorming

Two brainstorm sessions were run (see `.superpowers/brainstorm/722-1785159551/content/`). The recommended options were:

### Tech stack — Option A (recommended): Native Android
- **Kotlin + Jetpack Compose** for UI
- **OpenGL ES** for 3D card rendering
- Pros: small package size (~30MB), mature tooling, good AI-assisted dev efficiency, native performance
- Cons: 3D effects must be implemented from scratch (lower visual-effect ceiling); Android-only

Alternatives considered: **Option B** — full Unity (best 3D/effects, cross-platform, but ~200MB+ package, long dev cycle, poor AI-assist efficiency); **Option C** — hybrid native + Unity module (complex integration, Unity-in-Android pitfalls, large package, hard to debug). Option A was recommended.

### Gacha summon style — three candidates
- **A: Starry/cosmic summon** — starry vortex opens, character rises from a light pillar; strong "cross-dimensional collection" mystique
- **B: Magic scroll / summoning circle** — magic circle appears on the ground, character emerges in light; classic anime gacha style, high player familiarity
- **C: Dimensional rift / portal** — space tears, characters cross from different worlds; fits a "Marvel / DC / anime multiverse" setting

No single style was selected as final; this is an open decision.

## Decisions still to be made

Before or during development, expect to confirm:
- Final tech stack (the brainstorm recommends native Android, but it's not locked)
- Final summon art style (cosmic / scroll / rift)
- Target Android SDK / minimum API level
- Whether Unity is entirely off the table or reserved for a future 3D module
- Card data model and backend (local-only vs. server-driven gacha)
- Scope of the 360° inspection (purely visual vs. interactive/haptic)

## Notes for future sessions

- The `.superpowers/` directory is a planning-session artifact, not project source. Don't treat its HTML as code or as a buildable artifact.
- When implementation starts, expect this to be a standard Android/Gradle project if Option A is confirmed — set up `build.gradle(.kts)`, `app/src/main/java/...` (Kotlin), and an OpenGL ES or Compose 3D rendering path accordingly.
