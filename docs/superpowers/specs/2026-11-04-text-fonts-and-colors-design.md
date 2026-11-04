# Text fonts, free color choice and easier styling — design

Date: 2026-11-04
Status: approved in chat, awaiting spec review

## Problem

Text overlays can only use the system font, colors are limited to 10 fixed swatches, and the
Style panel (colors, size, bold, background, position) sits behind a separate "Style" toolbar
button that users miss. Users read this as "text has no color, size or font options".

## Goals

1. About 10 bundled decorative fonts selectable per text item.
2. Any color, via a hue/brightness picker next to the existing swatches.
3. Styling is visible immediately after adding text.
4. Preview and export render identically (they already share `CompositionFactory`).
5. Everything is free — no Frame Pro gating.

Non-goals: outline/stroke, shadow, letter spacing, background-box color, downloadable fonts.

## Data model

`TextOverlay` (domain/Models.kt) gains:

```kotlin
/** Id from [TextFonts]; "default" = system font. */
val font: String = TextFonts.DEFAULT_ID,
```

`ProjectJson` already uses `ignoreUnknownKeys = true` and `encodeDefaults = true`, so projects
saved before this change decode with `font = "default"`. Unknown ids (e.g. a font removed in a
later version) also resolve to the default font.

## Font catalog — `app/src/main/java/app/frame/studio/text/TextFonts.kt`

Single source of truth used by the panel, the exporter and captions:

```kotlin
data class TextFont(val id: String, val label: String, @FontRes val res: Int?)  // res null = system
object TextFonts {
    const val DEFAULT_ID = "default"
    val all: List<TextFont>
    fun byId(id: String): TextFont            // falls back to default
    fun init(appContext: Context)              // called from FrameApp.onCreate
    fun typeface(id: String): Typeface         // cached; bold handled by caller
}
```

Bundled fonts (SIL Open Font License, files in `app/src/main/res/font/`, license texts in
`app/src/main/assets/licenses/fonts/`):

| Id | Label | Style |
|---|---|---|
| default | Classic | system sans |
| pacifico | Pacifico | script |
| great_vibes | Great Vibes | script |
| dancing_script | Dancing Script | script |
| caveat | Caveat | handwritten |
| permanent_marker | Marker | handwritten |
| bebas_neue | Bebas Neue | display |
| lobster | Lobster | display |
| abril_fatface | Abril Fatface | display |
| press_start_2p | Pixel | retro |
| bungee | Bungee | retro |

Static (non-variable) TTFs only, to keep size predictable (target ≤ 2 MB total).
Typefaces load with `ResourcesCompat.getFont` and are cached in a map; load failure falls back
to `Typeface.DEFAULT` and is logged, never thrown.

## Rendering

`CompositionFactory.timedTextOverlay` and `CaptionSpans.build` apply a new
`CustomTypefaceSpan(typeface)` (a `MetricAffectingSpan` that sets `paint.typeface`, preserving
the fake-bold flag when `bold` is on). `TypefaceSpan(Typeface)` is API 28+, minSdk is 26, hence
the custom span. `CompositionFactory` is an `object` with no `Context`, called from
`ExportManager` and `PreviewController`; rather than threading a context through, `FrameApp.onCreate`
calls `TextFonts.init(applicationContext)` and `TextFonts.typeface(id)` uses that application
context. Before `init` (e.g. plain JVM unit tests) it returns `Typeface.DEFAULT`.

The editor preview plays the same composition, so no separate preview path changes.

## Hit box on the preview

`EditorScreen` currently estimates the text box from character count (line ~706). Replace with a
measurement using `android.graphics.Paint` set to the overlay's typeface, bold flag and base text
size, so wide fonts (Bungee, Pixel) and narrow ones (Bebas Neue) get correct drag/transform boxes.

## Style panel

`TextStylePanel` (ui/editor/Panels.kt), top to bottom:

1. **Text field** (moved in from `TextEditPanel`) — non-sticker text only.
2. **Presets** row (unchanged).
3. **Font** row: horizontal chips, each label rendered in its own font; selected chip outlined.
   Hidden for stickers.
4. **Color** row: existing swatches, then the current custom color (if not a swatch) shown
   selected, then a "+" chip opening a **color picker dialog**: hue slider, saturation/brightness
   square, preview chip, Done/Cancel. Hidden for stickers.
5. Size slider, Bold / Background / Fade / Top-Middle-Bottom chips (unchanged).

Toolbar: the separate "Edit" and "Style" text tools merge into one "Style" tool
(`Tool.TEXT_STYLE`). `vm.addText(...)` for non-sticker text opens `Tool.TEXT_STYLE` on the new
overlay. Stickers keep their current flow.

Presets also set `font` (Classic → default, Pop → bungee, Neon → bebas_neue,
Elegant → great_vibes, Love → pacifico, Caption → default).

## Testing

Unit (JVM):
- `TextFonts.byId` returns default for unknown ids; ids are unique.
- `ProjectJson` decodes a legacy text overlay JSON without `font` to `font = "default"`, and
  round-trips a non-default font.
- Color picker HSV ↔ ARGB conversion round-trips (pure function in `text/ColorMath.kt`).

Device (androidTest):
- Every catalog font loads to a non-default `Typeface`.
- Exporting a one-frame project with text in `pacifico` yields a frame that differs from the
  same project with `default` font (pixel diff over the text region).

## Risks

- APK size: +1–2 MB. Acceptable; checked in the plan by measuring the APK.
- Low-end devices: typefaces are cached, loaded once per export; no per-frame cost.
