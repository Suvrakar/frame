package app.frame.studio.export

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.frame.studio.FrameApp
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.OverlayClip
import app.frame.studio.domain.Project
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exports with overlays go through ExportManager, which retries once when Media3's multi-input
 * compositor hits its intermittent race (DefaultVideoCompositor.queueInputTexture). Three exports
 * in a row must all finish.
 */
@RunWith(AndroidJUnit4::class)
class ExportRetryDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val frameApp = context.applicationContext as FrameApp

    private fun photo(name: String, color: Int): String {
        val file = File(context.cacheDir, name)
        val bmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun project(): Project {
        val video = Uri.fromFile(File(context.cacheDir, "retry_sample.mp4").also { File("/data/local/tmp/sample.mp4").copyTo(it, overwrite = true) }).toString()
        return Project(
            id = "retry", name = "Retry", createdAtEpochMs = 0,
            videoClips = listOf(
                VideoClip(id = "a", sourceUri = photo("retry_a.jpg", 0xFF334455.toInt()), sourceDurationMs = VideoClip.MAX_IMAGE_MS, trimEndMs = 2_000, width = 720, height = 1280, isImage = true),
                VideoClip(id = "b", sourceUri = video, sourceDurationMs = 4_000, trimEndMs = 2_000, width = 1280, height = 720),
            ),
            overlays = listOf(
                OverlayClip(id = "o", sourceUri = photo("retry_o.jpg", 0xFFAA4488.toInt()), sourceDurationMs = VideoClip.MAX_IMAGE_MS, trimEndMs = 2_500, startMs = 500, width = 720, height = 1280, isImage = true, scale = 0.4f),
            ),
            textOverlays = listOf(TextOverlay(id = "t", text = "Retry", startMs = 0, endMs = 4_000)),
            aspect = CanvasAspect.R9_16,
            exportHeight = ExportHeight.SD_480,
        )
    }

    @Test
    fun overlayExportsFinishThreeTimesInARow() {
        assertNotNull("blank frame not prepared", CompositionFactory.blankFrameUri)
        val main = Handler(Looper.getMainLooper())
        repeat(3) { run ->
            main.post {
                frameApp.exportManager.acknowledge()
                frameApp.exportManager.start(project(), saveToGallery = false)
            }
            val deadline = System.currentTimeMillis() + 180_000
            var state: ExportState
            do {
                Thread.sleep(500)
                state = frameApp.exportManager.state.value
            } while ((state is ExportState.Running || state is ExportState.Saving || state is ExportState.Idle) && System.currentTimeMillis() < deadline)
            assertTrue("export ${run + 1} did not finish: $state", state is ExportState.Done)
            (state as ExportState.Done).record.filePath.let { File(it).delete() }
        }
        main.post { frameApp.exportManager.acknowledge() }
    }
}
