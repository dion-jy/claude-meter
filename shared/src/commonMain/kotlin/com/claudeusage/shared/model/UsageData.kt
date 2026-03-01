package com.claudeusage.shared.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

data class UsageMetric(
    val utilization: Double,
    val resetsAt: Instant?
) {
    val remainingDuration: Duration?
        get() = resetsAt?.let {
            val now = Clock.System.now()
            if (it > now) it - now else Duration.ZERO
        }

    val isExpired: Boolean
        get() = resetsAt?.let { Clock.System.now() > it } ?: false

    val statusLevel: StatusLevel
        get() = when {
            utilization >= 90.0 -> StatusLevel.CRITICAL
            utilization >= 75.0 -> StatusLevel.WARNING
            else -> StatusLevel.NORMAL
        }

    companion object
}

enum class StatusLevel {
    NORMAL, WARNING, CRITICAL
}

data class ExtraUsageInfo(
    val usedCents: Int? = null,
    val limitCents: Int? = null,
    val balanceCents: Int? = null
)

data class UsageData(
    val fiveHour: UsageMetric?,
    val sevenDay: UsageMetric?,
    val sevenDaySonnet: UsageMetric?,
    val sevenDayOpus: UsageMetric?,
    val sevenDayCowork: UsageMetric?,
    val sevenDayOauthApps: UsageMetric?,
    val extraUsage: UsageMetric?,
    val extraUsageInfo: ExtraUsageInfo? = null,
    val fetchedAt: Instant = Clock.System.now(),
    val rawKeys: List<String> = emptyList()
) {
    val extraMetrics: List<Pair<String, UsageMetric>>
        get() {
            val default = UsageMetric(0.0, null)
            return listOf(
                "Sonnet (7d)" to (sevenDaySonnet ?: default),
                "Opus (7d)" to (sevenDayOpus ?: default),
                "Cowork (7d)" to (sevenDayCowork ?: default),
                "OAuth Apps (7d)" to (sevenDayOauthApps ?: default),
                "Extra Usage" to (extraUsage ?: default)
            )
        }
}
