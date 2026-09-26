# Ianua v0.3 — The door for Instagram Reels, Facebook Reels and Snapchat Spotlight

**Date:** 2026-09-26
**Status:** plan, awaiting review; then `/implement`.
**Builds on:**
- ADR-0001 (UI-layer blocking);
- ADR-0003 (rule packs as data);
- ADR-0004 and ADR-0005 (Android service, browser address bar);
- ADR-0006 (multiple packs; gate beats block).

## Context
v0.1 put a door in front of YouTube Shorts, and v0.2 hard-blocked the apps that are *only*
short video. v0.3 brings the **door** (prompt, 10 s countdown, Continue) to the short-video
*sections* of three apps that are otherwise useful: Instagram Reels, Facebook Reels and
Snapchat Spotlight. The feed, messages and profiles of those apps must keep working.

The multi-pack groundwork from v0.2 means most of this is **data**: three new rule packs.
The code work is what the packs can't express yet:
- a name for the prompt;
- sections with no single video to open;
- allowances kept per platform on Android.

## Affected repos
| repo path | role | docs/ADRs to respect |
|---|---|---|
| `leojg/Ianua` | three rule packs, gate-pack format extension, shared core, extension, Android app | `CLAUDE.md`; ADR-0001 … ADR-0006; new ADR-0007 |

## Decisions taken (user)
| Topic | Decision |
|---|---|
| Platforms | Instagram Reels, Facebook Reels, **Snapchat Spotlight**. X, Reddit and LinkedIn are later; they have no distinct URLs. |
| Continue | **Same as YouTube.** Web: open that one video in a non-feed view. Android: 5-minute allowance. |
| Prompt copy | **Name the platform**: "You're about to enter Instagram Reels". |
| Settings | **Still all or nothing.** One main switch. |

## Approach

### Gate-pack format additions (ADR-0007, schema stays 1, additive)
- **`displayName`** (top level, optional): shown in the prompt. It falls back to a generic
  "short videos". `youtube.json` gets `"YouTube Shorts"`.
- **`web.feedPaths`** (optional): regexes for a short-video *feed* with no single item to
  open, e.g. Instagram's `/reels/`. These get the door **without Continue**, only *Go back*,
  because there is nothing non-feed to continue to. The same applies wherever a platform has
  no non-feed view (see Snapchat below).
- **`web.continueUrl`** stays required when `gatedPaths` is non-empty.

### The three packs (all ids, paths and selectors best-effort, verified by fixtures)
| | Instagram (`instagram.json`) | Facebook (`facebook.json`) | Snapchat (`snapchat.json`) |
|---|---|---|---|
| `displayName` | Instagram Reels | Facebook Reels | Snapchat Spotlight |
| web hosts | `www.instagram.com`, `instagram.com` | `www.facebook.com`, `facebook.com`, `m.facebook.com`, `web.facebook.com` | `www.snapchat.com`, `snapchat.com` |
| `gatedPaths` | `^/reels?/([A-Za-z0-9_-]{5,})` | `^/reels?/([0-9]{5,})` | — |
| `continueUrl` | `https://www.instagram.com/p/{id}/` | `https://www.facebook.com/watch/?v={id}` | — |
| `feedPaths` | `^/reels/?$` | `^/reels/?$`, `^/watch/reels` | `^/spotlight` |
| `hideSelectors` | nav "Reels" link, profile Reels tab | Reels nav shortcut, Reels shelves in feed | Spotlight nav link |
| android package | `com.instagram.android` | `com.facebook.katana` | `com.snapchat.android` |
| gated screen | the full-screen Reels viewer (clips viewer) | the Reels viewer | the Spotlight viewer |

**Snapchat web has no single-video view**, so Spotlight on the web gets Go back only. This
departs from "same as YouTube"; the platform leaves no alternative. **Please confirm when
reviewing.**

**Known gap: reels that autoplay inline** inside the Instagram and Facebook home feeds are
not gated. On the web some are hidden by selectors; on Android they are not. Only opening
the full-screen viewer triggers the door. This matches the YouTube Shorts behaviour on
Android.

### Shared
- `RulePack.displayName`; `WebRules.feedPaths`; the parser validates both. `feedPaths` must
  compile, and `displayName` uses the same character rules as block platform names.
- `WebMatcher.gate(url)` returns `Item(id)`, `Feed`, or null. `Packs.gatedVideo` becomes
  `Packs.gate(url)`, which returns the pack id, the display name and `Item` or `Feed`.
- `Verdict.Gate` carries the pack id and display name. `Packs.verdict` reports which pack
  matched.
- `GateCopy.title(name)`, with a generic fallback.

### Extension
- **Manifest:**
  - `content_scripts.matches` and `host_permissions` gain the three platforms' hosts (the
    hide CSS and in-page navigation gating run there);
  - `web_accessible_resources` is already `<all_urls>`;
  - `version` 0.3.0.
- **DNR:** `gateRules` also emits redirects for `feedPaths`, to `gate.html?feed=1&s=<pack>`.
- **`gate.html`:** the title comes from the pack's `displayName`. With `feed=1` there is no
  Continue button, only *Go back*.
- **Content script:** unchanged in shape. It already iterates `Packs.web`; it now also gates
  `Feed` results.

