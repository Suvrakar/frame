# Text Fonts and Colors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users pick a decorative font and any color for text overlays, from one Style panel that opens as soon as text is added.

**Architecture:** A `TextFonts` catalog (bundled TTFs in `res/font`) is the single source of fonts. `TextOverlay.font` stores the font id; `CompositionFactory` and `CaptionSpans` apply it with a `FontSpan`, so preview and export (which share the composition) match. The Style panel gains a font row, a color-picker dialog and the text field; the separate Edit tool is removed.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Media3 1.8.0 `TextOverlay`, kotlinx.serialization, JUnit 4, AndroidX Test.

Spec: `docs/superpowers/specs/2026-11-04-text-fonts-and-colors-design.md`

## Global Constraints

- minSdk 26: do not use `TypefaceSpan(Typeface)` (API 28); use the custom `FontSpan`.
- Font id `"default"` means system font; unknown ids resolve to default, never throw.
- Everything is free — no `ProStore`/Pro checks in any of this code.
- Build with JDK 17: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=$JAVA_HOME/bin:$PATH` (the default JDK 25 breaks Gradle 8.14; JDK 21 on this machine has no javac).
- Unit tests: `./gradlew :app:testDebugUnitTest --tests '<class>'`. `unitTests.isReturnDefaultValues = true`, so Android framework calls return null/0 on the JVM — anything touching `Typeface`/`Paint` is tested on device.
- Device tests: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fqcn>` with the Redmi connected (`adb devices` shows `4a3e5542`; if empty, `adb kill-server && adb start-server`).
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## File Map

| File | Responsibility |
|---|---|
| Create `app/src/main/res/font/*.ttf` (10 files) | Bundled fonts |
| Create `app/src/main/assets/licenses/fonts/OFL.txt`, `APACHE-2.0.txt` | Font licenses |
| Create `app/src/main/java/app/frame/studio/text/TextFonts.kt` | Font catalog, lookup, typeface cache |
| Create `app/src/main/java/app/frame/studio/text/FontSpan.kt` | minSdk-26 typeface span |
| Create `app/src/main/java/app/frame/studio/text/ColorMath.kt` | Pure HSV ↔ ARGB |
| Create `app/src/main/java/app/frame/studio/ui/editor/ColorPickerDialog.kt` | Hue slider + SV square dialog |
| Modify `app/src/main/java/app/frame/studio/domain/Models.kt:168-195` | `font` field |
| Modify `app/src/main/java/app/frame/studio/FrameApp.kt:46-59` | `TextFonts.init` |
| Modify `app/src/main/java/app/frame/studio/export/CompositionFactory.kt:291-380` | Apply font |
| Modify `app/src/main/java/app/frame/studio/captions/CaptionSpans.kt` | Apply font |
| Modify `app/src/main/java/app/frame/studio/ui/editor/Panels.kt:87-108, 777-905` | Tool enum, presets, merged Style panel |
| Modify `app/src/main/java/app/frame/studio/ui/editor/EditorScreen.kt:480-492, 696-710, 857-860, 939-940` | Open Style after add; toolbar; hit box |
| Tests: `app/src/test/java/app/frame/studio/text/TextFontsTest.kt`, `ColorMathTest.kt`; `app/src/test/java/app/frame/studio/domain/ProjectJsonTest.kt`; `app/src/androidTest/java/app/frame/studio/text/TextFontsDeviceTest.kt` | |

---

### Task 1: Bundle fonts and the `TextFonts` catalog

**Files:**
- Create: `app/src/main/res/font/` (10 TTFs), `app/src/main/assets/licenses/fonts/OFL.txt`, `app/src/main/assets/licenses/fonts/APACHE-2.0.txt`
- Create: `app/src/main/java/app/frame/studio/text/TextFonts.kt`
- Modify: `app/src/main/java/app/frame/studio/FrameApp.kt` (in `onCreate`)
- Test: `app/src/test/java/app/frame/studio/text/TextFontsTest.kt`

