# ADR-0006 — Hard-block whole-app short-video platforms

**Date:** 2026-09-26 · **Status:** accepted · **Extends:** ADR-0001, ADR-0003, ADR-0004, ADR-0005

## Context
Some platforms are nothing but short-form video (TikTok, Likee, Moj, Josh, Douyin,
Kuaishou/Kwai, Triller). There is no regular content to fall back to, so the Shorts *door*
(prompt, countdown, allowance) makes no sense there. The owner's decision: block them
outright.

## Decision
- **A new rule type.** A rule pack may carry a `block` section:
  `{ "platforms": [{ "name", "domains", "androidPackages" }] }`. `rules/blocked.json` holds
  the list. A domain matches itself and every subdomain (DNR `requestDomains` semantics).
- **Hard block.** No countdown, no Continue, no pass. The main switch turns it off together
  with the Shorts gate; there are no per-platform switches.
- **Every file in `rules/` is a pack** (amends ADR-0003). Each is refreshed independently.
  New entries in `blocked.json` ship with a rule update; a new pack file needs a release.
- **Web: a DNR pair per platform.** A `redirect` (priority 2) sends `main_frame` requests to
  the extension's `blocked.html`. It needs host permission, so v0.2's domains are in the
  manifest. A `block` (priority 1) on `main_frame` + `sub_frame` needs no host permission,
  so a domain added later by a rule update is still blocked (Chrome's own error page) until
  a release adds it to the manifest. This is network-level blocking, which ADR-0001 allows
  for platforms that are short-form in their entirety.
- **Android apps: package name only.** A window from a blocked package triggers
  `GLOBAL_ACTION_HOME` and a block card. Nothing on screen is read.
- **Android browsers:** the ADR-0005 address-bar path also matches blocked domains. The
  result is the block card and `GLOBAL_ACTION_BACK`, escalating to `HOME` if back does not
  leave the page.
- **Gate beats block.** A domain or package that a gate pack covers is never blocked, even
  if a `block` entry lists it (`Packs` drops it). YouTube can therefore never end up
  network-blocked through a rule update.

## Consequences
- The extension's host permissions grow to the blocked platforms' domains, which the
  Chrome Web Store reviews and the listing must explain.
- Wrong or missing package names and domains fail open: the app or site is not blocked.
- Links opened in other apps' in-app browsers are not covered.
