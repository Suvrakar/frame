package app.frame.studio.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Small user preferences, persisted in SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    private val _favoriteFilters = MutableStateFlow(prefs.getStringSet(KEY_FAV_FILTERS, emptySet()).orEmpty().toSet())
    /** Filter ids the user starred in the filter browser. */
    val favoriteFilters: StateFlow<Set<String>> = _favoriteFilters.asStateFlow()

    fun toggleFavoriteFilter(id: String) {
        val next = _favoriteFilters.value.let { if (id in it) it - id else it + id }
        _favoriteFilters.value = next
        prefs.edit().putStringSet(KEY_FAV_FILTERS, next).apply()
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_FAV_FILTERS = "favorite_filters"
    }
}
