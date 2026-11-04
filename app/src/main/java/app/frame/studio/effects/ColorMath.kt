package app.frame.studio.effects

import app.frame.studio.domain.ColorAdjust

/**
 * Affine RGB color transform stored row-major as 3 rows x 4 columns:
 * r' = m0*r + m1*g + m2*b + m3, g' = m4.., b' = m8.. (offset in 0..1 units).
 */
@JvmInline
value class ColorTransform(val m: FloatArray) {

    /** this ∘ other: applies [other] first, then this. */
    operator fun times(other: ColorTransform): ColorTransform {
        val a = m
        val b = other.m
        val out = FloatArray(12)
        for (row in 0 until 3) {
            for (col in 0 until 4) {
                var sum = 0f
                for (k in 0 until 3) sum += a[row * 4 + k] * b[k * 4 + col]
                if (col == 3) sum += a[row * 4 + 3]
                out[row * 4 + col] = sum
            }
        }
        return ColorTransform(out)
    }

    /** Linear blend with identity; t=0 identity, t=1 this. */
    fun mix(t: Float): ColorTransform {
        val id = IDENTITY.m
        return ColorTransform(FloatArray(12) { id[it] + (m[it] - id[it]) * t })
    }

    val isIdentity: Boolean get() = m.contentEquals(IDENTITY.m)

    /** Column-major 4x4 for Media3's RgbMatrix / OpenGL. */
    fun toGl(): FloatArray = floatArrayOf(
        m[0], m[4], m[8], 0f,
        m[1], m[5], m[9], 0f,
        m[2], m[6], m[10], 0f,
        m[3], m[7], m[11], 1f,
    )

    /** Row-major 4x5 for android.graphics.ColorMatrix / Compose ColorMatrix (offsets in 0..255). */
    fun toAndroid(): FloatArray = floatArrayOf(
        m[0], m[1], m[2], 0f, m[3] * 255f,
        m[4], m[5], m[6], 0f, m[7] * 255f,
        m[8], m[9], m[10], 0f, m[11] * 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun apply(r: Float, g: Float, b: Float): FloatArray = floatArrayOf(
        m[0] * r + m[1] * g + m[2] * b + m[3],
        m[4] * r + m[5] * g + m[6] * b + m[7],
        m[8] * r + m[9] * g + m[10] * b + m[11],
    )

    companion object {
        val IDENTITY = ColorTransform(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f))

        private const val LR = 0.2126f
        private const val LG = 0.7152f
        private const val LB = 0.0722f

        fun scale(r: Float, g: Float, b: Float, offset: Float = 0f) =
            ColorTransform(floatArrayOf(r, 0f, 0f, offset, 0f, g, 0f, offset, 0f, 0f, b, offset))

        fun offset(r: Float, g: Float, b: Float) =
            ColorTransform(floatArrayOf(1f, 0f, 0f, r, 0f, 1f, 0f, g, 0f, 0f, 1f, b))

        /** s = 1 keeps colors, 0 is grayscale, >1 boosts. */
        fun saturation(s: Float): ColorTransform {
            val i = 1 - s
            return ColorTransform(
                floatArrayOf(
                    LR * i + s, LG * i, LB * i, 0f,
                    LR * i, LG * i + s, LB * i, 0f,
                    LR * i, LG * i, LB * i + s, 0f,
                ),
            )
        }

        /** c = 1 keeps contrast; pivots around mid-gray. */
        fun contrast(c: Float): ColorTransform = scale(c, c, c, 0.5f * (1 - c))

        fun brightness(b: Float): ColorTransform = offset(b, b, b)

        fun warmth(w: Float): ColorTransform = scale(1 + 0.12f * w, 1 + 0.02f * w, 1 - 0.12f * w)

        fun sepia() = ColorTransform(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f,
                0.349f, 0.686f, 0.168f, 0f,
                0.272f, 0.534f, 0.131f, 0f,
            ),
        )

        /** Exposure in stops (−1..1 maps to ×0.5..×2). */
        fun exposure(e: Float): ColorTransform {
            val f = Math.pow(2.0, e.toDouble()).toFloat()
            return scale(f, f, f)
        }

        /** Green (−) to magenta (+) shift that keeps overall brightness. */
        fun tint(t: Float): ColorTransform = scale(1 + 0.06f * t, 1 - 0.1f * t, 1 + 0.06f * t)

        /** Lifts blacks and softens whites for a matte look. */
        fun fade(f: Float): ColorTransform = scale(1 - 0.22f * f, 1 - 0.22f * f, 1 - 0.22f * f, 0.14f * f)

        fun fromAdjust(a: ColorAdjust): ColorTransform {
            if (a.isColorIdentity) return IDENTITY
            // Applied right to left: exposure first, fade last.
            return fade(a.fade.coerceIn(0f, 1f)) *
                brightness(a.brightness * 0.4f) *
                contrast(1f + a.contrast * 0.6f) *
                saturation(1f + a.saturation) *
                tint(a.tint) *
                warmth(a.warmth) *
                exposure(a.exposure)
        }
    }
}

