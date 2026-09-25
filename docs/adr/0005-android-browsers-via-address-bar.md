# ADR-0005 — Gate Shorts in Android browsers by reading the address bar

**Date:** 2026-09-25 · **Status:** accepted · **Extends:** ADR-0003, ADR-0004

## Context
Mobile browsers on Android mostly have no extension support: Brave and Chrome have none,
and Firefox's is limited. People who watch YouTube in a browser, e.g. Brave for its ad
blocking, bypass the extension entirely. The Android accessibility service can see a
browser's address bar.

## Decision
- The rule pack's `android` section gains an optional `browsers` list:
  `{ "package": …, "urlBarViewIds": [ … ] }`. Anyone can add or fix a browser through a rule
  change; no app release is needed.
- For a listed browser, the service reads the address-bar text, but only while the bar is
  not focused, so text the user is still typing never triggers the gate. It matches that
  text with the **web** rules (`hosts` + `gatedPaths`). One definition of "a Short" serves
  every surface.
- The gate is the same as in the YouTube app: overlay, audio silenced, *Go back* (browser
  back) or *Continue* (5-minute allowance). A browser cannot be redirected from outside, so
  there is no "regular player" continue.
- The address bar is looked up by view id (`findAccessibilityNodeInfosByViewId`), not by
  walking the tree, because a browser's accessibility tree includes the whole web page.

## Consequences
- The service now receives events from the listed browsers. It reads only the address-bar
  text of those browsers, evaluates it in memory, and never stores or sends it (ADR-0004
  still holds). The in-app disclosure says so explicitly.
- Shorts shelves on m.youtube.com stay visible in mobile browsers. An accessibility service
  cannot hide page elements. Only the Shorts player is gated.
- Browsers that show only the domain in the address bar (e.g. Chrome with URL eliding)
  cannot be gated this way. The address-bar ids are unverified until captured with the
  debug dump.
- Adding `browsers` is backward compatible (older clients ignore unknown keys), so
  `schemaVersion` stays 1.
