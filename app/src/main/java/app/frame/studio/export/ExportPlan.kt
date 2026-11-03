package app.frame.studio.export

import app.frame.studio.domain.AudioClip
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

    /** Fade envelope for one clip on the output timeline. */
    data class FadeWindow(val startMs: Long, val endMs: Long, val fadeInMs: Long, val fadeOutMs: Long)

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
    fun outputSize(clips: List<VideoClip>, target: ExportHeight): Size {
        val (w, h) = clips.firstOrNull()?.let(::clipFrame)
            ?: (FALLBACK_WIDTH.toFloat() to FALLBACK_HEIGHT.toFloat())
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
    fun fadeWindows(clips: List<VideoClip>, transitionMs: Long): List<FadeWindow> {
        val starts = TimelineMath.videoStarts(clips)
        val half = transitionMs.coerceAtLeast(0) / 2
        return clips.mapIndexed { i, clip ->
            val start = starts[i]
            val duration = clip.outputDurationMs()
            val fadeIn = max(clip.fadeInMs, if (i > 0) half else 0).coerceAtMost(duration / 2)
            val fadeOut = max(clip.fadeOutMs, if (i < clips.lastIndex) half else 0)
                .coerceAtMost(duration / 2)
            FadeWindow(start, start + duration, fadeIn, fadeOut)
        }
    }

    /** Brightness multiplier (0..1) at output time [tMs]. */
    fun brightnessAt(windows: List<FadeWindow>, tMs: Long): Float {
        val window = windows.firstOrNull { tMs >= it.startMs && tMs < it.endMs } ?: return 1f
        var factor = 1f
        if (window.fadeInMs > 0) {
            factor = min(factor, (tMs - window.startMs).toFloat() / window.fadeInMs)
        }
        if (window.fadeOutMs > 0) {
            factor = min(factor, (window.endMs - tMs).toFloat() / window.fadeOutMs)
        }
        return factor.coerceIn(0f, 1f)
    }

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

    /**
     * The music track must not outlast the video: clips past the end are dropped and the last
     * one is shortened.
     */
    fun fitAudio(audio: List<AudioClip>, videoDurationMs: Long): List<AudioClip> {
        val result = mutableListOf<AudioClip>()
        var acc = 0L
        for (clip in audio) {
            if (acc >= videoDurationMs) break
            val remaining = videoDurationMs - acc
            val fitted = if (clip.outputDurationMs() > remaining) {
                clip.copy(trimEndMs = clip.trimStartMs + remaining)
            } else {
                clip
            }
            if (fitted.outputDurationMs() > 0) result += fitted
            acc += fitted.outputDurationMs()
        }
        return result
    }
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
