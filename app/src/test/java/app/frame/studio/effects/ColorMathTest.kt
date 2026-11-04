package app.frame.studio.effects

import app.frame.studio.domain.ColorAdjust
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    @Test
    fun identityIsNeutral() {
        assertArrayEquals(floatArrayOf(0.2f, 0.4f, 0.6f), ColorTransform.IDENTITY.apply(0.2f, 0.4f, 0.6f), 1e-5f)
        assertTrue(Filters.transformFor("none", 1f, ColorAdjust()).isIdentity)
    }

    @Test
    fun monoProducesGray() {
        val out = Filters.byId("mono").transform.apply(1f, 0f, 0f)
        assertEquals(out[0], out[1], 1e-5f)
        assertEquals(out[1], out[2], 1e-5f)
        assertEquals(0.2126f, out[0], 1e-4f)
    }

    @Test
    fun composeAppliesRightOperandFirst() {
        val brighten = ColorTransform.brightness(0.1f)
        val double = ColorTransform.scale(2f, 2f, 2f)
        // double first, then brighten: 0.2*2+0.1
        assertEquals(0.5f, (brighten * double).apply(0.2f, 0f, 0f)[0], 1e-5f)
        // brighten first, then double: (0.2+0.1)*2
        assertEquals(0.6f, (double * brighten).apply(0.2f, 0f, 0f)[0], 1e-5f)
    }

    @Test
    fun intensityBlendsWithIdentity() {
        val half = Filters.byId("mono").transform.mix(0.5f).apply(1f, 0f, 0f)
        assertEquals((1f + 0.2126f) / 2, half[0], 1e-4f)
    }

    @Test
    fun glAndAndroidLayouts() {
        val t = ColorTransform.brightness(0.25f)
        val gl = t.toGl()
        assertEquals(0.25f, gl[12], 1e-6f)
        assertEquals(1f, gl[15], 1e-6f)
        val android = t.toAndroid()
        assertEquals(63.75f, android[4], 1e-4f)
        assertEquals(20, android.size)
    }

    @Test
    fun everyFilterHasUniqueId() {
        assertEquals(Filters.all.size, Filters.all.map { it.id }.toSet().size)
    }

    @Test
    fun everyGroupHasLooksAndNoneBlowsOutMidGray() {
        FilterGroup.entries.forEach { g -> assertTrue(g.label, Filters.inGroup(g).size >= 6) }
        assertTrue(Filters.all.size >= 50)
        Filters.all.forEach { f ->
            f.transform.apply(0.5f, 0.5f, 0.5f).forEach { v -> assertTrue("${f.id}=$v", v in 0.2f..0.8f) }
        }
    }

    @Test
    fun blackAndWhiteLooksStayNeutralOrToned() {
        Filters.inGroup(FilterGroup.BW).filter { it.id != "none" }.forEach { f ->
            val out = f.transform.apply(1f, 0f, 0f)
            // Pure red maps to (nearly) equal channels: a monochrome image.
            assertEquals(f.id, out[0], out[1], 0.08f)
            assertEquals(f.id, out[1], out[2], 0.08f)
        }
    }

    @Test
    fun textureCombinesFilterAndAdjust() {
        val none = Filters.textureFor("none", 1f, ColorAdjust())
        assertTrue(none.isNone)
        val reel = Filters.textureFor("old_reel", 0.5f, ColorAdjust(vignette = 0.2f))
        assertEquals(0.55f * 0.5f + 0.2f, reel.vignette, 1e-5f)
        assertEquals(0.25f, reel.grain, 1e-5f)
        assertEquals(1f, Filters.textureFor("pinhole", 1f, ColorAdjust(vignette = 0.9f)).vignette, 1e-5f)
        assertTrue(ColorAdjust(grain = 0.3f).isColorIdentity)
        assertTrue(Filters.inGroup(FilterGroup.LOFI).all { it.id == "none" || it.grain > 0f })
    }

    @Test
    fun exposureTintAndFade() {
        assertEquals(0.4f, ColorTransform.exposure(1f).apply(0.2f, 0.2f, 0.2f)[0], 1e-4f)
        assertEquals(0.1f, ColorTransform.exposure(-1f).apply(0.2f, 0.2f, 0.2f)[0], 1e-4f)
        val magenta = ColorTransform.tint(1f).apply(0.5f, 0.5f, 0.5f)
        assertTrue(magenta[0] > 0.5f && magenta[1] < 0.5f && magenta[2] > 0.5f)
        // Fade lifts pure black.
        assertEquals(0.14f, ColorTransform.fade(1f).apply(0f, 0f, 0f)[0], 1e-4f)
        assertTrue(ColorAdjust(highlights = 0.5f).isColorIdentity)
        assertTrue(!ColorAdjust(exposure = 0.1f).isColorIdentity)
    }

    @Test
    fun toneGradingTargetsShadowsAndHighlights() {
        val lift = GradeParams(0f, 0f, shadows = 1f)
        assertTrue(GradeMath.tone(floatArrayOf(0.1f, 0.1f, 0.1f), lift)[0] > 0.3f)
        // Shadows barely touch bright pixels.
        assertEquals(0.9f, GradeMath.tone(floatArrayOf(0.9f, 0.9f, 0.9f), lift)[0], 0.01f)
        val recover = GradeParams(0f, 0f, highlights = -1f)
        assertTrue(GradeMath.tone(floatArrayOf(0.95f, 0.95f, 0.95f), recover)[0] < 0.75f)
        assertEquals(0.1f, GradeMath.tone(floatArrayOf(0.1f, 0.1f, 0.1f), recover)[0], 0.01f)
    }

    @Test
    fun splitToneTintsWithoutChangingBrightness() {
        val teal = GradeMath.hueTint(190f)
        assertEquals(0f, GradeMath.luma(teal[0], teal[1], teal[2]), 1e-4f)
        val p = Filters.gradeFor("none", 1f, ColorAdjust(shadowHue = 210f, shadowTone = 1f, highlightHue = 35f, highlightTone = 1f))
        val dark = GradeMath.tone(floatArrayOf(0.15f, 0.15f, 0.15f), p)
        assertTrue("shadows go blue", dark[2] > dark[0])
        val bright = GradeMath.tone(floatArrayOf(0.85f, 0.85f, 0.85f), p)
        assertTrue("highlights go orange", bright[0] > bright[2])
        assertTrue(!p.isNone)
        assertTrue(Filters.gradeFor("none", 1f, ColorAdjust()).isNone)
    }

    @Test
    fun vibranceSparesSaturatedColours() {
        val p = GradeParams(0f, 0f, vibrance = 1f)
        val muted = GradeMath.tone(floatArrayOf(0.55f, 0.5f, 0.45f), p)
        val vivid = GradeMath.tone(floatArrayOf(0.9f, 0.1f, 0.1f), p)
        val mutedGain = (muted[0] - muted[2]) / 0.1f
        val vividGain = (vivid[0] - vivid[2]) / 0.8f
        assertTrue(mutedGain > vividGain)
    }
}
