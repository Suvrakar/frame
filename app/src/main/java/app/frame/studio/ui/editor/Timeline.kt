package app.frame.studio.ui.editor

import app.frame.studio.ui.common.icon
import androidx.compose.material.icons.filled.PictureInPicture
import app.frame.studio.domain.OverlayClip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.frame.studio.FrameApp
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.AudioKind
import app.frame.studio.domain.Project
import app.frame.studio.domain.SceneEffect
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip
import app.frame.studio.effects.Filters
import app.frame.studio.timeline.AudioLayout
import app.frame.studio.timeline.TimelineMath
import app.frame.studio.timeline.TimelineOps
import app.frame.studio.ui.common.VideoThumbnail
import app.frame.studio.ui.common.formatDuration
import app.frame.studio.ui.theme.TrackColors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private val RULER_HEIGHT = 22.dp
private val VIDEO_TRACK_HEIGHT = 56.dp
private val LANE_HEIGHT = 30.dp
private val LANE_GAP = 6.dp
private val HANDLE_WIDTH = 14.dp
private val FRAME_WIDTH = 42.dp

/** In-progress drag of a timeline item; committed on release. */
private data class TrimDrag(val id: String, val edge: Edge, val deltaMs: Float)
private enum class Edge { START, END, BODY }

@Composable
fun Timeline(
    app: FrameApp,
    project: Project,
    controller: PreviewController,
    selection: Selection,
    missing: Set<String>,
    dpPerSecond: Float,
    onZoom: (Float) -> Unit,
    onSelect: (Selection) -> Unit,
    onTrimVideo: (id: String, startMs: Long, endMs: Long) -> Unit,
    onImageDuration: (id: String, ms: Long) -> Unit,
    onPlaceAudio: (id: String, startMs: Long, trimStartMs: Long, trimEndMs: Long) -> Unit,
    onTextRange: (id: String, startMs: Long, endMs: Long) -> Unit,
    onEffectRange: (id: String, startMs: Long, endMs: Long) -> Unit,
    onPlaceOverlay: (id: String, startMs: Long, trimStartMs: Long, trimEndMs: Long) -> Unit,
    onTransition: (clipId: String) -> Unit,
    onAddMedia: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val density = LocalDensity.current
    val pxPerMs = with(density) { dpPerSecond.dp.toPx() } / 1000f
    val pxPerMsState = rememberUpdatedState(pxPerMs)
    val scroll = rememberScrollState()
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    var userScrolling by remember { mutableStateOf(false) }
    val userScrollingState = rememberUpdatedState(userScrolling)
    var drag by remember { mutableStateOf<TrimDrag?>(null) }
    val edges = rememberUpdatedState(TimelineMath.videoEdges(project.videoClips))

    // Scrubbing: the user's finger owns the playhead until the scroll (including fling) settles.
    LaunchedEffect(dragged) {
        if (dragged) {
            userScrolling = true
            controller.pause()
            controller.setScrubbing(true)
        }
    }
    LaunchedEffect(scroll.isScrollInProgress, dragged) {
        if (!scroll.isScrollInProgress && !dragged && userScrolling) {
            userScrolling = false
            controller.setScrubbing(false)
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }.collect { value ->
            if (userScrollingState.value) {
                val raw = (value / pxPerMsState.value).toLong()
                val threshold = (6f * density.density / pxPerMsState.value).toLong()
                controller.scrubTo(TimelineOps.snap(raw, edges.value, threshold))
            }
        }
    }
    // Follow playback, seeks and zoom.
    // Read through snapshotFlow so playback never recomposes the timeline itself.
    LaunchedEffect(pxPerMs, userScrolling) {
        if (userScrolling) return@LaunchedEffect
        snapshotFlow { controller.positionMs }.collect { position ->
            scroll.scrollTo((position * pxPerMs).roundToInt())
        }
    }

    fun dp(ms: Float): Dp = with(density) { (ms * pxPerMs).toDp() }

    // Apply any in-progress drag so the timeline reacts live.
    val videoClips = project.videoClips.map { clip ->
        val d = drag
        if (d != null && d.id == clip.id) previewVideoTrim(clip, d) else clip
    }
    val audioClips = AudioLayout.normalize(project.audioClips).map { clip ->
        val d = drag
        if (d != null && d.id == clip.id) previewAudio(clip, d) else clip
    }
    val texts = project.textOverlays.map { o ->
        val d = drag
        if (d != null && d.id == o.id) previewText(o, d) else o
    }
    val totalMs = TimelineMath.totalVideoMs(videoClips)
    val audioLanes = AudioLayout.lanes(audioClips)
    val audioLaneCount = (audioLanes.values.maxOrNull() ?: -1) + 1
    val textLanes = remember(texts) { textLanes(texts) }
    val textLaneCount = (textLanes.values.maxOrNull() ?: -1) + 1
    val effects = project.effects.map { e ->
        val d = drag
        if (d != null && d.id == e.id) previewEffect(e, d) else e
    }
    val effectLanes = remember(effects) { rangeLanes(effects.map { Triple(it.id, it.startMs, it.endMs) }) }
    val effectLaneCount = (effectLanes.values.maxOrNull() ?: -1) + 1
    val overlays = project.overlays.map { o ->
        val d = drag
        if (d != null && d.id == o.id) previewOverlay(o, d) else o
    }
    val overlayLanes = remember(overlays) { rangeLanes(overlays.map { Triple(it.id, it.startMs, it.endMs()) }) }
    val overlayLaneCount = (overlayLanes.values.maxOrNull() ?: -1) + 1

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(Unit) {
                // Two-finger pinch zooms the timeline; one finger is left to scrolling.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                onZoom(zoom)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val half = maxWidth / 2
        val halfPx = with(density) { half.toPx() }
        val viewportPx = with(density) { maxWidth.toPx() }
        val contentWidth = half * 2 + dp(totalMs.toFloat())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.horizontalScroll(scroll).width(contentWidth)) {
                Column(Modifier.width(contentWidth).padding(bottom = if (compact) 0.dp else 12.dp)) {
                    Ruler(totalMs, pxPerMs, half, Modifier.width(contentWidth).height(RULER_HEIGHT))

                    // ---- Video track ----
                    Box(Modifier.width(contentWidth).height(VIDEO_TRACK_HEIGHT)) {
                        var x = 0f
                        videoClips.forEachIndexed { index, clip ->
                            val width = clip.outputDurationMs() * 1f
                            val selected = selection is Selection.Video && selection.id == clip.id
                            VideoTile(
                                app = app,
                                clip = clip,
                                width = dp(width),
                                pxPerMs = pxPerMs,
                                tileLeftPx = halfPx + x * pxPerMs,
                                viewportPx = viewportPx,
                                scroll = scroll,
                                selected = selected,
                                isMissing = clip.sourceUri in missing,
                                modifier = Modifier.offset(x = half + dp(x)),
                                onClick = { onSelect(if (selected) Selection.None else Selection.Video(clip.id)) },
                                onDrag = { edge, deltaPx ->
                                    val current = drag?.takeIf { it.id == clip.id && it.edge == edge }
                                    drag = TrimDrag(clip.id, edge, (current?.deltaMs ?: 0f) + deltaPx / pxPerMs)
                                },
                                onDragStart = { controller.pause() },
                                onDragEnd = {
                                    val d = drag
                                    // The item can vanish mid-drag (undo, delete); then there is nothing to apply.
                                    val original = project.videoClips.firstOrNull { it.id == clip.id }
                                    if (d != null && original != null) {
                                        val result = previewVideoTrim(original, d)
                                        if (original.isImage) onImageDuration(clip.id, result.outputDurationMs())
                                        else onTrimVideo(clip.id, result.trimStartMs, result.trimEndMs)
                                    }
                                    drag = null
                                },
                            )
                            x += width
                        }
                        // Transition buttons at each cut.
                        var cut = 0f
                        videoClips.forEachIndexed { index, clip ->
                            cut += clip.outputDurationMs()
                            if (index < videoClips.lastIndex) {
                                TransitionButton(
                                    active = clip.transition != TransitionType.NONE || project.crossfadeMs > 0,
                                    modifier = Modifier
                                        .offset(x = half + dp(cut) - 11.dp)
                                        .align(Alignment.CenterStart),
                                    onClick = { onTransition(clip.id) },
                                )
                            }
                        }
                    }

                    // ---- Overlay lanes (picture-in-picture) ----
                    for (lane in 0 until if (compact) 0 else overlayLaneCount) {
                        Box(Modifier.padding(top = LANE_GAP).width(contentWidth).height(LANE_HEIGHT)) {
                            overlays.filter { overlayLanes[it.id] == lane }.forEach { o ->
                                val selected = selection is Selection.Overlay && selection.id == o.id
                                LaneItem(
                                    markers = o.keyframes.map { dp(it.atMs.toFloat()) },
                                    label = o.displayName.ifBlank { if (o.isImage) "Photo overlay" else "Video overlay" },
                                    icon = Icons.Filled.PictureInPicture,
                                    color = if (o.sourceUri in missing) MaterialTheme.colorScheme.error else TrackColors.overlay,
                                    width = dp(o.durationMs().toFloat()),
                                    selected = selected,
                                    dimmedAfter = null,
                                    modifier = Modifier.offset(x = half + dp(o.startMs.toFloat())),
                                    onClick = { onSelect(if (selected) Selection.None else Selection.Overlay(o.id)) },
                                    onDragStart = { controller.pause() },
                                    onDrag = { edge, deltaPx ->
                                        val current = drag?.takeIf { it.id == o.id && it.edge == edge }
                                        drag = TrimDrag(o.id, edge, (current?.deltaMs ?: 0f) + deltaPx / pxPerMs)
                                    },
                                    onDragEnd = {
                                        val d = drag
                                        // The item can vanish mid-drag (undo, delete); then there is nothing to apply.
                                        val original = project.overlays.firstOrNull { it.id == o.id }
                                        if (d != null && original != null) {
                                            val r = previewOverlay(original, d)
                                            onPlaceOverlay(o.id, r.startMs, r.trimStartMs, r.trimEndMs)
                                        }
                                        drag = null
                                    },
                                )
                            }
                        }
                    }

                    // ---- Audio lanes ----
                    for (lane in 0 until if (compact) 0 else audioLaneCount) {
                        Box(Modifier.padding(top = LANE_GAP).width(contentWidth).height(LANE_HEIGHT)) {
                            audioClips.filter { audioLanes[it.id] == lane }.forEach { clip ->
                                val selected = selection is Selection.Audio && selection.id == clip.id
                                LaneItem(
                                    markers = clip.beatsMs
                                        .filter { it >= clip.trimStartMs && it < clip.trimEndMs }
                                        .map { dp((it - clip.trimStartMs).toFloat()) },
                                    label = clip.displayName.ifBlank { clip.kind.label },
                                    icon = when (clip.kind) {
                                        AudioKind.MUSIC -> Icons.Filled.MusicNote
                                        AudioKind.VOICE -> Icons.Filled.Mic
                                        AudioKind.SOUND -> Icons.Filled.GraphicEq
                                    },
                                    color = if (clip.sourceUri in missing) MaterialTheme.colorScheme.error else TrackColors.audio(clip.kind),
                                    width = dp(clip.outputDurationMs().toFloat()),
                                    selected = selected,
                                    dimmedAfter = if (clip.endMs() > totalMs) dp((totalMs - clip.startMs).coerceAtLeast(0).toFloat()) else null,
                                    modifier = Modifier.offset(x = half + dp(clip.startMs.toFloat())),
                                    onClick = { onSelect(if (selected) Selection.None else Selection.Audio(clip.id)) },
                                    onDragStart = { controller.pause() },
                                    onDrag = { edge, deltaPx ->
                                        val current = drag?.takeIf { it.id == clip.id && it.edge == edge }
                                        drag = TrimDrag(clip.id, edge, (current?.deltaMs ?: 0f) + deltaPx / pxPerMs)
                                    },
                                    onDragEnd = {
                                        val d = drag
                                        // The item can vanish mid-drag (undo, delete); then there is nothing to apply.
                                        val original = AudioLayout.normalize(project.audioClips).firstOrNull { it.id == clip.id }
                                        if (d != null && original != null) {
                                            val r = previewAudio(original, d)
                                            onPlaceAudio(clip.id, r.startMs, r.trimStartMs, r.trimEndMs)
                                        }
                                        drag = null
                                    },
                                )
                            }
                        }
                    }

                    // ---- Text lanes ----
                    for (lane in 0 until if (compact) 0 else textLaneCount) {
                        Box(Modifier.padding(top = LANE_GAP).width(contentWidth).height(LANE_HEIGHT)) {
                            texts.filter { textLanes[it.id] == lane }.forEach { o ->
                                val selected = selection is Selection.Text && selection.id == o.id
                                LaneItem(
                                    markers = o.keyframes.map { dp(it.atMs.toFloat()) },
                                    label = o.text,
                                    icon = if (o.isSticker) null else Icons.Filled.TextFields,
                                    color = if (o.isSticker) TrackColors.sticker else TrackColors.text,
                                    width = dp((o.endMs - o.startMs).toFloat()),
                                    selected = selected,
                                    dimmedAfter = null,
                                    modifier = Modifier.offset(x = half + dp(o.startMs.toFloat())),
                                    onClick = { onSelect(if (selected) Selection.None else Selection.Text(o.id)) },
                                    onDragStart = { controller.pause() },
                                    onDrag = { edge, deltaPx ->
                                        val current = drag?.takeIf { it.id == o.id && it.edge == edge }
                                        drag = TrimDrag(o.id, edge, (current?.deltaMs ?: 0f) + deltaPx / pxPerMs)
                                    },
                                    onDragEnd = {
                                        val d = drag
                                        // The item can vanish mid-drag (undo, delete); then there is nothing to apply.
                                        val original = project.textOverlays.firstOrNull { it.id == o.id }
                                        if (d != null && original != null) {
                                            val r = previewText(original, d)
                                            onTextRange(o.id, r.startMs, r.endMs)
                                        }
                                        drag = null
                                    },
                                )
                            }
                        }
                    }

                    // ---- Effect lanes ----
                    for (lane in 0 until if (compact) 0 else effectLaneCount) {
                        Box(Modifier.padding(top = LANE_GAP).width(contentWidth).height(LANE_HEIGHT)) {
                            effects.filter { effectLanes[it.id] == lane }.forEach { e ->
                                val selected = selection is Selection.Effect && selection.id == e.id
                                LaneItem(
                                    label = e.type.label,
                                    icon = e.type.icon,
                                    color = TrackColors.effect,
                                    width = dp((e.endMs - e.startMs).toFloat()),
                                    selected = selected,
                                    dimmedAfter = null,
                                    modifier = Modifier.offset(x = half + dp(e.startMs.toFloat())),
                                    onClick = { onSelect(if (selected) Selection.None else Selection.Effect(e.id)) },
                                    onDragStart = { controller.pause() },
                                    onDrag = { edge, deltaPx ->
                                        val current = drag?.takeIf { it.id == e.id && it.edge == edge }
                                        drag = TrimDrag(e.id, edge, (current?.deltaMs ?: 0f) + deltaPx / pxPerMs)
                                    },
                                    onDragEnd = {
                                        val d = drag
                                        // The item can vanish mid-drag (undo, delete); then there is nothing to apply.
                                        val original = project.effects.firstOrNull { it.id == e.id }
                                        if (d != null && original != null) {
                                            val r = previewEffect(original, d)
                                            onEffectRange(e.id, r.startMs, r.endMs)
                                        }
                                        drag = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Centered playhead.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.onSurface),
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface),
        )

        // Add media, pinned at the right edge beside the video track.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = RULER_HEIGHT + (VIDEO_TRACK_HEIGHT - 40.dp) / 2, end = 12.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onAddMedia),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add media", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

private fun previewVideoTrim(clip: VideoClip, d: TrimDrag): VideoClip {
    if (clip.isImage) {
        val delta = if (d.edge == Edge.START) -d.deltaMs else d.deltaMs
        val duration = (clip.outputDurationMs() + delta).toLong().coerceIn(500, VideoClip.MAX_IMAGE_MS)
        return clip.copy(trimStartMs = 0, trimEndMs = duration)
    }
    val rate = clip.speed.coerceIn(VideoClip.MIN_SPEED, VideoClip.MAX_SPEED)
    val deltaSource = (d.deltaMs * rate).toLong()
    val minLen = (TimelineOps.MIN_SPLIT_MS * rate).toLong()
    return when (d.edge) {
        Edge.START -> clip.copy(trimStartMs = (clip.trimStartMs + deltaSource).coerceIn(0, clip.trimEndMs - minLen))
        Edge.END -> clip.copy(trimEndMs = (clip.trimEndMs + deltaSource).coerceIn(clip.trimStartMs + minLen, clip.sourceDurationMs))
        Edge.BODY -> clip
    }
}

private fun previewAudio(clip: AudioClip, d: TrimDrag): AudioClip {
    val delta = d.deltaMs.toLong()
    val min = TimelineOps.MIN_SPLIT_MS
    return when (d.edge) {
        Edge.BODY -> clip.copy(startMs = (clip.startMs + delta).coerceAtLeast(0))
        Edge.START -> {
            val newTrimStart = (clip.trimStartMs + delta).coerceIn(
                (clip.trimStartMs - clip.startMs).coerceAtLeast(0),
                clip.trimEndMs - min,
            )
            val shift = newTrimStart - clip.trimStartMs
            clip.copy(trimStartMs = newTrimStart, startMs = clip.startMs + shift)
        }
        Edge.END -> clip.copy(trimEndMs = (clip.trimEndMs + delta).coerceIn(clip.trimStartMs + min, clip.sourceDurationMs))
    }
}

private fun previewText(o: TextOverlay, d: TrimDrag): TextOverlay {
    val delta = d.deltaMs.toLong()
    return when (d.edge) {
        Edge.BODY -> {
            val length = o.endMs - o.startMs
            val start = (o.startMs + delta).coerceAtLeast(0)
            o.copy(startMs = start, endMs = start + length)
        }
        Edge.START -> o.copy(startMs = (o.startMs + delta).coerceIn(0, o.endMs - 100))
        Edge.END -> o.copy(endMs = (o.endMs + delta).coerceAtLeast(o.startMs + 100))
    }
}

private fun previewOverlay(o: OverlayClip, d: TrimDrag): OverlayClip {
    val delta = d.deltaMs.toLong()
    return when (d.edge) {
        Edge.BODY -> o.copy(startMs = (o.startMs + delta).coerceAtLeast(0))
        Edge.START -> {
            // Trimming the head keeps the rest of the clip where it is on the timeline.
            val minDelta = maxOf(-o.trimStartMs, -o.startMs)
            val dd = delta.coerceIn(minDelta, o.durationMs() - 300)
            o.copy(startMs = o.startMs + dd, trimStartMs = o.trimStartMs + dd)
        }
        Edge.END -> o.copy(trimEndMs = (o.trimEndMs + delta).coerceIn(o.trimStartMs + 300, o.sourceDurationMs))
    }
}

private fun previewEffect(e: SceneEffect, d: TrimDrag): SceneEffect {
    val delta = d.deltaMs.toLong()
    return when (d.edge) {
        Edge.BODY -> {
            val length = e.endMs - e.startMs
            val start = (e.startMs + delta).coerceAtLeast(0)
            e.copy(startMs = start, endMs = start + length)
        }
        Edge.START -> e.copy(startMs = (e.startMs + delta).coerceIn(0, e.endMs - 100))
        Edge.END -> e.copy(endMs = (e.endMs + delta).coerceAtLeast(e.startMs + 100))
    }
}

/** Greedy lane assignment for (id, start, end) ranges. */
private fun rangeLanes(items: List<Triple<String, Long, Long>>): Map<String, Int> {
    val laneEnds = mutableListOf<Long>()
    val result = mutableMapOf<String, Int>()
    items.sortedBy { it.second }.forEach { (id, start, end) ->
        var lane = laneEnds.indexOfFirst { it <= start }
        if (lane < 0) {
            lane = laneEnds.size
            laneEnds += 0L
        }
        laneEnds[lane] = end
        result[id] = lane
    }
    return result
}

private fun textLanes(overlays: List<TextOverlay>): Map<String, Int> {
    val laneEnds = mutableListOf<Long>()
    val result = mutableMapOf<String, Int>()
    overlays.sortedBy { it.startMs }.forEach { o ->
        var lane = laneEnds.indexOfFirst { it <= o.startMs }
        if (lane < 0) {
            lane = laneEnds.size
            laneEnds += 0L
        }
        laneEnds[lane] = o.endMs
        result[o.id] = lane
    }
    return result
}

@Composable
private fun Ruler(totalMs: Long, pxPerMs: Float, startPad: Dp, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tickColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val style = TextStyle(fontSize = 9.sp, color = labelColor)
    Canvas(modifier) {
        val start = with(density) { startPad.toPx() }
        // Choose a label step that keeps labels at least ~56dp apart.
        val minGapMs = 56.dp.toPx() / pxPerMs
        val steps = longArrayOf(250, 500, 1_000, 2_000, 5_000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000)
        val step = steps.firstOrNull { it >= minGapMs } ?: steps.last()
        val minor = step / 2
        var t = 0L
        while (t <= totalMs) {
            val x = start + t * pxPerMs
            val major = t % step == 0L
            drawLine(
                tickColor,
                Offset(x, size.height - if (major) 8.dp.toPx() else 4.dp.toPx()),
                Offset(x, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            if (major) {
                drawText(measurer, formatRuler(t), topLeft = Offset(x + 3.dp.toPx(), 1.dp.toPx()), style = style)
            }
            t += minor
        }
    }
}

private fun formatRuler(ms: Long): String {
    val s = ms / 1000
    val frac = (ms % 1000) / 100
    return if (ms % 1000 == 0L) String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60)
    else String.format(java.util.Locale.US, "%d.%ds", s, frac)
}

@Composable
private fun TransitionButton(active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.SwapHoriz,
            contentDescription = "Transition",
            tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Drag gestures for the two trim handles and (optionally) the body of a selected item. */
private fun Modifier.dragEdge(
    key: Any,
    edge: Edge,
    onDragStart: () -> Unit,
    onDrag: (Edge, Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(key, edge) {
    detectHorizontalDragGestures(
        onDragStart = { onDragStart() },
        onDragEnd = onDragEnd,
        onDragCancel = onDragEnd,
    ) { change, dx ->
        change.consume()
        onDrag(edge, dx)
    }
}

@Composable
private fun VideoTile(
    app: FrameApp,
    clip: VideoClip,
    width: Dp,
    pxPerMs: Float,
    tileLeftPx: Float,
    viewportPx: Float,
    scroll: androidx.compose.foundation.ScrollState,
    selected: Boolean,
    isMissing: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onDrag: (Edge, Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
) {
    val onDragState = rememberUpdatedState(onDrag)
    val onDragStartState = rememberUpdatedState(onDragStart)
    val onDragEndState = rememberUpdatedState(onDragEnd)
    val shape = RoundedCornerShape(6.dp)
    val density = LocalDensity.current
    val color = Filters.transformFor(clip.filterId, clip.filterIntensity, clip.adjust)
    val colorFilter = if (color.isIdentity) null else ColorFilter.colorMatrix(ColorMatrix(color.toAndroid()))
    Box(modifier.width(width).height(VIDEO_TRACK_HEIGHT)) {
        // Filmstrip
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 1.dp)
                .clip(shape)
                .clickable(onClick = onClick),
        ) {
            val framePx = with(density) { FRAME_WIDTH.toPx() }
            val frameMs = framePx / pxPerMs
            val frames = max(1, ceil(clip.outputDurationMs() / frameMs).toInt()).coerceAtMost(400)
            val rate = if (clip.isImage) 0f else clip.speed
            // Only frames within one screen of the viewport are decoded; the rest are spacers.
            val visible by remember(tileLeftPx, framePx, viewportPx) {
                derivedStateOf {
                    val from = ((scroll.value - viewportPx - tileLeftPx) / framePx).toInt()
                    val to = ((scroll.value + 2 * viewportPx - tileLeftPx) / framePx).toInt()
                    from..to
                }
            }
            repeat(frames) { i ->
                if (i !in visible) {
                    Box(Modifier.width(FRAME_WIDTH).fillMaxHeight())
                } else {
                    // Quantized so nearby zoom levels reuse cached frames.
                    val at = (clip.trimStartMs + (i * frameMs * rate).toLong()) / 250 * 250
                    VideoThumbnail(
                        probe = app.mediaProbe,
                        uri = if (isMissing) null else clip.sourceUri,
                        atMs = at,
                        isImage = clip.isImage,
                        maxSize = app.device.filmstripFrameSize,
                        rotationDegrees = clip.rotationDegrees,
                        colorFilter = colorFilter,
                        showPlaceholderIcon = i == 0,
                        // Dozens of frames appear at once while scrolling; skip per-frame fades.
                        animate = false,
                        modifier = Modifier.width(FRAME_WIDTH).fillMaxHeight(),
                    )
                }
            }
        }
        if (isMissing) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error.copy(alpha = 0.5f)))
        // Badges
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (selected) HANDLE_WIDTH + 2.dp else 4.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Badge(formatDuration(clip.outputDurationMs()))
            if (!clip.isImage && clip.speed != 1f) Badge("${clip.speed}x")
            if (clip.isImage) Icon(Icons.Outlined.Image, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.primary, shape))
            TrimHandle(
                Modifier
                    .align(Alignment.CenterStart)
                    .dragEdge(clip.id, Edge.START, { onDragStartState.value() }, { e, d -> onDragState.value(e, d) }, { onDragEndState.value() }),
                start = true,
            )
            TrimHandle(
                Modifier
                    .align(Alignment.CenterEnd)
                    .dragEdge(clip.id, Edge.END, { onDragStartState.value() }, { e, d -> onDragState.value(e, d) }, { onDragEndState.value() }),
                start = false,
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

@Composable
private fun TrimHandle(modifier: Modifier, start: Boolean) {
    val shape = if (start) RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp) else RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
    Box(
        modifier
            .width(HANDLE_WIDTH)
            .fillMaxHeight()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).height(16.dp).clip(RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)))
    }
}

@Composable
private fun LaneItem(
    markers: List<Dp> = emptyList(),
    label: String,
    icon: ImageVector?,
    color: Color,
    width: Dp,
    selected: Boolean,
    dimmedAfter: Dp?,
    modifier: Modifier,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Edge, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val onDragState = rememberUpdatedState(onDrag)
    val onDragStartState = rememberUpdatedState(onDragStart)
    val onDragEndState = rememberUpdatedState(onDragEnd)
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.width(width.coerceAtLeast(6.dp)).height(LANE_HEIGHT)) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 1.dp)
                .clip(shape)
                .background(color.copy(alpha = 0.85f))
                .then(
                    if (selected) {
                        Modifier.dragEdge(label, Edge.BODY, { onDragStartState.value() }, { e, d -> onDragState.value(e, d) }, { onDragEndState.value() })
                    } else {
                        Modifier.clickable(onClick = onClick)
                    },
                )
                .padding(start = if (selected) HANDLE_WIDTH + 2.dp else 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Box(Modifier.width(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (markers.isNotEmpty()) {
            // Beat markers: small ticks along the bottom of the lane.
            val tick = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
            Canvas(Modifier.fillMaxSize()) {
                markers.forEach { x ->
                    val px = x.toPx()
                    if (px in 0f..size.width) {
                        drawLine(tick, Offset(px, size.height * 0.68f), Offset(px, size.height - 2.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                    }
                }
            }
        }
        if (dimmedAfter != null) {
            Box(
                Modifier
                    .offset(x = dimmedAfter)
                    .fillMaxHeight()
                    .width((width - dimmedAfter).coerceAtLeast(0.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
            )
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.primary, shape))
            TrimHandle(
                Modifier
                    .align(Alignment.CenterStart)
                    .dragEdge(label, Edge.START, { onDragStartState.value() }, { e, d -> onDragState.value(e, d) }, { onDragEndState.value() }),
                start = true,
            )
            TrimHandle(
                Modifier
                    .align(Alignment.CenterEnd)
                    .dragEdge(label, Edge.END, { onDragStartState.value() }, { e, d -> onDragState.value(e, d) }, { onDragEndState.value() }),
                start = false,
            )
        }
    }
}
