package app.frame.studio.export

/** Shared overlay sizing so the editor's handles match the rendered result (pure). */
object OverlayGeometry {

    /** Largest size with the clip's shape that fits the canvas (even numbers for encoders). */
    fun fitSize(srcW: Int, srcH: Int, canvasW: Int, canvasH: Int): Pair<Int, Int> {
        if (srcW <= 0 || srcH <= 0) return canvasW to canvasH
        val scale = minOf(canvasW.toFloat() / srcW, canvasH.toFloat() / srcH)
        val w = ((srcW * scale).toInt() / 2 * 2).coerceAtLeast(2)
        val h = ((srcH * scale).toInt() / 2 * 2).coerceAtLeast(2)
        return w to h
    }

    /** The overlay's box at [scale] in 0..1 frame units (width, height). */
    fun boxFraction(srcW: Int, srcH: Int, canvasW: Int, canvasH: Int, scale: Float): Pair<Float, Float> {
        val (w, h) = fitSize(srcW, srcH, canvasW, canvasH)
        return (w.toFloat() / canvasW * scale) to (h.toFloat() / canvasH * scale)
    }
}
