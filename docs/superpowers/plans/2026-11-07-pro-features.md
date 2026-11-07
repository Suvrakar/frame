# Pro features round — implementation plan

Goal: close the gaps users notice against CapCut / VN / InShot / Canva, in this order. Each phase
ships on its own (build + unit tests + device check + commit) before the next starts.

Build with JDK 17 (`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=$JAVA_HOME/bin:$PATH`).
Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Global constraints

- Preview and export look identical (same effect code path).
- Low-end phones first: one GPU pass per effect, no extra decoders, no overlapping clips.
- Old projects open unchanged (new model fields have defaults; enums only gain values).
- Decisions in pure, JVM-tested Kotlin; Android/GL code only renders.
- Free tier keeps the basics; the new premium set is Pro (rewarded-ad pass works as today).
- No new server costs; on-device only (except Phase 5, which reads static files).

## Phase 1 — Transitions pack

Design: a transition at a cut is split across it — its first half animates the end of clip A,
its second half the start of clip B (no overlap, total length unchanged). A composition-level
`TransitionShaderEffect` (one GL program, type chosen by uniform) renders it from a pure
`TransitionPlan`:
- `TransitionPlan.windows(clips): List<TransitionWindow(cutMs, halfMs, type)>` (cuts between
  consecutive clips; half = transitionMs / 2, clamped to half of each neighbouring clip).
- `TransitionPlan.at(windows, tMs): TransitionFrame(type, progress)?` with progress in [-1, 1]:
  -1..0 = outgoing half, 0..1 = incoming half; null outside every window.

Types (existing FADE_BLACK "Fade" and FADE_WHITE "Flash" keep the colour-dip path):
ZOOM_IN, ZOOM_OUT, WHIP_LEFT, WHIP_RIGHT, WHIP_UP, WHIP_DOWN, SPIN, BLUR, GLITCH, PIXELATE,
IRIS, SHAKE, BURN. Free: NONE, FADE_BLACK, FADE_WHITE, ZOOM_IN, WHIP_LEFT; the rest need
`ProFeature.PREMIUM_TRANSITIONS`.

UI: transition panel becomes a scrollable grid of tiles with Material icons, duration slider
(0.2–1.5 s), "Apply to all cuts" (gated the same way for Pro types).

Tests: JVM tests for windows/at (cut positions, clamping, progress sign, no window on first/last
edges, fades excluded); device: export a 3-clip project with a shader transition and check the
frame at the cut differs from the plain cut while length is unchanged.

## Phase 2 — Text animations

`TextOverlay` gains `animIn`, `animOut` (`TextAnimation`: NONE, FADE, POP, SLIDE_UP, SLIDE_DOWN,
SLIDE_LEFT, ZOOM, BOUNCE, TYPEWRITER) and `animMs` (default 400). Pure `TextMotion.at(overlay,
tMs)` → alpha, scale, offset, visibleChars. Rendered through the existing text overlay's
per-frame settings; typewriter swaps a cached bitmap per character count. Panel: "Animation" tab
in the text style sheet (In / Out rows). Basic (FADE, POP, SLIDE_UP) free, rest Pro.

## Phase 3 — Audio projects, noise reduction, ducking

- New project chooser: Video or Audio. `Project.kind` (VIDEO default). Audio projects: waveform
  timeline of audio lanes (trim, split, move, volume, fades, voice, music), audio-only preview,
  export to M4A (AAC) into Music/Frame; optional "audio + cover image" MP4 for socials.
- Noise reduction: on-device spectral gate as an `AudioProcessor` (per clip toggle + strength).
- Ducking: music lanes drop by a set amount while any voice/video-speech lane is active (pure
  envelope from loudness, applied as gain), per-project toggle.

## Phase 4 — AI: auto-cut silences, background removal

- Auto-cut silences: reuse caption loudness analysis; propose cuts (min silence, padding), user
  reviews then applies as splits/removals. Pro.
- Background removal for photos/overlays: ML Kit subject segmentation (on-device), cached cutout
  PNG; video background removal deferred (too heavy per frame on low-end phones). Pro.

## Phase 5 — Remote templates

Templates as JSON (same fields as `VideoTemplate`, built-in blocks only) + assets on Firebase
Storage/static host; index fetched at launch, cached for offline; Remote Config can hide, order
or mark Pro. Built-ins stay as fallback. New render blocks still need an app update.
