package app.frame.studio.device

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.provider.Settings
import android.util.Log
import app.frame.studio.domain.ExportHeight

/**
 * One-time snapshot of what this phone can comfortably do. Low-end devices get a lighter
 * preview, smaller caches and fewer parallel decoders so editing stays smooth.
 */
class DeviceProfile(context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /** Heap available to the app, in MB. */
    val memoryClassMb: Int = activityManager.memoryClass

    private val totalRamBytes: Long = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }.totalMem

    val cores: Int = Runtime.getRuntime().availableProcessors()

    val isLowEnd: Boolean = activityManager.isLowRamDevice ||
        memoryClassMb <= 192 ||
        totalRamBytes in 1..(3L * 1024 * 1024 * 1024) ||
        cores <= 4

    /** Preview renders at this short side; the export still uses the chosen resolution. */
    val previewHeight: ExportHeight = if (isLowEnd) ExportHeight.SD_480 else ExportHeight.HD_720

    /** In-memory thumbnail cache: an eighth of the heap, capped. */
    val thumbnailCacheBytes: Int = (memoryClassMb * 1024 * 1024 / 8).coerceIn(8 * 1024 * 1024, 48 * 1024 * 1024)

    val filmstripFrameSize: Int = if (isLowEnd) 96 else 160

    val decoderParallelism: Int = if (isLowEnd) 2 else 3

    /** Photos larger than this (long side, px) are downscaled once on import. */
    val maxImageLongSide: Int = if (isLowEnd) 1920 else 3840

    /** Delay before rebuilding the preview after an edit (lets slider drags settle). */
    val previewRebuildDelayMs: Long = if (isLowEnd) 260 else 120

    /** Decorative animations are skipped on low-end phones or when the user turned them off. */
    val animationsEnabled: Boolean = !isLowEnd && runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }.getOrDefault(true)

    /** Export resolutions this phone's H.264 encoder can produce (16:9 landscape check). */
    val supportedExportHeights: List<ExportHeight> = run {
        val supported = ExportHeight.entries.filter { encoderSupports(it.pixels) }
        supported.ifEmpty { listOf(ExportHeight.SD_480, ExportHeight.HD_720) }
    }

    val defaultExportHeight: ExportHeight =
        if (isLowEnd || ExportHeight.HD_1080 !in supportedExportHeights) ExportHeight.HD_720 else ExportHeight.HD_1080

    /** Highest supported height not above [requested]. */
    fun clampExportHeight(requested: ExportHeight): ExportHeight =
        supportedExportHeights.filter { it.pixels <= requested.pixels }.maxByOrNull { it.pixels }
            ?: supportedExportHeights.minBy { it.pixels }

    private val avcCapabilities by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } }
                .map { it.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities }
        }.getOrDefault(emptyList())
    }

    private val rateCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Whether an H.264 encoder can produce [width]×[height] at [fps] on this phone. */
    fun supportsFrameRate(width: Int, height: Int, fps: Int): Boolean = rateCache.getOrPut("${width}x$height@$fps") {
        if (avcCapabilities.isEmpty()) return@getOrPut fps <= 30 || maxOf(width, height) <= 1920
        avcCapabilities.any { caps ->
            runCatching {
                caps.areSizeAndRateSupported(width, height, fps.toDouble()) ||
                    caps.areSizeAndRateSupported(height, width, fps.toDouble())
            }.getOrDefault(false)
        }
    }

    /** AAC bitrates (bps) the audio encoder accepts. */
    val audioBitrateRange: IntRange by lazy {
        runCatching {
            val ranges = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) } }
                .map { it.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC).audioCapabilities.bitrateRange }
            if (ranges.isEmpty()) 32_000..320_000 else ranges.minOf { it.lower }..ranges.maxOf { it.upper }
        }.getOrDefault(32_000..320_000)
    }

    fun supportsAudioBitrate(kbps: Int): Boolean = kbps * 1_000 in audioBitrateRange

    /** Highest supported frame rate not above [requested] for this size (falls back to 30). */
    fun clampFrameRate(width: Int, height: Int, requested: Int): Int =
        listOf(60, 50, 30, 25, 24).filter { it <= requested }.firstOrNull { supportsFrameRate(width, height, it) } ?: 30

    fun clampAudioBitrate(kbps: Int): Int = (kbps * 1_000).coerceIn(audioBitrateRange.first, audioBitrateRange.last) / 1_000

    private fun encoderSupports(shortSide: Int): Boolean = runCatching {
        val longSide = (shortSide * 16 / 9 + 1) / 2 * 2
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } }
            .any { info ->
                val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
                caps.isSizeSupported(longSide, shortSide) || caps.isSizeSupported(shortSide, longSide)
            }
    }.onFailure { Log.w("DeviceProfile", "Codec query failed", it) }.getOrDefault(shortSide <= 1080)
}
