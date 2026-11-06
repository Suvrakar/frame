package app.frame.studio.media

/** User-facing copy for files refused during an import. */
object ImportMessages {
    /** One summary for a whole pick, or null when nothing failed. */
    fun summary(failures: List<String>): String? {
        if (failures.isEmpty()) return null
        val n = failures.size
        return "$n file${if (n > 1) "s" else ""} couldn't be added: ${failures.first()}"
    }

    fun mutedAudio(codec: String?): String =
        "Original sound turned off: this phone can't play the video's audio" +
            (if (codec != null) " ($codec)." else ".")
}
