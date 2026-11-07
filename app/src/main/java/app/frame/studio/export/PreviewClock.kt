package app.frame.studio.export

/**
 * Where the editor preview last started playing from (output timeline, ms). Media3 1.8 audio
 * processors aren't told the position after a seek, so time-based audio (fades, ducking) reads
 * it here when the preview flushes them. Export never seeks and ignores it.
 */
object PreviewClock {
    @Volatile
    var startMs: Long = 0L
}
