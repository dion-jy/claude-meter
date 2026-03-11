package com.claudeusage.widget.data.model

import org.json.JSONObject
import java.time.Instant

data class CodexUsageData(
    val minutesRemaining: Int?,
    val minutesLimit: Int?,
    val resetsAt: Instant?,
    val utilizationPercent: Double,
    val fetchedAt: Instant = Instant.now(),
    val rawJson: String = ""
) {
    val hasValidUsage: Boolean
        get() = minutesLimit != null && minutesLimit > 0

    companion object {
        fun fromJson(json: JSONObject): CodexUsageData {
            val remaining = json.optIntOrNull("minutes_remaining")
            val limit = json.optIntOrNull("minutes_limit")
                ?: json.optIntOrNull("total")
                ?: json.optIntOrNull("limit")
            val used = json.optIntOrNull("minutes_used")
                ?: json.optIntOrNull("used")

            val resetsAt = json.optStringOrNull("reset_at")
                ?: json.optStringOrNull("resets_at")
                ?: json.optStringOrNull("next_reset")

            val effectiveRemaining = remaining
                ?: if (limit != null && used != null) (limit - used) else null

            val utilization = when {
                limit != null && limit > 0 && effectiveRemaining != null -> {
                    ((limit - effectiveRemaining).toDouble() / limit) * 100
                }
                limit != null && limit > 0 && used != null -> {
                    (used.toDouble() / limit) * 100
                }
                json.has("utilization") -> json.optDouble("utilization", 0.0)
                else -> 0.0
            }

            return CodexUsageData(
                minutesRemaining = effectiveRemaining,
                minutesLimit = limit,
                resetsAt = resetsAt?.let {
                    try { Instant.parse(it) } catch (_: Exception) { null }
                },
                utilizationPercent = utilization.coerceIn(0.0, 100.0),
                rawJson = json.toString()
            )
        }

        private fun JSONObject.optIntOrNull(key: String): Int? {
            return if (has(key) && !isNull(key)) optInt(key) else null
        }

        private fun JSONObject.optStringOrNull(key: String): String? {
            return if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
        }
    }
}
