# Frame

A lightweight Android video editor built with Jetpack Compose and Media3.
Exports never carry a watermark.

## Features

- **Templates** – 34 ready-made templates. Video templates (Birthday Bash, Golden Birthday, Kids
  Party, Countdown, Through the Years, Birthday Card, Birthday Sparkle, Love Story, anniversary,
  wedding, graduation, baby, travel, social and seasonal) and **4-photo collage** templates
  (Birthday Collage, Party Polaroids, Birthday Film, Birthday Photo Dump, Anniversary Collage,
  Love Polaroids, Our Years, Friends Forever, Travel Grid, Family Album, Mother's Day, Father's
  Day, Graduation Film, Baby Milestones) in five layouts: grid, hero + 3, strips, polaroids and
  film strip. Type a name/age, pick photos or videos, and Frame builds the video with
  personalized title cards, captions, stickers, a colour look and transitions; collages fill in
  one photo at a time and then hold. Everything is rendered on device — no bundled images.
- **What-you-see-is-what-you-export preview** – the editor plays the same Media3 composition the
  exporter renders (speed, filters, crop, transitions, text, mixed audio).
- **Timeline** – ruler, thumbnail filmstrips, drag-to-trim handles, pinch-to-zoom, snapping,
  free-placed audio and text lanes, transition buttons at every cut.
- **Clips** – videos and photos; split, trim, reorder, duplicate, speed 0.25×–4×, volume up to
  200%, fades, rotate, mirror, crop presets, 70+ filters in eight tabs (Classic, Social looks,
  Portrait, Nature & food, Vintage, Lo-fi & grain, Cinematic, Black & white) with intensity,
  vignette and film grain (a custom GPU effect), brightness / contrast /
  saturation / warmth / vignette / grain, fade or flash transitions, "apply to all".
- **Color grading** – exposure, brightness, contrast, highlights, shadows, saturation, vibrance,
  temperature, tint, fade, vignette, grain and split toning (shadow/highlight hues), with a
  press-and-hold Before compare. Linear controls fold into the clip's colour matrix; the rest run
  in one GPU pass (`GradeEffect`).
- **Beat sync (on device)** – Frame decodes the music on the phone, finds its tempo and beats
  (spectral-flux onsets + autocorrelation, `BeatDetector`), shows beat markers on the music lane,
  and re-times the clips so every cut lands on a beat, every 2 beats or every bar. No server.
- **Scene effects** – 12 procedural GPU effects on their own timeline lane: Rain, Snow, Fog,
  Confetti, Sparkle, Hearts, Bokeh, Light leak, Old film, Glitch, Shake and Beat pulse, each with
  strength and soft fade in/out. No assets: everything is drawn by shaders (`SceneShaderEffect`).
- **Audio** – music, sound effects and recorded voiceovers on overlapping lanes with volume,
  fades, split, move and trim.
- **Text & stickers** – colours, size, bold, background, fade animation, text style presets,
  drag to position on the preview; emoji stickers.
- **Canvas** – Original, 9:16, 16:9, 1:1, 4:5, 3:4, 4:3, 21:9.
- **Social media presets** – one-tap formats for Instagram (Reel, Story, Feed, Square,
  Landscape), TikTok (Video, Smooth 60), YouTube (Short, Video, 4K 60), Facebook (Reel, Story,
  Feed, Landscape), Snapchat (Spotlight, Story), X (Landscape, Square, Vertical), LinkedIn
  (Square, Landscape, Vertical) and WhatsApp (Status, Chat). Each sets the frame shape,
  resolution (clamped to what the phone can encode), frame rate and quality; a warning appears
  when a video is longer than the destination accepts (Instagram Stories 60 s, Reels and YouTube
  Shorts 3 min, Snapchat Spotlight 60 s, X 2:20). Available in the Canvas panel and on the export
  screen. Plus 70+ Instagram-style looks.
