package app.frame.studio.ui.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.frame.studio.timeline.Transform
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * On-preview handles for the selected object: drag to move, pinch to resize, twist to rotate,
 * with one or two fingers anywhere on the preview. The outline follows the finger live; the
 * frame re-renders when the gesture ends. A tap without movement is passed to [onTap].
 *
 * [boxFraction] is the object's size at scale 1 in 0..1 frame units.
 *
 * [ghost], when given, is drawn inside the outline while the object is being moved and until the
 * preview has rendered the new placement ([frameVersion] changes), so the object itself follows
 * the finger instead of jumping once the preview rebuilds. After a gesture the committed placement
 * is held only while [transform] still equals the base it was committed from; once the view model
 * applies the (clamped) edit, [transform] is shown and only the ghost lingers. A commit that never
 * changes [transform] is dropped after [NOOP_WINDOW_MS]. Without a ghost nothing is held.
 */
@Composable
fun TransformFrame(
    transform: Transform,
    boxFraction: Pair<Float, Float>,
    frameW: Float,
    frameH: Float,
    keyed: Boolean,
    onCommit: (Transform) -> Unit,
    onTap: () -> Unit,
    frameVersion: Int = 0,
    ghost: (@Composable () -> Unit)? = null,
) {
    var live by remember { mutableStateOf<Transform?>(null) }
    // Committed placement, the frame version and the base transform at commit (ghost overlays only).
    var pending by remember { mutableStateOf<Held?>(null) }
    val latestBase = rememberUpdatedState(transform)
    LaunchedEffect(frameVersion) {
        val waiting = pending ?: return@LaunchedEffect
        if (frameVersion != waiting.version) {
            delay(GHOST_HANDOFF_MS) // let the rebuilt frame reach the screen before removing the ghost
            if (pending === waiting) pending = null
        }
    }
    LaunchedEffect(pending) {
        val held = pending ?: return@LaunchedEffect
        delay(NOOP_WINDOW_MS)
        if (pending === held && latestBase.value == held.base) pending = null // edit changed nothing
        delay(GHOST_TIMEOUT_MS - NOOP_WINDOW_MS)
        if (pending === held) pending = null
    }
    val latestVersion = rememberUpdatedState(frameVersion)
    val hasGhost = rememberUpdatedState(ghost != null)
    val held = pending
    // The held placement stands in only until the view model applies the edit.
    val shown = live ?: held?.takeIf { it.base == transform }?.placed ?: transform
    val showGhost = ghost != null && (live != null || held != null)
    val latestCommit = rememberUpdatedState(onCommit)
    val latestTap = rememberUpdatedState(onTap)
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(frameW, frameH) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var current = latestBase.value
                    var moved = false
                    var travel = 0f
                    do {
                        val event = awaitPointerEvent()
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        val rotation = event.calculateRotation()
                        travel += abs(pan.x) + abs(pan.y)
                        if (travel > viewConfiguration.touchSlop || zoom != 1f || rotation != 0f) {
                            if (pan != Offset.Zero || zoom != 1f || rotation != 0f) {
                                current = current.copy(
                                    x = current.x + pan.x / frameW,
                                    y = current.y + pan.y / frameH,
                                    scale = (current.scale * zoom).coerceIn(0.05f, 6f),
                                    rotation = current.rotation + rotation,
                                )
                                live = current
                                moved = true
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (moved) {
                        val committed = snapRotation(current)
                        if (hasGhost.value) pending = Held(committed, latestVersion.value, latestBase.value)
                        latestCommit.value(committed)
                    } else {
                        latestTap.value()
                    }
                    live = null
                }
            },
    ) {
        val w = (boxFraction.first * shown.scale * frameW).coerceAtLeast(24f)
        val h = (boxFraction.second * shown.scale * frameH).coerceAtLeast(24f)
        val cx = shown.x * frameW
        val cy = shown.y * frameH
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset((cx - w / 2).roundToInt(), (cy - h / 2).roundToInt()) }
                    .size(w.toDp(), h.toDp())
                    .graphicsLayer { rotationZ = shown.rotation }
                    .border(1.5.dp, if (keyed) Color(0xFFFFC94D) else Color.White, RoundedCornerShape(3.dp)),
            ) {
                if (showGhost) {
                    Box(Modifier.fillMaxSize()) { ghost?.invoke() }
                }
                // Corner dots hint that the object can be resized and rotated.
                listOf(Offset(0f, 0f), Offset(w, 0f), Offset(0f, h), Offset(w, h)).forEach { p ->
                    Box(
                        Modifier
                            .offset { IntOffset((p.x - 5.dp.toPx()).roundToInt(), (p.y - 5.dp.toPx()).roundToInt()) }
                            .size(10.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }
        }
    }
}

private class Held(val placed: Transform, val version: Int, val base: Transform)

/** Longest the moved object is drawn by Compose while waiting for the preview to re-render. */
private const val GHOST_TIMEOUT_MS = 4_000L
private const val GHOST_HANDOFF_MS = 120L

/** How long after a commit the transform may stay unchanged before the held state is dropped. */
private const val NOOP_WINDOW_MS = 300L

/** Snaps near-straight rotations to the nearest 90° so objects are easy to level. */
private fun snapRotation(t: Transform): Transform {
    val r = ((t.rotation % 360) + 360) % 360
    val nearest = (r / 90f).roundToInt() * 90f
    return if (abs(r - nearest) < 4f) t.copy(rotation = nearest % 360) else t.copy(rotation = r)
}
