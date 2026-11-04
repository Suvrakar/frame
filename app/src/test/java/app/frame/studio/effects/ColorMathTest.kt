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
}
