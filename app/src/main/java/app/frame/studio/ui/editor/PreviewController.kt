package app.frame.studio.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.ExportQuality
import app.frame.studio.domain.Project
import app.frame.studio.export.CompositionFactory
import app.frame.studio.timeline.TimelineMath

/**
 * Plays the project's Media3 composition, so the preview shows exactly what the export renders
 * (speed, filters, crop, transitions, text, mixed audio). Use on the main thread only.
 *
 * CompositionPlayer is still marked library-internal in Media3 1.8 (it powers Media3's own
 * composition demo). Media3 is pinned in app/build.gradle.kts and ExportPipelineTest exercises
 * this player on a device in CI; re-check it whenever Media3 is upgraded.
 */
@SuppressLint("RestrictedApi")
@OptIn(UnstableApi::class)
class PreviewController(private val context: Context) {

    var player by mutableStateOf<CompositionPlayer?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var totalMs by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var renderedKey: Project? = null
    private var muted = false
    private var ended = false
    private var scrubbing = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            ended = playbackState == Player.STATE_ENDED
            if (ended) positionMs = totalMs
        }

        override fun onPlayerError(e: PlaybackException) {
            Log.w(TAG, "Preview error", e)
            error = "Preview error: ${e.errorCodeName}"
        }
    }

    /** Rebuilds the preview when anything that affects rendering changed. */
    fun setProject(project: Project) {
        // Preview at 720p: fast to render, identical framing to the export.
        val key = project.copy(
            name = "",
            createdAtEpochMs = 0,
            updatedAtEpochMs = 0,
            exportHeight = ExportHeight.HD_720,
            exportFps = 30,
            exportQuality = ExportQuality.HIGH,
        )
        if (key == renderedKey) return
        renderedKey = key
        totalMs = TimelineMath.totalVideoMs(project.videoClips)
        if (project.videoClips.isEmpty()) {
            replacePlayer(null)
            positionMs = 0
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
        val next = CompositionPlayer.Builder(context).build()
        next.addListener(listener)
        next.volume = if (muted) 0f else 1f
        next.setComposition(composition)
        next.seekTo(resumeAt)
        next.prepare()
        next.playWhenReady = resume
        replacePlayer(next)
        positionMs = resumeAt
        ended = false
    }

    private fun replacePlayer(next: CompositionPlayer?) {
        val old = player
        player = next
        old?.removeListener(listener)
        old?.release()
        if (next == null) isPlaying = false
    }

    /** Called every frame by the UI to refresh the playhead. */
    fun tick() {
        val p = player ?: return
        if (!ended && !scrubbing) positionMs = p.currentPosition.coerceIn(0, totalMs)
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            return
        }
        if (ended || positionMs >= totalMs - 50) seekTo(0)
        p.play()
    }

    fun pause() {
        player?.pause()
    }

    /** Silences the preview, e.g. while recording a voiceover. */
    fun setMuted(value: Boolean) {
        muted = value
        player?.volume = if (value) 0f else 1f
    }

    fun setScrubbing(enabled: Boolean) {
        if (scrubbing == enabled) return
        scrubbing = enabled
        runCatching { player?.setScrubbingModeEnabled(enabled) }
    }

    fun seekTo(ms: Long) {
        val p = player ?: return
        val target = ms.coerceIn(0, (totalMs - 1).coerceAtLeast(0))
        p.seekTo(target)
        positionMs = target
        ended = false
    }

    fun release() {
        replacePlayer(null)
        renderedKey = null
    }

    companion object {
        private const val TAG = "PreviewController"
    }
}
