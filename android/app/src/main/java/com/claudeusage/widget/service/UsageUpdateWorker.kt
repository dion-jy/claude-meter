package com.claudeusage.widget.service

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.repository.UsageRepository
import com.claudeusage.widget.widget.UsageWidgetReceiver

class UsageUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val credentialManager = CredentialManager(applicationContext)
        val repository = UsageRepository()

        val credentials = credentialManager.getCredentials() ?: return Result.failure()

        val result = repository.fetchUsageData(credentials)

        return if (result.isSuccess) {
            // Trigger widget update
            try {
                UsageWidgetReceiver.updateWidget(applicationContext)
            } catch (_: Exception) {
                // Widget might not be placed
            }

            // Update persistent notification if enabled
            val prefs = AppPreferences(applicationContext)
            if (prefs.notificationEnabled) {
                try {
                    val data = result.getOrNull()
                    if (data != null) {
                        UsageNotificationService.ensureChannel(applicationContext)
                        val notification = UsageNotificationService.buildUsageNotification(applicationContext, data)
                        val manager = applicationContext.getSystemService(NotificationManager::class.java)
                        manager.notify(UsageNotificationService.NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {
                    // Notification update is best-effort
                }
            }

            Result.success()
        } else {
            Result.retry()
        }
    }
}
