# Ianua

*Ianua* is Latin for "door". Ianua puts a door in front of short-form video. It does not
block Shorts outright: to get in, you stop, read a prompt, wait 10 seconds, and choose to
continue.

Platforms that are *nothing but* short videos (TikTok, Douyin, Kuaishou, Kwai, Likee, Triller,
Moj, Josh) get no door: they are **blocked outright**, as apps on Android and as sites in the
browser.

Ianua covers **YouTube Shorts** and the block list on two surfaces:

| | What it does |
|---|---|
| **Chromium extension** (Chrome, Brave, Chromium, Edge) | Blocks the whole sites of blocked platforms. Hides Shorts shelves, sidebar entries, grid/search results and channel Shorts tabs. Opening a Short (link, typed URL or in-page navigation) shows the gate. *Continue* opens the video in the regular player, so the swipe feed never appears. |
| **Android app** | An accessibility service notices the Shorts player in the YouTube app, or a Shorts address in Brave, Chrome, Firefox, Edge or Samsung Internet. It silences playback and shows the gate. *Continue* unlocks Shorts for 5 minutes. Opening a blocked app sends you to the home screen; a blocked site in a browser goes back. |

Everything runs locally. The only network request is a daily download of updated detection
rules from this repository (`rules/`).

## Layout

| Path | What |
|---|---|
| `rules/` | Rule packs, one per file: `youtube.json` (the Shorts gate), `blocked.json` (the block list). Data only. See [`rules/README.md`](rules/README.md). |
| `shared/` | Kotlin Multiplatform: rule parsing and validation, matchers, gate policy, rule updates. |
| `extension/` | The MV3 extension in Kotlin/JS (`static/` holds the manifest and pages). `e2e/` holds the Playwright smoke test. |
| `androidApp/` | Android app: Jetpack Compose UI, accessibility service, gate overlay. Flavors `play` and `fdroid`. |
| `docs/adr/` | Binding architecture decisions. |
| `docs/specs/` | Specs for work in progress. |

## Build

Requires JDK 21 and the Android SDK (`local.properties` → `sdk.dir=…`). Node 22 is needed
for the end-to-end test only.

```bash
./gradlew check                              # all tests (JS + JVM) and Android lint
./gradlew :extension:packageExtension        # unpacked extension → extension/build/dist
./gradlew :extension:zipExtension            # Chrome Web Store zip → extension/build/store
./gradlew :androidApp:assembleFdroidDebug    # or assemblePlayDebug

cd extension/e2e && npm ci && npm test       # drives the built extension in Chromium
```

To try the extension, open `chrome://extensions`, enable *Developer mode*, choose *Load
unpacked* and select `extension/build/dist`.

To try the app, install the APK, open Ianua, read the disclosure and enable *Ianua Shorts
gate* in Accessibility settings. For an APK installed outside Google Play on Android 13+,
first allow restricted settings (App info → ⋮).

## Releasing

The version lives in two places, which must match: `versionName` in
`androidApp/build.gradle.kts` and `version` in `extension/static/manifest.json`. On every
merge to `master`, the *Tag release* workflow tags the merge commit `v<version>`, unless that
tag already exists. To release, bump both (and `versionCode`) in the PR.

## License

GPL-3.0. See [LICENSE](LICENSE).
