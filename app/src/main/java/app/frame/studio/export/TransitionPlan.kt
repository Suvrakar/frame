package app.frame.studio.export

import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip
import app.frame.studio.timeline.TimelineMath

/** A shader transition around one cut: [halfMs] before and after [cutMs] on the output timeline. */
data class TransitionWindow(val cutMs: Long, val halfMs: Long, val type: TransitionType)

/** The transition at one moment: [progress] -1..0 on the outgoing clip, 0..1 on the incoming one. */
data class TransitionFrame(val type: TransitionType, val progress: Float)

/**
 * Where shader transitions happen (pure; JVM-tested). A transition is split across its cut: the
 * first half animates the end of the outgoing clip, the second half the start of the next one,
 * so clips never overlap and the movie's length is unchanged. Fades keep the colour-dip path
 * (ExportPlan.fadeWindows).
 */
object TransitionPlan {
    private val FREE = setOf(
        TransitionType.NONE, TransitionType.FADE_BLACK, TransitionType.FADE_WHITE,
        TransitionType.ZOOM_IN, TransitionType.WHIP_LEFT,
    )

    fun usesShader(type: TransitionType): Boolean =
        type != TransitionType.NONE && type != TransitionType.FADE_BLACK && type != TransitionType.FADE_WHITE

    fun isPro(type: TransitionType): Boolean = type !in FREE

    /** The number the GL program switches on (1-based, stable per type). */
    fun shaderId(type: TransitionType): Int = when (type) {
        TransitionType.ZOOM_IN -> 1
        TransitionType.ZOOM_OUT -> 2
        TransitionType.WHIP_LEFT -> 3
        TransitionType.WHIP_RIGHT -> 4
        TransitionType.WHIP_UP -> 5
        TransitionType.WHIP_DOWN -> 6
        TransitionType.SPIN -> 7
        TransitionType.BLUR -> 8
        TransitionType.GLITCH -> 9
        TransitionType.PIXELATE -> 10
        TransitionType.IRIS -> 11
        TransitionType.SHAKE -> 12
        TransitionType.BURN -> 13
        TransitionType.NONE, TransitionType.FADE_BLACK, TransitionType.FADE_WHITE -> 0
    }

    fun windows(clips: List<VideoClip>): List<TransitionWindow> {
        if (clips.size < 2) return emptyList()
        val starts = TimelineMath.videoStarts(clips)
        return (0 until clips.lastIndex).mapNotNull { i ->
            val clip = clips[i]
            if (!usesShader(clip.transition)) return@mapNotNull null
            val a = clip.outputDurationMs()
            val b = clips[i + 1].outputDurationMs()
            val half = minOf(clip.transitionMs.coerceAtLeast(0) / 2, a / 2, b / 2)
            if (half <= 0) null else TransitionWindow(starts[i] + a, half, clip.transition)
        }
    }

    fun at(windows: List<TransitionWindow>, tMs: Long): TransitionFrame? {
        val w = windows.firstOrNull { tMs >= it.cutMs - it.halfMs && tMs < it.cutMs + it.halfMs } ?: return null
        return TransitionFrame(w.type, ((tMs - w.cutMs).toFloat() / w.halfMs).coerceIn(-1f, 1f))
    }
}
