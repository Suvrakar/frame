package app.frame.studio.timeline

import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.VideoClip

/** Maps between the project's output timeline and positions inside individual clips. */
object TimelineMath {

    data class Position(val index: Int, val offsetInClipMs: Long)

    /** Output-timeline start of every clip (the export plays clips back to back). */
    fun videoStarts(clips: List<VideoClip>): List<Long> {
        var acc = 0L
        return clips.map { clip -> acc.also { acc += clip.outputDurationMs() } }
    }

    fun totalVideoMs(clips: List<VideoClip>): Long = clips.sumOf { it.outputDurationMs() }

    /** Clip edges on the output timeline, used for snapping the playhead. */
    fun videoEdges(clips: List<VideoClip>): List<Long> {
        val starts = videoStarts(clips)
        return if (clips.isEmpty()) emptyList() else starts + totalVideoMs(clips)
    }

    /** Locates [globalMs] inside the clip list. Offset is in output time (already speed-adjusted). */
    fun locateVideo(clips: List<VideoClip>, globalMs: Long): Position? =
        locate(clips.map { it.outputDurationMs() }, globalMs)

    fun locateAudio(clips: List<AudioClip>, globalMs: Long): Position? =
        locate(clips.map { it.outputDurationMs() }, globalMs)

    private fun locate(durations: List<Long>, globalMs: Long): Position? {
        if (durations.isEmpty()) return null
        var acc = 0L
        durations.forEachIndexed { index, duration ->
            if (globalMs < acc + duration) return Position(index, (globalMs - acc).coerceAtLeast(0))
            acc += duration
        }
        return null
    }

    /** Converts output offset inside a clip to the media position the player must seek to. */
    fun outputOffsetToMediaMs(clip: VideoClip, offsetMs: Long): Long =
        if (clip.isImage) offsetMs else (offsetMs * clip.speed.coerceIn(VideoClip.MIN_SPEED, VideoClip.MAX_SPEED)).toLong()

    /** Converts a player position (relative to clip start, in media time) to output time. */
    fun mediaToOutputMs(clip: VideoClip, mediaPositionMs: Long): Long =
        if (clip.isImage) mediaPositionMs else (mediaPositionMs / clip.speed.coerceIn(VideoClip.MIN_SPEED, VideoClip.MAX_SPEED)).toLong()

    fun globalFromVideo(clips: List<VideoClip>, index: Int, mediaPositionMs: Long): Long {
        if (index !in clips.indices) return 0
        return videoStarts(clips)[index] + mediaToOutputMs(clips[index], mediaPositionMs)
            .coerceAtMost(clips[index].outputDurationMs())
    }
}
