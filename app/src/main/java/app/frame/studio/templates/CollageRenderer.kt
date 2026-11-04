package app.frame.studio.templates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import app.frame.studio.domain.ColorAdjust
import app.frame.studio.effects.Filters
import java.io.File

/**
 * Draws a multi-photo collage frame: template background, title, then the photos in their
 * layout (grid, hero, strips, polaroids or film strip). Slots not yet "filled" show a soft
 * placeholder, which is how the photos appear to pop in one by one.
 */
object CollageRenderer {

    private const val TAG = "CollageRenderer"

    fun render(
        template: VideoTemplate,
        style: CollageStyle,
        photos: List<Bitmap?>,
        visible: Int,
        title: String,
        subtitle: String,
        labels: List<String>,
        width: Int,
        height: Int,
        out: File,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            draw(Canvas(bitmap), template, style, photos, visible, title, subtitle, labels, width.toFloat(), height.toFloat())
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        } finally {
            bitmap.recycle()
        }
        return out
    }

    fun draw(
        canvas: Canvas,
        template: VideoTemplate,
        style: CollageStyle,
        photos: List<Bitmap?>,
        visible: Int,
        title: String,
        subtitle: String,
        labels: List<String>,
        w: Float,
        h: Float,
    ) {
        val minSide = minOf(w, h)
        CardRenderer.drawBackground(canvas, template, w, h)
        val onColor = Templates.onColor(template).toInt()
        val dark = onColor != 0xFFFFFFFF.toInt()
        if (style.ownTitle) {
            val filter = Filters.transformFor(template.filterId, 1f, ColorAdjust())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                if (!filter.isIdentity) colorFilter = ColorMatrixColorFilter(ColorMatrix(filter.toAndroid()))
            }
            FrameRenderer.draw(canvas, template, style, photos, visible, title, subtitle, labels, w, h, paint)
            return
        }
        drawHeader(canvas, title, subtitle, onColor, dark, w, h)

        val cells = CollageLayout.cells(style, w, h)
        if (style == CollageStyle.FILMSTRIP) drawFilmStrip(canvas, cells, w, h)

        val filter = Filters.transformFor(template.filterId, 1f, ColorAdjust())
        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (!filter.isIdentity) colorFilter = ColorMatrixColorFilter(ColorMatrix(filter.toAndroid()))
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            setShadowLayer(minSide * 0.02f, 0f, minSide * 0.008f, 0x55000000)
        }
        val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) 0x22000000 else 0x33FFFFFF }
        val radius = minSide * 0.025f

        cells.forEachIndexed { i, cell ->
            val shown = i < visible
            val photo = photos.getOrNull(i)
            canvas.save()
            canvas.rotate(cell.rotation, cell.centerX, cell.centerY)
            if (style == CollageStyle.POLAROIDS) {
                val frame = RectF(cell.left, cell.top, cell.right, cell.bottom)
                if (shown) {
                    canvas.drawRect(frame, shadow)
                    val inset = cell.width * 0.06f
                    val photoRect = RectF(frame.left + inset, frame.top + inset, frame.right - inset, frame.bottom - cell.height * 0.2f)
                    if (photo != null) drawCropped(canvas, photo, photoRect, 0f, photoPaint)
                    labels.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { label ->
                        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF3A3A3A.toInt()
                            textSize = cell.height * 0.075f
                            typeface = Typeface.create("casual", Typeface.NORMAL)
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText(label, frame.centerX(), frame.bottom - cell.height * 0.07f, labelPaint)
                    }
                } else {
                    canvas.drawRect(frame, placeholder)
                }
            } else {
                val rect = RectF(cell.left, cell.top, cell.right, cell.bottom)
                if (shown) {
                    if (style != CollageStyle.FILMSTRIP) {
                        // Thin white mat with a soft shadow.
                        val mat = minSide * 0.008f
                        canvas.drawRoundRect(RectF(rect.left - mat, rect.top - mat, rect.right + mat, rect.bottom + mat), radius + mat, radius + mat, shadow)
                    }
                    if (photo != null) drawCropped(canvas, photo, rect, if (style == CollageStyle.FILMSTRIP) radius * 0.4f else radius, photoPaint)
                } else {
                    canvas.drawRoundRect(rect, radius, radius, placeholder)
                }
            }
            canvas.restore()
        }
    }

    private fun drawHeader(canvas: Canvas, title: String, subtitle: String, onColor: Int, dark: Boolean, w: Float, h: Float) {
        if (title.isBlank()) return
        val minSide = minOf(w, h)
        val band = h * CollageLayout.HEADER_FRACTION
        val maxWidth = (w * 0.88f).toInt()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onColor
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            if (!dark) setShadowLayer(minSide * 0.015f, 0f, minSide * 0.004f, 0x55000000)
        }
        var size = minSide * 0.085f
        var layout: StaticLayout
        while (true) {
            paint.textSize = size
            layout = StaticLayout.Builder.obtain(title, 0, title.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .build()
            if ((layout.lineCount <= 1 && layout.height < band * 0.6f) || size < minSide * 0.04f) break
            size *= 0.9f
        }
        val subSize = minSide * 0.032f
        val blockH = layout.height + if (subtitle.isNotBlank()) subSize * 1.6f else 0f
        val top = (band - blockH) / 2 + minSide * 0.01f
        canvas.save()
        canvas.translate((w - maxWidth) / 2f, top)
        layout.draw(canvas)
        canvas.restore()
        if (subtitle.isNotBlank()) {
            val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = onColor
                alpha = 215
                textSize = subSize
                letterSpacing = 0.2f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(subtitle.uppercase(), w / 2f, top + layout.height + subSize * 1.3f, sub)
        }
    }

    private fun drawFilmStrip(canvas: Canvas, cells: List<Cell>, w: Float, h: Float) {
        val minSide = minOf(w, h)
        val portrait = h >= w
        val first = cells.first()
        val last = cells.last()
        val strip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0141414.toInt() }
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCF5F0E6.toInt() }
        if (portrait) {
            val inset = first.width * 0.14f / 0.72f
            val rect = RectF(first.left - inset, first.top - inset * 0.6f, first.right + inset, last.bottom + inset * 0.6f)
            canvas.drawRoundRect(rect, minSide * 0.01f, minSide * 0.01f, strip)
            val holeW = inset * 0.42f
            val holeH = holeW * 0.7f
            var y = rect.top + holeH
            while (y < rect.bottom - holeH) {
                canvas.drawRoundRect(rect.left + (inset - holeW) / 2, y, rect.left + (inset + holeW) / 2, y + holeH, holeH * 0.25f, holeH * 0.25f, hole)
                canvas.drawRoundRect(rect.right - (inset + holeW) / 2, y, rect.right - (inset - holeW) / 2, y + holeH, holeH * 0.25f, holeH * 0.25f, hole)
                y += holeH * 2.2f
            }
        } else {
            val inset = first.height * 0.14f / 0.72f
            val rect = RectF(first.left - inset * 0.6f, first.top - inset, last.right + inset * 0.6f, first.bottom + inset)
            canvas.drawRoundRect(rect, minSide * 0.01f, minSide * 0.01f, strip)
            val holeH = inset * 0.42f
            val holeW = holeH * 0.7f
            var x = rect.left + holeW
            while (x < rect.right - holeW) {
                canvas.drawRoundRect(x, rect.top + (inset - holeH) / 2, x + holeW, rect.top + (inset + holeH) / 2, holeW * 0.25f, holeW * 0.25f, hole)
                canvas.drawRoundRect(x, rect.bottom - (inset + holeH) / 2, x + holeW, rect.bottom - (inset - holeH) / 2, holeW * 0.25f, holeW * 0.25f, hole)
                x += holeW * 2.2f
            }
        }
    }

    /** Center-crops [bitmap] into [dst] with rounded corners. */
    internal fun drawCropped(canvas: Canvas, bitmap: Bitmap, dst: RectF, radius: Float, paint: Paint) {
        val scale = maxOf(dst.width() / bitmap.width, dst.height() / bitmap.height)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(
                dst.left + (dst.width() - bitmap.width * scale) / 2,
                dst.top + (dst.height() - bitmap.height * scale) / 2,
            )
        }
        canvas.save()
        if (radius > 0f) canvas.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
        else canvas.clipRect(dst)
        canvas.drawBitmap(bitmap, matrix, paint)
        canvas.restore()
    }

    /** Loads a photo, or a still from a video, at most [maxSide] px on its long side. */
    fun loadPhoto(context: Context, uri: Uri, isImage: Boolean, maxSide: Int): Bitmap? = try {
        if (isImage) loadImage(context, uri, maxSide) else loadVideoStill(context, uri, maxSide)
    } catch (e: Exception) {
        Log.w(TAG, "Could not load $uri", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Out of memory loading $uri", e)
        null
    }

    private fun loadImage(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        if (rotation == 0) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun loadVideoStill(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val atUs = duration * 1000 / 3
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                r.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)
            } else {
                r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                    val scale = maxSide.toFloat() / maxOf(full.width, full.height)
                    if (scale >= 1f) full else Bitmap.createScaledBitmap(
                        full,
                        (full.width * scale).toInt().coerceAtLeast(1),
                        (full.height * scale).toInt().coerceAtLeast(1),
                        true,
                    ).also { if (it !== full) full.recycle() }
                }
            }
        } finally {
            runCatching { r.release() }
        }
    }
}
