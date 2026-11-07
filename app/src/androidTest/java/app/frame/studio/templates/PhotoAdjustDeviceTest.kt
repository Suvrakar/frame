package app.frame.studio.templates

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.frame.studio.FrameApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Moving a photo inside a frame template changes the preview and the rendered video frame. */
@RunWith(AndroidJUnit4::class)
class PhotoAdjustDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as FrameApp

    private fun differs(a: Bitmap, b: Bitmap): Int {
        var n = 0
        for (y in 0 until a.height step 8) for (x in 0 until a.width step 8) if (a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }

    @Test
    fun framingChangesPreviewAndVideo() = runBlocking {
        val template = Templates.all.first { it.collage != null && it.collage!!.cells == 1 }
        val photo = Uri.fromFile(File(context.filesDir, "adj_big.jpg").also { File("/data/local/tmp/big.jpg").copyTo(it, overwrite = true) })
        val session = app.templateBuilder.openAdjust(template, emptyMap(), listOf(photo), mutableListOf())
        assertNotNull("${template.id} should open the adjust step", session)
        session!!
        try {
            // Large photos are normalised to a cached copy; fits are keyed by that (stable) uri.
            val key = session.media.single().uri
            val (plain, slots) = session.render(0, emptyMap())
            val slot = slots[key]
            assertNotNull("photo slot reported", slot)
            val (bw, bh) = session.photoSize(key)!!
            var fit = PhotoFraming.zoom(bw, bh, slot!!.width(), slot.height(), PhotoFit.CENTER, 2.5f)
            fit = PhotoFraming.pan(bw, bh, slot.width(), slot.height(), fit, slot.width(), slot.height())
            val (moved, _) = session.render(0, mapOf(key to fit))
            assertTrue("preview must change when the photo is moved", differs(plain, moved) > 50)

            // The real video uses the same framing.
            val a = app.templateBuilder.create(template, emptyMap(), listOf(photo))
            val b = app.templateBuilder.create(template, emptyMap(), listOf(photo), fits = mapOf(key to fit))
            fun page(p: app.frame.studio.domain.Project) = p.videoClips.first { it.sourceUri.contains("collage") }.sourceUri
            val pa = android.graphics.BitmapFactory.decodeFile(Uri.parse(page(a)).path)
            val pb = android.graphics.BitmapFactory.decodeFile(Uri.parse(page(b)).path)
            assertTrue("rendered frame must follow the framing", differs(pa, pb) > 50)
            app.repository.delete(a.id)
            app.repository.delete(b.id)
        } finally {
            session.close()
        }
    }
}
