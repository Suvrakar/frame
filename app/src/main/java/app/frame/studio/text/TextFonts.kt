package app.frame.studio.text

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import app.frame.studio.R
import java.util.concurrent.ConcurrentHashMap

/** A text font: [res] null means the system font. */
data class TextFont(val id: String, val label: String, @FontRes val res: Int?)

/** Bundled fonts for text overlays and captions. One catalog for the editor, preview and export. */
object TextFonts {
    const val DEFAULT_ID = "default"

    val all: List<TextFont> = listOf(
        TextFont(DEFAULT_ID, "Classic", null),
        TextFont("pacifico", "Pacifico", R.font.pacifico),
        TextFont("great_vibes", "Great Vibes", R.font.great_vibes),
        TextFont("dancing_script", "Dancing Script", R.font.dancing_script),
        TextFont("caveat", "Caveat", R.font.caveat),
        TextFont("permanent_marker", "Marker", R.font.permanent_marker),
        TextFont("bebas_neue", "Bebas Neue", R.font.bebas_neue),
        TextFont("lobster", "Lobster", R.font.lobster),
        TextFont("abril_fatface", "Abril Fatface", R.font.abril_fatface),
        TextFont("press_start_2p", "Pixel", R.font.press_start_2p),
        TextFont("bungee", "Bungee", R.font.bungee),
    )

    private val byId = all.associateBy { it.id }
    private val cache = ConcurrentHashMap<String, Typeface>()

    @Volatile private var appContext: Context? = null

    fun byId(id: String): TextFont = byId[id] ?: all.first()

    fun init(appContext: Context) {
        this.appContext = appContext.applicationContext
    }

    /** Cached typeface for [id]; system default before [init] or if loading fails. */
    fun typeface(id: String): Typeface {
        val font = byId(id)
        val res = font.res ?: return Typeface.DEFAULT
        val context = appContext ?: return Typeface.DEFAULT
        cache[font.id]?.let { return it }
        val loaded = try {
            ResourcesCompat.getFont(context, res)
        } catch (e: Exception) {
            Log.w("TextFonts", "Could not load font ${font.id}", e)
            null
        } ?: return Typeface.DEFAULT
        cache[font.id] = loaded
        return loaded
    }
}
