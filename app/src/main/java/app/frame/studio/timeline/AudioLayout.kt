package app.frame.studio.timeline

import app.frame.studio.domain.AudioClip

/** Placement of audio clips on the timeline. */
object AudioLayout {

    /** Resolves legacy clips (startMs < 0) to "right after the previous clip". */
    fun normalize(clips: List<AudioClip>): List<AudioClip> {
        var cursor = 0L
        return clips.map { clip ->
            val placed = if (clip.startMs < 0) clip.copy(startMs = cursor) else clip
            cursor = placed.endMs()
            placed
        }
    }

    /** Assigns each clip a lane so clips in the same lane never overlap. */
    fun lanes(clips: List<AudioClip>): Map<String, Int> {
        val laneEnds = mutableListOf<Long>()
        val result = mutableMapOf<String, Int>()
        normalize(clips).sortedBy { it.startMs }.forEach { clip ->
            var lane = laneEnds.indexOfFirst { it <= clip.startMs }
            if (lane < 0) {
                lane = laneEnds.size
                laneEnds += 0L
            }
            laneEnds[lane] = clip.endMs()
            result[clip.id] = lane
        }
        return result
    }

    /** Clips grouped by lane, each lane sorted by start time. */
    fun byLane(clips: List<AudioClip>): List<List<AudioClip>> {
        val normalized = normalize(clips)
        val lanes = lanes(normalized)
        val count = (lanes.values.maxOrNull() ?: -1) + 1
        return List(count) { lane -> normalized.filter { lanes[it.id] == lane }.sortedBy { it.startMs } }
    }

    /** Drops or shortens clips so nothing plays past [durationMs]. */
    fun clampTo(clips: List<AudioClip>, durationMs: Long): List<AudioClip> =
        normalize(clips).mapNotNull { clip ->
            when {
                clip.startMs >= durationMs -> null
                clip.endMs() > durationMs -> clip.copy(trimEndMs = clip.trimStartMs + (durationMs - clip.startMs))
                else -> clip
            }
        }.filter { it.outputDurationMs() > 0 }
}