### Android
- **Allowance per pack.** `GatePolicy` becomes a map keyed by pack id, so Continue on
  Instagram does not unlock YouTube. The overlay is shown for whichever pack's verdict
  triggered it.
- `GateScreen` takes the display name.
- `accessibility_service_config.xml` `packageNames` gains the three apps; `<queries>` gains
  them for the installed list. Extend `verifyBlockedPackages` into `verifyWatchedPackages`,
  covering gate packs too.
- **Android rules can't be trusted before real dumps.** Instagram and Snapchat expose some
  view ids. Facebook's app renders mostly without them (Litho), so its rule may need
  `anyContentDescription`, which is localized. The packs ship with best-effort values. Because
  rule packs update remotely, **real device dumps can fix them later without an app release**.

### Tests: every entry point, per behaviour (the v0.2 lesson)
| Entry point | YouTube (regression) | Instagram | Facebook | Snapchat |
|---|---|---|---|---|
| typed URL / hard load | ✓ | reel → gate → Continue → `/p/<id>/` | reel → gate → Continue → `/watch/?v=` | spotlight → gate, no Continue |
| in-page (SPA) navigation | ✓ | ✓ | ✓ | — (not an SPA route we can fake reliably) |
| link from another site | ✓ | ✓ | ✓ | ✓ |
| feed path | — | `/reels/` → no Continue | `/reels/` → no Continue | — |
| hide selectors on a fixture page | ✓ | ✓ | ✓ | ✓ |
| rule update through real storage | ✓ (v0.2 test) | — covered once, pack-agnostic | | |

Android fixtures (synthetic until dumped) cover each app's viewer (gated), home feed (not
gated), and the browser address bar for each platform's URL.

## Steps
1. **ADR-0007** `docs/adr/0007-gate-pack-display-name-and-feeds.md`: `displayName`,
   `feedPaths`, per-pack allowance.
2. **`rules/`:**
   - `instagram.json`, `facebook.json`, `snapchat.json`;
   - `youtube.json` gains `displayName` and a version bump;
   - `fixtures/web/{instagram,facebook,snapchat}_home.html`;
   - `fixtures/android/*` (viewer, home and browser cases per platform);
   - `README.md` format table.
3. **`shared`:** the model and parser, `WebMatcher.gate`, `Packs.gate` and `Verdict.Gate(pack)`,
   and `GateCopy.title`. Tests:
   - per-pack URL tables (item, feed, lookalike hosts, `?igsh=` query strings);
   - Android fixtures;
   - parser rejects.
4. **`extension`:**
   - manifest;
   - `gateRules` for feeds;
   - gate page name and feed mode;
   - e2e rows from the table above.
5. **`androidApp`:** per-pack `GatePolicy`, the named `GateScreen`, config and `<queries>`,
   `verifyWatchedPackages`, and disclosure strings that name the three apps.
6. **Docs and version:**
   - README table;
   - CLAUDE.md, where `verifyWatchedPackages` replaces the blocked-only note;
   - 0.3.0 / versionCode 3 plus the manifest.

## Constraints / ADRs to honor
- ADR-0001: all gating stays at the UI layer; nothing network-level for these platforms (they
  are not whole-app short video).
- ADR-0003: packs are data. `feedPaths` are regexes like `gatedPaths`, with the same
  JS/Java/RE2 subset.
- ADR-0004 and ADR-0005: the service reads only these apps' view structure and the listed
  browsers' address bars. The disclosure is updated to name Instagram, Facebook and Snapchat.
- ADR-0006: gate beats block, which is unaffected. No blocked platform overlaps these hosts.
- CLAUDE.md: every rule change comes with fixtures, and Android changes are marked as
  unverified on device.

## New/updated ADR
- **ADR-0007** (new): gate packs get `displayName` and `feedPaths` (door without Continue),
  and Android allowances are per pack.

## Verification
- `./gradlew check`: new shared tests on JS + JVM, lint, `verifyWatchedPackages`.
- `./gradlew :extension:packageExtension && (cd extension/e2e && npm ci && npm test)`: every
  row of the entry-point table.
- `./gradlew :androidApp:assembleFdroidDebug :androidApp:assemblePlayDebug`.
- **Manual on the owner's desktop:** a real reel URL on instagram.com and on facebook.com, a
  Spotlight URL, and Continue landing on the single-post view.
- **Manual on a device, when available:** open the Reels tab in each app, then capture dumps
  with the debug button and fix the Android rules by rule update.

## Risks / deferred
- **The single-post continue URLs are unverified**: Instagram `/p/<reel code>/` and Facebook
  `/watch/?v=<reel id>`. If one of them bounces back into the Reels viewer, that platform
  becomes Go-back-only on the web via a rule update.
- **Instagram and Facebook change their web markup often** and use obfuscated class names;
  hide selectors are limited to `href`- and `aria`-based ones.
- **Facebook's Android app** may not be detectable without localized content descriptions.
- **Signed-out pages** show login walls instead of reels. Fixtures and e2e use synthetic
  pages; manual checks need signed-in accounts.
- **The extension's host permissions grow again**, which the Chrome Web Store listing has to
  explain.
- **Deferred:**
  - X, Reddit and LinkedIn video feeds;
  - per-platform settings;
  - reels that autoplay inline in feeds;
  - Facebook Lite (`com.facebook.lite`) and Instagram Lite.
