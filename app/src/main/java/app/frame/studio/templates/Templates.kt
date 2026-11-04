package app.frame.studio.templates

import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.TransitionType

enum class TemplateCategory(val label: String) {
    BIRTHDAY("Birthday"),
    CELEBRATE("Celebrations"),
    TRAVEL("Travel"),
    SOCIAL("Social"),
    SEASONAL("Seasonal"),
}

/** Personalization the user can type in; [key] appears in template text as a placeholder. */
enum class TemplateField(val label: String, val hint: String, val key: String) {
    NAME("Name", "e.g. Mia", "{name}"),
    AGE("Age", "e.g. 7", "{age}"),
    TITLE("Title", "e.g. Summer 2026", "{title}"),
}

/** Full-screen title card (rendered to an image) at the start or end of the video. */
data class CardSpec(
    val title: String,
    val subtitle: String = "",
    val emoji: String = "",
    /** Used instead of [title] when one of its placeholders is left empty. */
    val fallbackTitle: String? = null,
)

data class VideoTemplate(
    val id: String,
    val name: String,
    val category: TemplateCategory,
    val tagline: String,
    /** Gradient stops, ARGB. */
    val colors: List<Long>,
    /** Confetti / highlight color, ARGB. */
    val accent: Long,
    val emoji: String,
    val aspect: CanvasAspect,
    val slots: Int,
    val slotMs: Long,
    val filterId: String = "none",
    val transition: TransitionType = TransitionType.FADE_BLACK,
    val transitionMs: Long = 400,
    val intro: CardSpec? = null,
    val outro: CardSpec? = null,
    /** One caption per slot (cycled). Captions with an unfilled placeholder are skipped. */
    val captions: List<String> = emptyList(),
    /** One sticker per slot (cycled); blank for none. */
    val stickers: List<String> = emptyList(),
    val captionColor: Long = 0xFFFFFFFF,
    val fields: List<TemplateField> = emptyList(),
    val badge: String? = null,
) {
    val introMs: Long get() = if (intro != null) 2_500 else 0
    val outroMs: Long get() = if (outro != null) 2_200 else 0
    val totalMs: Long get() = introMs + slots * slotMs + outroMs
}

object Templates {

    private const val PINK = 0xFFFF4F9A
    private const val GOLD = 0xFFFFC94D

