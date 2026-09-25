# Ianua v0.1 — YouTube Shorts gate (Chromium extension + Android)

**Date:** 2026-09-25
**Status:** plan — awaiting review; then `/implement`.
**Builds on:** ADR-0001 … ADR-0004 (all authored alongside this spec).

## Context

Ianua ("door" in Latin) is a distraction blocker for short-form video. Rather than a hard
block, it puts a **door** in front of Shorts: to get in, the user has to stop, read a
prompt, wait, and consciously choose to continue. v0.1 covers **YouTube only**, on two
surfaces:

- **Web** — a Manifest V3 extension for Chromium browsers (Chrome, Brave, Chromium, Edge),
  distributed through the Chrome Web Store (Brave installs from it directly).
- **Android** — a native app that gates Shorts inside the **YouTube app**, distributed on
  Google Play and F-Droid.

Shorts share domains and CDNs with regular YouTube, and the Android app pins TLS, so
network-level blocking cannot tell them apart; all blocking happens at the UI layer
(ADR-0001).

Intended outcome: on both surfaces, a user cannot land in the Shorts feed by accident.
On the web, Shorts do not even appear in the feed.

## Affected repos

| repo path | role | docs/ADRs to respect |
|---|---|---|
| `leojg/ianua` | the entire product — Gradle KMP monorepo: `shared/`, `extension/`, `androidApp/`, `rules/` | `CLAUDE.md`, `docs/adr/0001`–`0004` (this change authors them) |

## Decisions taken (user)

| Topic | Decision |
|---|---|
| Web target | Browser extension, Chromium family only (Chrome, Brave, Chromium). No website/dashboard. |
| Android mechanism | AccessibilityService — the only way to act inside the native YouTube app. |
| Behaviour | Friction **prompt**, not a hard block. **Not configurable in v0.1** (only an on/off toggle). |
| Prompt defaults | Message + **10 s** countdown → *Go back* (default) or *Continue*. Android: *Continue* unlocks for **5 min**. |
| Web Shorts access | Shelves, sidebar/tab entries and channel "Shorts" tabs are **hidden**. The only way in is a direct link, and only after tapping *Continue* on the prompt. |
| Scope | YouTube only. Adults / self-control; no strict mode, no parental control. |
| Data | Local only; no accounts, no backend, no telemetry. |
| Rule updates | Fetched from this GitHub repo; bundled copy as fallback. |
| Distribution | Chrome Web Store; Google Play + F-Droid. No Google Play Services / Firebase. |
| Money | Free for now; keep the door open to a paid tier (Play flavor only). |
| iOS / Safari | Out of scope for the near future. |
| Language | Kotlin everywhere (Kotlin Multiplatform; Kotlin/JS for the extension). |
| Android app id | `me.lgcode.iauna` — ⚠ **confirm spelling**: the project/repo is *Ianua*; the app id is permanent once published on Play. |
| minSdk | **26** (Android 8.0) — recommended, pending confirmation. |

## Approach

### Gate semantics

| | Web | Android |
|---|---|---|
| Trigger | Navigating to `/shorts/<id>` (hard load or in-app SPA navigation), incl. `m.youtube.com` | Shorts player visible in `com.google.android.youtube` |
| Gate UI | Extension-owned page `gate.html?v=<id>` (YouTube page never loads) | Full-screen `TYPE_ACCESSIBILITY_OVERLAY` window; media paused via a `KEYCODE_MEDIA_PAUSE` key event |
| *Go back* | `history.back()`, or close the tab if it has no history | Remove overlay + `GLOBAL_ACTION_BACK` |
| *Continue* (after 10 s) | Redirect to `https://www.youtube.com/watch?v=<id>` — the regular player, so there is **no swipe-to-next feed** | Remove overlay, resume media, allowance for 5 min; leaving Shorts ends the allowance early |
| Feed hiding | Shorts shelves, sidebar/mini-guide entries, search-result Shorts, channel Shorts tab | Not possible (other apps' views can't be modified) — gate only |

