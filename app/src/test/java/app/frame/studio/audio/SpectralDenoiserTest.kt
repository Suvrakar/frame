package app.frame.studio.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class SpectralDenoiserTest {
    private val rate = 48_000

    private fun run(d: SpectralDenoiser, x: FloatArray): FloatArray {
        // Feed in odd-sized chunks, like an audio pipeline, then flush with silence.
        val out = ArrayList<Float>()
        var i = 0
        val sizes = intArrayOf(333, 1024, 77, 4096)
        var n = 0
        while (i < x.size) {
            val c = minOf(sizes[n++ % sizes.size], x.size - i)
            out += d.process(x.copyOfRange(i, i + c)).toList()
            i += c
        }
        out += d.process(FloatArray(SpectralDenoiser.HOP * 2)).toList()
        return out.drop(SpectralDenoiser.LATENCY).take(x.size).toFloatArray()
    }

    @Test fun offReconstructsTheInputExactly() {
        val r = Random(1)
        val x = FloatArray(rate) { r.nextFloat() * 2 - 1 }
        val y = run(SpectralDenoiser(0f, rate), x)
        assertEquals(x.size, y.size)
        // Skip the first frame (the window ramps in from silence).
        for (i in SpectralDenoiser.N until x.size) assertEquals("sample $i", x[i], y[i], 1e-4f)
    }

    @Test fun removesHissAndKeepsSpeechLikeBursts() {
        val r = Random(7)
        val seconds = 4
        // "Syllables": 250 ms tone bursts with 200 ms gaps, smooth edges (speech is never a
        // constant tone; minimum statistics learn the noise in the gaps).
        fun envelope(i: Int): Double {
            val t = (i % (rate * 45 / 100)).toDouble() / rate
            return if (t < 0.25) sin(PI * t / 0.25) else 0.0
        }
        val clean = FloatArray(rate * seconds) { (0.3 * envelope(it) * sin(2 * PI * 440 * it / rate)).toFloat() }
        val noise = FloatArray(clean.size) { (r.nextGaussian() * 0.05).toFloat() }
        val noisy = FloatArray(clean.size) { clean[it] + noise[it] }
        val y = run(SpectralDenoiser(1f, rate), noisy)
        // Judge the last 2 s, after the noise floor has been learned.
        val from = rate * 2
        var noiseBefore = 0.0
        var noiseAfter = 0.0
        var tone = 0.0
        var toneKept = 0.0
        for (i in from until clean.size) {
            noiseBefore += noise[i] * noise[i]
            val err = y[i] - clean[i]
            noiseAfter += err * err
            tone += clean[i] * clean[i]
            toneKept += y[i] * clean[i]
        }
        val reductionDb = 10 * kotlin.math.log10(noiseBefore / noiseAfter)
        assertTrue("noise should drop by > 6 dB, was $reductionDb dB", reductionDb > 6)
        assertTrue("the voice must survive (${toneKept / tone})", toneKept / tone > 0.8)
    }
}
