package app.frame.studio.captions

import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import app.frame.studio.domain.CaptionStyle
import app.frame.studio.domain.CaptionWord

/** Styled text of an animated caption with word [active] (-1 = none yet) highlighted. */
object CaptionSpans {

    const val HIGHLIGHT = 0xFFFFD54F.toInt()
    const val BOX = 0xFF5B63F0.toInt()
    private const val BACKDROP = 0x99000000.toInt()

    fun build(
        words: List<CaptionWord>,
        style: CaptionStyle,
        baseColor: Int,
        active: Int,
        bold: Boolean,
        font: String = app.frame.studio.text.TextFonts.DEFAULT_ID,
    ): SpannableString {
        val padded = style == CaptionStyle.PLAIN || style == CaptionStyle.BOX
        val text = StringBuilder()
        if (padded) text.append(' ')
        val ranges = words.map { w ->
            val start = text.length
            text.append(w.text)
            val end = text.length
            text.append(' ')
            start to end
        }
        if (!padded && text.isNotEmpty()) text.setLength(text.length - 1)
        val s = SpannableString(text)
        fun span(what: Any, start: Int, end: Int) = s.setSpan(what, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        if (bold) span(StyleSpan(Typeface.BOLD), 0, s.length)
        if (font != app.frame.studio.text.TextFonts.DEFAULT_ID) span(app.frame.studio.text.FontSpan(font), 0, s.length)
        if (style == CaptionStyle.PLAIN) {
            span(BackgroundColorSpan(BACKDROP), 0, s.length)
            span(ForegroundColorSpan(baseColor), 0, s.length)
            return s
        }
        span(ShadowSpan(), 0, s.length)
        ranges.forEachIndexed { i, (start, end) ->
            val color = when (style) {
                CaptionStyle.KARAOKE -> if (i <= active) HIGHLIGHT else baseColor
                CaptionStyle.POP -> when {
                    i < active -> baseColor
                    i == active -> HIGHLIGHT
                    else -> 0x00000000 // not spoken yet: invisible, but keeps the line from jumping
                }
                CaptionStyle.BOX -> baseColor
                CaptionStyle.PLAIN -> baseColor
            }
            span(ForegroundColorSpan(color), start, end)
            if (style == CaptionStyle.BOX && i == active) {
                span(BackgroundColorSpan(BOX), (start - 1).coerceAtLeast(0), (end + 1).coerceAtMost(s.length))
            }
        }
        return s
    }

    /** Soft drop shadow so captions read on any footage without a box. */
    private class ShadowSpan : CharacterStyle() {
        override fun updateDrawState(tp: TextPaint) {
            tp.setShadowLayer(10f, 0f, 4f, 0xD0000000.toInt())
        }

        // Spans are compared when Media3 decides whether to redraw; equal styling = equal span.
        override fun equals(other: Any?): Boolean = other is ShadowSpan
        override fun hashCode(): Int = 1
    }
}
