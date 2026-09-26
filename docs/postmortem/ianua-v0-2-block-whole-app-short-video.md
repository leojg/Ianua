# Postmortem: Ianua v0.2 — Block whole-app short-video platforms

| | |
|---|---|
| **Date** | 2026-09-26 |
| **Spec** | `docs/specs/ianua-v0-2-block-whole-app-short-video.md` |
| **Backlog** | — |
| **Repos** | `leojg/Ianua` — rule packs, shared core, extension, Android app |
| **Commits** | `9fe2111`, `833ce46`, `903dd56`; merged in leojg/Ianua#2 (`3400077`), tagged `v0.2.0` |
| **Publishable** | yes — open-source project; nothing sensitive. The lessons are about the Kotlin/JS compiler and browser extension platform rules |

## Summary
The goal was to hard-block platforms that are nothing but short-form video (TikTok, Douyin,
Kuaishou, Kwai, Likee, Triller, Moj, Josh). The block had to cover the browser extension,
Android apps and Android browsers, through a new `block` rule type, while every file in
`rules/` becomes a pack. All of it shipped. The owner confirmed the extension in desktop
Brave; the Android block flow has **not** been run on a device.

Along the way, the new e2e tests exposed **two bugs that had been in v0.1 all along**:
- remote rule updates were never read;
- Shorts links clicked from other sites hit an error page.

**Faithful** to the spec's approach, with one mechanism corrected by its own test.

## Spec vs. shipped
| Area | Planned | Shipped | |
|---|---|---|---|
| ADR | ADR-0006, ADR-0003 note | both, plus corrections to ADR-0002 and the v0.1 postmortem | same + added |
| Rule pack | `blocked.json`, `block` section | 8 platforms; name, domain and package validation | same |
| Multi-pack | every `rules/` file is a pack | `RuleRepository.bundledIds`, `Packs` aggregate | same |
| Gate beats block | overlap rejected | `Packs` drops overlaps; also never blocks a listed browser | same + stricter |
| Extension DNR | redirect (p2) over block (p1) | redirect only for domains with host permission | changed |
| Blocked page | name, Go back, no Continue | as planned | same |
| Android apps | package-only detection → HOME + card | as planned; card auto-closes after 4 s | same |
| Android browsers | address bar → BACK, escalate to HOME | as planned | same |
| Installed list | `<queries>` + main screen | as planned, plus a Gradle check that keeps it in sync | same + added |
| Version | 0.2.0 / code 2 | tagged by the workflow on merge | same |

## Divergences (approach-level)
- **Redirect only where it can apply.** The spec asked the e2e test to confirm that, for a
  domain without host permission, the redirect does not shadow the block. It does shadow it:
  the redirect wins the match, is not applied, and the site loads. Redirect rules are now
  built only for domains that `chrome.permissions.contains` confirms. The plain block rule
  covers everything else. The spec had flagged exactly this as an open question, and the test
  it requested answered it.

## Design shifts along the way
- **The extension had never read a fetched rule pack.** The rule-update e2e test failed. A Node
  harness then ran the production bundle against a fake `chrome` API and showed that
  `chrome.storage.local.get(…).await()[key]` compiled **without a suspension point**. This is a
  Kotlin/JS (2.4) compiler bug: a suspend call whose `dynamic` result is indexed in the same
  expression. The code read `null` and fell back to the bundled pack; the promise later resumed
  a finished coroutine ("This continuation is already complete"). `ChromeRuleStore.load` had
  this shape since v0.1. It is also the likely real cause of v0.1's "kotlinx-coroutines
  dispatcher crash". The fix is to assign to a local before indexing. The pitfall is
  documented in `extension/.../Async.kt` and `CLAUDE.md`, and ADR-0002 is corrected.
  One wrong hypothesis (shared suspend-lambda instances) was tested and reverted.
- **Links from other sites hit an error page.** This was device feedback: clicking TikTok in
  DuckDuckGo briefly showed Brave's `ERR_BLOCKED_BY_CLIENT` page; in plain Chromium the
  navigation stopped there. The browser checks `web_accessible_resources` against the site the
  click comes *from*, and the pages were only exposed to YouTube or the blocked domains. Shorts
  links from other sites had failed this way **since v0.1**, because every e2e test typed URLs
  directly. Both pages are now exposed to `<all_urls>`, which lets sites detect that Ianua is
  installed. Two e2e tests click through from a third-party page.
- **Gate URLs carry the pack id** (`&s=youtube`), so v0.3 can have more than one gate pack.
- **`GateOverlay` became a generic `ComposeOverlay`**, used by both the gate and the block card.
- **A refresh republishes through `storage.onChanged`** instead of calling publish directly,
  so a saved pack always reaches the DNR rules.

## Dropped / deferred / added
- **Added unplanned:**
  - `:androidApp:verifyBlockedPackages` in `check`, because a package in `blocked.json` is
    useless on Android without `<queries>` and the accessibility-config entry;
  - `Packs` refuses to block a listed browser's package, which would lock the browser;
  - two third-party-link e2e tests.
- **Deferred:**
  - verifying package names and domains against real installs;
  - the Android block flow on a device;
  - in-app browsers;
  - fingerprinting mitigation (`use_dynamic_url`).
- **Dropped:** nothing.

## Verification
- **Planned:** shared tests, e2e with four block tests, both APKs, and device checks.
- **Run:**
  - `./gradlew check`: 57 tests on JS + 57 on JVM, lint 0 errors, `verifyBlockedPackages`
    (also negative-tested with a planted package);
  - e2e 13/13: the v0.1 tests plus block page, short links and bare domains, Go back, a
    remotely added domain without host permission, and two third-party-link tests;
  - both flavors built, with no GMS or Firebase.
- **On devices:** the owner confirmed the extension in desktop Brave. Android was not tested
  on a phone, and no emulator is available in the build environment.

## Lessons for the next spec
- **Test the path users actually take, not the one that is easy to script.** Typing a URL
  and clicking a link from another site hit different platform rules. The second was broken
  for two releases while every test was green. v0.3's e2e plan should list entry points
  (typed, in-page, cross-site link, rule update) per behaviour.
- **Each feature needs a test that exercises its data path end to end.** The remote update
  channel had a unit test (`RuleRepository`) but no test through the real storage layer, so
  "it never worked" went unnoticed for a release. Every channel in the spec should name its
  end-to-end check.
- **Keep a runtime harness for the extension.** The Node harness turned a Chromium-only
  failure into a two-second loop and found a compiler bug. It belongs in `extension/e2e/`
  rather than a scratch directory.
