package app.frame.studio.audio

import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.VideoClip
import app.frame.studio.timeline.BeatSync
import app.frame.studio.timeline.TimelineMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class BeatDetectorTest {

    /** A metronome: short decaying tone bursts every beat, over quiet noise. */
    private fun clickTrack(bpm: Float, seconds: Float, rate: Int = 11_025, offsetMs: Int = 120): FloatArray {
        val random = Random(7)
        val out = FloatArray((seconds * rate).toInt()) { (random.nextFloat() - 0.5f) * 0.02f }
        val period = 60f / bpm * rate
        var t = offsetMs / 1000f * rate
        while (t < out.size) {
            val start = t.toInt()
            for (i in 0 until (0.06f * rate).toInt()) {
                if (start + i >= out.size) break
                out[start + i] += (sin(2 * PI * 880 * i / rate) * exp(-i / (0.012 * rate))).toFloat() * 0.8f
            }
            t += period
        }
        return out
    }

    @Test
    fun fftFindsAPureTone() {
        val n = 1024
        val re = FloatArray(n) { sin(2 * PI * 64 * it / n).toFloat() }
        val im = FloatArray(n)
        BeatDetector.fft(re, im)
        val mags = (0 until n / 2).map { re[it] * re[it] + im[it] * im[it] }
        assertEquals(64, mags.indices.maxBy { mags[it] })
    }

    @Test
    fun detectsTempoAndBeatPositions() {
        listOf(90f, 120f, 128f, 150f).forEach { bpm ->
            val result = BeatDetector.detect(clickTrack(bpm, 20f), 11_025)
            assertNotNull(result)
            assertEquals("bpm $bpm", bpm, result!!.bpm, bpm * 0.03f)
            val period = 60_000f / bpm
            // Every detected beat sits within 35 ms of a true beat.
            result.beatsMs.forEach { b ->
                val phase = ((b - 120) % period + period) % period
                assertTrue("bpm $bpm beat $b", minOf(phase, period - phase) < 35f)
            }
            assertTrue(result.beatsMs.size >= (20 * bpm / 60f * 0.85f).toInt())
        }
    }

    @Test
    fun retimesClipsOntoBeats() {
        val music = AudioClip("m", "x", sourceDurationMs = 60_000, trimStartMs = 1_000, trimEndMs = 30_000, startMs = 0,
            beatsMs = (0 until 60).map { 1_000L + it * 500L })
        val beats = BeatSync.timelineBeats(music)
        assertEquals(0L, beats.first())
        val cuts = BeatSync.cutPoints(beats, every = 2)
        // Every 2 beats counted from the downbeat at 0.
        assertEquals(listOf(1_000L, 2_000L, 3_000L), cuts.take(3))

        val clips = listOf(
            VideoClip("a", "x", sourceDurationMs = 10_000, trimEndMs = 2_300),
            VideoClip("b", "x", sourceDurationMs = 10_000, trimStartMs = 1_000, trimEndMs = 4_100, speed = 2f),
            VideoClip("c", "x", sourceDurationMs = 60_000, trimEndMs = 3_000, isImage = true),
            VideoClip("d", "x", sourceDurationMs = 800, trimEndMs = 800),
        )
        val retimed = BeatSync.retime(clips, cuts)
        val ends = TimelineMath.videoStarts(retimed).zip(retimed) { s, c -> s + c.outputDurationMs() }
        // a: 2300 -> 2000; b: 1550 out -> 2000 out (4000 source ms at 2x); c: 3000 -> 3000 (ends on 7000).
        assertEquals(listOf(2_000L, 4_000L, 7_000L), ends.take(3))
        assertEquals(1_000L + 4_000L, retimed[1].trimEndMs)
        // d has only 800 ms of footage: the next cut (8000) needs 1000 ms -> keeps its length.
        assertEquals(800L, retimed[3].outputDurationMs())
    }
}
