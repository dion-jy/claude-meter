package com.claudeusage.widget.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.claudeusage.widget.MainActivity
import com.claudeusage.widget.R
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.model.UsageData
import com.claudeusage.widget.data.model.UsageMetric
import com.claudeusage.widget.data.repository.AuthException
import com.claudeusage.widget.data.repository.UsageRepository
import kotlinx.coroutines.*

class UsageNotificationService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = UsageRepository()
    private lateinit var credentialManager: CredentialManager
    private var consecutiveFailures = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        credentialManager = CredentialManager(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildSimpleNotification("Loading usage data..."))
        startPolling()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Claude Meter",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current Claude usage status"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                updateNotification()
                val backoffDelay = if (consecutiveFailures > 0) {
                    // Exponential backoff: 3min, 6min, 12min, capped at 30min
                    val multiplier = (1L shl consecutiveFailures.coerceAtMost(3))
                    (UPDATE_INTERVAL_MS * multiplier).coerceAtMost(MAX_BACKOFF_MS)
                } else {
                    UPDATE_INTERVAL_MS
                }
                delay(backoffDelay)
            }
        }
    }

    private suspend fun updateNotification() {
        val credentials = credentialManager.getCredentials()
        if (credentials == null) {
            stopSelf()
            return
        }

        val result = repository.fetchUsageData(credentials)
        result.onSuccess { data ->
            consecutiveFailures = 0
            val notification = buildUsageNotification(data)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        }
        result.onFailure { error ->
            consecutiveFailures++
            if (error is AuthException) {
                stopSelf()
            }
        }
    }

    private fun buildUsageNotification(data: UsageData): Notification {
        val remoteViews = RemoteViews(packageName, R.layout.notification_usage)

        if (data.fiveHour != null) {
            val progress = data.fiveHour.utilization.toInt().coerceIn(0, 100)
            remoteViews.setProgressBar(R.id.progress_5h, 100, progress, false)
            remoteViews.setTextViewText(R.id.percent_5h, String.format("%.1f%%", data.fiveHour.utilization))
            remoteViews.setTextViewText(R.id.time_5h, formatRemaining(data.fiveHour))
        }

        if (data.sevenDay != null) {
            val progress = data.sevenDay.utilization.toInt().coerceIn(0, 100)
            remoteViews.setProgressBar(R.id.progress_7d, 100, progress, false)
            remoteViews.setTextViewText(R.id.percent_7d, String.format("%.1f%%", data.sevenDay.utilization))
            remoteViews.setTextViewText(R.id.time_7d, formatRemaining(data.sevenDay))
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()
    }

    private fun formatRemaining(metric: UsageMetric): String {
        val remaining = metric.remainingDuration ?: return ""
        if (remaining.seconds <= 0) return ""
        val d = remaining.seconds / 86400
        val h = (remaining.seconds % 86400) / 3600
        val m = (remaining.seconds % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            else -> ""
        }
    }

    private fun buildSimpleNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "claude_usage_channel"
        const val NOTIFICATION_ID = 1001
        const val UPDATE_INTERVAL_MS = 3 * 60 * 1000L // 3 minutes
        const val MAX_BACKOFF_MS = 30 * 60 * 1000L // 30 minutes

        fun start(context: Context) {
            val intent = Intent(context, UsageNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, UsageNotificationService::class.java)
            context.stopService(intent)
        }
    }
}
