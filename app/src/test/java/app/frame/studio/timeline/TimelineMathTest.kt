package app.frame.studio.timeline

import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineMathTest {

    private fun video(id: String, durationMs: Long, speed: Float = 1f) = VideoClip(
        id = id,
        sourceUri = "file://$id.mp4",
        sourceDurationMs = durationMs,
        trimEndMs = durationMs,
        speed = speed,
    )

    private val clips = listOf(video("a", 4_000), video("b", 6_000, speed = 2f), video("c", 2_000))

    @Test
    fun startsAndEdges() {
        assertEquals(listOf(0L, 4_000L, 7_000L), TimelineMath.videoStarts(clips))
        assertEquals(listOf(0L, 4_000L, 7_000L, 9_000L), TimelineMath.videoEdges(clips))
    }

    @Test
    fun locatesGlobalPosition() {
        assertEquals(TimelineMath.Position(0, 1_000), TimelineMath.locateVideo(clips, 1_000))
        assertEquals(TimelineMath.Position(1, 500), TimelineMath.locateVideo(clips, 4_500))
        assertEquals(TimelineMath.Position(2, 0), TimelineMath.locateVideo(clips, 7_000))
        assertNull(TimelineMath.locateVideo(clips, 9_000))
    }

    @Test
    fun convertsBetweenMediaAndOutputTime() {
        assertEquals(1_000, TimelineMath.outputOffsetToMediaMs(clips[1], 500))
        assertEquals(4_500, TimelineMath.globalFromVideo(clips, 1, mediaPositionMs = 1_000))
    }

    @Test
    fun splitIgnoresTooShortClips() {
        val tiny = listOf(video("t", 150))
        assertEquals(tiny, TimelineOps.splitVideo(tiny, "t", 75))
    }
}
