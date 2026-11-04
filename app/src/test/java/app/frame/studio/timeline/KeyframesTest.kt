package app.frame.studio.timeline

import app.frame.studio.domain.Easing
import app.frame.studio.domain.Keyframe
import app.frame.studio.domain.OverlayClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframesTest {
    private val base = Transform(0.5f, 0.5f, 1f, 0f, 1f)

    @Test
    fun noKeyframesMeansStatic() {
        assertEquals(base, Keyframes.at(base, emptyList(), 1234))
    }

    @Test
    fun interpolatesBetweenKeyframesWithEasing() {
        val k = listOf(
            Keyframe(0, 0f, 0f, 1f, 0f, 1f, Easing.LINEAR),
            Keyframe(1000, 1f, 0.5f, 2f, 90f, 0f, Easing.LINEAR),
        )
        val mid = Keyframes.at(base, k, 500)
        assertEquals(0.5f, mid.x, 1e-4f)
        assertEquals(0.25f, mid.y, 1e-4f)
        assertEquals(1.5f, mid.scale, 1e-4f)
        assertEquals(45f, mid.rotation, 1e-3f)
        assertEquals(0.5f, mid.opacity, 1e-4f)
        // Holds the first/last value outside the keyframed range.
        assertEquals(0f, Keyframes.at(base, k, -50).x, 0f)
        assertEquals(1f, Keyframes.at(base, k, 5000).x, 0f)
        // Smooth easing is slower at the ends than linear.
        val smooth = k.map { it.copy(easing = Easing.SMOOTH) }
        assertTrue(Keyframes.at(base, smooth, 100).x < 0.1f)
        assertEquals(0.5f, Keyframes.at(base, smooth, 500).x, 1e-4f)
        // Hold jumps at the next keyframe.
        val hold = k.map { it.copy(easing = Easing.HOLD) }
        assertEquals(0f, Keyframes.at(base, hold, 999).x, 0f)
    }

    @Test
    fun editsMoveStaticObjectsAndKeyAnimatedOnes() {
        val moved = Transform(0.2f, 0.3f, 1.2f, 10f, 1f)
        val (b1, k1) = Keyframes.edit(base, emptyList(), 800, moved)
        assertEquals(moved, b1)
        assertTrue(k1.isEmpty())

        val keys = listOf(Keyframe(0, 0.5f, 0.5f))
        val (b2, k2) = Keyframes.edit(base, keys, 800, moved)
        assertEquals(base, b2)
        assertEquals(listOf(0L, 800L), k2.map { it.atMs })
        // An edit near an existing keyframe updates it instead of adding one.
        val (_, k3) = Keyframes.edit(base, k2, 830, moved.copy(x = 0.9f))
        assertEquals(2, k3.size)
        assertEquals(0.9f, k3.last().x, 0f)
        assertEquals(800L, k3.last().atMs)
    }

    @Test
    fun toggleAndRetime() {
        val k = Keyframes.upsert(emptyList(), 500, base)
        assertEquals(1, k.size)
        assertTrue(Keyframes.remove(k, 520).isEmpty())
        assertEquals(k, Keyframes.remove(k, 900))
        val many = listOf(Keyframe(0, 0f, 0f), Keyframe(1000, 0f, 0f), Keyframe(3000, 0f, 0f))
        assertEquals(listOf(0L, 2000L), Keyframes.retime(many, 1000, 2500).map { it.atMs })
    }

    private fun clip(id: String, start: Long, len: Long) =
        OverlayClip(id = id, sourceUri = id, sourceDurationMs = 10_000, trimEndMs = len, startMs = start, width = 100, height = 100)

    @Test
    fun overlayLanesStackAndCap() {
        val a = clip("a", 0, 3000)
        val b = clip("b", 1000, 3000)
        val c = clip("c", 3000, 1000)
        val lanes = OverlayLayout.lanes(listOf(a, b, c), maxLanes = 2)
        assertEquals(listOf(listOf("a", "c"), listOf("b")), lanes.map { l -> l.map { it.id } })
        // A third simultaneous overlay doesn't fit in two lanes.
        assertFalse(OverlayLayout.fits(listOf(a, b), 1500, 500, maxLanes = 2))
        assertTrue(OverlayLayout.fits(listOf(a, b), 4500, 500, maxLanes = 2))
        assertTrue(OverlayLayout.fits(listOf(a), 500, 500, maxLanes = 2))
        assertFalse(OverlayLayout.fits(listOf(a), 500, 500, maxLanes = 1))
        // Moving a clip doesn't collide with itself.
        assertTrue(OverlayLayout.fits(listOf(a, b), 1200, 3000, maxLanes = 2, ignoreId = "b"))
    }

    @Test
    fun overlaysAreClampedToTheVideo() {
        val clamped = OverlayLayout.clampTo(listOf(clip("a", 8000, 5000), clip("b", 12_000, 1000)), 10_000)
        assertEquals(1, clamped.size)
        assertEquals(10_000L, clamped.single().endMs())
    }
}
