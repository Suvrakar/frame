package app.frame.studio.timeline

import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.VideoClip
import kotlin.math.abs

/** Re-times video clips so cuts land on music beats (pure, unit-tested). */
object BeatSync {

    const val MIN_CLIP_MS = 300L

    /** Beats of [clip] (stored in source time) mapped onto the output timeline. */
    fun timelineBeats(clip: AudioClip): List<Long> {
        val start = clip.startMs.coerceAtLeast(0)
        return clip.beatsMs
            .filter { it >= clip.trimStartMs && it < clip.trimEndMs }
            .map { start + (it - clip.trimStartMs) }
    }

    /** Every [every]-th beat (after the first), as candidate cut points. */
    fun cutPoints(beats: List<Long>, every: Int): List<Long> =
        beats.sorted().filter { it > 0 }.filterIndexed { i, _ -> i % every.coerceAtLeast(1) == every.coerceAtLeast(1) - 1 }

    /**
     * Moves each clip's end onto the cut point closest to its current length, never asking for
     * more footage than the clip has and never making it shorter than [MIN_CLIP_MS]. Clips that
     * can't reach any cut point keep their length.
     */
    fun retime(clips: List<VideoClip>, cuts: List<Long>): List<VideoClip> {
        if (cuts.isEmpty()) return clips
        var cursor = 0L
        return clips.map { clip ->
            val current = clip.outputDurationMs()
            val rate = if (clip.isImage) 1f else clip.speed.coerceIn(VideoClip.MIN_SPEED, VideoClip.MAX_SPEED)
            val maxOut = if (clip.isImage) VideoClip.MAX_IMAGE_MS else ((clip.sourceDurationMs - clip.trimStartMs) / rate).toLong()
            val best = cuts
                .filter { it - cursor >= MIN_CLIP_MS && it - cursor <= maxOut }
                .minByOrNull { abs((it - cursor) - current) }
            val retimed = if (best == null) {
                clip
            } else {
                val length = best - cursor
                if (clip.isImage) {
                    clip.copy(trimStartMs = 0, trimEndMs = length)
                } else {
                    clip.copy(trimEndMs = (clip.trimStartMs + (length * rate).toLong()).coerceAtMost(clip.sourceDurationMs))
                }.let { c ->
                    val half = c.outputDurationMs() / 2
                    c.copy(fadeInMs = c.fadeInMs.coerceAtMost(half), fadeOutMs = c.fadeOutMs.coerceAtMost(half))
                }
            }
            cursor += retimed.outputDurationMs()
            retimed
        }
    }
}
