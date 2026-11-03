package app.frame.studio.export

import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.CropRect
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportPlanTest {

    private fun video(
        id: String = "v",
        durationMs: Long = 10_000,
        width: Int = 1920,
        height: Int = 1080,
        rotation: Int = 0,
        crop: CropRect? = null,
        fadeIn: Long = 0,
        fadeOut: Long = 0,
    ) = VideoClip(
        id = id,
        sourceUri = "file://$id.mp4",
        sourceDurationMs = durationMs,
        trimEndMs = durationMs,
        width = width,
        height = height,
        rotationDegrees = rotation,
        crop = crop,
        fadeInMs = fadeIn,
        fadeOutMs = fadeOut,
    )

    @Test
    fun outputSize_landscape1080() {
        assertEquals(ExportPlan.Size(1920, 1080), ExportPlan.outputSize(listOf(video()), ExportHeight.HD_1080))
    }

    @Test
    fun outputSize_portrait720() {
        val size = ExportPlan.outputSize(listOf(video(width = 1080, height = 1920)), ExportHeight.HD_720)
        assertEquals(ExportPlan.Size(720, 1280), size)
    }

    @Test
    fun outputSize_followsRotationAndCrop() {
        val rotated = ExportPlan.outputSize(listOf(video(rotation = 90)), ExportHeight.HD_1080)
        assertEquals(ExportPlan.Size(1080, 1920), rotated)
        val square = ExportPlan.outputSize(listOf(video(crop = centerCrop(1920, 1080, 1f))), ExportHeight.HD_1080)
        assertEquals(ExportPlan.Size(1080, 1080), square)
    }

    @Test
    fun outputSize_isAlwaysEven() {
        val size = ExportPlan.outputSize(listOf(video(width = 1001, height = 777)), ExportHeight.HD_720)
        assertEquals(0, size.width % 2)
        assertEquals(0, size.height % 2)
    }

    @Test
    fun cropToNdc_mapsTopLeftOriginToGlCoordinates() {
        val ndc = ExportPlan.cropToNdc(CropRect(0.25f, 0f, 0.75f, 1f))
        assertArrayEquals(floatArrayOf(-0.5f, 0.5f, -1f, 1f), ndc, 0.0001f)
    }

    @Test
    fun centerCrop_square() {
        val crop = centerCrop(1920, 1080, 1f)!!
        assertEquals(0.21875f, crop.left, 0.0001f)
        assertEquals(0.78125f, crop.right, 0.0001f)
        assertEquals(0f, crop.top, 0.0001f)
        assertEquals(1f, crop.bottom, 0.0001f)
        assertNull(centerCrop(0, 0, 1f))
    }

    @Test
    fun brightness_fadesClipEdgesAndTransitions() {
        val windows = ExportPlan.fadeWindows(listOf(video("a", fadeIn = 1_000), video("b")), transitionMs = 1_000)
        assertEquals(0f, ExportPlan.brightnessAt(windows, 0), 0.001f)
        assertEquals(0.5f, ExportPlan.brightnessAt(windows, 500), 0.001f)
        assertEquals(1f, ExportPlan.brightnessAt(windows, 5_000), 0.001f)
        // Dip to black around the cut at 10s.
        assertEquals(0.5f, ExportPlan.brightnessAt(windows, 9_750), 0.001f)
        assertEquals(0f, ExportPlan.brightnessAt(windows, 10_000), 0.001f)
        assertEquals(1f, ExportPlan.brightnessAt(windows, 19_999 - 10), 0.001f)
    }

    @Test
    fun gain_appliesVolumeAndFades() {
        assertEquals(0.5f, ExportPlan.gainAt(0.5f, 0, 0, 10_000, 5_000), 0.001f)
        assertEquals(0.25f, ExportPlan.gainAt(1f, 2_000, 0, 10_000, 500), 0.001f)
        assertEquals(0.5f, ExportPlan.gainAt(1f, 0, 2_000, 10_000, 9_000), 0.001f)
    }

    @Test
    fun fitAudio_trimsMusicToVideoLength() {
        val a = AudioClip("a", "file://a.m4a", sourceDurationMs = 8_000, trimEndMs = 8_000)
        val b = AudioClip("b", "file://b.m4a", sourceDurationMs = 8_000, trimEndMs = 8_000)
        val fitted = ExportPlan.fitAudio(listOf(a, b, a.copy(id = "c")), videoDurationMs = 12_000)
        assertEquals(listOf("a", "b"), fitted.map { it.id })
        assertEquals(4_000, fitted[1].outputDurationMs())
    }
}
