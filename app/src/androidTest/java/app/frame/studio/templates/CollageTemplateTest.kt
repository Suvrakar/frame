package app.frame.studio.templates

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.frame.studio.FrameApp
import app.frame.studio.timeline.TimelineMath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private val SHOWCASE = setOf(
    "birthday_collage", "party_polaroids", "taped_birthday", "circle_birthday", "ransom_birthday", "night_ransom",
    "gold_invite", "bunting_party", "scrapbook_birthday", "booth_birthday",
)

/** Builds every collage template on a real device from sample media and checks the frames. */
@RunWith(AndroidJUnit4::class)
class CollageTemplateTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as FrameApp
    private val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun sample(name: String): Uri {
        val copy = File(context.filesDir, "collage_$name")
        File("/data/local/tmp/$name").copyTo(copy, overwrite = true)
        return Uri.fromFile(copy)
    }

    @Test
    fun everyCollageTemplateBuilds() = runBlocking {
        val photo = sample("sample.png")
        val video = sample("sample.mp4")
        val values = mapOf(
            TemplateField.NAME to "Mia", TemplateField.AGE to "7", TemplateField.TITLE to "10",
            TemplateField.DATE to "Sat 9 March · 6 PM", TemplateField.PLACE to "Mia's house",
        )
        Templates.inCategory(TemplateCategory.COLLAGE).forEach { template ->
            val media = List(template.slots) { i -> if (i % 3 == 2) video else photo }
            val project = app.templateBuilder.create(template, values, media)
            assertEquals(template.id, template.totalMs, TimelineMath.totalVideoMs(project.videoClips))
            assertEquals(template.id, template.aspect, project.aspect)
            project.videoClips.forEach { clip ->
                val file = File(Uri.parse(clip.sourceUri).path!!)
                assertTrue("${template.id}: missing ${file.name}", file.exists() && file.length() > 10_000)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                assertEquals(template.id, clip.width, bounds.outWidth)
                assertEquals(template.id, clip.height, bounds.outHeight)
            }
            assertNotNull(app.repository.get(project.id))
            if (template.id in SHOWCASE) {
                // Copy the finished collage where the CI script can collect it for visual review.
                val finalFrame = project.videoClips.filter { it.displayName.startsWith("Collage") }.last()
                val src = File(Uri.parse(finalFrame.sourceUri).path!!)
                val ext = File(context.getExternalFilesDir(null), "collage_${template.id}.jpg")
                src.copyTo(ext, overwrite = true)
                device.executeShellCommand("mkdir -p /data/local/tmp/shots")
                device.executeShellCommand("cp ${ext.absolutePath} /data/local/tmp/shots/collage_${template.id}.jpg")
            }
            app.repository.delete(project.id)
        }
    }
}
