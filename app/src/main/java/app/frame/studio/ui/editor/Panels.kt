@file:OptIn(ExperimentalMaterial3Api::class)

package app.frame.studio.ui.editor

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.frame.studio.ui.common.bounceClick
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.GridView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.frame.studio.FrameApp
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ColorAdjust
import app.frame.studio.domain.EffectGroup
import app.frame.studio.domain.SceneEffect
import app.frame.studio.domain.SceneEffectType
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip
import app.frame.studio.effects.FilterGroup
import app.frame.studio.timeline.TimelineMath
import app.frame.studio.effects.Filters
import app.frame.studio.export.SocialPlatform
import app.frame.studio.export.SocialPreset
import app.frame.studio.export.SocialPresets
import app.frame.studio.export.centerCrop
import app.frame.studio.ui.common.VideoThumbnail
import app.frame.studio.ui.common.formatDuration
import app.frame.studio.ui.theme.TextSwatches
import kotlin.math.abs
import kotlin.math.roundToInt

/** Every editing tool that opens a panel in place of the toolbar. */
enum class Tool(val title: String) {
    SPEED("Speed"),
    DURATION("Duration"),
    VOLUME("Volume"),
    FADE("Fade"),
    FILTERS("Filters"),
    ADJUST("Color grade"),
    CROP("Crop"),
    TRANSITION("Transition"),
    CANVAS("Canvas"),
    AUDIO("Add audio"),
    VOICE("Voiceover"),
    STICKERS("Stickers"),
    TEXT_STYLE("Text"),
    EFFECTS("Effects"),
    BEAT_SYNC("Beat sync"),
    CAPTIONS("Captions"),
    OPACITY("Opacity"),
    OVERLAY_FILTER("Overlay filter"),
    EFFECT_EDIT("Effect"),
}

@Composable
fun ToolPanel(
    title: String,
    onDone: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(bottom = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            action?.invoke()
            IconButton(onClick = onDone) { Icon(Icons.Filled.Check, contentDescription = "Done") }
        }
        Column(Modifier.padding(horizontal = 16.dp)) { content() }
    }
}

@Composable
fun ValueSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0,
    onFinished: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = onFinished,
        )
    }
}

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)

