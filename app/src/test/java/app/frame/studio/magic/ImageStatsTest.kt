package app.frame.studio.magic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ImageStatsTest {
    private val w = 64
    private val h = 48

    private fun checker(cell: Int) = FloatArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if ((x / cell + y / cell) % 2 == 0) 0.85f else 0.15f
    }

    private fun blur(src: FloatArray, passes: Int): FloatArray {
        var a = src
        repeat(passes) {
            val b = FloatArray(a.size)
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0f
                var c = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx
                    val yy = y + dy
                    if (xx in 0 until w && yy in 0 until h) {
                        s += a[yy * w + xx]
                        c++
                    }
                }
                b[y * w + x] = s / c
            }
            a = b
        }
        return a
    }

    @Test
    fun sharpImageScoresHigherThanBlurred() {
        val sharp = ImageStats.analyze(checker(4), w, h)
        val soft = ImageStats.analyze(blur(checker(4), 6), w, h)
        assertTrue(sharp.sharpness > soft.sharpness * 4)
        assertTrue(ImageStats.score(sharp) > ImageStats.score(soft))
    }

    @Test
    fun darkImageScoresLowerThanWellExposed() {
        val good = checker(4)
        val dark = FloatArray(good.size) { good[it] * 0.08f }
        assertTrue(ImageStats.score(ImageStats.analyze(good, w, h)) > ImageStats.score(ImageStats.analyze(dark, w, h)))
    }

    @Test
    fun hashMatchesNearDuplicatesAndSeparatesDifferentShots() {
        val rnd = Random(7)
        val base = FloatArray(w * h) { i -> ((i % w).toFloat() / w + (i / w).toFloat() / h) / 2f }
        val noisy = FloatArray(base.size) { (base[it] + (rnd.nextFloat() - 0.5f) * 0.02f).coerceIn(0f, 1f) }
        val flipped = FloatArray(base.size) { i -> base[(i / w) * w + (w - 1 - i % w)] }
        val a = ImageStats.dHash(base, w, h)
        assertTrue(ImageStats.distance(a, ImageStats.dHash(noisy, w, h)) <= MagicPlan.DUPLICATE_DISTANCE)
        assertTrue(ImageStats.distance(a, ImageStats.dHash(flipped, w, h)) > 20)
        assertEquals(0, ImageStats.distance(a, a))
    }
}
