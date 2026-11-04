package app.frame.studio.export

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.frame.studio.FrameApp
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Keyframe
import app.frame.studio.domain.OverlayClip
import app.frame.studio.domain.Project
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Picture-in-picture overlays with keyframes, through the real compositor (export and preview). */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class OverlayDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val frameApp = context.applicationContext as FrameApp

    private fun sample(name: String): String =
        Uri.fromFile(File(context.cacheDir, "ov_$name").also { File("/data/local/tmp/$name").copyTo(it, overwrite = true) }).toString()

    /** A solid red photo: easy to find in the exported frames. */
    private fun redPhoto(): String {
        val file = File(context.cacheDir, "ov_red.png")
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFF0000.toInt()) }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun project(): Project {
        val main = sample("sample.mp4")
        return Project(
            id = "ov", name = "Overlay", createdAtEpochMs = 0,
            videoClips = listOf(VideoClip(id = "m", sourceUri = main, sourceDurationMs = 4_000, trimEndMs = 4_000, width = 1280, height = 720)),
            overlays = listOf(
                // A red square that slides from the top-left to the bottom-right corner.
                OverlayClip(
                    id = "red", sourceUri = redPhoto(), sourceDurationMs = VideoClip.MAX_IMAGE_MS, trimEndMs = 3_000, startMs = 500,
                    width = 400, height = 400, isImage = true, scale = 0.3f,
                    keyframes = listOf(Keyframe(0, 0.2f, 0.2f, 0.3f), Keyframe(2_000, 0.8f, 0.8f, 0.3f)),
                ),
                // A video overlay with sound on a second lane.
                OverlayClip(
                    id = "pip", sourceUri = sample("sample.mp4"), sourceDurationMs = 4_000, trimEndMs = 2_000, startMs = 1_000,
                    width = 1280, height = 720, x = 0.75f, y = 0.25f, scale = 0.35f, opacity = 0.8f, volume = 0.5f,
                ),
            ),
            textOverlays = listOf(TextOverlay(id = "t", text = "On top", startMs = 0, endMs = 4_000, y = 0.9f)),
            aspect = CanvasAspect.R16_9,
            exportHeight = ExportHeight.SD_480,
        )
    }

    @Test
    fun exportsKeyframedOverlays() {
        assertNotNull("blank frame not prepared", CompositionFactory.blankFrameUri)
        val p = project()
        assertTrue(CompositionFactory.hasOverlays(p))
        val output = File(context.cacheDir, "overlay.mp4").apply { delete() }
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
        assertTrue("export timed out", done.await(5, TimeUnit.MINUTES))
        assertNull("export failed: ${error?.errorCodeName} ${error?.cause}", error)

        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(output.absolutePath)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertEquals(4_000.0, duration.toDouble(), 400.0)
            assertEquals("yes", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            // Early on the red square sits top-left; at the end of its path, bottom-right.
            val early = r.getFrameAtTime(600_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            val late = r.getFrameAtTime(2_450_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            fun isRed(b: Bitmap, fx: Float, fy: Float): Boolean {
                val c = b.getPixel((b.width * fx).toInt(), (b.height * fy).toInt())
                val red = (c shr 16) and 0xFF
                val green = (c shr 8) and 0xFF
                val blue = c and 0xFF
                return red > 180 && green < 90 && blue < 90
            }
            assertTrue("red overlay missing top-left at 0.6 s", isRed(early, 0.2f, 0.2f))
            assertTrue("red overlay should have moved bottom-right by 2.45 s", isRed(late, 0.8f, 0.8f) && !isRed(late, 0.2f, 0.2f))
            // Before the overlay starts the corner is the main video.
            val before = r.getFrameAtTime(100_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            assertTrue("overlay visible before its start", !isRed(before, 0.2f, 0.2f))
        } finally {
            r.release()
        }
    }

    @Test
    fun layeredPreviewPlays() {
        val p = project()
        val ready = CountDownLatch(1)
        var playerError: PlaybackException? = null
        var player: CompositionPlayer? = null
        Handler(Looper.getMainLooper()).post {
            val pl = CompositionPlayer.Builder(context).setVideoGraphFactory(MultipleInputVideoGraph.Factory()).build()
            player = pl
            pl.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) ready.countDown()
                }
                override fun onPlayerError(error: PlaybackException) {
                    playerError = error
                    ready.countDown()
                }
            })
            pl.setComposition(CompositionFactory.build(p, forExport = false))
            pl.prepare()
            pl.play()
        }
        assertTrue("layered preview never became ready", ready.await(60, TimeUnit.SECONDS))
        Thread.sleep(2_000)
        val position = java.util.concurrent.atomic.AtomicLong()
        val read = CountDownLatch(1)
        var err: PlaybackException? = null
        Handler(Looper.getMainLooper()).post {
            position.set(player?.currentPosition ?: 0)
            err = playerError
            player?.release()
            read.countDown()
        }
        read.await(10, TimeUnit.SECONDS)
        assertNull("layered preview failed: ${err?.errorCodeName} ${err?.cause}", err)
        assertTrue("layered preview did not advance (${position.get()} ms)", position.get() > 500)
    }
}
