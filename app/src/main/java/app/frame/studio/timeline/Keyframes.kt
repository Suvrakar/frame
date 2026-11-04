package app.frame.studio.timeline

import app.frame.studio.domain.Easing
import app.frame.studio.domain.Keyframe
import app.frame.studio.domain.OverlayClip
import kotlin.math.abs

/** An object's placement at one moment. */
data class Transform(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
)

/** Pure keyframe maths: interpolation and editing (unit tested). */
object Keyframes {

    /** Edits within this distance of a keyframe change that keyframe instead of adding one. */
    const val SNAP_MS = 80L

    fun ease(easing: Easing, t: Float): Float {
        val u = t.coerceIn(0f, 1f)
        return when (easing) {
            Easing.LINEAR -> u
            Easing.SMOOTH -> u * u * (3 - 2 * u)
            Easing.EASE_IN -> u * u
            Easing.EASE_OUT -> 1 - (1 - u) * (1 - u)
            Easing.HOLD -> 0f
        }
    }

    fun Keyframe.transform() = Transform(x, y, scale, rotation, opacity)

    /** The transform at [atMs] (relative to the object's start). */
    fun at(base: Transform, keyframes: List<Keyframe>, atMs: Long): Transform {
        if (keyframes.isEmpty()) return base
        val k = keyframes.sortedBy { it.atMs }
        if (atMs <= k.first().atMs) return k.first().transform()
        if (atMs >= k.last().atMs) return k.last().transform()
        val i = k.indexOfLast { it.atMs <= atMs }
        val a = k[i]
        val b = k[i + 1]
        val span = (b.atMs - a.atMs).coerceAtLeast(1)
        val t = ease(a.easing, (atMs - a.atMs).toFloat() / span)
        fun lerp(p: Float, q: Float) = p + (q - p) * t
        return Transform(lerp(a.x, b.x), lerp(a.y, b.y), lerp(a.scale, b.scale), lerp(a.rotation, b.rotation), lerp(a.opacity, b.opacity))
    }

    fun nearest(keyframes: List<Keyframe>, atMs: Long, withinMs: Long = SNAP_MS): Keyframe? =
        keyframes.filter { abs(it.atMs - atMs) <= withinMs }.minByOrNull { abs(it.atMs - atMs) }

    /** Adds a keyframe at [atMs] with [t], or updates the one already there. */
    fun upsert(keyframes: List<Keyframe>, atMs: Long, t: Transform): List<Keyframe> {
        val existing = nearest(keyframes, atMs)
        val kf = Keyframe(existing?.atMs ?: atMs, t.x, t.y, t.scale, t.rotation, t.opacity, existing?.easing ?: Easing.SMOOTH)
        return (keyframes.filter { it !== existing } + kf).sortedBy { it.atMs }
    }

    fun remove(keyframes: List<Keyframe>, atMs: Long): List<Keyframe> {
        val existing = nearest(keyframes, atMs) ?: return keyframes
        return keyframes.filter { it !== existing }
    }

    /**
     * Applies a move/resize/rotate made at [atMs]: with no keyframes the object's base placement
     * changes; once it is animated, the edit becomes (or updates) a keyframe at that moment.
     * Returns the new base and keyframes.
     */
    fun edit(base: Transform, keyframes: List<Keyframe>, atMs: Long, t: Transform): Pair<Transform, List<Keyframe>> =
        if (keyframes.isEmpty()) t to keyframes else base to upsert(keyframes, atMs, t)

    /** Keeps only keyframes within [0, durationMs] after a trim; shifts them by [shiftMs]. */
    fun retime(keyframes: List<Keyframe>, shiftMs: Long, durationMs: Long): List<Keyframe> =
        keyframes.map { it.copy(atMs = it.atMs - shiftMs) }.filter { it.atMs in 0..durationMs }

    fun base(o: OverlayClip) = Transform(o.x, o.y, o.scale, o.rotation, o.opacity)
}
