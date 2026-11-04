package app.frame.studio.captions

import android.content.Context
import android.net.Uri
import android.os.Build
import android.speech.SpeechRecognizer
import app.frame.studio.audio.AudioPcmReader
import app.frame.studio.domain.AudioKind
import app.frame.studio.domain.Project
import app.frame.studio.domain.VideoClip
import app.frame.studio.timeline.AudioLayout
import app.frame.studio.timeline.TimelineMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Transcribes the speech in a project's clips and voiceovers onto the timeline, on the device. */
class AutoCaptioner(private val context: Context) {

    sealed interface Result {
        data class Words(val words: List<TimedWord>) : Result
        data object NoSpeech : Result
        data class Unavailable(val message: String) : Result
        data object NeedsPermission : Result
        data class Failed(val message: String) : Result
    }

    private val transcriber = SpeechTranscriber(context)

    val isSupported: Boolean get() = transcriber.isSupported

    private class Source(val uri: String, val mediaStartMs: Long, val mediaEndMs: Long, val timelineStartMs: Long, val speed: Float)

    suspend fun transcribe(project: Project, onProgress: (Float) -> Unit): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return Result.Unavailable("Auto captions need Android 13 or newer. You can still type captions and Frame will animate them.")
        }
        if (!transcriber.isSupported) {
            return Result.Unavailable("This phone has no on-device speech engine. You can still type captions and Frame will animate them.")
        }
        val sources = sources(project)
        if (sources.isEmpty()) return Result.NoSpeech

        val words = mutableListOf<TimedWord>()
        val totalMs = sources.sumOf { it.mediaEndMs - it.mediaStartMs }.coerceAtLeast(1)
        var doneMs = 0L
        for (src in sources) {
            coroutineContext.ensureActive()
            val pcm = withContext(Dispatchers.IO) {
                AudioPcmReader.readMono(context, Uri.parse(src.uri), src.mediaStartMs, src.mediaEndMs, SPEECH_RATE)
            }
            if (pcm != null) {
                val chunks = withContext(Dispatchers.Default) { CaptionPlanner.speechChunks(pcm.samples, pcm.sampleRate) }
                for ((cs, ce) in chunks) {
                    coroutineContext.ensureActive()
                    val from = (cs * pcm.sampleRate / 1000).toInt().coerceIn(0, pcm.samples.size)
                    val to = (ce * pcm.sampleRate / 1000).toInt().coerceIn(from, pcm.samples.size)
                    when (val r = transcriber.recognize(pcm.samples.copyOfRange(from, to), pcm.sampleRate)) {
                        is SpeechTranscriber.ChunkResult.Words -> {
                            CaptionPlanner.placeWords(r.words.map { it.text to it.offsetMs }, cs, ce).forEach { w ->
                                fun toTimeline(mediaOffset: Long) = src.timelineStartMs + (mediaOffset / src.speed).toLong()
                                words += TimedWord(w.text, toTimeline(w.startMs), toTimeline(w.endMs))
                            }
                        }
                        SpeechTranscriber.ChunkResult.NoSpeech -> Unit
                        is SpeechTranscriber.ChunkResult.Error -> when (r.code) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> return Result.NeedsPermission
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                                return Result.Unavailable(
                                    "Your phone's on-device speech pack for this language isn't installed. " +
                                        "Add it in Settings › System › Languages › Speech, or type captions instead.",
                                )
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> Unit
                            else -> if (words.isEmpty()) return Result.Failed("The speech engine stopped (code ${r.code}). Try again, or type captions.")
                        }
                    }
                    onProgress(((doneMs + ce).toFloat() / totalMs).coerceIn(0f, 1f))
                }
            }
            doneMs += src.mediaEndMs - src.mediaStartMs
            onProgress((doneMs.toFloat() / totalMs).coerceIn(0f, 1f))
        }
        return if (words.isEmpty()) Result.NoSpeech else Result.Words(words.sortedBy { it.startMs })
    }

    private fun sources(project: Project): List<Source> {
        val starts = TimelineMath.videoStarts(project.videoClips)
        val clips = project.videoClips.mapIndexedNotNull { i, c ->
            if (c.isImage || c.volume <= 0f) null
            else Source(c.sourceUri, c.trimStartMs, c.trimEndMs, starts[i], c.speed.coerceIn(VideoClip.MIN_SPEED, VideoClip.MAX_SPEED))
        }
        val voice = AudioLayout.normalize(project.audioClips)
            .filter { it.kind == AudioKind.VOICE && it.volume > 0f }
            .map { Source(it.sourceUri, it.trimStartMs, it.trimEndMs, it.startMs.coerceAtLeast(0), 1f) }
        return clips + voice
    }

    private companion object {
        const val SPEECH_RATE = 16_000
    }
}
