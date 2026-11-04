package app.frame.studio.effects

import app.frame.studio.domain.EffectGroup
import app.frame.studio.domain.SceneEffect
import app.frame.studio.domain.SceneEffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneEffectsTest {

    private val rain = SceneEffect("e", SceneEffectType.RAIN, startMs = 1_000, endMs = 5_000, intensity = 0.8f)

    @Test
    fun envelopeFadesInAndOut() {
        assertEquals(0f, SceneEffects.envelope(rain, 999), 1e-6f)
        assertEquals(0.4f, SceneEffects.envelope(rain, 1_150), 1e-3f)
        assertEquals(0.8f, SceneEffects.envelope(rain, 3_000), 1e-6f)
        assertEquals(0.4f, SceneEffects.envelope(rain, 4_850), 1e-3f)
        assertEquals(0f, SceneEffects.envelope(rain, 5_000), 1e-6f)
    }

    @Test
    fun placementStartsAtPlayheadOrFromTheStartAtTheEnd() {
        assertEquals(2_000L to 6_000L, SceneEffects.placement(2_000, 10_000))
        assertEquals(8_000L to 10_000L, SceneEffects.placement(8_000, 10_000))
        assertEquals(0L to 4_000L, SceneEffects.placement(9_950, 10_000))
        assertNull(SceneEffects.placement(0, 100))
    }

    @Test
    fun clampKeepsEffectsInsideTheVideo() {
        val clamped = SceneEffects.clamp(listOf(rain, rain.copy(id = "late", startMs = 9_000, endMs = 12_000)), totalMs = 4_000)
        assertEquals(listOf("e"), clamped.map { it.id })
        assertEquals(4_000L, clamped.first().endMs)
    }

    @Test
    fun everyGroupHasEffects() {
        EffectGroup.entries.forEach { g -> assertTrue(g.label, SceneEffectType.entries.any { it.group == g }) }
        assertEquals(12, SceneEffectType.entries.size)
    }
}
