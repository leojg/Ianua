# Ianua v0.2 — Block whole-app short-video platforms

**Date:** 2026-09-25
**Status:** plan, awaiting review; then `/implement`.
**Builds on:** ADR-0001 (UI-layer blocking; network-level allowed for whole-app platforms),
ADR-0003 (rule packs), ADR-0004 (Android service), ADR-0005 (browser address bar).
**Next:** v0.3 extends the existing *gate* rules to Instagram Reels, Facebook Reels,
Snapchat Spotlight and similar. That is not in scope here.

## Context
Some platforms are *nothing but* short-form video: TikTok, Likee, Moj, Josh and others.
There is no "regular video" to fall back to and no section to single out, so a door makes
no sense. v0.2 adds a second behaviour next to the Shorts gate: a **hard block**. The whole
app on Android, and the whole site in the browser and in Android browsers, is blocked.

This is new functionality with a new rule type, so it is a locked-area change (rule-pack
format) and gets ADR-0006.

## Affected repos
| repo path | role | docs/ADRs to respect |
|---|---|---|
| `leojg/Ianua` | rule-pack format, shared core, extension, Android app | `CLAUDE.md`; ADR-0001, -0002, -0003, -0004, -0005; new ADR-0006 |

## Decisions taken (user)
| Topic | Decision |
|---|---|
| Behaviour | **Hard block.** No countdown, no Continue, no daily pass. The only way through is switching Ianua off. |
| Configurability | **All or nothing.** The existing main switch covers the Shorts gate and the block list. There are no per-platform switches in v0.2. |
| Block list | **Every known whole-app platform**: TikTok (+ Lite), Likee, Triller, Moj, Josh, Douyin, Kuaishou/Kwai. Entries are data, so extra ones cost nothing. |
| Split | v0.2 = whole-app blocking; v0.3 = more platforms for the existing gate. |

## Approach

### Rule pack: a new `block` section and a new pack (ADR-0006)
`rules/blocked.json`, `schemaVersion` 1, uses a new optional top-level section. It is
backward compatible: v0.1 clients ignore unknown keys, and the bundled-only fallback keeps
them working.

```json
{
  "schemaVersion": 1,
  "id": "blocked",
  "version": 2026092601,
  "block": {
    "platforms": [
      {
        "name": "TikTok",
        "domains": ["tiktok.com", "tiktokv.com"],
        "androidPackages": ["com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"]
      }
    ]
  }
}
```

- `domains` match the domain **and all subdomains**: `tiktok.com` covers `www.`, `m.`,
  and the `vm.`/`vt.` short links. This is the same semantics as DNR `requestDomains`.
- `name` is shown on the block screen ("TikTok is blocked").
- **Parser validation:** non-empty `platforms`; each entry has a name and at least one domain
  or package. Domains are plain hostnames (the existing `HOST` regex) and must not be a
  public suffix alone (reject single-label entries). No package or domain may also appear
  in a `gate` rule pack.

### Multiple rule packs
v0.1 hardcodes `RuleRepository.YOUTUBE`. v0.2 treats **every file in `rules/`** as a pack:
`BundledRules.all.keys` is already generated from the directory. Each id gets its own
`RuleRepository` and its own daily refresh. A pack that exists only remotely is not picked
up; a new pack needs an app release, which is acceptable because new *kinds* of rules need
new code anyway. Adding domains or packages to `blocked.json` needs no release.

### Web (extension)
- **DNR rules, one pair per blocked platform:**
  1. `redirect` (priority 2) of `main_frame` requests for `requestDomains` to
     `blocked.html?p=<name>`. A redirect needs host permission for the site, so the
     manifest lists the v0.2 domains in `host_permissions`. The Chrome Web Store reviews
     that list.
  2. `block` (priority 1) for the same domains, `main_frame` + `sub_frame`. A block needs
     no host permission, so a domain added later through a remote rule update is still
     blocked, showing Chrome's own "blocked" page, until the next release adds it to the
     manifest.
  The e2e test must confirm that for a domain without host permission the redirect does
  not shadow the block rule.
- `blocked.html`: "<Name> is blocked", one line of copy, a *Go back* button (history back,
  or close/leave for a fresh tab). There is no Continue.
- No content script on blocked sites. The whole navigation is stopped before any page code
  runs.
- The main switch off removes the block rules along with the Shorts redirects
  (`syncRedirects` becomes `syncDnrRules`, built from all packs).

### Android
- **Apps:** the service adds every `androidPackages` entry to its `packageNames`. On
  `TYPE_WINDOW_STATE_CHANGED` from a blocked package it:
  - calls `GLOBAL_ACTION_HOME`;
  - shows a block overlay ("<Name> is blocked", *OK*) for a few seconds or until tapped.
  It needs only `event.packageName` and reads nothing on screen. The disclosure says so.
