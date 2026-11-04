package app.frame.studio.ui

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.frame.studio.FrameApp
import app.frame.studio.MainActivity
import app.frame.studio.templates.TemplateField
import app.frame.studio.templates.Templates
import app.frame.studio.domain.AudioClip
import app.frame.studio.domain.AudioKind
import app.frame.studio.domain.Project
import app.frame.studio.domain.ProjectJson
import app.frame.studio.domain.TextOverlay
import app.frame.studio.domain.TransitionType
import app.frame.studio.domain.VideoClip
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks the main screens with a seeded project and saves screenshots for visual review.
 * Output: PNG files in /data/local/tmp/shots (pulled by scripts/smoke-test.sh).
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    init {
        // Written by the shell user so the files survive the app being uninstalled after tests.
        device.executeShellCommand("mkdir -p /data/local/tmp/shots")
    }

    private fun sample(name: String): String {
        val copy = File(context.filesDir, "seed_$name")
        File("/data/local/tmp/$name").copyTo(copy, overwrite = true)
        return Uri.fromFile(copy).toString()
    }

    private fun shot(name: String) {
        device.waitForIdle()
        Thread.sleep(1_200)
        device.executeShellCommand("screencap -p /data/local/tmp/shots/$name.png")
    }

    private fun tap(text: String, scrollVertically: Boolean = false): Boolean {
        repeat(4) {
            val node = device.wait(Until.findObject(By.text(text)), 2_500)
            if (node != null) {
                try {
                    node.click()
                    return true
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    // Rebuilt between find and click; look again.
                }
            }
            if (scrollVertically) {
                val x = device.displayWidth / 2
                device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 20)
            } else {
                // Scroll the bottom toolbar to reveal more tools.
                val y = device.displayHeight - device.displayHeight / 18
                device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, 20)
            }
            // Let any fling settle before looking again.
            device.waitForIdle()
            Thread.sleep(700)
        }
        return false
    }

    /** Taps a chip in a horizontally scrolling row that also contains [anchor]. */
    private fun tapInRow(text: String, anchor: String): Boolean {
        repeat(5) {
            try {
                device.wait(Until.findObject(By.text(text)), 1_500)?.let {
                    it.click()
                    return true
                }
                // Compose may rebuild the row between finding and reading a node: re-find each time.
                val y = device.findObject(By.text(anchor))?.visibleCenter?.y ?: return false
                device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, 20)
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                // Try again with fresh nodes.
            }
            device.waitForIdle()
            Thread.sleep(500)
        }
        return false
    }

    private fun scrollToTop() {
        repeat(4) {
            val x = device.displayWidth / 2
            device.swipe(x, device.displayHeight / 4, x, device.displayHeight * 3 / 4, 15)
        }
        device.waitForIdle()
    }

    private fun restartHome() {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        device.wait(Until.hasObject(By.text("New project")), 15_000)
        Thread.sleep(800)
    }

    private fun openProject(name: String): Boolean {
        restartHome()
        if (!tap(name, scrollVertically = true)) return false
        val opened = device.wait(Until.hasObject(By.text("Export")), 10_000) == true
        Thread.sleep(2_000)
        return opened
    }

    /** Closes the open tool panel with its ✓ button. */
    private fun done() {
        device.wait(Until.findObject(By.desc("Done")), 3_000)?.click()
        device.waitForIdle()
    }

    /** Leaves the selection toolbar via its back chip. */
    private fun deselect() {
        device.wait(Until.findObject(By.desc("Back")), 3_000)?.click()
        device.waitForIdle()
    }

    @Test
    fun tour() {
        val video = sample("sample.mp4")
        val image = sample("sample.png")
        val music = sample("sample.m4a")
        val project = Project(
            id = "tour",
            name = "Summer trip",
            createdAtEpochMs = System.currentTimeMillis(),
            videoClips = listOf(
                VideoClip(id = "a", sourceUri = video, sourceDurationMs = 4_000, trimEndMs = 4_000, width = 1280, height = 720, transition = TransitionType.FADE_BLACK, filterId = "lofi"),
                VideoClip(id = "b", sourceUri = image, sourceDurationMs = VideoClip.MAX_IMAGE_MS, trimEndMs = 3_000, width = 640, height = 480, isImage = true, filterId = "warm"),
                VideoClip(id = "c", sourceUri = video, sourceDurationMs = 4_000, trimStartMs = 1_000, trimEndMs = 4_000, speed = 2f, width = 1280, height = 720),
            ),
            audioClips = listOf(
                AudioClip(id = "m", sourceUri = music, sourceDurationMs = 4_000, trimEndMs = 4_000, startMs = 0, displayName = "Sunny day.m4a"),
                AudioClip(id = "v", sourceUri = music, sourceDurationMs = 4_000, trimEndMs = 2_000, startMs = 3_000, kind = AudioKind.VOICE, displayName = "Voiceover"),
            ),
            textOverlays = listOf(TextOverlay(id = "t", text = "Best day ever", startMs = 500, endMs = 3_500, bold = true)),
        )
        File(context.filesDir, "projects").apply { mkdirs() }
            .resolve("tour.json").writeText(ProjectJson.encode(project))

        // A collage project made by the real template builder.
        val frameApp = context.applicationContext as FrameApp
        val collage = kotlinx.coroutines.runBlocking {
            frameApp.templateBuilder.create(
                Templates.byId("party_polaroids")!!,
                mapOf(TemplateField.NAME to "Mia"),
                listOf(Uri.parse(image), Uri.parse(video), Uri.parse(image), Uri.parse(video)),
            )
        }
        val collageName = collage.name
        // Each section starts from a fresh Home so one missed tap can't derail the rest.
        restartHome()
        shot("1_home")

        tap("See all")
        device.wait(Until.hasObject(By.text("Birthday collection")), 5_000)
        shot("1b_templates")
        if (tapInRow("Collage", anchor = "All")) {
            Thread.sleep(1_000)
            shot("1b2_collage_gallery")
        }

        restartHome()
        tap("See all")
        device.wait(Until.hasObject(By.text("Birthday collection")), 5_000)
        if (tap("Birthday Bash")) {
            device.wait(Until.hasObject(By.textStartsWith("Use template")), 5_000)
            shot("1c_template_detail")
        }

        if (openProject("Summer trip")) {
            shot("2_editor")
            tap("Edit")
            shot("3_clip_tools")
            tap("Speed")
            shot("3b_speed")
            done()
            tap("Filters")
            Thread.sleep(1_500)
            shot("4_filters")
            if (tapInRow("Social looks", anchor = "Classic")) {
                Thread.sleep(1_500)
                shot("4b_social_looks")
            }
            done()
        }

        if (openProject("Summer trip")) {
            tap("Audio")
            shot("5_audio")
            done()
            if (tap("Canvas")) shot("6_canvas")
        }

        if (openProject("Summer trip")) {
            tap("Export")
            device.wait(Until.hasObject(By.text("Resolution")), 10_000)
            shot("7_export")
            if (tapInRow("YouTube", anchor = "Instagram")) shot("8_export_youtube")
        }

        // Collage project: play past the build-up and capture the finished collage.
        if (openProject(collageName)) {
            Thread.sleep(1_500)
            device.wait(Until.findObject(By.desc("Play")), 3_000)?.click()
            Thread.sleep(2_600)
            device.wait(Until.findObject(By.desc("Pause")), 2_000)?.click()
            shot("9_collage_editor")
            if (tap("Edit") && tap("Grade")) {
                Thread.sleep(800)
                shot("16_grade_light")
                if (tapInRow("Split tone", anchor = "Light")) shot("17_split_tone")
            }
        }

        // Scene effects.
        if (openProject(collageName)) {
            if (tap("Effects")) {
                Thread.sleep(600)
                shot("18_effects_panel")
                if (tap("Snow")) {
                    Thread.sleep(2_500)
                    shot("19_effect_snow")
                }
            }
        }

        // Magic Movie and the Frame Pro sheet.
        restartHome()
        if (tap("Magic Movie", scrollVertically = true)) {
            device.wait(Until.hasObject(By.text("Vibe")), 5_000)
            Thread.sleep(600)
            shot("20_magic_movie")
        }
        restartHome()
        if (tap("Settings")) {
            Thread.sleep(600)
            if (tap("Get Frame Pro")) {
                device.wait(Until.hasObject(By.text("Not now")), 5_000)
                Thread.sleep(900)
                shot("21_paywall")
                device.pressBack()
            }
        }

        // Settings and dark mode.
        restartHome()
        if (tap("Settings")) {
            shot("10_settings_light")
            tap("Dark")
            Thread.sleep(800)
            shot("11_settings_dark")
        }
        restartHome()
        shot("12_home_dark")
        tap("See all")
        device.wait(Until.hasObject(By.text("Birthday collection")), 5_000)
        shot("12b_templates_dark")
        if (openProject("Summer trip")) {
            shot("13_editor_dark")
            tap("Edit")
            tap("Filters")
            Thread.sleep(1_500)
            shot("14_filters_dark")
        }
        if (openProject("Summer trip")) {
            tap("Export")
            device.wait(Until.hasObject(By.text("Resolution")), 10_000)
            shot("15_export_dark")
        }
    }
}
