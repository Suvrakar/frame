package app.frame.studio.export

import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ExportQuality
import app.frame.studio.domain.TransitionType
import app.frame.studio.timeline.AudioLayout
import app.frame.studio.domain.CropRect
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.VideoClip
import app.frame.studio.timeline.TimelineMath
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure, device-independent decisions about how a project is rendered. */
object ExportPlan {

    data class Size(val width: Int, val height: Int)

    /** Fade envelope for one clip on the output timeline. White fades flash instead of dipping. */
    data class FadeWindow(
        val startMs: Long,
        val endMs: Long,
        val fadeInMs: Long,
        val fadeOutMs: Long,
        val fadeInWhite: Boolean = false,
        val fadeOutWhite: Boolean = false,
    )

    /** Brightness factor (0..1) and whether the clip fades toward white rather than black. */
    data class Tone(val factor: Float, val white: Boolean)

    private const val FALLBACK_WIDTH = 1920
    private const val FALLBACK_HEIGHT = 1080

    /** Frame size of a clip after crop and user rotation. */
    fun clipFrame(clip: VideoClip): Pair<Float, Float> {
        var w = if (clip.width > 0) clip.width.toFloat() else FALLBACK_WIDTH.toFloat()
        var h = if (clip.height > 0) clip.height.toFloat() else FALLBACK_HEIGHT.toFloat()
        clip.crop?.let { crop ->
            w *= (crop.right - crop.left).coerceIn(0.05f, 1f)
            h *= (crop.bottom - crop.top).coerceIn(0.05f, 1f)
        }
        return if (clip.rotationDegrees % 180 != 0) h to w else w to h
    }

    /**
     * Output size: aspect ratio of the first clip, short side equal to the selected export height,
     * both dimensions even (required by most H.264 encoders).
     */
    fun outputSize(
        clips: List<VideoClip>,
        target: ExportHeight,
        aspect: CanvasAspect = CanvasAspect.ORIGINAL,
    ): Size {
        val ratio = aspect.ratio
        val (w, h) = when {
            ratio != null -> ratio to 1f
            else -> clips.firstOrNull()?.let(::clipFrame)
                ?: (FALLBACK_WIDTH.toFloat() to FALLBACK_HEIGHT.toFloat())
        }
        val shortSide = ExportPolicy.outputHeight(target)
        val scale = shortSide / min(w, h)
        return Size(even(w * scale), even(h * scale))
    }

    private fun even(value: Float): Int = max(2, (value / 2f).roundToInt() * 2)

    /** Media3 [androidx.media3.effect.Crop] takes normalized device coordinates (-1..1, y up). */
    fun cropToNdc(crop: CropRect): FloatArray {
        val left = crop.left.coerceIn(0f, 1f)
        val right = crop.right.coerceIn(left + 0.01f, 1f)
        val top = crop.top.coerceIn(0f, 1f)
        val bottom = crop.bottom.coerceIn(top + 0.01f, 1f)
        return floatArrayOf(left * 2 - 1, right * 2 - 1, 1 - bottom * 2, 1 - top * 2)
    }

    /**
     * Per-clip fade windows. The project transition ("crossfadeMs") is rendered as a dip to
     * black split evenly across the cut, so it never changes the total duration.
     */
    fun fadeWindows(clips: List<VideoClip>, legacyTransitionMs: Long): List<FadeWindow> {
        val starts = TimelineMath.videoStarts(clips)
        // Transition at the cut after clip i: (half duration, white?)
        val cuts = clips.mapIndexed { i, clip ->
            if (i == clips.lastIndex) {
                0L to false
            } else when (clip.transition) {
                TransitionType.NONE -> legacyTransitionMs.coerceAtLeast(0) / 2 to false
                TransitionType.FADE_BLACK -> clip.transitionMs.coerceAtLeast(0) / 2 to false
                TransitionType.FADE_WHITE -> clip.transitionMs.coerceAtLeast(0) / 2 to true
            }
        }
        return clips.mapIndexed { i, clip ->
            val start = starts[i]
            val duration = clip.outputDurationMs()
            val (inHalf, inWhite) = if (i > 0) cuts[i - 1] else (0L to false)
            val (outHalf, outWhite) = cuts[i]
            val fadeIn = max(clip.fadeInMs, inHalf).coerceAtMost(duration / 2)
            val fadeOut = max(clip.fadeOutMs, outHalf).coerceAtMost(duration / 2)
            FadeWindow(
                start, start + duration, fadeIn, fadeOut,
                fadeInWhite = inWhite && inHalf >= clip.fadeInMs,
                fadeOutWhite = outWhite && outHalf >= clip.fadeOutMs,
            )
        }
    }

