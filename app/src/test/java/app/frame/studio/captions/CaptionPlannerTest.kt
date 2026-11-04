package app.frame.studio.captions

import app.frame.studio.domain.CaptionStyle
import app.frame.studio.domain.CaptionWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class CaptionPlannerTest {
    private var n = 0
    private fun id() = "c${n++}"

    @Test
    fun findsSpeechChunksBetweenPauses() {
        val rate = 16_000
        // 1 s speech-like tone, 1 s silence, 1.5 s tone, 0.5 s silence.
        fun tone(ms: Int) = FloatArray(rate * ms / 1000) { i -> 0.3f * sin(2 * PI * 220 * i / rate).toFloat() }
        fun quiet(ms: Int) = FloatArray(rate * ms / 1000) { i -> 0.001f * sin(i * 0.1f) }
        val samples = tone(1000) + quiet(1000) + tone(1500) + quiet(500)
        val chunks = CaptionPlanner.speechChunks(samples, rate)
        assertEquals(2, chunks.size)
        assertEquals(0.0, chunks[0].first.toDouble(), 60.0)
        assertEquals(1000.0, chunks[0].second.toDouble(), 200.0)
        assertEquals(2000.0, chunks[1].first.toDouble(), 200.0)
        assertEquals(3500.0, chunks[1].second.toDouble(), 200.0)
    }

    @Test
    fun silenceHasNoChunks() {
        assertTrue(CaptionPlanner.speechChunks(FloatArray(32_000), 16_000).isEmpty())
    }

    @Test
    fun longSpeechIsCappedPerChunk() {
        val rate = 16_000
        val samples = FloatArray(rate * 30) { i -> 0.3f * sin(2 * PI * 220 * i / rate).toFloat() }
        val chunks = CaptionPlanner.speechChunks(samples, rate, maxChunkMs = 12_000)
        assertTrue(chunks.size >= 3)
        assertTrue(chunks.all { it.second - it.first <= 12_400 })
    }

    @Test
    fun evenWordsWeightsByLength() {
        val w = CaptionPlanner.evenWords("hi wonderful", 0, 1000)
        assertEquals(listOf("hi", "wonderful"), w.map { it.text })
        assertEquals(0L, w[0].startMs)
        assertTrue(w[0].endMs < 400)
        assertEquals(1000L, w[1].endMs)
    }

    @Test
    fun placeWordsKeepsKnownTimesAndFillsTheRest() {
        val placed = CaptionPlanner.placeWords(
            listOf("one" to 100L, "two" to null, "three" to null, "four" to 1600L, "five" to null),
            1_000, 3_000,
        )
        // Offsets are relative to the chunk start.
        assertEquals(listOf(1100L, 2600L), listOf(placed[0].startMs, placed[3].startMs))
        assertTrue(placed[1].startMs in 1101..2599)
        assertTrue(placed[2].startMs in placed[1].startMs + 1..2599)
        assertTrue(placed[4].startMs in 2601..2999)
        assertTrue(placed.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
    }

    @Test
    fun linesBreakAtPausesPunctuationAndLength() {
        val words = listOf(
            TimedWord("Hello", 0, 300), TimedWord("there.", 300, 600),
            TimedWord("This", 700, 900), TimedWord("is", 900, 1000), TimedWord("a", 1000, 1100), TimedWord("caption", 1100, 1500), TimedWord("test", 1500, 1800),
            TimedWord("later", 4000, 4400),
        )
        val lines = CaptionPlanner.lines(words).map { l -> l.joinToString(" ") { it.text } }
        assertEquals(listOf("Hello there.", "This is a caption", "test", "later"), lines)
    }

    @Test
    fun overlaysHaveRelativeWordsAndNeverOverlap() {
        val words = CaptionPlanner.evenWords("one two three four five six seven", 1_000, 4_500)
        val overlays = CaptionPlanner.toOverlays(words, CaptionStyle.POP, 10_000, ::id)
        assertEquals(2, overlays.size)
        overlays.zipWithNext().forEach { (a, b) -> assertTrue(a.endMs <= b.startMs) }
        overlays.forEach { o ->
            assertTrue(o.isCaption)
            assertEquals(CaptionStyle.POP, o.captionStyle)
            assertTrue(o.words.first().startMs >= 0)
            assertTrue(o.words.last().startMs < o.endMs - o.startMs)
            assertEquals(o.text, o.words.joinToString(" ") { it.text })
        }
    }

    @Test
    fun activeWordFollowsTime() {
        val w = listOf(CaptionWord("a", 0, 100), CaptionWord("b", 100, 200), CaptionWord("c", 250, 400))
        assertEquals(-1, CaptionPlanner.activeIndex(listOf(CaptionWord("x", 50, 60)), 10))
        assertEquals(0, CaptionPlanner.activeIndex(w, 50))
        assertEquals(1, CaptionPlanner.activeIndex(w, 249))
        assertEquals(2, CaptionPlanner.activeIndex(w, 1000))
    }

    @Test
    fun editingKeepsWordsInSync() {
        val o = CaptionPlanner.toOverlays(CaptionPlanner.evenWords("good morning world", 0, 1200), CaptionStyle.KARAOKE, 5_000, ::id).single()
        val edited = CaptionPlanner.retext(o, "hello lovely world")
        assertEquals(listOf("hello", "lovely", "world"), edited.words.map { it.text })
        val stretched = CaptionPlanner.retime(o, 1_000, 1_000 + (o.endMs - o.startMs) * 2)
        assertEquals(o.words.last().startMs * 2, stretched.words.last().startMs)
        val (left, right) = CaptionPlanner.split(o, o.startMs + o.words[1].startMs, "r")
        assertEquals("good", left.text)
        assertEquals("morning world", right.text)
        assertEquals(0L, right.words.first().startMs)
    }
}
