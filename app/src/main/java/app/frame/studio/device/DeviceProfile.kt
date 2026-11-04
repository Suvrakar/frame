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
