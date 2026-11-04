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

        fun fromAdjust(a: ColorAdjust): ColorTransform {
            if (a.isIdentity) return IDENTITY
            return brightness(a.brightness * 0.4f) *
                contrast(1f + a.contrast * 0.6f) *
                saturation(1f + a.saturation) *
                warmth(a.warmth)
        }
    }
}

enum class FilterGroup(val label: String) {
    CLASSIC("Classic"),
    SOCIAL("Social looks"),
    VINTAGE("Vintage"),
    CINEMATIC("Cinematic"),
    BW("Black & white"),
}

data class FilterPreset(
    val id: String,
    val name: String,
    val transform: ColorTransform,
    val group: FilterGroup = FilterGroup.CLASSIC,
)

object Filters {
    val all: List<FilterPreset> = listOf(
        FilterPreset("none", "Original", ColorTransform.IDENTITY),
        FilterPreset("vivid", "Vivid", ColorTransform.saturation(1.45f) * ColorTransform.contrast(1.1f)),
        FilterPreset("warm", "Warm", ColorTransform.warmth(1.2f) * ColorTransform.saturation(1.1f)),
        FilterPreset("cool", "Cool", ColorTransform.warmth(-1.2f)),
        FilterPreset("film", "Film", ColorTransform.scale(0.92f, 0.92f, 0.92f, 0.06f) * ColorTransform.saturation(0.85f) * ColorTransform.warmth(0.4f), FilterGroup.VINTAGE),
        FilterPreset("teal", "Teal & Orange", ColorTransform.scale(1.1f, 1.0f, 0.95f) * ColorTransform(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0.05f, 0f, -0.05f, 0.1f, 1f, 0f)) * ColorTransform.contrast(1.1f)),
        FilterPreset("fade", "Fade", ColorTransform.contrast(0.8f) * ColorTransform.saturation(0.8f)),
        FilterPreset("mono", "Mono", ColorTransform.saturation(0f), FilterGroup.BW),
        FilterPreset("noir", "Noir", ColorTransform.contrast(1.35f) * ColorTransform.saturation(0f), FilterGroup.BW),
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
        FilterPreset("retro", "Retro", ColorTransform.contrast(0.92f) * ColorTransform.warmth(0.8f) * ColorTransform.sepia().mix(0.4f), FilterGroup.VINTAGE),
        FilterPreset("instant", "Instant", ColorTransform.contrast(0.95f) * ColorTransform.saturation(0.9f) * ColorTransform.warmth(0.5f) * ColorTransform.scale(0.95f, 0.95f, 0.9f, 0.05f), FilterGroup.VINTAGE),
        FilterPreset("moody", "Moody", ColorTransform.warmth(-0.4f) * ColorTransform.brightness(-0.05f) * ColorTransform.saturation(0.7f) * ColorTransform.contrast(1.2f), FilterGroup.SOCIAL),
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
        FilterPreset("seventies", "Seventies", ColorTransform.offset(0.04f, 0.02f, 0f) * ColorTransform.contrast(0.9f) * ColorTransform.sepia().mix(0.55f), FilterGroup.VINTAGE),
        FilterPreset("faded_film", "Faded Film", ColorTransform.warmth(0.3f) * ColorTransform.saturation(0.7f) * ColorTransform.scale(0.82f, 0.82f, 0.85f, 0.1f), FilterGroup.VINTAGE),
        FilterPreset("sun_bleached", "Sun Bleached", ColorTransform.warmth(0.6f) * ColorTransform.contrast(0.85f) * ColorTransform.saturation(0.65f) * ColorTransform.brightness(0.06f), FilterGroup.VINTAGE),
        FilterPreset("cream", "Cream", ColorTransform.saturation(0.8f) * ColorTransform.scale(1.0f, 0.98f, 0.9f, 0.06f), FilterGroup.VINTAGE),
        FilterPreset("postcard", "Postcard", ColorTransform.scale(1.04f, 1.0f, 0.9f, 0.03f) * ColorTransform.contrast(0.9f) * ColorTransform.saturation(1.25f), FilterGroup.VINTAGE),
        FilterPreset("old_reel", "Old Reel", ColorTransform.brightness(-0.03f) * ColorTransform.contrast(1.1f) * ColorTransform.sepia().mix(0.8f), FilterGroup.VINTAGE),
        FilterPreset("daydream", "Daydream", ColorTransform.scale(1.0f, 1.0f, 1.05f, 0.05f) * ColorTransform.saturation(0.85f) * ColorTransform.brightness(0.05f), FilterGroup.VINTAGE),

