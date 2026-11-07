package app.frame.studio.templates

/**
 * How a photo sits inside its frame in a template: [zoom] over the "cover" fit (1 = fills the frame
 * exactly) and the source point ([centerX], [centerY], 0..1 of the photo) shown at the frame's
 * centre. Independent of the frame's shape, so one fit works for every page a photo appears on.
 */
data class PhotoFit(val zoom: Float = 1f, val centerX: Float = 0.5f, val centerY: Float = 0.5f) {
    companion object {
        val CENTER = PhotoFit()
        const val MAX_ZOOM = 4f
    }
}

/** Visible part of a photo, in photo pixels. */
data class SourceRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Pure framing maths for [PhotoFit] (JVM-tested); the frame is always fully covered. */
object PhotoFraming {
    /** The part of a [bw]×[bh] photo that a [dw]×[dh] frame shows. */
    fun source(bw: Float, bh: Float, dw: Float, dh: Float, fit: PhotoFit): SourceRect {
        val f = clamp(bw, bh, dw, dh, fit)
        val cover = maxOf(dw / bw, dh / bh)
        val scale = cover * f.zoom
        val sw = dw / scale
        val sh = dh / scale
        val left = f.centerX * bw - sw / 2
        val top = f.centerY * bh - sh / 2
        return SourceRect(left, top, left + sw, top + sh)
    }

    /** Keeps the zoom in range and the centre where the frame stays covered. */
    fun clamp(bw: Float, bh: Float, dw: Float, dh: Float, fit: PhotoFit): PhotoFit {
        val zoom = fit.zoom.coerceIn(1f, PhotoFit.MAX_ZOOM)
        val scale = maxOf(dw / bw, dh / bh) * zoom
        val halfW = (dw / scale) / 2 / bw
        val halfH = (dh / scale) / 2 / bh
        return PhotoFit(zoom, fit.centerX.coerceIn(halfW, 1 - halfW), fit.centerY.coerceIn(halfH, 1 - halfH))
    }

    /** Drag by ([dx], [dy]) frame pixels: the photo follows the finger. */
    fun pan(bw: Float, bh: Float, dw: Float, dh: Float, fit: PhotoFit, dx: Float, dy: Float): PhotoFit {
        val scale = maxOf(dw / bw, dh / bh) * fit.zoom.coerceIn(1f, PhotoFit.MAX_ZOOM)
        return clamp(bw, bh, dw, dh, fit.copy(centerX = fit.centerX - dx / scale / bw, centerY = fit.centerY - dy / scale / bh))
    }

    fun zoom(bw: Float, bh: Float, dw: Float, dh: Float, fit: PhotoFit, factor: Float): PhotoFit =
        clamp(bw, bh, dw, dh, fit.copy(zoom = fit.zoom * factor))
}