**Interfaces:**
- Produces: `data class TextFont(val id: String, val label: String, @FontRes val res: Int?)`; `object TextFonts { const val DEFAULT_ID = "default"; val all: List<TextFont>; fun byId(id: String): TextFont; fun init(appContext: Context); fun typeface(id: String): Typeface }`

- [ ] **Step 1: Download fonts** (resource names must be lowercase/underscore)

```bash
cd /home/bs01107/Desktop/Personal/frame && mkdir -p app/src/main/res/font app/src/main/assets/licenses/fonts
G=https://raw.githubusercontent.com/google/fonts/main
dl() { curl -sfL -o "app/src/main/res/font/$2.ttf" "$G/$1" && echo "ok $2" || echo "FAIL $2"; }
dl ofl/pacifico/Pacifico-Regular.ttf pacifico
dl ofl/greatvibes/GreatVibes-Regular.ttf great_vibes
dl "ofl/dancingscript/DancingScript%5Bwght%5D.ttf" dancing_script
dl "ofl/caveat/Caveat%5Bwght%5D.ttf" caveat
dl apache/permanentmarker/PermanentMarker-Regular.ttf permanent_marker
dl ofl/bebasneue/BebasNeue-Regular.ttf bebas_neue
dl ofl/lobster/Lobster-Regular.ttf lobster
dl ofl/abrilfatface/AbrilFatface-Regular.ttf abril_fatface
dl ofl/pressstart2p/PressStart2P-Regular.ttf press_start_2p
dl ofl/bungee/Bungee-Regular.ttf bungee
curl -sfL -o app/src/main/assets/licenses/fonts/OFL.txt $G/ofl/pacifico/OFL.txt
curl -sfL -o app/src/main/assets/licenses/fonts/APACHE-2.0.txt $G/apache/permanentmarker/LICENSE.txt
du -ch app/src/main/res/font/*.ttf | tail -1
```

Expected: ten `ok` lines, total under 2.5 MB. Dancing Script and Caveat are variable fonts; Android 8+ loads them at their default weight, which is fine.

- [ ] **Step 2: Write the failing test**

`app/src/test/java/app/frame/studio/text/TextFontsTest.kt`:

```kotlin
package app.frame.studio.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFontsTest {

    @Test
    fun idsAreUnique() {
        val ids = TextFonts.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun defaultIsFirstAndSystemFont() {
        val first = TextFonts.all.first()
        assertEquals(TextFonts.DEFAULT_ID, first.id)
        assertNull(first.res)
    }

    @Test
    fun unknownIdFallsBackToDefault() {
        assertEquals(TextFonts.DEFAULT_ID, TextFonts.byId("no_such_font").id)
    }

    @Test
    fun knownIdResolves() {
        assertEquals("pacifico", TextFonts.byId("pacifico").id)
    }

    @Test
    fun catalogHasTenDecorativeFonts() {
        assertEquals(10, TextFonts.all.count { it.res != null })
        assertTrue(TextFonts.all.all { it.label.isNotBlank() })
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.frame.studio.text.TextFontsTest'`
Expected: FAIL — `Unresolved reference: TextFonts`.

- [ ] **Step 4: Implement `TextFonts.kt`**

