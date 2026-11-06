package app.frame.studio.media

import app.frame.studio.telemetry.Telemetry
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

/**
 * Small 540p H.264 stand-ins for heavy videos, used by the editor preview only (export always reads
 * the original). Proxies are made one at a time in the background; any failure is logged and the
 * original keeps being used.
 */
@OptIn(UnstableApi::class)
class ProxyStore(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "proxies")
    private val main = Handler(Looper.getMainLooper())

    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    /** Sources queued or being converted. */
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    private val _readyVersion = MutableStateFlow(0)
    /** Bumps whenever a proxy finishes. */
    val readyVersion: StateFlow<Int> = _readyVersion.asStateFlow()

    // Only touched on the main thread.
    private val queue = ArrayDeque<String>()
    private var running = false
    private var cleaned = false

    /** The finished proxy for [uri], or null (never a half-written file). */
    fun proxyFor(uri: String): String? = try {
        val file = fileFor(uri)
        if (file.isFile && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            Uri.fromFile(file).toString()
        } else null
    } catch (e: Exception) {
        Log.w(TAG, "proxyFor failed", e)
        null
    }

    /** Queues a proxy for [uri] unless it exists or is already queued. */
    fun request(uri: String) {
        try {
            if (!_pending.updateIfAbsent(uri)) return
            main.post {
                try {
                    queue.addLast(uri)
                    pump()
                } catch (e: Exception) {
                    Log.w(TAG, "request failed", e)
                    finish(uri)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "request failed", e)
        }
    }

    private fun MutableStateFlow<Set<String>>.updateIfAbsent(uri: String): Boolean {
        var added = false
        update { if (uri in it) { added = false; it } else { added = true; it + uri } }
        return added
    }

    private fun pump() {
        if (running) return
        val uri = queue.removeFirstOrNull() ?: return
        running = true
        scope.launch(Dispatchers.IO) {
            val ready = try {
                prepare(uri)
            } catch (e: Exception) {
                Log.w(TAG, "prepare failed", e)
                false
            }
            main.post {
                if (ready) {
                    _readyVersion.update { it + 1 }
                    finish(uri)
                } else {
                    startTransform(uri)
                }
            }
        }
    }

    /** IO-side: set up the folder, drop stale partials. Returns true if the proxy already exists. */
    private fun prepare(uri: String): Boolean {
        dir.mkdirs()
        if (!cleaned) {
            // A partial file left by a process that died mid-transcode is never a valid proxy.
            dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }
            cleaned = true
        }
        val final = fileFor(uri)
        if (final.isFile && final.length() > 0) return true
        tmpFor(uri).delete()
        return false
    }

    private fun startTransform(uri: String) {
        try {
            val tmp = tmpFor(uri)
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(uri)))
                .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(SHORT_SIDE))))
                .build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setAssetLoaderFactory(DefaultAssetLoaderFactory(context, decoderFactory(), Clock.DEFAULT, null))
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setEnableFallback(true)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(BITRATE).build())
                        .build(),
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        complete(uri, tmp, true)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Log.w(TAG, "Proxy failed for $uri", exportException)
                        Telemetry.nonFatal("proxy", exportException)
                        complete(uri, tmp, false)
                    }
                })
                .build()
            transformer.start(edited, tmp.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start proxy for $uri", e)
            complete(uri, tmpFor(uri), false)
        }
    }

    private fun complete(uri: String, tmp: File, ok: Boolean) {
        scope.launch(Dispatchers.IO) {
            val success = try {
                if (ok && tmp.length() > 0) tmp.renameTo(fileFor(uri)) else false
            } catch (e: Exception) {
                false
            }
            if (!success) tmp.delete()
            try { enforceCap(keep = fileFor(uri)) } catch (e: Exception) { Log.w(TAG, "cap failed", e) }
            main.post {
                if (success) _readyVersion.update { it + 1 }
                finish(uri)
            }
        }
    }

    private fun finish(uri: String) {
        _pending.update { it - uri }
        running = false
        pump()
    }

    private fun enforceCap(keep: File) {
        val files = dir.listFiles { f -> f.name.endsWith(".mp4") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_BYTES) break
            if (f == keep) continue
            total -= f.length()
            f.delete()
        }
    }

    private fun fileFor(uri: String) = File(dir, name(uri) + ".mp4")
    private fun tmpFor(uri: String) = File(dir, name(uri) + ".mp4.tmp")

    private fun decoderFactory() = DefaultDecoderFactory.Builder(context)
        .setEnableDecoderFallback(true)
        .setMediaCodecSelector { mimeType, secure, tunneling ->
            MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
                .sortedBy { if (it.name.contains("goldfish", ignoreCase = true)) 1 else 0 }
        }
        .build()

    companion object {
        private const val TAG = "ProxyStore"
        const val SHORT_SIDE = 540
        const val BITRATE = 3_000_000
        const val MAX_BYTES = 1_500_000_000L

        /** Stable file name stem for a source: sha1 of its uri. */
        fun name(uri: String): String =
            MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