- **Browsers:** the address-bar check from ADR-0005 also matches `block.domains`. A match
  shows the same overlay and calls `GLOBAL_ACTION_BACK`. If the page is still in front on
  the next event, it repeats; after two failed backs it goes `HOME`.
- `GatePolicy` is untouched. Blocking is stateless, with no allowance.
- **Detecting installs:** the main screen lists which blocked apps are installed, so the
  user sees the block is active. This needs `<queries>` entries for the blocked packages;
  `QUERY_ALL_PACKAGES` is not requested.

### Shared
- `RulePack.block: BlockRules?` with `BlockPlatform(name, domains, androidPackages)`.
- `BlockMatcher`:
  - `platformForUrl(url)`, subdomain-aware;
  - `platformForAddressBar(text)`, which reuses the scheme-less handling;
  - `platformForPackage(pkg)`;
  - `dnrDomains()`.
- `AndroidMatcher` and `WebMatcher` stay per pack. A small `Packs` aggregate in shared
  combines all active packs, so each platform module asks one object "gate, block, or
  nothing?".

## Steps
1. **ADR-0006** `docs/adr/0006-hard-block-whole-app-platforms.md`: the new behaviour, the
   `block` section, the DNR redirect + block pair and why, and Android's package-only
   detection.
2. **`rules/`:**
   - `blocked.json` with all platforms;
   - `README.md` format table;
   - `fixtures/android/brave_tiktok.json`.
3. **`shared`:** model, parser validation, `BlockMatcher`, `Packs`, multi-pack
   `RuleRepository` wiring. Tests:
   - parsing and rejection;
   - subdomain matching (`vm.tiktok.com` yes; `nottiktok.com`, `tiktok.com.evil.test` no);
   - address bar;
   - package lookup;
   - overlap validation.
4. **`extension`:**
   - iterate all packs in `Background.kt` / `State.kt`;
   - `syncDnrRules`;
   - `blocked.html` + `Blocked.kt` (dispatched from `Main.kt`);
   - manifest `host_permissions`;
   - popup copy ("Gate Shorts, block short-video apps").
   e2e tests:
   - `https://www.tiktok.com/@x/video/1` → blocked page;
   - `vm.tiktok.com` short link → blocked;
   - a domain added only through a patched pack (no host permission) → request blocked;
   - switch off → site loads.
5. **`androidApp`:**
   - `Rules` holds all packs;
   - the service handles block events;
   - `BlockOverlay` (Compose, reusing the overlay owner from `GateOverlay`);
   - the accessibility config's `packageNames` default;
   - `<queries>`;
   - the main-screen "blocked apps installed" list;
   - disclosure and strings.
6. **Docs:**
   - `README.md` table;
   - `CLAUDE.md` layout line for multiple packs;
   - bump `versionName` 0.2.0, `versionCode` 2 and the manifest `version` together (the tag
     workflow enforces it).

## Constraints / ADRs to honor
- ADR-0001: network-level blocking stays banned for YouTube. DNR domain blocking is allowed
  here *because* these platforms are short-form in their entirety.
- ADR-0003: `blocked.json` is data only: names, domains, packages.
- ADR-0004: no new Android permissions beyond `<queries>`; nothing leaves the device.
- ADR-0005: browser handling reuses the address-bar path.
- The CLAUDE.md fixture rule: `brave_tiktok.json`, plus DNR e2e coverage in place of web
  fixtures.

## New/updated ADR
- **ADR-0006** (new): hard-block rule type for whole-app platforms.
- ADR-0003 gains a one-line note that packs are now all files in `rules/`.

## Verification
- `./gradlew check`: new shared tests on JS + JVM, lint.
- `./gradlew :extension:packageExtension && (cd extension/e2e && npm test)`: all v0.1
  tests plus the four block tests above.
- `./gradlew :androidApp:assembleFdroidDebug :androidApp:assemblePlayDebug`. Then on a device:
  1. install TikTok (or any listed app) and open it: you land on the home screen with the
     block card;
  2. open `tiktok.com` in Brave: the block card, then back;
  3. switch Ianua off: TikTok opens;
  4. v0.1 Shorts gating is unchanged.
  An emulator is not available in the build environment, so device checks are manual.

## Risks / deferred
- **Package and domain lists are best-effort.** Regional builds (e.g. TikTok's `trill`,
  Kwai vs Kuaishou) and short-link domains need checking. Wrong entries fail open, not
  closed.
- **CWS review of broad `host_permissions`.** Seven platforms' domains is more than a
  Shorts-only extension needed. The listing text must explain it.
- **Circumvention** (a different browser, a different launcher, PWAs) is out of scope for
  "adults, self-control".
- **In-app browsers** (e.g. a TikTok link opened inside another app's WebView) are not
  covered; deferred.
- **Deferred to v0.3:** Instagram Reels, Facebook Reels, Snapchat Spotlight, X / Reddit /
  LinkedIn video feeds, per-platform settings, and naming the platform in the gate prompt.
