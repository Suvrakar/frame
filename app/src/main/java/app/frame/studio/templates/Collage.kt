package app.frame.studio.templates

import kotlin.math.min

/** Multi-photo layouts rendered into a single frame. */
enum class CollageStyle(val label: String, val cells: Int, val ownTitle: Boolean = false) {
    GRID("Grid", 4),
    HERO("Hero", 4),
    STRIPS("Strips", 4),
    POLAROIDS("Polaroids", 4),
    FILMSTRIP("Film strip", 4),

    // Decorated photo frames: the renderer draws the title as part of the design.
    TAPED("Taped polaroid", 1, ownTitle = true),
    CIRCLE("Circle frame", 1, ownTitle = true),
    RANSOM("Cut-out letters", 1, ownTitle = true),
    INVITE("Invitation", 1, ownTitle = true),
    BUNTING("Bunting", 1, ownTitle = true),
    SCRAPBOOK("Scrapbook", 5, ownTitle = true),
    BOOTH("Photo booth", 4, ownTitle = true),
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
        if (style.ownTitle) return frameCells(style, w, h)
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
            else -> frameCells(style, w, h)
        }
    }
}

/** Photo slots of the decorated frame styles (they lay out their own titles). */
private fun frameCells(style: CollageStyle, w: Float, h: Float): List<Cell> {
    val m = min(w, h)
    return when (style) {
        CollageStyle.TAPED -> {
            // One large print, slightly tilted, below the title.
            val pw = min(w * 0.78f, h * 0.62f / 1.18f)
            val ph = pw * 1.18f
            val cx = w / 2
            val cy = h * 0.56f
            listOf(Cell(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2, -4f))
        }
        CollageStyle.CIRCLE -> {
            val d = min(w * 0.7f, h * 0.48f)
            val cy = h * 0.5f
            listOf(Cell(w / 2 - d / 2, cy - d / 2, w / 2 + d / 2, cy + d / 2))
        }
        CollageStyle.RANSOM -> {
            val pw = min(w * 0.76f, h * 0.56f / 1.15f)
            val ph = pw * 1.15f
            val cy = h * 0.5f
            listOf(Cell(w / 2 - pw / 2, cy - ph / 2, w / 2 + pw / 2, cy + ph / 2, 2.5f))
        }
        CollageStyle.INVITE -> {
            // Arch-topped photo in the upper half; text sits below.
            val pw = min(w * 0.52f, h * 0.36f / 1.25f)
            val ph = pw * 1.25f
            val top = h * 0.11f
            listOf(Cell(w / 2 - pw / 2, top, w / 2 + pw / 2, top + ph))
        }
        CollageStyle.BUNTING -> {
            val pw = min(w * 0.74f, h * 0.55f / 1.1f)
            val ph = pw * 1.1f
            val top = h * 0.2f
            listOf(Cell(w / 2 - pw / 2, top, w / 2 + pw / 2, top + ph, 1.5f))
        }
        CollageStyle.SCRAPBOOK -> {
            // Five tilted prints around a central title.
            val pw = min(w * 0.4f, h * 0.27f)
            val ph = pw * 1.12f
            val spots = listOf(
                Triple(0.27f, 0.2f, -7f), Triple(0.74f, 0.17f, 6f),
                Triple(0.22f, 0.79f, 5f), Triple(0.72f, 0.8f, -6f),
                Triple(0.5f, 0.5f, -2f),
            )
            spots.map { (fx, fy, r) ->
                val cx = w * fx
                val cy = h * fy
                Cell(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2, r)
            }
        }
        CollageStyle.BOOTH -> {
            // Big print on the left, a three-frame booth strip on the right.
            val pad = m * 0.06f
            val top = h * 0.24f
            val bottom = h - pad * 1.4f
            val stripW = w * 0.3f
            val stripX = w - pad - stripW
            val inset = stripW * 0.08f
            val fh = (bottom - top - inset * 4) / 3
            val big = Cell(pad, top + m * 0.04f, stripX - pad * 0.6f, bottom - m * 0.06f, -2f)
            listOf(big) + List(3) { i ->
                val y = top + inset + i * (fh + inset)
                Cell(stripX + inset, y, stripX + stripW - inset, y + fh)
            }
        }
        else -> emptyList()
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
