package com.claudeusage.widget.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.claudeusage.widget.MainActivity
import com.claudeusage.widget.R
import com.claudeusage.shared.model.UsageData
import com.claudeusage.shared.model.UsageMetric
import com.claudeusage.shared.repository.UsageRepository
import com.claudeusage.widget.data.local.CredentialManager
import kotlinx.coroutines.*

class UsageNotificationService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository = UsageRepository()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FORCE_UPDATE) {
            scope.launch { updateNotification() }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
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
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private suspend fun updateNotification() {
        val credentialManager = CredentialManager(applicationContext)
        val credentials = credentialManager.getCredentials() ?: return

        val result = repository.fetchUsageData(credentials)
        result.onSuccess { data ->
            val notification = buildUsageNotification(data)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
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
        if (remaining.inWholeSeconds <= 0) return ""
        val d = remaining.inWholeSeconds / 86400
        val h = (remaining.inWholeSeconds % 86400) / 3600
        val m = (remaining.inWholeSeconds % 3600) / 60
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
        repository.cancelPendingRequests()
        scope.cancel()
    }

    companion object {
        private const val TAG = "UsageNotificationService"
        const val CHANNEL_ID = "claude_usage_channel"
        const val NOTIFICATION_ID = 1001
        const val UPDATE_INTERVAL_MS = 3 * 60 * 1000L // 3 minutes
        private const val ACTION_FORCE_UPDATE = "com.claudeusage.widget.FORCE_UPDATE"

        fun start(context: Context) {
            val intent = Intent(context, UsageNotificationService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Log.w(TAG, "Cannot start foreground service from background", e)
                } else {
                    throw e
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, UsageNotificationService::class.java)
            context.stopService(intent)
        }

        fun forceUpdate(context: Context) {
            val intent = Intent(context, UsageNotificationService::class.java).apply {
                action = ACTION_FORCE_UPDATE
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Log.w(TAG, "Cannot update foreground service from background", e)
                } else {
                    throw e
                }
            }
        }
    }
}
