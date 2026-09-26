# Ianua — project rules

Ianua is a distraction blocker for short-form video (YouTube Shorts first). It puts a
*door* in front of Shorts: a prompt with a countdown instead of a hard block. It ships as
a Chromium MV3 extension and an Android app. License: GPL-3.0.

## Read before changing anything
1. `docs/adr/NNNN-*.md` — **binding decisions.** Read the ADR covering the area you touch.
   When a change makes a new decision in a locked area, author the next sequential ADR.
   Locked areas: the blocking mechanism, the rule-pack format or its update channel,
   Android permissions and dependencies, the language/stack.
2. `docs/specs/<slug>.md` — the spec for the work in progress. Specs are written here,
   not elsewhere. Postmortems go in `docs/postmortem/<slug>.md`.

## Layout
- `rules/` — rule packs (JSON, data only), one per file (`youtube.json` gate, `blocked.json`
  hard block, ADR-0006), and fixtures. Merging to `master` here ships to users (ADR-0003).
  A package added to `blocked.json` must also go in the Android manifest's `<queries>` and
  the accessibility config; `./gradlew check` enforces it.
- `shared/` — Kotlin Multiplatform (Android + JS). All non-glue logic lives here, tested in
  `commonTest`.
- `extension/` — one Kotlin/JS bundle for every context (`Main.kt` dispatches), `static/`
  (manifest, pages, icons), `e2e/` (Playwright smoke test). Stdlib coroutines only (ADR-0002).
  Never index a `dynamic` straight off a suspend call (`x.await()[k]`): Kotlin/JS drops the
  suspension point. Assign to a local first (see `Async.kt`).
- `androidApp/` — Jetpack Compose + `IanuaAccessibilityService`; flavors `play`, `fdroid`.

## Hard rules
- Never block at the network layer for YouTube (ADR-0001).
- Rule packs never carry code or raw CSS, only selectors and predicates (ADR-0003).
- No Google Play Services / Firebase in any Android flavor. Play-only deps go in `play`
  (ADR-0004).
- Nothing leaves the device except the daily rule-pack fetch from GitHub.
- Every rule change updates `rules/fixtures/` so the tests exercise it.

## Verify
```bash
./gradlew check                                   # shared tests (JS + JVM) + Android lint
./gradlew :extension:packageExtension && (cd extension/e2e && npm ci && npm test)
./gradlew :androidApp:assembleFdroidDebug :androidApp:assemblePlayDebug
```
The Android service needs a real device or an emulator. Say so when a change could not be
run on one.

## Git
Conventional Commits (`type(scope): summary`, scopes: `rules`, `shared`, `extension`,
`android`, `docs`, `ci`). Branch, commit and push only with explicit confirmation.
