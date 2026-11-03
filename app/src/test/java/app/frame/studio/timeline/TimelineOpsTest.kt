package app.frame.studio.timeline

import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineOpsTest {

    private fun video(
        id: String = "v1",
        sourceDurationMs: Long = 10_000,
        trimStartMs: Long = 0,
        trimEndMs: Long = sourceDurationMs,
        speed: Float = 1f,
    ) = VideoClip(
        id = id,
        sourceUri = "file://clip.mp4",
        sourceDurationMs = sourceDurationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        speed = speed,
    )

    @Test
    fun trim_shortensClipWithinSource() {
        val trimmed = TimelineOps.trim(video(), startMs = 1_000, endMs = 4_000)
        assertEquals(1_000, trimmed.trimStartMs)
        assertEquals(4_000, trimmed.trimEndMs)
        assertEquals(3_000, trimmed.outputDurationMs())
    }

    @Test
    fun split_dividesSelectedClipAtPlayhead() {
        val clips = listOf(video("a"))
        val split = TimelineOps.splitVideo(clips, clipId = "a", atOutputMs = 4_000)
        assertEquals(2, split.size)
        assertEquals(0, split[0].trimStartMs)
        assertEquals(4_000, split[0].trimEndMs)
        assertEquals(4_000, split[1].trimStartMs)
        assertEquals(10_000, split[1].trimEndMs)
    }

    @Test
    fun delete_removesClipById() {
        val remaining = TimelineOps.delete(listOf(video("a"), video("b")), "a")
        assertEquals(listOf("b"), remaining.map { it.id })
    }

    @Test
    fun reorder_movesClipToNewIndex() {
        val clips = listOf(video("a"), video("b"), video("c"))
        val reordered = TimelineOps.reorder(clips, fromIndex = 0, toIndex = 2)
        assertEquals(listOf("b", "c", "a"), reordered.map { it.id })
    }

    @Test
    fun speed_changesOutputDuration() {
        assertEquals(5_000, video(speed = 2f).outputDurationMs())
        assertEquals(20_000, video(speed = 0.5f).outputDurationMs())
    }

    @Test
    fun snap_snapsNearNeighborEdge() {
        val snapped = TimelineOps.snap(980, edgesMs = listOf(0L, 1_000L, 5_000L), thresholdMs = 50)
        assertEquals(1_000, snapped)
    }

    @Test
    fun snap_leavesPositionWhenFarFromEdges() {
        assertEquals(2_000, TimelineOps.snap(2_000, edgesMs = listOf(0L, 1_000L), thresholdMs = 50))
    }

    @Test
    fun splitAudio_dividesClip() {
        val audio = AudioClip(
            id = "m1",
            sourceUri = "file://music.m4a",
            sourceDurationMs = 8_000,
            trimStartMs = 0,
            trimEndMs = 8_000,
        )
        val split = TimelineOps.splitAudio(listOf(audio), "m1", atOutputMs = 3_000)
        assertEquals(2, split.size)
        assertEquals(3_000, split[0].trimEndMs)
        assertEquals(3_000, split[1].trimStartMs)
    }

    @Test
    fun projectDuration_subtractsCrossfades() {
        val clips = listOf(video("a"), video("b"))
        val duration = TimelineOps.videoTimelineDurationMs(clips, crossfadeMs = 500)
        assertEquals(19_500, duration)
        assertTrue(duration < clips.sumOf { it.outputDurationMs() })
    }
}