@Composable
fun SpeedPanel(clip: VideoClip, onSpeed: (Float) -> Unit) {
    val index = SPEEDS.indexOfFirst { abs(it - clip.speed) < 0.001f }.let { if (it < 0) SPEEDS.indexOf(1f) else it }
    Column {
        Text(
            "${clip.speed}x",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Slider(
            value = index.toFloat(),
            onValueChange = { onSpeed(SPEEDS[it.roundToInt().coerceIn(0, SPEEDS.lastIndex)]) },
            valueRange = 0f..SPEEDS.lastIndex.toFloat(),
            steps = SPEEDS.size - 2,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0.25x", "4x").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "${formatDuration(clip.trimEndMs - clip.trimStartMs)} → ${formatDuration(clip.outputDurationMs())}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun DurationPanel(clip: VideoClip, onDuration: (Long) -> Unit) {
    ValueSlider(
        "Photo duration",
        formatDuration(clip.outputDurationMs()),
        clip.outputDurationMs() / 1000f,
        0.5f..15f,
        { onDuration((it * 10).roundToInt() * 100L) },
    )
}

@Composable
fun VolumePanel(volume: Float, onVolume: (Float) -> Unit) {
    ValueSlider("Volume", "${(volume * 100).roundToInt()}%", volume, 0f..2f, { onVolume((it * 20).roundToInt() / 20f) })
    if (volume > 1f) {
        Text(
            "Above 100% may distort loud audio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun FadePanel(fadeInMs: Long, fadeOutMs: Long, durationMs: Long, onFadeIn: (Long) -> Unit, onFadeOut: (Long) -> Unit) {
    val max = (durationMs / 2).coerceAtMost(10_000).toFloat().coerceAtLeast(100f) / 1000f
    Column {
        ValueSlider("Fade in", formatDuration(fadeInMs), fadeInMs / 1000f, 0f..max, { onFadeIn((it * 10).roundToInt() * 100L) })
        ValueSlider("Fade out", formatDuration(fadeOutMs), fadeOutMs / 1000f, 0f..max, { onFadeOut((it * 10).roundToInt() * 100L) })
    }
}

@Composable
fun FiltersPanel(
    app: FrameApp,
    clip: VideoClip,
    isPro: Boolean,
    onFilter: (String) -> Unit,
    onIntensity: (Float) -> Unit,
    onBrowse: () -> Unit,
) {
    var group by remember(clip.id) { mutableStateOf(Filters.byId(clip.filterId).group) }
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.AssistChip(
                onClick = onBrowse,
                label = { Text("All ${Filters.all.size - 1}") },
                leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            FilterGroup.entries.forEach { g ->
                FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g.label) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(Filters.inGroup(group), key = { it.id }) { filter ->
                val selected = clip.filterId == filter.id
                val matrix = Filters.transformFor(filter.id, 1f, ColorAdjust())
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp).bounceClick { onFilter(filter.id) },
                ) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))) {
                    VideoThumbnail(
                        probe = app.mediaProbe,
                        uri = clip.sourceUri,
                        atMs = clip.trimStartMs,
                        isImage = clip.isImage,
                        maxSize = 200,
                        colorFilter = if (matrix.isIdentity) null else ColorFilter.colorMatrix(ColorMatrix(matrix.toAndroid())),
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                else Modifier,
                            ),
                    )
                    if (filter.vignette > 0f) {
                        Box(
                            Modifier.matchParentSize().background(
                                androidx.compose.ui.graphics.Brush.radialGradient(
                                    0.45f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = filter.vignette * 0.8f),
                                ),
                            ),
                        )
                    }
                    if (filter.pro && !isPro) {
                        Icon(
                            Icons.Filled.WorkspacePremium,
                            contentDescription = "Pro",
                            tint = Color(0xFFFFC94D),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(16.dp)
                                .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(2.dp),
                        )
                    }
                    }
                    Text(
                        filter.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (clip.filterId != "none") {
            ValueSlider("Intensity", "${(clip.filterIntensity * 100).roundToInt()}", clip.filterIntensity, 0f..1f, onIntensity)
        }
    }
}

/** One grading control: how to read/write it on [ColorAdjust] and its slider range. */
private enum class GradeControl(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (ColorAdjust) -> Float,
    val set: (ColorAdjust, Float) -> ColorAdjust,
) {
    EXPOSURE("Exposure", -1f..1f, { it.exposure }, { a, v -> a.copy(exposure = v) }),
    BRIGHTNESS("Brightness", -1f..1f, { it.brightness }, { a, v -> a.copy(brightness = v) }),
    CONTRAST("Contrast", -1f..1f, { it.contrast }, { a, v -> a.copy(contrast = v) }),
    HIGHLIGHTS("Highlights", -1f..1f, { it.highlights }, { a, v -> a.copy(highlights = v) }),
    SHADOWS("Shadows", -1f..1f, { it.shadows }, { a, v -> a.copy(shadows = v) }),
    SATURATION("Saturation", -1f..1f, { it.saturation }, { a, v -> a.copy(saturation = v) }),
    VIBRANCE("Vibrance", -1f..1f, { it.vibrance }, { a, v -> a.copy(vibrance = v) }),
    WARMTH("Temperature", -1f..1f, { it.warmth }, { a, v -> a.copy(warmth = v) }),
    TINT("Tint", -1f..1f, { it.tint }, { a, v -> a.copy(tint = v) }),
    FADE("Fade", 0f..1f, { it.fade }, { a, v -> a.copy(fade = v) }),
    VIGNETTE("Vignette", 0f..1f, { it.vignette }, { a, v -> a.copy(vignette = v) }),
    GRAIN("Grain", 0f..1f, { it.grain }, { a, v -> a.copy(grain = v) }),
}

private enum class GradeGroup(val label: String, val controls: List<GradeControl>) {
    LIGHT("Light", listOf(GradeControl.EXPOSURE, GradeControl.BRIGHTNESS, GradeControl.CONTRAST, GradeControl.HIGHLIGHTS, GradeControl.SHADOWS)),
    COLOR("Color", listOf(GradeControl.SATURATION, GradeControl.VIBRANCE, GradeControl.WARMTH, GradeControl.TINT)),
    SPLIT("Split tone", emptyList()),
    EFFECTS("Effects", listOf(GradeControl.FADE, GradeControl.VIGNETTE, GradeControl.GRAIN)),
}

/** Lightroom-style colour grading for a clip. */
@Composable
fun AdjustPanel(adjust: ColorAdjust, onAdjust: (ColorAdjust) -> Unit) {
    var group by remember { mutableStateOf(GradeGroup.LIGHT) }
    var control by remember { mutableStateOf(GradeControl.EXPOSURE) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradeGroup.entries.forEach { g ->
                val changed = when (g) {
                    GradeGroup.SPLIT -> adjust.shadowTone > 0f || adjust.highlightTone > 0f
                    else -> g.controls.any { it.get(adjust) != 0f }
                }
                FilterChip(
                    selected = group == g,
                    onClick = {
                        group = g
                        g.controls.firstOrNull()?.let { control = it }
                    },
                    label = { Text(if (changed) "${g.label} •" else g.label) },
                )
            }
        }
        if (group == GradeGroup.SPLIT) {
            SplitToneRow("Shadows", adjust.shadowHue, adjust.shadowTone,
                { h -> onAdjust(adjust.copy(shadowHue = h)) }, { a -> onAdjust(adjust.copy(shadowTone = a)) })
            SplitToneRow("Highlights", adjust.highlightHue, adjust.highlightTone,
                { h -> onAdjust(adjust.copy(highlightHue = h)) }, { a -> onAdjust(adjust.copy(highlightTone = a)) })
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.controls.forEach { c ->
                    val v = c.get(adjust)
                    val selected = c == control
                    Text(
                        if (v != 0f) "${c.label} •" else c.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { control = c }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            val value = control.get(adjust)
            ValueSlider(
                control.label,
                "${if (value > 0f && control.range.start < 0f) "+" else ""}${(value * 100).roundToInt()}",
                value,
                control.range,
                { raw -> onAdjust(control.set(adjust, (raw * 100).roundToInt() / 100f)) },
            )
        }
        if (!adjust.isIdentity) {
            TextButton(onClick = { onAdjust(ColorAdjust()) }) { Text("Reset grade") }
        }
    }
}

/** Hue strip plus strength for one split-tone range. */
@Composable
private fun SplitToneRow(label: String, hue: Float, amount: Float, onHue: (Float) -> Unit, onAmount: (Float) -> Unit) {
    val rainbow = remember {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it % 360f, 0.85f, 1f) },
        )
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color.hsv(hue % 360f, 0.85f, 1f)))
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp).weight(1f))
            Text("${(amount * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(rainbow))
            Slider(
                value = hue,
                onValueChange = { onHue(it.roundToInt().toFloat()) },
                valueRange = 0f..360f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    thumbColor = Color.hsv(hue % 360f, 0.85f, 1f),
                ),
            )
        }
        Slider(value = amount, onValueChange = { onAmount((it * 100).roundToInt() / 100f) }, valueRange = 0f..1f)
    }
}

