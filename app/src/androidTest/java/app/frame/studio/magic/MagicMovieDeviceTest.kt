package app.frame.studio.magic

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
import app.frame.studio.export.CompositionFactory
import app.frame.studio.export.ExportManager
import app.frame.studio.timeline.TimelineMath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs Magic Movie end to end on a device: analysis, beat sync, then a real export. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MagicMovieDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val frameApp = context.applicationContext as FrameApp

    private fun sample(name: String, copyName: String = name): Uri {
        val copy = File(context.filesDir, "magic_$copyName")
        File("/data/local/tmp/$name").copyTo(copy, overwrite = true)
        return Uri.fromFile(copy)
    }

    /** A photo that looks nothing like the test pattern: soft diagonal gradient with a disc. */
    private fun gradientPhoto(): Uri {
        val bmp = android.graphics.Bitmap.createBitmap(1200, 900, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint()
        paint.shader = android.graphics.LinearGradient(0f, 0f, 1200f, 900f, 0xFF203A80.toInt(), 0xFFF0A040.toInt(), android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1200f, 900f, paint)
        paint.shader = null
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(400f, 500f, 220f, paint)
        val file = File(context.filesDir, "magic_gradient.png")
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return Uri.fromFile(file)
    }

    @Test
    fun makesBeatSyncedMovieAndExportsIt() = runBlocking {
        val media = listOf(
            sample("sample.png", "a.png"),
            sample("sample.png", "b.png"), // exact duplicate: should be dropped
            sample("big.jpg"), // same test pattern at 48 MP: also a duplicate
            gradientPhoto(),
            sample("sample.mp4"),
        )
        val stages = mutableListOf<String>()
        val start = System.currentTimeMillis()
        val project = frameApp.magicMovie.create(
            uris = media,
            musicUri = sample("beats.m4a"),
            title = "Test Movie",
            vibe = MagicVibe.PARTY,
            length = MagicLength.ALL,
            aspect = CanvasAspect.R9_16,
        ) { stages += it.stage }
        Log.i("FramePerf", "magic.create ms=${System.currentTimeMillis() - start}")

        assertTrue(stages.contains("Listening for the beat"))
        assertEquals("Test Movie", project.name)
        assertEquals(CanvasAspect.R9_16, project.aspect)
        // One of the three identical test-pattern photos, the distinct photo and the video.
        assertEquals("clips: ${project.videoClips.map { it.displayName }}", 3, project.videoClips.size)
        assertEquals(1, project.videoClips.count { it.sourceUri.contains("magic_gradient") })
        assertEquals(1, project.videoClips.count { !it.isImage })

        val music = project.audioClips.single()
        assertTrue("bpm ${music.bpm}", music.bpm in 110f..130f)
        val total = TimelineMath.totalVideoMs(project.videoClips)
        // Cuts land within a frame of a detected beat.
        var t = 0L
        project.videoClips.dropLast(1).forEach { c ->
            t += c.outputDurationMs()
            assertTrue("cut $t not on a beat", music.beatsMs.any { kotlin.math.abs(it - t) <= 40 })
        }

        val output = File(context.cacheDir, "magic.mp4").apply { delete() }
        val done = CountDownLatch(1)
        var error: ExportException? = null
        val exportProject = project.copy(exportHeight = ExportHeight.HD_720)
        Handler(Looper.getMainLooper()).post {
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    error = exportException
                    done.countDown()
                }
            }
            ExportManager.newTransformer(context, frameApp.device, exportProject, listener)
                .start(CompositionFactory.build(exportProject), output.absolutePath)
        }
        assertTrue("export timed out", done.await(5, TimeUnit.MINUTES))
        assertNull("export failed: ${error?.errorCodeName} ${error?.cause}", error)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(output.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertEquals(total.toDouble(), duration.toDouble(), 400.0)
            assertEquals("720", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
            assertEquals("1280", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
        } finally {
            retriever.release()
        }
        frameApp.repository.delete(project.id)
    }
}
