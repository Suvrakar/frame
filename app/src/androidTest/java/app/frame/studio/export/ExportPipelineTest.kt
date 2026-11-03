package app.frame.studio.export

import android.media.MediaMetadataRetriever
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
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.ExportHeight
import app.frame.studio.domain.Project
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.VideoClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Renders a project using every editing feature through the real Media3 pipeline.
 * CI pushes sample media to /data/local/tmp (see scripts/smoke-test.sh).
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ExportPipelineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sample(name: String): File {
        val source = File("/data/local/tmp/$name")
        val copy = File(context.cacheDir, name)
        source.copyTo(copy, overwrite = true)
        return copy
    }

    @Test
    fun exportsFullFeaturedProject() {
        val video = sample("sample.mp4")
        val music = sample("sample.m4a")
        val uri = android.net.Uri.fromFile(video).toString()
        val project = Project(
            id = "test",
            name = "Test",
            createdAtEpochMs = 0,
            videoClips = listOf(
                VideoClip(
                    id = "a", sourceUri = uri, sourceDurationMs = 4_000, trimStartMs = 500, trimEndMs = 3_500,
                    speed = 2f, volume = 0.5f, fadeInMs = 300, fadeOutMs = 300, width = 1280, height = 720,
                ),
                VideoClip(
                    id = "b", sourceUri = uri, sourceDurationMs = 4_000, trimEndMs = 2_000,
                    rotationDegrees = 90, crop = centerCrop(1280, 720, 1f), width = 1280, height = 720,
                ),
            ),
            audioClips = listOf(
                AudioClip(id = "m", sourceUri = android.net.Uri.fromFile(music).toString(), sourceDurationMs = 4_000, trimEndMs = 4_000, fadeOutMs = 500),
            ),
            textOverlays = listOf(TextOverlay(id = "t", text = "Hello Frame", startMs = 0, endMs = 2_000)),
            crossfadeMs = 400,
            exportHeight = ExportHeight.HD_720,
        )
        val output = File(context.cacheDir, "out.mp4").apply { delete() }
        val done = CountDownLatch(1)
        var error: ExportException? = null

        Handler(Looper.getMainLooper()).post {
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(ExportPolicy.videoMimeType)
                .setAudioMimeType(ExportPolicy.audioMimeType)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        error = exportException
                        done.countDown()
                    }
                })
                .build()
            transformer.start(CompositionFactory.build(project), output.absolutePath)
        }

        assertTrue("export timed out", done.await(5, TimeUnit.MINUTES))
        assertNull("export failed: ${error?.errorCodeName} ${error?.cause}", error)
        assertTrue(output.length() > 10_000)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(output.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            // 1.5s (3s at 2x) + 2s = 3.5s
            assertEquals(3_500.0, duration.toDouble(), 300.0)
            assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            assertEquals("1280", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
            assertEquals("720", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
        } finally {
            retriever.release()
        }
    }
}
