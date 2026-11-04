package app.frame.studio.ui.editor

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
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

    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("FramePreview", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val playerHandler = Handler(thread.looper)

    private var renderedKey: Project? = null
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
            main.post {
                ended = isEnded
                if (isEnded) positionMs = totalMs
            }
        }

        override fun onPlayerError(e: PlaybackException) {
            Log.w(TAG, "Preview error", e)
            main.post { error = "Preview error: ${e.errorCodeName}" }
        }
    }

    private fun onPlayer(block: () -> Unit) {
        if (!released) playerHandler.post(block)
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
            error = e.message
            return
        }
        val resumeAt = positionMs.coerceIn(0, (totalMs - 1).coerceAtLeast(0))
        val resume = isPlaying
        error = null
        ended = false
        hasPlayer = true
        positionMs = resumeAt
        publishedPositionMs = resumeAt
        onPlayer { buildPlayerOnThread(composition, resumeAt, resume) }
    }

    private fun buildPlayerOnThread(composition: Composition, resumeAt: Long, resume: Boolean) {
        // Release first: a Surface can only feed one player at a time.
        releasePlayerOnThread()
        val next = try {
            CompositionPlayer.Builder(context).setLooper(thread.looper).build()
        } catch (e: Exception) {
            Log.w(TAG, "Could not create preview player", e)
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
            surface = newSurface
            surfaceSize = Size(width, height)
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
        val restart = ended || positionMs >= totalMs - 50
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

    fun seekTo(ms: Long) {
        if (!hasPlayer) return
        val target = ms.coerceIn(0, (totalMs - 1).coerceAtLeast(0))
        positionMs = target
        publishedPositionMs = target
        ended = false
        onPlayer { player?.seekTo(target) }
    }

    fun release() {
        if (released) return
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
    }
}
