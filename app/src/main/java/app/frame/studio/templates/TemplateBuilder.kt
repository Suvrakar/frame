package app.frame.studio.templates

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

        val project = TemplatePlan.build(
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

    private suspend fun probeSlot(uri: Uri): SlotMedia? {
        probe.persistPermission(uri)
        return if (probe.isImage(uri)) {
            val name = probe.probeImage(uri)?.displayName.orEmpty()
            normalizer.normalize(uri)?.let { SlotMedia(it.uri.toString(), true, 0, it.width, it.height, name) }
        } else {
            probe.probeVideo(uri)?.takeIf { it.durationMs >= 100 }?.let {
                SlotMedia(uri.toString(), false, it.durationMs, it.width, it.height, it.displayName)
            }
        }
    }
}
