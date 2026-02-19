package com.claudeusage.shared.util

import kotlin.time.Duration

fun formatDuration(duration: Duration): String {
    if (duration <= Duration.ZERO) return "Resetting..."

    val totalSeconds = duration.inWholeSeconds
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        days > 0 -> "Resets in ${days}d ${hours}h"
        hours > 0 -> "Resets in ${hours}h ${minutes}m"
        minutes > 0 -> "Resets in ${minutes}m"
        else -> "Resetting soon..."
    }
}

fun formatRemaining(totalSeconds: Long): String {
    if (totalSeconds <= 0) return ""
    val d = totalSeconds / 86400
    val h = (totalSeconds % 86400) / 3600
    val m = (totalSeconds % 3600) / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> ""
    }
}
