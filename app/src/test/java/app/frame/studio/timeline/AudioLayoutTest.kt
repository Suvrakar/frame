package app.frame.studio.timeline

import app.frame.studio.domain.AudioClip
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioLayoutTest {

    private fun clip(id: String, start: Long, duration: Long) =
        AudioClip(id, "file://$id.m4a", sourceDurationMs = 60_000, trimEndMs = duration, startMs = start)

    @Test
    fun legacyClipsArePlacedBackToBack() {
        val placed = AudioLayout.normalize(listOf(clip("a", -1, 2_000), clip("b", -1, 3_000)))
        assertEquals(listOf(0L, 2_000L), placed.map { it.startMs })
    }

    @Test
    fun overlappingClipsGetSeparateLanes() {
        val lanes = AudioLayout.lanes(listOf(clip("music", 0, 10_000), clip("voice", 2_000, 3_000), clip("sfx", 6_000, 1_000)))
        assertEquals(0, lanes["music"])
        assertEquals(1, lanes["voice"])
        assertEquals(1, lanes["sfx"])
    }

    @Test
    fun clampDropsAndShortensPastTheEnd() {
        val clamped = AudioLayout.clampTo(listOf(clip("a", 0, 8_000), clip("b", 9_000, 5_000), clip("c", 12_000, 1_000)), 10_000)
        assertEquals(listOf("a", "b"), clamped.map { it.id })
        assertEquals(1_000, clamped[1].outputDurationMs())
    }
}
