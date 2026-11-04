package app.frame.studio.export

import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.FrameDropEffect
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
import app.frame.studio.captions.CaptionPlanner
import app.frame.studio.captions.CaptionSpans
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.OverlayClip
import app.frame.studio.timeline.Keyframes
import app.frame.studio.timeline.OverlayLayout
import app.frame.studio.timeline.Transform
import app.frame.studio.domain.Project
import app.frame.studio.domain.VideoClip
import app.frame.studio.effects.Filters
import app.frame.studio.effects.GradeEffect
import app.frame.studio.effects.SceneEffects
import app.frame.studio.effects.SceneShaderEffect
import app.frame.studio.timeline.AudioLayout
import app.frame.studio.timeline.TimelineMath
import com.google.common.collect.ImmutableList

/**
 * Translates a [Project] into a Media3 [Composition]. The same composition drives both the
 * editor preview ([androidx.media3.transformer.CompositionPlayer]) and the export, so what you
 * see is what you get.
 */
@OptIn(UnstableApi::class)
object CompositionFactory {

    private const val IMAGE_FPS = 30
    private const val TEXT_FADE_US = 250_000L

    /** A tiny transparent image that fills the gaps of overlay lanes (set by the app at start). */
    @Volatile
    var blankFrameUri: String? = null

    /** Overlay lanes composited at once (each is an extra decoder). */
    @Volatile
    var maxOverlayLanes: Int = 2

    fun hasOverlays(project: Project): Boolean =
        blankFrameUri != null && OverlayLayout.clampTo(project.overlays, TimelineMath.totalVideoMs(project.videoClips)).isNotEmpty()