/** Detect the beats of a music clip, then cut the video on them. */
@Composable
fun BeatSyncPanel(clip: AudioClip, detecting: Boolean, onDetect: () -> Unit, onSync: (every: Int) -> Unit) {
    var every by remember { mutableStateOf(2) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (clip.beatsMs.isEmpty()) {
            Text(
                "Frame listens to this track on your phone and finds its beats. Nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.Button(onClick = onDetect, enabled = !detecting, modifier = Modifier.fillMaxWidth()) {
                if (detecting) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text("Listening…")
                } else {
                    Text("Detect beats")
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♩ ${clip.bpm.roundToInt()} BPM", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "  ·  ${clip.beatsMs.size} beats",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDetect, enabled = !detecting) { Text("Re-detect") }
            }
            Text("Cut every", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Beat", 2 to "2 beats", 4 to "Bar (4)").forEach { (n, label) ->
                    FilterChip(selected = every == n, onClick = { every = n }, label = { Text(label) })
                }
            }
            androidx.compose.material3.Button(onClick = { onSync(every) }, modifier = Modifier.fillMaxWidth()) {
                Text("Sync clips to the beat")
            }
        }
    }
}

private fun effectColors(group: EffectGroup): List<Color> = when (group) {
    EffectGroup.WEATHER -> listOf(Color(0xFF3A6EA5), Color(0xFF8FB8DE))
    EffectGroup.PARTY -> listOf(Color(0xFFE0518C), Color(0xFFF6A84B))
    EffectGroup.FILM -> listOf(Color(0xFF6B4A2B), Color(0xFFE0A15A))
    EffectGroup.MOTION -> listOf(Color(0xFF5B63F0), Color(0xFF9B6BFF))
}

