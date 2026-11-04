package app.frame.studio.domain

import kotlinx.serialization.Serializable

@Serializable
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Short side of the exported frame. */
@Serializable
enum class ExportHeight(val pixels: Int, val label: String) {
    SD_480(480, "480p"),
    HD_720(720, "720p"),
    HD_1080(1080, "1080p"),
    QHD_1440(1440, "2K"),
    UHD_2160(2160, "4K"),
}

/** Output canvas shape. ORIGINAL follows the first clip. */
@Serializable
enum class CanvasAspect(val ratio: Float?, val label: String) {
    ORIGINAL(null, "Original"),
    R9_16(9f / 16f, "9:16"),
    R16_9(16f / 9f, "16:9"),
    R1_1(1f, "1:1"),
    R4_5(4f / 5f, "4:5"),
    R3_4(3f / 4f, "3:4"),
    R4_3(4f / 3f, "4:3"),
    R21_9(21f / 9f, "21:9"),
}

@Serializable
enum class TransitionType(val label: String) {
    NONE("None"),
    FADE_BLACK("Fade"),
    FADE_WHITE("Flash"),
}

@Serializable
enum class ExportQuality(val label: String, val bitsPerPixel: Float) {
    STANDARD("Standard", 0.08f),
    HIGH("High", 0.14f),
    BEST("Best", 0.22f),
}

@Serializable
data class ColorAdjust(
    /** -1..1 */
    val brightness: Float = 0f,
    /** -1..1 */
    val contrast: Float = 0f,
    /** -1..1 */
    val saturation: Float = 0f,
    /** -1 (cool) .. 1 (warm) */
    val warmth: Float = 0f,
    /** 0..1, darkened corners */
    val vignette: Float = 0f,
    /** 0..1, film grain */
    val grain: Float = 0f,
) {
    val isColorIdentity: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f

    val isIdentity: Boolean
        get() = isColorIdentity && vignette == 0f && grain == 0f
}

@Serializable
data class VideoClip(
    val id: String,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0,
    val trimEndMs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val rotationDegrees: Int = 0,
    val crop: CropRect? = null,
    /** Display width after container rotation metadata is applied. 0 when unknown. */
    val width: Int = 0,
    /** Display height after container rotation metadata is applied. 0 when unknown. */
    val height: Int = 0,
    val displayName: String = "",
    /** Still photo shown for (trimEndMs - trimStartMs). */
    val isImage: Boolean = false,
    val filterId: String = "none",
    /** 0..1 */
    val filterIntensity: Float = 1f,
    val adjust: ColorAdjust = ColorAdjust(),
    /** Transition into the next clip. */
    val transition: TransitionType = TransitionType.NONE,
    val transitionMs: Long = 500,
    /** Mirror horizontally. */
    val flipH: Boolean = false,
) {
    fun outputDurationMs(): Long {
        val source = (trimEndMs - trimStartMs).coerceAtLeast(0)
        if (isImage) return source
        val safeSpeed = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        return (source / safeSpeed).toLong()
    }

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
        const val DEFAULT_IMAGE_MS = 3_000L
        /** Images can be stretched up to this length. */
        const val MAX_IMAGE_MS = 60_000L
    }
}

@Serializable
enum class AudioKind(val label: String) { MUSIC("Music"), VOICE("Voiceover"), SOUND("Sound") }

@Serializable
data class AudioClip(
    val id: String,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0,
    val trimEndMs: Long,
    val volume: Float = 1f,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val displayName: String = "",
    /** Position on the timeline. Negative means "after the previous clip" (projects from v1.0). */
    val startMs: Long = -1,
    val kind: AudioKind = AudioKind.MUSIC,
) {
    fun outputDurationMs(): Long = (trimEndMs - trimStartMs).coerceAtLeast(0)
    fun endMs(): Long = startMs.coerceAtLeast(0) + outputDurationMs()
}

@Serializable
data class TextOverlay(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val x: Float = 0.5f,
    val y: Float = 0.85f,
    /** ARGB */
    val color: Long = 0xFFFFFFFF,
    /** 1 = 100px per 1080px of short side. */
    val scale: Float = 1f,
    val background: Boolean = true,
    val bold: Boolean = false,
    /** Fade the text in and out. */
    val animate: Boolean = true,
    /** Emoji sticker: rendered larger and without a background. */
    val isSticker: Boolean = false,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAtEpochMs: Long,
    val videoClips: List<VideoClip> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    /** Legacy (v1.0) project-wide dip-to-black; used when a clip has no transition of its own. */
    val crossfadeMs: Long = 0,
    val exportHeight: ExportHeight = ExportHeight.HD_1080,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val aspect: CanvasAspect = CanvasAspect.ORIGINAL,
    val exportFps: Int = 30,
    val exportQuality: ExportQuality = ExportQuality.HIGH,
)

@Serializable
data class ExportRecord(
    val id: String,
    val projectId: String,
    val filePath: String,
    val createdAtEpochMs: Long,
    val height: Int,
    val projectName: String = "",
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    /** content:// URI of the copy saved to the shared Movies collection, if any. */
    val galleryUri: String? = null,
)