    val all: List<VideoTemplate> = listOf(
        // ---- Birthday ----
        VideoTemplate(
            id = "birthday_bash", name = "Birthday Bash", category = TemplateCategory.BIRTHDAY,
            tagline = "Confetti, colour and a big hello for the birthday star.",
            colors = listOf(0xFFFF5F9E, 0xFF9B5CFF, 0xFFFF9A3D), accent = GOLD, emoji = "🎂",
            aspect = CanvasAspect.R9_16, slots = 6, slotMs = 2_000, filterId = "vivid",
            transition = TransitionType.FADE_WHITE, transitionMs = 400,
            intro = CardSpec("Happy Birthday {name}!", "Let's celebrate", "🎉"),
            outro = CardSpec("Make a wish", "{name}", "✨"),
            captions = listOf("Turning {age}!", "Best day ever", "So much love", "Party time", "Cheers!", "Here's to you"),
            stickers = listOf("🎉", "🎈", "🎂", "🥳", "✨", "🎁"),
            captionColor = 0xFFFFFFFF, fields = listOf(TemplateField.NAME, TemplateField.AGE), badge = "Popular",
        ),
        VideoTemplate(
            id = "birthday_glow", name = "Golden Birthday", category = TemplateCategory.BIRTHDAY,
            tagline = "Warm, glowing and a little bit fancy.",
            colors = listOf(0xFF2B1B0E, 0xFF8A5A1E, 0xFFE8B04A), accent = 0xFFFFE6A8, emoji = "🥂",
            aspect = CanvasAspect.R4_5, slots = 5, slotMs = 2_400, filterId = "warm",
            transition = TransitionType.FADE_BLACK, transitionMs = 600,
            intro = CardSpec("Happy Birthday", "{name}", "✨"),
            outro = CardSpec("Here's to another year", "", "🥂"),
            captions = listOf("{age} looks good on you", "Golden moments", "", "Forever young", ""),
            stickers = listOf("✨", "", "⭐", "", "✨"),
            captionColor = 0xFFFFE6A8, fields = listOf(TemplateField.NAME, TemplateField.AGE),
        ),
        VideoTemplate(
            id = "kids_party", name = "Kids Party", category = TemplateCategory.BIRTHDAY,
            tagline = "Rainbow-bright and full of giggles.",
            colors = listOf(0xFF6FD6FF, 0xFFB78CFF, 0xFFFF9EC7), accent = 0xFFFFF36B, emoji = "🦄",
            aspect = CanvasAspect.R9_16, slots = 6, slotMs = 1_800, filterId = "dream",
            transition = TransitionType.FADE_WHITE, transitionMs = 350,
            intro = CardSpec("{name} is {age}!", "Party time", "🦄", fallbackTitle = "It's party time!"),
            outro = CardSpec("Thanks for coming!", "", "🎈"),
            captions = listOf("Yay!", "Cake time", "Presents!", "Silly faces", "Best friends", "Hip hip hooray!"),
            stickers = listOf("🎈", "🍰", "🎁", "😜", "🦄", "🌈"),
            fields = listOf(TemplateField.NAME, TemplateField.AGE), badge = "New",
        ),
        VideoTemplate(
            id = "birthday_countdown", name = "Countdown", category = TemplateCategory.BIRTHDAY,
            tagline = "Five clips, five seconds, one big surprise.",
            colors = listOf(0xFF0B1026, 0xFF2A2F6B, 0xFF6A4CFF), accent = GOLD, emoji = "⏳",
            aspect = CanvasAspect.R16_9, slots = 5, slotMs = 1_600, filterId = "punch",
            transition = TransitionType.FADE_WHITE, transitionMs = 300,
            outro = CardSpec("Happy Birthday {name}!", "", "🎂"),
            captions = listOf("5", "4", "3", "2", "1"),
            stickers = listOf("", "", "", "", "🎉"),
            captionColor = 0xFFFFC94D, fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "birthday_throwback", name = "Through the Years", category = TemplateCategory.BIRTHDAY,
            tagline = "Old photos meet new memories in a film look.",
            colors = listOf(0xFF3B2A20, 0xFF7A5A3A, 0xFFC9A16E), accent = 0xFFF3E1C3, emoji = "📼",
            aspect = CanvasAspect.R4_5, slots = 6, slotMs = 2_500, filterId = "film",
            transition = TransitionType.FADE_BLACK, transitionMs = 700,
            intro = CardSpec("Through the years", "{name}", "📸"),
            outro = CardSpec("Happy Birthday", "with love", "❤️"),
            captions = listOf("Where it all began", "", "Growing up", "", "And now…", ""),
            captionColor = 0xFFF3E1C3, fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "birthday_card", name = "Birthday Card", category = TemplateCategory.BIRTHDAY,
            tagline = "A short, sweet video card to send today.",
            colors = listOf(0xFFFFD6E5, 0xFFFFB3CF, 0xFFFF8FB8), accent = 0xFFFFFFFF, emoji = "💌",
            aspect = CanvasAspect.R1_1, slots = 3, slotMs = 3_000, filterId = "dream",
            transition = TransitionType.FADE_WHITE, transitionMs = 500,
            intro = CardSpec("Dear {name}", "", "💌", fallbackTitle = "Hey you"),
            outro = CardSpec("Happy Birthday!", "", "🎂"),
            captions = listOf("Wishing you joy", "and laughter", "all year long"),
            stickers = listOf("💖", "🌸", "🎂"),
            fields = listOf(TemplateField.NAME),
        ),

        // ---- Celebrations ----
        VideoTemplate(
            id = "anniversary", name = "Anniversary", category = TemplateCategory.CELEBRATE,
            tagline = "Your story, beautifully told.",
            colors = listOf(0xFF3A0F1E, 0xFF8E2A4C, 0xFFE58FA8), accent = 0xFFFFD1DC, emoji = "💍",
            aspect = CanvasAspect.R4_5, slots = 6, slotMs = 2_500, filterId = "film",
            transition = TransitionType.FADE_BLACK, transitionMs = 700,
            intro = CardSpec("Happy Anniversary", "{name}", "💍"),
            outro = CardSpec("Here's to forever", "", "❤️"),
            captions = listOf("", "Us", "", "Always", "", "Forever"),
            captionColor = 0xFFFFD1DC, fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "wedding", name = "Wedding Day", category = TemplateCategory.CELEBRATE,
            tagline = "Soft, cinematic and timeless.",
            colors = listOf(0xFFF8F1E7, 0xFFE9D8C1, 0xFFC8A97E), accent = 0xFFFFFFFF, emoji = "🤍",
            aspect = CanvasAspect.R16_9, slots = 8, slotMs = 2_500, filterId = "fade",
            transition = TransitionType.FADE_WHITE, transitionMs = 800,
            intro = CardSpec("{name}", "Our wedding day", "🤍", fallbackTitle = "Forever starts today"),
            outro = CardSpec("Just married", "", "💐"),
            fields = listOf(TemplateField.NAME), badge = "Popular",
        ),
        VideoTemplate(
            id = "graduation", name = "Graduation", category = TemplateCategory.CELEBRATE,
            tagline = "Caps in the air. You did it!",
            colors = listOf(0xFF0D1B3D, 0xFF1E3A8A, 0xFF3B82F6), accent = GOLD, emoji = "🎓",
            aspect = CanvasAspect.R9_16, slots = 6, slotMs = 2_000, filterId = "punch",
            transition = TransitionType.FADE_WHITE, transitionMs = 400,
            intro = CardSpec("Class of {title}", "{name}", "🎓", fallbackTitle = "Graduation"),
            outro = CardSpec("The future is yours", "", "🚀"),
            captions = listOf("We did it!", "The crew", "", "Proud moment", "", "Onwards"),
            stickers = listOf("🎓", "", "🎉", "", "⭐", ""),
            fields = listOf(TemplateField.NAME, TemplateField.TITLE),
        ),
        VideoTemplate(
            id = "new_baby", name = "Hello Baby", category = TemplateCategory.CELEBRATE,
            tagline = "Tiny fingers, big love.",
            colors = listOf(0xFFDDF4FF, 0xFFBFE8E0, 0xFFFFE9D6), accent = 0xFFFFFFFF, emoji = "👶",
            aspect = CanvasAspect.R1_1, slots = 5, slotMs = 2_500, filterId = "dream",
            transition = TransitionType.FADE_WHITE, transitionMs = 600,
            intro = CardSpec("Hello, {name}", "Welcome to the world", "👶", fallbackTitle = "Hello, little one"),
            outro = CardSpec("Loved already", "", "💛"),
            captions = listOf("", "So tiny", "", "Sweet dreams", ""),
            fields = listOf(TemplateField.NAME),
        ),

        // ---- Travel ----
        VideoTemplate(
            id = "getaway", name = "Weekend Getaway", category = TemplateCategory.TRAVEL,
            tagline = "Fast cuts and sunny colour for your trip.",
            colors = listOf(0xFF00A6A6, 0xFF2BB3C0, 0xFFFF8C42), accent = 0xFFFFE07A, emoji = "✈️",
            aspect = CanvasAspect.R9_16, slots = 8, slotMs = 1_500, filterId = "teal",
            transition = TransitionType.FADE_WHITE, transitionMs = 300,
            intro = CardSpec("{title}", "Weekend escape", "✈️", fallbackTitle = "Off we go"),
            outro = CardSpec("Until next time", "", "🌴"),
            captions = listOf("Day 1", "", "", "Views", "", "", "Golden hour", ""),
            stickers = listOf("📍", "", "☀️", "", "🌊", "", "🌅", ""),
            fields = listOf(TemplateField.TITLE), badge = "Popular",
        ),
        VideoTemplate(
            id = "road_trip", name = "Road Trip", category = TemplateCategory.TRAVEL,
            tagline = "Miles of memories in widescreen.",
            colors = listOf(0xFF1B1F3B, 0xFFB33F62, 0xFFF9A03F), accent = 0xFFFFE6B3, emoji = "🚗",
            aspect = CanvasAspect.R16_9, slots = 10, slotMs = 1_800, filterId = "film",
            transition = TransitionType.FADE_BLACK, transitionMs = 400,
            intro = CardSpec("{title}", "Road trip", "🚗", fallbackTitle = "On the road"),
            outro = CardSpec("Home again", "", "🏡"),
            fields = listOf(TemplateField.TITLE),
        ),

        // ---- Social ----
        VideoTemplate(
            id = "quick_reel", name = "Quick Reel", category = TemplateCategory.SOCIAL,
            tagline = "Ten punchy beats, made for Reels and Shorts.",
            colors = listOf(0xFF0A0A0F, 0xFF1F1147, 0xFF00E5FF), accent = 0xFF00E5FF, emoji = "⚡",
            aspect = CanvasAspect.R9_16, slots = 10, slotMs = 1_000, filterId = "punch",
            transition = TransitionType.FADE_WHITE, transitionMs = 200,
            outro = CardSpec("Follow for more", "", "⚡"),
            captionColor = 0xFF00E5FF, badge = "Trending",
        ),
        VideoTemplate(
            id = "day_in_life", name = "Day in My Life", category = TemplateCategory.SOCIAL,
            tagline = "Morning to night in under 20 seconds.",
            colors = listOf(0xFFFFE0B2, 0xFFFFB74D, 0xFFF06292), accent = 0xFFFFFFFF, emoji = "☀️",
            aspect = CanvasAspect.R9_16, slots = 8, slotMs = 2_000, filterId = "warm",
            transition = TransitionType.FADE_BLACK, transitionMs = 300,
            intro = CardSpec("A day in my life", "{name}", "☀️"),
            captions = listOf("Morning", "Coffee first", "Getting things done", "Lunch", "Afternoon", "Golden hour", "Dinner", "Goodnight"),
            stickers = listOf("☀️", "☕", "💻", "🥗", "", "🌇", "🍝", "🌙"),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "food_diary", name = "Food Diary", category = TemplateCategory.SOCIAL,
            tagline = "Make every bite look delicious.",
            colors = listOf(0xFF3E2723, 0xFFD84315, 0xFFFFB300), accent = 0xFFFFF3E0, emoji = "🍜",
            aspect = CanvasAspect.R4_5, slots = 6, slotMs = 1_800, filterId = "vivid",
            transition = TransitionType.FADE_BLACK, transitionMs = 300,
            intro = CardSpec("{title}", "Food diary", "🍜", fallbackTitle = "Good food"),
            captions = listOf("", "Yum", "", "Chef's kiss", "", "10/10"),
            stickers = listOf("", "😋", "", "👨‍🍳", "", "⭐"),
            fields = listOf(TemplateField.TITLE),
        ),

        // ---- Seasonal ----
        VideoTemplate(
            id = "holidays", name = "Holiday Cheer", category = TemplateCategory.SEASONAL,
            tagline = "Twinkly, cosy and festive.",
            colors = listOf(0xFF0B3D2E, 0xFF14532D, 0xFFB91C1C), accent = 0xFFFFE08A, emoji = "🎄",
            aspect = CanvasAspect.R4_5, slots = 6, slotMs = 2_000, filterId = "warm",
            transition = TransitionType.FADE_WHITE, transitionMs = 500,
            intro = CardSpec("Happy Holidays", "from {name}", "🎄"),
            outro = CardSpec("Season's greetings", "", "❄️"),
            stickers = listOf("❄️", "", "🎁", "", "⭐", ""),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "new_year", name = "New Year", category = TemplateCategory.SEASONAL,
            tagline = "Midnight sparkle for a fresh start.",
            colors = listOf(0xFF05060F, 0xFF1A1446, 0xFF3C2A8C), accent = GOLD, emoji = "🎆",
            aspect = CanvasAspect.R9_16, slots = 8, slotMs = 1_500, filterId = "punch",
            transition = TransitionType.FADE_WHITE, transitionMs = 300,
            intro = CardSpec("Goodbye {title}", "", "✨", fallbackTitle = "What a year"),
            outro = CardSpec("Happy New Year!", "", "🎆"),
            captions = listOf("Best moments", "", "", "Highlights", "", "", "Here we go", ""),
            fields = listOf(TemplateField.TITLE),
        ),
        VideoTemplate(
            id = "valentine", name = "Valentine", category = TemplateCategory.SEASONAL,
            tagline = "Say it with a little love story.",
            colors = listOf(0xFFFF4D6D, 0xFFFF758F, 0xFFFFB3C1), accent = 0xFFFFFFFF, emoji = "💘",
            aspect = CanvasAspect.R9_16, slots = 5, slotMs = 2_200, filterId = "dream",
            transition = TransitionType.FADE_WHITE, transitionMs = 500,
            intro = CardSpec("For {name}", "", "💘", fallbackTitle = "For you"),
            outro = CardSpec("Be mine?", "", "❤️"),
            captions = listOf("You", "Me", "Us", "", "Always"),
            stickers = listOf("💕", "", "💖", "", "💘"),
            fields = listOf(TemplateField.NAME),
        ),
    )

