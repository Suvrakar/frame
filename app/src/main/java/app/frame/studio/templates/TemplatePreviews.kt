package app.frame.studio.templates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import android.util.LruCache
import app.frame.studio.domain.ColorAdjust
import app.frame.studio.effects.Filters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Random

/**
 * Gallery previews rendered on the device with the real template renderers, so the gallery shows
 * exactly what the user will get. Sample "photos" are generated (soft party bokeh), so no stock
 * images ship with the app. Cached in memory and on disk.
 */
class TemplatePreviews(context: Context, lowEnd: Boolean) {

    private val dir = File(context.cacheDir, "template_previews_v$VERSION").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>(if (lowEnd) 6 shl 20 else 16 shl 20) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val limiter = Semaphore(if (lowEnd) 1 else 2)
    private val samples = mutableMapOf<Int, Bitmap>()

    fun cached(template: VideoTemplate, width: Int): Bitmap? = memory.get(key(template, width))

    suspend fun preview(template: VideoTemplate, width: Int): Bitmap? {
        val key = key(template, width)
        memory.get(key)?.let { return it }
        return limiter.withPermit {
            memory.get(key) ?: withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(dir, "$key.jpg")
                    val bitmap = if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else {
                        render(template, width).also { b -> file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
                    }
                    bitmap?.also { memory.put(key, it) }
                }.onFailure { Log.w("TemplatePreviews", "Preview failed for ${template.id}", it) }.getOrNull()
            }
        }
    }

    private fun key(t: VideoTemplate, width: Int) = "${t.id}_$width"

    fun render(template: VideoTemplate, width: Int, values: Map<TemplateField, String> = Templates.previewValues): Bitmap {
        val ratio = template.aspect.ratio ?: (9f / 16f)
        val w = width
        val h = (width / ratio).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val style = template.collage
        if (style != null) {
            val photos = List(style.cells) { sample(it + template.id.length, maxOf(w, h) / 2) }
            val header = template.header?.let { Templates.fillCard(it, values) } ?: ("" to "")
            val labels = template.photoLabels.map { Templates.fillCaption(it, values).orEmpty() }
            CollageRenderer.draw(canvas, template, style, photos, style.cells, header.first, header.second, labels, w.toFloat(), h.toFloat())
        } else {
            drawSlideshowCover(canvas, template, w.toFloat(), h.toFloat())
        }
        return bitmap
    }

    /** Video templates: a filtered sample shot with the intro title, like a video thumbnail. */
    private fun drawSlideshowCover(canvas: Canvas, template: VideoTemplate, w: Float, h: Float) {
        val photo = sample(template.id.length, maxOf(w, h).toInt())
        val filter = Filters.transformFor(template.filterId, 1f, ColorAdjust())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (!filter.isIdentity) colorFilter = ColorMatrixColorFilter(ColorMatrix(filter.toAndroid()))
        }
        CollageRenderer.drawCropped(canvas, photo, RectF(0f, 0f, w, h), 0f, paint)
        // Template colours wash over the shot so each design keeps its identity.
        val c = template.colors.map { it.toInt() }
        canvas.drawRect(0f, 0f, w, h, Paint().apply {
            shader = LinearGradient(0f, 0f, w, h, (c.first() and 0x00FFFFFF) or 0x66000000, (c.last() and 0x00FFFFFF) or 0x55000000, Shader.TileMode.CLAMP)
        })
        canvas.drawRect(0f, h * 0.5f, w, h, Paint().apply {
            shader = LinearGradient(0f, h * 0.5f, 0f, h, 0x00000000, 0xAA000000.toInt(), Shader.TileMode.CLAMP)
        })
        val card = template.intro ?: template.outro
        val (title, subtitle) = card?.let { Templates.fillCard(it, Templates.previewValues) } ?: (template.name to "")
        val m = minOf(w, h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            setShadowLayer(m * 0.02f, 0f, m * 0.006f, 0x88000000.toInt())
        }
        var size = m * 0.12f
        p.textSize = size
        while (p.measureText(title) > w * 0.88f && size > m * 0.05f) {
            size *= 0.92f
            p.textSize = size
        }
        canvas.drawText(title, w / 2, h * 0.84f, p)
        if (subtitle.isNotBlank()) {
            p.textSize = m * 0.045f
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.letterSpacing = 0.15f
            canvas.drawText(subtitle.uppercase(), w / 2, h * 0.84f + m * 0.08f, p)
        }
        card?.emoji?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it, w / 2, h * 0.84f - size * 1.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = m * 0.12f
                textAlign = Paint.Align.CENTER
            })
        }
    }

    /** A soft, out-of-focus "party" photo: warm gradient with bokeh lights. */
    @Suppress("UNUSED_PARAMETER")
    private fun sample(index: Int, size: Int): Bitmap = synchronized(samples) {
        val key = index % PALETTES.size
        // One small shared copy per palette (about 0.6 MB each); previews are thumbnails.
        samples[key] ?: generateSample(key, 400).also { samples[key] = it }
    }

    companion object {
        private const val VERSION = 1

        private val PALETTES = listOf(
            intArrayOf(0xFF2B1B3D.toInt(), 0xFF8E3B5E.toInt(), 0xFFF2A65A.toInt()),
            intArrayOf(0xFF0F2A3D.toInt(), 0xFF2E6F8E.toInt(), 0xFFF6D365.toInt()),
            intArrayOf(0xFF3D1F1F.toInt(), 0xFFB24A3B.toInt(), 0xFFFFC89A.toInt()),
            intArrayOf(0xFF1F2D1F.toInt(), 0xFF5E8C61.toInt(), 0xFFF4E3B2.toInt()),
            intArrayOf(0xFF2A1F3D.toInt(), 0xFF6A4C93.toInt(), 0xFFF7A8C4.toInt()),
            intArrayOf(0xFF3A2A18.toInt(), 0xFFA0703A.toInt(), 0xFFFFE1A8.toInt()),
        )

        fun generateSample(index: Int, size: Int): Bitmap {
            val w = size
            val h = (size * 1.25f).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val pal = PALETTES[index % PALETTES.size]
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), pal, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            })
            val rnd = Random(index * 31L + 7)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(26) {
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h * 0.75f
                val r = w * (0.04f + rnd.nextFloat() * 0.14f)
                val base = if (rnd.nextBoolean()) pal[2] else 0xFFFFF3D6.toInt()
                val a = 60 + rnd.nextInt(120)
                p.shader = RadialGradient(x, y, r, intArrayOf((base and 0x00FFFFFF) or (a shl 24), (base and 0x00FFFFFF) or ((a / 2) shl 24), base and 0x00FFFFFF), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(x, y, r, p)
            }
            // A soft, warm "subject" glow in the lower middle.
            p.shader = RadialGradient(w * 0.5f, h * 0.78f, w * 0.55f, intArrayOf((pal[2] and 0x00FFFFFF) or 0x88000000.toInt(), pal[2] and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(w * 0.5f, h * 0.78f, w * 0.55f, p)
            return bmp
        }
    }
}
