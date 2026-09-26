# ADR-0003 — Rule packs are versioned data, fetched from GitHub

**Date:** 2026-09-25 · **Status:** accepted

## Context
YouTube changes markup and Android view ids frequently. Shipping every fix through store
review (Chrome Web Store, Play, F-Droid) would leave users unprotected for days or weeks.
The Chrome Web Store (MV3) forbids remotely hosted *code*; remote *data* is allowed.

## Decision
- Detection rules live in `rules/<site>.json` (`schemaVersion`, `version`, `web`, `android`,
  and since ADR-0006 `block`). Every file in `rules/` is a pack, and each is refreshed
  independently.
- Every build bundles the current packs. Clients refresh about daily from
  `https://raw.githubusercontent.com/leojg/Ianua/master/rules/<site>.json`. They validate
  the pack, keep the last-known-good copy, and reject unknown `schemaVersion`s and version
  downgrades.
- Rule packs are **data only**. Web rules carry selectors, never CSS text or scripts:
  Ianua generates `… { display: none !important }` itself. Android rules are predicates
  over view ids and content descriptions.
- Every rule change comes with updated fixtures in `rules/fixtures/` that the test suite
  matches against.

## Consequences
- Merging to `master` in `rules/` is effectively a release to all users. Review it
  accordingly.
- No signing in v0.1. It relies on HTTPS plus the restricted format. Add signing before
  the format gains any more expressive power.
- Clients make one outbound request per day to GitHub. No user data is sent.
