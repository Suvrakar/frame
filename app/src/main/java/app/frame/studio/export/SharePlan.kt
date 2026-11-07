package app.frame.studio.export

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a finished video can go, and the file each destination keeps best. Social apps re-encode
 * every upload; files bigger, sharper or faster than they keep get compressed hardest (and often
 * downscaled with a poor scaler). Handing them a file already in their sweet spot means their
 * re-encode changes little, so the post looks like the export. YouTube and "other apps" get the
 * untouched original.
 */
enum class ShareTarget(
    val label: String,
    /** Packages to try, in order (main app, then business / regional variants). */
    val packages: List<String>,
    /** Short side the destination keeps; null = send the original. */
    val maxShortSide: Int?,
    val maxFps: Int,
    /** Video bitrate at 1080 short side (scaled by pixel count for other sizes). */
    val bitrateAt1080: Int,
    val audioKbps: Int,
    /** A tip shown before handing over. */
    val tip: String?,
    /** Longest single clip the main surface takes (stories), for a friendly warning. */
    val storyLimitMs: Long? = null,
) {
    WHATSAPP(
        "WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b"), maxShortSide = 720, maxFps = 30,
        bitrateAt1080 = 7_000_000, audioKbps = 128, tip = "In WhatsApp, tap HD before sending to keep it sharp.",
        storyLimitMs = 60_000,
    ),
    INSTAGRAM(
        "Instagram", listOf("com.instagram.android"), maxShortSide = 1080, maxFps = 30,
        bitrateAt1080 = 8_000_000, audioKbps = 128, tip = "Choose Reel, Story or Post in Instagram.",
        storyLimitMs = 60_000,
    ),
    FACEBOOK(
        "Facebook", listOf("com.facebook.katana", "com.facebook.lite"), maxShortSide = 1080, maxFps = 30,
        bitrateAt1080 = 8_000_000, audioKbps = 128, tip = "Choose Reel, Story or Post in Facebook.",
        storyLimitMs = 60_000,
    ),
    TIKTOK(
        "TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), maxShortSide = 1080, maxFps = 60,
        bitrateAt1080 = 10_000_000, audioKbps = 192, tip = null,
    ),
    YOUTUBE(
        "YouTube", listOf("com.google.android.youtube"), maxShortSide = null, maxFps = 60,
        bitrateAt1080 = 0, audioKbps = 192, tip = "YouTube keeps the most detail from the full-quality file.",
    ),
}

/** What we know about an exported file. */
data class VideoFacts(val width: Int, val height: Int, val fps: Float, val bitrate: Int, val durationMs: Long)

/** The upload-ready version to make for a destination (null = share the original as is). */
data class UploadSpec(val width: Int, val height: Int, val fps: Int, val bitrate: Int, val audioKbps: Int)

object SharePlan {
    /** Bitrates up to this much over the target are left alone (re-encoding would cost more). */
    private const val BITRATE_SLACK = 1.35f

    fun spec(source: VideoFacts, target: ShareTarget): UploadSpec? {
        val maxShort = target.maxShortSide ?: return null
        val short = min(source.width, source.height)
        val scale = if (short > maxShort) maxShort.toFloat() / short else 1f
        val w = even(source.width * scale)
        val h = even(source.height * scale)
        val fps = if (source.fps > target.maxFps + 0.5f) target.maxFps else source.fps.roundToInt().coerceAtLeast(1)
        val bitrate = (target.bitrateAt1080 * (w.toLong() * h) / (1920L * 1080L)).toInt().coerceAtLeast(1_000_000)
        val tooBig = scale < 1f
        val tooFast = fps < source.fps - 0.5f
        val tooHeavy = source.bitrate > bitrate * BITRATE_SLACK
        return if (tooBig || tooFast || tooHeavy) UploadSpec(w, h, fps, minOf(bitrate, source.bitrate.coerceAtLeast(1_000_000)), target.audioKbps) else null
    }

    /** A friendly note when a story would be split or cut. */
    fun lengthNote(source: VideoFacts, target: ShareTarget): String? {
        val limit = target.storyLimitMs ?: return null
        if (source.durationMs <= limit) return null
        return "Longer than ${limit / 1000}s: ${target.label} Stories may split or trim it. Reels and posts are fine."
    }

    private fun even(v: Float): Int = maxOf(2, (v / 2f).roundToInt() * 2)
}
