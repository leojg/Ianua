# ADR-0004 — Android: AccessibilityService, local-only, no Google Play Services

**Date:** 2026-09-25 · **Status:** accepted

## Context
Gating Shorts inside the native YouTube app requires knowing what is on screen in another
app. On Android, only an AccessibilityService can do this. Ianua is distributed on both
Google Play and F-Droid.

## Decision
- `IanuaAccessibilityService`, scoped via `packageNames` to the target apps (v0.1:
  `com.google.android.youtube`), `isAccessibilityTool=false`. The gate is a
  `TYPE_ACCESSIBILITY_OVERLAY` window, so no `SYSTEM_ALERT_WINDOW` permission is needed.
- A prominent in-app disclosure comes before the user is sent to Accessibility settings.
  Screen content is evaluated in memory and never stored or transmitted. The debug-only
  fixture dump tool is the one exception, and it is user-triggered.
- **No Google Play Services or Firebase** in any flavor. Everything is AndroidX:
  DataStore, WorkManager. This keeps F-Droid eligibility.
- Product flavors `play` and `fdroid`. Anything Play-only (e.g. a future Play Billing paid
  tier) goes only in `play`.
- minSdk 26: `java.time` without desugaring, adaptive icons, notification channels, about
  97% of active devices. Nothing in v0.1 needs a higher API.

## Consequences
- Google Play review: Permissions Declaration Form, prominent disclosure and a demo video.
  Rejection risk is real but mitigable. Comparable blockers are listed.
- Sideloaded installs (F-Droid) on Android 13+ need "Allow restricted settings" before the
  service can be enabled. Onboarding must explain this.
- Android 16 Advanced Protection mode disables non-accessibility-tool services. Users with
  that mode on cannot use Ianua.
