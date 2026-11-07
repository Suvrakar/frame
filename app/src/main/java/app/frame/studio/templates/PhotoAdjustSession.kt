package app.frame.studio.templates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import java.io.Closeable
import java.util.IdentityHashMap
import kotlin.math.ceil

/**
 * Live preview for framing photos inside a frame / collage template before the video is made.
 * Photos are decoded once at preview size; [render] redraws one page of the real design with the
 * user's fits and reports where each photo landed (for touch hit-testing). [render] and [close]
 * are serialised, so closing while a frame is being drawn is safe.
 */
class PhotoAdjustSession internal constructor(
    private val template: VideoTemplate,
    private val style: CollageStyle,
    val media: List<SlotMedia>,
    private val photos: List<Bitmap?>,
    private val title: String,
    private val subtitle: String,
    private val labels: List<String>,
    val width: Int,
    val height: Int,
) : Closeable {
    private var closed = false

    val pages: Int get() = ceil(media.size / style.cells.toFloat()).toInt().coerceAtLeast(1)

    /** Photo size in pixels (aspect is what matters), or null if it couldn't be loaded. */
    @Synchronized
    fun photoSize(uri: String): Pair<Float, Float>? = if (closed) null else
        media.indexOfFirst { it.uri == uri }.takeIf { it >= 0 }?.let { photos[it] }?.let { it.width.toFloat() to it.height.toFloat() }

    /** Draws page [page] with every photo shown; returns the bitmap and each photo's slot (by uri). */
    @Synchronized
    fun render(page: Int, fits: Map<String, PhotoFit>): Pair<Bitmap, Map<String, RectF>> {
        if (closed) return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) to emptyMap()
        val from = page * style.cells
        val slice = media.drop(from).take(style.cells)
        val bitmaps = photos.drop(from).take(style.cells)
        val byBitmap = IdentityHashMap<Bitmap, PhotoFit>()
        slice.forEachIndexed { i, m -> bitmaps[i]?.let { b -> fits[m.uri]?.let { byBitmap[b] = it } } }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val (_, landed) = CollageRenderer.withFits(byBitmap) {
            CollageRenderer.draw(Canvas(out), template, style, bitmaps, style.cells, title, subtitle, labels, width.toFloat(), height.toFloat())
        }
        val slots = HashMap<String, RectF>()
        slice.forEachIndexed { i, m -> bitmaps[i]?.let { b -> landed[b]?.let { slots[m.uri] = it } } }
        return out to slots
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        photos.forEach { it?.recycle() }
    }

    companion object {
        /** Preview pages are drawn with this many pixels on their long side. */
        const val PREVIEW_LONG_SIDE = 960

        internal fun open(
            context: Context,
            template: VideoTemplate,
            values: Map<TemplateField, String>,
            media: List<SlotMedia>,
            outputWidth: Int,
            outputHeight: Int,
        ): PhotoAdjustSession? {
            val style = template.collage ?: return null
            val scale = PREVIEW_LONG_SIDE / maxOf(outputWidth, outputHeight).toFloat()
            val w = (outputWidth * scale).toInt().coerceAtLeast(2)
            val h = (outputHeight * scale).toInt().coerceAtLeast(2)
            val header = template.header?.let { Templates.fillCard(it, values) } ?: ("" to "")
            val labels = template.photoLabels.map { Templates.fillCaption(it, values).orEmpty() }
            val maxSide = (PREVIEW_LONG_SIDE * 0.6f).toInt()
            val photos = media.map { CollageRenderer.loadPhoto(context, Uri.parse(it.uri), it.isImage, maxSide) }
            return PhotoAdjustSession(template, style, media, photos, header.first, header.second, labels, w, h)
        }
    }
}
