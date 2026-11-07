package app.frame.studio.export

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import app.frame.studio.telemetry.Telemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Makes (and caches) the upload-ready copy of an export for a [ShareTarget] — see [SharePlan].
 * The original export is never changed; when it's already ideal, it is shared as is.
 */
@OptIn(UnstableApi::class)
class ShareOptimizer(private val context: Context) {
    private val dir = File(context.filesDir, "share")

    /** Size, frame rate, bitrate and length of [file]; null if unreadable. */
    suspend fun facts(file: File): VideoFacts? = withContext(Dispatchers.IO) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?: return@withContext null
            val f = ex.getTrackFormat(track)
            val durationUs = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            val fps = if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) runCatching { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrElse { f.getFloat(MediaFormat.KEY_FRAME_RATE) } else 30f
            val ms = (durationUs / 1000).coerceAtLeast(1)
            VideoFacts(
                width = f.getInteger(MediaFormat.KEY_WIDTH),
                height = f.getInteger(MediaFormat.KEY_HEIGHT),
                fps = fps,
                bitrate = (file.length() * 8 * 1000 / ms).toInt(),
                durationMs = ms,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read $file", e)
            null
        } finally {
            ex.release()
        }
    }

    /** The file to hand to [target]: a cached upload-ready copy, or [source] itself. */
    suspend fun prepare(source: File, target: ShareTarget, onProgress: (Int) -> Unit): File {
        val facts = facts(source) ?: return source
        val spec = SharePlan.spec(facts, target) ?: return source
        val out = File(dir, "${source.nameWithoutExtension}_${target.name.lowercase()}.mp4")
        if (out.exists() && out.length() > 0 && out.lastModified() >= source.lastModified()) return out
        dir.mkdirs()
        val tmp = File(dir, out.name + ".tmp").apply { delete() }
        return try {
            transcode(source, tmp, spec, onProgress)
            if (!tmp.renameTo(out)) tmp.copyTo(out, overwrite = true).also { tmp.delete() }
            Telemetry.event("share_optimized", "target" to target.name.lowercase(), "height" to spec.height.coerceAtMost(spec.width))
            out
        } catch (e: kotlinx.coroutines.CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            // Never block sharing: fall back to the original file.
            Log.w(TAG, "Upload-ready copy failed; sharing the original", e)
            Telemetry.nonFatal("share_optimize", e)
            tmp.delete()
            source
        }
    }

    private suspend fun transcode(source: File, out: File, spec: UploadSpec, onProgress: (Int) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        suspendCancellableCoroutine { cont ->
            var transformer: Transformer? = null
            val poll = object : Runnable {
                val holder = ProgressHolder()
                override fun run() {
                    val t = transformer ?: return
                    if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                    main.postDelayed(this, 250)
                }
            }
            main.post {
                val effects = buildList {
                    add(Presentation.createForWidthAndHeight(spec.width, spec.height, Presentation.LAYOUT_SCALE_TO_FIT))
                    add(FrameDropEffect.createDefaultFrameDropEffect(spec.fps.toFloat()))
                }
                val item = EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(source)))
                    .setEffects(Effects(emptyList(), effects))
                    .build()
                val t = Transformer.Builder(context)
                    // Same decoder fallbacks as exports: budget chips under-report 4K support.
                    .setAssetLoaderFactory(
                        androidx.media3.transformer.DefaultAssetLoaderFactory(
                            context, ExportManager.robustDecoderFactory(context), androidx.media3.common.util.Clock.DEFAULT, null,
                        ),
                    )
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setEnableFallback(true)
                            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(spec.bitrate).build())
                            .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(spec.audioKbps * 1000).build())
                            .build(),
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            main.removeCallbacks(poll)
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            main.removeCallbacks(poll)
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                transformer = t
                t.start(item, out.absolutePath)
                main.post(poll)
            }
            cont.invokeOnCancellation {
                main.post {
                    main.removeCallbacks(poll)
                    runCatching { transformer?.cancel() }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ShareOptimizer"
    }
}
