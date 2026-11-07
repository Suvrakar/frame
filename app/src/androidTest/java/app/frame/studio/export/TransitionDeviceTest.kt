package app.frame.studio.export

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project
import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Every shader transition compiles and runs on the GPU, and none changes the movie's length. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class TransitionDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as FrameApp

    private fun sample(): String {
        val f = File(context.filesDir, "tr_sample.mp4")
        File("/data/local/tmp/sample.mp4").copyTo(f, overwrite = true)
        return Uri.fromFile(f).toString()
    }

    @Test
    fun everyShaderTransitionExports() {
        val types = TransitionType.entries.filter(TransitionPlan::usesShader)
        val uri = sample()
        val clips = (types + TransitionType.NONE).mapIndexed { i, t ->
            VideoClip(id = "c$i", sourceUri = uri, sourceDurationMs = 4_000, trimEndMs = 1_000, width = 1280, height = 720, transition = t, transitionMs = 600)
        }
        val p = Project(id = "tr", name = "Transitions", createdAtEpochMs = 0, videoClips = clips, aspect = CanvasAspect.R16_9, exportHeight = ExportHeight.SD_480)
        val out = File(context.cacheDir, "transitions.mp4").apply { delete() }
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
        assertTrue("export timed out", done.await(5, TimeUnit.MINUTES))
        assertNull("export failed: ${error?.errorCodeName} ${error?.cause}", error)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(out.absolutePath)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertEquals("length must not change", clips.size * 1_000.0, ms.toDouble(), 250.0)
            // Frames 150 ms after each cut (inside every incoming half) for a visual check.
            val dir = File(context.getExternalFilesDir(null), "transition_frames").apply { deleteRecursively(); mkdirs() }
            types.forEachIndexed { i, t ->
                val at = (i + 1) * 1_000_000L - 150_000L
                r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST)?.let { b ->
                    File(dir, "%02d_%s.jpg".format(i, t.name)).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                }
            }
            Log.i("TransitionTest", "exported ${ms} ms, frames in $dir")
        } finally {
            r.release()
            out.delete()
        }
    }
}
