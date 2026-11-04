package app.frame.studio.export

import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.ExportQuality
import app.frame.studio.domain.Project

/** One-tap format for a social destination: canvas shape plus matching export settings. */
data class SocialPreset(
    val id: String,
    val name: String,
    val description: String,
    val aspect: CanvasAspect,
    val height: ExportHeight = ExportHeight.HD_1080,
    val fps: Int = 30,
    val quality: ExportQuality = ExportQuality.HIGH,
    /** Longest video the destination accepts in one piece, if limited. */
    val maxDurationMs: Long? = null,
)

object SocialPresets {

    val instagram: List<SocialPreset> = listOf(
        SocialPreset("ig_reel", "Reel", "9:16 · 1080×1920", CanvasAspect.R9_16, maxDurationMs = 180_000),
        SocialPreset("ig_story", "Story", "9:16 · up to 60s", CanvasAspect.R9_16, maxDurationMs = 60_000),
        SocialPreset("ig_feed", "Feed post", "4:5 · 1080×1350", CanvasAspect.R4_5),
        SocialPreset("ig_square", "Square", "1:1 · 1080×1080", CanvasAspect.R1_1),
        SocialPreset("ig_landscape", "Landscape", "16:9 · 1920×1080", CanvasAspect.R16_9),
    )

    /** The first preset whose shape and export settings all match the project, if any. */
    fun matching(project: Project, presets: List<SocialPreset> = instagram): SocialPreset? =
        presets.firstOrNull {
            it.aspect == project.aspect && it.height == project.exportHeight &&
                it.fps == project.exportFps && it.quality == project.exportQuality
        }

    fun apply(project: Project, preset: SocialPreset, clampHeight: (ExportHeight) -> ExportHeight = { it }): Project =
        project.copy(
            aspect = preset.aspect,
            exportHeight = clampHeight(preset.height),
            exportFps = preset.fps,
            exportQuality = preset.quality,
        )

    /** A friendly note when the video is longer than the destination allows. */
    fun lengthWarning(preset: SocialPreset, durationMs: Long): String? {
        val max = preset.maxDurationMs ?: return null
        if (durationMs <= max) return null
        val limit = if (max % 60_000 == 0L) "${max / 60_000} min" else "${max / 1000} s"
        return "Instagram ${preset.name.lowercase()}s can be up to $limit. Trim it, or Instagram may cut it short."
    }
}
