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
        // Clicks are every 500 ms starting at 0. Typical error must be small; single beats may drift
        // a little more (decoder priming differs between devices) but stay below what's audible.
        // Signed error to the nearest click: negative = early, positive = late.
        val signed = result.beatsMs.map { b -> (b % 500).let { if (it > 250) it - 500 else it } }
        val errors = signed.map { kotlin.math.abs(it) }
        val median = errors.sorted()[errors.size / 2]
        android.util.Log.i("FramePerf", "beats.signed=$signed encoderDelay=${AudioPcmReader.lastEncoderDelay}")
        // Under two video frames: cuts look on-beat. Decoders differ by a few tens of ms.
        assertTrue("median beat error $median ms", median < 60)
        errors.forEachIndexed { i, e -> assertTrue("beat ${result.beatsMs[i]} off by $e ms", e < 90) }
    }
}
