package app.frame.studio.domain

import kotlinx.serialization.Serializable

@Serializable
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Serializable
enum class ExportHeight { HD_720, HD_1080 }

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
) {
    fun outputDurationMs(): Long {
        val source = (trimEndMs - trimStartMs).coerceAtLeast(0)
        val safeSpeed = speed.coerceIn(0.5f, 2f)
        return (source / safeSpeed).toLong()
    }
}

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
) {
    fun outputDurationMs(): Long = (trimEndMs - trimStartMs).coerceAtLeast(0)
}

@Serializable
data class TextOverlay(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val x: Float = 0.5f,
    val y: Float = 0.85f,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAtEpochMs: Long,
    val videoClips: List<VideoClip> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val crossfadeMs: Long = 0,
    val exportHeight: ExportHeight = ExportHeight.HD_1080,
    val updatedAtEpochMs: Long = createdAtEpochMs,
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
