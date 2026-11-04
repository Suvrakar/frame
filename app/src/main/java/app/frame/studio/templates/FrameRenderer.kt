package app.frame.studio.templates

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Random
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The decorated photo-frame designs (taped polaroid, circle frame, cut-out letters, invitation,
 * bunting, scrapbook, photo booth). Everything is drawn with Canvas: no image assets.
 */
internal object FrameRenderer {

    private val SCRIPT = Typeface.create("cursive", Typeface.BOLD)
    private val BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val SERIF = Typeface.create("serif", Typeface.NORMAL)
    private val CASUAL = Typeface.create("casual", Typeface.BOLD)
    private val RANSOM_FACES = listOf(
        Typeface.create("serif", Typeface.BOLD), BLACK, Typeface.create("monospace", Typeface.BOLD), CASUAL,
        Typeface.create("sans-serif-condensed", Typeface.BOLD),
    )

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
        photoPaint: Paint,
    ) {
        val m = min(w, h)
        val rnd = Random(template.id.hashCode().toLong())
        val on = Templates.onColor(template).toInt()
        val accent = template.accent.toInt()
        val palette = listOf(accent) + template.colors.map { it.toInt() } + listOf(0xFFFFFFFF.toInt())
        val cells = CollageLayout.cells(style, w, h)
        val ctx = Ctx(canvas, m, w, h, on, accent, palette, rnd, photoPaint)

        when (style) {
            CollageStyle.TAPED -> {
                ctx.confetti(26)
                ctx.titleBlock(title, subtitle, h * 0.03f, h * 0.19f, SCRIPT, 0.15f)
                ctx.print(cells[0], photos.getOrNull(0), visible > 0, labels.getOrNull(0), tape = true)
            }
            CollageStyle.CIRCLE -> {
                ctx.confetti(30)
                ctx.titleBlock(title, subtitle, h * 0.06f, h * 0.22f, BLACK, 0.12f)
                ctx.circle(cells[0], photos.getOrNull(0), visible > 0)
                labels.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { ctx.pill(it, w / 2, cells[0].bottom + m * 0.11f) }
            }
            CollageStyle.RANSOM -> {
                ctx.confetti(18)
                val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val half = (words.size + 1) / 2
                val top = words.take(half).joinToString(" ")
                val bottom = words.drop(half).joinToString(" ")
                ctx.print(cells[0], photos.getOrNull(0), visible > 0, null, tape = false, border = 0.035f)
                ctx.ransom(top.uppercase(), w / 2, cells[0].top - m * 0.02f, w * 0.9f, m * 0.15f)
                if (bottom.isNotBlank()) ctx.ransom(bottom, w / 2, cells[0].bottom + m * 0.03f, w * 0.92f, m * 0.13f)
                if (subtitle.isNotBlank()) ctx.line(subtitle, w / 2, h - m * 0.05f, m * 0.04f, MEDIUM, on, spacing = 0.15f)
            }
            CollageStyle.INVITE -> ctx.invite(cells[0], photos.getOrNull(0), visible > 0, title, subtitle, labels)
            CollageStyle.BUNTING -> {
                ctx.confetti(20)
                ctx.bunting(h * 0.035f, h * 0.1f)
                ctx.print(cells[0], photos.getOrNull(0), visible > 0, null, tape = false, border = 0.03f)
                ctx.titleBlock(title, subtitle, cells[0].bottom + m * 0.04f, h - m * 0.03f, SCRIPT, 0.14f)
            }
            CollageStyle.SCRAPBOOK -> {
                ctx.confetti(16)
                cells.forEachIndexed { i, c -> ctx.print(c, photos.getOrNull(i), i < visible, labels.getOrNull(i), tape = i % 2 == 0, border = 0.035f) }
                val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val half = (words.size + 1) / 2
                val center = cells[4]
                ctx.ransom(words.take(half).joinToString(" ").uppercase(), w / 2, center.bottom - center.height * 0.36f, w * 0.86f, m * 0.12f)
                if (words.size > half) ctx.ransom(words.drop(half).joinToString(" ").uppercase(), w / 2, center.bottom - center.height * 0.36f + m * 0.14f, w * 0.9f, m * 0.11f)
            }
            CollageStyle.BOOTH -> {
                ctx.confetti(14)
                ctx.titleBlock(title, subtitle, h * 0.03f, h * 0.21f, SCRIPT, 0.14f)
                ctx.print(cells[0], photos.getOrNull(0), visible > 0, null, tape = true, border = 0.03f)
                val strip = RectF(cells[1].left - m * 0.025f, cells[1].top - m * 0.025f, cells[1].right + m * 0.025f, cells[3].bottom + m * 0.025f)
                canvas.save()
                canvas.rotate(3f, strip.centerX(), strip.centerY())
                canvas.drawRoundRect(strip, m * 0.01f, m * 0.01f, ctx.shadowPaint())
                for (i in 1..3) ctx.photoOrPlaceholder(cells[i], photos.getOrNull(i), i < visible, 0f)
                canvas.restore()
            }
            else -> Unit
        }
    }

    private class Ctx(
        val canvas: Canvas,
        val m: Float,
        val w: Float,
        val h: Float,
        val on: Int,
        val accent: Int,
        val palette: List<Int>,
        val rnd: Random,
        val photoPaint: Paint,
    ) {
        fun shadowPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            setShadowLayer(m * 0.025f, 0f, m * 0.01f, 0x55000000)
        }

        fun photoOrPlaceholder(c: Cell, photo: Bitmap?, shown: Boolean, radius: Float) {
            val r = RectF(c.left, c.top, c.right, c.bottom)
            if (shown && photo != null) CollageRenderer.drawCropped(canvas, photo, r, radius, photoPaint)
            else canvas.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 })
        }

        /** A white-bordered print with an optional handwritten caption and washi tape. */
        fun print(c: Cell, photo: Bitmap?, shown: Boolean, caption: String?, tape: Boolean, border: Float = 0.06f) {
            canvas.save()
            canvas.rotate(c.rotation, c.centerX, c.centerY)
            val frame = RectF(c.left, c.top, c.right, c.bottom)
            if (!shown) {
                canvas.drawRect(frame, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
                canvas.restore()
                return
            }
            canvas.drawRect(frame, shadowPaint())
            val inset = c.width * border
            val bottomBand = if (caption.isNullOrBlank() && border < 0.05f) inset else c.height * 0.18f
            val photoRect = Cell(frame.left + inset, frame.top + inset, frame.right - inset, frame.bottom - bottomBand)
            photoOrPlaceholder(photoRect, photo, true, 0f)
            caption?.takeIf { it.isNotBlank() }?.let {
                line(it, frame.centerX(), frame.bottom - bottomBand * 0.32f, bottomBand * 0.42f, SCRIPT, 0xFF3A3A3A.toInt(), maxWidth = c.width * 0.9f)
            }
            if (tape) {
                val tw = c.width * 0.3f
                val th = c.height * 0.07f
                val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (accent and 0x00FFFFFF) or 0xB0000000.toInt() }
                listOf(frame.left + tw * 0.25f to -38f, frame.right - tw * 0.25f to 38f).forEach { (x, rot) ->
                    canvas.save()
                    canvas.rotate(rot, x, frame.top)
                    canvas.drawRect(x - tw / 2, frame.top - th / 2, x + tw / 2, frame.top + th / 2, tapePaint)
                    canvas.restore()
                }
            }
            canvas.restore()
        }

        fun circle(c: Cell, photo: Bitmap?, shown: Boolean) {
            val cx = c.centerX
            val cy = c.centerY
            val r = c.width / 2
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = r * 0.16f
                color = accent
                setShadowLayer(m * 0.02f, 0f, m * 0.008f, 0x44000000)
            }
            canvas.drawCircle(cx, cy, r + ring.strokeWidth / 2, ring)
            val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = r * 0.025f
                color = 0xFFFFFFFF.toInt()
                pathEffect = DashPathEffect(floatArrayOf(r * 0.08f, r * 0.06f), 0f)
            }
            canvas.drawCircle(cx, cy, r + ring.strokeWidth / 2, dashed)
            // Beads around the ring.
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until 14) {
                val a = Math.PI * 2 * i / 14
                dot.color = palette[i % palette.size]
                val rr = r + ring.strokeWidth * 1.35f
                canvas.drawCircle(cx + (cos(a) * rr).toFloat(), cy + (sin(a) * rr).toFloat(), r * 0.045f, dot)
            }
            canvas.save()
            canvas.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
            photoOrPlaceholder(c, photo, shown, 0f)
            canvas.restore()
        }

        fun pill(text: String, cx: Float, cy: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = BLACK
                textSize = m * 0.055f
                letterSpacing = 0.12f
                color = contrast(accent)
                textAlign = Paint.Align.CENTER
            }
            val tw = p.measureText(text.uppercase())
            val padX = m * 0.05f
            val ph = m * 0.09f
            canvas.drawRoundRect(cx - tw / 2 - padX, cy - ph / 2, cx + tw / 2 + padX, cy + ph / 2, ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                setShadowLayer(m * 0.015f, 0f, m * 0.006f, 0x44000000)
            })
            canvas.drawText(text.uppercase(), cx, cy - (p.ascent() + p.descent()) / 2, p)
        }

        /** Cut-out letters on mismatched paper tiles, centred on (cx, cy). */
        fun ransom(text: String, cx: Float, cy: Float, maxWidth: Float, startTile: Float) {
            val letters = text.filter { !it.isWhitespace() }
            if (letters.isEmpty()) return
            val spaces = text.count { it.isWhitespace() }
            var tile = startTile
            val gapF = 0.08f
            while (tile * (letters.length + spaces * 0.4f) * (1 + gapF) > maxWidth && tile > m * 0.04f) tile *= 0.92f
            val total = tile * (letters.length + spaces * 0.4f) * (1 + gapF)
            var x = cx - total / 2
            val tiles = listOf(0xFFFFFFFF.toInt(), 0xFF1E1E1E.toInt(), accent, 0xFFFF6FA8.toInt(), 0xFFFFD54F.toInt(), 0xFF4DB6AC.toInt(), 0xFF7E57C2.toInt())
            text.forEach { ch ->
                if (ch.isWhitespace()) {
                    x += tile * 0.4f * (1 + gapF)
                    return@forEach
                }
                val bg = tiles[rnd.nextInt(tiles.size)]
                val tw = tile * (0.86f + rnd.nextFloat() * 0.22f)
                val th = tile * (1.0f + rnd.nextFloat() * 0.2f)
                val tcx = x + tile * (1 + gapF) / 2
                val tcy = cy + (rnd.nextFloat() - 0.5f) * tile * 0.16f
                canvas.save()
                canvas.rotate((rnd.nextFloat() - 0.5f) * 16f, tcx, tcy)
                canvas.drawRect(tcx - tw / 2, tcy - th / 2, tcx + tw / 2, tcy + th / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = bg
                    setShadowLayer(m * 0.008f, 0f, m * 0.004f, 0x55000000)
                })
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = RANSOM_FACES[rnd.nextInt(RANSOM_FACES.size)]
                    textSize = th * 0.78f
                    color = contrast(bg)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(ch.toString(), tcx, tcy - (p.ascent() + p.descent()) / 2, p)
                canvas.restore()
                x += tile * (1 + gapF)
            }
        }

        fun invite(c: Cell, photo: Bitmap?, shown: Boolean, title: String, subtitle: String, labels: List<String>) {
            val gold = LinearGradient(0f, 0f, w, h, intArrayOf(0xFFF6E3A1.toInt(), 0xFFC9A04A.toInt(), 0xFFF2D98C.toInt()), null, Shader.TileMode.CLAMP)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = m * 0.006f
                shader = gold
            }
            canvas.drawRect(m * 0.045f, m * 0.045f, w - m * 0.045f, h - m * 0.045f, border)
            canvas.drawRect(m * 0.065f, m * 0.065f, w - m * 0.065f, h - m * 0.065f, border)
            // Gold confetti in the corners.
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gold }
            repeat(40) {
                val corner = it % 4
                val x = if (corner % 2 == 0) rnd.nextFloat() * w * 0.25f else w - rnd.nextFloat() * w * 0.25f
                val y = if (corner < 2) rnd.nextFloat() * h * 0.12f else h - rnd.nextFloat() * h * 0.12f
                canvas.drawCircle(x, y, m * (0.004f + rnd.nextFloat() * 0.01f), dot)
            }
            // Arch-topped photo.
            val arch = Path().apply {
                val r = c.width / 2
                moveTo(c.left, c.bottom)
                lineTo(c.left, c.top + r)
                arcTo(RectF(c.left, c.top, c.right, c.top + 2 * r), 180f, 180f)
                lineTo(c.right, c.bottom)
                close()
            }
            canvas.save()
            canvas.clipPath(arch)
            photoOrPlaceholder(c, photo, shown, 0f)
            canvas.restore()
            canvas.drawPath(arch, Paint(border).apply { strokeWidth = m * 0.01f })

            var y = c.bottom + m * 0.09f
            if (subtitle.isNotBlank()) {
                line(subtitle.uppercase(), w / 2, y, m * 0.035f, MEDIUM, 0xFFEADFC8.toInt(), spacing = 0.3f)
                y += m * 0.05f
            }
            val titleSize = fit(title, SCRIPT, m * 0.16f, w * 0.82f)
            line(title, w / 2, y + titleSize * 0.85f, titleSize, SCRIPT, 0, shader = gold)
            y += titleSize * 1.25f
            val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gold; strokeWidth = m * 0.004f }
            canvas.drawLine(w / 2 - m * 0.14f, y, w / 2 + m * 0.14f, y, divider)
            canvas.save()
            canvas.rotate(45f, w / 2, y)
            canvas.drawRect(w / 2 - m * 0.012f, y - m * 0.012f, w / 2 + m * 0.012f, y + m * 0.012f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gold })
            canvas.restore()
            y += m * 0.08f
            labels.filter { it.isNotBlank() }.take(3).forEach { text ->
                line(text, w / 2, y, m * 0.042f, SERIF, 0xFFEADFC8.toInt(), maxWidth = w * 0.8f)
                y += m * 0.065f
            }
        }

        fun bunting(top: Float, sag: Float) {
            val string = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = m * 0.005f
                color = 0xCC5A4636.toInt()
            }
            val path = Path().apply {
                moveTo(-m * 0.02f, top)
                quadTo(w / 2, top + sag * 2, w + m * 0.02f, top)
            }
            canvas.drawPath(path, string)
            val flags = 9
            for (i in 0 until flags) {
                val t = (i + 0.5f) / flags
                val x = w * t
                // Point on the quadratic curve at t.
                val y = (1 - t) * (1 - t) * top + 2 * (1 - t) * t * (top + sag * 2) + t * t * top
                val fw = w / flags * 0.82f
                val fh = fw * 1.15f
                val tri = Path().apply {
                    moveTo(x - fw / 2, y)
                    lineTo(x + fw / 2, y)
                    lineTo(x, y + fh)
                    close()
                }
                canvas.drawPath(tri, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette[i % palette.size]
                    setShadowLayer(m * 0.006f, 0f, m * 0.003f, 0x33000000)
                })
            }
        }

        fun confetti(count: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(count) {
                p.color = (palette[rnd.nextInt(palette.size)] and 0x00FFFFFF) or 0xD0000000.toInt()
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h
                val s = m * (0.008f + rnd.nextFloat() * 0.014f)
                when (rnd.nextInt(3)) {
                    0 -> canvas.drawCircle(x, y, s, p)
                    1 -> {
                        canvas.save()
                        canvas.rotate(rnd.nextFloat() * 180f, x, y)
                        canvas.drawRect(x - s * 1.4f, y - s * 0.5f, x + s * 1.4f, y + s * 0.5f, p)
                        canvas.restore()
                    }
                    else -> canvas.drawPath(star(x, y, s * 1.5f), p)
                }
            }
        }

        fun titleBlock(title: String, subtitle: String, top: Float, bottom: Float, face: Typeface, rel: Float) {
            if (title.isBlank()) return
            val size = fit(title, face, min(m * rel, (bottom - top) * 0.62f), w * 0.86f)
            val subSize = m * 0.036f
            val block = size + if (subtitle.isNotBlank()) subSize * 1.8f else 0f
            val y0 = top + (bottom - top - block) / 2 + size * 0.85f
            line(title, w / 2, y0, size, face, on, shadow = on == 0xFFFFFFFF.toInt())
            if (subtitle.isNotBlank()) line(subtitle.uppercase(), w / 2, y0 + subSize * 1.9f, subSize, MEDIUM, on, spacing = 0.2f)
        }

        fun fit(text: String, face: Typeface, start: Float, maxWidth: Float): Float {
            val p = Paint().apply { typeface = face }
            var size = start
            while (size > m * 0.03f) {
                p.textSize = size
                if (p.measureText(text) <= maxWidth) break
                size *= 0.93f
            }
            return size
        }

        fun line(
            text: String, cx: Float, baseline: Float, size: Float, face: Typeface, color: Int,
            spacing: Float = 0f, maxWidth: Float = w * 0.9f, shader: Shader? = null, shadow: Boolean = false,
        ) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face
                this.color = color
                letterSpacing = spacing
                textAlign = Paint.Align.CENTER
                this.shader = shader
                if (shadow) setShadowLayer(m * 0.012f, 0f, m * 0.004f, 0x66000000)
            }
            var s = size
            p.textSize = s
            while (p.measureText(text) > maxWidth && s > m * 0.025f) {
                s *= 0.93f
                p.textSize = s
            }
            canvas.drawText(text, cx, baseline, p)
        }
    }

    private fun star(cx: Float, cy: Float, r: Float) = Path().apply {
        for (i in 0 until 10) {
            val a = Math.PI / 5 * i - Math.PI / 2
            val rr = if (i % 2 == 0) r else r * 0.45f
            val x = cx + (cos(a) * rr).toFloat()
            val y = cy + (sin(a) * rr).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** Black or white, whichever reads better on [bg]. */
    fun contrast(bg: Int): Int {
        val r = (bg shr 16) and 0xFF
        val g = (bg shr 8) and 0xFF
        val b = bg and 0xFF
        return if (0.299 * r + 0.587 * g + 0.114 * b > 150) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
    }
}
