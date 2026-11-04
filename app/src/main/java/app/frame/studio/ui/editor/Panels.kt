@file:OptIn(ExperimentalMaterial3Api::class)

package app.frame.studio.ui.editor

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.frame.studio.FrameApp
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ColorAdjust
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip
import app.frame.studio.effects.FilterGroup
import app.frame.studio.effects.Filters
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
    ADJUST("Adjust"),
    CROP("Crop"),
    TRANSITION("Transition"),
    CANVAS("Canvas"),
    AUDIO("Add audio"),
    VOICE("Voiceover"),
    STICKERS("Stickers"),
    TEXT_EDIT("Edit text"),
    TEXT_STYLE("Text style"),
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
fun FiltersPanel(app: FrameApp, clip: VideoClip, onFilter: (String) -> Unit, onIntensity: (Float) -> Unit) {
    var group by remember(clip.id) { mutableStateOf(Filters.byId(clip.filterId).group) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.width(64.dp).clickable { onFilter(filter.id) },
                ) {
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

private enum class AdjustKind(val label: String) { BRIGHTNESS("Brightness"), CONTRAST("Contrast"), SATURATION("Saturation"), WARMTH("Warmth") }

@Composable
fun AdjustPanel(adjust: ColorAdjust, onAdjust: (ColorAdjust) -> Unit) {
    var kind by remember { mutableStateOf(AdjustKind.BRIGHTNESS) }
    val value = when (kind) {
        AdjustKind.BRIGHTNESS -> adjust.brightness
        AdjustKind.CONTRAST -> adjust.contrast
        AdjustKind.SATURATION -> adjust.saturation
        AdjustKind.WARMTH -> adjust.warmth
    }
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdjustKind.entries.forEach { k ->
                val v = when (k) {
                    AdjustKind.BRIGHTNESS -> adjust.brightness
                    AdjustKind.CONTRAST -> adjust.contrast
                    AdjustKind.SATURATION -> adjust.saturation
                    AdjustKind.WARMTH -> adjust.warmth
                }
                FilterChip(
                    selected = kind == k,
                    onClick = { kind = k },
                    label = { Text(if (v != 0f) "${k.label} •" else k.label) },
                )
            }
        }
        ValueSlider(
            kind.label,
            "${(value * 100).roundToInt()}",
            value,
            -1f..1f,
            { raw ->
                val v = (raw * 100).roundToInt() / 100f
                onAdjust(
                    when (kind) {
                        AdjustKind.BRIGHTNESS -> adjust.copy(brightness = v)
                        AdjustKind.CONTRAST -> adjust.copy(contrast = v)
                        AdjustKind.SATURATION -> adjust.copy(saturation = v)
                        AdjustKind.WARMTH -> adjust.copy(warmth = v)
                    },
                )
            },
        )
        if (!adjust.isIdentity) {
            TextButton(onClick = { onAdjust(ColorAdjust()) }) { Text("Reset all") }
        }
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
    aspect: CanvasAspect,
    activePreset: SocialPreset?,
    onAspect: (CanvasAspect) -> Unit,
    onPreset: (SocialPreset) -> Unit,
) {
    Column {
        Text("Instagram", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SocialPresets.instagram.forEach { preset ->
                SocialPresetPill(preset, selected = activePreset?.id == preset.id) { onPreset(preset) }
            }
        }
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

/** Pill with a tiny frame-shape glyph, used for one-tap social formats. */
@Composable
fun SocialPresetPill(preset: SocialPreset, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val r = preset.aspect.ratio ?: 1f
        Box(
            Modifier
                .then(if (r >= 1f) Modifier.width(18.dp) else Modifier.height(18.dp))
                .aspectRatio(r)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFFB347), Color(0xFFFF5F8F), Color(0xFF8E5CFF)))),
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
            .clickable(onClick = onClick)
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
            .clickable(onClick = onClick)
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
                .border(3.dp, Color.White, CircleShape)
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
    val apply: (TextOverlay) -> TextOverlay,
)

private val TEXT_PRESETS = listOf(
    TextPreset("Classic", 0xFFFFFFFF, background = true, bold = false) { it.copy(color = 0xFFFFFFFF, background = true, bold = false, scale = 1f) },
    TextPreset("Pop", 0xFFFFD60A, background = false, bold = true) { it.copy(color = 0xFFFFD60A, background = false, bold = true, scale = 1.4f) },
    TextPreset("Neon", 0xFF64D2FF, background = false, bold = true) { it.copy(color = 0xFF64D2FF, background = false, bold = true, scale = 1.2f) },
    TextPreset("Elegant", 0xFFFFFFFF, background = false, bold = false) { it.copy(color = 0xFFFFFFFF, background = false, bold = false, scale = 1.1f) },
    TextPreset("Love", 0xFFFF375F, background = false, bold = true) { it.copy(color = 0xFFFF375F, background = false, bold = true, scale = 1.3f) },
    TextPreset("Caption", 0xFFFFD60A, background = true, bold = true) { it.copy(color = 0xFFFFD60A, background = true, bold = true, scale = 0.9f) },
)

@Composable
private fun TextPresetChip(preset: TextPreset, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            preset.name,
            color = Color(preset.color.toInt()),
            fontWeight = if (preset.bold) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge,
            modifier = if (preset.background) Modifier.background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp) else Modifier,
        )
    }
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
fun TextEditPanel(overlay: TextOverlay, onText: (String) -> Unit) {
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
}

@Composable
fun TextStylePanel(overlay: TextOverlay, onStyle: ((TextOverlay) -> TextOverlay) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!overlay.isSticker) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TEXT_PRESETS.forEach { preset ->
                    TextPresetChip(preset) { onStyle(preset.apply) }
                }
            }
        }
        if (!overlay.isSticker) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextSwatches.forEach { argb ->
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
}