- **Export** – 480p–4K (only resolutions the phone's encoder supports are offered), 24/30/60 fps,
  Standard/High/Best quality with a size estimate, optional copy to `Movies/Frame`, share and
  history. No watermark.
- **Built for low-end phones**
  - `DeviceProfile` detects the device tier and lowers preview resolution, thumbnail cache and
    decoder parallelism, delays preview rebuilds while sliders move, and skips decorative animation.
  - Photos larger than the export needs (often 12–50 MP) are downscaled once on import
    (`ImageNormalizer`), instead of being decoded at full size for every frame.
  - The preview player runs on its own thread and renders into a plain `SurfaceView`; building,
    seeking and releasing it never blocks the UI thread. Timeline scrubbing throttles seeks.
  - Filmstrips reuse open decoders, decode only frames near the viewport, and are released when
    leaving the editor or under memory pressure; the editor avoids per-frame recomposition.
  - Export: decoder fallback when a hardware decoder can't start, a longer muxer watchdog on slow
    phones, and the stable (non-experimental) speed effect.
  - `profileinstaller` installs the baseline profiles shipped by Compose and Media3.
  - CI runs every device test on a low-end emulator (Android 10, 2 cores, 1.5 GB RAM) as well,
    including `PerfStressTest`: a 48 MP photo and a 20-clip timeline with heavy looks, previewed,
    exported and scrubbed, with frame and memory stats in the `ci-smoke-lowend` branch.
- **Magic Movie** (Pro) – pick up to 60 photos/videos and an optional song. On the device Frame
  scores every shot (sharpness, exposure, contrast), drops near-duplicates with a difference hash,
  orders shots by capture date, keeps the best ones for the chosen length (15/30/60 s or all), finds
  the song's beat and lands every cut on it. Five vibes set the look, pacing, transitions and a scene
  effect; a title and date span ("May – July 2026") are added. The result opens in the editor.
- **Animated captions** (Pro) – word-by-word captions in Karaoke, Pop, Highlight or Plain style.
  Auto captions use only Android's on-device speech recogniser (Android 13+, word timestamps on 14+);
  audio is split into speech chunks by a small voice-activity detector. Where no on-device engine
  exists, typed captions are timed by word length and animated the same way. Captions stay in sync
  when edited, trimmed or split.
- **Frame Pro** – one-time Google Play purchase (`frame_pro`), verified on the device and cached
  for offline use; "Restore purchase" in settings. Unlocks Magic Movie, animated captions, 2K/4K and
  50/60 fps export, and removes banner ads. Free users can watch one rewarded ad to use a Pro
  feature once. There is no server.
- **Ads** – anchored adaptive AdMob banner after Google UMP consent; "Privacy choices" in settings.
- **Undo / redo** for every edit.

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
| `admobRewardedId`   | `FRAME_ADMOB_REWARDED_ID`  | AdMob rewarded unit (`ca-app-pub-…/…`) |

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
`FRAME_ADMOB_BANNER_ID`, `FRAME_ADMOB_REWARDED_ID`.

A second job boots an Android 14 emulator, launches the app (`scripts/smoke-test.sh`) and runs
`ExportPipelineTest`, which renders a project using every editing feature through Media3 and
checks the output's duration, resolution and audio, plus `ScreenshotTour`, which captures the main
screens. Logs and screenshots from both jobs are mirrored to the `ci-results` and `ci-smoke`
branches.

## Play Store checklist

- [ ] In-app product `frame_pro` (one-time, non-consumable) created and active in Play Console →
      Monetize → Products. Test with a license-tester account.
- [ ] Rewarded ad unit created in AdMob and set as `FRAME_ADMOB_REWARDED_ID`.
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
- The canvas follows the chosen aspect ratio (or the first clip); other shapes are letterboxed.
- The preview uses Media3's `CompositionPlayer`, which is still library-internal in Media3 1.8.
  Media3 is pinned to 1.8.0 and `ExportPipelineTest` checks the preview on a device in CI; re-check
  it when upgrading Media3.
- Exports run in the foreground; the screen is kept awake while encoding.
