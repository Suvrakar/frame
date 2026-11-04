package app.frame.studio.export

import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.ExportQuality
import app.frame.studio.domain.Project

enum class SocialPlatform(val label: String, val color: Long) {
    INSTAGRAM("Instagram", 0xFFE1306C),
    TIKTOK("TikTok", 0xFF25F4EE),
    YOUTUBE("YouTube", 0xFFFF0033),
    FACEBOOK("Facebook", 0xFF1877F2),
    SNAPCHAT("Snapchat", 0xFFFFFC00),
    X("X", 0xFFE7E9EA),
    LINKEDIN("LinkedIn", 0xFF0A66C2),
    WHATSAPP("WhatsApp", 0xFF25D366),
}

/** One-tap format for a social destination: canvas shape plus matching export settings. */
data class SocialPreset(
    val id: String,
    val platform: SocialPlatform,
    val name: String,
    val description: String,
    val aspect: CanvasAspect,
    val height: ExportHeight = ExportHeight.HD_1080,
    val fps: Int = 30,
    val quality: ExportQuality = ExportQuality.HIGH,
    /** Longest video the destination accepts in one piece, when we know it. */
    val maxDurationMs: Long? = null,
) {
    val displayName: String get() = "${platform.label} ${name.lowercase()}"
}

object SocialPresets {


    val all: List<SocialPreset> = listOf(
        // Instagram
        SocialPreset("ig_reel", SocialPlatform.INSTAGRAM, "Reel", "9:16 · 1080×1920", CanvasAspect.R9_16, maxDurationMs = 180_000),
        SocialPreset("ig_story", SocialPlatform.INSTAGRAM, "Story", "9:16 · up to 60s", CanvasAspect.R9_16, maxDurationMs = 60_000),
        SocialPreset("ig_feed", SocialPlatform.INSTAGRAM, "Feed post", "4:5 · 1080×1350", CanvasAspect.R4_5),
        SocialPreset("ig_square", SocialPlatform.INSTAGRAM, "Square", "1:1 · 1080×1080", CanvasAspect.R1_1),
        SocialPreset("ig_landscape", SocialPlatform.INSTAGRAM, "Landscape", "16:9 · 1920×1080", CanvasAspect.R16_9),
        // TikTok
        SocialPreset("tt_video", SocialPlatform.TIKTOK, "Video", "9:16 · 1080×1920", CanvasAspect.R9_16),
        SocialPreset("tt_smooth", SocialPlatform.TIKTOK, "Smooth 60", "9:16 · 60 fps", CanvasAspect.R9_16, fps = 60),
        // YouTube
        SocialPreset("yt_short", SocialPlatform.YOUTUBE, "Short", "9:16 · up to 3 min", CanvasAspect.R9_16, maxDurationMs = 180_000),
        SocialPreset("yt_video", SocialPlatform.YOUTUBE, "Video", "16:9 · 1080p", CanvasAspect.R16_9),
        SocialPreset("yt_4k", SocialPlatform.YOUTUBE, "4K", "16:9 · 2160p 60fps", CanvasAspect.R16_9, height = ExportHeight.UHD_2160, fps = 60, quality = ExportQuality.BEST),
        // Facebook
        SocialPreset("fb_reel", SocialPlatform.FACEBOOK, "Reel", "9:16 · 1080×1920", CanvasAspect.R9_16),
        SocialPreset("fb_story", SocialPlatform.FACEBOOK, "Story", "9:16 · 1080×1920", CanvasAspect.R9_16),
        SocialPreset("fb_feed", SocialPlatform.FACEBOOK, "Feed", "4:5 · 1080×1350", CanvasAspect.R4_5),
        SocialPreset("fb_landscape", SocialPlatform.FACEBOOK, "Landscape", "16:9 · 1080p", CanvasAspect.R16_9),
        // Snapchat
        SocialPreset("sc_spotlight", SocialPlatform.SNAPCHAT, "Spotlight", "9:16 · up to 60s", CanvasAspect.R9_16, maxDurationMs = 60_000),
        SocialPreset("sc_story", SocialPlatform.SNAPCHAT, "Story", "9:16 · 1080×1920", CanvasAspect.R9_16),
        // X
        SocialPreset("x_landscape", SocialPlatform.X, "Landscape", "16:9 · up to 2:20", CanvasAspect.R16_9, maxDurationMs = 140_000),
        SocialPreset("x_square", SocialPlatform.X, "Square", "1:1 · up to 2:20", CanvasAspect.R1_1, maxDurationMs = 140_000),
        SocialPreset("x_vertical", SocialPlatform.X, "Vertical", "9:16 · up to 2:20", CanvasAspect.R9_16, maxDurationMs = 140_000),
        // LinkedIn
        SocialPreset("li_square", SocialPlatform.LINKEDIN, "Square", "1:1 · 1080×1080", CanvasAspect.R1_1),
        SocialPreset("li_landscape", SocialPlatform.LINKEDIN, "Landscape", "16:9 · 1080p", CanvasAspect.R16_9),
        SocialPreset("li_vertical", SocialPlatform.LINKEDIN, "Vertical", "4:5 · 1080×1350", CanvasAspect.R4_5),
        // WhatsApp: smaller files share faster.
        SocialPreset("wa_status", SocialPlatform.WHATSAPP, "Status", "9:16 · 720p, small file", CanvasAspect.R9_16, height = ExportHeight.HD_720, quality = ExportQuality.STANDARD),
        SocialPreset("wa_chat", SocialPlatform.WHATSAPP, "Chat", "Original shape · 720p", CanvasAspect.ORIGINAL, height = ExportHeight.HD_720, quality = ExportQuality.STANDARD),
    )

    val instagram: List<SocialPreset> get() = forPlatform(SocialPlatform.INSTAGRAM)

    fun forPlatform(platform: SocialPlatform): List<SocialPreset> = all.filter { it.platform == platform }

    /** First preset (optionally on [platform]) whose shape and export settings match the project. */
    fun matching(project: Project, platform: SocialPlatform? = null): SocialPreset? =
        (if (platform == null) all else forPlatform(platform)).firstOrNull {
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
        val limit = when {
            max % 60_000 == 0L -> "${max / 60_000} min"
            max > 60_000 -> "${max / 60_000}:${"%02d".format((max % 60_000) / 1000)}"
            else -> "${max / 1000} s"
        }
        return "${preset.displayName.replaceFirstChar { it.uppercase() }} can be up to $limit. Trim it, or ${preset.platform.label} may cut it short."
    }

}
