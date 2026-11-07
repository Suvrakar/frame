package app.frame.studio.export

import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ExportHeight
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomCanvasTest {
    @Test fun customRatioSetsTheCanvas() {
        assertEquals(ExportPlan.Size(1080, 1350), ExportPlan.outputSize(emptyList(), ExportHeight.HD_1080, CanvasAspect.CUSTOM, 1080f / 1350f))
        assertEquals(ExportPlan.Size(2560, 1080), ExportPlan.outputSize(emptyList(), ExportHeight.HD_1080, CanvasAspect.CUSTOM, 2560f / 1080f))
    }

    @Test fun extremeRatiosAreClamped() {
        assertEquals(ExportPlan.Size(4320, 1080), ExportPlan.outputSize(emptyList(), ExportHeight.HD_1080, CanvasAspect.CUSTOM, 50f))
    }

    @Test fun customWithoutARatioFollowsTheFirstClip() {
        assertEquals(
            ExportPlan.outputSize(emptyList(), ExportHeight.HD_1080, CanvasAspect.ORIGINAL),
            ExportPlan.outputSize(emptyList(), ExportHeight.HD_1080, CanvasAspect.CUSTOM, null),
        )
    }
}