enum class FilterGroup(val label: String) {
    CLASSIC("Classic"),
    SOCIAL("Social looks"),
    PORTRAIT("Portrait"),
    NATURE("Nature & food"),
    VINTAGE("Vintage"),
    LOFI("Lo-fi & grain"),
    CINEMATIC("Cinematic"),
    BW("Black & white"),
}

data class FilterPreset(
    val id: String,
    val name: String,
    val transform: ColorTransform,
    val group: FilterGroup = FilterGroup.CLASSIC,
    /** 0..1 darkened corners baked into the look. */
    val vignette: Float = 0f,
    /** 0..1 film grain baked into the look. */
    val grain: Float = 0f,
)

/**
 * Everything the tone shader applies to a clip: tone-dependent grading (highlights, shadows,
 * vibrance, split toning) plus vignette and grain. Tints are luminance-neutral RGB offsets.
 */
data class GradeParams(
    val vignette: Float,
    val grain: Float,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val vibrance: Float = 0f,
    val shadowTint: FloatArray = floatArrayOf(0f, 0f, 0f),
    val highlightTint: FloatArray = floatArrayOf(0f, 0f, 0f),
) {
    val isNone: Boolean
        get() = vignette <= 0f && grain <= 0f && highlights == 0f && shadows == 0f && vibrance == 0f &&
            shadowTint.all { it == 0f } && highlightTint.all { it == 0f }

    override fun equals(other: Any?): Boolean = other is GradeParams && vignette == other.vignette &&
        grain == other.grain && highlights == other.highlights && shadows == other.shadows &&
        vibrance == other.vibrance && shadowTint.contentEquals(other.shadowTint) &&
        highlightTint.contentEquals(other.highlightTint)

    override fun hashCode(): Int = listOf(vignette, grain, highlights, shadows, vibrance).hashCode() * 31 +
        shadowTint.contentHashCode() * 17 + highlightTint.contentHashCode()
}

typealias TextureLook = GradeParams

/**
 * CPU mirror of the tone shader in [GradeEffect], used by tests and kept in sync with it.
 * Inputs and outputs are 0..1 RGB.
 */
object GradeMath {
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    fun luma(r: Float, g: Float, b: Float) = LR * r + LG * g + LB * b

