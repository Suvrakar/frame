package app.frame.studio.captions

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionPart
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Speech to text with Android's **on-device** recogniser only: audio is streamed to the
 * system speech service on this phone, never to a server. Needs Android 13+ (audio-file input);
 * word timestamps come from Android 14+, otherwise words are spread over the speech chunk.
 */
class SpeechTranscriber(private val context: Context) {

    /** A recognised word; [offsetMs] is relative to the chunk start, or null when not reported. */
    data class Word(val text: String, val offsetMs: Long?)

    sealed interface ChunkResult {
        data class Words(val words: List<Word>) : ChunkResult
        data object NoSpeech : ChunkResult
        data class Error(val code: Int) : ChunkResult
    }

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** Recognises one chunk of mono 16-bit PCM-able audio ([samples] in -1..1). */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun recognize(samples: FloatArray, sampleRate: Int, locale: Locale = Locale.getDefault()): ChunkResult {
        val pipe = ParcelFileDescriptor.createPipe()
        val read = pipe[0]
        val write = pipe[1]
        val timeoutMs = samples.size * 1000L / sampleRate + 20_000
        return try {
            withTimeoutOrNull(timeoutMs) {
                withContext(Dispatchers.Main) { listen(read, write, samples, sampleRate, locale) }
            } ?: ChunkResult.Error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        } finally {
            runCatching { read.close() }
            runCatching { write.close() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun listen(
        read: ParcelFileDescriptor,
        write: ParcelFileDescriptor,
        samples: FloatArray,
        sampleRate: Int,
        locale: Locale,
    ): ChunkResult = suspendCancellableCoroutine { cont ->
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        fun finish(result: ChunkResult) {
            if (cont.isActive) cont.resume(result)
            recognizer.destroy()
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) = finish(parse(results))
            override fun onError(error: Int) = finish(
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ChunkResult.NoSpeech
                else ChunkResult.Error(error),
            )
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, sampleRate)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING, true)
            }
        }
        cont.invokeOnCancellation { runCatching { recognizer.cancel(); recognizer.destroy() } }
        recognizer.startListening(intent)
        // Stream the audio, then a little silence so the recogniser's end-pointer closes the session.
        Thread {
            runCatching {
                FileOutputStream(write.fileDescriptor).use { out ->
                    val bytes = ByteBuffer.allocate(4096 * 2).order(ByteOrder.LITTLE_ENDIAN)
                    val tail = sampleRate * 3 / 2
                    for (i in 0 until samples.size + tail) {
                        val v = if (i < samples.size) samples[i] else 0f
                        bytes.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
                        if (!bytes.hasRemaining()) {
                            out.write(bytes.array(), 0, bytes.position())
                            bytes.clear()
                        }
                    }
                    out.write(bytes.array(), 0, bytes.position())
                }
            }.onFailure { Log.w(TAG, "Audio stream closed early", it) }
            runCatching { write.close() }
        }.apply { name = "frame-speech-feed" }.start()
    }

    private fun parse(results: Bundle): ChunkResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val parts = results.getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS, RecognitionPart::class.java)
            if (!parts.isNullOrEmpty()) {
                val words = parts.flatMap { part ->
                    val text = part.formattedText ?: part.rawText
                    CaptionPlanner.tokens(text).mapIndexed { i, t -> Word(t, if (i == 0) part.timestampMillis else null) }
                }
                if (words.isNotEmpty()) return ChunkResult.Words(words)
            }
        }
        val best = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        val words = CaptionPlanner.tokens(best).map { Word(it, null) }
        return if (words.isEmpty()) ChunkResult.NoSpeech else ChunkResult.Words(words)
    }

    private companion object {
        const val TAG = "SpeechTranscriber"
    }
}