```kotlin
package app.frame.studio.text

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import app.frame.studio.R
import java.util.concurrent.ConcurrentHashMap

/** A text font: [res] null means the system font. */
data class TextFont(val id: String, val label: String, @FontRes val res: Int?)

/** Bundled fonts for text overlays and captions. One catalog for the editor, preview and export. */
object TextFonts {
    const val DEFAULT_ID = "default"

    val all: List<TextFont> = listOf(
        TextFont(DEFAULT_ID, "Classic", null),
        TextFont("pacifico", "Pacifico", R.font.pacifico),
        TextFont("great_vibes", "Great Vibes", R.font.great_vibes),
        TextFont("dancing_script", "Dancing Script", R.font.dancing_script),
        TextFont("caveat", "Caveat", R.font.caveat),
        TextFont("permanent_marker", "Marker", R.font.permanent_marker),
        TextFont("bebas_neue", "Bebas Neue", R.font.bebas_neue),
        TextFont("lobster", "Lobster", R.font.lobster),
        TextFont("abril_fatface", "Abril Fatface", R.font.abril_fatface),
        TextFont("press_start_2p", "Pixel", R.font.press_start_2p),
        TextFont("bungee", "Bungee", R.font.bungee),
    )

    private val byId = all.associateBy { it.id }
    private val cache = ConcurrentHashMap<String, Typeface>()

    @Volatile private var appContext: Context? = null

    fun byId(id: String): TextFont = byId[id] ?: all.first()

    fun init(appContext: Context) {
        this.appContext = appContext.applicationContext
    }

    /** Cached typeface for [id]; system default before [init] or if loading fails. */
    fun typeface(id: String): Typeface {
        val font = byId(id)
        val res = font.res ?: return Typeface.DEFAULT
        val context = appContext ?: return Typeface.DEFAULT
        cache[font.id]?.let { return it }
        val loaded = try {
            ResourcesCompat.getFont(context, res)
        } catch (e: Exception) {
            Log.w("TextFonts", "Could not load font ${font.id}", e)
            null
        } ?: return Typeface.DEFAULT
        cache[font.id] = loaded
        return loaded
    }
}
```

- [ ] **Step 5: Init from `FrameApp.onCreate`**

In `app/src/main/java/app/frame/studio/FrameApp.kt`, after `super.onCreate()` add:

```kotlin
        app.frame.studio.text.TextFonts.init(this)
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.frame.studio.text.TextFontsTest'`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/font app/src/main/assets/licenses/fonts app/src/main/java/app/frame/studio/text/TextFonts.kt app/src/main/java/app/frame/studio/FrameApp.kt app/src/test/java/app/frame/studio/text/TextFontsTest.kt
git commit -m "Text: bundle 10 open-license fonts and a font catalog

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `font` on `TextOverlay`, applied in export and captions

**Files:**
- Modify: `app/src/main/java/app/frame/studio/domain/Models.kt:168-195`
- Create: `app/src/main/java/app/frame/studio/text/FontSpan.kt`
- Modify: `app/src/main/java/app/frame/studio/export/CompositionFactory.kt` (`timedTextOverlay`, `captionOverlay`)
- Modify: `app/src/main/java/app/frame/studio/captions/CaptionSpans.kt` (`build`)
- Test: `app/src/test/java/app/frame/studio/domain/ProjectJsonTest.kt`, `app/src/androidTest/java/app/frame/studio/text/TextFontsDeviceTest.kt`

**Interfaces:**
- Consumes: `TextFonts.DEFAULT_ID`, `TextFonts.typeface(id)` (Task 1)
- Produces: `TextOverlay.font: String`; `class FontSpan(val fontId: String) : MetricAffectingSpan`; `CaptionSpans.build(words, style, baseColor, active, bold, font: String = TextFonts.DEFAULT_ID)`

- [ ] **Step 1: Write failing JSON tests**

Append inside `class ProjectJsonTest` in `app/src/test/java/app/frame/studio/domain/ProjectJsonTest.kt`:

```kotlin
    @Test
    fun legacyTextWithoutFontDecodesToDefault() {
        val base = Project(
            id = "p2", name = "Old", createdAtEpochMs = 1L,
            textOverlays = listOf(TextOverlay(id = "t1", text = "Hi", startMs = 0, endMs = 1_000)),
        )
        val legacy = ProjectJson.encode(base).replace(",\"font\":\"default\"", "")
        assertEquals(false, legacy.contains("\"font\""))
        assertEquals("default", ProjectJson.decode(legacy).textOverlays.single().font)
    }

    @Test
    fun roundTripsTextFont() {
        val project = Project(
            id = "p3", name = "Fonts", createdAtEpochMs = 1L,
            textOverlays = listOf(TextOverlay(id = "t1", text = "Hi", startMs = 0, endMs = 1_000, font = "pacifico")),
        )
        assertEquals("pacifico", ProjectJson.decode(ProjectJson.encode(project)).textOverlays.single().font)
    }
```

If `Project` requires more constructor arguments than shown, copy the required ones from the existing `roundTripsProject` test in the same file.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.frame.studio.domain.ProjectJsonTest'`
Expected: FAIL — `No parameter with name 'font'` / unresolved `font`.

