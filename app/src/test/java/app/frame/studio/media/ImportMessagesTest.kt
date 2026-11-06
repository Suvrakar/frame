package app.frame.studio.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportMessagesTest {
    @Test
    fun noFailuresNoMessage() = assertNull(ImportMessages.summary(emptyList()))

    @Test
    fun singleFile() = assertEquals(
        "1 file couldn't be added: Too big.",
        ImportMessages.summary(listOf("Too big.")),
    )

    @Test
    fun manyFilesUseFirstReason() = assertEquals(
        "2 files couldn't be added: Too big.",
        ImportMessages.summary(listOf("Too big.", "Other.")),
    )

    @Test
    fun mutedAudioNamesCodec() {
        assertEquals(
            "Original sound turned off: this phone can't play the video's audio (Dolby Digital (AC-3)).",
            ImportMessages.mutedAudio("Dolby Digital (AC-3)"),
        )
        assertEquals("Original sound turned off: this phone can't play the video's audio.", ImportMessages.mutedAudio(null))
    }
}