The web *Continue* redirect to `/watch` is what makes the user's rule hold ("only a
direct link, only after the prompt"). It also removes the need for a timed allowance on
the web.

### Architecture (ADR-0002)

```
rules/                  youtube.json (rule pack) + schema doc + fixtures/
shared/                 KMP (androidTarget + js(IR)): rule model, parsing, matching, GatePolicy, RuleRepository
extension/
  background/           Kotlin/JS → service worker: nav interception, rule refresh (chrome.alarms)
  content/              Kotlin/JS → content script: builds a display:none stylesheet from rule selectors
  gate/                 Kotlin/JS → gate.html page: countdown, Go back / Continue
  popup/                Kotlin/JS → popup: on/off toggle
  static/               manifest.json, gate.html, popup.html, icons, dnr rules
androidApp/             Jetpack Compose UI + IanuaAccessibilityService; flavors `play` / `fdroid`
```

- **Rule pack (ADR-0003).** A versioned JSON document, as data only. Web matchers:
  hosts, gated-path regexes and **selectors only**. Ianua builds the CSS itself
  (`<selectors> { display: none !important }`), so a fetched rule can never inject
  arbitrary CSS or code. Android matchers: package plus view-id / content-description
  predicates, evaluated against a platform-neutral `NodeSnapshot` tree. That keeps the
  matchers testable in `commonTest`.
- **Rule refresh.** `RuleRepository` fetches
  `https://raw.githubusercontent.com/leojg/ianua/main/rules/youtube.json` about once a
  day. It validates the result and keeps the last-known-good copy. It rejects packs with
  an unknown `schemaVersion` or a `version` lower than the current one, and falls back to
  the bundled copy. HTTP is `expect/actual`: `fetch` on JS, `HttpURLConnection` on
  Android. Ktor is avoided to keep the extension bundle small.
- **Kotlin/JS ↔ browser APIs.** Hand-written `external` declarations are kept in
  `extension/background` (or a small `extension/chrome-api` module if more than one
  module needs them). They cover only the APIs we use: `storage.local`, `webNavigation`,
  `tabs`, `alarms`, `runtime`, `declarativeNetRequest`.
- **UI.** Android uses Jetpack Compose, which has the same API as Compose Multiplatform;
  it can move into a CMP module if another UI target appears. The extension popup and
  gate use `kotlinx-html` with plain DOM. There is no Compose Wasm canvas: it costs
  several MB and a slow first paint in a popup.

## Steps

### 1 — Repo scaffolding
- Gradle wrapper, `settings.gradle.kts`, `gradle/libs.versions.toml` (latest stable
  Kotlin, AGP, Compose BOM, kotlinx-serialization, kotlinx-html, kotlinx-coroutines,
  AndroidX WorkManager/DataStore at scaffold time).
- `.gitignore`, `.editorconfig`, a `README.md` explaining what Ianua is and how to build it.
- `.github/workflows/ci.yml`: `./gradlew check :extension:packageExtension :androidApp:assembleFdroidDebug`.

### 2 — `rules/`
- `rules/youtube.json`, `schemaVersion: 1`:
  - web hosts `www.youtube.com`, `m.youtube.com`;
  - gated paths `^/shorts/([A-Za-z0-9_-]{5,})`;
  - hide selectors for: the Shorts shelf (desktop `ytd-rich-shelf-renderer[is-shorts]`,
    `ytd-reel-shelf-renderer`, grid-shelf variants; mobile `ytm-reel-shelf-renderer`,
    `ytm-shorts-lockup-view-model`), guide/mini-guide "Shorts" entries, search-result
    Shorts (`a[href^="/shorts/"]` containers), and the channel "Shorts" tab;
  - Android package `com.google.android.youtube`, with Shorts-player predicates on view
    ids such as `…:id/reel_player_page_container` / `reel_recycler`. Verify these against
    a live dump before committing.
