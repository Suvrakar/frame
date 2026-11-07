package app.frame.studio.text

import app.frame.studio.domain.TextAnimation
import app.frame.studio.domain.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMotionTest {
    private fun text(inA: TextAnimation? = null, outA: TextAnimation? = null, animate: Boolean = true, ms: Long = 400) =
        TextOverlay(id = "t", text = "Hello", startMs = 1_000, endMs = 4_000, animate = animate, animIn = inA, animOut = outA, animMs = ms)

    @Test fun hiddenOutsideItsTime() {
        assertNull(TextMotion.at(text(), 999))
        assertNull(TextMotion.at(text(), 4_000))
    }

    @Test fun oldProjectsKeepTheir250msFade() {
        val o = text() // animIn/animOut null, animate = true: the v1 fade
        assertEquals(0f, TextMotion.at(o, 1_000)!!.alpha, 1e-3f)
        assertEquals(0.5f, TextMotion.at(o, 1_125)!!.alpha, 1e-3f)
        assertEquals(1f, TextMotion.at(o, 1_250)!!.alpha, 1e-3f)
        assertEquals(0.5f, TextMotion.at(o, 3_875)!!.alpha, 1e-3f)
    }

    @Test fun noAnimationWhenTurnedOff() {
        val p = TextMotion.at(text(animate = false), 1_000)!!
        assertEquals(1f, p.alpha, 0f)
        assertEquals(TextPose.REST, p)
    }

    @Test fun restPoseInTheMiddle() {
        TextAnimation.entries.forEach { a ->
            assertEquals("$a mid", TextPose.REST, TextMotion.at(text(a, a), 2_500))
        }
    }

    @Test fun everyAnimationStartsHiddenOrOffAndEndsAtRest() {
        TextAnimation.entries.filter { it != TextAnimation.NONE }.forEach { a ->
            val start = TextMotion.at(text(a, TextAnimation.NONE), 1_000)!!
            assertTrue("$a should not start at rest", start != TextPose.REST)
            assertEquals("$a at the end of its in-time", TextPose.REST, TextMotion.at(text(a, TextAnimation.NONE), 1_400))
        }
    }

    @Test fun slideUpComesFromBelow() {
        val p = TextMotion.at(text(TextAnimation.SLIDE_UP, TextAnimation.NONE), 1_100)!!
        assertTrue(p.dy > 0f)
        assertEquals(0f, p.dx, 0f)
    }

    @Test fun popOvershoots() {
        val peak = (1_000L..1_400L step 20).maxOf { TextMotion.at(text(TextAnimation.POP, TextAnimation.NONE), it)!!.scale }
        assertTrue("pop should overshoot, max $peak", peak > 1.02f)
    }

    @Test fun typewriterRevealsLettersAndKeepsFullOpacity() {
        val o = text(TextAnimation.TYPEWRITER, TextAnimation.NONE)
        val half = TextMotion.at(o, 1_200)!!
        assertEquals(1f, half.alpha, 0f)
        assertEquals(0.5f, half.reveal, 0.01f)
        assertEquals(3, TextMotion.visibleChars("Hello", half.reveal))
        assertEquals(5, TextMotion.visibleChars("Hello", 1f))
        assertEquals(0, TextMotion.visibleChars("Hello", 0f))
    }

    @Test fun outAnimationMirrorsIn() {
        val o = text(TextAnimation.NONE, TextAnimation.FADE)
        assertEquals(1f, TextMotion.at(o, 1_000)!!.alpha, 0f)
        assertEquals(0.5f, TextMotion.at(o, 3_800)!!.alpha, 0.02f)
    }

    @Test fun animationTimeIsCappedToAThirdOfAShortText() {
        val o = TextOverlay(id = "t", text = "Hi", startMs = 0, endMs = 600, animIn = TextAnimation.FADE, animOut = TextAnimation.FADE, animMs = 1_000)
        assertEquals(1f, TextMotion.at(o, 300)!!.alpha, 1e-3f)
        assertEquals(0.5f, TextMotion.at(o, 100)!!.alpha, 1e-3f)
    }

    @Test fun basicsAreFree() {
        listOf(TextAnimation.NONE, TextAnimation.FADE, TextAnimation.POP, TextAnimation.SLIDE_UP).forEach { assertFalse(TextMotion.isPro(it)) }
        assertTrue(TextMotion.isPro(TextAnimation.TYPEWRITER))
    }
}