    fun toneAt(windows: List<FadeWindow>, tMs: Long): Tone {
        val window = windows.firstOrNull { tMs >= it.startMs && tMs < it.endMs } ?: return Tone(1f, false)
        var factor = 1f
        var white = false
        if (window.fadeInMs > 0) {
            val f = (tMs - window.startMs).toFloat() / window.fadeInMs
            if (f < factor) {
                factor = f
                white = window.fadeInWhite
            }
        }
        if (window.fadeOutMs > 0) {
            val f = (window.endMs - tMs).toFloat() / window.fadeOutMs
            if (f < factor) {
                factor = f
                white = window.fadeOutWhite
            }
        }
        return Tone(factor.coerceIn(0f, 1f), white)
    }

    /** Brightness multiplier (0..1) at output time [tMs]. */
    fun brightnessAt(windows: List<FadeWindow>, tMs: Long): Float = toneAt(windows, tMs).factor

    /** Gain at [positionMs] inside a clip of [durationMs] with the given volume and fades. */
    fun gainAt(volume: Float, fadeInMs: Long, fadeOutMs: Long, durationMs: Long, positionMs: Long): Float {
        var gain = volume.coerceIn(0f, 2f)
        if (fadeInMs > 0 && positionMs < fadeInMs) {
            gain *= (positionMs.toFloat() / fadeInMs).coerceIn(0f, 1f)
        }
        val remaining = durationMs - positionMs
        if (fadeOutMs > 0 && remaining < fadeOutMs) {
            gain *= (remaining.toFloat() / fadeOutMs).coerceIn(0f, 1f)
        }
        return gain
    }

    /** Audio clips placed on the timeline and clipped to the video's length. */
    fun fitAudio(audio: List<AudioClip>, videoDurationMs: Long): List<AudioClip> =
        AudioLayout.clampTo(audio, videoDurationMs)

    /** Video bitrate for the chosen quality. */
    fun bitrate(size: Size, fps: Int, quality: ExportQuality): Int =
        (size.width.toLong() * size.height * fps.coerceIn(24, 60) * quality.bitsPerPixel).toLong()
            .coerceIn(1_000_000L, 80_000_000L).toInt()

    /** Rough output size in bytes (video + 128 kbps audio), for the export sheet. */
    /** Frame rates offered at export. Sources below the chosen rate keep their own rate. */
    val FRAME_RATES = listOf(24, 25, 30, 50, 60)

    /** AAC bitrates offered at export, in kbps. */
    val AUDIO_BITRATES_KBPS = listOf(96, 128, 192, 256, 320)

    /** Rough output size in bytes (video + audio), for the export sheet. */
    fun estimateBytes(durationMs: Long, size: Size, fps: Int, quality: ExportQuality, audioKbps: Int = 192): Long =
        (bitrate(size, fps, quality).toLong() + audioKbps * 1_000L) * durationMs / 8_000

}

/** Centered crop of a [width]x[height] frame to the aspect ratio [ratio] (width / height). */
fun centerCrop(width: Int, height: Int, ratio: Float): app.frame.studio.domain.CropRect? {
    if (width <= 0 || height <= 0 || ratio <= 0f) return null
    val source = width.toFloat() / height
    return if (source > ratio) {
        val fraction = ratio / source
        val inset = (1f - fraction) / 2f
        app.frame.studio.domain.CropRect(inset, 0f, 1f - inset, 1f)
    } else {
        val fraction = source / ratio
        val inset = (1f - fraction) / 2f
        app.frame.studio.domain.CropRect(0f, inset, 1f, 1f - inset)
    }
}
