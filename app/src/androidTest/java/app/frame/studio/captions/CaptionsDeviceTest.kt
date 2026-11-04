package app.frame.studio.captions

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.frame.studio.FrameApp
import app.frame.studio.domain.CaptionStyle
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project
import app.frame.studio.domain.VideoClip
import app.frame.studio.export.CompositionFactory
import app.frame.studio.export.ExportManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class CaptionsDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val frameApp = context.applicationContext as FrameApp

    private fun sample(name: String): File =
        File(context.cacheDir, name).also { File("/data/local/tmp/$name").copyTo(it, overwrite = true) }

    private fun project(): Project {
        val video = sample("sample.mp4")
        return Project(
            id = "captions", name = "Captions", createdAtEpochMs = 0,
            videoClips = listOf(
                VideoClip(id = "v", sourceUri = android.net.Uri.fromFile(video).toString(), sourceDurationMs = 4_000, trimEndMs = 4_000, width = 1280, height = 720),
            ),
            exportHeight = ExportHeight.HD_720,
        )
    }

    @Test
    fun exportsEveryCaptionStyle() {
        var n = 0
        val words = CaptionPlanner.evenWords("Frame makes every word pop on screen today", 100, 3_800)
        val overlays = CaptionStyle.entries.flatMapIndexed { i, style ->
            CaptionPlanner.toOverlays(words, style, 4_000, { "c${n++}" }, y = 0.2f + i * 0.2f)
        }
        val p = project().copy(textOverlays = overlays)
        val output = File(context.cacheDir, "captions.mp4").apply { delete() }
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
            ExportManager.newTransformer(context, frameApp.device, p, listener).start(CompositionFactory.build(p), output.absolutePath)
        }
        assertTrue("export timed out", done.await(4, TimeUnit.MINUTES))
        assertNull("export failed: ${error?.errorCodeName} ${error?.cause}", error)
        assertTrue(output.length() > 10_000)
    }

    /** Emulators usually lack an on-device speech engine; the flow must still end cleanly. */
    @Test
    fun autoCaptionsFinishCleanly() = runBlocking {
        val captioner = AutoCaptioner(context)
        val result = withTimeout(150_000) { captioner.transcribe(project()) {} }
        Log.i("FramePerf", "captions.supported=${captioner.isSupported} result=${result::class.simpleName} $result")
        if (!captioner.isSupported) assertTrue(result is AutoCaptioner.Result.Unavailable)
    }
}
