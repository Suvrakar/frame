package app.frame.studio.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A 4K60 export is made Instagram-ready (1080p, 30 fps, sane bitrate); a small one is sent as is. */
@RunWith(AndroidJUnit4::class)
class ShareOptimizerDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fourKSixtyBecomesInstagramReady() = runBlocking<Unit> {
        val src = File(context.filesDir, "exports/share_test_4k.mp4").apply { parentFile?.mkdirs() }
        File("/data/local/tmp/v4k60.mp4").copyTo(src, overwrite = true)
        val optimizer = ShareOptimizer(context)
        val before = optimizer.facts(src)!!
        val out = optimizer.prepare(src, ShareTarget.INSTAGRAM) {}
        assertNotEquals("a copy should be made", src, out)
        val after = optimizer.facts(out)
        assertNotNull(after)
        after!!
        android.util.Log.i("ShareTest", "before $before after $after")
        assertEquals(1080, minOf(after.width, after.height))
        assertTrue("fps ${after.fps}", after.fps <= 31f)
        assertTrue("bitrate ${after.bitrate}", after.bitrate < 12_000_000)
        // Cached: a second request returns the same file without re-encoding.
        val again = optimizer.prepare(src, ShareTarget.INSTAGRAM) {}
        assertEquals(out, again)
        // YouTube gets the original.
        assertEquals(src, optimizer.prepare(src, ShareTarget.YOUTUBE) {})
        out.delete()
        src.delete()
    }
}
