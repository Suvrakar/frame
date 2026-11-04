package app.frame.studio.perf

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.frame.studio.FrameApp
import app.frame.studio.MainActivity
import app.frame.studio.data.ImageNormalizer
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.AudioKind
import app.frame.studio.domain.Project
import app.frame.studio.domain.ProjectJson
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip
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
import java.util.concurrent.atomic.AtomicLong

/**
 * Stress run meant for a low-end emulator profile (2 cores, 1.5 GB RAM): a 48 MP photo, a
 * 20-clip timeline with heavy looks, preview playback, export and UI scrolling. Metrics are
 * logged under the "FramePerf" tag and collected by scripts/smoke-test.sh.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PerfStressTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as FrameApp
    private val device = UiDevice.getInstance(instrumentation)
    private val pkg = context.packageName

    private fun report(key: String, value: Any) = Log.i("FramePerf", "$key=$value")

    private fun reportGfx(screen: String, gfx: String) {
        listOf("Total frames rendered", "Janky frames", "50th percentile", "90th percentile", "95th percentile", "99th percentile")
            .forEach { key -> gfx.lineSequence().firstOrNull { it.trim().startsWith(key) }?.let { report("ui.$screen.$key", it.substringAfter(":").trim()) } }
    }

    private fun sample(name: String): File {
        val copy = File(context.filesDir, "perf_$name")
        File("/data/local/tmp/$name").copyTo(copy, overwrite = true)
        return copy
    }

    private fun longProject(photo: Uri, photoW: Int, photoH: Int): Project {
        val video = Uri.fromFile(sample("sample.mp4")).toString()
        val music = Uri.fromFile(sample("sample.m4a")).toString()
        val looks = listOf("lofi", "vivid", "old_reel", "golden_hour", "noir", "teal")
        val clips = List(20) { i ->
            if (i % 2 == 0) {
                VideoClip(
                    id = "v$i", sourceUri = video, sourceDurationMs = 4_000, trimStartMs = 500, trimEndMs = 2_500,
                    width = 1280, height = 720, filterId = looks[i % looks.size],
                    speed = if (i % 4 == 0) 1.5f else 1f,
                    transition = TransitionType.FADE_BLACK, transitionMs = 300,
                )
            } else {
                VideoClip(
                    id = "p$i", sourceUri = photo.toString(), sourceDurationMs = VideoClip.MAX_IMAGE_MS, trimEndMs = 1_500,
                    width = photoW, height = photoH, isImage = true, filterId = looks[(i + 3) % looks.size],
                    transition = TransitionType.FADE_WHITE, transitionMs = 300,
                )
            }
        }
        return Project(
            id = "perf",
            name = "Stress test",
            createdAtEpochMs = System.currentTimeMillis(),
            videoClips = clips,
            audioClips = listOf(
                AudioClip("m1", music, 4_000, trimEndMs = 4_000, startMs = 0),
                AudioClip("m2", music, 4_000, trimEndMs = 4_000, startMs = 6_000, kind = AudioKind.VOICE),
            ),
            textOverlays = List(6) { i -> TextOverlay("t$i", "Caption $i", startMs = i * 4_000L, endMs = i * 4_000L + 2_500) },
            exportHeight = app.device.defaultExportHeight,
        )
    }

    @Test
    fun lowEndStress() {
        report("device.lowEnd", app.device.isLowEnd)
        report("device.cores", app.device.cores)
        report("device.memoryClassMb", app.device.memoryClassMb)
        report("device.previewHeight", app.device.previewHeight)
        report("device.supportedExport", app.device.supportedExportHeights.joinToString { it.label })

        // 1) A 48 MP photo is downscaled on import instead of being decoded at full size.
        val big = sample("big.jpg")
        val t0 = SystemClock.elapsedRealtime()
        val normalized = runBlocking { ImageNormalizer(context, app.device.maxImageLongSide).normalize(Uri.fromFile(big)) }!!
        report("import.bigPhotoMs", SystemClock.elapsedRealtime() - t0)
        report("import.bigPhotoSize", "${normalized.width}x${normalized.height}")
        assertTrue(normalized.resized)
        assertTrue(maxOf(normalized.width, normalized.height) <= app.device.maxImageLongSide)

        val project = longProject(normalized.uri, normalized.width, normalized.height)

        // 2) Preview of a long, effect-heavy timeline keeps playing.
        val ready = CountDownLatch(1)
        var previewError: PlaybackException? = null
        var player: CompositionPlayer? = null
        val previewProject = project.copy(exportHeight = app.device.previewHeight)
        Handler(Looper.getMainLooper()).post {
            val p = CompositionPlayer.Builder(context).build()
            player = p
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) ready.countDown()
                }
                override fun onPlayerError(error: PlaybackException) {
                    previewError = error
                    ready.countDown()
                }
            })
            p.setComposition(CompositionFactory.build(previewProject, forExport = false))
            p.prepare()
            p.play()
        }
        val prepStart = SystemClock.elapsedRealtime()
        assertTrue("preview never became ready", ready.await(90, TimeUnit.SECONDS))
        report("preview.readyMs", SystemClock.elapsedRealtime() - prepStart)
        Thread.sleep(5_000)
        val position = AtomicLong()
        val released = CountDownLatch(1)
        var errorDuringPlayback: PlaybackException? = null
        Handler(Looper.getMainLooper()).post {
            position.set(player?.currentPosition ?: 0)
            errorDuringPlayback = previewError
            // The app releases players on a background thread; here we only measure how long it takes.
            val t = SystemClock.elapsedRealtime()
            player?.release()
            report("preview.releaseMs", SystemClock.elapsedRealtime() - t)
            released.countDown()
        }
        released.await(10, TimeUnit.SECONDS)
        report("preview.advancedMsIn5s", position.get())
        assertNull("preview error: ${errorDuringPlayback?.errorCodeName}", errorDuringPlayback)
        assertTrue("preview stalled (${position.get()} ms)", position.get() > 500)

        // 3) Export of the whole timeline completes at the device's default resolution.
        val out = File(context.cacheDir, "perf_out.mp4").apply { delete() }
        var transformerRef: Transformer? = null
        val done = CountDownLatch(1)
        var exportError: ExportException? = null
        val exportStart = SystemClock.elapsedRealtime()
        Handler(Looper.getMainLooper()).post {
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = done.countDown()
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    exportError = exportException
                    done.countDown()
                }
            }
            // Same configuration as the app's export screen.
            transformerRef = ExportManager.newTransformer(context, app.device, project, listener).also {
                it.start(CompositionFactory.build(project), out.absolutePath)
            }
        }
        // Log progress so a stall can be located.
        val holder = androidx.media3.transformer.ProgressHolder()
        var waited = 0
        while (!done.await(10, TimeUnit.SECONDS) && waited < 60) {
            waited++
            val progress = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                report("export.progressAt${waited * 10}s", "${transformerRef?.getProgress(holder)}:${holder.progress}")
                progress.countDown()
            }
            progress.await(2, TimeUnit.SECONDS)
        }
        assertTrue("export timed out", done.await(1, TimeUnit.SECONDS))
        val exportMs = SystemClock.elapsedRealtime() - exportStart
        assertNull("export failed: ${exportError?.errorCodeName}", exportError)
        report("export.ms", exportMs)
        report("export.timelineMs", TimelineMath.totalVideoMs(project.videoClips))
        report("export.bytes", out.length())
        assertTrue(out.length() > 50_000)

        // 4) UI: open the project, play and scrub the timeline; collect frame and memory stats.
        File(context.filesDir, "projects").apply { mkdirs() }.resolve("perf.json").writeText(ProjectJson.encode(project))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.wait(Until.hasObject(By.text("New project")), 20_000)
        Thread.sleep(2_000)
        // Baseline: the home screen has no video at all, so its frame times show what this
        // device/emulator renders at best.
        device.executeShellCommand("dumpsys gfxinfo $pkg reset")
        repeat(4) {
            val x = device.displayWidth / 2
            device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 12)
            device.swipe(x, device.displayHeight / 4, x, device.displayHeight * 3 / 4, 12)
        }
        Thread.sleep(800)
        reportGfx("home", device.executeShellCommand("dumpsys gfxinfo $pkg"))
        // Template gallery: masonry grid of rendered previews, the heaviest list in the app.
        device.wait(Until.findObject(By.text("Templates")), 3_000)?.click()
        device.wait(Until.hasObject(By.textStartsWith("Search")), 5_000)
        Thread.sleep(2_500) // let previews render/cache once
        device.executeShellCommand("dumpsys gfxinfo $pkg reset")
        repeat(4) {
            val x = device.displayWidth / 2
            device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 10)
            device.swipe(x, device.displayHeight / 4, x, device.displayHeight * 3 / 4, 10)
        }
        Thread.sleep(800)
        reportGfx("templates", device.executeShellCommand("dumpsys gfxinfo $pkg"))
        device.wait(Until.findObject(By.text("Home")), 3_000)?.click()
        device.wait(Until.hasObject(By.text("New project")), 5_000)
        // Open the project; verify the editor actually appeared and retry if a tap was lost mid-scroll.
        var opened = false
        repeat(6) {
            if (opened) return@repeat
            val card = device.wait(Until.findObject(By.text("Stress test")), 2_500)
            if (card == null) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 2 / 3, device.displayWidth / 2, device.displayHeight / 3, 25)
            } else {
                device.waitForIdle()
                runCatching { card.click() }
                opened = device.wait(Until.hasObject(By.text("Export")), 8_000) == true
            }
        }
        assertTrue("could not open the project", opened)
        Thread.sleep(3_000)
        device.executeShellCommand("dumpsys gfxinfo $pkg reset")
        device.wait(Until.findObject(By.desc("Play")), 5_000)?.click()
        Thread.sleep(4_000)
        device.wait(Until.findObject(By.desc("Pause")), 2_000)?.click()
        val y = device.displayHeight * 62 / 100
        repeat(6) {
            device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, 12)
            device.swipe(device.displayWidth / 5, y, device.displayWidth * 4 / 5, y, 12)
        }
        Thread.sleep(1_000)
        reportGfx("editor", device.executeShellCommand("dumpsys gfxinfo $pkg"))
        val mem = device.executeShellCommand("dumpsys meminfo $pkg")
        val totalPssKb = mem.lineSequence().firstOrNull { it.trim().startsWith("TOTAL PSS:") || it.trim().startsWith("TOTAL:") }
            ?.trim()?.split(Regex("\\s+"))?.firstNotNullOfOrNull { it.toIntOrNull() }
        report("ui.totalPssMb", (totalPssKb ?: 0) / 1024)
        // The app must still be alive and responsive after the stress run.
        assertTrue("editor no longer responsive", device.wait(Until.hasObject(By.text("Export")), 5_000))
        assertTrue("memory too high: ${totalPssKb?.div(1024)} MB", totalPssKb == null || totalPssKb / 1024 < 450)
    }
}
