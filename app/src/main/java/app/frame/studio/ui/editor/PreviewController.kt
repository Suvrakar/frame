package app.frame.studio.ui.editor

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.Project
import app.frame.studio.domain.VideoClip
import app.frame.studio.export.ExportPlan
import app.frame.studio.timeline.TimelineMath
import kotlin.math.abs

/**
 * Drives the editor preview: one player for the video track and one for the music track,
 * kept in sync on the project's output timeline. Not thread-safe; use on the main thread.
 */
@OptIn(UnstableApi::class)
class PreviewController(context: Context) {

    val videoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private val musicPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var currentClipIndex by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var clips: List<VideoClip> = emptyList()
    private var music: List<AudioClip> = emptyList()
    private var fades: List<ExportPlan.FadeWindow> = emptyList()
    private var videoKey: List<Any> = emptyList()
    private var musicKey: List<Any> = emptyList()

    init {
        videoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyClipParameters()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                syncMusic(force = true)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    videoPlayer.playWhenReady = false
                    musicPlayer.playWhenReady = false
                    positionMs = TimelineMath.totalVideoMs(clips)
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error = "Preview error: ${e.errorCodeName}"
            }
        })
    }

    val totalMs: Long get() = TimelineMath.totalVideoMs(clips)

    fun setProject(project: Project, transitionMs: Long = project.crossfadeMs) {
        val newVideoKey: List<Any> = project.videoClips.map { listOf(it.id, it.sourceUri, it.trimStartMs, it.trimEndMs) }
        val resume = positionMs
        clips = project.videoClips
        fades = ExportPlan.fadeWindows(clips, transitionMs)
        if (newVideoKey != videoKey) {
            videoKey = newVideoKey
            error = null
            videoPlayer.setMediaItems(clips.map { clipItem(it.sourceUri, it.trimStartMs, it.trimEndMs) })
            videoPlayer.prepare()
            if (clips.isNotEmpty()) seekTo(resume.coerceAtMost((totalMs - 1).coerceAtLeast(0)))
        }
        applyClipParameters()

        val fitted = ExportPlan.fitAudio(project.audioClips, totalMs)
        val newMusicKey: List<Any> = fitted.map { listOf(it.id, it.sourceUri, it.trimStartMs, it.trimEndMs) }
        music = fitted
        if (newMusicKey != musicKey) {
            musicKey = newMusicKey
            musicPlayer.setMediaItems(fitted.map { clipItem(it.sourceUri, it.trimStartMs, it.trimEndMs) })
            musicPlayer.prepare()
            syncMusic(force = true)
        }
    }

    private fun clipItem(uri: String, startMs: Long, endMs: Long) = MediaItem.Builder()
        .setUri(Uri.parse(uri))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build(),
        )
        .build()

    private fun applyClipParameters() {
        val clip = clips.getOrNull(videoPlayer.currentMediaItemIndex) ?: return
        currentClipIndex = videoPlayer.currentMediaItemIndex
        val speed = clip.speed.coerceIn(0.5f, 2f)
        if (videoPlayer.playbackParameters.speed != speed) {
            videoPlayer.playbackParameters = PlaybackParameters(speed)
        }
        updateVolumes()
    }

    /** Called every frame-ish by the UI to refresh the playhead, fades and music sync. */
    fun tick() {
        if (clips.isEmpty()) {
            positionMs = 0
            return
        }
        if (videoPlayer.playbackState != Player.STATE_ENDED) {
            positionMs = TimelineMath.globalFromVideo(clips, videoPlayer.currentMediaItemIndex, videoPlayer.currentPosition)
        }
        updateVolumes()
        syncMusic(force = false)
    }

    /** Brightness for the preview fade overlay. */
    fun brightness(): Float = ExportPlan.brightnessAt(fades, positionMs)

    private fun updateVolumes() {
        val index = videoPlayer.currentMediaItemIndex
        val clip = clips.getOrNull(index)
        if (clip != null) {
            val offset = positionMs - TimelineMath.videoStarts(clips)[index]
            videoPlayer.volume = ExportPlan.gainAt(clip.volume, clip.fadeInMs, clip.fadeOutMs, clip.outputDurationMs(), offset)
                .coerceIn(0f, 1f)
        }
        TimelineMath.locateAudio(music, positionMs)?.let { pos ->
            val a = music[pos.index]
            musicPlayer.volume = ExportPlan.gainAt(a.volume, a.fadeInMs, a.fadeOutMs, a.outputDurationMs(), pos.offsetInClipMs)
                .coerceIn(0f, 1f)
        }
    }

    private fun syncMusic(force: Boolean) {
        if (music.isEmpty()) {
            musicPlayer.playWhenReady = false
            return
        }
        val pos = TimelineMath.locateAudio(music, positionMs)
        if (pos == null) {
            musicPlayer.playWhenReady = false
            return
        }
        val drift = if (musicPlayer.currentMediaItemIndex == pos.index) {
            abs(musicPlayer.currentPosition - pos.offsetInClipMs)
        } else {
            Long.MAX_VALUE
        }
        if (force || drift > MAX_DRIFT_MS) musicPlayer.seekTo(pos.index, pos.offsetInClipMs)
        musicPlayer.playWhenReady = videoPlayer.isPlaying
    }

    fun togglePlay() {
        if (clips.isEmpty()) return
        if (videoPlayer.isPlaying) {
            videoPlayer.pause()
            return
        }
        if (videoPlayer.playbackState == Player.STATE_ENDED || positionMs >= totalMs - 50) seekTo(0)
        if (videoPlayer.playbackState == Player.STATE_IDLE) videoPlayer.prepare()
        videoPlayer.play()
    }

    fun pause() {
        videoPlayer.pause()
        musicPlayer.pause()
    }

    fun seekTo(globalMs: Long) {
        if (clips.isEmpty()) return
        val target = globalMs.coerceIn(0, (totalMs - 1).coerceAtLeast(0))
        val pos = TimelineMath.locateVideo(clips, target) ?: return
        videoPlayer.seekTo(pos.index, TimelineMath.outputOffsetToMediaMs(clips[pos.index], pos.offsetInClipMs))
        positionMs = target
        applyClipParameters()
        syncMusic(force = true)
    }

    fun release() {
        videoPlayer.release()
        musicPlayer.release()
    }

    companion object {
        private const val MAX_DRIFT_MS = 300L
    }
}
