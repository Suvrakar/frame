package app.frame.studio.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.util.Log
import java.nio.ByteOrder

/**
 * Decodes part of an audio (or video) file to mono floats at [TARGET_RATE]. Low-rate mono is
 * plenty for beat tracking and keeps memory and CPU tiny, even for long songs on budget phones.
 */
object AudioPcmReader {

    const val TARGET_RATE = 11_025
    private const val KEY_ENCODER_DELAY = "encoder-delay"

    /** Priming frames skipped by the last read (for diagnostics). */
    @Volatile
    var lastEncoderDelay = 0
        private set
    private const val TAG = "AudioPcmReader"
    private const val TIMEOUT_US = 10_000L

    class Pcm(val samples: FloatArray, val sampleRate: Int, val startMs: Long)

    fun readMono(context: Context, uri: Uri, startMs: Long, endMs: Long, targetRate: Int = TARGET_RATE): Pcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val startUs = startMs * 1000
            val endUs = endMs * 1000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            // AAC starts with encoder "priming" silence (usually 2112 samples). Some decoders don't
            // strip it, which would put every detected beat ~48 ms late. Skip it when the file says.
            var primingFrames = if (startMs <= 0 && format.containsKey(KEY_ENCODER_DELAY)) {
                format.getInteger(KEY_ENCODER_DELAY).coerceIn(0, 8192)
            } else 0
            lastEncoderDelay = primingFrames
            var sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var floatPcm = false
            val out = FloatBuilder(((endMs - startMs).coerceAtLeast(1000) * targetRate / 1000).toInt() + targetRate)
            var step = sourceRate.toFloat() / targetRate
            var acc = 0f
            var sum = 0f
            var count = 0
            var inputDone = false
            val info = MediaCodec.BufferInfo()

            while (true) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > endUs) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat
                        sourceRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        floatPcm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        step = sourceRate.toFloat() / targetRate
                    }
                    outIndex >= 0 -> {
                        val buffer = decoder.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val bytesPerSample = if (floatPcm) 4 else 2
                        val frames = info.size / (bytesPerSample * channels)
                        // Skip audio decoded before the requested start (seek lands on a sync sample).
                        val skip = if (info.presentationTimeUs < startUs) {
                            (((startUs - info.presentationTimeUs) * sourceRate) / 1_000_000).toInt().coerceAtMost(frames)
                        } else 0
                        for (frame in 0 until frames) {
                            var mono = 0f
                            for (c in 0 until channels) {
                                mono += if (floatPcm) buffer.float else buffer.short / 32768f
                            }
                            if (frame < skip) continue
                            if (primingFrames > 0) {
                                primingFrames--
                                continue
                            }
                            sum += mono / channels
                            count++
                            acc += 1f
                            if (acc >= step) {
                                out.add(sum / count)
                                acc -= step
                                sum = 0f
                                count = 0
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        if (info.presentationTimeUs > endUs) break
                    }
                }
            }
            return Pcm(out.toArray(), targetRate, startMs)
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode $uri", e)
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private class FloatBuilder(capacity: Int) {
        private var data = FloatArray(capacity.coerceAtLeast(1024))
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }
}
