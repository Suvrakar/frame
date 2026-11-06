package app.frame.studio.templates

import app.frame.studio.media.VideoVerdict
import android.content.Context
import android.net.Uri
import app.frame.studio.data.ImageNormalizer
import app.frame.studio.data.MediaProbe
import app.frame.studio.data.ProjectRepository
import app.frame.studio.device.DeviceProfile
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project
import app.frame.studio.export.ExportPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Turns a template, the user's text and their picked media into a saved project. */
class TemplateBuilder(
    private val context: Context,
    private val repository: ProjectRepository,
    private val probe: MediaProbe,
    private val normalizer: ImageNormalizer,
    private val device: DeviceProfile,
) {

    suspend fun create(template: VideoTemplate, values: Map<TemplateField, String>, uris: List<Uri>): Project {
        val media = uris.mapNotNull { uri -> probeSlot(uri) }
        require(media.isNotEmpty()) { "Those files couldn't be opened" }

        // Cards are rendered at a resolution the device handles comfortably.
        val cardHeight = if (device.isLowEnd) ExportHeight.HD_720 else ExportHeight.HD_1080
        val size = ExportPlan.outputSize(emptyList(), cardHeight, template.aspect)
        val projectId = UUID.randomUUID().toString()
        val cards = File(context.filesDir, "cards")

        val (introUri, outroUri) = withContext(Dispatchers.Default) {
            val intro = template.intro?.let { card ->
                val (title, subtitle) = Templates.fillCard(card, values)
                CardRenderer.render(template, title, subtitle, card.emoji, size.width, size.height, File(cards, "${projectId}_intro.png"))
            }
            val outro = template.outro?.let { card ->
                val (title, subtitle) = Templates.fillCard(card, values)
                CardRenderer.render(template, title, subtitle, card.emoji, size.width, size.height, File(cards, "${projectId}_outro.png"))
            }
            intro?.let { Uri.fromFile(it).toString() } to outro?.let { Uri.fromFile(it).toString() }
        }

        val style = template.collage
        val project = if (style != null) {
            val pages = renderCollagePages(template, style, values, media, size, projectId, cards)
            TemplatePlan.buildCollage(
                template = template,
                values = values,
                pages = pages,
                projectId = projectId,
                nowMs = System.currentTimeMillis(),
                introUri = introUri,
                outroUri = outroUri,
                cardWidth = size.width,
                cardHeight = size.height,
                newId = { UUID.randomUUID().toString().take(12) },
            ).copy(exportHeight = device.defaultExportHeight)
        } else TemplatePlan.build(
            template = template,
            values = values,
            media = media,
            projectId = projectId,
            nowMs = System.currentTimeMillis(),
            introUri = introUri,
            outroUri = outroUri,
            cardWidth = size.width,
            cardHeight = size.height,
            newId = { UUID.randomUUID().toString().take(12) },
        ).copy(exportHeight = device.defaultExportHeight)
        repository.save(project)
        return project
    }

    /** Renders every build-up and final collage frame to a JPEG; photos are decoded once per group. */
    private suspend fun renderCollagePages(
        template: VideoTemplate,
        style: CollageStyle,
        values: Map<TemplateField, String>,
        media: List<SlotMedia>,
        size: ExportPlan.Size,
        projectId: String,
        dir: File,
    ): List<Pair<CollagePage, String>> = withContext(Dispatchers.IO) {
        val header = template.header?.let { Templates.fillCard(it, values) } ?: ("" to "")
        val labels = template.photoLabels.map { Templates.fillCaption(it, values).orEmpty() }
        // Each photo only needs to be about half the frame wide.
        val maxSide = (maxOf(size.width, size.height) * if (device.isLowEnd) 0.5f else 0.6f).toInt()
        val pages = CollagePlan.pages(media.size, style.cells, template.slotMs)
        val result = mutableListOf<Pair<CollagePage, String>>()
        pages.groupBy { it.group }.forEach { (group, groupPages) ->
            val slice = media.drop(group * style.cells).take(style.cells)
            val photos = slice.map { CollageRenderer.loadPhoto(context, Uri.parse(it.uri), it.isImage, maxSide) }
            try {
                groupPages.forEach { page ->
                    val file = File(dir, "${projectId}_collage_${group}_${page.visible}.jpg")
                    CollageRenderer.render(
                        template, style, photos, page.visible, header.first, header.second,
                        labels, size.width, size.height, file,
                    )
                    result += page to Uri.fromFile(file).toString()
                }
            } finally {
                photos.forEach { it?.recycle() }
            }
        }
        result
    }

    private suspend fun probeSlot(uri: Uri): SlotMedia? {
        probe.persistPermission(uri)
        return if (probe.isImage(uri)) {
            val name = probe.probeImage(uri)?.displayName.orEmpty()
            normalizer.normalize(uri)?.let { SlotMedia(it.uri.toString(), true, 0, it.width, it.height, name) }
        } else {
            probe.probeVideo(uri)?.takeIf { it.durationMs >= 100 && it.verdict !is VideoVerdict.Unsupported }?.let {
                SlotMedia(
                    uri.toString(), false, it.durationMs, it.width, it.height, it.displayName,
                    needsProxy = it.verdict is VideoVerdict.Heavy, audioMuted = !it.audioSupported,
                )
            }
        }
    }
}
