# Rule packs

One JSON file per site: what Ianua treats as short-form video and where it puts the gate.
Every build bundles these files, and clients refresh them about daily from
`https://raw.githubusercontent.com/leojg/Ianua/master/rules/<id>.json` (ADR-0003).

**Merging a change to `master` here ships it to every user.** Review it like a release.

## Format (`schemaVersion: 1`)

| Field | Meaning |
|---|---|
| `schemaVersion` | Format version. Clients ignore packs with a version they don't know. |
| `id` | Site id; must equal the file name. |
| `version` | Monotonic integer, by convention `yyyymmddNN`. **Bump it on every change.** Clients never replace a pack with one that is not strictly newer. |
| `web.hosts` | Exact hostnames the web rules apply to. |
| `web.gatedPaths` | Regexes over the URL path. Each needs **exactly one capture group: the video id**. Keep them to syntax that JavaScript, Java and RE2 all accept (DNR uses RE2): character classes, `{n,}`, no lookarounds or backreferences. |
| `web.continueUrl` | Where *Continue* goes; `{id}` is replaced by the video id. Must be `https`. |
| `web.hideSelectors` | CSS selectors to hide. **Selectors only**: `{ } ; @ < /* */ url( \` are rejected, and Ianua writes the `display: none` rule itself. Each selector is its own rule, so one that a browser doesn't support only disables itself. |
| `android.packages` | App packages the accessibility service listens to. |
| `android.gatedScreens` | The screen is gated when any rule matches a visible node: `anyViewId` (full resource ids) or `anyContentDescription` (exact, but localized, so prefer view ids). |
| `android.browsers` | Optional. `[{ "package", "urlBarViewIds" }]`: browsers whose address bar is matched against `web.hosts` + `web.gatedPaths` (ADR-0005). The bar is read only while it isn't focused, so text being typed never triggers the gate. Requires `web`. **To add or fix a browser:** dump its screen on a Shorts page (see below), find the address-bar node's `viewId`, add it, bump `version`, and add the dump as a fixture. |

## Fixtures

`fixtures/android/*.json` are accessibility-tree snapshots. `shared` tests assert that the
Shorts player is gated and other screens are not. `fixtures/web/*.html` is the page the
extension end-to-end test runs against.

**The current fixtures are synthetic.** They mirror the ids and elements the rules target,
not captures from the real apps. Replace them with real captures:

- **Android:** install a debug build, tap *Dump the next YouTube screen (debug)* and open the
  screen you want (in the YouTube app or a listed browser) within 30 s. Dumps keep text only
  for address bars. Then
  `adb pull /sdcard/Android/data/me.lgcode.ianua/files/dumps/` and copy the JSON here.
- **Web:** on youtube.com, copy the rendered DOM (DevTools → `<html>` → Copy outerHTML) into
  `fixtures/web/`. Keep the element ids the smoke test looks up, or adjust
  `extension/e2e/smoke.mjs`.

When YouTube changes its UI, first capture a fixture that shows the breakage, then fix the
rule, bump `version`, and run `./gradlew check` and the e2e test.