    /** A fully saturated colour for [hueDegrees], as an offset with zero luminance. */
    fun hueTint(hueDegrees: Float): FloatArray {
        val h = ((hueDegrees % 360f) + 360f) % 360f / 60f
        val x = 1f - kotlin.math.abs(h % 2f - 1f)
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(1f, x, 0f)
            1 -> Triple(x, 1f, 0f)
            2 -> Triple(0f, 1f, x)
            3 -> Triple(0f, x, 1f)
            4 -> Triple(x, 0f, 1f)
            else -> Triple(1f, 0f, x)
        }
        val l = luma(r, g, b)
        return floatArrayOf(r - l, g - l, b - l)
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /** Tone grading only (no vignette/grain), exactly as the shader does it. */
    fun tone(rgb: FloatArray, p: GradeParams): FloatArray {
        var r = rgb[0]
        var g = rgb[1]
        var b = rgb[2]
        val l = luma(r, g, b)
        val sw = 1f - smoothstep(0f, 0.55f, l)
        val hw = smoothstep(0.45f, 1f, l)
        val lift = p.shadows * 0.28f * sw + p.highlights * 0.28f * hw
        r += lift; g += lift; b += lift
        r += (p.shadowTint[0] * sw + p.highlightTint[0] * hw) * 0.3f
        g += (p.shadowTint[1] * sw + p.highlightTint[1] * hw) * 0.3f
        b += (p.shadowTint[2] * sw + p.highlightTint[2] * hw) * 0.3f
        val l2 = luma(r, g, b)
        val sat = maxOf(r, g, b) - minOf(r, g, b)
        val k = 1f + p.vibrance * (1f - sat)
        r = l2 + (r - l2) * k; g = l2 + (g - l2) * k; b = l2 + (b - l2) * k
        return floatArrayOf(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }
}

object Filters {
    val all: List<FilterPreset> = listOf(
        FilterPreset("none", "Original", ColorTransform.IDENTITY),
        FilterPreset("vivid", "Vivid", ColorTransform.saturation(1.45f) * ColorTransform.contrast(1.1f)),
        FilterPreset("warm", "Warm", ColorTransform.warmth(1.2f) * ColorTransform.saturation(1.1f)),
        FilterPreset("cool", "Cool", ColorTransform.warmth(-1.2f)),
        FilterPreset("film", "Film", ColorTransform.scale(0.92f, 0.92f, 0.92f, 0.06f) * ColorTransform.saturation(0.85f) * ColorTransform.warmth(0.4f), FilterGroup.VINTAGE, vignette = 0.2f, grain = 0.25f),
        FilterPreset("teal", "Teal & Orange", ColorTransform.scale(1.1f, 1.0f, 0.95f) * ColorTransform(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0.05f, 0f, -0.05f, 0.1f, 1f, 0f)) * ColorTransform.contrast(1.1f)),
        FilterPreset("fade", "Fade", ColorTransform.contrast(0.8f) * ColorTransform.saturation(0.8f)),
        FilterPreset("mono", "Mono", ColorTransform.saturation(0f), FilterGroup.BW),
        FilterPreset("noir", "Noir", ColorTransform.contrast(1.35f) * ColorTransform.saturation(0f), FilterGroup.BW, vignette = 0.45f, grain = 0.1f),
        FilterPreset("sepia", "Sepia", ColorTransform.sepia(), FilterGroup.VINTAGE),
        FilterPreset("dream", "Dream", ColorTransform.scale(1.03f, 0.97f, 1.06f, 0.05f) * ColorTransform.saturation(1.15f)),
        FilterPreset("punch", "Punch", ColorTransform.contrast(1.25f) * ColorTransform.saturation(1.25f)),

