package app.frame.studio.audio

import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.AudioKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckingTest {
    private fun clip(start: Long, ms: Long, kind: AudioKind = AudioKind.VOICE, volume: Float = 1f) =
        AudioClip(id = "$start", sourceUri = "u", sourceDurationMs = ms, trimEndMs = ms, startMs = start, kind = kind, volume = volume)

    @Test fun onlyVoiceCounts() {
        val spans = Ducking.voiceSpans(listOf(clip(0, 1_000, AudioKind.MUSIC), clip(2_000, 1_000), clip(5_000, 500, AudioKind.SOUND)))
        assertEquals(listOf(2_000L until 3_000L), spans)
    }

    @Test fun mutedVoiceDoesNotDuck() {
        assertTrue(Ducking.voiceSpans(listOf(clip(0, 1_000, volume = 0f))).isEmpty())
    }

    @Test fun shortPausesAreBridged() {
        val spans = Ducking.voiceSpans(listOf(clip(0, 1_000), clip(1_300, 1_000), clip(5_000, 1_000)))
        assertEquals(2, spans.size)
        assertEquals(0L, spans[0].first)
        assertEquals(2_299L, spans[0].last)
    }

    @Test fun gainDipsUnderVoiceWithRamps() {
        val spans = listOf(2_000L until 3_000L)
        assertEquals(1f, Ducking.gainAt(spans, 1_000), 0f)
        assertEquals(Ducking.DEPTH, Ducking.gainAt(spans, 2_500), 0f)
        val mid = Ducking.gainAt(spans, 2_000 - Ducking.RAMP_MS / 2)
        assertTrue(mid < 1f && mid > Ducking.DEPTH)
        assertEquals(1f, Ducking.gainAt(spans, 3_000 + Ducking.RAMP_MS), 0f)
    }
}
