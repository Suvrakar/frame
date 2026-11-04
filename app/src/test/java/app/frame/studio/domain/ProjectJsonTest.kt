package app.frame.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectJsonTest {

    @Test
    fun roundTripsProject() {
        val project = Project(
            id = "p1",
            name = "Beach",
            createdAtEpochMs = 1_700_000_000_000,
            videoClips = listOf(
                VideoClip(
                    id = "v1",
                    sourceUri = "file://a.mp4",
                    sourceDurationMs = 5_000,
                    trimStartMs = 200,
                    trimEndMs = 4_800,
                    speed = 1.5f,
                    volume = 0.8f,
                    fadeInMs = 100,
                    fadeOutMs = 200,
                    rotationDegrees = 90,
                    crop = CropRect(0.1f, 0.1f, 0.9f, 0.9f),
                ),
            ),
            audioClips = listOf(
                AudioClip(
                    id = "a1",
                    sourceUri = "file://b.m4a",
                    sourceDurationMs = 4_000,
                    trimStartMs = 0,
                    trimEndMs = 4_000,
                    volume = 0.5f,
                    fadeInMs = 0,
                    fadeOutMs = 0,
                ),
            ),
            textOverlays = listOf(
                TextOverlay(
                    id = "t1",
                    text = "Hello",
                    startMs = 0,
                    endMs = 2_000,
                    x = 0.5f,
                    y = 0.8f,
                ),
            ),
            crossfadeMs = 250,
            exportHeight = ExportHeight.HD_720,
        )

        val decoded = ProjectJson.decode(ProjectJson.encode(project))
        assertEquals(project, decoded)
    }

    @Test
    fun legacyTextWithoutFontDecodesToDefault() {
        val base = Project(
            id = "p2", name = "Old", createdAtEpochMs = 1L,
            textOverlays = listOf(TextOverlay(id = "t1", text = "Hi", startMs = 0, endMs = 1_000)),
        )
        val legacy = ProjectJson.encode(base).replace(",\"font\":\"default\"", "")
        assertEquals(false, legacy.contains("\"font\""))
        assertEquals("default", ProjectJson.decode(legacy).textOverlays.single().font)
    }

    @Test
    fun roundTripsTextFont() {
        val project = Project(
            id = "p3", name = "Fonts", createdAtEpochMs = 1L,
            textOverlays = listOf(TextOverlay(id = "t1", text = "Hi", startMs = 0, endMs = 1_000, font = "pacifico")),
        )
        assertEquals("pacifico", ProjectJson.decode(ProjectJson.encode(project)).textOverlays.single().font)
    }
}
