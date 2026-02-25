package com.claudeusage.widget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.claudeusage.widget.MainActivity
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.data.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

class UsageAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val credentialManager = CredentialManager(context)
        val repository = UsageRepository()

        val usageData = try {
            val credentials = credentialManager.getCredentials()
            if (credentials != null) {
                withContext(Dispatchers.IO) {
                    repository.fetchUsageData(credentials).getOrNull()
                }
            } else null
        } catch (e: Exception) {
            null
        }

        provideContent {
            WidgetContent(usageData)
        }
    }
}

private val BgDark = ColorProvider(Color(0xFF0D0D0D))
private val BgTrack = ColorProvider(Color(0xFF2A2A40))
private val TextLight = ColorProvider(Color(0xFFE8E6F0))
private val TextDim = ColorProvider(Color(0xFFA09BB0))

@Composable
private fun WidgetContent(usageData: UsageData?) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BgDark)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        if (usageData == null) {
            NoDataContent()
        } else {
            UsageDataContent(usageData)
        }
    }
}

@Composable
private fun NoDataContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Claude Meter",
            style = TextStyle(
                color = TextLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap to sign in",
            style = TextStyle(
                color = TextDim,
                fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun UsageDataContent(data: UsageData) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
    ) {
        Text(
            text = "Claude Meter",
            style = TextStyle(
                color = TextLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        WidgetUsageRow(
            label = "Session",
            utilization = data.fiveHour?.utilization ?: 0.0
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        WidgetProgressBar(utilization = data.fiveHour?.utilization ?: 0.0)

        Spacer(modifier = GlanceModifier.height(8.dp))

        WidgetUsageRow(
            label = "Weekly",
            utilization = data.sevenDay?.utilization ?: 0.0
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        WidgetProgressBar(utilization = data.sevenDay?.utilization ?: 0.0)

        // Coach message
        val coachMsg = getCoachMessage(data)
        if (coachMsg != null) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = coachMsg,
                style = TextStyle(
                    color = TextDim,
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WidgetUsageRow(label: String, utilization: Double) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = TextDim,
                fontSize = 11.sp
            )
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = String.format("%.1f%%", utilization),
            style = TextStyle(
                color = ColorProvider(getStatusColor(utilization)),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun WidgetProgressBar(utilization: Double) {
    val statusColor = getStatusColor(utilization)
    val alpha = (utilization / 100.0).toFloat().coerceIn(0.15f, 1f)
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(6.dp)
            .cornerRadius(3.dp)
            .background(BgTrack)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(6.dp)
                .cornerRadius(3.dp)
                .background(ColorProvider(statusColor.copy(alpha = alpha)))
        ) {}
    }
}

private fun getCoachMessage(data: UsageData): String? {
    val sessionUtil = data.fiveHour?.utilization ?: 0.0
    val sessionRemaining = data.fiveHour?.remainingDuration
    val weeklyUtil = data.sevenDay?.utilization ?: 0.0
    val weeklyRemaining = data.sevenDay?.remainingDuration

    return when {
        sessionUtil >= 100.0 -> "\u26A0 Session maxed — switch tasks!"
        sessionUtil > 80.0 && sessionRemaining != null
            && sessionRemaining < Duration.ofMinutes(30) -> "\uD83D\uDCA1 Reset imminent — take a break"
        weeklyUtil > 70.0 && weeklyRemaining != null
            && weeklyRemaining > Duration.ofDays(2) -> "\uD83D\uDCA1 70%+ weekly — focus on high-impact"
        weeklyUtil < 50.0 && weeklyRemaining != null
            && weeklyRemaining > Duration.ofDays(3) -> "\uD83D\uDE80 Plenty of capacity!"
        else -> null
    }
}

private fun getStatusColor(utilization: Double): Color {
    return when {
        utilization >= 90.0 -> Color(0xFFE85454)
        utilization >= 75.0 -> Color(0xFFE8943A)
        else -> Color(0xFF6B4FBB)
    }
}
