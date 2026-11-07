package app.frame.studio.text

import app.frame.studio.domain.TextAnimation
import app.frame.studio.domain.TextOverlay
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A text's animated pose at one moment, relative to its resting place. [dx]/[dy] are in frame
 * fractions (y down), [rotation] clockwise degrees, [reveal] the typed fraction (typewriter).
 */
data class TextPose(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rotation: Float = 0f,
    val reveal: Float = 1f,
) {
    companion object {
        val REST = TextPose()
    }
}

/** Entrance and exit motion for text and stickers (pure; JVM-tested). */
object TextMotion {
    /** The v1 fade, kept exactly for old projects. */
    private const val LEGACY_FADE_MS = 250L
    private val FREE = setOf(TextAnimation.NONE, TextAnimation.FADE, TextAnimation.POP, TextAnimation.SLIDE_UP)

    fun isPro(animation: TextAnimation): Boolean = animation !in FREE

    fun entrance(o: TextOverlay): TextAnimation = o.animIn ?: if (o.animate) TextAnimation.FADE else TextAnimation.NONE
    fun exit(o: TextOverlay): TextAnimation = o.animOut ?: if (o.animate) TextAnimation.FADE else TextAnimation.NONE

    /** Pose at [tMs] on the output timeline; null while the text is not on screen. */
    fun at(o: TextOverlay, tMs: Long): TextPose? {
        if (tMs < o.startMs || tMs >= o.endMs) return null
        val duration = o.endMs - o.startMs
        val legacy = o.animIn == null && o.animOut == null
        val ms = minOf(if (legacy) LEGACY_FADE_MS else o.animMs.coerceAtLeast(1), duration / 3)
        if (ms <= 0) return TextPose.REST
        val kIn = ((tMs - o.startMs).toFloat() / ms).coerceIn(0f, 1f)
        val kOut = ((o.endMs - tMs).toFloat() / ms).coerceIn(0f, 1f)
        val a = pose(entrance(o), kIn)
        val b = pose(exit(o), kOut)
        return TextPose(
            alpha = a.alpha * b.alpha,
            scale = a.scale * b.scale,
            dx = a.dx + b.dx,
            dy = a.dy + b.dy,
            rotation = a.rotation + b.rotation,
            reveal = minOf(a.reveal, b.reveal),
        )
    }

    /** Characters shown at [reveal] (whole letters; whitespace counts so spacing stays put). */
    fun visibleChars(text: String, reveal: Float): Int = (text.length * reveal.coerceIn(0f, 1f)).roundToInt()

    /** [k]: 0 = fully "away", 1 = at rest. */
    private fun pose(animation: TextAnimation, k: Float): TextPose {
        if (k >= 1f || animation == TextAnimation.NONE) return TextPose.REST
        val e = easeOutCubic(k)
        val fade = smooth(minOf(1f, k * 2.5f))
        return when (animation) {
            TextAnimation.NONE -> TextPose.REST
            TextAnimation.FADE -> TextPose(alpha = k)
            TextAnimation.POP -> TextPose(alpha = fade, scale = 0.3f + 0.7f * easeOutBack(k))
            TextAnimation.SLIDE_UP -> TextPose(alpha = fade, dy = 0.08f * (1 - e))
            TextAnimation.SLIDE_DOWN -> TextPose(alpha = fade, dy = -0.08f * (1 - e))
            TextAnimation.SLIDE_LEFT -> TextPose(alpha = fade, dx = 0.12f * (1 - e))
            TextAnimation.SLIDE_RIGHT -> TextPose(alpha = fade, dx = -0.12f * (1 - e))
            TextAnimation.ZOOM -> TextPose(alpha = fade, scale = 1.8f - 0.8f * e)
            TextAnimation.BOUNCE -> TextPose(alpha = smooth(minOf(1f, k * 4f)), dy = -0.15f * (1 - easeOutBounce(k)))
            TextAnimation.SPIN -> TextPose(alpha = fade, scale = 0.4f + 0.6f * e, rotation = -180f * (1 - e))
            TextAnimation.TYPEWRITER -> TextPose(reveal = k)
        }
    }

    private fun smooth(x: Float) = x * x * (3 - 2 * x)
    private fun easeOutCubic(x: Float) = 1 - (1 - x).pow(3)
    private fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1
        return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)
    }
    private fun easeOutBounce(x: Float): Float {
        val n = 7.5625f
        val d = 2.75f
        return when {
            x < 1 / d -> n * x * x
            x < 2 / d -> { val y = x - 1.5f / d; n * y * y + 0.75f }
            x < 2.5f / d -> { val y = x - 2.25f / d; n * y * y + 0.9375f }
            else -> { val y = x - 2.625f / d; n * y * y + 0.984375f }
        }
    }

}
