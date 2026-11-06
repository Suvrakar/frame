package app.frame.studio.ui.editor

import app.frame.studio.telemetry.Telemetry
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.CompositionPlayer
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.ExportQuality
import app.frame.studio.domain.Project
import app.frame.studio.export.CompositionFactory
import app.frame.studio.timeline.TimelineMath
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Plays the project's Media3 composition, so the preview shows exactly what the export renders
 * (speed, filters, crop, transitions, text, mixed audio).
 *
 * The player lives on its own thread: building, seeking and especially releasing a
 * CompositionPlayer (which waits for its decoders to shut down) never blocks the UI thread, so
 * edits stay smooth on low-end phones. The UI talks to it only through this class, which is used
 * from the main thread.
 *
 * CompositionPlayer is still marked library-internal in Media3 1.8 (it powers Media3's own
 * composition demo). Media3 is pinned in app/build.gradle.kts and the device tests exercise this
 * player in CI; re-check it whenever Media3 is upgraded.
 */
@SuppressLint("RestrictedApi")
@OptIn(UnstableApi::class)
class PreviewController(
    private val context: Context,
    private val previewHeight: ExportHeight = ExportHeight.HD_720,
) {
    // ---- Main-thread state observed by Compose ----
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var totalMs by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasPlayer: Boolean = false
        private set

    /** Bumps each time the player is ready with a freshly rendered frame (after a rebuild or seek). */
    var frameVersion by mutableIntStateOf(0)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("FramePreview", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val playerHandler = Handler(thread.looper)

    private var renderedKey: Project? = null
    /** Whether the current preview composites overlays (multi-input video graph). */
    private var layered = false
    /** When the player was last rebuilt after an error; see [RECOVERY_WINDOW_MS]. */
    private var lastRecoveryAt = -RECOVERY_WINDOW_MS
    private var lastProject: Project? = null
    private var ended = false
    private var scrubbing = false
    private var released = false

    // ---- Player-thread state ----
    private var player: CompositionPlayer? = null
    private var surface: Surface? = null
    private var surfaceSize: Size? = null
    private var muted = false

    /** Latest position published by the player thread; read by [tick]. */
    @Volatile
    private var publishedPositionMs = 0L

    private val publishPosition = object : Runnable {
        override fun run() {
            val p = player ?: return
            publishedPositionMs = p.currentPosition
            if (p.isPlaying) playerHandler.postDelayed(this, 16)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            if (playing) {
                playerHandler.removeCallbacks(publishPosition)
                playerHandler.post(publishPosition)
            } else {
                publishedPositionMs = player?.currentPosition ?: publishedPositionMs
            }
            main.post { isPlaying = playing }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isEnded = playbackState == Player.STATE_ENDED
            val isReady = playbackState == Player.STATE_READY
            main.post {
                if (isReady) frameVersion++
                ended = isEnded
                if (isEnded) positionMs = totalMs
            }
        }

        override fun onPlayerError(e: PlaybackException) {
            Log.w(TAG, "Preview error", e)
            Telemetry.nonFatal("preview_playback", e)
            main.post {
                // Media3's composition player has rare internal failures (e.g. its compositor after
                // reaching the end). Rebuild once at a safe position before bothering the user.
                val project = lastProject
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastRecoveryAt > RECOVERY_WINDOW_MS && project != null && !released) {
                    lastRecoveryAt = now
                    isPlaying = false
                    setProject(project, force = true)
                } else {
                    error = "Preview error: ${e.errorCodeName}"
                }
            }
        }
    }

    /**
     * Runs [block] on the player thread. A failure there must never take the app down: it is
     * logged and shown as a preview error instead (the editor and the user's work stay intact).
     */
    private fun onPlayer(block: () -> Unit) {
        if (released) return
        playerHandler.post {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "Preview player call failed", e)
                main.post { error = "Preview error: ${e.javaClass.simpleName}" }
            }
        }
    }

    /** Rebuilds the preview when anything that affects rendering changed. */
    fun setProject(project: Project, force: Boolean = false) {
        lastProject = project
        // Preview at a device-appropriate resolution: identical framing to the export, cheaper to render.
        val key = project.copy(
            name = "",
            createdAtEpochMs = 0,
            updatedAtEpochMs = 0,
            exportHeight = previewHeight,
            exportFps = 30,
            exportQuality = ExportQuality.HIGH,
        )
        if (key == renderedKey && !force) return
        renderedKey = key
        totalMs = TimelineMath.totalVideoMs(project.videoClips)
        if (project.videoClips.isEmpty()) {
            hasPlayer = false
            isPlaying = false
            positionMs = 0
            onPlayer { releasePlayerOnThread() }
            return
        }
        val composition = try {
            CompositionFactory.build(key, forExport = false)
        } catch (e: Exception) {
            Log.w(TAG, "Could not build preview", e)
            Telemetry.nonFatal("preview_build", e)
            error = e.message
            return
        }
        val resumeAt = safeResumePosition(positionMs, totalMs)
        val resume = isPlaying
        error = null
        ended = false
        hasPlayer = true
        positionMs = resumeAt
        publishedPositionMs = resumeAt
        val isLayered = CompositionFactory.hasOverlays(key)
        layered = isLayered
        onPlayer { buildPlayerOnThread(composition, resumeAt, resume, isLayered) }
    }

    private fun buildPlayerOnThread(composition: Composition, resumeAt: Long, resume: Boolean, layered: Boolean) {
        // Release first: a Surface can only feed one player at a time.
        releasePlayerOnThread()
        val next = try {
            CompositionPlayer.Builder(context).setLooper(thread.looper)
                // Overlays (picture-in-picture) need the compositing video graph.
                .apply { if (layered) setVideoGraphFactory(MultipleInputVideoGraph.Factory()) }
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Could not create preview player", e)
            Telemetry.nonFatal("preview_player", e)
            main.post { error = "Preview unavailable" }
            return
        }
        next.addListener(listener)
        next.volume = if (muted) 0f else 1f
        next.setComposition(composition)
        val s = surface
        val size = surfaceSize
        if (s != null && size != null) next.setVideoSurface(s, size)
        next.seekTo(resumeAt)
        next.prepare()
        next.playWhenReady = resume
        player = next
    }

    private fun releasePlayerOnThread() {
        playerHandler.removeCallbacks(publishPosition)
        val old = player ?: return
        player = null
        old.removeListener(listener)
        try {
            old.release()
        } catch (e: Exception) {
            Log.w(TAG, "Preview release failed", e)
        }
    }

    // ---- Surface (called from the main thread by the SurfaceView) ----

    fun attachSurface(newSurface: Surface, width: Int, height: Int) {
        onPlayer {
            val resized = newSurface === surface && surfaceSize != Size(width, height)
            surface = newSurface
            surfaceSize = Size(width, height)
            // The player ignores a new size for the same Surface object and keeps rendering at the
            // old one; the system then rejects every frame (wrong buffer size) and the preview
            // freezes, drawn small in a corner. Re-attach so the new size is taken.
            if (resized) player?.clearVideoSurface(newSurface)
            player?.setVideoSurface(newSurface, Size(width, height))
            // Redraw the current frame at the new size when paused.
            player?.let { if (!it.isPlaying) it.seekTo(it.currentPosition) }
        }
        // A player released while the surface was gone is rebuilt now.
        val project = lastProject
        if (project != null && hasPlayer.not() && project.videoClips.isNotEmpty()) setProject(project, force = true)
    }

    /**
     * The surface is being destroyed: the player must stop drawing into it before this returns.
     * Releasing on the player thread usually takes a few milliseconds; the wait is capped.
     */
    fun detachSurface() {
        val done = CountDownLatch(1)
        onPlayer {
            releasePlayerOnThread()
            surface = null
            surfaceSize = null
            done.countDown()
        }
        done.await(SURFACE_RELEASE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        hasPlayer = false
        isPlaying = false
        renderedKey = null
    }

    // ---- Transport ----

    /** Called every frame by the UI to refresh the playhead. */
    fun tick() {
        if (!hasPlayer || ended || scrubbing) return
        positionMs = publishedPositionMs.coerceIn(0, totalMs)
    }

    fun togglePlay() {
        if (!hasPlayer) return
        if (isPlaying) {
            pause()
            return
        }
        val restart = ended || positionMs >= totalMs - END_GUARD_MS - 50
        val project = lastProject
        if (restart && layered && project != null) {
            // A layered (overlay) player that reached the end can't seek back: its compositor has
            // ended its inputs. Build a fresh player at the start and play.
            positionMs = 0
            publishedPositionMs = 0
            ended = false
            isPlaying = true
            setProject(project, force = true)
            return
        }
        if (restart) seekTo(0)
        ended = false
        onPlayer { player?.play() }
    }

    fun play() {
        onPlayer { player?.play() }
    }

    fun pause() {
        onPlayer { player?.pause() }
    }

    /** Re-renders the current frame, e.g. after the preview surface was resized while paused. */
    fun redraw() {
        onPlayer { player?.let { if (!it.isPlaying) it.seekTo(it.currentPosition) } }
    }

    /** Silences the preview, e.g. while recording a voiceover. */
    fun setMuted(value: Boolean) {
        onPlayer {
            muted = value
            player?.volume = if (value) 0f else 1f
        }
    }

    fun setScrubbing(enabled: Boolean) {
        if (scrubbing == enabled) return
        scrubbing = enabled
        onPlayer { runCatching { player?.setScrubbingModeEnabled(enabled) } }
    }

    private var lastScrubSeekAt = 0L
    private var pendingScrubMs = -1L
    private val trailingScrubSeek = Runnable {
        val target = pendingScrubMs
        if (target >= 0) {
            pendingScrubMs = -1
            lastScrubSeekAt = android.os.SystemClock.uptimeMillis()
            onPlayer { player?.seekTo(target) }
        }
    }

    /**
     * Scrubbing from the timeline: the playhead follows every frame, but the player (which must
     * decode a frame per seek) is asked at most every [SCRUB_SEEK_INTERVAL_MS], plus once more at
     * the final position. Keeps fast scrubbing smooth on slow phones.
     */
    fun scrubTo(ms: Long) {
        if (!hasPlayer) return
        val target = safeResumePosition(ms, totalMs)
        positionMs = target
        publishedPositionMs = target
        ended = false
        val now = android.os.SystemClock.uptimeMillis()
        main.removeCallbacks(trailingScrubSeek)
        if (now - lastScrubSeekAt >= SCRUB_SEEK_INTERVAL_MS) {
            lastScrubSeekAt = now
            pendingScrubMs = -1
            onPlayer { player?.seekTo(target) }
        } else {
            pendingScrubMs = target
            main.postDelayed(trailingScrubSeek, SCRUB_SEEK_INTERVAL_MS)
        }
    }

    fun seekTo(ms: Long) {
        if (!hasPlayer) return
        val target = safeResumePosition(ms, totalMs)
        positionMs = target
        publishedPositionMs = target
        ended = false
        val project = lastProject
        if (layered && !isPlaying && project != null) {
            // A paused layered (overlay) player doesn't redraw after a seek: the old frame stays
            // and the overlay/text at the new time never appear. Rebuild at the target instead.
            setProject(project, force = true)
            return
        }
        onPlayer { player?.seekTo(target) }
    }

    fun release() {
        if (released) return
        main.removeCallbacks(trailingScrubSeek)
        hasPlayer = false
        renderedKey = null
        playerHandler.post {
            releasePlayerOnThread()
            thread.quitSafely()
        }
        released = true
    }

    companion object {
        private const val TAG = "PreviewController"
        private const val SURFACE_RELEASE_TIMEOUT_MS = 1_000L
        private const val SCRUB_SEEK_INTERVAL_MS = 50L

        /**
         * The preview never parks closer than this to the end. Media3 1.8's composition audio graph
         * throws (NullPointerException in AudioGraphInputAudioSink.playToEndOfStream) when a player
         * is prepared or seeked to the very end of a timeline whose items have no real audio, e.g.
         * photos. That showed as "Preview error: ERROR_CODE_UNSPECIFIED" after editing text or an
         * overlay with the playhead at the end.
         */
        const val END_GUARD_MS = 250L

        /** At most one automatic rebuild after a player error per this window; a repeat is shown. */
        private const val RECOVERY_WINDOW_MS = 10_000L

        /** Where the preview may seek for a playhead at [positionMs] in a [totalMs] video. */
        fun safeResumePosition(positionMs: Long, totalMs: Long): Long =
            positionMs.coerceIn(0, (totalMs - END_GUARD_MS).coerceAtLeast(0))
    }
}
