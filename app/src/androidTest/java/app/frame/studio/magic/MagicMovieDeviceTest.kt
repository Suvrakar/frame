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

    @Test
    fun makesBeatSyncedMovieAndExportsIt() = runBlocking {
        val media = listOf(
            sample("sample.png", "a.png"),
            sample("sample.png", "b.png"), // exact duplicate: should be dropped
            sample("big.jpg"),
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
        val pngCopies = project.videoClips.count { it.sourceUri.contains("magic_a.png") || it.sourceUri.contains("magic_b.png") }
        assertEquals("duplicates kept", 1, pngCopies)
        assertEquals(3, project.videoClips.size)

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
