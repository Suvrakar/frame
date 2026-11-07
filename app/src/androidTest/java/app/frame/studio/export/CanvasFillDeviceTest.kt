package app.frame.studio.export

import android.graphics.Bitmap
import android.graphics.Color
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
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.CanvasFill
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

/** A 16:9 clip on a 9:16 canvas: Blur fills the bars, Fill crops, Fit keeps black bars. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class CanvasFillDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as FrameApp

    private fun export(fill: CanvasFill): Bitmap {
        val f = File(context.filesDir, "cf_sample.mp4").also { File("/data/local/tmp/sample.mp4").copyTo(it, overwrite = true) }
        val p = Project(
            id = "cf", name = "Fill", createdAtEpochMs = 0,
            videoClips = listOf(VideoClip(id = "v", sourceUri = Uri.fromFile(f).toString(), sourceDurationMs = 4_000, trimEndMs = 2_000, width = 1280, height = 720)),
            aspect = CanvasAspect.R9_16, exportHeight = ExportHeight.SD_480, canvasFill = fill,
        )
        val out = File(context.cacheDir, "fill_$fill.mp4").apply { delete() }
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
        assertNull("$fill export failed: ${error?.errorCodeName} ${error?.cause}", error)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(out.absolutePath)
            val frame = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            val dir = File(context.getExternalFilesDir(null), "fill_frames").apply { mkdirs() }
            File(dir, "$fill.jpg").outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            return frame
        } finally {
            r.release()
            out.delete()
        }
    }

    /** Average brightness of the strip above where a fitted 16:9 clip would sit. */
    private fun topBrightness(b: Bitmap): Float {
        var sum = 0f
        var n = 0
        for (y in b.height / 20 until b.height / 5 step 4) for (x in 0 until b.width step 4) {
            val c = b.getPixel(x, y)
            sum += (Color.red(c) + Color.green(c) + Color.blue(c)) / 3f
            n++
        }
        return sum / n
    }

    @Test
    fun fillModesPlaceTheClipAsDescribed() {
        val fit = export(CanvasFill.FIT)
        val blur = export(CanvasFill.BLUR)
        val fill = export(CanvasFill.FILL)
        assertTrue("portrait canvas", fit.height > fit.width)
        assertEquals(fit.width, blur.width)
        assertTrue("Fit keeps black bars (${topBrightness(fit)})", topBrightness(fit) < 12f)
        assertTrue("Blur fills the bars (${topBrightness(blur)})", topBrightness(blur) > 25f)
        assertTrue("Fill covers the frame (${topBrightness(fill)})", topBrightness(fill) > 25f)
    }
}
