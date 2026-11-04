package app.frame.studio.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Reads duration, size and thumbnails from user-picked media. */
class MediaProbe(private val context: Context) {

    data class VideoInfo(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val displayName: String,
    )

    data class AudioInfo(val durationMs: Long, val displayName: String)

    data class ImageInfo(val width: Int, val height: Int, val displayName: String)

    fun isImage(uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.startsWith("image/") == true ||
            uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif")

    suspend fun probeImage(uri: Uri): ImageInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) return@runCatching null
            val rotated = exifRotation(uri) % 180 != 0
            ImageInfo(
                width = if (rotated) options.outHeight else options.outWidth,
                height = if (rotated) options.outWidth else options.outHeight,
                displayName = displayName(uri),
            )
        }.onFailure { Log.w(TAG, "Failed to read image $uri", it) }.getOrNull()
    }

    private fun exifRotation(uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    /** Thumbnail of a photo, honoring EXIF orientation. */
    suspend fun imageThumbnail(uri: String, maxSize: Int = 320): Bitmap? {
        val key = "img:$uri@$maxSize"
        thumbnails.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
                val decoded = context.contentResolver.openInputStream(parsed)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching null
                val rotation = exifRotation(parsed)
                if (rotation == 0) decoded else {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                }
            }.getOrNull()
        }?.also { thumbnails.put(key, it) }
    }

    private val thumbnails = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Keeps read access to a document across app restarts. Returns false if not persistable. */
    fun persistPermission(uri: Uri): Boolean = runCatching {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        true
    }.getOrElse {
        Log.w(TAG, "Could not persist permission for $uri", it)
        false
    }

    fun releasePermission(uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun isReadable(uri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    suspend fun probeVideo(uri: Uri): VideoInfo? = withContext(Dispatchers.IO) {
        withRetriever(uri) { r ->
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return@withRetriever null
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return@withRetriever null
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val rotated = rotation % 180 != 0
            VideoInfo(
                durationMs = duration,
                width = if (rotated) h else w,
                height = if (rotated) w else h,
                displayName = displayName(uri),
            )
        }
    }

    suspend fun probeAudio(uri: Uri): AudioInfo? = withContext(Dispatchers.IO) {
        withRetriever(uri) { r ->
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return@withRetriever null
            AudioInfo(duration, displayName(uri))
        }
    }

    suspend fun thumbnail(uri: String, atMs: Long, maxSize: Int = 320): Bitmap? {
        val key = "$uri@$atMs@$maxSize"
        thumbnails.get(key)?.let { return it }
        return frameLimiter.withPermit { thumbnails.get(key) ?: extractFrame(uri, atMs, maxSize) }?.also { thumbnails.put(key, it) }
    }

    /** Limits concurrent decoders; filmstrips request many frames at once. */
    private val frameLimiter = Semaphore(3)

    private suspend fun extractFrame(uri: String, atMs: Long, maxSize: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            withRetriever(Uri.parse(uri)) { r ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    r.getScaledFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSize, maxSize)
                } else {
                    r.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                        val scale = maxSize.toFloat() / maxOf(full.width, full.height)
                        if (scale >= 1f) {
                            full
                        } else {
                            Bitmap.createScaledBitmap(
                                full,
                                (full.width * scale).toInt().coerceAtLeast(1),
                                (full.height * scale).toInt().coerceAtLeast(1),
                                true,
                            ).also { if (it !== full) full.recycle() }
                        }
                    }
                }
            }
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    private inline fun <T> withRetriever(uri: Uri, block: (MediaMetadataRetriever) -> T?): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            block(retriever)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read $uri", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val TAG = "MediaProbe"
    }
}