- `rules/README.md` documenting the format. `rules/fixtures/`: saved YouTube HTML
  (desktop home, search, channel, mobile home) and JSON node dumps of the Android app
  (Home tab, Shorts player, regular player).

### 3 — `shared/`
- `commonMain`:
  - `RulePack` (kotlinx-serialization) and `RulePackParser` (validation, schemaVersion
    check);
  - `WebMatcher.gatedVideoId(url): String?` and `WebMatcher.hideCss(): String`;
  - `NodeSnapshot` + `AndroidMatcher.isGatedScreen(root): Boolean`;
  - `GatePolicy`: countdown of 10 s, allowance of 5 min, state machine
    `Idle → Gating → Allowed(until) → Idle`, clock injected;
  - `RuleRepository`: bundled → cached → remote, with the monotonic version rule.
- `commonTest`:
  - parser accepts/rejects cases;
  - URL cases (`/shorts/abc`, `/shorts/abc?feature=share`, `m.`, `/watch?v=` not gated,
    `/@chan/shorts` handled);
  - GatePolicy transitions;
  - AndroidMatcher against `rules/fixtures/*.json` (fixtures copied in as test
    resources).

### 4 — `extension/`
- `static/manifest.json` (MV3):
  - permissions: `storage`, `webNavigation`, `alarms`, `declarativeNetRequest`;
  - host permissions: `*://www.youtube.com/*`, `*://m.youtube.com/*`,
    `https://raw.githubusercontent.com/*`;
  - `gate.html` listed under `web_accessible_resources` for the YouTube hosts;
  - static DNR ruleset `rules_shorts.json`: `main_frame` redirect of `/shorts/<id>` to
    `/gate.html?v=<id>`, catching hard loads before YouTube renders.
- `background`: `webNavigation.onHistoryStateUpdated` + `onReferenceFragmentUpdated`.
  For YouTube tabs, `WebMatcher.gatedVideoId` → `tabs.update(url = gate)`. That catches
  SPA navigation. The toggle state lives in `storage.local`; turning Ianua off disables
  the DNR ruleset and skips gating. A daily `alarms` job refreshes the rules.
- `content` (runs at `document_start` on the YouTube hosts): injects a `<style>` built from
  `WebMatcher.hideCss()`. As a belt-and-braces measure, it listens for `yt-navigate-start`
  to the Shorts path and asks the background to gate early.
- `gate`: message, 10 s visible countdown, *Go back* (focused by default) and *Continue*
  (disabled until the countdown ends) → `location.replace("https://www.youtube.com/watch?v=<id>")`.
- `popup`: an on/off switch and the rule-pack version.
- Gradle task `:extension:packageExtension`: copies the 4 JS bundles plus `static/` into
  `extension/build/dist/` (loadable unpacked) and zips it for the Chrome Web Store.

### 5 — `androidApp/`
- `applicationId "me.lgcode.iauna"` (pending spelling confirmation), `minSdk 26`,
  `targetSdk`/`compileSdk` = latest. Product flavors `play` and `fdroid`, identical in
  v0.1; the `play` flavor exists so a future Play Billing dependency never reaches F-Droid.
  **No GMS/Firebase dependencies in any flavor.**
- `IanuaAccessibilityService` + `res/xml/accessibility_service_config.xml`:
  - `packageNames="com.google.android.youtube"`,
    `typeWindowStateChanged|typeWindowContentChanged`, `canRetrieveWindowContent`,
    `notificationTimeout≈150`, `isAccessibilityTool=false`;
  - converts `rootInActiveWindow` → `NodeSnapshot`, bounding depth and node count;
  - `AndroidMatcher` → `GatePolicy`; shows or removes the overlay.
