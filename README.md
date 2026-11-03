# Frame

A lightweight Android video editor built with Jetpack Compose and Media3.
Exports never carry a watermark.

## Features

- **Projects** – create, rename, duplicate and delete; autosaved as JSON in app storage.
- **Video track** – import multiple clips, trim, split at the playhead, reorder, duplicate,
  delete, speed (0.5×–2×), clip volume, fade in/out, rotate, crop presets (1:1, 4:5, 9:16, 16:9).
- **Music track** – add audio files, trim, split, reorder, volume, fades. Music is automatically
  cut to the length of the video.
- **Text overlays** – timed captions with position control.
- **Transitions** – "fade between clips" (dip to black, does not change duration).
- **Preview** – synced video + music playback with a scrubbable, snapping timeline.
- **Undo / redo** for every edit.
- **Export** – H.264/AAC MP4 at 720p or 1080p (short side), progress + cancel, optional copy to
  `Movies/Frame`, share and export history.
- **Ads** – anchored adaptive AdMob banner, shown only after Google UMP consent
  (GDPR / US state privacy). "Privacy choices" appears in the menu when required.

## Requirements

- Android Studio Narwhal (or newer) / AGP 8.13, JDK 17
- compileSdk / targetSdk 36, minSdk 26

## Build

```bash
./gradlew testDebugUnitTest        # unit tests
./gradlew assembleDebug            # debug APK (applicationId app.frame.studio.debug, test ads)
./gradlew bundleRelease            # Play Store bundle
```

## Release configuration

Secrets are never committed. Copy `keystore.properties.example` to `keystore.properties`
(git-ignored) or set the equivalent environment variables:

| keystore.properties | Environment variable       | Purpose                        |
|---------------------|----------------------------|--------------------------------|
| `storeFile`         | `FRAME_KEYSTORE_PATH`      | Upload keystore (relative to repo root) |
| `storePassword`     | `FRAME_KEYSTORE_PASSWORD`  |                                |
| `keyAlias`          | `FRAME_KEY_ALIAS`          |                                |
| `keyPassword`       | `FRAME_KEY_PASSWORD`       |                                |
| `admobAppId`        | `FRAME_ADMOB_APP_ID`       | AdMob app ID (`ca-app-pub-…~…`) |
| `admobBannerId`     | `FRAME_ADMOB_BANNER_ID`    | AdMob banner unit (`ca-app-pub-…/…`) |

Without AdMob IDs a release build falls back to Google's **test** ad units and Gradle prints a
warning. Without a keystore the release outputs are unsigned.

Version numbers: `./gradlew bundleRelease -Pframe.versionCode=2 -Pframe.versionName=1.0.1`.

Create an upload key once:

```bash
keytool -genkeypair -v -keystore release.jks -alias frame -keyalg RSA -keysize 4096 -validity 10000
```

## CI

`.github/workflows/android.yml` runs unit tests, lint and builds the release APK + AAB on every
push and pull request, uploading them (plus the R8 `mapping.txt`) as artifacts. To sign in CI
add these repository secrets: `FRAME_KEYSTORE_BASE64` (`base64 -w0 release.jks`),
`FRAME_KEYSTORE_PASSWORD`, `FRAME_KEY_ALIAS`, `FRAME_KEY_PASSWORD`, `FRAME_ADMOB_APP_ID`,
`FRAME_ADMOB_BANNER_ID`.

A second job boots an Android 14 emulator, launches the app (`scripts/smoke-test.sh`) and runs
`ExportPipelineTest`, which renders a project using every editing feature through Media3 and
checks the output's duration, resolution and audio. Logs from both jobs are mirrored to the
`ci-results` and `ci-smoke` branches.

## Play Store checklist

- [ ] Real AdMob app + banner IDs configured; `app-ads.txt` published on your developer website.
- [ ] GDPR message and US state regulations message created in AdMob → Privacy & messaging.
- [ ] Privacy policy URL (`privacy_policy_url` in `strings.xml`) is live and mentions AdMob.
- [ ] Data safety form: device/advertising ID and approximate location collected by AdMob for ads;
      no user content leaves the device.
- [ ] Content rating questionnaire, target audience (not designed for children), ads declaration: yes.
- [ ] Upload `mapping.txt` with each release for readable crash reports.

## Architecture

```
app/src/main/java/app/frame/studio
├── FrameApp.kt / MainActivity.kt   app wiring, consent, keep-screen-on during export
├── domain/        serializable project model
├── timeline/      pure timeline operations and time mapping (unit tested)
├── data/          ProjectRepository (atomic JSON files), MediaProbe (metadata + thumbnails)
├── export/        ExportPlan (pure, unit tested), CompositionFactory (Media3 Composition),
│                  GainAudioProcessor, ExportManager (Transformer), GallerySaver
├── ads/           UMP consent + banner
└── ui/            Compose screens: projects, editor (preview + timeline + tools), export, exports
```

Notes:
- Imported media is referenced by URI with a persisted read grant; nothing is copied. If a source
  is deleted, the editor flags it and offers to remove the affected clips.
- Output resolution follows the first clip's aspect ratio; other clips are letterboxed.
- Exports run in the foreground; the screen is kept awake while encoding.
