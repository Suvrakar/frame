package app.frame.studio.ui

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.frame.studio.MainActivity
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

    private fun tap(text: String): Boolean {
        val node = device.wait(Until.findObject(By.text(text)), 5_000) ?: return false
        node.click()
        return true
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
                VideoClip(id = "a", sourceUri = video, sourceDurationMs = 4_000, trimEndMs = 4_000, width = 1280, height = 720, transition = TransitionType.FADE_BLACK),
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

        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        device.wait(Until.hasObject(By.text("New project")), 15_000)
        shot("1_home")

        tap("Summer trip")
        device.wait(Until.hasObject(By.text("Export")), 10_000)
        Thread.sleep(2_000)
        shot("2_editor")

        tap("Edit")
        shot("3_clip_tools")

        tap("Filters")
        Thread.sleep(1_500)
        shot("4_filters")
        device.pressBack()

        device.pressBack()
        tap("Audio")
        shot("5_audio")
        device.pressBack()

        tap("Canvas")
        shot("6_canvas")
        device.pressBack()

        tap("Export")
        device.wait(Until.hasObject(By.text("Resolution")), 10_000)
        shot("7_export")
    }
}
