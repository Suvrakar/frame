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
    /** -1..1, about ±1 stop */
    val exposure: Float = 0f,
    /** -1..1, recover (−) or boost (+) bright areas */
    val highlights: Float = 0f,
    /** -1..1, deepen (−) or lift (+) dark areas */
    val shadows: Float = 0f,
    /** -1 (green) .. 1 (magenta) */
    val tint: Float = 0f,
    /** -1..1, saturation that spares already-saturated colours and skin */
    val vibrance: Float = 0f,
    /** 0..1, lifted blacks for a matte look */
    val fade: Float = 0f,
    /** Split toning: hue (0..360) and strength (0..1) for dark and bright areas. */
    val shadowHue: Float = 210f,
    val shadowTone: Float = 0f,
    val highlightHue: Float = 35f,
    val highlightTone: Float = 0f,
) {
    /** True when the linear colour-matrix part does nothing. */
    val isColorIdentity: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f &&
            exposure == 0f && tint == 0f && fade == 0f

    /** True when the tone/texture shader part does nothing. */
    val isToneIdentity: Boolean
        get() = vignette == 0f && grain == 0f && highlights == 0f && shadows == 0f &&
            vibrance == 0f && shadowTone == 0f && highlightTone == 0f

    val isIdentity: Boolean
        get() = isColorIdentity && isToneIdentity
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
    /** Detected beats, in source time (ms). Empty until beat detection runs. */
    val beatsMs: List<Long> = emptyList(),
    val bpm: Float = 0f,
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
    /** Word-by-word caption timing, relative to [startMs]. Empty for ordinary text. */
    val words: List<CaptionWord> = emptyList(),
    val captionStyle: CaptionStyle = CaptionStyle.KARAOKE,
    /** Clockwise degrees. */
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    /** Animation over time (relative to [startMs]); empty = static. */
    val keyframes: List<Keyframe> = emptyList(),
) {
    val isCaption: Boolean get() = words.isNotEmpty()
}

/** How motion eases between two keyframes. */
@Serializable
enum class Easing(val label: String) { LINEAR("Linear"), SMOOTH("Smooth"), EASE_IN("Ease in"), EASE_OUT("Ease out"), HOLD("Hold") }

/**
 * A keyframe of an object's transform at [atMs] (relative to the object's start).
 * Position is the object's centre in 0..1 of the frame; [scale] is relative to the object's
 * default size; [rotation] is clockwise degrees; [opacity] 0..1.
 */
@Serializable
data class Keyframe(
    val atMs: Long,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    /** Easing from this keyframe to the next. */
    val easing: Easing = Easing.SMOOTH,
)

/**
 * A video or photo layered above the main video (picture-in-picture). It plays from [startMs] on
 * the timeline; its sound is mixed with everything else.
 */
@Serializable
data class OverlayClip(
    val id: String,
    val sourceUri: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0,
    val trimEndMs: Long,
    val startMs: Long,
    val width: Int,
    val height: Int,
    val isImage: Boolean = false,
    val displayName: String = "",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    /** 1 = fits the frame; overlays start smaller. */
    val scale: Float = 0.45f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val volume: Float = 1f,
    val filterId: String = "none",
    val keyframes: List<Keyframe> = emptyList(),
) {
    fun durationMs(): Long = (trimEndMs - trimStartMs).coerceAtLeast(0)
    fun endMs(): Long = startMs + durationMs()
}

/** One spoken word of a caption; times are relative to the caption's start. */
@Serializable
data class CaptionWord(val text: String, val startMs: Long, val endMs: Long)

/** How the current word of an animated caption is shown. */
@Serializable
enum class CaptionStyle(val label: String) {
    KARAOKE("Karaoke"),
    POP("Pop"),
    BOX("Highlight"),
    PLAIN("Plain"),
}

@Serializable
enum class EffectGroup(val label: String) { WEATHER("Weather"), PARTY("Party"), FILM("Film"), MOTION("Motion") }

/** Animated, procedurally drawn scene effects (rendered by a GPU shader, no assets). */
@Serializable
enum class SceneEffectType(val label: String, val emoji: String, val group: EffectGroup) {
    RAIN("Rain", "🌧️", EffectGroup.WEATHER),
    SNOW("Snow", "❄️", EffectGroup.WEATHER),
    FOG("Fog", "🌫️", EffectGroup.WEATHER),
    CONFETTI("Confetti", "🎊", EffectGroup.PARTY),
    SPARKLE("Sparkle", "✨", EffectGroup.PARTY),
    HEARTS("Hearts", "💗", EffectGroup.PARTY),
    BOKEH("Bokeh", "🔆", EffectGroup.PARTY),
    LIGHT_LEAK("Light leak", "🌅", EffectGroup.FILM),
    OLD_FILM("Old film", "🎞️", EffectGroup.FILM),
    GLITCH("Glitch", "📺", EffectGroup.FILM),
    SHAKE("Shake", "📳", EffectGroup.MOTION),
    PULSE("Beat pulse", "💓", EffectGroup.MOTION),
}

/** A scene effect placed on the timeline. */
@Serializable
data class SceneEffect(
    val id: String,
    val type: SceneEffectType,
    val startMs: Long,
    val endMs: Long,
    /** 0..1 */
    val intensity: Float = 0.8f,
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
    val effects: List<SceneEffect> = emptyList(),
    /** AAC audio bitrate of the export, in kbps. */
    val audioBitrateKbps: Int = 192,
    /** Videos and photos layered above the main video. */
    val overlays: List<OverlayClip> = emptyList(),
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
