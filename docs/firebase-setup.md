# Firebase: Analytics + Crashlytics

The code is already wired. Firebase switches on when `app/google-services.json` exists; without
it the app builds and runs normally and sends nothing. The file is git-ignored.

## One-time setup (Firebase console)

1. https://console.firebase.google.com → **Add project** → name "Frame" → keep **Google Analytics** on.
2. **Add app → Android**, package name `app.frame.studio` (release).
3. **Add app → Android** again, package name `app.frame.studio.debug` (debug builds, for testing).
4. Download `google-services.json` (it contains both apps) → put it at `app/google-services.json`.
5. Console → **Crashlytics** → "Enable Crashlytics".

Any build type whose package is not in the file (the benchmark build `app.frame.studio.bench`,
or debug until `app.frame.studio.debug` is registered) builds without Firebase and sends nothing.

## What is sent

| Build | Collection |
|---|---|
| release | on |
| debug | off, unless built with `-Pframe.telemetryInDebug=true` |
| benchmark | off |

Analytics events (all in `telemetry/AppEvents.kt`): `screen_view` (per screen), `project_created`
(source = blank / magic_movie / template), `export_started`, `export_completed`, `export_failed`,
`export_cancelled`, `magic_movie_created`, `magic_movie_failed`, `template_used`,
`import_refused`, `pro_purchase_started`, `pro_purchased`. User property: `device_tier`.

Crashlytics: all crashes, plus non-fatal reports for failed exports, preview failures, project
save failures, Magic Movie/template failures, preview-proxy failures and unexpected background
errors (key `where` tells which).

Privacy: only counts, sizes and settings. No project names, text, or file paths (the
`TelemetryRules` filter drops anything that looks like a file or link), and Analytics does not use
the advertising ID. Play Console → Data safety should declare: App activity (app interactions),
App info and performance (crash logs, diagnostics), Device or other IDs — collected, not shared,
for analytics.

## Testing it

```bash
./gradlew installDebug -Pframe.telemetryInDebug=true
adb shell setprop debug.firebase.analytics.app app.frame.studio.debug   # live events in DebugView
```

Open the app, move between screens and export a video; events appear in Firebase console →
Analytics → **DebugView** within seconds. Crash reports appear in Crashlytics after the app is
restarted following a crash. Turn DebugView off with
`adb shell setprop debug.firebase.analytics.app .none.`