- [ ] **Step 3: Add the field**

In `Models.kt`, inside `data class TextOverlay(...)`, after `val keyframes: List<Keyframe> = emptyList(),` add:

```kotlin
    /** Id from [app.frame.studio.text.TextFonts]; "default" = system font. */
    val font: String = app.frame.studio.text.TextFonts.DEFAULT_ID,
```

- [ ] **Step 4: Run JSON tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.frame.studio.domain.ProjectJsonTest'`
Expected: PASS.

- [ ] **Step 5: Create `FontSpan.kt`**

```kotlin
package app.frame.studio.text

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * Applies a [TextFonts] typeface. `TypefaceSpan(Typeface)` needs API 28; minSdk is 26.
 * Keeps any bold requested by a [android.text.style.StyleSpan] via fake bold.
 */
class FontSpan(val fontId: String) : MetricAffectingSpan() {

    override fun updateDrawState(tp: TextPaint) = apply(tp)

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    private fun apply(tp: TextPaint) {
        val wantsBold = ((tp.typeface?.style ?: 0) and Typeface.BOLD) != 0
        val face = TextFonts.typeface(fontId)
        tp.typeface = face
        if (wantsBold && !face.isBold) tp.isFakeBoldText = true
    }

    // Media3 compares spans to decide whether to redraw; same font = equal span.
    override fun equals(other: Any?): Boolean = other is FontSpan && other.fontId == fontId
    override fun hashCode(): Int = fontId.hashCode()
}
```

- [ ] **Step 6: Apply in `CompositionFactory.timedTextOverlay`**

In the `SpannableString(padded).apply { ... }` block, after the `if (overlay.bold) setSpan(StyleSpan(...))` line, add (order matters: `StyleSpan` first so `FontSpan` sees the bold request):

```kotlin
            if (overlay.font != app.frame.studio.text.TextFonts.DEFAULT_ID) {
                setSpan(app.frame.studio.text.FontSpan(overlay.font), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
```

In `captionOverlay`, change the `CaptionSpans.build(...)` call to:

```kotlin
            CaptionSpans.build(words, overlay.captionStyle, overlay.color.toInt(), active, overlay.bold, overlay.font)
```

- [ ] **Step 7: Apply in `CaptionSpans.build`**

Change the signature and add the span right after the bold span:

```kotlin
    fun build(
        words: List<CaptionWord>,
        style: CaptionStyle,
        baseColor: Int,
        active: Int,
        bold: Boolean,
        font: String = app.frame.studio.text.TextFonts.DEFAULT_ID,
    ): SpannableString {
```

```kotlin
        if (bold) span(StyleSpan(Typeface.BOLD), 0, s.length)
        if (font != app.frame.studio.text.TextFonts.DEFAULT_ID) span(app.frame.studio.text.FontSpan(font), 0, s.length)
```

- [ ] **Step 8: Write the device test**

`app/src/androidTest/java/app/frame/studio/text/TextFontsDeviceTest.kt`:

```kotlin
package app.frame.studio.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextFontsDeviceTest {

    @Before
    fun setUp() {
        TextFonts.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun everyBundledFontLoads() {
        TextFonts.all.filter { it.res != null }.forEach { font ->
            assertNotSame("${font.id} fell back to default", Typeface.DEFAULT, TextFonts.typeface(font.id))
        }
    }

    @Test
    fun fontSpanChangesRenderedPixels() {
        val plain = render(null)
        val script = render("pacifico")
        assertFalse("Pacifico rendered identically to the default font", plain.sameAs(script))
    }

    private fun render(fontId: String?): Bitmap {
        val text = SpannableString("Happy Birthday")
        if (fontId != null) text.setSpan(FontSpan(fontId), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 64f; color = Color.WHITE }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 1000)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        val bitmap = Bitmap.createBitmap(1000, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        layout.draw(Canvas(bitmap))
        return bitmap
    }
}
```

