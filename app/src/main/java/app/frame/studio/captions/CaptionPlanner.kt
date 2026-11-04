package app.frame.studio.captions

import app.frame.studio.domain.CaptionStyle
import app.frame.studio.domain.CaptionWord
import app.frame.studio.domain.TextOverlay
import kotlin.math.sqrt

/** A recognised word on the project timeline (absolute ms). */
data class TimedWord(val text: String, val startMs: Long, val endMs: Long)

/** Pure caption logic: speech chunking, word timing, line breaking and editing. */
object CaptionPlanner {

    const val MAX_WORDS_PER_LINE = 4
    const val MAX_CHARS_PER_LINE = 24
    const val MAX_GAP_MS = 700L
    const val LEAD_IN_MS = 80L
    const val HOLD_MS = 250L

    /**
     * Splits audio into speech chunks using frame energy (a simple voice-activity detector) so
     * each chunk can be recognised on its own. Returns (startMs, endMs) relative to the samples.
     */
    fun speechChunks(
        samples: FloatArray,
        sampleRate: Int,
        frameMs: Int = 30,
        minSilenceMs: Long = 400,
        maxChunkMs: Long = 12_000,
        padMs: Long = 150,
    ): List<Pair<Long, Long>> {
        val frame = (sampleRate * frameMs / 1000).coerceAtLeast(1)
        val frames = samples.size / frame
        if (frames == 0) return emptyList()
        val energy = FloatArray(frames) { f ->
            var s = 0f
            for (i in f * frame until (f + 1) * frame) s += samples[i] * samples[i]
            sqrt(s / frame)
        }
        val sorted = energy.sorted()
        val noise = sorted[(frames * 0.1f).toInt().coerceAtMost(frames - 1)]
        val loud = sorted[(frames * 0.95f).toInt().coerceAtMost(frames - 1)]
        if (loud < 0.01f) return emptyList() // silence
        // With no real pauses (continuous talking) the "noise floor" is speech itself.
        val threshold = if (loud - noise < loud * 0.5f) maxOf(loud * 0.25f, 0.008f)
        else maxOf(noise * 2.5f, noise + (loud - noise) * 0.15f, 0.008f)

        val totalMs = samples.size * 1000L / sampleRate
        val chunks = mutableListOf<Pair<Long, Long>>()
        var start = -1L
        var lastVoice = -1L
        for (f in 0 until frames) {
            val t = f.toLong() * frameMs
            if (energy[f] >= threshold) {
                if (start < 0) start = t
                lastVoice = t + frameMs
            }
            val silentFor = if (lastVoice < 0) 0 else t - lastVoice
            val tooLong = start >= 0 && t - start >= maxChunkMs
            if (start >= 0 && (silentFor >= minSilenceMs || tooLong)) {
                chunks += (start - padMs).coerceAtLeast(0) to (lastVoice + padMs).coerceAtMost(totalMs)
                start = if (tooLong && silentFor < minSilenceMs) t else -1
                if (start < 0) lastVoice = -1
            }
        }
        if (start >= 0 && lastVoice > start) chunks += (start - padMs).coerceAtLeast(0) to (lastVoice + padMs).coerceAtMost(totalMs)
        return chunks.filter { it.second - it.first >= 250 }
    }

    fun tokens(text: String): List<String> = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    /** Spreads words over [startMs, endMs], longer words taking longer (no timing available). */
    fun evenWords(text: String, startMs: Long, endMs: Long): List<TimedWord> {
        val words = tokens(text)
        if (words.isEmpty() || endMs <= startMs) return emptyList()
        val weights = words.map { it.length + 2 }
        val total = weights.sum().toDouble()
        val span = endMs - startMs
        var acc = 0
        return words.mapIndexed { i, w ->
            val s = startMs + (span * (acc / total)).toLong()
            acc += weights[i]
            val e = startMs + (span * (acc / total)).toLong()
            TimedWord(w, s, e)
        }
    }

    /**
     * Places recognised words in [startMs, endMs]: words with a reported time keep it (as an
     * offset from [startMs]); the rest are spread between their neighbours by length.
     */
    fun placeWords(words: List<Pair<String, Long?>>, startMs: Long, endMs: Long): List<TimedWord> {
        if (words.isEmpty() || endMs <= startMs) return emptyList()
        val starts = LongArray(words.size)
        val known = BooleanArray(words.size)
        words.forEachIndexed { i, (_, offset) ->
            if (offset != null) {
                starts[i] = (startMs + offset).coerceIn(startMs, endMs)
                known[i] = true
            }
        }
        var i = 0
        while (i < words.size) {
            if (known[i]) { i++; continue }
            var j = i
            while (j < words.size && !known[j]) j++
            val to = if (j < words.size) starts[j] else endMs
            val run = words.subList(i, j).map { it.first }
            if (i == 0) {
                evenWords(run.joinToString(" "), startMs, maxOf(to, startMs + run.size))
                    .forEachIndexed { k, w -> starts[k] = w.startMs }
            } else {
                // The previous (timed) word shares the gap with the untimed words after it.
                val from = starts[i - 1]
                evenWords((listOf(words[i - 1].first) + run).joinToString(" "), from, maxOf(to, from + run.size + 1))
                    .drop(1).forEachIndexed { k, w -> starts[i + k] = w.startMs }
            }
            i = j
        }
        for (k in 1 until starts.size) if (starts[k] < starts[k - 1]) starts[k] = starts[k - 1]
        return words.mapIndexed { k, (text, _) ->
            val end = if (k + 1 < words.size) maxOf(starts[k + 1], starts[k] + 1) else endMs
            TimedWord(text, starts[k], minOf(end, starts[k] + 1_500).coerceAtLeast(starts[k] + 1))
        }
    }

