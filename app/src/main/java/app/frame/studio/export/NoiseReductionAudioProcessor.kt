package app.frame.studio.export

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import app.frame.studio.audio.SpectralDenoiser
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Voice noise reduction for 16-bit PCM (any channel count / sample rate), one [SpectralDenoiser]
 * per channel. The denoiser's fixed latency is removed (its first output is dropped and the tail
 * flushed at end of stream), so audio stays in sync with the picture and the clip keeps its
 * exact length.
 */
@OptIn(UnstableApi::class)
class NoiseReductionAudioProcessor(private val strength: Float) : BaseAudioProcessor() {
    private var channels = 1
    private var denoisers: List<SpectralDenoiser> = emptyList()
    private var inputFrames = 0L
    private var outputFrames = 0L
    private var toDrop = SpectralDenoiser.LATENCY

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return if (strength <= 0f) AudioProcessor.AudioFormat.NOT_SET else inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        ensureDenoisers()
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val frames = input.remaining() / (2 * channels)
        val planes = Array(channels) { FloatArray(frames) }
        for (f in 0 until frames) for (c in 0 until channels) planes[c][f] = input.short / 32768f
        inputBuffer.position(inputBuffer.limit())
        inputFrames += frames
        emit(Array(channels) { denoisers[it].process(planes[it]) })
    }

    override fun onQueueEndOfStream() {
        if (denoisers.isEmpty()) return
        // Push silence through to release the last real samples, then trim to the input length.
        val pad = SpectralDenoiser.N
        emit(Array(channels) { denoisers[it].process(FloatArray(pad)) })
    }

    private fun emit(planes: Array<FloatArray>) {
        var available = planes.minOf { it.size }
        var start = 0
        if (toDrop > 0) {
            val d = minOf(toDrop, available)
            toDrop -= d
            start = d
            available -= d
        }
        val allowed = (inputFrames - outputFrames).coerceAtLeast(0).toInt()
        val count = minOf(available, allowed)
        if (count <= 0) return
        val out = replaceOutputBuffer(count * channels * 2).order(ByteOrder.nativeOrder())
        for (f in start until start + count) for (c in 0 until channels) {
            val v = (planes[c][f] * 32768f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out.putShort(v.toShort())
        }
        out.flip()
        outputFrames += count
    }

    private fun ensureDenoisers() {
        if (denoisers.isNotEmpty()) return
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        denoisers = List(channels) { SpectralDenoiser(strength, inputAudioFormat.sampleRate.coerceAtLeast(8_000)) }
    }

    private fun clear() {
        denoisers = emptyList()
        inputFrames = 0
        outputFrames = 0
        toDrop = SpectralDenoiser.LATENCY
    }

    override fun onFlush() = clear()
    override fun onReset() = clear()
}
