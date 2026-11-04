package app.frame.studio.export

import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project
import app.frame.studio.effects.FilterGroup
import app.frame.studio.effects.Filters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialPresetsTest {

    private val project = Project(id = "p", name = "p", createdAtEpochMs = 0)

    @Test
    fun applyingAPresetMakesItMatch() {
        val reel = SocialPresets.instagram.first { it.id == "ig_reel" }
        val applied = SocialPresets.apply(project, reel)
        assertEquals(CanvasAspect.R9_16, applied.aspect)
        assertEquals(reel, SocialPresets.matching(applied))
        assertEquals(ExportPlan.Size(1080, 1920), ExportPlan.outputSize(emptyList(), applied.exportHeight, applied.aspect))
    }

    @Test
    fun feedPostIs1080x1350() {
        val feed = SocialPresets.instagram.first { it.id == "ig_feed" }
        val p = SocialPresets.apply(project, feed)
        assertEquals(ExportPlan.Size(1080, 1350), ExportPlan.outputSize(emptyList(), p.exportHeight, p.aspect))
    }

    @Test
    fun clampsToWhatTheDeviceSupports() {
        val reel = SocialPresets.instagram.first()
        assertEquals(ExportHeight.HD_720, SocialPresets.apply(project, reel) { ExportHeight.HD_720 }.exportHeight)
    }

    @Test
    fun warnsOnlyWhenTooLong() {
        val story = SocialPresets.instagram.first { it.id == "ig_story" }
        assertNull(SocialPresets.lengthWarning(story, 45_000))
        assertNotNull(SocialPresets.lengthWarning(story, 75_000))
        assertTrue(SocialPresets.lengthWarning(story, 75_000)!!.contains("1 min"))
        val x = SocialPresets.all.first { it.id == "x_landscape" }
        assertTrue(SocialPresets.lengthWarning(x, 150_000)!!.contains("2:20"))
    }

    @Test
    fun everyPlatformHasPresetsWithUniqueIds() {
        SocialPlatform.entries.forEach { assertTrue(it.label, SocialPresets.forPlatform(it).isNotEmpty()) }
        assertEquals(SocialPresets.all.size, SocialPresets.all.map { it.id }.toSet().size)
    }

    @Test
    fun matchingPrefersTheRequestedPlatform() {
        val tiktok = SocialPresets.all.first { it.id == "tt_video" }
        val p = SocialPresets.apply(project, tiktok)
        // Same settings as an Instagram Reel; the platform filter picks the TikTok one.
        assertEquals("ig_reel", SocialPresets.matching(p)!!.id)
        assertEquals("tt_video", SocialPresets.matching(p, SocialPlatform.TIKTOK)!!.id)
        val yt4k = SocialPresets.apply(project, SocialPresets.all.first { it.id == "yt_4k" })
        assertEquals(ExportPlan.Size(3840, 2160), ExportPlan.outputSize(emptyList(), yt4k.exportHeight, yt4k.aspect))
        assertEquals(60, yt4k.exportFps)
    }

    @Test
    fun socialLooksArePresent() {
        assertTrue(Filters.inGroup(FilterGroup.SOCIAL).size >= 12)
        assertEquals("none", Filters.inGroup(FilterGroup.SOCIAL).first().id)
    }
}