    /** Breaks words into short caption lines at pauses, punctuation and length limits. */
    fun lines(words: List<TimedWord>): List<List<TimedWord>> {
        val result = mutableListOf<List<TimedWord>>()
        var line = mutableListOf<TimedWord>()
        var chars = 0
        for (w in words.sortedBy { it.startMs }) {
            val gap = line.lastOrNull()?.let { w.startMs - it.endMs } ?: 0
            val wouldBe = chars + (if (line.isEmpty()) 0 else 1) + w.text.length
            if (line.isNotEmpty() && (line.size >= MAX_WORDS_PER_LINE || wouldBe > MAX_CHARS_PER_LINE || gap > MAX_GAP_MS)) {
                result += line
                line = mutableListOf()
                chars = 0
            }
            line += w
            chars += (if (line.size == 1) 0 else 1) + w.text.length
            if (w.text.last() in ".!?") {
                result += line
                line = mutableListOf()
                chars = 0
            }
        }
        if (line.isNotEmpty()) result += line
        return result
    }

    /** One animated caption overlay per line; lines never overlap on screen. */
    fun toOverlays(
        words: List<TimedWord>,
        style: CaptionStyle,
        totalMs: Long,
        newId: () -> String,
        y: Float = 0.78f,
    ): List<TextOverlay> {
        val lines = lines(words)
        return lines.mapIndexed { i, line ->
            val start = (line.first().startMs - LEAD_IN_MS).coerceAtLeast(0)
            val nextStart = lines.getOrNull(i + 1)?.let { (it.first().startMs - LEAD_IN_MS).coerceAtLeast(0) } ?: totalMs
            val end = minOf(line.last().endMs + HOLD_MS, nextStart, totalMs).coerceAtLeast(start + 100)
            TextOverlay(
                id = newId(),
                text = line.joinToString(" ") { it.text },
                startMs = start,
                endMs = end,
                y = y,
                scale = 1.15f,
                bold = true,
                background = style == CaptionStyle.PLAIN,
                animate = false,
                words = line.map { CaptionWord(it.text, it.startMs - start, it.endMs - start) },
                captionStyle = style,
            )
        }
    }

    /** Index of the word being spoken at [relativeMs], or -1 before the first word. */
    fun activeIndex(words: List<CaptionWord>, relativeMs: Long): Int {
        var index = -1
        for (i in words.indices) if (words[i].startMs <= relativeMs) index = i else break
        return index
    }

    /** After the caption's text was edited: re-spread the new words over its time. */
    fun retext(overlay: TextOverlay, text: String): TextOverlay {
        if (!overlay.isCaption) return overlay.copy(text = text)
        val span = overlay.endMs - overlay.startMs
        val first = overlay.words.first().startMs.coerceIn(0, span)
        val last = overlay.words.last().endMs.coerceIn(first, span)
        val words = evenWords(text, first, if (last > first) last else span)
            .map { CaptionWord(it.text, it.startMs, it.endMs) }
        return overlay.copy(text = text, words = words)
    }

    /** After the caption was trimmed or stretched: scale word times to the new length. */
    fun retime(overlay: TextOverlay, startMs: Long, endMs: Long): TextOverlay {
        if (!overlay.isCaption) return overlay.copy(startMs = startMs, endMs = endMs)
        val oldSpan = (overlay.endMs - overlay.startMs).coerceAtLeast(1)
        val newSpan = (endMs - startMs).coerceAtLeast(1)
        val f = newSpan.toDouble() / oldSpan
        return overlay.copy(
            startMs = startMs,
            endMs = endMs,
            words = overlay.words.map { CaptionWord(it.text, (it.startMs * f).toLong(), (it.endMs * f).toLong()) },
        )
    }

    /** Splits a caption at [atMs] (absolute); words go to the side where they start. */
    fun split(overlay: TextOverlay, atMs: Long, rightId: String): Pair<TextOverlay, TextOverlay> {
        val left = overlay.copy(endMs = atMs)
        val right = overlay.copy(id = rightId, startMs = atMs)
        if (!overlay.isCaption) return left to right
        val cut = atMs - overlay.startMs
        val lw = overlay.words.filter { it.startMs < cut }
        val rw = overlay.words.filter { it.startMs >= cut }.map { CaptionWord(it.text, it.startMs - cut, it.endMs - cut) }
        return left.copy(words = lw, text = lw.joinToString(" ") { it.text }.ifBlank { overlay.text }) to
            right.copy(words = rw, text = rw.joinToString(" ") { it.text }.ifBlank { overlay.text })
    }
}
