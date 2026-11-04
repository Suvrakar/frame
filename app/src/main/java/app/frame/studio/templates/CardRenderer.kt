package app.frame.studio.templates

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import kotlin.random.Random

/**
 * Draws a template's title card (gradient, confetti, emoji, title, subtitle) into a PNG.
 * Pure drawing code: no bundled images, so templates add almost nothing to the APK.
 */
object CardRenderer {

    fun render(
        template: VideoTemplate,
        title: String,
        subtitle: String,
        emoji: String,
        width: Int,
        height: Int,
        out: File,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            draw(Canvas(bitmap), template, title, subtitle, emoji, width.toFloat(), height.toFloat())
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return out
    }

    /** Gradient, soft highlight and confetti shared by title cards and collages. */
    fun drawBackground(canvas: Canvas, template: VideoTemplate, w: Float, h: Float) {
        val minSide = minOf(w, h)
        val colors = template.colors.map { it.toInt() }.toIntArray()

        // Background gradient plus a soft highlight.
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w, h, colors, null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, bg)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(w * 0.5f, h * 0.3f, maxOf(w, h) * 0.6f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, glow)

        // Confetti, deterministic per template.
        val random = Random(template.id.hashCode())
        val confetti = Paint(Paint.ANTI_ALIAS_FLAG)
        val palette = intArrayOf(template.accent.toInt(), 0xFFFFFFFF.toInt(), colors.last(), colors.first())
        repeat(70) {
            confetti.color = palette[random.nextInt(palette.size)]
            confetti.alpha = 90 + random.nextInt(140)
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            val s = minSide * (0.006f + random.nextFloat() * 0.012f)
            if (random.nextBoolean()) {
                canvas.drawCircle(x, y, s, confetti)
            } else {
                canvas.save()
                canvas.rotate(random.nextFloat() * 180f, x, y)
                canvas.drawRoundRect(x - s * 1.6f, y - s * 0.6f, x + s * 1.6f, y + s * 0.6f, s * 0.4f, s * 0.4f, confetti)
                canvas.restore()
            }
        }

    }

    fun draw(canvas: Canvas, template: VideoTemplate, title: String, subtitle: String, emoji: String, w: Float, h: Float) {
        val minSide = minOf(w, h)
        drawBackground(canvas, template, w, h)

        val onColor = Templates.onColor(template).toInt()
        val dark = onColor != 0xFFFFFFFF.toInt()

        // Emoji
        if (emoji.isNotBlank()) {
            val emojiPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = minSide * 0.2f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(emoji, w / 2f, h * 0.36f, emojiPaint)
        }

        // Title, auto-sized to at most three lines.
        val maxWidth = (w * 0.84f).toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onColor
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            if (!dark) setShadowLayer(minSide * 0.02f, 0f, minSide * 0.006f, 0x66000000)
        }
        var size = minSide * 0.12f
        var layout: StaticLayout
        while (true) {
            titlePaint.textSize = size
            layout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 0.95f)
                .build()
            if (layout.lineCount <= 3 || size < minSide * 0.05f) break
            size *= 0.9f
        }
        val titleTop = h * 0.47f
        canvas.save()
        canvas.translate((w - maxWidth) / 2f, titleTop)
        layout.draw(canvas)
        canvas.restore()

        // Subtitle: tracked caps under the title.
        if (subtitle.isNotBlank()) {
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = onColor
                alpha = 220
                textSize = minSide * 0.045f
                letterSpacing = 0.18f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                if (!dark) setShadowLayer(minSide * 0.012f, 0f, 0f, 0x55000000)
            }
            canvas.drawText(subtitle.uppercase(), w / 2f, titleTop + layout.height + minSide * 0.09f, subPaint)
        }
    }
}
