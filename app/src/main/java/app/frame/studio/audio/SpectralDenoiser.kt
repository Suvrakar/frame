package app.frame.studio.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Streaming spectral noise reduction for one channel (pure Kotlin; JVM-tested).
 *
 * Short-time FFT (1024-point, 50% overlap, sqrt-Hann analysis and synthesis windows, which
 * reconstruct the input exactly when nothing is removed). Per frequency band the noise floor is
 * tracked by minimum statistics — the quietest smoothed level over the last ~0.5–1 s — so it
 * adapts to hum, hiss and room tone without a "learn noise" step. Each band is then attenuated
 * by a smoothed spectral-subtraction gain, never below a floor set by [strength], which keeps
 * voices natural (no "underwater" artefacts).
 *
 * Output lags input by [LATENCY] samples; callers compensate (see NoiseReductionAudioProcessor).
 */
class SpectralDenoiser(strength: Float, sampleRate: Int) {
    private val s = strength.coerceIn(0f, 1f)
    private val window = FloatArray(N) { sqrt(0.5f - 0.5f * cos(2.0 * PI * it / N).toFloat()) }
    private val history = FloatArray(N)
    private var filled = 0
    private val ola = FloatArray(N)
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val bins = N / 2 + 1
    private val smoothed = FloatArray(bins)
    private val currentMin = FloatArray(bins) { Float.MAX_VALUE }
    private val previousMin = FloatArray(bins) { Float.MAX_VALUE }
    private val gains = FloatArray(bins) { 1f }
    private var frames = 0L
    private val minWindowFrames = max(4, (sampleRate * 0.5f / HOP).toInt())
    private val oversubtract = 1f + 1.5f * s
    private val floorGain = 10f.pow(-(6f + 18f * s) / 20f)

    /** Processes [count] samples of [input]; returns the samples ready so far (multiples of [HOP]). */
    fun process(input: FloatArray, count: Int = input.size): FloatArray {
        var out = FloatArray(0)
        var i = 0
        while (i < count) {
            val take = min(HOP - filled, count - i)
            System.arraycopy(input, i, history, HOP + filled, take)
            filled += take
            i += take
            if (filled == HOP) {
                out += frame()
                System.arraycopy(history, HOP, history, 0, HOP)
                filled = 0
            }
        }
        return out
    }

    private fun frame(): FloatArray {
        for (k in 0 until N) {
            re[k] = history[k] * window[k]
            im[k] = 0f
        }
        fft(re, im, inverse = false)
        if (s > 0f) suppress()
        fft(re, im, inverse = true)
        for (k in 0 until N) ola[k] += re[k] * window[k]
        val ready = ola.copyOfRange(0, HOP)
        System.arraycopy(ola, HOP, ola, 0, HOP)
        java.util.Arrays.fill(ola, HOP, N, 0f)
        frames++
        return ready
    }

    private fun suppress() {
        val roll = frames % minWindowFrames == 0L
        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            smoothed[k] = if (frames == 0L) p else 0.7f * smoothed[k] + 0.3f * p
            if (roll) {
                previousMin[k] = currentMin[k]
                currentMin[k] = smoothed[k]
            } else {
                currentMin[k] = min(currentMin[k], smoothed[k])
            }
            val noise = min(currentMin[k], previousMin[k]) * NOISE_BIAS
            val raw = if (p <= 1e-12f) floorGain else max(floorGain, 1f - oversubtract * noise / p)
            // Temporal smoothing tames "musical noise" (isolated bins flickering on and off).
            gains[k] = 0.6f * raw + 0.4f * gains[k]
        }
        for (k in 0 until bins) {
            // Light smoothing across neighbouring bands.
            val g = (gains[max(0, k - 1)] + 2 * gains[k] + gains[min(bins - 1, k + 1)]) / 4f
            re[k] *= g
            im[k] *= g
            if (k in 1 until N / 2) {
                re[N - k] *= g
                im[N - k] *= g
            }
        }
    }

    companion object {
        const val N = 1024
        const val HOP = N / 2
        /** Samples of delay between input and output. */
        const val LATENCY = HOP
        private const val NOISE_BIAS = 1.6f

        private val bitReverse = IntArray(N).also { r ->
            val bits = Integer.numberOfTrailingZeros(N)
            for (i in 0 until N) r[i] = Integer.reverse(i) ushr (32 - bits)
        }
        private val cosTable = FloatArray(N / 2) { cos(2.0 * PI * it / N).toFloat() }
        private val sinTable = FloatArray(N / 2) { sin(2.0 * PI * it / N).toFloat() }

        /** In-place radix-2 complex FFT of size [N]; the inverse is scaled by 1/N. */
        private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
            for (i in 0 until N) {
                val j = bitReverse[i]
                if (j > i) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var size = 2
            while (size <= N) {
                val half = size / 2
                val step = N / size
                var start = 0
                while (start < N) {
                    for (k in 0 until half) {
                        val c = cosTable[k * step]
                        val sn = if (inverse) sinTable[k * step] else -sinTable[k * step]
                        val a = start + k
                        val b = a + half
                        val xr = re[b] * c - im[b] * sn
                        val xi = re[b] * sn + im[b] * c
                        re[b] = re[a] - xr
                        im[b] = im[a] - xi
                        re[a] += xr
                        im[a] += xi
                    }
                    start += size
                }
                size *= 2
            }
            if (inverse) for (i in 0 until N) { re[i] /= N; im[i] /= N }
        }
    }
}
