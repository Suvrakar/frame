package app.frame.studio.media

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build

data class Inspection(val video: VideoTrack?, val audio: AudioTrack?)

/** Reads track facts from a media Uri. Never throws; returns null when unreadable. */
class MediaInspector(private val context: Context) {

    fun inspect(uri: Uri): Inspection? {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, uri, null)
            var video: VideoTrack? = null
            var audio: AudioTrack? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (video == null && mime.startsWith("video/")) {
                    video = VideoTrack(
                        mime = mime,
                        width = intOr(f, MediaFormat.KEY_WIDTH, 0),
                        height = intOr(f, MediaFormat.KEY_HEIGHT, 0),
                        fps = fpsOf(f),
                        hdr = isHdr(f, mime),
                    )
                } else if (audio == null && mime.startsWith("audio/")) {
                    audio = AudioTrack(
                        mime = mime,
                        sampleRate = intOr(f, MediaFormat.KEY_SAMPLE_RATE, 0),
                        channels = intOr(f, MediaFormat.KEY_CHANNEL_COUNT, 0),
                    )
                }
            }
            Inspection(video, audio)
        } catch (e: Exception) {
            null
        } finally {
            try { ex.release() } catch (_: Exception) {}
        }
    }

    private fun intOr(f: MediaFormat, key: String, d: Int): Int =
        try { if (f.containsKey(key)) f.getInteger(key) else d } catch (_: Exception) { d }

    /** KEY_FRAME_RATE may be stored as Int or Float; 0 means unknown. */
    private fun fpsOf(f: MediaFormat): Float {
        if (!f.containsKey(MediaFormat.KEY_FRAME_RATE)) return 0f
        return try { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() } catch (_: Exception) {
            try { f.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { 0f }
        }
    }

    /**
     * HDR/10-bit if the transfer function is PQ (ST2084) or HLG, or the profile is a
     * known 10-bit HEVC / VP9 / AV1 profile.
     */
    private fun isHdr(f: MediaFormat, mime: String): Boolean {
        if (Build.VERSION.SDK_INT >= 24 && f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
            val t = intOr(f, MediaFormat.KEY_COLOR_TRANSFER, 0)
            if (t == MediaFormat.COLOR_TRANSFER_ST2084 || t == MediaFormat.COLOR_TRANSFER_HLG) return true
        }
        if (!f.containsKey(MediaFormat.KEY_PROFILE)) return false
        val p = intOr(f, MediaFormat.KEY_PROFILE, -1)
        return when (mime) {
            "video/hevc" -> p == CodecProfileLevel.HEVCProfileMain10 ||
                p == CodecProfileLevel.HEVCProfileMain10HDR10 ||
                p == CodecProfileLevel.HEVCProfileMain10HDR10Plus
            "video/x-vnd.on2.vp9" -> p == CodecProfileLevel.VP9Profile2 ||
                p == CodecProfileLevel.VP9Profile2HDR ||
                p == CodecProfileLevel.VP9Profile2HDR10Plus
            "video/av01" -> p == CodecProfileLevel.AV1ProfileMain10 ||
                p == CodecProfileLevel.AV1ProfileMain10HDR10 ||
                p == CodecProfileLevel.AV1ProfileMain10HDR10Plus
            else -> false
        }
    }
}

/** Decoder table backed by the device's MediaCodecList; cached per mime; never throws. */
class AndroidDecoderTable : DecoderTable {
    private val infos: List<MediaCodecInfo> by lazy {
        try {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
        } catch (_: Exception) { emptyList() }
    }
    private val videoCache = HashMap<String, List<VideoDecoder>>()
    private val audioCache = HashMap<String, List<AudioDecoder>>()

    @Synchronized
    override fun video(mime: String): List<VideoDecoder> = videoCache.getOrPut(mime) {
        infos.mapNotNull { info ->
            try {
                if (info.supportedTypes.none { it.equals(mime, true) }) return@mapNotNull null
                val v = info.getCapabilitiesForType(mime).videoCapabilities ?: return@mapNotNull null
                VideoDecoder(
                    name = info.name,
                    hardware = isHardware(info),
                    maxWidth = v.supportedWidths.upper,
                    maxHeight = v.supportedHeights.upper,
                    supports = { w, h, fps -> safe { v.areSizeAndRateSupported(w, h, fps.toDouble()) } },
                    supportsSize = { w, h -> safe { v.isSizeSupported(w, h) } },
                )
            } catch (_: Exception) { null }
        }
    }

    @Synchronized
    override fun audio(mime: String): List<AudioDecoder> = audioCache.getOrPut(mime) {
        infos.mapNotNull { info ->
            try {
                if (info.supportedTypes.any { it.equals(mime, true) }) AudioDecoder(info.name) else null
            } catch (_: Exception) { null }
        }
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
        else !info.name.startsWith("c2.android.") && !info.name.startsWith("OMX.google.")

    private inline fun safe(block: () -> Boolean): Boolean =
        try { block() } catch (_: Exception) { false }
}
