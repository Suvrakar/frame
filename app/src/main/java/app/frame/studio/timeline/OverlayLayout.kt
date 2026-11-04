package app.frame.studio.timeline

import app.frame.studio.domain.OverlayClip

/** Places overlay clips on stacked lanes so clips in one lane never overlap (pure). */
object OverlayLayout {

    /** Clips trimmed to the video's length; ones starting after the end are dropped. */
    fun clampTo(overlays: List<OverlayClip>, totalMs: Long): List<OverlayClip> = overlays.mapNotNull { o ->
        val start = o.startMs.coerceAtLeast(0)
        if (start >= totalMs || o.durationMs() <= 0) return@mapNotNull null
        val maxLen = totalMs - start
        if (o.durationMs() <= maxLen) o.copy(startMs = start) else o.copy(startMs = start, trimEndMs = o.trimStartMs + maxLen)
    }

    /** Lane 0 is drawn on top. Clips that don't fit in [maxLanes] are left out. */
    fun lanes(overlays: List<OverlayClip>, maxLanes: Int): List<List<OverlayClip>> {
        val lanes = mutableListOf<MutableList<OverlayClip>>()
        overlays.sortedBy { it.startMs }.forEach { o ->
            val lane = lanes.firstOrNull { it.last().endMs() <= o.startMs }
            when {
                lane != null -> lane += o
                lanes.size < maxLanes -> lanes += mutableListOf(o)
            }
        }
        return lanes
    }

    /** First lane that is free over [startMs, startMs + durationMs), or null when all are busy. */
    fun fits(overlays: List<OverlayClip>, startMs: Long, durationMs: Long, maxLanes: Int, ignoreId: String? = null): Boolean {
        val others = overlays.filter { it.id != ignoreId }
        val probe = OverlayClip(id = "_probe", sourceUri = "", sourceDurationMs = durationMs, trimEndMs = durationMs, startMs = startMs, width = 1, height = 1)
        return lanes(others + probe, maxLanes).any { lane -> lane.any { it.id == "_probe" } }
    }
}
