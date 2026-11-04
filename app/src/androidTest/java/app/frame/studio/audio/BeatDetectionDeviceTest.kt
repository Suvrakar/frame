package app.frame.studio.audio

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Decodes a real AAC metronome (120 BPM, made by CI) with the device's decoder and tracks its beats. */
@RunWith(AndroidJUnit4::class)
class BeatDetectionDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun findsTheBeatOfARealAacFile() {
        val copy = File(context.filesDir, "beats.m4a")
        File("/data/local/tmp/beats.m4a").copyTo(copy, overwrite = true)
        val started = System.currentTimeMillis()
        val pcm = AudioPcmReader.readMono(context, Uri.fromFile(copy), 0, 12_000)
        assertNotNull("decode failed", pcm)
        assertTrue("too little audio: ${pcm!!.samples.size}", pcm.samples.size > AudioPcmReader.TARGET_RATE * 8)
        val result = BeatDetector.detect(pcm.samples, pcm.sampleRate)
        android.util.Log.i("FramePerf", "beats.ms=${System.currentTimeMillis() - started} bpm=${result?.bpm} count=${result?.beatsMs?.size}")
        assertNotNull(result)
        assertEquals(120f, result!!.bpm, 4f)
        // Clicks are every 500 ms starting at 0; detected beats sit within 50 ms of them.
        result.beatsMs.forEach { b ->
            val phase = b % 500
            assertTrue("beat $b", minOf(phase, 500 - phase) < 50)
        }
    }
}
