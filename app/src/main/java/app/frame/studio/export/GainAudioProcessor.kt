package app.frame.studio.export

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Applies a constant volume plus linear fade in / fade out to 16-bit PCM.
 * One instance is created per clip, so the sample counter starts at the clip's first sample.
 */
@OptIn(UnstableApi::class)
class GainAudioProcessor(
    private val volume: Float,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
    private val durationMs: Long,
) : BaseAudioProcessor() {

    private var framesProcessed = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val isNoOp = volume == 1f && fadeInMs <= 0 && fadeOutMs <= 0
        return if (isNoOp) AudioProcessor.AudioFormat.NOT_SET else inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val format = inputAudioFormat
        val channels = format.channelCount.coerceAtLeast(1)
        val sampleRate = format.sampleRate.coerceAtLeast(1)
        val output = replaceOutputBuffer(remaining)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())
        var channel = 0
        var gain = gainForFrame(framesProcessed, sampleRate)
        while (input.remaining() >= 2) {
            val sample = input.short
            val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.putShort(scaled.toShort())
            channel++
            if (channel == channels) {
                channel = 0
                framesProcessed++
                gain = gainForFrame(framesProcessed, sampleRate)
            }
        }
        // Drop a dangling odd byte, never expected for 16-bit PCM.
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun gainForFrame(frame: Long, sampleRate: Int): Float =
        ExportPlan.gainAt(volume, fadeInMs, fadeOutMs, durationMs, frame * 1000 / sampleRate)

    override fun onFlush() {
        framesProcessed = 0
    }

    override fun onReset() {
        framesProcessed = 0
    }
}