        // Instagram-style looks for feeds, Reels and Stories.
        FilterPreset("glow", "Glow", ColorTransform.offset(-0.02f, 0f, 0.03f) * ColorTransform.saturation(1.2f) * ColorTransform.contrast(1.18f), FilterGroup.SOCIAL),
        FilterPreset("sunkissed", "Sunkissed", ColorTransform.saturation(1.15f) * ColorTransform.brightness(0.04f) * ColorTransform.warmth(0.9f), FilterGroup.SOCIAL),
        FilterPreset("golden_hour", "Golden Hour", ColorTransform.brightness(0.03f) * ColorTransform.saturation(1.2f) * ColorTransform.warmth(1.5f), FilterGroup.SOCIAL),
        FilterPreset("pastel", "Pastel", ColorTransform.offset(0.05f, 0.04f, 0.07f) * ColorTransform.saturation(0.75f) * ColorTransform.contrast(0.85f), FilterGroup.SOCIAL),
        FilterPreset("blush", "Blush", ColorTransform.saturation(0.9f) * ColorTransform.scale(1.06f, 0.98f, 1.02f, 0.03f), FilterGroup.SOCIAL),
        FilterPreset("crisp", "Crisp", ColorTransform.saturation(1.05f) * ColorTransform.contrast(1.3f), FilterGroup.SOCIAL),
        FilterPreset("lagoon", "Lagoon", ColorTransform.saturation(1.1f) * ColorTransform.scale(0.9f, 1.05f, 1.1f), FilterGroup.SOCIAL),
        FilterPreset("matte", "Matte", ColorTransform.saturation(0.9f) * ColorTransform.scale(0.85f, 0.85f, 0.85f, 0.08f), FilterGroup.SOCIAL),
        FilterPreset("retro", "Retro", ColorTransform.contrast(0.92f) * ColorTransform.warmth(0.8f) * ColorTransform.sepia().mix(0.4f), FilterGroup.VINTAGE, vignette = 0.25f, grain = 0.2f),
        FilterPreset("instant", "Instant", ColorTransform.contrast(0.95f) * ColorTransform.saturation(0.9f) * ColorTransform.warmth(0.5f) * ColorTransform.scale(0.95f, 0.95f, 0.9f, 0.05f), FilterGroup.VINTAGE, vignette = 0.2f, grain = 0.15f),
        FilterPreset("moody", "Moody", ColorTransform.warmth(-0.4f) * ColorTransform.brightness(-0.05f) * ColorTransform.saturation(0.7f) * ColorTransform.contrast(1.2f), FilterGroup.SOCIAL, vignette = 0.35f, grain = 0f),
        FilterPreset("city_night", "City Night", ColorTransform.brightness(-0.04f) * ColorTransform.saturation(1.1f) * ColorTransform.contrast(1.25f) * ColorTransform.warmth(-0.8f), FilterGroup.SOCIAL),
        FilterPreset("ink", "Ink", ColorTransform.brightness(0.03f) * ColorTransform.saturation(0f) * ColorTransform.contrast(1.1f), FilterGroup.BW),

        // More social looks.
        FilterPreset("clean", "Clean", ColorTransform.saturation(1.05f) * ColorTransform.contrast(1.08f) * ColorTransform.brightness(0.04f), FilterGroup.SOCIAL),
        FilterPreset("peach", "Peach", ColorTransform.scale(1.05f, 1.0f, 0.95f, 0.03f) * ColorTransform.saturation(1.05f), FilterGroup.SOCIAL),
        FilterPreset("rose", "Rosé", ColorTransform.contrast(0.95f) * ColorTransform.scale(1.07f, 0.97f, 1.0f, 0.02f), FilterGroup.SOCIAL),
        FilterPreset("mint", "Mint", ColorTransform.saturation(0.95f) * ColorTransform.scale(0.95f, 1.05f, 1.0f, 0.03f), FilterGroup.SOCIAL),
        FilterPreset("honey", "Honey", ColorTransform.contrast(1.05f) * ColorTransform.scale(1.03f, 1.0f, 0.9f) * ColorTransform.warmth(1.1f), FilterGroup.SOCIAL),
        FilterPreset("citrus", "Citrus", ColorTransform.brightness(0.02f) * ColorTransform.scale(1.02f, 1.04f, 0.9f) * ColorTransform.saturation(1.3f), FilterGroup.SOCIAL),
        FilterPreset("lavender", "Lavender", ColorTransform.saturation(0.9f) * ColorTransform.scale(1.0f, 0.96f, 1.08f, 0.03f), FilterGroup.SOCIAL),

