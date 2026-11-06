package app.frame.studio.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log

/**
 * Decides "can this phone decode this video?" the way the editor will: by decoding its first frame
 * with MediaCodec (hardware decoders first, then software). Declared decoder limits — also used by
 * MediaMetadataRetriever — under-report on some phones (a Snapdragon 685 declares 1920x1920 for
 * H.264 yet decodes 4K), so this is the ground truth before refusing a file. Never throws.
 */
object DecodeProbe {
    private const val TAG = "DecodeProbe"
    private const val TIMEOUT_MS = 2_500L
    private const val DEQUEUE_US = 10_000L

    fun decodesFirstFrame(context: Context, uri: Uri): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return false
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
            for (name in decoderNames(mime)) {
                extractor.unselectTrack(track)
                extractor.selectTrack(track)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                if (tryDecoder(name, format, extractor)) return true
            }
            return false
        } catch (e: Exception) {
            Log.w(TAG, "Probe failed for $uri", e)
            return false
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** Decoders for [mime], hardware first; aliases skipped. */
    private fun decoderNames(mime: String): List<String> = try {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }
            .filter { android.os.Build.VERSION.SDK_INT < 29 || !it.isAlias }
            .sortedBy { if (android.os.Build.VERSION.SDK_INT >= 29 && it.isHardwareAccelerated) 0 else 1 }
            .map { it.name }
    } catch (e: Exception) {
        emptyList()
    }

    private fun tryDecoder(name: String, format: MediaFormat, extractor: MediaExtractor): Boolean {
        var codec: MediaCodec? = null
        try {
            codec = MediaCodec.createByCodecName(name)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            var inputDone = false
            while (SystemClock.uptimeMillis() < deadline) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(DEQUEUE_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index) ?: return false
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, DEQUEUE_US)
                if (out >= 0) {
                    codec.releaseOutputBuffer(out, false)
                    if (info.size > 0 || (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) return true
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return false
                }
            }
            return false
        } catch (e: Exception) {
            Log.i(TAG, "$name can't decode this file: ${e.javaClass.simpleName}")
            return false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
        }
    }
}
