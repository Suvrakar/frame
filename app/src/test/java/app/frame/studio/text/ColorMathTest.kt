package app.frame.studio.text

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMathTest {

    @Test
    fun primaries() {
        assertEquals(0xFFFF0000, ColorMath.hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00, ColorMath.hsvToArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FF, ColorMath.hsvToArgb(240f, 1f, 1f))
        assertEquals(0xFFFFFFFF, ColorMath.hsvToArgb(0f, 0f, 1f))
        assertEquals(0xFF000000, ColorMath.hsvToArgb(200f, 0.5f, 0f))
    }

    @Test
    fun roundTrips() {
        listOf(0xFFFFD60A, 0xFF5B63F0, 0xFFFF375F, 0xFF30D158, 0xFF808080).forEach { argb ->
            val (h, s, v) = ColorMath.argbToHsv(argb).let { Triple(it[0], it[1], it[2]) }
            assertEquals(argb, ColorMath.hsvToArgb(h, s, v))
        }
    }

    @Test
    fun hueWraps() {
        assertEquals(ColorMath.hsvToArgb(0f, 1f, 1f), ColorMath.hsvToArgb(360f, 1f, 1f))
    }
}
