package app.frame.studio.magic

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.media.ExifInterface
import app.frame.studio.audio.AudioPcmReader
import app.frame.studio.audio.BeatDetector
import app.frame.studio.data.ImageNormalizer
import app.frame.studio.data.MediaProbe
import app.frame.studio.data.ProjectRepository
import app.frame.studio.device.DeviceProfile
import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Magic Movie: looks at the user's picked photos and videos on the device (sharpness,
 * exposure, duplicates, capture date), finds the music's beat, and assembles a finished,
 * editable project. Nothing leaves the phone.
 */
class MagicMovieBuilder(
    private val context: Context,
    private val repository: ProjectRepository,
    private val probe: MediaProbe,
    private val normalizer: ImageNormalizer,
    private val device: DeviceProfile,
) {

    data class Progress(val done: Int, val total: Int, val stage: String) {
        val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
    }

    suspend fun create(
        uris: List<Uri>,
        musicUri: Uri?,
        title: String,
        vibe: MagicVibe,
        length: MagicLength,
        aspect: CanvasAspect,
        onProgress: (Progress) -> Unit,
    ): Project {
        val total = uris.size + (if (musicUri != null) 1 else 0) + 1
        var done = 0
        val assets = mutableListOf<MagicAsset>()
        uris.forEachIndexed { index, uri ->
            coroutineContext.ensureActive()
            onProgress(Progress(done, total, "Finding your best moments"))
            analyze(uri, index)?.let { assets += it }
            done++
        }
        require(assets.isNotEmpty()) { "Those files couldn't be opened" }

        val music = musicUri?.let {
            onProgress(Progress(done, total, "Listening for the beat"))
            analyzeMusic(it).also { done++ }
        }

        onProgress(Progress(done, total, "Cutting your movie"))
        val project = MagicPlan.build(
            assets = assets,
            vibe = vibe,
            length = length,
            title = title,
            subtitle = MagicPlan.dateSpan(assets, ::monthYear, ::month, ::year),
            music = music,
            aspect = aspect,
            projectId = UUID.randomUUID().toString(),
            nowMs = System.currentTimeMillis(),
            newId = { UUID.randomUUID().toString().take(12) },
        ).copy(exportHeight = device.defaultExportHeight)
        repository.save(project)
        onProgress(Progress(total, total, "Done"))
        return project
    }

    private suspend fun analyze(uri: Uri, index: Int): MagicAsset? {
        probe.persistPermission(uri)
        return runCatching {
            if (probe.isImage(uri)) {
                val name = probe.probeImage(uri)?.displayName.orEmpty()
                val normalized = normalizer.normalize(uri) ?: return null
                val stats = probe.imageThumbnail(normalized.uri.toString(), THUMB)?.let(::stats)
                MagicAsset(
                    uri = normalized.uri.toString(), isImage = true, durationMs = 0,
                    width = normalized.width, height = normalized.height, displayName = name,
                    takenAtMs = photoDate(uri),
                    score = stats?.let(ImageStats::score) ?: 0.5f,
                    hash = stats?.hash ?: index.toLong(),
                    pickIndex = index,
                )
            } else {
                val info = probe.probeVideo(uri)?.takeIf { it.durationMs >= 500 } ?: return null
                // Sample a few moments; keep the sharpest, best-exposed one.
                val candidates = listOf(0.2f, 0.45f, 0.7f).map { (info.durationMs * it).toLong() }
                val scored = candidates.mapNotNull { at ->
                    probe.thumbnail(uri.toString(), at, THUMB)?.let(::stats)?.let { at to it }
                }
                val best = scored.maxByOrNull { ImageStats.score(it.second) }
                MagicAsset(
                    uri = uri.toString(), isImage = false, durationMs = info.durationMs,
                    width = info.width, height = info.height, displayName = info.displayName,
                    takenAtMs = videoDate(uri),
                    // Videos carry motion and sound; nudge them up so a movie isn't all stills.
                    score = ((best?.second?.let(ImageStats::score) ?: 0.5f) + 0.1f).coerceAtMost(1f),
                    hash = best?.second?.hash ?: index.toLong().inv(),
                    pickIndex = index,
                    bestMs = best?.first ?: (info.durationMs / 3),
                )
            }
        }.onFailure { Log.w(TAG, "Couldn't analyse $uri", it) }.getOrNull()
    }

    private suspend fun analyzeMusic(uri: Uri): MagicMusic? = withContext(Dispatchers.Default) {
        probe.persistPermission(uri)
        val info = probe.probeAudio(uri) ?: return@withContext null
        // The first 90 s is plenty to lock onto the tempo. Beat finding is a bonus: if decoding or
        // detection fails on an unusual file, keep the song and just don't cut on the beat.
        val pcm = try {
            AudioPcmReader.readMono(context, uri, 0, minOf(info.durationMs, 90_000))
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't decode $uri for beats", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory decoding $uri for beats", e)
            null
        }
        val beats = pcm?.let {
            try {
                BeatDetector.detect(it.samples, it.sampleRate)
            } catch (e: Exception) {
                Log.w(TAG, "Beat detection failed for $uri", e)
                null
            }
        }
        val tempo = beats?.beatsMs?.zipWithNext { a, b -> b - a }?.sorted()?.let { it.getOrNull(it.size / 2) }
        // Extend the detected grid across the whole song so long movies stay on beat.
        val all = if (beats != null && tempo != null && tempo > 0 && beats.beatsMs.isNotEmpty()) {
            val last = beats.beatsMs.last()
            beats.beatsMs + generateSequence(last + tempo) { it + tempo }.takeWhile { it < info.durationMs }.toList()
        } else {
            emptyList()
        }
        MagicMusic(uri.toString(), info.durationMs, info.displayName, all.map { it + (pcm?.startMs ?: 0) }, beats?.bpm ?: 0f)
    }

    private fun stats(bitmap: Bitmap): ImageStats.Stats? {
        if (bitmap.isRecycled || bitmap.config == Bitmap.Config.HARDWARE) return null
        val scale = THUMB.toFloat() / maxOf(bitmap.width, bitmap.height)
        val src = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(3), (bitmap.height * scale).toInt().coerceAtLeast(3), true)
        } else bitmap
        try {
            val w = src.width
            val h = src.height
            if (w < 3 || h < 3) return null
            val pixels = IntArray(w * h)
            src.getPixels(pixels, 0, w, 0, 0, w, h)
            val luma = FloatArray(w * h) { i ->
                val p = pixels[i]
                (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f
            }
            return ImageStats.analyze(luma, w, h)
        } finally {
            // Thumbnails are shared through the probe's cache; only free our own copy.
            if (src !== bitmap) src.recycle()
        }
    }

    private fun photoDate(uri: Uri): Long? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            raw?.let { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time }
        }
    }.getOrNull()

    private fun videoDate(uri: Uri): Long? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val raw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) ?: return@runCatching null
            // e.g. 20260614T183012.000Z
            SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(raw.take(15))?.time?.takeIf { it > 0 && Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.YEAR) > 1971 }
        } finally {
            r.release()
        }
    }.getOrNull()

    private fun monthYear(ms: Long): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(ms)
    private fun month(ms: Long): String = SimpleDateFormat("MMMM", Locale.getDefault()).format(ms)
    private fun year(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)

    private companion object {
        const val TAG = "MagicMovie"
        const val THUMB = 128
    }
}
