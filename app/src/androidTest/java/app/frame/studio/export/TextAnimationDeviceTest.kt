package app.frame.studio.export

import android.graphics.Bitmap
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
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project
import app.frame.studio.domain.TextAnimation
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Every text animation renders in a real export (frames saved mid-entrance for a visual check). */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class TextAnimationDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as FrameApp

    @Test
    fun everyTextAnimationExports() {
        val f = File(context.filesDir, "ta_sample.mp4").also { File("/data/local/tmp/sample.mp4").copyTo(it, overwrite = true) }
        val uri = Uri.fromFile(f).toString()
        val clips = (0 until 3).map { VideoClip(id = "c$it", sourceUri = uri, sourceDurationMs = 4_000, trimEndMs = 4_000, width = 1280, height = 720) }
        val anims = TextAnimation.entries
        val texts = anims.mapIndexed { i, a ->
            TextOverlay(id = "t$i", text = a.label.uppercase(), startMs = i * 1_000L, endMs = i * 1_000L + 1_000, y = 0.5f, scale = 1.6f, animIn = a, animOut = TextAnimation.FADE, animMs = 400)
        }
        val p = Project(id = "ta", name = "Text", createdAtEpochMs = 0, videoClips = clips, textOverlays = texts, aspect = CanvasAspect.R16_9, exportHeight = ExportHeight.SD_480)
        val out = File(context.cacheDir, "text_anim.mp4").apply { delete() }
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
            val dir = File(context.getExternalFilesDir(null), "text_frames").apply { deleteRecursively(); mkdirs() }
            anims.forEachIndexed { i, a ->
                listOf(200L, 600L).forEach { off ->
                    r.getFrameAtTime((i * 1_000L + off) * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { b ->
                        File(dir, "%02d_%s_%d.jpg".format(i, a.name, off)).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                    }
                }
            }
        } finally {
            r.release()
            out.delete()
        }
    }
}
