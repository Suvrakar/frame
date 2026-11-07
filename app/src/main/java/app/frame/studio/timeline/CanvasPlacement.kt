package app.frame.studio.timeline

import app.frame.studio.domain.CanvasFill
import app.frame.studio.domain.VideoClip
import app.frame.studio.export.ExportPlan
import kotlin.math.abs

/**
 * Where a main clip sits on the canvas (pure; JVM-tested). Its base box is the fitted size
 * ([CanvasFill.FIT] / [CanvasFill.BLUR]) or the covering size ([CanvasFill.FILL]); the user's
 * pinch, drag and twist ([VideoClip.placeX] …) are applied on top. Defaults = the base box, centred.
 */
object CanvasPlacement {
    const val MIN_SCALE = 0.1f
    const val MAX_SCALE = 5f
    private const val SNAP_POSITION = 0.02f
    private const val SNAP_SCALE = 0.04f

    fun isDefault(c: VideoClip): Boolean =
        c.placeX == 0.5f && c.placeY == 0.5f && c.placeScale == 1f && c.placeRotation == 0f

    fun transform(c: VideoClip): Transform = Transform(c.placeX, c.placeY, c.placeScale, c.placeRotation, 1f)

    /** Base box size in canvas fractions (width, height); may exceed 1 for Fill. */
    fun baseBox(clip: VideoClip, canvasW: Int, canvasH: Int, fill: CanvasFill): Pair<Float, Float> {
        val (w, h) = ExportPlan.clipFrame(clip)
        val clipAspect = w / h
        val canvasAspect = canvasW.toFloat() / canvasH.coerceAtLeast(1)
        val wider = clipAspect > canvasAspect
        return if (fill == CanvasFill.FILL) {
            if (wider) (clipAspect / canvasAspect) to 1f else 1f to (canvasAspect / clipAspect)
        } else {
            if (wider) 1f to (canvasAspect / clipAspect) else (clipAspect / canvasAspect) to 1f
        }
    }

    /** Keeps the clip reachable and the size sane. */
    fun clamp(t: Transform): Transform = t.copy(
        x = t.x.coerceIn(-0.5f, 1.5f),
        y = t.y.coerceIn(-0.5f, 1.5f),
        scale = t.scale.coerceIn(MIN_SCALE, MAX_SCALE),
        opacity = 1f,
    )

    /** Snaps to the canvas centre lines and back to the exact base size when close. */
    fun snap(t: Transform): Transform = t.copy(
        x = if (abs(t.x - 0.5f) < SNAP_POSITION) 0.5f else t.x,
        y = if (abs(t.y - 0.5f) < SNAP_POSITION) 0.5f else t.y,
        scale = if (abs(t.scale - 1f) < SNAP_SCALE) 1f else t.scale,
    )
}
