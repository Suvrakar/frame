package app.frame.studio.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device beat tracking: spectral-flux onsets, tempo from autocorrelation (with a gentle
 * preference for ~120 BPM), beat phase from the onset envelope, then each beat nudged onto the
 * nearest onset peak. Pure Kotlin, no models; a 3-minute song takes well under a second.
 */
object BeatDetector {

    data class Result(val bpm: Float, val beatsMs: List<Long>)

    private const val FRAME = 1024
    private const val HOP = 256
    private const val MIN_BPM = 70f
    private const val MAX_BPM = 180f

    fun detect(mono: FloatArray, sampleRate: Int): Result? {
        if (mono.size < FRAME * 8 || sampleRate <= 0) return null
        val flux = onsetEnvelope(mono)
        if (flux.size < 16) return null
        val framesPerSecond = sampleRate.toFloat() / HOP

        // Tempo: autocorrelation over the plausible BPM range, weighted toward common tempos.
        val minLag = (60f / MAX_BPM * framesPerSecond).toInt().coerceAtLeast(1)
        val maxLag = (60f / MIN_BPM * framesPerSecond).toInt().coerceAtMost(flux.size / 2)
        if (maxLag <= minLag) return null
        val scores = FloatArray(maxLag + 2)
        var bestLag = minLag
        for (lag in minLag..maxLag) {
            var sum = 0f
            for (i in 0 until flux.size - lag) sum += flux[i] * flux[i + lag]
            val bpm = 60f * framesPerSecond / lag
            val prior = exp(-0.5 * (ln(bpm / 120.0) / 0.45).let { it * it }).toFloat()
            scores[lag] = sum / (flux.size - lag) * prior
            if (scores[lag] > scores[bestLag]) bestLag = lag
        }
        // Sub-frame precision by parabolic interpolation.
        val period = if (bestLag in (minLag + 1) until maxLag) {
            val a = scores[bestLag - 1]
            val b = scores[bestLag]
            val c = scores[bestLag + 1]
            val denom = a - 2 * b + c
            if (denom != 0f) bestLag + 0.5f * (a - c) / denom else bestLag.toFloat()
        } else {
            bestLag.toFloat()
        }

        // Phase: the offset whose beat grid collects the most onset energy.
        var bestOffset = 0
        var bestScore = -1f
        for (offset in 0 until period.toInt().coerceAtLeast(1)) {
            var sum = 0f
            var t = offset.toFloat()
            while (t < flux.size) {
                sum += flux[t.roundToInt().coerceAtMost(flux.size - 1)]
                t += period
            }
            if (sum > bestScore) {
                bestScore = sum
                bestOffset = offset
            }
        }

        // Beats on the grid, each snapped to the strongest onset within ±12% of a period.
        val window = (period * 0.12f).roundToInt().coerceAtLeast(1)
        val beats = mutableListOf<Long>()
        var t = bestOffset.toFloat()
        while (t < flux.size) {
            val center = t.roundToInt()
            var peak = center
            for (i in (center - window).coerceAtLeast(0)..(center + window).coerceAtMost(flux.size - 1)) {
                if (flux[i] > flux[peak.coerceIn(0, flux.size - 1)]) peak = i
            }
            // Frames are timestamped at their centre, where the windowed onset energy peaks.
            val ms = ((peak.coerceIn(0, flux.size - 1) * HOP + FRAME / 2) * 1000L) / sampleRate
            if (beats.isEmpty() || ms - beats.last() > (period * HOP * 1000f / sampleRate) * 0.5f) beats += ms
            t += period
        }
        return Result(bpm = 60f * framesPerSecond / period, beatsMs = beats)
    }

    /** Positive spectral flux on a log-compressed magnitude spectrum, with the local mean removed. */
    fun onsetEnvelope(mono: FloatArray): FloatArray {
        val frames = (mono.size - FRAME) / HOP + 1
        if (frames <= 1) return FloatArray(0)
        val window = FloatArray(FRAME) { (0.5 - 0.5 * cos(2 * PI * it / (FRAME - 1))).toFloat() }
        val re = FloatArray(FRAME)
        val im = FloatArray(FRAME)
        val bins = FRAME / 2
        var prev = FloatArray(bins)
        val flux = FloatArray(frames)
        for (f in 0 until frames) {
            val start = f * HOP
            for (i in 0 until FRAME) {
                re[i] = mono[start + i] * window[i]
                im[i] = 0f
            }
            fft(re, im)
            val mag = FloatArray(bins)
            var sum = 0f
            for (k in 1 until bins) {
                mag[k] = ln(1f + 100f * sqrt(re[k] * re[k] + im[k] * im[k]))
                val d = mag[k] - prev[k]
                if (d > 0) sum += d
            }
            // The first frame has nothing to compare against; it is not an onset.
            flux[f] = if (f == 0) 0f else sum
            prev = mag
        }
        // Remove the slowly varying loudness so only onsets remain.
        val half = 8
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var mean = 0f
            var n = 0
            for (j in (i - half).coerceAtLeast(0)..(i + half).coerceAtMost(frames - 1)) {
                mean += flux[j]
                n++
            }
            out[i] = max(0f, flux[i] - mean / n)
        }
        return out
    }

    /** In-place iterative radix-2 FFT; [re] and [im] must have a power-of-two length. */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val aRe = re[i + k]
                    val aIm = im[i + k]
                    val bRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val bIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = aRe + bRe
                    im[i + k] = aIm + bIm
                    re[i + k + len / 2] = aRe - bRe
                    im[i + k + len / 2] = aIm - bIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
