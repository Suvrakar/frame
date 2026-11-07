package app.frame.studio.export

import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionPlanTest {
    private fun clip(id: String, ms: Long, t: TransitionType = TransitionType.NONE, tMs: Long = 600) =
        VideoClip(id = id, sourceUri = "content://$id", sourceDurationMs = ms, trimEndMs = ms, transition = t, transitionMs = tMs)

    @Test fun oneWindowPerShaderCut() {
        val w = TransitionPlan.windows(listOf(clip("a", 2_000, TransitionType.ZOOM_IN), clip("b", 3_000, TransitionType.SPIN), clip("c", 1_000)))
        assertEquals(2, w.size)
        assertEquals(2_000L, w[0].cutMs)
        assertEquals(300L, w[0].halfMs)
        assertEquals(TransitionType.ZOOM_IN, w[0].type)
        assertEquals(5_000L, w[1].cutMs)
        assertEquals(TransitionType.SPIN, w[1].type)
    }

    @Test fun lastClipsTransitionIsIgnored() {
        assertTrue(TransitionPlan.windows(listOf(clip("a", 2_000), clip("b", 2_000, TransitionType.ZOOM_IN))).isEmpty())
    }

    @Test fun fadesAndNoneUseTheColourDipNotTheShader() {
        val w = TransitionPlan.windows(listOf(clip("a", 2_000, TransitionType.FADE_BLACK), clip("b", 2_000, TransitionType.FADE_WHITE), clip("c", 2_000)))
        assertTrue(w.isEmpty())
        assertFalse(TransitionPlan.usesShader(TransitionType.NONE))
        assertTrue(TransitionPlan.usesShader(TransitionType.GLITCH))
    }

    @Test fun halfIsClampedToHalfOfTheShorterNeighbour() {
        val w = TransitionPlan.windows(listOf(clip("a", 400, TransitionType.WHIP_LEFT, tMs = 2_000), clip("b", 10_000)))
        assertEquals(200L, w.single().halfMs)
    }

    @Test fun progressRunsFromMinusOneThroughZeroAtTheCutToOne() {
        val w = TransitionPlan.windows(listOf(clip("a", 2_000, TransitionType.ZOOM_IN, tMs = 1_000), clip("b", 2_000)))
        assertNull(TransitionPlan.at(w, 1_400))
        assertEquals(-1f, TransitionPlan.at(w, 1_500)!!.progress, 1e-4f)
        assertEquals(-0.5f, TransitionPlan.at(w, 1_750)!!.progress, 1e-4f)
        assertEquals(0f, TransitionPlan.at(w, 2_000)!!.progress, 1e-4f)
        assertEquals(0.5f, TransitionPlan.at(w, 2_250)!!.progress, 1e-4f)
        assertNull(TransitionPlan.at(w, 2_500))
        assertEquals(TransitionType.ZOOM_IN, TransitionPlan.at(w, 1_900)!!.type)
    }

    @Test fun shaderIndexIsStablePerType() {
        // The GL program switches on this number; every shader type needs a distinct one.
        val ids = TransitionType.entries.filter(TransitionPlan::usesShader).map(TransitionPlan::shaderId)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it >= 1 })
    }

    @Test fun proSetLeavesBasicsFree() {
        listOf(TransitionType.NONE, TransitionType.FADE_BLACK, TransitionType.FADE_WHITE, TransitionType.ZOOM_IN, TransitionType.WHIP_LEFT)
            .forEach { assertFalse("$it should be free", TransitionPlan.isPro(it)) }
        assertTrue(TransitionPlan.isPro(TransitionType.GLITCH))
    }
}
