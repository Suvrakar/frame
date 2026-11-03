package app.frame.studio.export

import android.graphics.Color
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.Project
import app.frame.studio.domain.VideoClip
import app.frame.studio.timeline.TimelineMath
import com.google.common.collect.ImmutableList

/** Translates a [Project] into a Media3 [Composition]. */
@OptIn(UnstableApi::class)
object CompositionFactory {

    fun build(project: Project): Composition {
        require(project.videoClips.isNotEmpty()) { "Add at least one video clip" }
        val size = ExportPlan.outputSize(project.videoClips, project.exportHeight)

        val videoItems = project.videoClips.map { videoItem(it, size) }
        val videoSequence = EditedMediaItemSequence.Builder(videoItems)
            // Clips without an audio track still contribute silence so the mix stays in sync.
            .experimentalSetForceAudioTrack(true)
            .build()

        val sequences = mutableListOf(videoSequence)
        val totalMs = TimelineMath.totalVideoMs(project.videoClips)
        val music = ExportPlan.fitAudio(project.audioClips, totalMs)
        if (music.isNotEmpty()) {
            sequences += EditedMediaItemSequence.Builder(music.map(::audioItem)).build()
        }

        val compositionVideoEffects = mutableListOf<Effect>()
        val windows = ExportPlan.fadeWindows(project.videoClips, project.crossfadeMs)
        if (windows.any { it.fadeInMs > 0 || it.fadeOutMs > 0 }) {
            compositionVideoEffects += fadeEffect(windows)
        }
        val overlays = project.textOverlays
            .filter { it.text.isNotBlank() && it.endMs > it.startMs }
            .map { timedTextOverlay(it, size) }
        if (overlays.isNotEmpty()) {
            compositionVideoEffects += OverlayEffect(ImmutableList.copyOf(overlays))
        }

        return Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), compositionVideoEffects))
            .build()
    }

    private fun videoItem(clip: VideoClip, size: ExportPlan.Size): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(clip.sourceUri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs)
                    .setEndPositionMs(clip.trimEndMs)
                    .build(),
            )
            .build()

        val speed = clip.speed.coerceIn(0.5f, 2f)
        val audio = mutableListOf<AudioProcessor>()
        val video = mutableListOf<Effect>()
        if (speed != 1f) {
            val provider = ConstantSpeedProvider(speed)
            audio += SpeedChangingAudioProcessor(provider)
            video += SpeedChangeEffect(provider)
        }
        if (clip.volume != 1f || clip.fadeInMs > 0 || clip.fadeOutMs > 0) {
            audio += GainAudioProcessor(clip.volume, clip.fadeInMs, clip.fadeOutMs, clip.outputDurationMs())
        }
        clip.crop?.let { crop ->
            val ndc = ExportPlan.cropToNdc(crop)
            video += Crop(ndc[0], ndc[1], ndc[2], ndc[3])
        }
        if (clip.rotationDegrees % 360 != 0) {
            // Media3 rotates counter-clockwise; the editor rotates clockwise.
            video += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees((360 - clip.rotationDegrees % 360).toFloat())
                .build()
        }
        // Every clip is letterboxed into the same frame so mixed orientations export cleanly.
        video += Presentation.createForWidthAndHeight(
            size.width,
            size.height,
            Presentation.LAYOUT_SCALE_TO_FIT,
        )

        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(clip.volume <= 0f)
            .setEffects(Effects(audio, video))
            .build()
    }

    private fun audioItem(clip: AudioClip): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(clip.sourceUri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartMs)
                    .setEndPositionMs(clip.trimEndMs)
                    .build(),
            )
            .build()
        val audio = mutableListOf<AudioProcessor>()
        if (clip.volume != 1f || clip.fadeInMs > 0 || clip.fadeOutMs > 0) {
            audio += GainAudioProcessor(clip.volume, clip.fadeInMs, clip.fadeOutMs, clip.outputDurationMs())
        }
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(audio, emptyList()))
            .build()
    }

    private fun fadeEffect(windows: List<ExportPlan.FadeWindow>): RgbMatrix = RgbMatrix { presentationTimeUs, _ ->
        val f = ExportPlan.brightnessAt(windows, presentationTimeUs / 1000)
        floatArrayOf(
            f, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, f, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    private fun timedTextOverlay(
        overlay: app.frame.studio.domain.TextOverlay,
        size: ExportPlan.Size,
    ): TextureOverlay {
        val text = SpannableString(" ${overlay.text.trim()} ").apply {
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(BackgroundColorSpan(0x99000000.toInt()), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // TextOverlay renders at a fixed 100px; scale it relative to a 1080p short side.
        val scale = (minOf(size.width, size.height) / 1080f).coerceAtLeast(0.4f)
        fun settings(alpha: Float) = StaticOverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setScale(scale, scale)
            .setBackgroundFrameAnchor(
                (overlay.x.coerceIn(0f, 1f) * 2 - 1),
                (1 - overlay.y.coerceIn(0f, 1f) * 2),
            )
            .build()
        val visible = settings(1f)
        val hidden = settings(0f)
        val startUs = overlay.startMs * 1000
        val endUs = overlay.endMs * 1000
        return object : TextOverlay() {
            override fun getText(presentationTimeUs: Long): SpannableString = text
            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
                if (presentationTimeUs in startUs until endUs) visible else hidden
        }
    }

    private class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
    }
}
