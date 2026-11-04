package app.frame.studio.export

import app.frame.studio.domain.ExportHeight

object ExportPolicy {
    const val includesWatermark: Boolean = false
    const val videoMimeType: String = "video/avc"
    const val audioMimeType: String = "audio/mp4a-latm"

    fun watermarkOverlay(): Any? = null

    fun outputHeight(height: ExportHeight): Int = height.pixels
}
