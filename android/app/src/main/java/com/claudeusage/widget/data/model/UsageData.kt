package com.claudeusage.widget.data.model

import org.json.JSONObject
import java.time.Instant
import java.time.Duration

data class UsageMetric(
    val utilization: Double,
    val resetsAt: Instant?
) {
    val remainingDuration: Duration?
        get() = resetsAt?.let {
            val now = Instant.now()
            if (it.isAfter(now)) Duration.between(now, it) else Duration.ZERO
        }

    val isExpired: Boolean
        get() = resetsAt?.let { Instant.now().isAfter(it) } ?: false

    val statusLevel: StatusLevel
        get() = when {
            utilization >= 90.0 -> StatusLevel.CRITICAL
            utilization >= 75.0 -> StatusLevel.WARNING
            else -> StatusLevel.NORMAL
        }

    companion object {
        fun fromJson(json: JSONObject?): UsageMetric? {
            if (json == null) return null
            return try {
                UsageMetric(
                    utilization = json.optDouble("utilization", 0.0),
                    resetsAt = json.optString("resets_at", "").takeIf { it.isNotEmpty() }
                        ?.let { Instant.parse(it) }
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

enum class StatusLevel {
    NORMAL, WARNING, CRITICAL
}

data class ExtraUsageInfo(
    val usedCents: Int? = null,
    val limitCents: Int? = null,
    val balanceCents: Int? = null
)

data class LabeledMetric(
    val key: String,
    val label: String,
    val metric: UsageMetric
)

data class UsageData(
    val fiveHour: UsageMetric?,
    val sevenDay: UsageMetric?,
    val dynamicMetrics: List<LabeledMetric> = emptyList(),
    val extraUsage: UsageMetric?,
    val extraUsageInfo: ExtraUsageInfo? = null,
    val fetchedAt: Instant = Instant.now(),
    val rawKeys: List<String> = emptyList(),
    // TEMP diagnostic: "key = value" per response entry, values truncated
    val rawSummary: List<String> = emptyList()
) {
    val extraMetrics: List<LabeledMetric>
        get() = dynamicMetrics + listOfNotNull(
            extraUsage?.let { LabeledMetric("extra_usage", "Extra Usage", it) }
        )

    companion object {
        // Keys with dedicated UI/fields; everything else is picked up dynamically
        private val PINNED_KEYS = setOf("five_hour", "seven_day", "extra_usage")

        fun fromJson(json: JSONObject): UsageData {
            val keys = json.keys().asSequence().toList()
            val dynamic = keys
                .filter { it !in PINNED_KEYS }
                .mapNotNull { key ->
                    val obj = json.optJSONObject(key) ?: return@mapNotNull null
                    if (!obj.has("utilization")) return@mapNotNull null
                    UsageMetric.fromJson(obj)?.let { LabeledMetric(key, labelForKey(key), it) }
                }
            val rawSummary = keys.map { key ->
                val value = if (json.isNull(key)) "null" else json.opt(key).toString()
                "$key = ${value.take(160)}"
            }
            return UsageData(
                fiveHour = UsageMetric.fromJson(json.optJSONObject("five_hour")),
                sevenDay = UsageMetric.fromJson(json.optJSONObject("seven_day")),
                dynamicMetrics = dynamic,
                extraUsage = UsageMetric.fromJson(json.optJSONObject("extra_usage")),
                rawKeys = keys,
                rawSummary = rawSummary
            )
        }

        // Words that need casing other than simple capitalization, plus
        // internal codenames the API uses for public model names
        // (e.g. the web UI shows seven_day_omelette as the Fable limit)
        private val WORD_OVERRIDES = mapOf(
            "oauth" to "OAuth",
            "api" to "API",
            "apps" to "Apps",
            "omelette" to "Fable"
        )

        // "seven_day_fable" -> "Fable (7d)", "seven_day_oauth_apps" -> "OAuth Apps (7d)"
        fun labelForKey(key: String): String {
            var name = key
            var suffix = ""
            when {
                name.startsWith("seven_day_") -> {
                    name = name.removePrefix("seven_day_")
                    suffix = " (7d)"
                }
                name.startsWith("five_hour_") -> {
                    name = name.removePrefix("five_hour_")
                    suffix = " (5h)"
                }
            }
            val words = name.split('_').filter { it.isNotEmpty() }.joinToString(" ") { word ->
                WORD_OVERRIDES[word] ?: word.replaceFirstChar { it.uppercase() }
            }
            return words + suffix
        }
    }
}
