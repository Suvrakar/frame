package app.frame.studio.templates

import app.frame.studio.domain.TransitionType
import app.frame.studio.timeline.TimelineMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {

    private val name = mapOf(TemplateField.NAME to "Mia")

    @Test
    fun catalogIsConsistent() {
        assertEquals(Templates.all.size, Templates.all.map { it.id }.toSet().size)
        assertTrue(Templates.inCategory(TemplateCategory.BIRTHDAY).size >= 5)
        Templates.all.forEach { t ->
            assertTrue(t.id, t.slots in 1..12 && t.slotMs >= 500 && t.colors.size >= 2)
        }
    }

    @Test
    fun fillsAndTidiesCardText() {
        assertEquals("Happy Birthday Mia!", Templates.fillCardText("Happy Birthday {name}!", name))
        assertEquals("Happy Birthday!", Templates.fillCardText("Happy Birthday {name}!", emptyMap()))
        assertEquals("", Templates.fillCardText("from {name}", emptyMap()))
        assertEquals("from Mia", Templates.fillCardText("from {name}", name))
    }

    @Test
    fun usesFallbackWhenTitleIsIncomplete() {
        val kids = Templates.byId("kids_party")!!.intro!!
        assertEquals("It's party time!", Templates.fillCard(kids, name).first)
        assertEquals("Mia is 7!", Templates.fillCard(kids, name + (TemplateField.AGE to "7")).first)
    }

    @Test
    fun dropsCaptionsWithMissingValues() {
        assertNull(Templates.fillCaption("Turning {age}!", name))
        assertEquals("Turning 7!", Templates.fillCaption("Turning {age}!", mapOf(TemplateField.AGE to "7")))
        assertNull(Templates.fillCaption("", name))
    }

    @Test
    fun buildsProjectWithCardsSlotsAndOverlays() {
        val t = Templates.byId("birthday_bash")!!
        var n = 0
        val media = listOf(
            SlotMedia("v1", isImage = false, durationMs = 10_000, width = 1920, height = 1080),
            SlotMedia("p1", isImage = true, durationMs = 0, width = 1000, height = 1000),
        )
        val p = TemplatePlan.build(t, name, media, "id", 0, "intro", "outro", 1080, 1920) { "x${n++}" }
        assertEquals(4, p.videoClips.size)
        assertEquals(t.aspect, p.aspect)
        assertEquals(t.introMs + 2 * t.slotMs + t.outroMs, TimelineMath.totalVideoMs(p.videoClips))
        // Long video trimmed to the slot, skipping the first quarter of the spare footage.
        assertEquals(2_000, p.videoClips[1].trimStartMs)
        assertEquals(TransitionType.NONE, p.videoClips.last().transition)
        // "Turning {age}!" is skipped without an age; slot 2 caption + 2 stickers remain.
        assertEquals(3, p.textOverlays.size)
        assertEquals("Happy Birthday Mia!", p.name)
    }
}
