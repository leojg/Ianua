# Gate YouTube Shorts in Android browsers

**Date:** 2026-09-25
**Status:** implemented 2026-09-25, not yet verified on a device.
**Builds on:** ADR-0005 (authored with this spec), ADR-0003, ADR-0004.

## Context
Device feedback on v0.1: the extension works in desktop Brave, but Brave on Android has no
extension support, and the owner watches YouTube there, because of its ad blocking. The
Android app only watched the YouTube app.

## Affected repos
| repo path | role | docs/ADRs to respect |
|---|---|---|
| `leojg/Ianua` | rule-pack format, shared matcher, Android service + disclosure | ADR-0001, -0003, -0004; new ADR-0005 |

## Decisions taken (user)
- The rule pack lists the browsers. Contributors can add or fix one with a data change.
- Scope: Brave, Chrome, Firefox, Edge, Samsung Internet. The list is data, so extra
  browsers cost nothing.

## Approach
- `android.browsers: [{ package, urlBarViewIds }]` in the rule pack (optional, schema 1).
- `ScreenNode` gains `text`, `isFocused` and `findByViewId(id)`. `NodeSnapshot` implements
  them for tests, and the Android wrapper uses `findAccessibilityNodeInfosByViewId`.
- `AndroidMatcher` takes the pack's `WebMatcher`. For a browser package, it takes the first
  visible, unfocused address bar and gates when `WebMatcher.gatedVideoIdInAddressBar(text)`
  matches. That method accepts scheme-less text such as `m.youtube.com/shorts/…`.
  App rules are unchanged.
- The service needs no new logic; `matcher.packages` now includes the browsers. The
  disclosure and accessibility description mention the address bar.

## Steps
1. `rules/youtube.json`: add `browsers` and bump `version`.
   `rules/fixtures/android/`: browser snapshots (Shorts URL, focused bar, watch URL).
2. `shared`: `RulePack` (`BrowserRule`), parser validation (browsers need `web` rules,
   non-empty ids), `ScreenNode` and `NodeSnapshot`, `WebMatcher.gatedVideoIdInAddressBar`,
   `AndroidMatcher`, and tests.
3. `androidApp`: `NodeInfoScreenNode`, the service's package list (XML default plus runtime
   narrowing), strings.
4. Docs: `rules/README.md` format table, `README.md`.

## Verification
- `./gradlew check` (new matcher and parser tests on JS and JVM).
- `./gradlew :androidApp:assembleFdroidDebug`. Then on a device, in each browser:
  - opening `m.youtube.com/shorts/…` shows the gate;
  - typing a Shorts URL does not show the gate until you press Enter;
  - regular videos never show the gate;
  - Go back goes back in the browser.
  If a browser doesn't react, capture its screen with the debug dump and fix its
  `urlBarViewIds`.

## Risks / deferred
- The address-bar view ids are best-effort. Firefox's newer toolbar may expose none.
- Browsers that elide the URL to the domain can't be matched.
- Shorts shelves on the mobile site stay visible.
