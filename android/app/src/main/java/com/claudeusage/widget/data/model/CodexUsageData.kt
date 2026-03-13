package com.claudeusage.widget.data.model

import org.json.JSONObject
import java.time.Instant

data class CodexUsageData(
    val planType: String?,
    val primaryWindow: UsageWindow?,
    val secondaryWindow: UsageWindow?,
    val allowed: Boolean,
    val limitReached: Boolean,
    val fetchedAt: Instant = Instant.now(),
    val rawJson: String = ""
) {
    /** True when we successfully parsed at least one usage window */
    val hasValidUsage: Boolean
        get() = primaryWindow != null || secondaryWindow != null

    /** The higher of the two windows' used_percent, for the main display */
    val utilizationPercent: Double
        get() {
            val p = primaryWindow?.usedPercent ?: 0.0
            val s = secondaryWindow?.usedPercent ?: 0.0
            return maxOf(p, s)
        }

    /** The earliest upcoming reset from either window */
    val nextResetAt: Instant?
        get() {
            val resets = listOfNotNull(primaryWindow?.resetAt, secondaryWindow?.resetAt)
            return resets.filter { it.isAfter(Instant.now()) }.minOrNull()
        }

    data class UsageWindow(
        val usedPercent: Double,
        val limitWindowSeconds: Long,
        val resetAfterSeconds: Long,
        val resetAt: Instant?
    ) {
        val windowLabel: String
            get() = when {
                limitWindowSeconds <= 21600 -> "5h" // up to 6 hours → primary
                limitWindowSeconds <= 86400 -> "daily"
                else -> "weekly"
            }
    }

    companion object {
        fun fromJson(json: JSONObject): CodexUsageData {
            val planType = json.optString("plan_type", null)
            val rateLimit = json.optJSONObject("rate_limit")

            val allowed = rateLimit?.optBoolean("allowed", true) ?: true
            val limitReached = rateLimit?.optBoolean("limit_reached", false) ?: false

            val primaryWindow = rateLimit?.optJSONObject("primary_window")?.let { parseWindow(it) }
            val secondaryWindow = rateLimit?.optJSONObject("secondary_window")?.let { parseWindow(it) }

            return CodexUsageData(
                planType = planType,
                primaryWindow = primaryWindow,
                secondaryWindow = secondaryWindow,
                allowed = allowed,
                limitReached = limitReached,
                rawJson = json.toString()
            )
        }

        private fun parseWindow(json: JSONObject): UsageWindow {
            val resetEpoch = json.optLong("reset_at", 0L)
            return UsageWindow(
                usedPercent = json.optDouble("used_percent", 0.0),
                limitWindowSeconds = json.optLong("limit_window_seconds", 0L),
                resetAfterSeconds = json.optLong("reset_after_seconds", 0L),
                resetAt = if (resetEpoch > 0) Instant.ofEpochSecond(resetEpoch) else null
            )
        }
    }
}
