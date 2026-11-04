package app.frame.studio.templates

import kotlin.math.min

/** Multi-photo layouts rendered into a single frame. */
enum class CollageStyle(val label: String, val cells: Int) {
    GRID("Grid", 4),
    HERO("Hero", 4),
    STRIPS("Strips", 4),
    POLAROIDS("Polaroids", 4),
    FILMSTRIP("Film strip", 4),
}

/** A photo slot in frame pixels (or any unit); [rotation] is clockwise degrees. */
data class Cell(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val rotation: Float = 0f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
}

object CollageLayout {

    /** Share of the frame height reserved for the title above the photos. */
    const val HEADER_FRACTION = 0.17f

    fun padding(w: Float, h: Float) = min(w, h) * 0.06f
    fun gap(w: Float, h: Float) = min(w, h) * 0.025f

    /** Photo slots for [style] in a [w]×[h] frame, leaving room for a title when [withHeader]. */
    fun cells(style: CollageStyle, w: Float, h: Float, withHeader: Boolean = true): List<Cell> {
        val pad = padding(w, h)
        val gap = gap(w, h)
        val top = if (withHeader) h * HEADER_FRACTION else pad
        val left = pad
        val right = w - pad
        val bottom = h - pad
        val rw = right - left
        val rh = bottom - top
        val portrait = h >= w
        return when (style) {
            CollageStyle.GRID -> {
                val cw = (rw - gap) / 2
                val ch = (rh - gap) / 2
                listOf(
                    Cell(left, top, left + cw, top + ch),
                    Cell(left + cw + gap, top, right, top + ch),
                    Cell(left, top + ch + gap, left + cw, bottom),
                    Cell(left + cw + gap, top + ch + gap, right, bottom),
                )
            }
            CollageStyle.HERO -> {
                val heroH = rh * 0.6f
                val sw = (rw - 2 * gap) / 3
                val y = top + heroH + gap
                listOf(
                    Cell(left, top, right, top + heroH),
                    Cell(left, y, left + sw, bottom),
                    Cell(left + sw + gap, y, left + 2 * sw + gap, bottom),
                    Cell(left + 2 * (sw + gap), y, right, bottom),
                )
            }
            CollageStyle.STRIPS -> if (portrait) {
                val sh = (rh - 3 * gap) / 4
                List(4) { i -> Cell(left, top + i * (sh + gap), right, top + i * (sh + gap) + sh) }
            } else {
                val sw = (rw - 3 * gap) / 4
                List(4) { i -> Cell(left + i * (sw + gap), top, left + i * (sw + gap) + sw, bottom) }
            }
            CollageStyle.POLAROIDS -> {
                // Four tilted prints, one per quadrant, slightly overlapping the centre.
                // Sized so the top prints' captions stay visible above the bottom prints.
                val pw = min(rw * 0.46f, rh * 0.39f)
                val ph = pw * 1.2f
                val rotations = listOf(-6f, 5f, 4f, -5f)
                val centers = listOf(
                    left + rw * 0.27f to top + rh * 0.25f,
                    left + rw * 0.73f to top + rh * 0.27f,
                    left + rw * 0.29f to top + rh * 0.75f,
                    left + rw * 0.72f to top + rh * 0.76f,
                )
                centers.mapIndexed { i, (cx, cy) ->
                    Cell(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2, rotations[i])
                }
            }
            CollageStyle.FILMSTRIP -> if (portrait) {
                // Frames inside a vertical strip; the renderer draws the strip and sprocket holes.
                val stripW = rw * 0.78f
                val sx = left + (rw - stripW) / 2
                val inset = stripW * 0.14f
                val fh = (rh - 5 * gap) / 4
                List(4) { i ->
                    val y = top + gap + i * (fh + gap)
                    Cell(sx + inset, y, sx + stripW - inset, y + fh)
                }
            } else {
                val stripH = rh * 0.78f
                val sy = top + (rh - stripH) / 2
                val inset = stripH * 0.14f
                val fw = (rw - 5 * gap) / 4
                List(4) { i ->
                    val x = left + gap + i * (fw + gap)
                    Cell(x, sy + inset, x + fw, sy + stripH - inset)
                }
            }
        }
    }
}

/** One rendered collage frame: [visible] photos of group [group], shown for [durationMs]. */
data class CollagePage(val group: Int, val visible: Int, val durationMs: Long, val isFinal: Boolean)

object CollagePlan {

    const val STEP_MS = 450L

    /**
     * Photos fill each collage one by one (a short page per photo), then the full collage holds
     * for [holdMs]. Every [perPage] photos start a new collage.
     */
    fun pages(photoCount: Int, perPage: Int, holdMs: Long, buildUp: Boolean = true): List<CollagePage> {
        if (photoCount <= 0) return emptyList()
        val groups = (photoCount + perPage - 1) / perPage
        return (0 until groups).flatMap { g ->
            val n = min(perPage, photoCount - g * perPage)
            val steps = if (buildUp) (1 until n).map { k -> CollagePage(g, k, STEP_MS, isFinal = false) } else emptyList()
            steps + CollagePage(g, n, holdMs, isFinal = true)
        }
    }

    fun durationMs(photoCount: Int, perPage: Int, holdMs: Long): Long =
        pages(photoCount, perPage, holdMs).sumOf { it.durationMs }
}
