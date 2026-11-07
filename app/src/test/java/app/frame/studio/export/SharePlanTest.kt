package app.frame.studio.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharePlanTest {
    private fun facts(w: Int, h: Int, fps: Float = 30f, mbps: Float = 8f, ms: Long = 20_000) =
        VideoFacts(w, h, fps, (mbps * 1_000_000).toInt(), ms)

    @Test fun a4kExportIsMadeInstagramReady() {
        val s = SharePlan.spec(facts(2160, 3840, fps = 60f, mbps = 45f), ShareTarget.INSTAGRAM)!!
        assertEquals(1080, s.width)
        assertEquals(1920, s.height)
        assertEquals(30, s.fps)
        assertEquals(8_000_000, s.bitrate)
    }

    @Test fun anAlreadyIdealFileIsSentUntouched() {
        assertNull(SharePlan.spec(facts(1080, 1920, mbps = 8f), ShareTarget.INSTAGRAM))
        assertNull(SharePlan.spec(facts(1080, 1920, mbps = 10f), ShareTarget.INSTAGRAM)) // within slack
    }

    @Test fun neverUpscalesOrRaisesBitrate() {
        val s = SharePlan.spec(facts(720, 1280, fps = 60f, mbps = 3f), ShareTarget.INSTAGRAM)!!
        assertEquals(720, s.width)
        assertEquals(30, s.fps)
        assertTrue(s.bitrate <= 3_000_000)
    }

    @Test fun whatsappGets720pForItsHdMode() {
        val s = SharePlan.spec(facts(1080, 1920), ShareTarget.WHATSAPP)!!
        assertEquals(720, s.width)
        assertEquals(1280, s.height)
    }

    @Test fun youtubeAlwaysGetsTheOriginal() {
        assertNull(SharePlan.spec(facts(2160, 3840, fps = 60f, mbps = 60f), ShareTarget.YOUTUBE))
    }

    @Test fun tiktokKeeps60fps() {
        assertNull(SharePlan.spec(facts(1080, 1920, fps = 60f, mbps = 10f), ShareTarget.TIKTOK))
    }

    @Test fun storyLengthNote() {
        assertNotNull(SharePlan.lengthNote(facts(1080, 1920, ms = 90_000), ShareTarget.INSTAGRAM))
        assertNull(SharePlan.lengthNote(facts(1080, 1920, ms = 30_000), ShareTarget.INSTAGRAM))
        assertNull(SharePlan.lengthNote(facts(1080, 1920, ms = 90_000), ShareTarget.TIKTOK))
    }
}
