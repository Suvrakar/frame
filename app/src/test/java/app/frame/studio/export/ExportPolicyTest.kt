package app.frame.studio.export

import app.frame.studio.domain.ExportHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportPolicyTest {

    @Test
    fun neverAttachesWatermark() {
        assertNull(ExportPolicy.watermarkOverlay())
        assertEquals(false, ExportPolicy.includesWatermark)
    }

    @Test
    fun usesHardwareFriendlyCodecs() {
        assertEquals("video/avc", ExportPolicy.videoMimeType)
        assertEquals("audio/mp4a-latm", ExportPolicy.audioMimeType)
    }

    @Test
    fun mapsExportHeight() {
        assertEquals(720, ExportPolicy.outputHeight(ExportHeight.HD_720))
        assertEquals(1080, ExportPolicy.outputHeight(ExportHeight.HD_1080))
    }
}
