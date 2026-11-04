package app.frame.studio.templates

import app.frame.studio.domain.CanvasAspect
import app.frame.studio.domain.TransitionType

enum class TemplateCategory(val label: String) {
    BIRTHDAY("Birthday"),
    CELEBRATE("Celebrations"),
    TRAVEL("Travel"),
    SOCIAL("Social"),
    SEASONAL("Seasonal"),

    /** Filter only: every template that has a photo collage. */
    COLLAGE("Collage & frames"),
}

/** Personalization the user can type in; [key] appears in template text as a placeholder. */
enum class TemplateField(val label: String, val hint: String, val key: String) {
    NAME("Name", "e.g. Mia", "{name}"),
    AGE("Age", "e.g. 7", "{age}"),
    TITLE("Title", "e.g. Summer 2026", "{title}"),
    DATE("Date & time", "e.g. Sat 9 March · 6 PM", "{date}"),
    PLACE("Place", "e.g. Mia's house", "{place}"),
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
    /** When set, photos are arranged in this multi-photo layout instead of shown one by one. */
    val collage: CollageStyle? = null,
    /** Title drawn above the collage photos. */
    val header: CardSpec? = null,
    /** Per-photo labels inside the collage (polaroid captions); placeholders allowed. */
    val photoLabels: List<String> = emptyList(),
    /** Part of Frame Pro (or unlocked once with a rewarded ad). */
    val pro: Boolean = false,
) {
    val introMs: Long get() = if (intro != null) 2_500 else 0
    val outroMs: Long get() = if (outro != null) 2_200 else 0
    val bodyMs: Long
        get() = if (collage != null) CollagePlan.durationMs(slots, collage.cells, slotMs) else slots * slotMs
    val totalMs: Long get() = introMs + bodyMs + outroMs
}

object Templates {

    private const val PINK = 0xFFFF4F9A
    private const val GOLD = 0xFFFFC94D

    /** Existing designs that are part of Frame Pro; the rest stay free. */
    private val PRO_IDS = setOf(
        "birthday_glow", "birthday_throwback", "wedding", "love_story", "birthday_filmstrip",
        "our_years", "graduation_film", "road_trip", "new_year",
    )

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

        // ---- More birthday & anniversary videos ----
        VideoTemplate(
            id = "birthday_sparkle", name = "Birthday Sparkle", category = TemplateCategory.BIRTHDAY,
            tagline = "Glowy colour and quick flashes, made for Reels.",
            colors = listOf(0xFF1B0B3A, 0xFF6A1B9A, 0xFFFF6FB5), accent = GOLD, emoji = "✨",
            aspect = CanvasAspect.R9_16, slots = 8, slotMs = 1_400, filterId = "glow",
            transition = TransitionType.FADE_WHITE, transitionMs = 250,
            intro = CardSpec("{name}'s birthday", "Let the party begin", "✨", fallbackTitle = "Birthday mode: on"),
            outro = CardSpec("Happy Birthday!", "{name}", "🎂"),
            captions = listOf("", "Party", "", "Cake", "", "Besties", "", "Make a wish"),
            stickers = listOf("✨", "", "🥳", "", "🎂", "", "💖", ""),
            fields = listOf(TemplateField.NAME), badge = "New",
        ),
        VideoTemplate(
            id = "love_story", name = "Love Story", category = TemplateCategory.CELEBRATE,
            tagline = "Cinematic fades for your favourite person.",
            colors = listOf(0xFF2B0A1A, 0xFF7A1F3D, 0xFFE07A98), accent = 0xFFFFD1DC, emoji = "💞",
            aspect = CanvasAspect.R9_16, slots = 6, slotMs = 2_600, filterId = "blockbuster",
            transition = TransitionType.FADE_BLACK, transitionMs = 800,
            intro = CardSpec("Our story", "{name}", "💞"),
            outro = CardSpec("Happy Anniversary", "{title}", "💍"),
            captions = listOf("It started with a hello", "", "Then came adventures", "", "And a lifetime of us", ""),
            captionColor = 0xFFFFD1DC, fields = listOf(TemplateField.NAME, TemplateField.TITLE),
        ),

