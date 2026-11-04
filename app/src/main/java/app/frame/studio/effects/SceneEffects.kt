package app.frame.studio.effects

import app.frame.studio.domain.SceneEffect

/** Timing helpers for scene effects (pure, unit-tested). */
object SceneEffects {

    const val FADE_MS = 300L
    const val DEFAULT_MS = 4_000L

    /** Strength (0..1) of [effect] at output time [tMs], with short fades at both ends. */
    fun envelope(effect: SceneEffect, tMs: Long): Float {
        if (tMs < effect.startMs || tMs >= effect.endMs) return 0f
        val length = effect.endMs - effect.startMs
        val fade = minOf(FADE_MS, length / 3).coerceAtLeast(1)
        val a = minOf((tMs - effect.startMs).toFloat() / fade, (effect.endMs - tMs).toFloat() / fade, 1f)
        return a.coerceIn(0f, 1f) * effect.intensity.coerceIn(0f, 1f)
    }

    /** Where a new effect goes: from the playhead, [DEFAULT_MS] long, clipped to the video. */
    fun placement(playheadMs: Long, totalMs: Long): Pair<Long, Long>? {
        if (totalMs < 200) return null
        val start = if (playheadMs >= totalMs - 200) 0 else playheadMs.coerceAtLeast(0)
        return start to minOf(start + DEFAULT_MS, totalMs)
    }

    /** Keeps effects inside the video after edits; drops ones that no longer fit. */
    fun clamp(effects: List<SceneEffect>, totalMs: Long): List<SceneEffect> = effects.mapNotNull { e ->
        val start = e.startMs.coerceIn(0, totalMs)
        val end = e.endMs.coerceIn(start, totalMs)
        if (end - start < 100) null else e.copy(startMs = start, endMs = end)
    }
}
