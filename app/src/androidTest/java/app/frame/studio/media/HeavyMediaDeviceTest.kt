package app.frame.studio.media

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
import app.frame.studio.domain.VideoClip
import app.frame.studio.export.CompositionFactory
import app.frame.studio.export.ExportManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Heavy and unsupported media on a real phone (needs /data/local/tmp/{v8k,v4k60,hevc10,ac3}.mp4).
 * Expectations are derived from the phone's own decoder table, so the test holds on any device.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class HeavyMediaDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as FrameApp
    private val table = AndroidDecoderTable()
    private val inspector = MediaInspector(context)

    private fun sample(name: String): Uri =
        Uri.fromFile(File(context.filesDir, "heavy_$name").also { File("/data/local/tmp/$name").copyTo(it, overwrite = true) })

    @Test
    fun verdictsMatchThePhonesDecoders() = runBlocking {
        for (name in listOf("v8k.mp4", "v4k60.mp4", "hevc10.mp4", "ac3.mp4", "sample.mp4")) {
            val uri = sample(name)
            val inspection = inspector.inspect(uri)
            assertNotNull("could not inspect $name", inspection)
            val video = inspection!!.video!!
            val declared = ImportPolicy.video(video, table, app.device.isLowEnd)
            val info = app.mediaProbe.probeVideo(uri)
            assertNotNull("probe failed for $name", info)
            Log.i("HeavyMedia", "$name ${video.width}x${video.height}@${video.fps} ${video.mime} hdr=${video.hdr} declared=$declared -> ${info!!.verdict} audioOk=${info.audioSupported}")
            // Declared limits may under-report; the probe may only ever relax them (verified decode).
            if (declared !is VideoVerdict.Unsupported) assertEquals("verdict for $name", declared, info.verdict)
            inspection.audio?.let { assertEquals("audio verdict for $name", ImportPolicy.audioSupported(it, table), info.audioSupported) }
        }
        // A plain 720p H.264 clip is always fine.
        assertEquals(VideoVerdict.Ok, app.mediaProbe.probeVideo(sample("sample.mp4"))!!.verdict)
        // This phone demonstrably decodes the 4K60 file (it is proxied and exported below): never refuse it.
        val fourK = app.mediaProbe.probeVideo(sample("v4k60.mp4"))!!.verdict
        assertTrue("4K60 that decodes must not be refused: $fourK", fourK !is VideoVerdict.Unsupported)
    }

    @Test
    fun eightKIsRefusedWithAClearReasonWhenNoDecoderFits() = runBlocking {
        val video = inspector.inspect(sample("v8k.mp4"))!!.video!!
        val fits = table.video(video.mime).any { it.supportsSize(video.width, video.height) || it.supportsSize(video.height, video.width) }
        val verdict = app.mediaProbe.probeVideo(sample("v8k.mp4"))!!.verdict
        if (fits || verdict !is VideoVerdict.Unsupported) {
            // Declared to fit, or a frame decoded: it must import (as Heavy at most).
            assertTrue("8K decodable here, so it must import: $verdict", verdict !is VideoVerdict.Unsupported)
        } else {
            assertTrue("expected refusal: $verdict", verdict is VideoVerdict.Unsupported)
            assertTrue((verdict as VideoVerdict.Unsupported).message, verdict.message.contains("8K"))
        }
    }

    @Test
    fun proxyIsMadeAtShortSide540() {
        val uri = sample("v4k60.mp4").toString()
        val proxies = app.proxies
        Handler(Looper.getMainLooper()).post { proxies.request(uri) }
        val deadline = System.currentTimeMillis() + 120_000
        var proxy: String? = null
        while (proxy == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            proxy = proxies.proxyFor(uri)
        }
        assertNotNull("no proxy within 2 minutes", proxy)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, Uri.parse(proxy))
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            assertEquals("proxy short side", 540, minOf(w, h))
        } finally {
            r.release()
        }
        // Preview uses the proxy; export never does.
        assertEquals(proxy, CompositionFactory.sourceFor(uri, needsProxy = true, forExport = false))
        assertEquals(uri, CompositionFactory.sourceFor(uri, needsProxy = true, forExport = true))
    }

    @Test
    fun heavyClipStillExportsAtFullResolution() {
        val uri = sample("v4k60.mp4").toString()
        val target = app.device.clampExportHeight(ExportHeight.UHD_2160)
        val p = Project(
            id = "heavy", name = "Heavy", createdAtEpochMs = 0,
            videoClips = listOf(VideoClip(id = "h", sourceUri = uri, sourceDurationMs = 3_000, trimEndMs = 3_000, width = 3840, height = 2160, needsProxy = true)),
            aspect = CanvasAspect.R16_9,
            exportHeight = target,
            exportFps = 30,
        )
        val out = File(context.cacheDir, "heavy_export.mp4").apply { delete() }
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
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            Log.i("HeavyMedia", "heavy export ${w}x$h (target ${target.pixels}p)")
            assertEquals("export resolution must not drop to the proxy's", target.pixels, minOf(w, h))
        } finally {
            r.release()
            out.delete()
        }
    }
}