    /** Readable text color (ARGB) on top of the template's gradient. */
    fun onColor(template: VideoTemplate): Long {
        val avg = template.colors.map { luminance(it) }.average()
        return if (avg > 0.62) 0xFF2B2B2B else 0xFFFFFFFF
    }

    /** Relative luminance (0..1) of an ARGB color. */
    fun luminance(argb: Long): Double {
        fun channel(c: Long): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Sample values used to render gallery previews. */
    val previewValues: Map<TemplateField, String> = mapOf(
        TemplateField.NAME to "Mia",
        TemplateField.AGE to "7",
        TemplateField.TITLE to "Summer",
    )

    fun byId(id: String): VideoTemplate? = all.firstOrNull { it.id == id }

    fun inCategory(category: TemplateCategory?): List<VideoTemplate> =
        if (category == null) all else all.filter { it.category == category }

    /** Fills placeholders in card text; an empty value is removed along with surrounding spacing. */
    fun fillCardText(text: String, values: Map<TemplateField, String>): String {
        var out = text
        TemplateField.entries.forEach { field ->
            val value = values[field]?.trim().orEmpty()
            out = if (value.isEmpty()) {
                out.replace(Regex("""\s*\Q${field.key}\E"""), "")
            } else {
                out.replace(field.key, value)
            }
        }
        // Tidy leftovers such as "Happy Birthday !" or "from" with nothing after it.
        out = out.replace(Regex("""\s+([!?.,])"""), "$1").trim()
        return if (out.matches(Regex("""(?i)(for|from|dear|class of|goodbye)"""))) "" else out
    }

    /** Title and subtitle for a card with the user's values applied. */
    fun fillCard(card: CardSpec, values: Map<TemplateField, String>): Pair<String, String> {
        val missing = TemplateField.entries.any { f -> card.title.contains(f.key) && values[f].isNullOrBlank() }
        val title = if (missing && card.fallbackTitle != null) card.fallbackTitle else fillCardText(card.title, values)
        return title to fillCardText(card.subtitle, values)
    }

    /** Captions that still need a missing value are dropped instead of rendered half-empty. */
    fun fillCaption(text: String, values: Map<TemplateField, String>): String? {
        if (text.isBlank()) return null
        var out = text
        for (field in TemplateField.entries) {
            if (!out.contains(field.key)) continue
            val value = values[field]?.trim().orEmpty()
            if (value.isEmpty()) return null
            out = out.replace(field.key, value)
        }
        return out
    }
}
