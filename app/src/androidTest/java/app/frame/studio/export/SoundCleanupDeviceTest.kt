package app.frame.studio.export

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.frame.studio.FrameApp
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.AudioKind
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project
import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Noise reduction and ducking run in a real export without changing its length. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SoundCleanupDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as FrameApp

    private fun copy(name: String) = Uri.fromFile(File(context.filesDir, "sc_$name").also { File("/data/local/tmp/$name").copyTo(it, overwrite = true) }).toString()

    @Test
    fun cleanedAndDuckedExportKeepsItsLength() {
        val video = copy("sample.mp4")
        val music = copy("sample.m4a")
        val p = Project(
            id = "sc", name = "Sound", createdAtEpochMs = 0,
            videoClips = listOf(VideoClip(id = "v", sourceUri = video, sourceDurationMs = 4_000, trimEndMs = 4_000, width = 1280, height = 720, noiseReduction = 0.75f)),
            audioClips = listOf(
                AudioClip(id = "m", sourceUri = music, sourceDurationMs = 4_000, trimEndMs = 4_000, startMs = 0, kind = AudioKind.MUSIC),
                AudioClip(id = "vo", sourceUri = music, sourceDurationMs = 4_000, trimEndMs = 1_500, startMs = 1_000, kind = AudioKind.VOICE, noiseReduction = 1f),
            ),
            duckMusic = true,
            aspect = CanvasAspect.R16_9,
            exportHeight = ExportHeight.SD_480,
        )
        val out = File(context.cacheDir, "sound.mp4").apply { delete() }
        val done = CountDownLatch(1)
        var error: ExportException? = null
        Handler(Looper.getMainLooper()).post {
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    error = exportException
                    done.countDown()
                }
            }
            ExportManager.newTransformer(context, app.device, p, listener).start(CompositionFactory.build(p), out.absolutePath)
        }
        assertTrue("export timed out", done.await(3, TimeUnit.MINUTES))
        assertNull("export failed: ${error?.errorCodeName} ${error?.cause}", error)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(out.absolutePath)
            assertEquals("yes", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            assertEquals(4_000.0, r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toDouble(), 250.0)
        } finally {
            r.release()
            out.delete()
        }
    }
}