- `GateOverlay`: a `ComposeView` in a `TYPE_ACCESSIBILITY_OVERLAY` window (with a
  lifecycle/saved-state owner attached), the same prompt as the web, and media
  pause/resume via `AudioManager.dispatchMediaKeyEvent`.
- Onboarding (Compose), in order:
  1. prominent disclosure: what the service reads (only the YouTube app's screen
     structure), what it does, and that nothing leaves the device;
  2. deep link to Accessibility settings;
  3. for sideloaded installs on Android 13+, "Allow restricted settings" guidance.
- Main screen: service status, on/off toggle (DataStore), rule-pack version.
- `RuleRefreshWorker` (WorkManager, daily, network-constrained) → `RuleRepository`.
- Debug-only "dump current screen" action that writes a `NodeSnapshot` JSON. It is how
  `rules/fixtures/` gets refreshed when YouTube changes.

### 6 — Project docs
- `CLAUDE.md` and `docs/adr/0001`–`0004` (authored with this spec).

## Constraints / ADRs to honor
- `docs/adr/0001-block-at-the-ui-layer.md` — no DNS/VPN/network-level Shorts blocking.
- `docs/adr/0002-kotlin-multiplatform-everywhere.md` — logic lives in `shared/`; platform
  modules stay thin.
- `docs/adr/0003-rule-packs-are-remote-data.md` — fetched content is data, never code or
  raw CSS. This keeps the Chrome Web Store MV3 "no remote code" rule and Play policy
  satisfied.
- `docs/adr/0004-android-accessibility-service-without-gms.md` — scoped to the YouTube
  package; local-only; F-Droid-clean.
- License: GPL-3.0 (root `LICENSE`).

## New/updated ADR
ADR-0001 … ADR-0004 are authored with this spec (first decisions in the repo).

## Verification
- `./gradlew :shared:allTests`: parser, matchers against fixtures, GatePolicy.
- `./gradlew :extension:packageExtension`, then load `extension/build/dist/` unpacked in
  Chromium. Automated Playwright smoke test (Chromium at `/opt/pw-browsers/chromium`)
  against a local fixture server:
  - a hard load of `/shorts/x` ends at `gate.html`;
  - an SPA `pushState` to `/shorts/x` ends at `gate.html`;
  - *Continue* is disabled for 10 s, then lands on `/watch?v=x`;
  - shelf selectors are hidden in the home fixture.
- Manual check on live youtube.com: home, search, channel, and a shared Shorts link.
- `./gradlew :androidApp:assembleFdroidDebug :androidApp:assemblePlayDebug lint`.
- `./gradlew :androidApp:dependencies` shows no `com.google.android.gms` / `firebase`.
- Manual check on a device or emulator with the YouTube app: Shorts tab → gate; a
  Shorts link from the feed → gate; *Continue* → 5 min allowance; regular video → no gate.

## Risks / deferred
- **Selector/view-id rot** — YouTube changes its UI often. Mitigated by remote rule
  packs plus fixtures, and by the dump tool on Android. This is the main ongoing cost.
- **Play accessibility review** — needs the Permissions Declaration, prominent
  disclosure and a demo video. Release work goes in its own spec.
- **Media pause on Android** — `KEYCODE_MEDIA_PAUSE` depends on YouTube's media
  session. The fallback is `GLOBAL_ACTION_BACK` behind the overlay, with *Continue*
  then just dismissing the overlay.
- **Kotlin/JS extension tooling** is uncommon. If it becomes a drag, the extension
  modules (~few hundred lines) can be rewritten in TypeScript while keeping
  `rules/` unchanged (ADR-0002).
- **Rule signing** is deferred. v0.1 relies on HTTPS plus the selector-only format.
  Revisit before adding anything more powerful to rule packs.
- **Google developer verification** for sideloaded apps (F-Droid) — check its status
  before the first F-Droid submission.
- Deferred features: other sites/apps, configurable prompt, schedules/budgets, stats,
  strict mode, sync, Firefox, landing page at `iauna.lgcode.me`.
