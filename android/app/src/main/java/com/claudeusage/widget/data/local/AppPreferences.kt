package com.claudeusage.widget.data.local

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    var notificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, value).apply()

    /**
     * Metric keys the user has toggled off. Keys not in this set are shown,
     * so metrics newly added by the server are visible by default.
     */
    var hiddenMetricKeys: Set<String>
        get() {
            migrateLegacyVisibility()
            return prefs.getStringSet(KEY_HIDDEN_METRICS, null) ?: emptySet()
        }
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN_METRICS, value.toSet()).apply()

    private fun migrateLegacyVisibility() {
        if (prefs.contains(KEY_HIDDEN_METRICS)) return
        val hidden = mutableSetOf<String>()
        if (!prefs.getBoolean(KEY_SHOW_SONNET, true)) hidden.add("seven_day_sonnet")
        if (!prefs.getBoolean(KEY_SHOW_EXTRA_USAGE, true)) hidden.add("extra_usage")
        if (!prefs.getBoolean(KEY_SHOW_CODEX_USAGE, true)) hidden.add("codex_usage")
        prefs.edit().putStringSet(KEY_HIDDEN_METRICS, hidden).apply()
    }

    /** "dark" (default), "light", or "system" */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_DARK) ?: THEME_DARK
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var coachEnabled: Boolean
        get() = prefs.getBoolean(KEY_COACH_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_COACH_ENABLED, value).apply()

    companion object {
        private const val PREFS_NAME = "claude_app_preferences"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_HIDDEN_METRICS = "hidden_metric_keys"
        // Legacy per-metric booleans, read once by migrateLegacyVisibility()
        private const val KEY_SHOW_SONNET = "show_sonnet"
        private const val KEY_SHOW_EXTRA_USAGE = "show_extra_usage"
        private const val KEY_SHOW_CODEX_USAGE = "show_codex_usage"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COACH_ENABLED = "coach_enabled"

        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"
    }
}
