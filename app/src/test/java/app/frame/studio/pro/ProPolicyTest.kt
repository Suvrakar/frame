package app.frame.studio.pro

import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProPolicyTest {
    private val base = Project(id = "p", name = "p", createdAtEpochMs = 0)

    @Test
    fun fullHdAt30IsFree() {
        assertFalse(ProPolicy.exportNeedsPro(base.copy(exportHeight = ExportHeight.HD_1080, exportFps = 30)))
        assertFalse(ProPolicy.exportNeedsPro(base.copy(exportHeight = ExportHeight.SD_480, exportFps = 24)))
    }

    @Test
    fun highResolutionOrFrameRateNeedsPro() {
        assertTrue(ProPolicy.exportNeedsPro(base.copy(exportHeight = ExportHeight.UHD_2160, exportFps = 30)))
        assertTrue(ProPolicy.exportNeedsPro(base.copy(exportHeight = ExportHeight.QHD_1440, exportFps = 24)))
        assertTrue(ProPolicy.exportNeedsPro(base.copy(exportHeight = ExportHeight.HD_720, exportFps = 60)))
        assertTrue(ProPolicy.exportNeedsPro(base.copy(exportHeight = ExportHeight.HD_1080, exportFps = 50)))
    }
}