@Composable
private fun EffectTile(type: SceneEffectType, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp).bounceClick(onClick = onClick)) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(effectColors(type.group)))
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(type.emoji, fontSize = 26.sp)
        }
        Text(
            type.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Browse and add scene effects; the new effect starts at the playhead. */
@Composable
fun EffectsPanel(current: SceneEffectType?, onPick: (SceneEffectType) -> Unit) {
    var group by remember { mutableStateOf(current?.group ?: EffectGroup.WEATHER) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EffectGroup.entries.forEach { g ->
                FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g.label) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SceneEffectType.entries.filter { it.group == group }) { type ->
                EffectTile(type, selected = type == current) { onPick(type) }
            }
        }
        if (current == null) {
            Text(
                "Effects start at the playhead. Drag the amber bar on the timeline to move or trim them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EffectEditPanel(effect: SceneEffect, onType: (SceneEffectType) -> Unit, onIntensity: (Float) -> Unit) {
    Column {
        ValueSlider("Strength", "${(effect.intensity * 100).roundToInt()}", effect.intensity, 0.05f..1f, onIntensity)
        EffectsPanel(current = effect.type, onPick = onType)
    }
}

private val CROPS = listOf<Pair<String, Float?>>(
    "Free" to null, "1:1" to 1f, "4:5" to 0.8f, "9:16" to 9f / 16f, "16:9" to 16f / 9f, "3:4" to 0.75f, "4:3" to 4f / 3f,
)

@Composable
fun CropPanel(clip: VideoClip, onCrop: (Float?) -> Unit) {
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CROPS.forEach { (label, ratio) ->
                val selected = if (ratio == null) clip.crop == null else clip.crop == centerCrop(clip.width, clip.height, ratio)
                ShapeCard(label = if (ratio == null) "Original" else label, ratio = ratio ?: (clip.width.toFloat() / clip.height.coerceAtLeast(1)), selected = selected) {
                    onCrop(ratio)
                }
            }
        }
        if (clip.width <= 0 || clip.height <= 0) {
            Text("Crop isn't available for this clip.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun CanvasPanel(
    project: app.frame.studio.domain.Project,
    onAspect: (CanvasAspect) -> Unit,
    onPreset: (SocialPreset) -> Unit,
) {
    val aspect = project.aspect
    Column {
        SocialPresetPicker(project, onPreset, Modifier.padding(bottom = 10.dp))
        Text("Shape", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CanvasAspect.entries.forEach { a ->
                ShapeCard(label = a.label, ratio = a.ratio ?: (16f / 9f), selected = aspect == a, dashed = a.ratio == null) { onAspect(a) }
            }
        }
        Text(
            when (aspect) {
                CanvasAspect.R9_16 -> "Reels, TikTok, Shorts"
                CanvasAspect.R16_9 -> "YouTube, TV"
                CanvasAspect.R1_1, CanvasAspect.R4_5 -> "Instagram feed"
                CanvasAspect.ORIGINAL -> "Follows your first clip"
                else -> "Clips are fitted inside the frame"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Platform tabs (Instagram, TikTok, YouTube…) and that platform's one-tap formats.
 * Shared by the editor's Canvas panel and the export screen.
 */
@Composable
fun SocialPresetPicker(
    project: app.frame.studio.domain.Project,
    onPreset: (SocialPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var platform by remember { mutableStateOf(SocialPresets.matching(project)?.platform ?: SocialPlatform.INSTAGRAM) }
    val active = SocialPresets.matching(project, platform)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SocialPlatform.entries.forEach { p ->
                val selected = p == platform
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest)
                        .bounceClick { platform = p }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color(p.color.toInt())))
                    Text(
                        p.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialPresets.forPlatform(platform).forEach { preset ->
                SocialPresetPill(preset, selected = active?.id == preset.id) { onPreset(preset) }
            }
        }
        active?.let { SocialPresets.lengthWarning(it, TimelineMath.totalVideoMs(project.videoClips)) }?.let { warning ->
            Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** Pill with a tiny frame-shape glyph, used for one-tap social formats. */
@Composable
fun SocialPresetPill(preset: SocialPreset, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val r = preset.aspect.ratio ?: (4f / 3f)
        Box(
            Modifier
                .then(if (r >= 1f) Modifier.width(18.dp) else Modifier.height(18.dp))
                .aspectRatio(r)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(preset.platform.color.toInt()).copy(alpha = 0.85f)),
        )
        Column(Modifier.padding(start = 8.dp)) {
            Text(preset.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(preset.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShapeCard(label: String, ratio: Float, selected: Boolean, dashed: Boolean = false, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .bounceClick(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            val r = ratio.coerceIn(0.3f, 3f)
            Box(
                Modifier
                    .then(if (r >= 1f) Modifier.width(32.dp) else Modifier.height(32.dp))
                    .aspectRatio(r)
                    .border(if (dashed) 1.dp else 2.dp, color, RoundedCornerShape(3.dp)),
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun TransitionPanel(clip: VideoClip, onChange: (TransitionType, Long) -> Unit, onApplyAll: () -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TransitionType.entries.forEach { type ->
                val selected = clip.transition == type
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onChange(type, clip.transitionMs) }
                        .padding(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (type) {
                                    TransitionType.NONE -> MaterialTheme.colorScheme.surfaceVariant
                                    TransitionType.FADE_BLACK -> Color.Black
                                    TransitionType.FADE_WHITE -> Color.White
                                },
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    )
                    Text(
                        type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        if (clip.transition != TransitionType.NONE) {
            ValueSlider("Duration", formatDuration(clip.transitionMs), clip.transitionMs / 1000f, 0.1f..2f, {
                onChange(clip.transition, (it * 10).roundToInt() * 100L)
            })
        }
        TextButton(onClick = onApplyAll) { Text("Apply to all cuts") }
    }
}

@Composable
fun AddAudioPanel(onMusic: () -> Unit, onSound: () -> Unit, onVoice: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigTile(Icons.Filled.MusicNote, "Music", "Your files", Modifier.weight(1f), onMusic)
        BigTile(Icons.Filled.GraphicEq, "Sound", "Effects", Modifier.weight(1f), onSound)
        BigTile(Icons.Filled.Mic, "Voice", "Record", Modifier.weight(1f), onVoice)
    }
}

@Composable
private fun BigTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .bounceClick(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun VoicePanel(recording: Boolean, elapsedMs: Long, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (recording) formatDuration(elapsedMs) else "Recording starts at the playhead",
            style = if (recording) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .padding(6.dp)
                .clip(if (recording) RoundedCornerShape(8.dp) else CircleShape)
                .background(Color(0xFFFF453A))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = if (recording) "Stop" else "Record", tint = Color.White)
        }
    }
}

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

@Composable
private fun TextPresetChip(preset: TextPreset, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            preset.name,
            color = Color(preset.color.toInt()),
            fontWeight = if (preset.bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = fontFamilyFor(preset.font),
            style = MaterialTheme.typography.labelLarge,
            modifier = if (preset.background) Modifier.background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp) else Modifier,
        )
    }
}

@Composable
private fun fontFamilyFor(id: String): androidx.compose.ui.text.font.FontFamily =
    remember(id) {
        if (id == app.frame.studio.text.TextFonts.DEFAULT_ID) androidx.compose.ui.text.font.FontFamily.Default
        else androidx.compose.ui.text.font.FontFamily(app.frame.studio.text.TextFonts.typeface(id))
    }

private val STICKERS = listOf(
    "😀", "😂", "😍", "🥳", "😎", "🤯", "😭", "😡", "👍", "👏", "🙌", "🙏", "💪", "🔥", "✨", "⭐",
    "❤️", "💯", "🎉", "🎂", "🎵", "📍", "☀️", "🌙", "🌈", "⚡", "❄️", "🌸", "🍕", "☕", "✈️", "🏖️",
    "⚽", "🏀", "🎮", "📸", "🎬", "💡", "✅", "❌", "➡️", "⬇️", "👀", "💬", "🚀", "🐶", "🐱", "🦄",
)

@Composable
fun StickersPanel(onSticker: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(44.dp),
        modifier = Modifier.fillMaxWidth().height(168.dp),
    ) {
        items(STICKERS) { emoji ->
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSticker(emoji) },
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 26.sp) }
        }
    }
}

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
                        .semantics { contentDescription = "Custom color"; role = Role.Button }
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

@Composable
fun CaptionsPanel(
    hasCaptions: Boolean,
    style: app.frame.studio.domain.CaptionStyle,
    autoSupported: Boolean,
    progress: Float?,
    onStyle: (app.frame.studio.domain.CaptionStyle) -> Unit,
    onAuto: () -> Unit,
    onCancel: () -> Unit,
    onTyped: (String) -> Unit,
    onClear: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.frame.studio.domain.CaptionStyle.entries.forEach { s ->
                FilterChip(selected = s == style, onClick = { onStyle(s) }, label = { Text(s.label) })
            }
        }
        if (progress != null) {
            Text(
                "Listening on your phone… ${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onCancel) { Text("Cancel") }
            return@Column
        }
        androidx.compose.material3.Button(onClick = onAuto, enabled = autoSupported, modifier = Modifier.fillMaxWidth()) {
            Text(if (hasCaptions) "Redo auto captions" else "Auto captions from speech")
        }
        Text(
            if (autoSupported) "Speech is recognised by your phone's on-device engine. Nothing is uploaded."
            else "Auto captions need Android 13+ with an on-device speech engine. Type them instead — Frame times and animates each word.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.take(500) },
                placeholder = { Text("Or type what's said…") },
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.FilledTonalButton(
                onClick = {
                    onTyped(typed)
                    typed = ""
                },
                enabled = typed.isNotBlank(),
            ) { Text("Add") }
        }
        if (hasCaptions) {
            TextButton(onClick = onClear) { Text("Remove all captions") }
        }
    }
}