        // Cinematic
        FilterPreset(
            "blockbuster", "Blockbuster",
            ColorTransform(floatArrayOf(1.1f, -0.05f, -0.05f, 0f, 0f, 1f, 0f, 0f, -0.1f, 0.05f, 1.05f, 0.03f)) * ColorTransform.contrast(1.15f),
            FilterGroup.CINEMATIC,
        ),
        FilterPreset("noir_blue", "Blue Hour", ColorTransform.contrast(1.2f) * ColorTransform.scale(0.92f, 0.97f, 1.1f) * ColorTransform.saturation(0.35f), FilterGroup.CINEMATIC),
        FilterPreset("desert", "Desert", ColorTransform.scale(1.05f, 0.98f, 0.85f) * ColorTransform.contrast(1.1f) * ColorTransform.saturation(0.85f) * ColorTransform.warmth(1.3f), FilterGroup.CINEMATIC),
        FilterPreset("arctic", "Arctic", ColorTransform.saturation(0.8f) * ColorTransform.brightness(0.04f) * ColorTransform.warmth(-1.4f), FilterGroup.CINEMATIC),
        FilterPreset("neon_nights", "Neon Nights", ColorTransform.brightness(-0.03f) * ColorTransform.contrast(1.2f) * ColorTransform.scale(1.05f, 0.9f, 1.15f) * ColorTransform.saturation(1.5f), FilterGroup.CINEMATIC),
        FilterPreset("forest", "Forest", ColorTransform.contrast(1.1f) * ColorTransform.saturation(0.9f) * ColorTransform.scale(0.92f, 1.06f, 0.95f), FilterGroup.CINEMATIC),
        FilterPreset("velvet", "Velvet", ColorTransform.brightness(-0.02f) * ColorTransform.saturation(1.1f) * ColorTransform.contrast(1.15f) * ColorTransform.scale(1.05f, 0.92f, 1.02f), FilterGroup.CINEMATIC),

        // Black & white
        FilterPreset("silver", "Silver", ColorTransform.brightness(0.02f) * ColorTransform.contrast(1.15f) * ColorTransform.saturation(0f), FilterGroup.BW),
        FilterPreset("charcoal", "Charcoal", ColorTransform.brightness(-0.05f) * ColorTransform.contrast(1.4f) * ColorTransform.saturation(0f), FilterGroup.BW),
        FilterPreset("high_key", "High Key", ColorTransform.contrast(0.85f) * ColorTransform.brightness(0.1f) * ColorTransform.saturation(0f), FilterGroup.BW),
        FilterPreset("low_key", "Low Key", ColorTransform.contrast(1.3f) * ColorTransform.brightness(-0.08f) * ColorTransform.saturation(0f), FilterGroup.BW),
        FilterPreset("platinum", "Platinum", ColorTransform.scale(1.04f, 1.0f, 0.94f) * ColorTransform.saturation(0f), FilterGroup.BW),
        FilterPreset("selenium", "Selenium", ColorTransform.scale(0.95f, 1.0f, 1.06f) * ColorTransform.saturation(0f), FilterGroup.BW),
    )

    fun inGroup(group: FilterGroup): List<FilterPreset> =
        all.filter { it.group == group || it.id == "none" }

    fun byId(id: String): FilterPreset = all.firstOrNull { it.id == id } ?: all.first()

    /** Combined filter + adjustments for a clip; adjustments are applied after the filter. */
    fun transformFor(filterId: String, intensity: Float, adjust: ColorAdjust): ColorTransform {
        val filter = byId(filterId).transform.mix(intensity.coerceIn(0f, 1f))
        return ColorTransform.fromAdjust(adjust) * filter
    }
}
