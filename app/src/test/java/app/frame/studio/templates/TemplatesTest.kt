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

    @Test
    fun collageCellsStayInsideTheFrame() {
        listOf(1080f to 1920f, 1080f to 1350f, 1080f to 1080f, 1920f to 1080f).forEach { (w, h) ->
            CollageStyle.entries.forEach { style ->
                val cells = CollageLayout.cells(style, w, h)
                assertEquals(style.cells, cells.size)
                cells.forEach { c ->
                    assertTrue("$style $w x $h", c.left >= 0 && c.top >= 0 && c.right <= w && c.bottom <= h && c.width > 50 && c.height > 50)
                }
            }
        }
        // Polaroid captions (bottom fifth of each top print) clear the prints below.
        listOf(1080f to 1350f, 1080f to 1920f, 1080f to 1080f).forEach { (w, h) ->
            val p = CollageLayout.cells(CollageStyle.POLAROIDS, w, h)
            assertTrue("$w x $h", p[0].bottom < p[2].top + p[2].height * 0.04f)
            assertTrue("$w x $h", p[1].bottom < p[3].top + p[3].height * 0.04f)
        }
        // Grid cells don't overlap.
        val grid = CollageLayout.cells(CollageStyle.GRID, 1080f, 1920f)
        assertTrue(grid[0].right < grid[1].left && grid[0].bottom < grid[2].top)
    }

    @Test
    fun collagePagesBuildUpThenHold() {
        val pages = CollagePlan.pages(photoCount = 6, perPage = 4, holdMs = 3_000)
        assertEquals(listOf(1, 2, 3, 4, 1, 2), pages.map { it.visible })
        assertEquals(listOf(false, false, false, true, false, true), pages.map { it.isFinal })
        assertEquals(3 * 450L + 3_000 + 450 + 3_000, CollagePlan.durationMs(6, 4, 3_000))
    }

    @Test
    fun buildsCollageProject() {
        val t = Templates.byId("birthday_collage")!!
        var n = 0
        val pages = CollagePlan.pages(4, 4, t.slotMs).mapIndexed { i, p -> p to "page$i" }
        val project = TemplatePlan.buildCollage(t, name, pages, "id", 0, null, "outro", 1080, 1920) { "c${n++}" }
        assertEquals(5, project.videoClips.size)
        assertEquals(TransitionType.NONE, project.videoClips[0].transition)
        assertEquals(t.transition, project.videoClips[3].transition)
        assertEquals(TransitionType.NONE, project.videoClips.last().transition)
        assertEquals(t.totalMs, TimelineMath.totalVideoMs(project.videoClips))
        assertEquals(2, project.textOverlays.size)
        assertEquals("Happy Birthday Mia!", project.name)
        // No age given: the subtitle is dropped instead of reading "years of awesome".
        assertEquals("", Templates.fillCard(t.header!!, name).second)
        assertTrue(Templates.inCategory(TemplateCategory.COLLAGE).size >= 12)
        assertTrue(Templates.inCategory(TemplateCategory.BIRTHDAY).any { it.collage != null })
    }
}