        // ---- Decorated photo frames ----
        VideoTemplate(
            id = "taped_birthday", name = "Taped Polaroid", category = TemplateCategory.BIRTHDAY,
            tagline = "A big instant print, taped down with a handwritten note.",
            colors = listOf(0xFFFFF4EC, 0xFFFFE3E8, 0xFFFDEBD3), accent = 0xFFFF8FB1, emoji = "📌",
            aspect = CanvasAspect.R9_16, slots = 3, slotMs = 2_800, filterId = "peach",
            transition = TransitionType.FADE_WHITE, transitionMs = 400,
            collage = CollageStyle.TAPED,
            header = CardSpec("Happy Birthday", "{name} turns {age}"),
            photoLabels = listOf("Make a wish", "Cheers to you", "So loved"),
            fields = listOf(TemplateField.NAME, TemplateField.AGE), badge = "New",
        ),
        VideoTemplate(
            id = "circle_birthday", name = "Circle Frame", category = TemplateCategory.BIRTHDAY,
            tagline = "A round portrait in a beaded ring with a name tag.",
            colors = listOf(0xFFFFF8E7, 0xFFFFEFD0, 0xFFFFE0B2), accent = 0xFFFF9F43, emoji = "🟠",
            aspect = CanvasAspect.R4_5, slots = 3, slotMs = 2_600, filterId = "sunkissed",
            transition = TransitionType.FADE_WHITE, transitionMs = 350,
            collage = CollageStyle.CIRCLE,
            header = CardSpec("Happy Birthday", ""),
            photoLabels = listOf("{name}", "{name}", "{name}"),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "circle_baby", name = "Little One", category = TemplateCategory.BIRTHDAY,
            tagline = "Soft blues for a first birthday.",
            colors = listOf(0xFFE3F2FD, 0xFFD6ECFF, 0xFFF0F8FF), accent = 0xFF64B5F6, emoji = "🍼",
            aspect = CanvasAspect.R4_5, slots = 3, slotMs = 2_600, filterId = "dream",
            transition = TransitionType.FADE_WHITE, transitionMs = 350,
            collage = CollageStyle.CIRCLE,
            header = CardSpec("Happy {age} Birthday", "", fallbackTitle = "Happy Birthday"),
            photoLabels = listOf("{name}", "{name}", "{name}"),
            fields = listOf(TemplateField.NAME, TemplateField.AGE),
        ),
        VideoTemplate(
            id = "ransom_birthday", name = "Cut-out Letters", category = TemplateCategory.BIRTHDAY,
            tagline = "Magazine cut-out letters around your photo.",
            colors = listOf(0xFFFFB6C8, 0xFFFF9DB8, 0xFFFFC6D6), accent = 0xFF7E57C2, emoji = "✂️",
            aspect = CanvasAspect.R9_16, slots = 3, slotMs = 2_600, filterId = "blush",
            transition = TransitionType.FADE_WHITE, transitionMs = 300,
            collage = CollageStyle.RANSOM,
            header = CardSpec("Happy Birthday", "{name}"),
            fields = listOf(TemplateField.NAME), pro = true, badge = "Trending",
        ),
        VideoTemplate(
            id = "night_ransom", name = "Black & White Party", category = TemplateCategory.BIRTHDAY,
            tagline = "Monochrome photos, loud colourful letters.",
            colors = listOf(0xFF111111, 0xFF1F1F1F, 0xFF2B2B2B), accent = 0xFFFFD54F, emoji = "🖤",
            aspect = CanvasAspect.R9_16, slots = 3, slotMs = 2_400, filterId = "noir",
            transition = TransitionType.FADE_WHITE, transitionMs = 250,
            collage = CollageStyle.RANSOM,
            header = CardSpec("Happy Birthday", "{name}"),
            fields = listOf(TemplateField.NAME), pro = true,
        ),
        VideoTemplate(
            id = "gold_invite", name = "Gold Invitation", category = TemplateCategory.BIRTHDAY,
            tagline = "An elegant save-the-date with date and place.",
            colors = listOf(0xFF1A1A1A, 0xFF262320, 0xFF141210), accent = 0xFFE6C46C, emoji = "💌",
            aspect = CanvasAspect.R9_16, slots = 1, slotMs = 5_000, filterId = "warm",
            transition = TransitionType.FADE_BLACK, transitionMs = 500,
            collage = CollageStyle.INVITE,
            header = CardSpec("{name}'s Birthday", "Save the date", fallbackTitle = "Birthday Party"),
            photoLabels = listOf("{date}", "{place}"),
            fields = listOf(TemplateField.NAME, TemplateField.DATE, TemplateField.PLACE), pro = true, badge = "Invite",
        ),
        VideoTemplate(
            id = "bunting_party", name = "Bunting Party", category = TemplateCategory.BIRTHDAY,
            tagline = "Party flags, confetti and a handwritten hello.",
            colors = listOf(0xFFE8F4FF, 0xFFFFF1F6, 0xFFFFF9E6), accent = 0xFF4FC3F7, emoji = "🎏",
            aspect = CanvasAspect.R9_16, slots = 3, slotMs = 2_600, filterId = "pastel",
            transition = TransitionType.FADE_WHITE, transitionMs = 350,
            collage = CollageStyle.BUNTING,
            header = CardSpec("Happy Birthday", "{name}"),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "scrapbook_birthday", name = "Scrapbook", category = TemplateCategory.BIRTHDAY,
            tagline = "Five prints and cut-out letters on a scrapbook page.",
            colors = listOf(0xFFFFF3E0, 0xFFFDE7D7, 0xFFFFF8EE), accent = 0xFFFF7043, emoji = "📒",
            aspect = CanvasAspect.R9_16, slots = 5, slotMs = 3_500, filterId = "instant",
            transition = TransitionType.FADE_WHITE, transitionMs = 400,
            collage = CollageStyle.SCRAPBOOK,
            header = CardSpec("Happy Birthday", ""),
            fields = emptyList(), pro = true,
        ),
        VideoTemplate(
            id = "booth_birthday", name = "Photo Booth", category = TemplateCategory.BIRTHDAY,
            tagline = "One big print and a three-shot booth strip.",
            colors = listOf(0xFFB8C9A9, 0xFFA3B795, 0xFFCAD8BE), accent = 0xFFFFFFFF, emoji = "📷",
            aspect = CanvasAspect.R9_16, slots = 4, slotMs = 3_500, filterId = "fresh",
            transition = TransitionType.FADE_WHITE, transitionMs = 400,
            collage = CollageStyle.BOOTH,
            header = CardSpec("Happy Birthday", "{name}"),
            fields = listOf(TemplateField.NAME), pro = true,
        ),
        VideoTemplate(
            id = "taped_love", name = "Taped Love Note", category = TemplateCategory.CELEBRATE,
            tagline = "Your favourite photo of the two of you, taped with love.",
            colors = listOf(0xFFFCE4EC, 0xFFF8D7E0, 0xFFFFF0F3), accent = 0xFFE57373, emoji = "💗",
            aspect = CanvasAspect.R9_16, slots = 3, slotMs = 2_800, filterId = "rosy",
            transition = TransitionType.FADE_WHITE, transitionMs = 400,
            collage = CollageStyle.TAPED,
            header = CardSpec("Happy Anniversary", "{name}"),
            photoLabels = listOf("Us", "Forever", "Always"),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "bunting_baby", name = "Welcome Baby", category = TemplateCategory.CELEBRATE,
            tagline = "Flags up for the newest family member.",
            colors = listOf(0xFFFFF8F0, 0xFFF3ECFF, 0xFFE8F8F2), accent = 0xFFB39DDB, emoji = "👶",
            aspect = CanvasAspect.R4_5, slots = 3, slotMs = 2_600, filterId = "dream",
            transition = TransitionType.FADE_WHITE, transitionMs = 350,
            collage = CollageStyle.BUNTING,
            header = CardSpec("Welcome {name}", "", fallbackTitle = "Welcome baby"),
            fields = listOf(TemplateField.NAME), pro = true,
        ),

