package app.frame.studio.audio

import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.AudioKind

/**
 * Auto-ducking (pure; JVM-tested): while a voiceover plays, music dips by [DEPTH] with short
 * ramps so it never pumps audibly. Times are on the output timeline.
 */
object Ducking {
    /** About -12 dB. */
    const val DEPTH = 0.25f
    const val RAMP_MS = 250L

    /** Merged spans where any voice clip plays. */
    fun voiceSpans(audio: List<AudioClip>): List<LongRange> {
        val spans = audio.filter { it.kind == AudioKind.VOICE && it.volume > 0f && it.outputDurationMs() > 0 }
            .map { it.startMs.coerceAtLeast(0) until it.endMs() }
            .sortedBy { it.first }
        val merged = ArrayList<LongRange>()
        for (s in spans) {
            val last = merged.lastOrNull()
            // Gaps shorter than two ramps are bridged so music doesn't bob between sentences.
            if (last != null && s.first <= last.last + 1 + 2 * RAMP_MS) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, s.last)
            } else {
                merged += s
            }
        }
        return merged
    }

    /** Gain for a music sample at [tMs]: 1 away from voice, [DEPTH] under it, ramped at edges. */
    fun gainAt(spans: List<LongRange>, tMs: Long): Float {
        var gain = 1f
        for (s in spans) {
            val g = when {
                tMs in s -> DEPTH
                tMs < s.first && s.first - tMs < RAMP_MS -> 1f - (1f - DEPTH) * (1f - (s.first - tMs).toFloat() / RAMP_MS)
                tMs > s.last && tMs - s.last < RAMP_MS -> 1f - (1f - DEPTH) * (1f - (tMs - s.last).toFloat() / RAMP_MS)
                else -> 1f
            }
            gain = minOf(gain, g)
        }
        return gain
    }
}
