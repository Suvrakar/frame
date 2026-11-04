package app.frame.studio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Phone photos are often 12–50 MP. Decoding those at full size costs 48–200 MB of RAM per frame,
 * which stalls or kills low-end devices. Oversized photos are copied once, downscaled to what
 * the export can use and with EXIF rotation applied; the copy is what the editor and exporter read.
 */
class ImageNormalizer(private val context: Context, private val maxLongSide: Int) {

    data class Result(val uri: Uri, val width: Int, val height: Int, val resized: Boolean)

    private val dir = File(context.filesDir, "media").apply { mkdirs() }

    suspend fun normalize(source: Uri): Result? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return@withContext null
            val rotation = exifRotation(source)
            val (dw, dh) = if (rotation % 180 != 0) h to w else w to h
            if (maxOf(w, h) <= maxLongSide) return@withContext Result(source, dw, dh, resized = false)

            val target = File(dir, "${key(source)}_$maxLongSide.jpg")
            if (target.exists() && target.length() > 0) {
                val existing = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.absolutePath, existing)
                return@withContext Result(Uri.fromFile(target), existing.outWidth, existing.outHeight, resized = true)
            }

            // Power-of-two subsampling first (cheap, low memory), then an exact scale.
            var sample = 1
            while (maxOf(w, h) / (sample * 2) >= maxLongSide) sample *= 2
            val decoded = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            val scale = maxLongSide.toFloat() / maxOf(decoded.width, decoded.height)
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0) postRotate(rotation.toFloat())
            }
            val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (out !== decoded) decoded.recycle()
            val tmp = File(dir, target.name + ".tmp")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            tmp.renameTo(target)
            val result = Result(Uri.fromFile(target), out.width, out.height, resized = true)
            out.recycle()
            result
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Not enough memory to downscale $source", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not normalize $source", e)
            null
        }
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

    private fun key(uri: Uri): String =
        MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)

    companion object {
        private const val TAG = "ImageNormalizer"
    }
}
