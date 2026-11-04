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
) {
    var live by remember { mutableStateOf<Transform?>(null) }
    val shown = live ?: transform
    val latestBase = rememberUpdatedState(transform)
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
                    if (moved) latestCommit.value(snapRotation(current)) else latestTap.value()
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

/** Snaps near-straight rotations to the nearest 90° so objects are easy to level. */
private fun snapRotation(t: Transform): Transform {
    val r = ((t.rotation % 360) + 360) % 360
    val nearest = (r / 90f).roundToInt() * 90f
    return if (abs(r - nearest) < 4f) t.copy(rotation = nearest % 360) else t.copy(rotation = r)
}
