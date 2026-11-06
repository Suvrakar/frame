package app.frame.studio.telemetry

/**
 * Pure rules for what may be sent to Analytics (JVM-tested). Firebase silently drops events that
 * break its limits, and we never send anything that could identify a person or a file.
 */
object TelemetryRules {
    private val NAME = Regex("^[A-Za-z][A-Za-z0-9_]{0,39}$")
    private val RESERVED = listOf("firebase_", "google_", "ga_")
    private const val MAX_TEXT = 100
    private const val MAX_PARAMS = 25

    fun isValidName(name: String): Boolean =
        NAME.matches(name) && RESERVED.none { name.startsWith(it) }

    /** Long for whole numbers and booleans, Double for decimals, short text; null to drop. */
    fun cleanValue(value: Any?): Any? = when (value) {
        null -> null
        is Boolean -> if (value) 1L else 0L
        is Int -> value.toLong()
        is Long -> value
        is Float -> value.toDouble()
        is Double -> value
        is Enum<*> -> value.name.lowercase()
        else -> value.toString().takeUnless(::looksLikeLocation)?.take(MAX_TEXT)
    }

    fun cleanParams(params: List<Pair<String, Any?>>): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for ((key, raw) in params) {
            if (out.size == MAX_PARAMS) break
            if (!isValidName(key)) continue
            out[key] = cleanValue(raw) ?: continue
        }
        return out
    }

    /** "editor/{projectId}?import={import}" -> "editor": the route's first segment, never an id. */
    fun screenName(route: String?): String? =
        route?.substringBefore('/')?.substringBefore('?')?.takeIf(::isValidName)

    private fun looksLikeLocation(text: String): Boolean =
        text.contains("://") || text.startsWith("/")
}
