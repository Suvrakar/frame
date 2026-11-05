# Google Play listing — Frame

Paste these into Play Console → Grow → Store presence → Main store listing.
Images: `store/play-icon-512.png` (app icon), `store/feature-graphic-1024x500.png` (feature graphic).
Regenerate them with `python3 scripts/brand/render_brand.py store` after changing the icon.

## App name (30/30)

```
Frame: AI Video & Audio Editor
```

## Short description (77/80)

```
AI video maker with music, auto captions & beat sync. No watermark, up to 4K.
```

## Full description (3133/4000)

```
Frame is an AI-powered video editor and video maker for your phone. Cut clips, add music and voiceovers, drop in captions, and export in up to 4K with no watermark. Everything runs on your phone, so your videos stay private.

✨ AI-POWERED EDITING
• Magic Movie (Pro): pick photos and videos plus a song, and Frame makes the movie for you. It scores every shot for sharpness and exposure, skips near-duplicates, puts the best moments in order and cuts on the beat.
• Auto captions (Pro, Android 13+): Frame listens on your phone and turns speech into word-by-word animated subtitles in Karaoke, Pop, Highlight or Plain style. Or type them and Frame times every word.
• Beat sync: Frame finds the tempo and beats of your music and re-times your clips so every cut lands on the beat, every 2 beats or every bar.

🎬 VIDEO EDITOR
• Trim, split, duplicate and reorder clips on a simple timeline with pinch-to-zoom
• Speed from 0.25× to 4×, rotate, mirror and crop
• Fades and transitions between clips
• Picture-in-picture overlays with keyframe animation: move, resize and rotate on the preview
• 70+ filters and looks: cinematic, vintage, film grain, black & white and more
• Color grading: exposure, highlights, shadows, contrast, saturation, warmth, tint, vibrance and split toning
• 12 effects: rain, snow, fog, confetti, sparkle, hearts, bokeh, light leak, old film, glitch, shake and beat pulse

🎵 AUDIO EDITOR
• Video maker with music: add songs, sound effects and voiceovers on separate tracks
• Record a voiceover right in the editor
• Volume up to 200%, fade in and out, trim, split and move audio
• Mute or lower the original sound

🔤 TEXT, STICKERS & CAPTIONS
• Text with stylish fonts, any color, size, background and fade animation
• Emoji stickers you can place anywhere
• Animated subtitles for talking videos

📐 MADE FOR SOCIAL VIDEO
• 9:16, 1:1, 4:5, 16:9 and more for stories, short videos, posts and widescreen
• One-tap presets set the shape, resolution and frame rate for each platform
• Warnings when your video is longer than the destination allows

🎉 TEMPLATES & SLIDESHOWS
• 34 ready-made templates: birthday, anniversary, wedding, graduation, baby, travel and more
• Photo collage and slideshow maker: grid, polaroid and film strip layouts
• Type a name, pick your photos, and the video is ready to share

📤 EXPORT
• No watermark on any export
• 480p to 1080p free; 2K and 4K, 50 and 60 fps with Frame Pro
• Size estimate before you export, then save to your gallery or share

⚡ FAST ON EVERY PHONE
Frame adjusts itself to your phone, so editing stays smooth even on budget devices. The preview shows exactly what you'll export.

💎 FRAME PRO
A one-time purchase unlocks Magic Movie, animated auto captions, 2K/4K and 60 fps export, premium templates and filters, and removes ads. Or watch a short ad to use a Pro feature once.

🔒 PRIVATE BY DESIGN
Your photos, videos and recordings never leave your phone. Speech recognition, beat detection and Magic Movie all run on the device.

Make birthday videos, travel reels, music videos, slideshows and vlogs in minutes. Download Frame, the AI video and audio editor.
```

## Why these words

Google Play has no keyword field: it ranks on the title, short description and full description.
Searched Play (US, 2026-11-05) for "video editor", "ai video editor", "video maker with music" and
"audio video editor"; the top results all lead their titles with **Video Editor**, **AI Video
Editor** or **Video Maker** (CapCut, InShot, YouCut, PowerDirector, Filmora, Videoleap, KineMaster,
VivaCut, Canva). One ASO tool lists popularity: video editor 66, ai video editor 50, video maker 48,
reels editor 30 (source does not say which store).

- **Title** carries the two strongest terms we can honestly claim: "AI Video" and "Audio Editor"
  (the app edits music, sound effects and voiceovers, not only video).
- **Short description** adds "video maker", "music", "auto captions", "beat sync", "no watermark",
  "4K" — the features people filter for.
- **Full description** repeats each main term 2–7 times in natural sentences (Google can suspend
  apps for keyword stuffing) and covers long-tail searches: slideshow maker, photo collage,
  subtitles, voiceover, birthday video, music video, filters, effects, templates.
- **No third-party brand names** (Instagram, TikTok, YouTube, CapCut…) — Play rejects or suspends
  listings that use other companies' trademarks as keywords. "reels" appears once, lowercase, as a
  generic word.
- **Every claim matches a shipped feature**; Pro-only features are marked "(Pro)".

## "AI" claims (keep them true)

- Magic Movie: on-device shot scoring, near-duplicate removal and beat-matched cuts.
- Auto captions: Android's on-device speech recogniser (Android 13+).
- Beat sync: on-device tempo and beat detection.
Do not add fingerprint protection to the listing until it ships.

## Sources

- https://play.google.com/store/search?q=video%20editor&c=apps
- https://play.google.com/store/search?q=ai%20video%20editor&c=apps
- https://play.google.com/store/search?q=video%20maker%20with%20music&c=apps
- https://play.google.com/store/search?q=audio%20video%20editor&c=apps
- https://appfollow.io/blog/google-play-aso-keywords
- https://asolytics.pro/
