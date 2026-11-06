package app.frame.studio.media

/** Facts about a file's video track. [hdr] is true for HDR or 10-bit video. */
data class VideoTrack(val mime: String, val width: Int, val height: Int, val fps: Float, val hdr: Boolean)

data class AudioTrack(val mime: String, val sampleRate: Int, val channels: Int)

data class VideoDecoder(
    val name: String,
    val hardware: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val supports: (w: Int, h: Int, fps: Int) -> Boolean,
    val supportsSize: (w: Int, h: Int) -> Boolean,
)

data class AudioDecoder(val name: String)

interface DecoderTable {
    fun video(mime: String): List<VideoDecoder>
    fun audio(mime: String): List<AudioDecoder>
}

sealed interface VideoVerdict {
    data object Ok : VideoVerdict
    data class Heavy(val reason: String) : VideoVerdict
    data class Unsupported(val message: String) : VideoVerdict
}

/** Pure import decisions; Android code only gathers the facts. */
object ImportPolicy {
    private const val LOW_END_BUDGET = 1920L * 1080L * 60L

    /**
     * [decodeVerified]: a real frame of this file was decoded on this phone. Declared decoder
     * limits are conservative (a Snapdragon 685 declares 1920x1920 yet decodes 4K), so a verified
     * decode turns "too large" into Heavy (imported, edited through a preview proxy).
     */
    fun video(track: VideoTrack, table: DecoderTable, lowEnd: Boolean, decodeVerified: Boolean = false): VideoVerdict {
        val decoders = table.video(track.mime)
        if (decoders.isEmpty()) {
            return VideoVerdict.Unsupported(
                "This video's format (${codecName(track.mime)}) isn't supported on this phone.",
            )
        }
        val w = track.width
        val h = track.height
        val fps = if (track.fps > 0f) Math.round(track.fps) else 30
        val sizeOk = decoders.filter { it.supportsSize(w, h) || it.supportsSize(h, w) }
        if (sizeOk.isEmpty()) {
            if (decodeVerified) return VideoVerdict.Heavy("This phone under-reports support for this video size.")
            val maxShort = decoders.maxOf { minOf(it.maxWidth, it.maxHeight) }
            return VideoVerdict.Unsupported(
                "This ${friendlySize(minOf(w, h))} video is too large for this phone " +
                    "(up to ${friendlySize(maxShort)}).",
            )
        }
        val rateOk = sizeOk.filter { it.supports(w, h, fps) || it.supports(h, w, fps) }
        if (rateOk.isEmpty()) {
            return VideoVerdict.Heavy("This phone may play this video slower than real time.")
        }
        if (rateOk.none { it.hardware }) {
            return VideoVerdict.Heavy("This phone can only decode this video in software.")
        }
        if (lowEnd && w.toLong() * h * fps > LOW_END_BUDGET) {
            return VideoVerdict.Heavy("This video is demanding for this phone.")
        }
        if (lowEnd && track.hdr) {
            return VideoVerdict.Heavy("HDR video is demanding for this phone.")
        }
        return VideoVerdict.Ok
    }

    fun audioSupported(track: AudioTrack, table: DecoderTable): Boolean =
        table.audio(track.mime).isNotEmpty()

    fun friendlySize(shortSide: Int): String = when {
        shortSide >= 4320 -> "8K"
        shortSide >= 2160 -> "4K"
        shortSide >= 1440 -> "2K"
        shortSide >= 1080 -> "1080p"
        shortSide >= 720 -> "720p"
        else -> "${shortSide}p"
    }

    fun codecName(mime: String): String = when (mime.lowercase()) {
        "video/avc" -> "H.264"
        "video/hevc" -> "HEVC"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/av01" -> "AV1"
        "audio/ac3" -> "Dolby Digital (AC-3)"
        "audio/eac3" -> "Dolby Digital Plus"
        else -> mime.substringAfter('/').uppercase()
    }
}
