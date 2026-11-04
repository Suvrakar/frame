package app.frame.studio.timeline

import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.VideoClip
import kotlin.math.abs

object TimelineOps {

    /** Shortest piece a split may produce. */
    const val MIN_SPLIT_MS = 100L

    fun trim(clip: VideoClip, startMs: Long, endMs: Long): VideoClip {
        if (clip.sourceDurationMs < 2) return clip
        val start = startMs.coerceIn(0, clip.sourceDurationMs - 1)
        val end = endMs.coerceIn(start + 1, clip.sourceDurationMs)
        return clip.copy(trimStartMs = start, trimEndMs = end)
    }

    fun trimAudio(clip: AudioClip, startMs: Long, endMs: Long): AudioClip {
        if (clip.sourceDurationMs < 2) return clip
        val start = startMs.coerceIn(0, clip.sourceDurationMs - 1)
        val end = endMs.coerceIn(start + 1, clip.sourceDurationMs)
        return clip.copy(trimStartMs = start, trimEndMs = end)
    }

    fun splitVideo(
        clips: List<VideoClip>,
        clipId: String,
        atOutputMs: Long,
    ): List<VideoClip> {
        val index = clips.indexOfFirst { it.id == clipId }
        if (index < 0) return clips
        val clip = clips[index]
        if (clip.outputDurationMs() < MIN_SPLIT_MS * 2) return clips
        val relative = atOutputMs.coerceIn(1, clip.outputDurationMs() - 1)
        val rate = if (clip.isImage) 1f else clip.speed.coerceIn(VideoClip.MIN_SPEED, VideoClip.MAX_SPEED)
        val atSource = clip.trimStartMs + (relative * rate).toLong()
        if (atSource <= clip.trimStartMs || atSource >= clip.trimEndMs) return clips
        val left = clip.copy(id = "${clip.id}_a", trimEndMs = atSource)
        val right = clip.copy(id = "${clip.id}_b", trimStartMs = atSource)
        return clips.toMutableList().apply {
            removeAt(index)
            addAll(index, listOf(left, right))
        }
    }

    fun splitAudio(
        clips: List<AudioClip>,
        clipId: String,
        atOutputMs: Long,
    ): List<AudioClip> {
        val index = clips.indexOfFirst { it.id == clipId }
        if (index < 0) return clips
        val clip = clips[index]
        if (clip.outputDurationMs() < MIN_SPLIT_MS * 2) return clips
        val relative = atOutputMs.coerceIn(1, clip.outputDurationMs() - 1)
        val atSource = clip.trimStartMs + relative
        if (atSource <= clip.trimStartMs || atSource >= clip.trimEndMs) return clips
        val left = clip.copy(id = "${clip.id}_a", trimEndMs = atSource)
        val right = clip.copy(id = "${clip.id}_b", trimStartMs = atSource)
        return clips.toMutableList().apply {
            removeAt(index)
            addAll(index, listOf(left, right))
        }
    }

    fun <T> delete(clips: List<T>, id: String, idOf: (T) -> String): List<T> =
        clips.filter { idOf(it) != id }

    fun delete(clips: List<VideoClip>, id: String): List<VideoClip> =
        clips.filter { it.id != id }

    fun deleteAudio(clips: List<AudioClip>, id: String): List<AudioClip> =
        clips.filter { it.id != id }

    fun <T> reorder(clips: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex !in clips.indices || clips.isEmpty()) return clips
        val target = toIndex.coerceIn(0, clips.lastIndex)
        if (fromIndex == target) return clips
        val mutable = clips.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(target, item)
        return mutable
    }

    fun snap(positionMs: Long, edgesMs: List<Long>, thresholdMs: Long): Long {
        val nearest = edgesMs.minByOrNull { abs(it - positionMs) } ?: return positionMs
        return if (abs(nearest - positionMs) <= thresholdMs) nearest else positionMs
    }

    fun videoTimelineDurationMs(clips: List<VideoClip>, crossfadeMs: Long): Long {
        if (clips.isEmpty()) return 0
        val raw = clips.sumOf { it.outputDurationMs() }
        val overlaps = (clips.size - 1).coerceAtLeast(0) * crossfadeMs.coerceAtLeast(0)
        return (raw - overlaps).coerceAtLeast(0)
    }

    fun audioTimelineDurationMs(clips: List<AudioClip>): Long =
        clips.sumOf { it.outputDurationMs() }

    fun rotate(clip: VideoClip): VideoClip =
        clip.copy(rotationDegrees = (clip.rotationDegrees + 90) % 360)
}
