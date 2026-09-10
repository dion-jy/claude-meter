package com.claudeusage.widget.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.data.local.UsageHistoryEntry
import com.claudeusage.widget.data.local.UsageHistoryStore
import com.claudeusage.widget.data.model.CodexUsageData
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.ui.components.BannerAd
import com.claudeusage.widget.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private const val PROVIDER_CLAUDE = "Claude"
private const val PROVIDER_OPENAI = "OpenAI"

/**
 * One selectable 7d limit line on the forecast graph.
 *
 * [provider] groups the chips and stat rows so a per-model sub-limit never
 * sits beside its own parent as if the two were different products; the
 * provider name is said once by the group label, and [label] only has to
 * separate the limits within it. [isTotal] marks that parent — the
 * provider-wide weekly limit the others roll up into.
 */
private data class WeeklySeries(
    val key: String,
    val label: String,
    val provider: String,
    val isTotal: Boolean,
    val color: Color,
    val currentUtil: Double?,
    val resetsAt: Instant?,
    val entries: List<UsageHistoryEntry>
) {
    /** Latest known utilization: live value, else last recorded point. */
    val effectiveUtil: Double
        get() = currentUtil ?: entries.lastOrNull()?.utilization ?: 0.0
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ForecastScreen(
    usageData: UsageData?,
    codexData: CodexUsageData?,
    history: Map<String, List<UsageHistoryEntry>>,
    hiddenMetrics: Set<String>,
    hiddenSeries: Set<String>,
    onToggleSeries: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    // Two levels of visibility: Settings decides which limits the app tracks
    // at all (hiddenMetrics, shared with UsageScreen), and the chips below
    // filter what the graph draws from what is left.
    val allSeries = remember(usageData, codexData, history, hiddenMetrics) {
        buildWeeklySeries(usageData, codexData, history, hiddenMetrics)
    }
    // Keys not in hiddenSeries are shown, so new limits appear by default.
    // If prefs somehow hide everything, fall back to showing all.
    val visibleSeries = allSeries.filter { it.key !in hiddenSeries }
        .ifEmpty { allSeries }

    // Shared week window: the overall Claude week when available
    val now = Instant.now()
    val weekDurationMs = Duration.ofDays(7).toMillis()
    val weekEndMs = (usageData?.sevenDay?.resetsAt
        ?: allSeries.firstNotNullOfOrNull { it.resetsAt })
        ?.toEpochMilli()
        ?: (now.toEpochMilli() + weekDurationMs)
    val weekStartMs = weekEndMs - weekDurationMs
    val totalWeekMs = weekDurationMs.toDouble()
    val elapsedMs = (now.toEpochMilli() - weekStartMs).coerceAtLeast(0)

    // Swipe-back gesture
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val swipeThreshold = screenWidthPx * 0.3f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetX.value > swipeThreshold) {
                                offsetX.animateTo(screenWidthPx, tween(200))
                                onBack()
                            } else {
                                offsetX.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetX.animateTo(0f, tween(200))
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        coroutineScope.launch {
                            val newValue = (offsetX.value + dragAmount).coerceAtLeast(0f)
                            offsetX.snapTo(newValue)
                        }
                    }
                )
            }
    ) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Weekly Usage Forecast",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = ExtendedTheme.colors.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Graph card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Series picker: one chip per available 7d limit, grouped
                    // under the provider that owns it
                    if (allSeries.size > 1) {
                        allSeries.groupBy { it.provider }.forEach { (provider, group) ->
                            GroupLabel(provider)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                group.forEach { series ->
                                    val selected = visibleSeries.any { it.key == series.key }
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            // Keep at least one series on the graph
                                            if (selected && visibleSeries.size == 1) return@FilterChip
                                            onToggleSeries(series.key, !selected)
                                        },
                                        label = {
                                            Text(
                                                text = series.label,
                                                fontSize = 12.sp,
                                                fontWeight = if (series.isTotal) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Medium
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(series.color, CircleShape)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = series.color.copy(alpha = 0.18f),
                                            selectedLabelColor = MaterialTheme.colorScheme.onBackground
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    val textMeasurer = rememberTextMeasurer()

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        val leftPad = 40.dp.toPx()
                        val rightPad = 16.dp.toPx()
                        val topPad = 16.dp.toPx()
                        val bottomPad = 30.dp.toPx()

                        val graphWidth = size.width - leftPad - rightPad
                        val graphHeight = size.height - topPad - bottomPad

                        fun xAt(timestampMs: Long): Float =
                            leftPad + graphWidth * ((timestampMs - weekStartMs) / totalWeekMs)
                                .toFloat().coerceIn(0f, 1f)

                        fun yAt(utilization: Double): Float =
                            topPad + graphHeight * (1 - (utilization / 100.0).toFloat().coerceIn(0f, 1f))

                        // Danger zone (above 80%)
                        val dangerY = topPad + graphHeight * (1 - 0.8f)
                        drawRect(
                            color = StatusCritical.copy(alpha = 0.08f),
                            topLeft = Offset(leftPad, topPad),
                            size = androidx.compose.ui.geometry.Size(graphWidth, dangerY - topPad)
                        )

                        // Grid lines + Y-axis labels
                        val ySteps = listOf(0, 25, 50, 75, 100)
                        for (pct in ySteps) {
                            val y = topPad + graphHeight * (1 - pct / 100f)
                            drawLine(
                                color = TextMuted.copy(alpha = 0.2f),
                                start = Offset(leftPad, y),
                                end = Offset(leftPad + graphWidth, y),
                                strokeWidth = 1.dp.toPx()
                            )
                            // Y-axis label
                            val label = textMeasurer.measure(
                                text = AnnotatedString("${pct}%"),
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            )
                            drawText(
                                textLayoutResult = label,
                                topLeft = Offset(
                                    leftPad - label.size.width - 4.dp.toPx(),
                                    y - label.size.height / 2
                                )
                            )
                        }

                        // X-axis labels (Day 0 to Day 7)
                        for (day in 0..7) {
                            val x = leftPad + graphWidth * (day / 7f)
                            val label = textMeasurer.measure(
                                text = AnnotatedString("D$day"),
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            )
                            drawText(
                                textLayoutResult = label,
                                topLeft = Offset(
                                    x - label.size.width / 2,
                                    topPad + graphHeight + 8.dp.toPx()
                                )
                            )
                        }

                        // Axes
                        drawLine(
                            color = TextMuted.copy(alpha = 0.3f),
                            start = Offset(leftPad, topPad),
                            end = Offset(leftPad, topPad + graphHeight),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = TextMuted.copy(alpha = 0.3f),
                            start = Offset(leftPad, topPad + graphHeight),
                            end = Offset(leftPad + graphWidth, topPad + graphHeight),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Sub-limits first so the thicker total line sits on top
                        for (series in visibleSeries.sortedBy { if (it.isTotal) 1 else 0 }) {
                            // History polyline (only current week data)
                            val sortedHistory = series.entries
                                .filter { it.timestamp >= weekStartMs }
                                .sortedBy { it.timestamp }
                            // Weight, not hue, carries the parent/child relation:
                            // colors stay free to tell the limits apart
                            val strokeWidth = if (series.isTotal) 3.5.dp.toPx() else 2.5.dp.toPx()
                            for (i in 0 until sortedHistory.size - 1) {
                                val e1 = sortedHistory[i]
                                val e2 = sortedHistory[i + 1]
                                drawLine(
                                    color = series.color,
                                    start = Offset(xAt(e1.timestamp), yAt(e1.utilization)),
                                    end = Offset(xAt(e2.timestamp), yAt(e2.utilization)),
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            }

                            // Current position dot
                            val util = series.effectiveUtil
                            val currentX = xAt(now.toEpochMilli())
                            val currentY = yAt(util)
                            drawCircle(
                                color = series.color.copy(alpha = 0.4f),
                                radius = 5.dp.toPx(),
                                center = Offset(currentX, currentY)
                            )
                            drawCircle(
                                color = series.color,
                                radius = 3.dp.toPx(),
                                center = Offset(currentX, currentY)
                            )

                            // Projection (dashed), using this series' own burning rate
                            val rate = series.burningRatePerHour(weekStartMs, now)
                            val remainingChartHours =
                                (weekEndMs - now.toEpochMilli()).coerceAtLeast(0) / 3600_000.0
                            val projectedAtEnd = util + rate * remainingChartHours
                            val hoursTo100 = if (rate > 0) (100.0 - util) / rate else Double.MAX_VALUE

                            if (projectedAtEnd >= 100.0 && hoursTo100 < remainingChartHours) {
                                val depletionX = xAt(now.toEpochMilli() + (hoursTo100 * 3600_000).toLong())
                                val depletionY = topPad // 100%

                                drawDashedLine(
                                    color = series.color.copy(alpha = 0.6f),
                                    start = Offset(currentX, currentY),
                                    end = Offset(depletionX, depletionY),
                                    strokeWidth = 2.dp.toPx()
                                )
                                // Depletion marker
                                drawCircle(
                                    color = StatusCritical,
                                    radius = 5.dp.toPx(),
                                    center = Offset(depletionX, depletionY)
                                )
                                // Flat at 100% from depletion to end
                                if (depletionX < leftPad + graphWidth) {
                                    drawDashedLine(
                                        color = StatusCritical.copy(alpha = 0.4f),
                                        start = Offset(depletionX, depletionY),
                                        end = Offset(leftPad + graphWidth, depletionY),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                            } else {
                                drawDashedLine(
                                    color = series.color.copy(alpha = 0.6f),
                                    start = Offset(currentX, currentY),
                                    end = Offset(leftPad + graphWidth, yAt(projectedAtEnd.coerceAtMost(100.0))),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Per-series stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Day ${String.format("%.1f", elapsedMs / (1000.0 * 60 * 60 * 24))} / 7",
                            color = ExtendedTheme.colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    // Same grouping and order as the chips above
                    visibleSeries.groupBy { it.provider }.forEach { (provider, group) ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GroupLabel(provider)
                            group.forEach { series ->
                                SeriesStatRow(
                                    series = series,
                                    weekStartMs = weekStartMs,
                                    weekEndMs = weekEndMs,
                                    now = now
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ExtendedTheme.colors.cardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LegendItem(color = ExtendedTheme.colors.textSecondary, label = "Actual usage (per limit color)")
                    Spacer(modifier = Modifier.height(8.dp))
                    LegendItem(color = ExtendedTheme.colors.textSecondary.copy(alpha = 0.6f), label = "Projected usage", dashed = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    LegendItem(color = StatusCritical.copy(alpha = 0.15f), label = "Danger zone (>80%)")
                    Spacer(modifier = Modifier.height(8.dp))
                    LegendItem(color = StatusCritical, label = "Projected depletion point")
                }
            }

            // Banner Ad
            Spacer(modifier = Modifier.height(16.dp))
            BannerAd(
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    } // Box
}

/**
 * Burning rate in %/h based on this series' own weekly window when its
 * reset time is known, else the shared window passed in.
 */
private fun WeeklySeries.burningRatePerHour(sharedWeekStartMs: Long, now: Instant): Double {
    val weekStart = resetsAt?.toEpochMilli()?.minus(Duration.ofDays(7).toMillis())
        ?: sharedWeekStartMs
    val elapsedHours = (now.toEpochMilli() - weekStart) / 3600_000.0
    return if (elapsedHours > 0) effectiveUtil / elapsedHours else 0.0
}

/**
 * Builds the graph's series, skipping anything the user switched off in
 * Settings so the forecast screen shows exactly the limits the rest of the
 * app tracks. The provider-wide Claude total has no Settings toggle (it is
 * a pinned card on the usage screen), so it is always present.
 */
private fun buildWeeklySeries(
    usageData: UsageData?,
    codexData: CodexUsageData?,
    history: Map<String, List<UsageHistoryEntry>>,
    hiddenMetrics: Set<String>
): List<WeeklySeries> {
    val palette = listOf(GraphBlue, GraphAmber, GraphPink, GraphCyan, GraphLime)
    var paletteIndex = 0
    fun nextColor() = palette[paletteIndex++ % palette.size]

    val result = mutableListOf<WeeklySeries>()
    val usedKeys = mutableSetOf(
        UsageHistoryStore.SERIES_WEEKLY_ALL,
        UsageHistoryStore.SERIES_CODEX_WEEKLY
    )

    // Overall Claude weekly limit — the total its per-model limits roll into
    result += WeeklySeries(
        key = UsageHistoryStore.SERIES_WEEKLY_ALL,
        label = "All",
        provider = PROVIDER_CLAUDE,
        isTotal = true,
        color = ClaudePurple,
        currentUtil = usageData?.sevenDay?.utilization,
        resetsAt = usageData?.sevenDay?.resetsAt,
        entries = history[UsageHistoryStore.SERIES_WEEKLY_ALL].orEmpty()
    )

    // Per-model weekly limits currently reported (Fable, Sonnet, ...)
    usageData?.dynamicMetrics
        ?.filter { it.key.startsWith("seven_day_") || it.label.endsWith("(7d)") }
        ?.forEach { labeled ->
            usedKeys += labeled.key
            if (labeled.key in hiddenMetrics) return@forEach
            result += WeeklySeries(
                key = labeled.key,
                label = labeled.label.substringBefore(" ("),
                provider = PROVIDER_CLAUDE,
                isTotal = false,
                color = nextColor(),
                currentUtil = labeled.metric.utilization,
                resetsAt = labeled.metric.resetsAt,
                entries = history[labeled.key].orEmpty()
            )
        }

    // Codex/GPT weekly window, behind the app-level Codex toggle
    if (AppPreferences.CODEX_METRIC_KEY !in hiddenMetrics) {
        val codexWeekly = codexData?.let { data ->
            listOfNotNull(data.primaryWindow, data.secondaryWindow)
                .firstOrNull { it.windowLabel == "weekly" }
        }
        result += WeeklySeries(
            key = UsageHistoryStore.SERIES_CODEX_WEEKLY,
            label = "GPT",
            provider = PROVIDER_OPENAI,
            isTotal = false,
            color = CodexGreen,
            currentUtil = codexWeekly?.usedPercent,
            resetsAt = codexWeekly?.resetAt,
            entries = history[UsageHistoryStore.SERIES_CODEX_WEEKLY].orEmpty()
        )
    }

    // Series that only exist in history (e.g. limit no longer reported)
    history.keys
        .filter { it !in usedKeys && it !in hiddenMetrics }
        .sorted()
        .forEach { key ->
            result += WeeklySeries(
                key = key,
                label = UsageData.labelForKey(key).substringBefore(" ("),
                provider = PROVIDER_CLAUDE,
                isTotal = false,
                color = nextColor(),
                currentUtil = null,
                resetsAt = null,
                entries = history[key].orEmpty()
            )
        }

    return result.filter { it.currentUtil != null || it.entries.isNotEmpty() }
}

@Composable
private fun GroupLabel(provider: String) {
    Text(
        text = provider.uppercase(),
        color = ExtendedTheme.colors.textMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp
    )
}

@Composable
private fun SeriesStatRow(
    series: WeeklySeries,
    weekStartMs: Long,
    weekEndMs: Long,
    now: Instant
) {
    val util = series.effectiveUtil
    val rate = series.burningRatePerHour(weekStartMs, now)
    val remainingHours = (weekEndMs - now.toEpochMilli()).coerceAtLeast(0) / 3600_000.0
    val projected = util + rate * remainingHours
    val hoursTo100 = if (rate > 0) (100.0 - util) / rate else Double.MAX_VALUE
    val willDeplete = projected >= 100.0 && hoursTo100 < remainingHours

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(series.color, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = series.label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = if (series.isTotal) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = String.format("%.1f%%", util),
            color = series.color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = String.format("%.2f%%/h", rate),
            color = ExtendedTheme.colors.textSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (willDeplete) formatDepletionTime(hoursTo100) else "Safe",
            color = if (willDeplete) StatusCritical else StatusExtra,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    dashed: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) {
            Canvas(modifier = Modifier.size(16.dp, 3.dp)) {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                    )
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = ExtendedTheme.colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

private fun DrawScope.drawDashedLine(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(8.dp.toPx(), 6.dp.toPx())
        )
    )
}

private fun formatDepletionTime(hours: Double): String {
    return when {
        hours < 1 -> "${(hours * 60).toInt()}m"
        hours < 24 -> String.format("%.1fh", hours)
        else -> String.format("%.1fd", hours / 24)
    }
}
