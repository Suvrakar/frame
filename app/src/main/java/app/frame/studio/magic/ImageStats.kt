package app.frame.studio.magic

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Cheap, on-device photo quality signals computed from a small grayscale thumbnail
 * (luma 0..1, row-major). Pure Kotlin so it is unit tested without a device.
 */
object ImageStats {

    data class Stats(
        /** Variance of the Laplacian: higher = sharper. */
        val sharpness: Float,
        /** Mean luma 0..1. */
        val brightness: Float,
        /** Standard deviation of luma. */
        val contrast: Float,
        /** 64-bit difference hash; near-identical shots differ in only a few bits. */
        val hash: Long,
    )

    fun analyze(luma: FloatArray, width: Int, height: Int): Stats {
        require(width >= 3 && height >= 3 && luma.size >= width * height) { "image too small" }
        var sum = 0.0
        for (i in 0 until width * height) sum += luma[i]
        val mean = (sum / (width * height)).toFloat()
        var varSum = 0.0
        for (i in 0 until width * height) {
            val d = luma[i] - mean
            varSum += d * d
        }
        val contrast = sqrt(varSum / (width * height)).toFloat()

        // 4-neighbour Laplacian on the interior.
        var lapSum = 0.0
        var lapSq = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val l = luma[i - 1] + luma[i + 1] + luma[i - width] + luma[i + width] - 4 * luma[i]
                lapSum += l
                lapSq += l * l
                n++
            }
        }
        val lapMean = lapSum / n
        val sharpness = (lapSq / n - lapMean * lapMean).toFloat().coerceAtLeast(0f)
        return Stats(sharpness, mean, contrast, dHash(luma, width, height))
    }

    /** Difference hash on a 9×8 box-downscaled image. */
    fun dHash(luma: FloatArray, width: Int, height: Int): Long {
        val small = FloatArray(9 * 8)
        for (gy in 0 until 8) {
            val y0 = gy * height / 8
            val y1 = maxOf(y0 + 1, (gy + 1) * height / 8)
            for (gx in 0 until 9) {
                val x0 = gx * width / 9
                val x1 = maxOf(x0 + 1, (gx + 1) * width / 9)
                var s = 0f
                var c = 0
                for (y in y0 until y1) for (x in x0 until x1) {
                    s += luma[y * width + x]
                    c++
                }
                small[gy * 9 + gx] = s / c
            }
        }
        var hash = 0L
        var bit = 0
        for (gy in 0 until 8) for (gx in 0 until 8) {
            if (small[gy * 9 + gx] > small[gy * 9 + gx + 1]) hash = hash or (1L shl bit)
            bit++
        }
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * 0..1 quality score: sharp, well exposed and not flat. Thresholds are tuned for
     * thumbnails around 128 px on the long side.
     */
    fun score(stats: Stats): Float {
        val sharp = (stats.sharpness / SHARP_REFERENCE).coerceIn(0f, 1f)
        val exposure = 1f - (abs(stats.brightness - 0.48f) / 0.48f).coerceIn(0f, 1f)
        val contrast = (stats.contrast / 0.22f).coerceIn(0f, 1f)
        return (0.5f * sharp + 0.3f * exposure + 0.2f * contrast).coerceIn(0f, 1f)
    }

    private const val SHARP_REFERENCE = 0.01f
}