        // Vintage
        FilterPreset("seventies", "Seventies", ColorTransform.offset(0.04f, 0.02f, 0f) * ColorTransform.contrast(0.9f) * ColorTransform.sepia().mix(0.55f), FilterGroup.VINTAGE, vignette = 0.3f, grain = 0.3f),
        FilterPreset("faded_film", "Faded Film", ColorTransform.warmth(0.3f) * ColorTransform.saturation(0.7f) * ColorTransform.scale(0.82f, 0.82f, 0.85f, 0.1f), FilterGroup.VINTAGE, vignette = 0.15f, grain = 0.3f),
        FilterPreset("sun_bleached", "Sun Bleached", ColorTransform.warmth(0.6f) * ColorTransform.contrast(0.85f) * ColorTransform.saturation(0.65f) * ColorTransform.brightness(0.06f), FilterGroup.VINTAGE),
        FilterPreset("cream", "Cream", ColorTransform.saturation(0.8f) * ColorTransform.scale(1.0f, 0.98f, 0.9f, 0.06f), FilterGroup.VINTAGE),
        FilterPreset("postcard", "Postcard", ColorTransform.scale(1.04f, 1.0f, 0.9f, 0.03f) * ColorTransform.contrast(0.9f) * ColorTransform.saturation(1.25f), FilterGroup.VINTAGE),
        FilterPreset("old_reel", "Old Reel", ColorTransform.brightness(-0.03f) * ColorTransform.contrast(1.1f) * ColorTransform.sepia().mix(0.8f), FilterGroup.VINTAGE, vignette = 0.55f, grain = 0.5f),
        FilterPreset("daydream", "Daydream", ColorTransform.scale(1.0f, 1.0f, 1.05f, 0.05f) * ColorTransform.saturation(0.85f) * ColorTransform.brightness(0.05f), FilterGroup.VINTAGE),

        // Cinematic
        FilterPreset(
            "blockbuster", "Blockbuster",
            ColorTransform(floatArrayOf(1.1f, -0.05f, -0.05f, 0f, 0f, 1f, 0f, 0f, -0.1f, 0.05f, 1.05f, 0.03f)) * ColorTransform.contrast(1.15f),
            FilterGroup.CINEMATIC, vignette = 0.3f, grain = 0f),
        FilterPreset("noir_blue", "Blue Hour", ColorTransform.contrast(1.2f) * ColorTransform.scale(0.92f, 0.97f, 1.1f) * ColorTransform.saturation(0.35f), FilterGroup.CINEMATIC),
        FilterPreset("desert", "Desert", ColorTransform.scale(1.05f, 0.98f, 0.85f) * ColorTransform.contrast(1.1f) * ColorTransform.saturation(0.85f) * ColorTransform.warmth(1.3f), FilterGroup.CINEMATIC),
        FilterPreset("arctic", "Arctic", ColorTransform.saturation(0.8f) * ColorTransform.brightness(0.04f) * ColorTransform.warmth(-1.4f), FilterGroup.CINEMATIC),
        FilterPreset("neon_nights", "Neon Nights", ColorTransform.brightness(-0.03f) * ColorTransform.contrast(1.2f) * ColorTransform.scale(1.05f, 0.9f, 1.15f) * ColorTransform.saturation(1.5f), FilterGroup.CINEMATIC),
        FilterPreset("forest", "Forest", ColorTransform.contrast(1.1f) * ColorTransform.saturation(0.9f) * ColorTransform.scale(0.92f, 1.06f, 0.95f), FilterGroup.CINEMATIC),
        FilterPreset("velvet", "Velvet", ColorTransform.brightness(-0.02f) * ColorTransform.saturation(1.1f) * ColorTransform.contrast(1.15f) * ColorTransform.scale(1.05f, 0.92f, 1.02f), FilterGroup.CINEMATIC),

