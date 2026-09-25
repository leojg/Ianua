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
- `rules/` — rule packs (JSON, data only) and fixtures. Merging to `main` here ships to users
  (ADR-0003).
- `shared/` — Kotlin Multiplatform (Android + JS). All non-glue logic lives here, tested in
  `commonTest`.
- `extension/` — Kotlin/JS modules (background, content, gate, popup) + `static/manifest.json`.
- `androidApp/` — Jetpack Compose + `IanuaAccessibilityService`; flavors `play`, `fdroid`.

## Hard rules
- Never block at the network layer for YouTube (ADR-0001).
- Rule packs never carry code or raw CSS, only selectors and predicates (ADR-0003).
- No Google Play Services / Firebase in any Android flavor. Play-only deps go in `play`
  (ADR-0004).
- Nothing leaves the device except the daily rule-pack fetch from GitHub.
- Every rule change updates `rules/fixtures/` so the tests exercise it.

## Git
Conventional Commits (`type(scope): summary`, scopes: `rules`, `shared`, `extension`,
`android`, `docs`, `ci`). Branch, commit and push only with explicit confirmation.
