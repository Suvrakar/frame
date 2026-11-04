package app.frame.studio.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFontsTest {

    @Test
    fun idsAreUnique() {
        val ids = TextFonts.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun defaultIsFirstAndSystemFont() {
        val first = TextFonts.all.first()
        assertEquals(TextFonts.DEFAULT_ID, first.id)
        assertNull(first.res)
    }

    @Test
    fun unknownIdFallsBackToDefault() {
        assertEquals(TextFonts.DEFAULT_ID, TextFonts.byId("no_such_font").id)
    }

    @Test
    fun knownIdResolves() {
        assertEquals("pacifico", TextFonts.byId("pacifico").id)
    }

    @Test
    fun catalogHasTenDecorativeFonts() {
        assertEquals(10, TextFonts.all.count { it.res != null })
        assertTrue(TextFonts.all.all { it.label.isNotBlank() })
    }
}