        // ---- 4-photo collages ----
        VideoTemplate(
            id = "birthday_collage", name = "Birthday Collage", category = TemplateCategory.BIRTHDAY,
            tagline = "Four favourite photos pop into a party grid.",
            colors = listOf(0xFFFF6FA8, 0xFFA66BFF, 0xFFFFB86B), accent = GOLD, emoji = "🎉",
            aspect = CanvasAspect.R9_16, slots = 4, slotMs = 3_200, filterId = "vivid",
            transition = TransitionType.FADE_WHITE, transitionMs = 400,
            collage = CollageStyle.GRID,
            header = CardSpec("Happy Birthday {name}!", "{age} years of awesome", "🎉", fallbackTitle = "Happy Birthday!"),
            outro = CardSpec("Make a wish", "", "✨"),
            stickers = listOf("🎈", "🎂"),
            fields = listOf(TemplateField.NAME, TemplateField.AGE), badge = "Popular",
        ),
        VideoTemplate(
            id = "party_polaroids", name = "Party Polaroids", category = TemplateCategory.BIRTHDAY,
            tagline = "Instant-photo prints scattered on a party table.",
            colors = listOf(0xFFFFB199, 0xFFFF7EB3, 0xFFFFD27A), accent = 0xFFFFFFFF, emoji = "📸",
            aspect = CanvasAspect.R4_5, slots = 4, slotMs = 3_500, filterId = "instant",
            transition = TransitionType.FADE_WHITE, transitionMs = 400,
            collage = CollageStyle.POLAROIDS,
            header = CardSpec("{name}'s party", "", "📸", fallbackTitle = "Party time"),
            photoLabels = listOf("Cake!", "Besties", "Gifts", "Smiles"),
            outro = CardSpec("Best day ever", "", "🥳"),
            fields = listOf(TemplateField.NAME), badge = "New",
        ),
        VideoTemplate(
            id = "birthday_filmstrip", name = "Birthday Film", category = TemplateCategory.BIRTHDAY,
            tagline = "A retro film strip for your birthday memories.",
            colors = listOf(0xFF140F0A, 0xFF3D2A16, 0xFFB8862E), accent = GOLD, emoji = "🎞️",
            aspect = CanvasAspect.R9_16, slots = 4, slotMs = 3_200, filterId = "film",
            transition = TransitionType.FADE_BLACK, transitionMs = 500,
            collage = CollageStyle.FILMSTRIP,
            header = CardSpec("Happy Birthday", "{name}", "🎞️"),
            outro = CardSpec("Here's to you", "", "🥂"),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "birthday_dump", name = "Birthday Photo Dump", category = TemplateCategory.BIRTHDAY,
            tagline = "Eight photos, two grids, zero effort.",
            colors = listOf(0xFF2E1065, 0xFF7C3AED, 0xFFF472B6), accent = 0xFFFDE68A, emoji = "🥳",
            aspect = CanvasAspect.R9_16, slots = 8, slotMs = 2_600, filterId = "punch",
            transition = TransitionType.FADE_WHITE, transitionMs = 300,
            collage = CollageStyle.GRID,
            header = CardSpec("{name} turned {age}", "photo dump", "🥳", fallbackTitle = "Birthday dump"),
            outro = CardSpec("Thanks for the love", "", "💜"),
            fields = listOf(TemplateField.NAME, TemplateField.AGE), badge = "Trending",
        ),
        VideoTemplate(
            id = "anniversary_collage", name = "Anniversary Collage", category = TemplateCategory.CELEBRATE,
            tagline = "One big moment and three little ones.",
            colors = listOf(0xFF3A0F1E, 0xFF8E2A4C, 0xFFF2A7B8), accent = 0xFFFFE4EC, emoji = "💍",
            aspect = CanvasAspect.R4_5, slots = 4, slotMs = 3_500, filterId = "rose",
            transition = TransitionType.FADE_BLACK, transitionMs = 600,
            collage = CollageStyle.HERO,
            header = CardSpec("Happy Anniversary", "{name}", "💍"),
            outro = CardSpec("Here's to forever", "", "❤️"),
            fields = listOf(TemplateField.NAME), badge = "Popular",
        ),
        VideoTemplate(
            id = "love_polaroids", name = "Love Polaroids", category = TemplateCategory.CELEBRATE,
            tagline = "Your love story in four little prints.",
            colors = listOf(0xFFFFC2D1, 0xFFFF8FAB, 0xFFFB6F92), accent = 0xFFFFFFFF, emoji = "💌",
            aspect = CanvasAspect.R9_16, slots = 4, slotMs = 3_500, filterId = "blush",
            transition = TransitionType.FADE_WHITE, transitionMs = 500,
            collage = CollageStyle.POLAROIDS,
            header = CardSpec("Us", "{name}", "💌"),
            photoLabels = listOf("First date", "Adventures", "Home", "Forever"),
            outro = CardSpec("Happy Anniversary", "", "💕"),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "our_years", name = "Our Years", category = TemplateCategory.CELEBRATE,
            tagline = "Four strips, four chapters of your story.",
            colors = listOf(0xFFF5EBDD, 0xFFE8D3B6, 0xFFC9A46A), accent = 0xFFFFFFFF, emoji = "🤍",
            aspect = CanvasAspect.R9_16, slots = 4, slotMs = 3_500, filterId = "cream",
            transition = TransitionType.FADE_WHITE, transitionMs = 600,
            collage = CollageStyle.STRIPS,
            header = CardSpec("{title} years together", "{name}", "🤍", fallbackTitle = "Our years together"),
            outro = CardSpec("And counting", "", "💛"),
            fields = listOf(TemplateField.NAME, TemplateField.TITLE),
        ),
        VideoTemplate(
            id = "friends_forever", name = "Friends Forever", category = TemplateCategory.SOCIAL,
            tagline = "A bright square grid for your crew.",
            colors = listOf(0xFF00C9A7, 0xFF00A8E8, 0xFF845EC2), accent = 0xFFFFF59D, emoji = "🤝",
            aspect = CanvasAspect.R1_1, slots = 4, slotMs = 3_000, filterId = "clean",
            transition = TransitionType.FADE_WHITE, transitionMs = 300,
            collage = CollageStyle.GRID,
            header = CardSpec("Friends forever", "{name}", "🤝"),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "travel_grid", name = "Travel Grid", category = TemplateCategory.TRAVEL,
            tagline = "Your best shot up top, three more below.",
            colors = listOf(0xFF006D77, 0xFF83C5BE, 0xFFFFB4A2), accent = 0xFFFFFFFF, emoji = "🧭",
            aspect = CanvasAspect.R4_5, slots = 4, slotMs = 3_200, filterId = "lagoon",
            transition = TransitionType.FADE_WHITE, transitionMs = 300,
            collage = CollageStyle.HERO,
            header = CardSpec("{title}", "travel diary", "🧭", fallbackTitle = "Wanderlust"),
            fields = listOf(TemplateField.TITLE),
        ),
        VideoTemplate(
            id = "family_album", name = "Family Album", category = TemplateCategory.CELEBRATE,
            tagline = "Warm prints for the people who matter most.",
            colors = listOf(0xFF8D5524, 0xFFC68642, 0xFFF1C27D), accent = 0xFFFFF8E1, emoji = "🏡",
            aspect = CanvasAspect.R4_5, slots = 4, slotMs = 3_500, filterId = "honey",
            transition = TransitionType.FADE_BLACK, transitionMs = 500,
            collage = CollageStyle.POLAROIDS,
            header = CardSpec("Family", "{name}", "🏡"),
            photoLabels = listOf("Us", "Love", "Home", "Always"),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "mothers_day", name = "Mother's Day", category = TemplateCategory.SEASONAL,
            tagline = "A soft collage to say thank you, Mum.",
            colors = listOf(0xFFFFE5EC, 0xFFFFB3C6, 0xFFFF8FAB), accent = 0xFFFFFFFF, emoji = "💐",
            aspect = CanvasAspect.R4_5, slots = 4, slotMs = 3_500, filterId = "soft_skin",
            transition = TransitionType.FADE_WHITE, transitionMs = 500,
            collage = CollageStyle.HERO,
            header = CardSpec("Happy Mother's Day", "{name}", "💐"),
            outro = CardSpec("Love you, Mum", "", "💗"),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "fathers_day", name = "Father's Day", category = TemplateCategory.SEASONAL,
            tagline = "Classic and bold, for the best dad.",
            colors = listOf(0xFF0F172A, 0xFF1E3A5F, 0xFF3B82F6), accent = 0xFFFFC94D, emoji = "👔",
            aspect = CanvasAspect.R4_5, slots = 4, slotMs = 3_500, filterId = "crisp",
            transition = TransitionType.FADE_BLACK, transitionMs = 500,
            collage = CollageStyle.GRID,
            header = CardSpec("Happy Father's Day", "{name}", "👔"),
            outro = CardSpec("Thanks, Dad", "", "💙"),
            fields = listOf(TemplateField.NAME),
        ),
        VideoTemplate(
            id = "graduation_film", name = "Graduation Film", category = TemplateCategory.CELEBRATE,
            tagline = "Four frames from the big day.",
            colors = listOf(0xFF0D1B3D, 0xFF1E3A8A, 0xFFB8862E), accent = GOLD, emoji = "🎓",
            aspect = CanvasAspect.R9_16, slots = 4, slotMs = 3_200, filterId = "golden_hour",
            transition = TransitionType.FADE_WHITE, transitionMs = 400,
            collage = CollageStyle.FILMSTRIP,
            header = CardSpec("Class of {title}", "{name}", "🎓", fallbackTitle = "Graduation day"),
            fields = listOf(TemplateField.NAME, TemplateField.TITLE),
        ),
        VideoTemplate(
            id = "baby_milestones", name = "Baby Milestones", category = TemplateCategory.CELEBRATE,
            tagline = "Little moments, big milestones.",
            colors = listOf(0xFFDDF4FF, 0xFFE0F7EF, 0xFFFFF1D6), accent = 0xFFFFFFFF, emoji = "🍼",
            aspect = CanvasAspect.R1_1, slots = 4, slotMs = 3_500, filterId = "pastel",
            transition = TransitionType.FADE_WHITE, transitionMs = 500,
            collage = CollageStyle.GRID,
            header = CardSpec("{name}'s first year", "", "🍼", fallbackTitle = "Little milestones"),
            fields = listOf(TemplateField.NAME),
        ),
    ).map { if (it.id in PRO_IDS) it.copy(pro = true) else it }

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
        TemplateField.DATE to "Sat 9 March · 6 PM",
        TemplateField.PLACE to "Mia's house",
    )

    fun byId(id: String): VideoTemplate? = all.firstOrNull { it.id == id }

    /** Shown on the home screen: a mix of video and collage templates. */
    val featured: List<VideoTemplate> by lazy {
        listOf(
            "taped_birthday", "ransom_birthday", "gold_invite", "circle_birthday", "booth_birthday",
            "birthday_collage", "scrapbook_birthday", "bunting_party", "party_polaroids", "anniversary_collage",
            "birthday_bash", "love_polaroids", "kids_party", "getaway", "friends_forever", "quick_reel",
        ).mapNotNull { byId(it) }
    }

    /** "4 photos · Grid" for collages, "6 clips" otherwise. */
    fun mediaLabel(t: VideoTemplate): String =
        t.collage?.let { "${t.slots} photos · ${it.label}" } ?: "${t.slots} clips"

    fun inCategory(category: TemplateCategory?): List<VideoTemplate> = when (category) {
        null -> all
        TemplateCategory.COLLAGE -> all.filter { it.collage != null }
        else -> all.filter { it.category == category }
    }

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
        val subtitleMissing = TemplateField.entries.any { f -> card.subtitle.contains(f.key) && values[f].isNullOrBlank() }
        // A subtitle is decoration: drop it rather than show half a sentence.
        val subtitle = if (subtitleMissing && card.subtitle.trim() != "from {name}") "" else fillCardText(card.subtitle, values)
        return title to subtitle
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
