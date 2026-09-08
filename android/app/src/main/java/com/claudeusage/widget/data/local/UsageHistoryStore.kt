package com.claudeusage.widget.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class UsageHistoryEntry(
    val timestamp: Long,
    val utilization: Double
)

/**
 * Stores utilization history as multiple named series, e.g.
 * "seven_day" (overall Claude weekly), "seven_day_fable",
 * "codex_weekly", so each 7d limit can be graphed independently.
 */
class UsageHistoryStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    fun getAllHistory(): Map<String, List<UsageHistoryEntry>> {
        migrateLegacyHistory()
        val json = prefs.getString(KEY_HISTORY_V2, null) ?: return emptyMap()
        return try {
            val root = JSONObject(json)
            root.keys().asSequence().associateWith { key ->
                parseEntries(root.optJSONArray(key))
            }.filterValues { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getHistory(seriesKey: String = SERIES_WEEKLY_ALL): List<UsageHistoryEntry> =
        getAllHistory()[seriesKey] ?: emptyList()

    /** Appends one data point to each given series at the same timestamp. */
    fun addEntries(timestamp: Long, values: Map<String, Double>) {
        if (values.isEmpty()) return
        val all = getAllHistory().toMutableMap()
        for ((key, utilization) in values) {
            all[key] = (all[key].orEmpty() + UsageHistoryEntry(timestamp, utilization))
                .takeLast(MAX_ENTRIES_PER_SERIES)
        }
        // Drop stale points so abandoned series eventually disappear
        val cutoff = timestamp - MAX_AGE_MS
        val pruned = all
            .mapValues { (_, entries) -> entries.filter { it.timestamp >= cutoff } }
            .filterValues { it.isNotEmpty() }
        saveHistory(pruned)
    }

    fun addEntry(timestamp: Long, utilization: Double) {
        addEntries(timestamp, mapOf(SERIES_WEEKLY_ALL to utilization))
    }

    /** Removes the series whose keys match [predicate], keeping the rest. */
    fun clearSeriesWhere(predicate: (String) -> Boolean) {
        val remaining = getAllHistory().filterKeys { !predicate(it) }
        saveHistory(remaining)
    }

    fun clearHistory() {
        prefs.edit()
            .remove(KEY_HISTORY_V2)
            .remove(KEY_HISTORY_LEGACY)
            .apply()
    }

    private fun migrateLegacyHistory() {
        if (prefs.contains(KEY_HISTORY_V2)) return
        val legacyJson = prefs.getString(KEY_HISTORY_LEGACY, null) ?: return
        try {
            val entries = parseEntries(JSONArray(legacyJson))
            if (entries.isNotEmpty()) {
                saveHistory(mapOf(SERIES_WEEKLY_ALL to entries))
            }
        } catch (e: Exception) {
            // Corrupt legacy data — drop it
        }
        prefs.edit().remove(KEY_HISTORY_LEGACY).apply()
    }

    private fun parseEntries(array: JSONArray?): List<UsageHistoryEntry> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            UsageHistoryEntry(
                timestamp = obj.optLong("t", -1L).takeIf { it >= 0 } ?: return@mapNotNull null,
                utilization = obj.optDouble("u", 0.0)
            )
        }
    }

    private fun saveHistory(series: Map<String, List<UsageHistoryEntry>>) {
        val root = JSONObject()
        for ((key, entries) in series) {
            val array = JSONArray()
            for (entry in entries) {
                array.put(JSONObject().put("t", entry.timestamp).put("u", entry.utilization))
            }
            root.put(key, array)
        }
        prefs.edit().putString(KEY_HISTORY_V2, root.toString()).apply()
    }

    companion object {
        const val SERIES_WEEKLY_ALL = "seven_day"
        const val SERIES_CODEX_WEEKLY = "codex_weekly"

        private const val PREFS_NAME = "claude_usage_history"
        private const val KEY_HISTORY_LEGACY = "history"
        private const val KEY_HISTORY_V2 = "history_v2"
        private const val MAX_ENTRIES_PER_SERIES = 2016 // 7 days * 24h * 60min / 5min
        private const val MAX_AGE_MS = 8L * 24 * 60 * 60 * 1000 // 8 days
    }
}