        // Black & white
        FilterPreset("silver", "Silver", ColorTransform.brightness(0.02f) * ColorTransform.contrast(1.15f) * ColorTransform.saturation(0f), FilterGroup.BW),
        FilterPreset("charcoal", "Charcoal", ColorTransform.brightness(-0.05f) * ColorTransform.contrast(1.4f) * ColorTransform.saturation(0f), FilterGroup.BW, vignette = 0.5f, grain = 0.15f),
        FilterPreset("high_key", "High Key", ColorTransform.contrast(0.85f) * ColorTransform.brightness(0.1f) * ColorTransform.saturation(0f), FilterGroup.BW),
        FilterPreset("low_key", "Low Key", ColorTransform.contrast(1.3f) * ColorTransform.brightness(-0.08f) * ColorTransform.saturation(0f), FilterGroup.BW, vignette = 0.45f, grain = 0f),
        FilterPreset("platinum", "Platinum", ColorTransform.scale(1.04f, 1.0f, 0.94f) * ColorTransform.saturation(0f), FilterGroup.BW),
        FilterPreset("selenium", "Selenium", ColorTransform.scale(0.95f, 1.0f, 1.06f) * ColorTransform.saturation(0f), FilterGroup.BW),

        // Portrait: flattering skin, gentle contrast.
        FilterPreset("soft_skin", "Soft Skin", ColorTransform.brightness(0.03f) * ColorTransform.contrast(0.92f) * ColorTransform.scale(1.03f, 1.0f, 0.98f, 0.02f), FilterGroup.PORTRAIT),
        FilterPreset("glow_up", "Glow Up", ColorTransform.saturation(1.08f) * ColorTransform.brightness(0.05f) * ColorTransform.scale(1.04f, 1.0f, 0.97f, 0.03f), FilterGroup.PORTRAIT, vignette = 0.15f),
        FilterPreset("porcelain", "Porcelain", ColorTransform.saturation(0.85f) * ColorTransform.brightness(0.06f) * ColorTransform.scale(0.98f, 1.0f, 1.03f, 0.03f), FilterGroup.PORTRAIT),
        FilterPreset("bronze", "Bronze", ColorTransform.contrast(1.08f) * ColorTransform.scale(1.06f, 1.0f, 0.88f) * ColorTransform.warmth(0.8f), FilterGroup.PORTRAIT, vignette = 0.2f),
        FilterPreset("studio", "Studio", ColorTransform.saturation(1.02f) * ColorTransform.contrast(1.12f) * ColorTransform.brightness(0.02f), FilterGroup.PORTRAIT, vignette = 0.25f),
        FilterPreset("rosy", "Rosy", ColorTransform.contrast(0.95f) * ColorTransform.scale(1.05f, 0.98f, 1.01f, 0.04f), FilterGroup.PORTRAIT),

        // Nature & food
        FilterPreset("evergreen", "Evergreen", ColorTransform.contrast(1.08f) * ColorTransform.scale(0.94f, 1.08f, 0.96f) * ColorTransform.saturation(1.15f), FilterGroup.NATURE),
        FilterPreset("ocean", "Ocean", ColorTransform.saturation(1.15f) * ColorTransform.scale(0.92f, 1.03f, 1.12f), FilterGroup.NATURE),
        FilterPreset("autumn", "Autumn", ColorTransform.saturation(1.1f) * ColorTransform.scale(1.08f, 0.98f, 0.82f) * ColorTransform.warmth(0.8f), FilterGroup.NATURE),
        FilterPreset("bloom", "Bloom", ColorTransform.brightness(0.04f) * ColorTransform.saturation(1.25f) * ColorTransform.scale(1.04f, 0.98f, 1.04f), FilterGroup.NATURE),
        FilterPreset("fresh", "Fresh", ColorTransform.contrast(1.1f) * ColorTransform.saturation(1.2f) * ColorTransform.brightness(0.03f), FilterGroup.NATURE),
        FilterPreset("savory", "Savory", ColorTransform.contrast(1.12f) * ColorTransform.saturation(1.25f) * ColorTransform.warmth(0.7f), FilterGroup.NATURE),
        FilterPreset("bakery", "Bakery", ColorTransform.saturation(1.05f) * ColorTransform.scale(1.05f, 1.0f, 0.88f, 0.03f) * ColorTransform.warmth(0.6f), FilterGroup.NATURE),