    fun build(project: Project, forExport: Boolean = true): Composition {
        require(project.videoClips.isNotEmpty()) { "Add at least one clip" }
        val size = ExportPlan.outputSize(project.videoClips, project.exportHeight, project.aspect)

        val videoSequence = EditedMediaItemSequence.Builder(project.videoClips.map { videoItem(it, size, forExport) })
            // Clips without an audio track (and photos) still contribute silence so the mix stays in sync.
            .experimentalSetForceAudioTrack(true)
            .build()
        val totalMs = TimelineMath.totalVideoMs(project.videoClips)

        // Overlay lanes come first: the compositor draws the first sequence on top, and it also
        // sets the output timing, so every lane spans the whole video (gaps are transparent).
        val blank = blankFrameUri
        val lanes = if (blank == null) emptyList() else OverlayLayout.lanes(OverlayLayout.clampTo(project.overlays, totalMs), maxOverlayLanes)
        val sequences = mutableListOf<EditedMediaItemSequence>()
        lanes.forEach { lane -> sequences += overlayLane(lane, totalMs, size, blank!!, project.exportFps) }
        sequences += videoSequence

        AudioLayout.byLane(AudioLayout.clampTo(project.audioClips, totalMs)).forEach { lane ->
            if (lane.isEmpty()) return@forEach
            val builder = EditedMediaItemSequence.Builder()
            var cursorMs = 0L
            lane.forEach { clip ->
                val gap = clip.startMs - cursorMs
                if (gap > 0) builder.addGap(gap * 1000)
                builder.addItem(audioItem(clip))
                cursorMs = clip.startMs + clip.outputDurationMs()
            }
            sequences += builder.experimentalSetForceAudioTrack(true).build()
        }

        val compositionVideoEffects = mutableListOf<Effect>()
        val windows = ExportPlan.fadeWindows(project.videoClips, project.crossfadeMs)
        if (windows.any { it.fadeInMs > 0 || it.fadeOutMs > 0 }) {
            compositionVideoEffects += fadeEffect(windows)
        }
        // Scene effects (rain, snow, confetti…) sit above the picture but below text and stickers.
        SceneEffects.clamp(project.effects, totalMs).forEach { compositionVideoEffects += SceneShaderEffect(it) }
        val overlays = project.textOverlays
            .filter { it.text.isNotBlank() && it.endMs > it.startMs }
            .map { timedTextOverlay(it, size) }
        if (overlays.isNotEmpty()) {
            compositionVideoEffects += OverlayEffect(ImmutableList.copyOf(overlays))
        }
        if (forExport && project.exportFps < 60) {
            compositionVideoEffects += FrameDropEffect.createDefaultFrameDropEffect(project.exportFps.toFloat())
        }

        val builder = Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), compositionVideoEffects))
        if (lanes.isNotEmpty()) builder.setVideoCompositorSettings(OverlayCompositor(lanes, size))
        return builder.build()
    }

    /** One overlay lane: its clips, with transparent stills in the gaps, spanning [totalMs]. */
    private fun overlayLane(lane: List<OverlayClip>, totalMs: Long, size: ExportPlan.Size, blank: String, fps: Int): EditedMediaItemSequence {
        val items = mutableListOf<EditedMediaItem>()
        var cursor = 0L
        fun gap(ms: Long) {
            if (ms <= 0) return
            val item = MediaItem.Builder().setUri(Uri.parse(blank)).setImageDurationMs(ms).build()
            items += EditedMediaItem.Builder(item).setDurationUs(ms * 1000).setFrameRate(fps.coerceIn(24, 60)).setRemoveAudio(true).build()
        }
        lane.forEach { o ->
            gap(o.startMs - cursor)
            items += overlayItem(o, size, fps)
            cursor = o.endMs()
        }
        gap(totalMs - cursor)
        return EditedMediaItemSequence.Builder(items).experimentalSetForceAudioTrack(true).build()
    }

    private fun overlayItem(o: OverlayClip, canvas: ExportPlan.Size, fps: Int): EditedMediaItem {
        val video = mutableListOf<Effect>()
        val audio = mutableListOf<AudioProcessor>()
        val color = Filters.transformFor(o.filterId, 1f, app.frame.studio.domain.ColorAdjust())
        if (!color.isIdentity) {
            val matrix = color.toGl()
            video += RgbMatrix { _, _ -> matrix }
        }
        // Scale 1 = the overlay fits the frame; the compositor scales it down from there.
        val fit = OverlayGeometry.fitSize(o.width, o.height, canvas.width, canvas.height)
        video += Presentation.createForWidthAndHeight(fit.first, fit.second, Presentation.LAYOUT_SCALE_TO_FIT)
        if (o.volume != 1f) audio += GainAudioProcessor(o.volume, 0, 0, o.durationMs())
        val builder = if (o.isImage) {
            val item = MediaItem.Builder().setUri(Uri.parse(o.sourceUri)).setImageDurationMs(o.durationMs()).build()
            EditedMediaItem.Builder(item).setDurationUs(o.durationMs() * 1000).setFrameRate(fps.coerceIn(24, 60))
        } else {
            EditedMediaItem.Builder(clipped(o.sourceUri, o.trimStartMs, o.trimEndMs)).setDurationUs(o.sourceDurationMs.coerceAtLeast(1) * 1000)
        }
        return builder
            .setRemoveAudio(o.isImage || o.volume <= 0f)
            .setEffects(Effects(audio, video))
            .build()
    }

    /** Places each overlay lane per frame from its clip's keyframes; the main video fills the frame. */
    private class OverlayCompositor(private val lanes: List<List<OverlayClip>>, private val size: ExportPlan.Size) : VideoCompositorSettings {
        private val hidden = StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        private val full = StaticOverlaySettings.Builder().build()

        override fun getOutputSize(inputSizes: List<androidx.media3.common.util.Size>): androidx.media3.common.util.Size =
            androidx.media3.common.util.Size(size.width, size.height)

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            val lane = lanes.getOrNull(inputId) ?: return full
            val ms = presentationTimeUs / 1000
            val o = lane.firstOrNull { ms >= it.startMs && ms < it.endMs() } ?: return hidden
            val t = Keyframes.at(Keyframes.base(o), o.keyframes, ms - o.startMs)
            return StaticOverlaySettings.Builder()
                .setAlphaScale(t.opacity.coerceIn(0f, 1f))
                .setScale(t.scale.coerceIn(0.05f, 4f), t.scale.coerceIn(0.05f, 4f))
                // Media3 rotates counter-clockwise; the editor rotates clockwise.
                .setRotationDegrees(-t.rotation)
                .setBackgroundFrameAnchor(t.x.coerceIn(0f, 1f) * 2 - 1, 1 - t.y.coerceIn(0f, 1f) * 2)
                .build()
        }
    }

    private fun clipped(uri: String, startMs: Long, endMs: Long): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build(),
            )
            .build()

    private fun videoItem(clip: VideoClip, size: ExportPlan.Size, forExport: Boolean): EditedMediaItem {
        val audio = mutableListOf<AudioProcessor>()
        val video = mutableListOf<Effect>()

        val builder = if (clip.isImage) {
            val durationMs = clip.outputDurationMs().coerceAtLeast(100)
            val item = MediaItem.Builder()
                .setUri(Uri.parse(clip.sourceUri))
                .setImageDurationMs(durationMs)
                .build()
            EditedMediaItem.Builder(item)
                .setDurationUs(durationMs * 1000)
                .setFrameRate(IMAGE_FPS)
        } else {
            val speed = clip.speed.coerceIn(VideoClip.MIN_SPEED, VideoClip.MAX_SPEED)
            if (speed != 1f) {
                if (forExport) {
                    // Export: independent audio and video speed changes. The audio-driven variant
                    // below can stall long exports when audio and video timing race.
                    val provider = ConstantSpeedProvider(speed)
                    audio += SpeedChangingAudioProcessor(provider)
                    video += SpeedChangeEffect(provider)
                } else {
                    // Preview: CompositionPlayer only supports the audio-driven speed effect.
                    val pair = Effects.createExperimentalSpeedChangingEffect(ConstantSpeedProvider(speed))
                    audio += pair.first
                    video += pair.second
                }
            }
            if (clip.volume != 1f || clip.fadeInMs > 0 || clip.fadeOutMs > 0) {
                audio += GainAudioProcessor(clip.volume, clip.fadeInMs, clip.fadeOutMs, clip.outputDurationMs())
            }
            EditedMediaItem.Builder(clipped(clip.sourceUri, clip.trimStartMs, clip.trimEndMs))
                .setDurationUs(clip.sourceDurationMs.coerceAtLeast(1) * 1000)
        }

        clip.crop?.let { crop ->
            val ndc = ExportPlan.cropToNdc(crop)
            video += Crop(ndc[0], ndc[1], ndc[2], ndc[3])
        }
        if (clip.flipH || clip.rotationDegrees % 360 != 0) {
            // Media3 rotates counter-clockwise; the editor rotates clockwise.
            video += ScaleAndRotateTransformation.Builder()
                .setScale(if (clip.flipH) -1f else 1f, 1f)
                .setRotationDegrees(((360 - clip.rotationDegrees % 360) % 360).toFloat())
                .build()
        }
        val color = Filters.transformFor(clip.filterId, clip.filterIntensity, clip.adjust)
        if (!color.isIdentity) {
            val matrix = color.toGl()
            video += RgbMatrix { _, _ -> matrix }
        }
        // Every clip is fitted into the same canvas so mixed shapes export cleanly.
        video += Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_SCALE_TO_FIT)
        // Vignette and grain work on the fitted frame so they follow the canvas shape.
        val grade = Filters.gradeFor(clip.filterId, clip.filterIntensity, clip.adjust)
        if (!grade.isNone) video += GradeEffect(grade)

        return builder
            .setRemoveAudio(clip.isImage || clip.volume <= 0f)
            .setEffects(Effects(audio, video))
            .build()
    }

    private fun audioItem(clip: AudioClip): EditedMediaItem {
        val audio = mutableListOf<AudioProcessor>()
        if (clip.volume != 1f || clip.fadeInMs > 0 || clip.fadeOutMs > 0) {
            audio += GainAudioProcessor(clip.volume, clip.fadeInMs, clip.fadeOutMs, clip.outputDurationMs())
        }
        return EditedMediaItem.Builder(clipped(clip.sourceUri, clip.trimStartMs, clip.trimEndMs))
            .setDurationUs(clip.sourceDurationMs.coerceAtLeast(1) * 1000)
            .setRemoveVideo(true)
            .setEffects(Effects(audio, emptyList()))
            .build()
    }

    private fun fadeEffect(windows: List<ExportPlan.FadeWindow>): RgbMatrix = RgbMatrix { presentationTimeUs, _ ->
        val tone = ExportPlan.toneAt(windows, presentationTimeUs / 1000)
        val f = tone.factor
        val lift = if (tone.white) 1f - f else 0f
        floatArrayOf(
            f, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, f, 0f,
            lift, lift, lift, 1f,
        )
    }

    private fun timedTextOverlay(
        overlay: app.frame.studio.domain.TextOverlay,
        size: ExportPlan.Size,
    ): TextureOverlay {
        if (overlay.isCaption) return captionOverlay(overlay, size)
        val padded = if (overlay.background && !overlay.isSticker) " ${overlay.text.trim()} " else overlay.text.trim()
        val text = SpannableString(padded).apply {
            setSpan(ForegroundColorSpan(overlay.color.toInt()), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (overlay.background && !overlay.isSticker) {
                setSpan(BackgroundColorSpan(0x99000000.toInt()), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (overlay.bold) setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // TextOverlay renders at a fixed 100px; scale it relative to a 1080p short side.
        val base = (minOf(size.width, size.height) / 1080f).coerceAtLeast(0.3f)
        val scale = base * overlay.scale.coerceIn(0.3f, 4f) * (if (overlay.isSticker) 2f else 1f)
        val anchorX = overlay.x.coerceIn(0f, 1f) * 2 - 1
        val anchorY = 1 - overlay.y.coerceIn(0f, 1f) * 2
        fun settings(alpha: Float) = StaticOverlaySettings.Builder()
            .setAlphaScale((alpha * overlay.opacity).coerceIn(0f, 1f))
            .setScale(scale, scale)
            .setRotationDegrees(-overlay.rotation)
            .setBackgroundFrameAnchor(anchorX, anchorY)
            .build()
        val keyframes = overlay.keyframes
        val restPose = Transform(overlay.x, overlay.y, 1f, overlay.rotation, overlay.opacity)
        fun animated(presentationTimeUs: Long, alpha: Float): OverlaySettings {
            val t = Keyframes.at(restPose, keyframes, presentationTimeUs / 1000 - overlay.startMs)
            val s = scale * t.scale.coerceIn(0.1f, 6f)
            return StaticOverlaySettings.Builder()
                .setAlphaScale((alpha * t.opacity).coerceIn(0f, 1f))
                .setScale(s, s)
                .setRotationDegrees(-t.rotation)
                .setBackgroundFrameAnchor(t.x.coerceIn(0f, 1f) * 2 - 1, 1 - t.y.coerceIn(0f, 1f) * 2)
                .build()
        }
        val visible = settings(1f)
        val hidden = settings(0f)
        val startUs = overlay.startMs * 1000
        val endUs = overlay.endMs * 1000
        val animate = overlay.animate
        return object : TextOverlay() {
            override fun getText(presentationTimeUs: Long): SpannableString = text
            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                if (presentationTimeUs < startUs || presentationTimeUs >= endUs) return hidden
                if (keyframes.isNotEmpty()) {
                    val fade = if (animate) minOf(TEXT_FADE_US, (endUs - startUs) / 3) else 0
                    val alpha = if (fade <= 0) 1f else minOf(
                        (presentationTimeUs - startUs).toFloat() / fade,
                        (endUs - presentationTimeUs).toFloat() / fade,
                    ).coerceAtMost(1f)
                    return animated(presentationTimeUs, alpha)
                }
                if (!animate) return visible
                val fade = minOf(TEXT_FADE_US, (endUs - startUs) / 3)
                if (fade <= 0) return visible
                val alpha = minOf(
                    (presentationTimeUs - startUs).toFloat() / fade,
                    (endUs - presentationTimeUs).toFloat() / fade,
                )
                return if (alpha >= 1f) visible else settings(alpha)
            }
        }
    }

    /** Word-by-word caption: one pre-built styled string per word, switched by time. */
    private fun captionOverlay(overlay: app.frame.studio.domain.TextOverlay, size: ExportPlan.Size): TextureOverlay {
        val words = overlay.words
        val variants = (-1 until words.size).map { active ->
            CaptionSpans.build(words, overlay.captionStyle, overlay.color.toInt(), active, overlay.bold)
        }
        val base = (minOf(size.width, size.height) / 1080f).coerceAtLeast(0.3f)
        val scale = base * overlay.scale.coerceIn(0.3f, 4f)
        val visible = StaticOverlaySettings.Builder()
            .setScale(scale, scale)
            .setBackgroundFrameAnchor(overlay.x.coerceIn(0f, 1f) * 2 - 1, 1 - overlay.y.coerceIn(0f, 1f) * 2)
            .build()
        val hidden = StaticOverlaySettings.Builder().setAlphaScale(0f).setScale(scale, scale).build()
        val startUs = overlay.startMs * 1000
        val endUs = overlay.endMs * 1000
        return object : TextOverlay() {
            override fun getText(presentationTimeUs: Long): SpannableString {
                val rel = (presentationTimeUs - startUs) / 1000
                return variants[CaptionPlanner.activeIndex(words, rel) + 1]
            }

            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
                if (presentationTimeUs < startUs || presentationTimeUs >= endUs) hidden else visible
        }
    }

    private class ConstantSpeedProvider(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }
}
