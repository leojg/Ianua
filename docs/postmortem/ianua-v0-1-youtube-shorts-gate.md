# Postmortem: Ianua v0.1 — YouTube Shorts gate (Chromium extension + Android)

| | |
|---|---|
| **Date** | 2026-09-25 |
| **Spec** | `docs/specs/ianua-v0-1-youtube-shorts-gate.md` (+ follow-up `docs/specs/android-browser-shorts-gate.md`) |
| **Backlog** | — |
| **Repos** | `leojg/Ianua` — the whole product (rules, shared KMP core, extension, Android app) |
| **Commits** | `e5cd117..ca43ea8`, merged in leojg/Ianua#1 (`6811d5b`) |
| **Publishable** | yes — open-source project, no client, pricing or secret data; the lessons are about platform runtimes |

## Summary
The goal was a "door" in front of YouTube Shorts: a prompt with a 10 s countdown instead of
a hard block. It had to work as a Chromium MV3 extension and in the Android YouTube app,
from one Kotlin Multiplatform codebase and one data-only rule pack. Both shipped. After
on-device feedback, one feature the spec never planned was added: gating Shorts in Android
browsers through the address bar.

The architecture held: UI-layer blocking, rule packs as remote data, Kotlin everywhere, an
accessibility service with no Google Play Services. Four implementation mechanisms had to
change, and each change was forced by a real runtime, not by review. **Minor drift** overall.

## Spec vs. shipped
| Area | Planned | Shipped | |
|---|---|---|---|
| Rule pack | `rules/youtube.json`, schema 1, web + android | as planned; `android.browsers` added later (schema still 1) | same + added |
| Shared core | parser, matchers, GatePolicy, RuleRepository, tests on both targets | as planned; 46 tests × (JS + JVM) | same |
| Extension layout | 4 Kotlin/JS modules (background, content, gate, popup) | 1 module, 1 bundle, `Main.kt` dispatches per context | changed |
| Extension redirects | static DNR ruleset | dynamic DNR rules generated from the rule pack | changed |
| Extension async | kotlinx-coroutines | stdlib `startCoroutine` + a `Promise.await` | changed |
| Extension UI | kotlinx-html popup + gate page | as planned | same |
| Android service | scoped AccessibilityService, overlay gate, 5 min allowance | as planned | same |
| Android audio | `KEYCODE_MEDIA_PAUSE`/`PLAY` | transient audio focus + mute fallback (`GateAudio.kt`) | changed |
| Android onboarding | disclosure, restricted-settings hint | as planned | same |
| Rule refresh | daily fetch from GitHub | as planned; URL fixed from `/main/` to `/master/` | same (bug fixed) |
| CI | check + builds | + GMS guard, + e2e, + tag-on-merge workflow | added |

## Divergences (approach-level)
- **The extension doesn't use kotlinx-coroutines.** The spec assumed it. Inside the MV3
  service worker, its JS dispatcher threw `CoroutinesInternalError` (a continuation
  released twice) on the install path, so the redirect rules silently never installed.
  The same code passed under Node. Found by attaching over the Chrome DevTools Protocol to
  the service worker, after Playwright's worker console and `evaluate` gave misleading
  results. Replaced with ~20 lines of stdlib coroutines, which is all the extension needs:
  every suspension is a chrome.* promise. The bundle shrank from ~316 KB to ~253 KB.
  Recorded in ADR-0002.
- **One extension bundle, not four modules.** This was chosen at implementation time for
  build simplicity: one Gradle module and one set of `chrome.*` bindings.
- **Dynamic DNR rules instead of a static ruleset.** The static ruleset would have
  duplicated the gated-path regex from `rules/`. Generating the rules from the rule pack
  keeps `rules/` the single source (ADR-0003).
- **Audio focus instead of media keys.** Found on a real device: the Short kept playing,
  unseen, behind the gate. The media key goes to whichever app last played media, so it
  missed the Shorts player, and it could also have paused or started an unrelated app.
  Transient audio focus pauses well-behaved players. If playback continues after 400 ms,
  the media stream is muted until the gate closes.

> **Correction (2026-09-26, v0.2):** the coroutine crash above was most likely not
> kotlinx's dispatcher. It was a Kotlin/JS compiler bug: `promise.await()[key]` on a
> `dynamic` compiles without a suspension point. `ChromeRuleStore.load` had that shape, so
> v0.1's extension also never read a fetched rule pack. See
> `docs/specs/ianua-v0-2-block-whole-app-short-video.md`, Implementation notes.

## Design shifts along the way
- **The Android matcher works on a `ScreenNode` interface, not on an `AccessibilityNodeInfo`
  snapshot.** It is lazy on device and serializable in tests, which puts the matcher in
  `commonTest`, and it gained `findByViewId` for browsers, whose trees include the whole
  page (`shared/.../AndroidMatcher.kt`).
- **Selectors-only hiding, one CSS rule per selector.** A fetched pack can't inject CSS,
  and a selector a browser doesn't support only disables itself (`RulePackParser`,
  `WebMatcher.hideCss`).
- **The allowance has a leave-grace period** (3 s). Accessibility events flicker during
  transitions, and one "not Shorts" frame would otherwise re-gate (`GatePolicy`).
- **Debug dumps keep text only for address bars.** Dumps become committed fixtures, and
  browser pages contain personal text (`NodeSnapshot.copyOf(keepTextOf)`).

## Dropped / deferred / added
- **Added unplanned:** Android browser gating via the address bar. Brave on Android has no
  extensions, and it is the owner's YouTube browser. Scoped with its own spec and ADR-0005
  before building it.
- **Added unplanned:** a tag-on-merge workflow that requires the Android and extension
  versions to match.
- **Deferred:** real fixtures (all are still synthetic); verifying the YouTube view ids and
  hide selectors against the live app and site; browser address-bar ids (Firefox is the
  most doubtful); targetSdk 37; rule-pack signing; store submission (Play accessibility
  declaration, F-Droid and developer verification).
- **Dropped:** nothing from the spec.

## Verification
- **Planned:** shared tests, a Playwright smoke test of the extension, both APK builds, a
  GMS dependency check, and manual device checks.
- **Run in the build environment:**
  - `./gradlew check`: 46 tests on JS + 46 on JVM, Android lint 0 errors;
  - the e2e test, 6/6 in Chromium: hard-load gate, mobile-web gate, the 10 s lock then
    `/watch`, shelves hidden, in-page navigation gated with Go back, toggle off;
  - both flavors built, including a minified release;
  - no GMS or Firebase dependencies.
- **Not runnable there:** no KVM, so no emulator.
- **Owner tested on devices:** desktop Brave works. Android works, apart from the audio
  bug, which was then fixed. The browser gating was accepted, but its on-device check is
  not recorded.
- **The e2e test paid for itself:** DNR `regexSubstitution` replaces only the matched part
  of the URL, so `?feature=share` leaked into the gate URL and broke the id check. No unit
  test could see it.

## Lessons for the next spec
- **Budget a real-runtime check for every platform seam, early.** All four mechanism
  changes came from a real runtime (MV3 service worker, DNR, an Android media session)
  while the pure-logic tests stayed green throughout. The next spec should name, per
  surface, the smoke test that runs the actual runtime, and say when there is none (as
  with Android here) so device testing is planned rather than discovered.
- **Ask which browsers and devices the owner actually uses.** "Web = extension" missed that
  the owner watches YouTube in a mobile browser with no extension support. One question up
  front would have scoped browser gating into v0.1.
- **Check repo facts before writing them into code.** The spec hardcoded `main` as the
  branch; the repo's default is `master`. Remote updates would have 404'd silently.