        // Lo-fi & grain
        FilterPreset("lofi", "Lo-Fi", ColorTransform.saturation(1.2f) * ColorTransform.contrast(1.25f), FilterGroup.LOFI, vignette = 0.6f, grain = 0.25f),
        FilterPreset("analog", "Analog", ColorTransform.warmth(0.5f) * ColorTransform.saturation(0.85f) * ColorTransform.scale(0.9f, 0.9f, 0.88f, 0.06f), FilterGroup.LOFI, vignette = 0.35f, grain = 0.45f),
        FilterPreset("vhs", "VHS", ColorTransform.offset(0.03f, 0f, 0.04f) * ColorTransform.saturation(1.25f) * ColorTransform.contrast(0.88f), FilterGroup.LOFI, vignette = 0.3f, grain = 0.6f),
        FilterPreset("grunge", "Grunge", ColorTransform.brightness(-0.04f) * ColorTransform.contrast(1.3f) * ColorTransform.saturation(0.6f), FilterGroup.LOFI, vignette = 0.55f, grain = 0.5f),
        FilterPreset("dusty", "Dusty", ColorTransform.warmth(0.4f) * ColorTransform.saturation(0.7f) * ColorTransform.scale(0.88f, 0.86f, 0.84f, 0.08f), FilterGroup.LOFI, vignette = 0.25f, grain = 0.35f),
        FilterPreset("pinhole", "Pinhole", ColorTransform.contrast(1.15f) * ColorTransform.sepia().mix(0.3f), FilterGroup.LOFI, vignette = 0.85f, grain = 0.2f),
        FilterPreset("grain_bw", "Grain B&W", ColorTransform.contrast(1.2f) * ColorTransform.saturation(0f), FilterGroup.LOFI, vignette = 0.3f, grain = 0.55f),
    )

    /** Shader grading for a clip: the filter's vignette/grain (scaled by intensity) plus the user's grade. */
    fun gradeFor(filterId: String, intensity: Float, adjust: ColorAdjust): GradeParams {
        val f = byId(filterId)
        val t = intensity.coerceIn(0f, 1f)
        fun tint(hue: Float, amount: Float): FloatArray =
            if (amount <= 0f) floatArrayOf(0f, 0f, 0f) else GradeMath.hueTint(hue).map { it * amount.coerceIn(0f, 1f) }.toFloatArray()
        return GradeParams(
            vignette = (f.vignette * t + adjust.vignette).coerceIn(0f, 1f),
            grain = (f.grain * t + adjust.grain).coerceIn(0f, 1f),
            highlights = adjust.highlights.coerceIn(-1f, 1f),
            shadows = adjust.shadows.coerceIn(-1f, 1f),
            vibrance = adjust.vibrance.coerceIn(-1f, 1f),
            shadowTint = tint(adjust.shadowHue, adjust.shadowTone),
            highlightTint = tint(adjust.highlightHue, adjust.highlightTone),
        )
    }

    fun textureFor(filterId: String, intensity: Float, adjust: ColorAdjust): TextureLook = gradeFor(filterId, intensity, adjust)

    fun inGroup(group: FilterGroup): List<FilterPreset> =
        all.filter { it.group == group || it.id == "none" }

    fun byId(id: String): FilterPreset = all.firstOrNull { it.id == id } ?: all.first()

    /** Combined filter + adjustments for a clip; adjustments are applied after the filter. */
    fun transformFor(filterId: String, intensity: Float, adjust: ColorAdjust): ColorTransform {
        val filter = byId(filterId).transform.mix(intensity.coerceIn(0f, 1f))
        return ColorTransform.fromAdjust(adjust) * filter
    }
}
