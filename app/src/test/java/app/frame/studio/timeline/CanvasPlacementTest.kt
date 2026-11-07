package app.frame.studio.timeline

import app.frame.studio.domain.CanvasFill
import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPlacementTest {
    private val landscape = VideoClip(id = "v", sourceUri = "u", sourceDurationMs = 1_000, trimEndMs = 1_000, width = 1920, height = 1080)

    @Test fun landscapeOnPortraitFitsWidthOrCoversHeight() {
        val (fw, fh) = CanvasPlacement.baseBox(landscape, 1080, 1920, CanvasFill.FIT)
        assertEquals(1f, fw, 1e-4f)
        assertEquals((1080f / 1920f) / (1920f / 1080f), fh, 1e-4f)
        val (cw, ch) = CanvasPlacement.baseBox(landscape, 1080, 1920, CanvasFill.FILL)
        assertEquals(1f, ch, 1e-4f)
        assertTrue(cw > 3f)
        assertEquals(CanvasPlacement.baseBox(landscape, 1080, 1920, CanvasFill.FIT), CanvasPlacement.baseBox(landscape, 1080, 1920, CanvasFill.BLUR))
    }

    @Test fun defaultsAreTheBaseBoxCentred() {
        assertTrue(CanvasPlacement.isDefault(landscape))
        assertFalse(CanvasPlacement.isDefault(landscape.copy(placeScale = 1.2f)))
    }

    @Test fun snapsToCentreAndBaseSize() {
        val t = CanvasPlacement.snap(Transform(0.51f, 0.488f, 1.03f, 0f))
        assertEquals(Transform(0.5f, 0.5f, 1f, 0f), t)
        val far = Transform(0.6f, 0.3f, 1.3f, 0f)
        assertEquals(far, CanvasPlacement.snap(far))
    }

    @Test fun clampKeepsTheClipReachable() {
        val t = CanvasPlacement.clamp(Transform(9f, -9f, 99f, 10f, 0.2f))
        assertEquals(Transform(1.5f, -0.5f, CanvasPlacement.MAX_SCALE, 10f, 1f), t)
    }
}
