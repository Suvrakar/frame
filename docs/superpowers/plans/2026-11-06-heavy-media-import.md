# Heavy media import — implementation plan

> For agentic workers: execute task by task (superpowers:subagent-driven-development). TDD where a
> pure function exists. Build with JDK 17:
> `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=$JAVA_HOME/bin:$PATH`.
> Commit messages end with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**Goal:** Files a phone can't handle never break the editor. Every imported video/audio is checked
against the phone's real decoders: unsupported files are refused with a clear reason; videos the
phone can decode but not smoothly get an automatic low-res **preview proxy**; **export always uses
the original file** (export quality is never lowered).

## Global constraints

- Export (`CompositionFactory.build(forExport = true)`) must never use a proxy.
- A refused file never stops the rest of a multi-file pick.
- Unsupported audio track inside a supported video → import the video with `volume = 0` (original
  sound off) and tell the user; the picture is kept.
- Decisions are made by a pure, JVM-tested `ImportPolicy`; Android code only gathers facts.
- Everything off the main thread; no new dependencies.
- User-facing copy is short and specific (resolution/codec in plain words).

## Decision rules (ImportPolicy)

Inputs: video track (mime, width, height, fps, hdr/10-bit), audio track (mime, sampleRate, channels),
the phone's decoder table, `isLowEnd`.

Video:
1. No decoder (hardware or software) for the mime → `Unsupported("This video's format (<codec>) isn't supported on this phone")`.
2. No decoder supports the size (either orientation) → `Unsupported("This <8K> video is too large for this phone (up to <4K>)")` using the largest supported size.
3. Supported, but heavy → `Heavy` when ANY of: only software decoders support size+rate; no decoder
   supports size *and* rate (decodable but slower than real time); low-end phone and
   width×height×fps > 1920×1080×60; low-end phone and HDR/10-bit.
4. Otherwise `Ok`.

Audio (file or track): no decoder for the mime → unsupported (file refused / track muted).
Unknown/missing fps → treat as 30.

## Tasks

### Task 1 — Capability core (pure + Android gatherer)
- `app/src/main/java/app/frame/studio/media/ImportPolicy.kt` (pure): data classes
  `VideoTrack(mime, width, height, fps, hdr)`, `AudioTrack(mime, sampleRate, channels)`,
  `VideoDecoder(name, hardware, maxWidth, maxHeight, supports: (w, h, fps) -> Boolean, supportsSize: (w, h) -> Boolean)`,
  `AudioDecoder(name)`, `interface DecoderTable { fun video(mime): List<VideoDecoder>; fun audio(mime): List<AudioDecoder> }`;
  `sealed interface VideoVerdict { Ok; Heavy(reason); Unsupported(message) }`; `fun video(track, table, lowEnd): VideoVerdict`;
  `fun audioSupported(track, table): Boolean`; friendly size names (8K/4K/2K/1080p/720p).
- `app/src/main/java/app/frame/studio/media/MediaInspector.kt` (Android): `MediaExtractor` → tracks
  (video + first audio), fps from KEY_FRAME_RATE, HDR from KEY_COLOR_TRANSFER (ST2084/HLG) or
  profile 10-bit; `AndroidDecoderTable` from `MediaCodecList(ALL_CODECS)` decoders (isHardwareAccelerated
  on API 29+, else name prefix not `c2.android.`/`OMX.google.`); cached per mime. Never throws.
- Tests: `app/src/test/java/app/frame/studio/media/ImportPolicyTest.kt` with a fake table — missing
  codec, too large (message names 8K / max 4K), software-only → Heavy, size ok but rate not →
  Heavy, low-end 4K60 → Heavy, low-end HDR → Heavy, 1080p30 on low-end → Ok, rotated portrait
  sizes, unsupported audio.

### Task 2 — Wire into every import path
- `MediaProbe`: `VideoInfo` gains `verdict: VideoVerdict` and `audioSupported: Boolean`;
  `AudioInfo` gains `supported: Boolean`. `MediaProbe` gets `inspector` + `device.isLowEnd`.
- EditorViewModel media import (videos), overlay import, audio import, voiceover untouched:
  Unsupported → skip + collect message; Heavy → import, set `needsProxy = true`; audio track
  unsupported → `volume = 0f` + message. Messages are summarised once per pick
  ("2 files couldn't be added: <reason>").
- `VideoClip` and `OverlayClip` gain `needsProxy: Boolean = false` (serialized, default false).
- MagicMovieBuilder + TemplateBuilder: skip Unsupported videos (they already skip unreadable ones),
  mark Heavy clips `needsProxy`, mute unsupported audio tracks; unsupported music → no music
  (Magic Movie screen already says so on pick; make that check use `supported`).
- JVM tests where logic is pure; build + unit tests must pass.

### Task 3 — Preview proxies
- `app/src/main/java/app/frame/studio/media/ProxyStore.kt`: proxies in `files/proxies/<sha1(uri)>.mp4`;
  `fun proxyFor(uri): String?` (ready only); `fun request(uri)` queues a background Transformer job
  (one at a time, `Presentation.createForHeight(540)`, H.264, keeps audio unless muted source),
  `readyVersion: StateFlow<Int>` bumps when one finishes; failures ignored (original still used);
  cap total size 1.5 GB (LRU by lastModified); never throws.
- `CompositionFactory.build(project, forExport = false)` uses `ProxyStore.proxyFor(uri) ?: uri` for
  clips/overlays with `needsProxy`; `forExport = true` never does (add a JVM-testable seam: a
  `proxyResolver: (String) -> String?` property, default null).
- Editor: on open and after imports, request proxies for `needsProxy` clips missing one; when
  `readyVersion` changes, rebuild the preview (`controller.setProject(project, force = true)`);
  show a small "Preparing smooth preview…" pill over the preview while any are pending.
- Unit test: build(forExport=false) substitutes, build(forExport=true) doesn't.

### Task 4 — Device verification (Redmi Note 13)
- Generated media: 8K H.264 (expect refusal message if the phone's decoders stop below 8K; if the
  phone *can* decode 8K, expect Heavy), 4K60 H.264 (expect Heavy on low-end rules / Ok otherwise —
  assert per actual decoder table), HEVC 10-bit, MP4 with AC-3 audio (expect muted import if no AC-3
  decoder), an unsupported audio file.
- Device test asserts: refusals carry the expected reason; Heavy clip gets a proxy within 60 s and
  the preview composition uses it; **export of a project with a Heavy 4K clip produces a 4K file**
  (export resolution unaffected) and reads the original.
- Full device suite still passes.
