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
        val image = sample("sample.png")
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
                    filterId = "vivid", adjust = app.frame.studio.domain.ColorAdjust(brightness = 0.2f, warmth = 0.5f),
                    flipH = true, transition = app.frame.studio.domain.TransitionType.FADE_WHITE, transitionMs = 600,
                ),
                VideoClip(
                    id = "c", sourceUri = android.net.Uri.fromFile(image).toString(), sourceDurationMs = VideoClip.MAX_IMAGE_MS,
                    trimEndMs = 2_000, width = 640, height = 480, isImage = true, filterId = "mono",
                ),
            ),
            audioClips = listOf(
                AudioClip(id = "m", sourceUri = android.net.Uri.fromFile(music).toString(), sourceDurationMs = 4_000, trimEndMs = 4_000, fadeOutMs = 500, startMs = 0),
                AudioClip(id = "v", sourceUri = android.net.Uri.fromFile(music).toString(), sourceDurationMs = 4_000, trimEndMs = 1_500, startMs = 2_000, kind = app.frame.studio.domain.AudioKind.VOICE),
            ),
            textOverlays = listOf(
                TextOverlay(id = "t", text = "Hello Frame", startMs = 0, endMs = 2_000, color = 0xFFFFD60A, bold = true),
                TextOverlay(id = "s", text = "🔥", startMs = 1_000, endMs = 4_000, isSticker = true, background = false),
            ),
            exportFps = 24,
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
            // 1.5s (3s at 2x) + 2s + 2s photo = 5.5s
            assertEquals(5_500.0, duration.toDouble(), 400.0)
            assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            assertEquals("1280", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
            assertEquals("720", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
        } finally {
            retriever.release()
        }
    }

    @Test
    fun previewPlaysCompositionWithoutErrors() {
        val video = sample("sample.mp4")
        val image = sample("sample.png")
        val music = sample("sample.m4a")
        val project = Project(
            id = "preview",
            name = "Preview",
            createdAtEpochMs = 0,
            videoClips = listOf(
                VideoClip(id = "a", sourceUri = android.net.Uri.fromFile(video).toString(), sourceDurationMs = 4_000, trimEndMs = 2_000, speed = 1.5f, filterId = "warm", width = 1280, height = 720),
                VideoClip(id = "b", sourceUri = android.net.Uri.fromFile(image).toString(), sourceDurationMs = VideoClip.MAX_IMAGE_MS, trimEndMs = 1_500, isImage = true, width = 640, height = 480),
            ),
            audioClips = listOf(AudioClip(id = "m", sourceUri = android.net.Uri.fromFile(music).toString(), sourceDurationMs = 4_000, trimEndMs = 2_000, startMs = 500)),
            textOverlays = listOf(TextOverlay(id = "t", text = "Preview", startMs = 0, endMs = 1_000)),
            aspect = app.frame.studio.domain.CanvasAspect.R9_16,
        )
        val ready = CountDownLatch(1)
        var playerError: androidx.media3.common.PlaybackException? = null
        var player: androidx.media3.transformer.CompositionPlayer? = null
        Handler(Looper.getMainLooper()).post {
            val p = androidx.media3.transformer.CompositionPlayer.Builder(context).build()
            player = p
            p.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY) ready.countDown()
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playerError = error
                    ready.countDown()
                }
            })
            p.setComposition(CompositionFactory.build(project, forExport = false))
            p.prepare()
            p.play()
        }
        assertTrue("preview never became ready", ready.await(60, TimeUnit.SECONDS))
        Thread.sleep(1_500)
        val position = java.util.concurrent.atomic.AtomicLong()
        val read = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            position.set(player?.currentPosition ?: 0)
            player?.release()
            read.countDown()
        }
        read.await(10, TimeUnit.SECONDS)
        assertNull("preview failed: ${playerError?.errorCodeName}", playerError)
        assertTrue("preview did not advance (${position.get()} ms)", position.get() > 300)
    }
}
