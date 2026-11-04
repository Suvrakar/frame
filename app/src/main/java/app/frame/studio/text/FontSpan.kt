package app.frame.studio.text

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * Applies a [TextFonts] typeface. `TypefaceSpan(Typeface)` needs API 28; minSdk is 26.
 * Keeps any bold requested by a [android.text.style.StyleSpan] via fake bold.
 */
class FontSpan(val fontId: String) : MetricAffectingSpan() {

    override fun updateDrawState(tp: TextPaint) = apply(tp)

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    private fun apply(tp: TextPaint) {
        val wantsBold = ((tp.typeface?.style ?: 0) and Typeface.BOLD) != 0
        val face = TextFonts.typeface(fontId)
        tp.typeface = face
        if (wantsBold && !face.isBold) tp.isFakeBoldText = true
    }

    // Media3 compares spans to decide whether to redraw; same font = equal span.
    override fun equals(other: Any?): Boolean = other is FontSpan && other.fontId == fontId
    override fun hashCode(): Int = fontId.hashCode()
}
