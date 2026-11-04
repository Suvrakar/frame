package app.frame.studio.magic

import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagicPlanTest {

    private var n = 0
    private fun id() = "id${n++}"

    private fun photo(i: Int, score: Float = 0.6f, hash: Long = i * 0x0F0F0F0F0FL, date: Long? = null) =
        MagicAsset("p$i", true, 0, 1080, 1920, "p$i", date, score, hash, i)

    private fun video(i: Int, durationMs: Long = 10_000, date: Long? = null) =
        MagicAsset("v$i", false, durationMs, 1920, 1080, "v$i", date, 0.7f, i * 0x3333333333L.inv(), i, bestMs = durationMs / 2)

    @Test
    fun dedupeKeepsBestOfBurst() {
        val a = photo(0, score = 0.4f, hash = 0L)
        val b = photo(1, score = 0.9f, hash = 0b111L) // 3 bits away: same burst
        val c = photo(2, score = 0.5f, hash = -1L)
        val kept = MagicPlan.dedupe(listOf(a, b, c))
        assertEquals(setOf("p1", "p2"), kept.map { it.uri }.toSet())
    }

    @Test
    fun ordersByCaptureDateThenPickOrder() {
        val list = listOf(photo(0, date = 300), photo(1, date = 100), photo(2, date = null), photo(3, date = 200))
        assertEquals(listOf("p1", "p3", "p0", "p2"), MagicPlan.chronological(list).map { it.uri })
    }

    @Test
    fun targetLengthLimitsTheBestShots() {
        val assets = (0 until 30).map { photo(it, score = it / 30f) }
        val beat = MagicPlan.beatMs(null, MagicVibe.UPBEAT)
        val picked = MagicPlan.select(assets, MagicVibe.UPBEAT, MagicLength.SHORT, beat)
        val total = picked.sumOf { MagicPlan.shotMs(it, beat, MagicVibe.UPBEAT) }
        assertTrue("total $total", total in 15_000..(15_000 + MagicPlan.MAX_PHOTO_MS))
        // The best-scored shots were chosen.
        val left = assets.filter { a -> picked.none { it.uri == a.uri } }
        assertTrue(picked.minOf { it.score } > left.maxOf { it.score })
    }

    @Test
    fun beatFromMusicUsesMedianGap() {
        val music = MagicMusic("m", 60_000, "song", (0 until 40).map { it * 500L }, 120f)
        assertEquals(500L, MagicPlan.beatMs(music, MagicVibe.CHILL))
        assertEquals(MagicVibe.CHILL.defaultBeatMs, MagicPlan.beatMs(null, MagicVibe.CHILL))
    }

    @Test
    fun buildsBeatSyncedMovieWithTitleMusicAndEffect() {
        val music = MagicMusic("m", 120_000, "song", (0 until 240).map { 250L + it * 500L }, 120f)
        val assets = (0 until 6).map { photo(it) } + video(6) + video(7, durationMs = 1_500)
        val p = MagicPlan.build(
            assets, MagicVibe.PARTY, MagicLength.ALL, "Summer", "June 2026", music, CanvasAspect.R9_16, "pid", 0L, ::id,
        )
        assertEquals(8, p.videoClips.size)
        assertEquals(CanvasAspect.R9_16, p.aspect)
        assertEquals("Summer", p.name)
        // Every cut lands on a beat of the song.
        val beats = music.beatsMs.toSet()
        var t = 0L
        p.videoClips.dropLast(1).forEach { c ->
            t += c.outputDurationMs()
            assertTrue("cut at $t", t in beats)
        }
        assertEquals(TransitionType.NONE, p.videoClips.last().transition)
        assertTrue(p.videoClips.all { it.filterId == MagicVibe.PARTY.filterId })
        // Video sound is ducked under the music.
        assertTrue(p.videoClips.filter { !it.isImage }.all { it.volume < 0.5f })
        // Short videos never ask for more footage than they have.
        p.videoClips.filter { !it.isImage }.forEach { assertTrue(it.trimEndMs <= it.sourceDurationMs) }
        assertEquals(1, p.audioClips.size)
        assertEquals(p.videoClips.sumOf { it.outputDurationMs() }, p.audioClips[0].outputDurationMs())
        assertEquals(listOf("Summer", "June 2026"), p.textOverlays.map { it.text })
        assertEquals(1, p.effects.size)
    }

    @Test
    fun worksWithoutMusicOrTitle() {
        val p = MagicPlan.build(
            listOf(photo(0), photo(1)), MagicVibe.CHILL, MagicLength.MEDIUM, "", null, null, CanvasAspect.R1_1, "pid", 0L, ::id,
        )
        assertTrue(p.audioClips.isEmpty())
        assertTrue(p.textOverlays.isEmpty())
        assertEquals("Magic Movie", p.name)
        assertFalse(p.videoClips.any { it.outputDurationMs() < MagicPlan.MIN_SHOT_MS })
    }

    @Test
    fun dateSpanFormats() {
        val months = listOf("Jan", "Feb", "Mar")
        fun my(ms: Long) = "${months[(ms % 100).toInt()]} ${ms / 100}"
        fun m(ms: Long) = months[(ms % 100).toInt()]
        fun y(ms: Long) = (ms / 100).toInt()
        val a = photo(0, date = 202600)
        assertEquals("Jan 2026", MagicPlan.dateSpan(listOf(a, photo(1, date = 202600)), ::my, ::m, ::y))
        assertEquals("Jan – Mar 2026", MagicPlan.dateSpan(listOf(a, photo(1, date = 202602)), ::my, ::m, ::y))
        assertEquals("Mar 2025 – Jan 2026", MagicPlan.dateSpan(listOf(a, photo(1, date = 202502)), ::my, ::m, ::y))
        assertEquals(null, MagicPlan.dateSpan(listOf(photo(3)), ::my, ::m, ::y))
    }
}
