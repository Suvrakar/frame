package app.frame.studio.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryRulesTest {
    @Test fun validNamesPass() {
        assertTrue(TelemetryRules.isValidName("export_completed"))
        assertTrue(TelemetryRules.isValidName("a".repeat(40)))
    }

    @Test fun badNamesFail() {
        assertFalse(TelemetryRules.isValidName(""))
        assertFalse(TelemetryRules.isValidName("1export"))
        assertFalse(TelemetryRules.isValidName("export-done"))
        assertFalse(TelemetryRules.isValidName("a".repeat(41)))
        assertFalse(TelemetryRules.isValidName("firebase_x"))
        assertFalse(TelemetryRules.isValidName("google_x"))
        assertFalse(TelemetryRules.isValidName("ga_x"))
    }

    @Test fun textValuesAreCappedAt100() {
        assertEquals(100, (TelemetryRules.cleanValue("x".repeat(300)) as String).length)
    }

    @Test fun numbersAndBooleansAreKept() {
        assertEquals(5L, TelemetryRules.cleanValue(5))
        assertEquals(5L, TelemetryRules.cleanValue(5L))
        assertEquals(1.5, TelemetryRules.cleanValue(1.5f))
        assertEquals(1L, TelemetryRules.cleanValue(true))
        assertEquals(0L, TelemetryRules.cleanValue(false))
        assertNull(TelemetryRules.cleanValue(null))
    }

    @Test fun anythingThatLooksLikeAFileOrLinkIsDropped() {
        // Never send file names or locations: they can carry personal information.
        assertNull(TelemetryRules.cleanValue("content://media/external/video/42"))
        assertNull(TelemetryRules.cleanValue("file:///sdcard/DCIM/Me at home.mp4"))
        assertNull(TelemetryRules.cleanValue("/storage/emulated/0/x.mp4"))
    }

    @Test fun paramsAreCleanedAndLimited() {
        val p = TelemetryRules.cleanParams(
            listOf("ok" to 1, "bad-name" to 2, "path" to "content://x") + (1..40).map { "p$it" to it },
        )
        assertTrue("ok" in p)
        assertFalse("bad-name" in p)
        assertFalse("path" in p)
        assertEquals(25, p.size)
    }

    @Test fun screenNamesComeFromRoutesWithoutIds() {
        assertEquals("editor", TelemetryRules.screenName("editor/{projectId}?import={import}"))
        assertEquals("templates", TelemetryRules.screenName("templates?category={category}"))
        assertEquals("projects", TelemetryRules.screenName("projects"))
        assertNull(TelemetryRules.screenName(null))
    }
}
