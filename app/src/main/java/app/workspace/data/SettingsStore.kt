package app.workspace.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The few app-wide choices: theme, font, and when the last backup was made. Stored on the device. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(prefs.getString("theme", THEME_SYSTEM) ?: THEME_SYSTEM)
    private val _font = MutableStateFlow(prefs.getString("font", FONT_SANS) ?: FONT_SANS)
    private val _lastBackup = MutableStateFlow(prefs.getLong("last_backup", 0L))

    val theme: StateFlow<String> get() = _theme
    val font: StateFlow<String> get() = _font

    /** Epoch milliseconds of the last export, or 0 if there has not been one. */
    val lastBackup: StateFlow<Long> get() = _lastBackup

    fun setTheme(value: String) {
        prefs.edit().putString("theme", value).apply()
        _theme.value = value
    }

    fun setFont(value: String) {
        prefs.edit().putString("font", value).apply()
        _font.value = value
    }

    fun setLastBackup(value: Long) {
        prefs.edit().putLong("last_backup", value).apply()
        _lastBackup.value = value
    }

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val FONT_SANS = "sans"
        const val FONT_SERIF = "serif"
        const val FONT_MONO = "mono"
    }
}
