package app.frame.studio.magic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import app.frame.studio.export.CompositionFactory
import app.frame.studio.export.ExportManager
import app.frame.studio.timeline.TimelineMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Magic Movie under hostile input: broken files, silent or unreadable music, extremes, cancel. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MagicMovieRobustnessTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as FrameApp
    private val created = mutableListOf<String>()

    @After
    fun cleanUp() = runBlocking { created.forEach { app.repository.delete(it) } }

    private fun sample(name: String, copyName: String = name): Uri {
        val copy = File(context.filesDir, "robust_$copyName")
        File("/data/local/tmp/$name").copyTo(copy, overwrite = true)
        return Uri.fromFile(copy)
    }

    private fun file(name: String, bytes: ByteArray): Uri =
        Uri.fromFile(File(context.filesDir, "robust_$name").apply { writeBytes(bytes) })

    /** A distinct photo per [seed]: random shapes, so no two count as duplicates. */
    private fun photo(seed: Int, w: Int = 1080, h: Int = 1350): Uri {
        val r = Random(seed)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF000000.toInt() or r.nextInt(0xFFFFFF))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(12) {
            paint.color = 0xFF000000.toInt() or r.nextInt(0xFFFFFF)
            canvas.drawCircle(r.nextFloat() * w, r.nextFloat() * h, 60f + r.nextFloat() * 300f, paint)
        }
        val f = File(context.filesDir, "robust_photo_$seed.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        return Uri.fromFile(f)
    }

    private suspend fun make(uris: List<Uri>, music: Uri? = null, length: MagicLength = MagicLength.ALL, vibe: MagicVibe = MagicVibe.UPBEAT): Project {
        val start = System.currentTimeMillis()
        val p = app.magicMovie.create(uris, music, "Robust", vibe, length, CanvasAspect.R9_16) { }
        Log.i("FramePerf", "magic.robust items=${uris.size} ms=${System.currentTimeMillis() - start}")
        created += p.id
        return p
    }

    private fun assertExports(project: Project) {
        val out = File(context.cacheDir, "robust.mp4").apply { delete() }
        val p = project.copy(exportHeight = ExportHeight.SD_480)
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
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertEquals(TimelineMath.totalVideoMs(p.videoClips).toDouble(), ms.toDouble(), 500.0)
        } finally {
            r.release()
            out.delete()
        }
    }

    @Test
    fun skipsBrokenFilesAndKeepsTheGoodOnes() = runBlocking {
        val p = make(
            listOf(
                file("empty.jpg", ByteArray(0)),
                file("garbage.jpg", Random(1).nextBytes(50_000)),
                file("notes.txt", "hello".toByteArray()),
                file("truncated.mp4", File("/data/local/tmp/sample.mp4").readBytes().copyOf(20_000)),
                photo(1),
                photo(2),
            ),
        )
        assertEquals("only the two real photos: ${p.videoClips.map { it.sourceUri }}", 2, p.videoClips.size)
        assertExports(p)
    }

    @Test
    fun allBrokenGivesAClearError() = runBlocking {
        try {
            make(listOf(file("empty2.jpg", ByteArray(0)), file("garbage2.mp4", Random(2).nextBytes(10_000))))
            fail("expected an error")
        } catch (e: IllegalArgumentException) {
            assertEquals("Those files couldn't be opened", e.message)
        }
    }

    @Test
    fun unreadableMusicStillMakesTheMovie() = runBlocking {
        val p = make(listOf(photo(3), photo(4), photo(5)), music = file("broken.m4a", Random(3).nextBytes(30_000)))
        assertEquals(3, p.videoClips.size)
        assertTrue("no music track expected", p.audioClips.isEmpty())
        assertExports(p)
    }

    @Test
    fun silentMusicHasNoBeatsButStillWorks() = runBlocking {
        val p = make(listOf(photo(6), photo(7), photo(8), sample("short.mp4")), music = sample("silence.m4a"))
        assertEquals(1, p.audioClips.size)
        assertEquals(4, p.videoClips.size)
        assertExports(p)
    }

    @Test
    fun singlePhoto() = runBlocking {
        val p = make(listOf(photo(9)), length = MagicLength.SHORT)
        assertEquals(1, p.videoClips.size)
        assertTrue(TimelineMath.totalVideoMs(p.videoClips) >= 900)
        assertExports(p)
    }

    @Test
    fun veryShortAndVeryLongVideos() = runBlocking {
        val p = make(listOf(sample("short.mp4"), sample("long.mp4")), music = sample("beats.m4a"), length = MagicLength.MEDIUM)
        assertEquals(2, p.videoClips.size)
        p.videoClips.forEach { c ->
            assertTrue("clip out of bounds: $c", c.trimStartMs >= 0 && c.trimEndMs <= c.sourceDurationMs && c.trimEndMs > c.trimStartMs)
        }
        assertExports(p)
    }

    @Test
    fun manyPhotosStayWithinLimitsAndTime() = runBlocking {
        val uris = (100 until 145).map { photo(it, 1600, 2000) }
        val start = System.currentTimeMillis()
        val p = make(uris, music = sample("beats.m4a"), length = MagicLength.LONG, vibe = MagicVibe.PARTY)
        val ms = System.currentTimeMillis() - start
        assertTrue("too many clips: ${p.videoClips.size}", p.videoClips.size <= MagicPlan.MAX_CLIPS)
        assertTrue("45 photos took $ms ms", ms < 60_000)
        val total = TimelineMath.totalVideoMs(p.videoClips)
        assertTrue("LONG should be about a minute, was $total", total in 30_000L..90_000L)
    }

    @Test
    fun cancellingStopsWithoutSavingAProject() = runBlocking {
        val before = app.repository.projects.value.size
        val uris = (200 until 230).map { photo(it, 1600, 2000) }
        // Cancel from inside the build once a few photos are analysed (timing-independent).
        val job = async {
            app.magicMovie.create(uris, null, "Robust", MagicVibe.UPBEAT, MagicLength.ALL, CanvasAspect.R9_16) { p ->
                if (p.done >= 3) coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }.also { created += it.id }
        }
        try {
            job.await()
            fail("expected cancellation")
        } catch (e: CancellationException) {
            // expected
        }
        delay(500)
        assertEquals("a cancelled movie must not be saved", before, app.repository.projects.value.size)
    }

    @Test
    fun duplicatesOnlyLeaveOne() = runBlocking {
        val one = photo(300)
        val p = make(listOf(one, sample("sample.png", "d1.png"), sample("sample.png", "d2.png"), sample("sample.png", "d3.png")))
        assertEquals(2, p.videoClips.size)
        assertFalse(p.videoClips.isEmpty())
    }
}
