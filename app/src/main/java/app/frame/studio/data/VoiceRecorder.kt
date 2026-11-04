package app.frame.studio.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File

/** Records voiceovers to AAC/M4A files in app storage. Caller must hold RECORD_AUDIO. */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun elapsedMs(): Long = if (recorder != null) SystemClock.elapsedRealtime() - startedAt else 0

    fun start(): Boolean {
        if (recorder != null) return true
        val dir = File(context.filesDir, "voice").apply { mkdirs() }
        val out = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not start recording", e)
            r.release()
            out.delete()
            false
        }
    }

    /** Stops and returns the recorded file, or null if nothing usable was captured. */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val out = file
        file = null
        val ok = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            // Thrown when stop() is called right after start() with no data.
            false
        } finally {
            r.release()
        }
        if (!ok) {
            out?.delete()
            return null
        }
        return out
    }

    fun cancel() {
        stop()?.delete()
    }

    companion object {
        private const val TAG = "VoiceRecorder"
    }
}
