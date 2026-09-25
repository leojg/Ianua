# ADR-0002 — Kotlin Multiplatform for both the extension and the Android app

**Date:** 2026-09-25 · **Status:** accepted

## Context
Ianua ships a Chromium extension and an Android app. The shared logic is rule-pack parsing,
matching and the gate policy (countdown, allowance). The maintainer knows Kotlin/Compose,
not TypeScript.

## Decision
One Gradle monorepo:
- `shared/`: KMP, `androidTarget` + `js(IR)`. All logic that is not platform glue lives here
  and is tested in `commonTest`.
- `extension/*`: Kotlin/JS executables (background service worker, content script, gate
  page, popup). Hand-written `external` declarations cover only the `chrome.*` APIs used.
  UI is `kotlinx-html` over plain DOM. No Compose Wasm/canvas, because of size and
  first-paint cost in a popup.
- `androidApp/`: Jetpack Compose (the Compose Multiplatform API). It can move to a CMP
  module if another UI target appears.

## Alternatives rejected
- TypeScript extension + Kotlin app, with the engine duplicated and held in line by shared
  test vectors: two languages and two engines for one maintainer.
- Flutter / React Native: no gain. The AccessibilityService must be native Kotlin anyway,
  and the extension UI is tiny.

## Consequences
- Kotlin/JS extensions are uncommon: few examples, and our own `chrome.*` bindings.
- Escape hatch: the extension modules are small. If Kotlin/JS becomes a drag they can be
  rewritten in TypeScript without touching `rules/`, which is the real shared contract.
