package app.frame.studio.export

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.CompositionPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.OverlayClip
import app.frame.studio.domain.Project
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.VideoClip
import app.frame.studio.ui.editor.PreviewController
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Regression (found on device): editing text or an overlay rebuilds the preview at the playhead;
 * with the playhead at the very end, Media3's audio graph hit a NullPointerException
 * (AudioGraphInputAudioSink.playToEndOfStream) and the preview showed ERROR_CODE_UNSPECIFIED.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PreviewAtEndDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sample(): String =
        Uri.fromFile(File(context.cacheDir, "end_sample.mp4").also { File("/data/local/tmp/sample.mp4").copyTo(it, overwrite = true) }).toString()

    /** A screenshot-like photo (the user's project was made of screenshots). */
    private fun photo(name: String, color: Int): String {
        val file = File(context.cacheDir, name)
        val bmp = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun project(withOverlay: Boolean, photos: Boolean = true): Project {
        val main = if (photos) photo("end_main.jpg", 0xFF223344.toInt()) else sample()
        val clip = if (photos) {
            VideoClip(id = "m", sourceUri = main, sourceDurationMs = VideoClip.MAX_IMAGE_MS, trimEndMs = 4_000, width = 1080, height = 2400, isImage = true)
        } else {
            VideoClip(id = "m", sourceUri = main, sourceDurationMs = 4_000, trimEndMs = 4_000, width = 1280, height = 720)
        }
        return Project(
            id = "end", name = "End", createdAtEpochMs = 0,
            videoClips = listOf(clip),
            overlays = if (withOverlay) listOf(
                OverlayClip(
                    id = "o", sourceUri = photo("end_overlay.jpg", 0xFF8844AA.toInt()), sourceDurationMs = VideoClip.MAX_IMAGE_MS,
                    trimEndMs = 4_000, startMs = 0, width = 1080, height = 2400, isImage = true, scale = 0.4f,
                ),
            ) else emptyList(),
            textOverlays = listOf(TextOverlay(id = "t", text = "Hello", startMs = 0, endMs = 4_000)),
            aspect = CanvasAspect.R9_16,
            exportHeight = ExportHeight.SD_480,
        )
    }

    /** What PreviewController does after an edit: build, seek to the playhead, prepare paused. */
    private fun prepareAt(p: Project, positionMs: Long): PlaybackException? {
        val settled = CountDownLatch(1)
        var error: PlaybackException? = null
        var player: CompositionPlayer? = null
        Handler(Looper.getMainLooper()).post {
            val layered = CompositionFactory.hasOverlays(p)
            val pl = CompositionPlayer.Builder(context)
                .apply { if (layered) setVideoGraphFactory(MultipleInputVideoGraph.Factory()) }
                .build()
            player = pl
            pl.addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    error = e
                    settled.countDown()
                }
            })
            pl.setComposition(CompositionFactory.build(p, forExport = false))
            pl.seekTo(PreviewController.safeResumePosition(positionMs, 4_000))
            pl.prepare()
        }
        // Errors surface within a second or two of preparing; give it a generous window.
        settled.await(6, TimeUnit.SECONDS)
        val released = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            player?.release()
            released.countDown()
        }
        released.await(5, TimeUnit.SECONDS)
        return error
    }

    @Test
    fun overlayProjectPreparesAtTheEnd() {
        assertNotNull("blank frame not prepared", CompositionFactory.blankFrameUri)
        val e = prepareAt(project(withOverlay = true), 3_999)
        assertNull("preview failed at the end: ${e?.errorCodeName} ${e?.cause}", e)
    }

    @Test
    fun videoOverlayProjectPreparesAtTheEnd() {
        val e = prepareAt(project(withOverlay = true, photos = false), 3_999)
        assertNull("preview failed at the end: ${e?.errorCodeName} ${e?.cause}", e)
    }

    @Test
    fun textProjectPreparesAtTheEnd() {
        val e = prepareAt(project(withOverlay = false), 3_999)
        assertNull("preview failed at the end: ${e?.errorCodeName} ${e?.cause}", e)
    }

    @Test
    fun safeResumePositionStaysAwayFromTheEnd() {
        assertTrue(PreviewController.safeResumePosition(3_999, 4_000) <= 4_000 - PreviewController.END_GUARD_MS)
        assertTrue(PreviewController.safeResumePosition(1_000, 4_000) == 1_000L)
        assertTrue(PreviewController.safeResumePosition(50, 100) == 0L)
    }
}
