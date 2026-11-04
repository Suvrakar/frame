package app.frame.studio.templates

import app.frame.studio.domain.Project
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip

/** A picked photo or video, already probed. */
data class SlotMedia(
    val uri: String,
    val isImage: Boolean,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val displayName: String = "",
)

/** Pure assembly of a [Project] from a template; card images are rendered by the caller. */
object TemplatePlan {

    private val STICKER_SPOTS = listOf(0.82f to 0.16f, 0.18f to 0.2f, 0.8f to 0.7f, 0.2f to 0.72f)

    fun build(
        template: VideoTemplate,
        values: Map<TemplateField, String>,
        media: List<SlotMedia>,
        projectId: String,
        nowMs: Long,
        introUri: String?,
        outroUri: String?,
        cardWidth: Int,
        cardHeight: Int,
        newId: () -> String,
    ): Project {
        require(media.isNotEmpty()) { "Pick at least one photo or video" }
        val clips = mutableListOf<VideoClip>()
        val overlays = mutableListOf<TextOverlay>()

        fun card(uri: String, ms: Long, fadeIn: Long, fadeOut: Long) = VideoClip(
            id = newId(),
            sourceUri = uri,
            sourceDurationMs = VideoClip.MAX_IMAGE_MS,
            trimEndMs = ms,
            width = cardWidth,
            height = cardHeight,
            isImage = true,
            displayName = "Title card",
            fadeInMs = fadeIn,
            fadeOutMs = fadeOut,
            transition = template.transition,
            transitionMs = template.transitionMs,
        )

        if (introUri != null && template.intro != null) clips += card(introUri, template.introMs, 300, 0)
        var cursor = clips.sumOf { it.outputDurationMs() }

        media.forEachIndexed { index, m ->
            val slotMs = template.slotMs
            val clip = if (m.isImage) {
                VideoClip(
                    id = newId(), sourceUri = m.uri, sourceDurationMs = VideoClip.MAX_IMAGE_MS, trimEndMs = slotMs,
                    width = m.width, height = m.height, isImage = true, displayName = m.displayName,
                )
            } else {
                val length = minOf(slotMs, m.durationMs)
                // Skip the first part of long videos, where the interesting bit rarely is.
                val start = ((m.durationMs - length) / 4).coerceAtLeast(0)
                VideoClip(
                    id = newId(), sourceUri = m.uri, sourceDurationMs = m.durationMs,
                    trimStartMs = start, trimEndMs = start + length,
                    width = m.width, height = m.height, displayName = m.displayName,
                )
            }.copy(
                filterId = template.filterId,
                transition = template.transition,
                transitionMs = template.transitionMs,
            )
            clips += clip
            val duration = clip.outputDurationMs()

            template.captions.getOrNull(index % template.captions.size.coerceAtLeast(1))
                ?.let { Templates.fillCaption(it, values) }
                ?.let { caption ->
                    overlays += TextOverlay(
                        id = newId(), text = caption,
                        startMs = cursor + 150, endMs = cursor + duration - 100,
                        y = 0.82f, color = template.captionColor, bold = true, scale = 1.1f,
                    )
                }
            template.stickers.getOrNull(index % template.stickers.size.coerceAtLeast(1))
                ?.takeIf { it.isNotBlank() }
                ?.let { emoji ->
                    val (x, y) = STICKER_SPOTS[index % STICKER_SPOTS.size]
                    overlays += TextOverlay(
                        id = newId(), text = emoji, startMs = cursor + 100, endMs = cursor + duration - 50,
                        x = x, y = y, isSticker = true, background = false,
                    )
                }
            cursor += duration
        }

        if (outroUri != null && template.outro != null) clips += card(outroUri, template.outroMs, 0, 500)
        // No transition after the final clip.
        val last = clips.last()
        clips[clips.lastIndex] = last.copy(transition = TransitionType.NONE)

        val name = Templates.fillCardText(
            template.intro?.let { Templates.fillCard(it, values).first } ?: template.name,
            values,
        ).ifBlank { template.name }

        return Project(
            id = projectId,
            name = name,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
            videoClips = clips,
            textOverlays = overlays,
            aspect = template.aspect,
        )
    }

    private val COLLAGE_STICKER_SPOTS = listOf(0.1f to 0.075f, 0.9f to 0.075f)

    /** Assembles a collage template from already-rendered collage pages (in [pages] order). */
    fun buildCollage(
        template: VideoTemplate,
        values: Map<TemplateField, String>,
        pages: List<Pair<CollagePage, String>>,
        projectId: String,
        nowMs: Long,
        introUri: String?,
        outroUri: String?,
        cardWidth: Int,
        cardHeight: Int,
        newId: () -> String,
    ): Project {
        require(pages.isNotEmpty()) { "Pick at least one photo" }
        val clips = mutableListOf<VideoClip>()
        val overlays = mutableListOf<TextOverlay>()

        fun still(uri: String, ms: Long, name: String, transition: TransitionType, fadeIn: Long = 0, fadeOut: Long = 0) = VideoClip(
            id = newId(),
            sourceUri = uri,
            sourceDurationMs = VideoClip.MAX_IMAGE_MS,
            trimEndMs = ms,
            width = cardWidth,
            height = cardHeight,
            isImage = true,
            displayName = name,
            transition = transition,
            transitionMs = template.transitionMs,
            fadeInMs = fadeIn,
            fadeOutMs = fadeOut,
        )

        if (introUri != null && template.intro != null) clips += still(introUri, template.introMs, "Title card", template.transition, fadeIn = 300)
        var cursor = clips.sumOf { it.outputDurationMs() }
        pages.forEachIndexed { index, (page, uri) ->
            // Build-up pages cut straight to the next one so photos "pop" in.
            val transition = if (page.isFinal) template.transition else TransitionType.NONE
            clips += still(uri, page.durationMs, "Collage ${page.group + 1}", transition, fadeIn = if (index == 0 && introUri == null) 300 else 0)
            if (page.isFinal) {
                template.stickers.take(COLLAGE_STICKER_SPOTS.size).forEachIndexed { i, emoji ->
                    if (emoji.isBlank()) return@forEachIndexed
                    val (x, y) = COLLAGE_STICKER_SPOTS[i]
                    overlays += TextOverlay(
                        id = newId(), text = emoji, startMs = cursor + 150, endMs = cursor + page.durationMs - 50,
                        x = x, y = y, isSticker = true, background = false, scale = 0.8f,
                    )
                }
            }
            cursor += page.durationMs
        }
        if (outroUri != null && template.outro != null) clips += still(outroUri, template.outroMs, "Title card", TransitionType.NONE, fadeOut = 500)
        clips[clips.lastIndex] = clips.last().copy(transition = TransitionType.NONE)

        val headline = (template.header ?: template.intro)?.let { Templates.fillCard(it, values).first }
        return Project(
            id = projectId,
            name = headline?.ifBlank { null } ?: template.name,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
            videoClips = clips,
            textOverlays = overlays,
            aspect = template.aspect,
        )
    }
}
