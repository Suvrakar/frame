package app.frame.studio.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPolicyTest {
    private class FakeTable(
        val video: Map<String, List<VideoDecoder>> = emptyMap(),
        val audio: Map<String, List<AudioDecoder>> = emptyMap(),
    ) : DecoderTable {
        override fun video(mime: String) = video[mime].orEmpty()
        override fun audio(mime: String) = audio[mime].orEmpty()
    }

    private fun dec(
        hw: Boolean = true, maxW: Int = 4096, maxH: Int = 4096,
        rateOk: (Int, Int, Int) -> Boolean = { _, _, _ -> true },
    ) = VideoDecoder(
        name = if (hw) "hw" else "sw", hardware = hw, maxWidth = maxW, maxHeight = maxH,
        supports = { w, h, f -> w <= maxW && h <= maxH && rateOk(w, h, f) },
        supportsSize = { w, h -> w <= maxW && h <= maxH },
    )

    private fun table(vararg d: VideoDecoder) = FakeTable(video = mapOf("video/hevc" to d.toList()))
    private fun track(w: Int, h: Int, fps: Float = 30f, hdr: Boolean = false) =
        VideoTrack("video/hevc", w, h, fps, hdr)

    @Test fun missingCodecIsUnsupported() {
        val v = ImportPolicy.video(track(1920, 1080), FakeTable(), false)
        assertEquals(VideoVerdict.Unsupported("This video's format (HEVC) isn't supported on this phone."), v)
    }

    @Test fun tooLargeButVerifiedByDecodingIsHeavy() {
        // Snapdragon 685: declares 1920x1920 for H.264 yet decodes 4K.
        val v = ImportPolicy.video(track(3840, 2160, fps = 60f), table(dec(maxW = 1920, maxH = 1920)), false, decodeVerified = true)
        assertTrue("expected Heavy, was $v", v is VideoVerdict.Heavy)
    }

    @Test fun tooLargeNamesBothSizes() {
        val v = ImportPolicy.video(track(7680, 4320), table(dec(maxW = 4096, maxH = 2304)), false)
        assertTrue(v is VideoVerdict.Unsupported)
        val m = (v as VideoVerdict.Unsupported).message
        assertTrue(m, m.contains("8K") && m.contains("4K"))
        assertEquals("This 8K video is too large for this phone (up to 4K).", m)
    }

    @Test fun softwareOnlyIsHeavy() {
        val v = ImportPolicy.video(track(1920, 1080), table(dec(hw = false)), false)
        assertTrue(v is VideoVerdict.Heavy)
    }

    @Test fun sizeOkButRateNotIsHeavy() {
        val v = ImportPolicy.video(track(3840, 2160, 60f), table(dec(rateOk = { _, _, f -> f <= 30 })), false)
        assertTrue(v is VideoVerdict.Heavy)
    }

    @Test fun lowEnd4k60IsHeavy() {
        assertTrue(ImportPolicy.video(track(3840, 2160, 60f), table(dec()), true) is VideoVerdict.Heavy)
    }

    @Test fun lowEndHdrIsHeavy() {
        assertTrue(ImportPolicy.video(track(1920, 1080, 30f, hdr = true), table(dec()), true) is VideoVerdict.Heavy)
    }

    @Test fun highEndHdrIsOk() {
        assertEquals(VideoVerdict.Ok, ImportPolicy.video(track(1920, 1080, 30f, hdr = true), table(dec()), false))
    }

    @Test fun lowEnd1080p30IsOk() {
        assertEquals(VideoVerdict.Ok, ImportPolicy.video(track(1920, 1080, 30f), table(dec()), true))
    }

    @Test fun unknownFpsTreatedAs30() {
        assertEquals(VideoVerdict.Ok, ImportPolicy.video(track(1920, 1080, 0f), table(dec(rateOk = { _, _, f -> f == 30 })), true))
    }

    @Test fun rotatedPortraitJudgedCorrectly() {
        val landscapeOnly = VideoDecoder("hw", true, 4096, 2304,
            supports = { w, h, _ -> w <= 4096 && h <= 2304 }, supportsSize = { w, h -> w <= 4096 && h <= 2304 })
        assertEquals(VideoVerdict.Ok, ImportPolicy.video(track(2160, 3840), table(landscapeOnly), false))
        val v = ImportPolicy.video(track(4320, 7680), table(landscapeOnly), false)
        assertEquals("This 8K video is too large for this phone (up to 4K).", (v as VideoVerdict.Unsupported).message)
    }

    @Test fun friendlySizes() {
        assertEquals("8K", ImportPolicy.friendlySize(4320))
        assertEquals("4K", ImportPolicy.friendlySize(2160))
        assertEquals("2K", ImportPolicy.friendlySize(1440))
        assertEquals("1080p", ImportPolicy.friendlySize(1080))
        assertEquals("720p", ImportPolicy.friendlySize(720))
        assertEquals("480p", ImportPolicy.friendlySize(480))
    }

    @Test fun audioSupport() {
        val t = FakeTable(audio = mapOf("audio/mp4a-latm" to listOf(AudioDecoder("aac"))))
        assertTrue(ImportPolicy.audioSupported(AudioTrack("audio/mp4a-latm", 44100, 2), t))
        assertFalse(ImportPolicy.audioSupported(AudioTrack("audio/ac3", 48000, 6), t))
    }
}