- [ ] **Step 9: Run device test**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.frame.studio.text.TextFontsDeviceTest`
Expected: PASS (2 tests). Also run `./gradlew :app:testDebugUnitTest` — all unit tests PASS (captions tests still compile because `font` has a default).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/app/frame/studio/domain/Models.kt app/src/main/java/app/frame/studio/text/FontSpan.kt app/src/main/java/app/frame/studio/export/CompositionFactory.kt app/src/main/java/app/frame/studio/captions/CaptionSpans.kt app/src/test/java/app/frame/studio/domain/ProjectJsonTest.kt app/src/androidTest/java/app/frame/studio/text/TextFontsDeviceTest.kt
git commit -m "Text: per-overlay font, rendered in preview, export and captions

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Color math and color picker dialog

**Files:**
- Create: `app/src/main/java/app/frame/studio/text/ColorMath.kt`
- Create: `app/src/main/java/app/frame/studio/ui/editor/ColorPickerDialog.kt`
- Test: `app/src/test/java/app/frame/studio/text/ColorMathTest.kt`

**Interfaces:**
- Produces: `object ColorMath { fun hsvToArgb(h: Float, s: Float, v: Float): Long; fun argbToHsv(argb: Long): FloatArray /* [h 0..360, s 0..1, v 0..1] */ }`; `@Composable fun ColorPickerDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit)`

- [ ] **Step 1: Write failing test**

`app/src/test/java/app/frame/studio/text/ColorMathTest.kt`:

```kotlin
package app.frame.studio.text

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMathTest {

    @Test
    fun primaries() {
        assertEquals(0xFFFF0000, ColorMath.hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00, ColorMath.hsvToArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FF, ColorMath.hsvToArgb(240f, 1f, 1f))
        assertEquals(0xFFFFFFFF, ColorMath.hsvToArgb(0f, 0f, 1f))
        assertEquals(0xFF000000, ColorMath.hsvToArgb(200f, 0.5f, 0f))
    }

    @Test
    fun roundTrips() {
        listOf(0xFFFFD60A, 0xFF5B63F0, 0xFFFF375F, 0xFF30D158, 0xFF808080).forEach { argb ->
            val (h, s, v) = ColorMath.argbToHsv(argb).let { Triple(it[0], it[1], it[2]) }
            assertEquals(argb, ColorMath.hsvToArgb(h, s, v))
        }
    }

    @Test
    fun hueWraps() {
        assertEquals(ColorMath.hsvToArgb(0f, 1f, 1f), ColorMath.hsvToArgb(360f, 1f, 1f))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.frame.studio.text.ColorMathTest'`
Expected: FAIL — `Unresolved reference: ColorMath`.

- [ ] **Step 3: Implement `ColorMath.kt`** (pure Kotlin; `android.graphics.Color.HSVToColor` returns 0 in JVM tests)

```kotlin
package app.frame.studio.text

import kotlin.math.abs
import kotlin.math.roundToInt

/** Opaque HSV ↔ ARGB (as Long, like [app.frame.studio.domain.TextOverlay.color]). */
object ColorMath {

    fun hsvToArgb(h: Float, s: Float, v: Float): Long {
        val hue = ((h % 360f) + 360f) % 360f
        val sat = s.coerceIn(0f, 1f)
        val value = v.coerceIn(0f, 1f)
        val c = value * sat
        val x = c * (1 - abs((hue / 60f) % 2 - 1))
        val m = value - c
        val (r, g, b) = when ((hue / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    fun argbToHsv(argb: Long): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val s = if (max == 0f) 0f else d / max
        return floatArrayOf(h, s, max)
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.frame.studio.text.ColorMathTest'`
Expected: PASS (3 tests).

- [ ] **Step 5: Implement `ColorPickerDialog.kt`**

```kotlin
package app.frame.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.frame.studio.text.ColorMath

/** Any opaque color: hue strip plus saturation/brightness square. */
@Composable
fun ColorPickerDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val start = remember(initial) { ColorMath.argbToHsv(initial) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val current = ColorMath.hsvToArgb(hue, sat, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text color") },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SaturationValueSquare(hue, sat, value) { s, v -> sat = s; value = v }
                HueStrip(hue) { hue = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(Color(current.toInt()))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text("#%06X".format(current and 0xFFFFFF), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
    )
}

@Composable
private fun SaturationValueSquare(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val hueColor = Color(ColorMath.hsvToArgb(hue, 1f, 1f).toInt())
    fun pick(p: Offset, w: Float, h: Float) =
        onChange((p.x / w).coerceIn(0f, 1f), 1f - (p.y / h).coerceIn(0f, 1f))
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> change.consume(); pick(change.position, size.width.toFloat(), size.height.toFloat()) }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val c = Offset(sat * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 13.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun HueStrip(hue: Float, onHue: (Float) -> Unit) {
    val stops = (0..6).map { Color(ColorMath.hsvToArgb(it * 60f, 1f, 1f).toInt()) }
    fun pick(x: Float, w: Float) = onHue((x / w).coerceIn(0f, 1f) * 359.9f)
    Canvas(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> change.consume(); pick(change.position.x, size.width.toFloat()) }
            },
    ) {
        drawRect(Brush.horizontalGradient(stops))
        val x = hue / 360f * size.width
        drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
    }
}
```

- [ ] **Step 6: Compile**

Run: `./gradlew :app:compileDebugKotlin -q 2>&1 | grep -v '^w:'`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/frame/studio/text/ColorMath.kt app/src/main/java/app/frame/studio/ui/editor/ColorPickerDialog.kt app/src/test/java/app/frame/studio/text/ColorMathTest.kt
git commit -m "Text: HSV color math and a color picker dialog

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Merged Style panel with fonts and custom color; open it after adding text

**Files:**
- Modify: `app/src/main/java/app/frame/studio/ui/editor/Panels.kt` (Tool enum lines 87-108; `TextPreset`/`TEXT_PRESETS` ~777-791; `TextEditPanel` ~839-853; `TextStylePanel` ~855-905)
- Modify: `app/src/main/java/app/frame/studio/ui/editor/EditorScreen.kt` (add-text dialog ~480-492; text toolbar ~857-860; tool panel switch ~939-940)

**Interfaces:**
- Consumes: `TextFonts.all`, `TextFonts.typeface(id)`, `TextOverlay.font`, `ColorPickerDialog(initial, onDismiss, onPick)`
- Produces: `fun TextStylePanel(overlay: TextOverlay, onText: (String) -> Unit, onStyle: ((TextOverlay) -> TextOverlay) -> Unit)`; `Tool.TEXT_EDIT` removed

- [ ] **Step 1: Presets set a font**

Replace the `TextPreset` class and `TEXT_PRESETS` list with:

```kotlin
private class TextPreset(
    val name: String,
    val color: Long,
    val background: Boolean,
    val bold: Boolean,
    val font: String,
    val apply: (TextOverlay) -> TextOverlay,
)

private val TEXT_PRESETS = listOf(
    TextPreset("Classic", 0xFFFFFFFF, background = true, bold = false, font = "default") { it.copy(color = 0xFFFFFFFF, background = true, bold = false, scale = 1f, font = "default") },
    TextPreset("Pop", 0xFFFFD60A, background = false, bold = true, font = "bungee") { it.copy(color = 0xFFFFD60A, background = false, bold = true, scale = 1.4f, font = "bungee") },
    TextPreset("Neon", 0xFF64D2FF, background = false, bold = true, font = "bebas_neue") { it.copy(color = 0xFF64D2FF, background = false, bold = true, scale = 1.2f, font = "bebas_neue") },
    TextPreset("Elegant", 0xFFFFFFFF, background = false, bold = false, font = "great_vibes") { it.copy(color = 0xFFFFFFFF, background = false, bold = false, scale = 1.1f, font = "great_vibes") },
    TextPreset("Love", 0xFFFF375F, background = false, bold = true, font = "pacifico") { it.copy(color = 0xFFFF375F, background = false, bold = true, scale = 1.3f, font = "pacifico") },
    TextPreset("Caption", 0xFFFFD60A, background = true, bold = true, font = "default") { it.copy(color = 0xFFFFD60A, background = true, bold = true, scale = 0.9f, font = "default") },
)
```

In `TextPresetChip`, give the label its font by adding to the `Text(...)` call:

```kotlin
            fontFamily = fontFamilyFor(preset.font),
```

and add this helper below `TextPresetChip`:

```kotlin
@Composable
private fun fontFamilyFor(id: String): androidx.compose.ui.text.font.FontFamily =
    remember(id) {
        if (id == app.frame.studio.text.TextFonts.DEFAULT_ID) androidx.compose.ui.text.font.FontFamily.Default
        else androidx.compose.ui.text.font.FontFamily(app.frame.studio.text.TextFonts.typeface(id))
    }
```

- [ ] **Step 2: Rewrite `TextStylePanel` and delete `TextEditPanel`**

Delete the whole `TextEditPanel` function. Replace `TextStylePanel` with:

```kotlin
@Composable
fun TextStylePanel(
    overlay: TextOverlay,
    onText: (String) -> Unit,
    onStyle: ((TextOverlay) -> TextOverlay) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var text by remember(overlay.id) { mutableStateOf(overlay.text) }
        OutlinedTextField(
            value = text,
            onValueChange = {
                if (it.length <= 200) {
                    text = it
                    if (it.isNotBlank()) onText(it)
                }
            },
            label = { Text(if (overlay.isSticker) "Sticker" else "Text") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
        if (!overlay.isSticker) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TEXT_PRESETS.forEach { preset ->
                    TextPresetChip(preset) { onStyle(preset.apply) }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                app.frame.studio.text.TextFonts.all.forEach { font ->
                    FilterChip(
                        selected = overlay.font == font.id,
                        onClick = { onStyle { it.copy(font = font.id) } },
                        label = { Text(font.label, fontFamily = fontFamilyFor(font.id)) },
                    )
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val custom = overlay.color !in TextSwatches
                (if (custom) listOf(overlay.color) + TextSwatches else TextSwatches).forEach { argb ->
                    val selected = overlay.color == argb
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(argb.toInt()))
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { onStyle { it.copy(color = argb) } },
                    )
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.sweepGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                            ),
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { showPicker = true },
                    contentAlignment = Alignment.Center,
                ) { Text("+", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        ValueSlider("Size", "${(overlay.scale * 100).roundToInt()}%", overlay.scale, 0.4f..3f, { v -> onStyle { it.copy(scale = v) } })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!overlay.isSticker) {
                FilterChip(selected = overlay.bold, onClick = { onStyle { it.copy(bold = !it.bold) } }, label = { Text("Bold") })
                FilterChip(selected = overlay.background, onClick = { onStyle { it.copy(background = !it.background) } }, label = { Text("Background") })
            }
            FilterChip(selected = overlay.animate, onClick = { onStyle { it.copy(animate = !it.animate) } }, label = { Text("Fade in/out") })
            listOf("Top" to 0.12f, "Middle" to 0.5f, "Bottom" to 0.85f).forEach { (label, y) ->
                FilterChip(
                    selected = abs(overlay.y - y) < 0.01f && abs(overlay.x - 0.5f) < 0.01f,
                    onClick = { onStyle { it.copy(x = 0.5f, y = y) } },
                    label = { Text(label) },
                )
            }
        }
        Text(
            "Tip: drag the text on the preview to place it anywhere.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showPicker) {
        ColorPickerDialog(
            initial = overlay.color,
            onDismiss = { showPicker = false },
            onPick = { argb ->
                showPicker = false
                onStyle { it.copy(color = argb) }
            },
        )
    }
}
```

- [ ] **Step 3: Remove `TEXT_EDIT` from the `Tool` enum**

In `Panels.kt` delete the line `    TEXT_EDIT("Edit text"),` and change `TEXT_STYLE("Text style"),` to `TEXT_STYLE("Text"),`.

- [ ] **Step 4: Wire up `EditorScreen.kt`**

Text toolbar — delete the line:

```kotlin
            ToolbarItem(Icons.Filled.Edit, "Edit") { onTool(Tool.TEXT_EDIT) }
```

Tool panel switch — replace the two `Tool.TEXT_EDIT` / `Tool.TEXT_STYLE` branches with:

```kotlin
            Tool.TEXT_STYLE -> text?.let { o ->
                TextStylePanel(o, onText = { vm.setText(o.id, it) }) { transform -> vm.styleText(o.id, transform) }
            }
```

Add-text dialog `onConfirm` — open the Style panel on the new text:

```kotlin
            onConfirm = {
                showAddText = false
                vm.addText(it)
                tool = Tool.TEXT_STYLE
            },
```

(If `addText` returns early because the timeline is empty, `text` is null and the `TEXT_STYLE` branch renders nothing; `vm.addText` already handles that case and no change is needed.)

- [ ] **Step 5: Build and check nothing else referenced the removed pieces**

Run: `grep -rn 'TEXT_EDIT\|TextEditPanel' app/src/ ; ./gradlew :app:assembleDebug -q 2>&1 | grep -v '^w:'`
Expected: grep prints nothing; build has no errors. If an `androidTest` (e.g. the screenshot tour in `app/src/androidTest/java/app/frame/studio/ui/`) taps the "Edit" text-toolbar item by label, change it to tap "Style".

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/frame/studio/ui/editor/Panels.kt app/src/main/java/app/frame/studio/ui/editor/EditorScreen.kt app/src/androidTest
git commit -m "Text: one Style panel with fonts, any color, and the text field; opens after adding text

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Measure the preview drag box with the real font

**Files:**
- Modify: `app/src/main/java/app/frame/studio/ui/editor/EditorScreen.kt` (`TextDragFrame`, ~lines 696-710)

**Interfaces:**
- Consumes: `TextFonts.typeface(id)`, `TextOverlay.font`, `TextOverlay.bold`

- [ ] **Step 1: Replace the character-count estimate**

In `TextDragFrame`, replace these lines:

```kotlin
    val longestLine = overlay.text.lines().maxOfOrNull { it.length } ?: 1
    val boxW = (fontPx * 0.58f * (longestLine + if (overlay.background) 2 else 0)).coerceIn(fontPx, frameW)
    val boxH = (fontPx * 1.25f * overlay.text.lines().size.coerceAtLeast(1)).coerceAtMost(frameH)
```

with:

```kotlin
    val (textW, lineH) = remember(overlay.text, overlay.font, overlay.bold, overlay.background, fontPx) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontPx
            typeface = app.frame.studio.text.TextFonts.typeface(overlay.font)
            isFakeBoldText = overlay.bold && !typeface.isBold
        }
        val pad = if (overlay.background && !overlay.isSticker) paint.measureText("  ") else 0f
        val widest = overlay.text.lines().maxOfOrNull { paint.measureText(it) } ?: fontPx
        (widest + pad) to paint.fontSpacing
    }
    val boxW = textW.coerceIn(fontPx, frameW)
    val boxH = (lineH * overlay.text.lines().size.coerceAtLeast(1)).coerceAtMost(frameH)
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug -q 2>&1 | grep -v '^w:'`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/frame/studio/ui/editor/EditorScreen.kt
git commit -m "Text: size the preview drag box from the real font

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Verify on the phone

- [ ] **Step 1: Full unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Install and launch**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop app.frame.studio.debug
adb shell monkey -p app.frame.studio.debug -c android.intent.category.LAUNCHER 1
```

- [ ] **Step 3: Drive it and screenshot**

Open a project (or create one with a photo), tap Text → type "Happy Birthday" → Add. Confirm the Style panel opens by itself with the text field, presets, font row and colors. Tap "Pacifico", tap the "+" color chip, pick a color, Done. Screenshot with `adb exec-out screencap -p > <scratchpad>/text-style.png` and check: preview text is in Pacifico, in the picked color, and the white drag box hugs the text.

- [ ] **Step 4: Export check**

Export the project at 720p, open the result from Exports, screenshot a frame showing the text, and confirm it matches the preview font and color.

- [ ] **Step 5: APK size**

Run: `ls -la app/build/outputs/apk/debug/app-debug.apk` — growth vs. the pre-change APK (25.9 MB) should be ≤ 2.5 MB.
