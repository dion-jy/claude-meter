package com.claudeusage.widget.ui.screens

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claudeusage.shared.model.Credentials
import com.claudeusage.shared.model.UsageData
import com.claudeusage.shared.repository.AuthException
import com.claudeusage.shared.repository.UsageRepository
import com.claudeusage.widget.MainActivity
import com.claudeusage.widget.R
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.data.local.CredentialManager
import com.claudeusage.widget.data.local.UsageHistoryEntry
import com.claudeusage.widget.data.local.UsageHistoryStore
import com.claudeusage.widget.service.UsageNotificationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration

sealed class UiState {
    data object Loading : UiState()
    data object LoginRequired : UiState()
    data class Success(val data: UsageData) : UiState()
    data class Error(val message: String, val isAuthError: Boolean = false) : UiState()
}

class UsageViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialManager = CredentialManager(application)
    private val appPreferences = AppPreferences(application)
    private val repository = UsageRepository()
    private val historyStore = UsageHistoryStore(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastUpdated = MutableStateFlow<String?>(null)
    val lastUpdated: StateFlow<String?> = _lastUpdated.asStateFlow()

    private val _usageHistory = MutableStateFlow<List<UsageHistoryEntry>>(emptyList())
    val usageHistory: StateFlow<List<UsageHistoryEntry>> = _usageHistory.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var prevSessionUtil: Double? = null
    private var prevWeeklyUtil: Double? = null
    private var prevSonnetUtil: Double? = null
    private var prevOpusUtil: Double? = null
    private var lastCoachEvalTime: Long = 0L

    init {
        createCoachNotificationChannel()
        _usageHistory.value = historyStore.getHistory()
        checkCredentialsAndLoad()
    }

    fun checkCredentialsAndLoad() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            if (credentialManager.hasCredentials()) {
                fetchUsageData()
            } else {
                _uiState.value = UiState.LoginRequired
            }
        }
    }

    fun onLoginComplete(sessionKey: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val orgResult = repository.fetchOrganizationId(sessionKey)
            orgResult.fold(
                onSuccess = { orgId ->
                    val credentials = Credentials(sessionKey, orgId)
                    credentialManager.saveCredentials(credentials)
                    fetchUsageData()
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(
                        message = error.message ?: "Failed to get organization info.",
                        isAuthError = error is AuthException
                    )
                }
            )
        }
    }

    fun onManualLogin(sessionKey: String) {
        onLoginComplete(sessionKey)
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchUsageData()
            _isRefreshing.value = false
        }
    }

    fun logout() {
        autoRefreshJob?.cancel()
        credentialManager.clearCredentials()
        _uiState.value = UiState.LoginRequired
        _lastUpdated.value = null
        UsageNotificationService.stop(getApplication())
    }

    private suspend fun fetchUsageData() {
        val credentials = credentialManager.getCredentials()
        if (credentials == null) {
            _uiState.value = UiState.LoginRequired
            return
        }

        val result = repository.fetchUsageData(credentials)
        result.fold(
            onSuccess = { data ->
                _uiState.value = UiState.Success(data)
                _lastUpdated.value = formatLastUpdated()
                startAutoRefresh()
                if (appPreferences.notificationEnabled) {
                    UsageNotificationService.forceUpdate(getApplication())
                }
                // Coach features (push notifications)
                if (appPreferences.coachEnabled) {
                    detectResets(data)
                    val now = System.currentTimeMillis()
                    if (now - lastCoachEvalTime >= COACH_EVAL_INTERVAL_MS) {
                        evaluateCoachNotification(data)
                        lastCoachEvalTime = now
                    }
                }
                recordUsageHistory(data)
                // Update previous values for next comparison
                prevSessionUtil = data.fiveHour?.utilization
                prevWeeklyUtil = data.sevenDay?.utilization
                prevSonnetUtil = data.sevenDaySonnet?.utilization
                prevOpusUtil = data.sevenDayOpus?.utilization
            },
            onFailure = { error ->
                val isAuth = error is AuthException
                if (isAuth) {
                    credentialManager.clearCredentials()
                }
                // Only show error if we don't already have data
                val currentState = _uiState.value
                if (currentState is UiState.Success) {
                    // Keep existing data, just update timestamp note
                    _lastUpdated.value = "Update failed - ${formatLastUpdated()}"
                } else {
                    _uiState.value = UiState.Error(
                        message = error.message ?: "Failed to fetch usage data.",
                        isAuthError = isAuth
                    )
                }
            }
        )
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(UPDATE_INTERVAL_MS)
                fetchUsageData()
            }
        }
    }

    private fun formatLastUpdated(): String {
        val now = java.time.LocalTime.now()
        return String.format("%02d:%02d", now.hour, now.minute)
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }

    private fun evaluateCoachNotification(data: UsageData) {
        val sessionUtil = data.fiveHour?.utilization ?: 0.0
        val sessionRemaining = data.fiveHour?.remainingDuration
        val weeklyUtil = data.sevenDay?.utilization ?: 0.0
        val weeklyRemaining = data.sevenDay?.remainingDuration
        val sonnetUtil = data.sevenDaySonnet?.utilization ?: 0.0
        val opusUtil = data.sevenDayOpus?.utilization ?: 0.0

        val (title, message) = when {
            // Session maxed out
            sessionUtil >= 100.0 -> {
                val timeStr = sessionRemaining?.let { formatDuration(it) } ?: ""
                "Session maxed out" to "Resets in $timeStr — switch tasks or take a break!"
            }
            // Session > 80% & reset < 30min
            sessionUtil > 80.0 && sessionRemaining != null
                && sessionRemaining < Duration.ofMinutes(30) -> {
                "Reset imminent" to "Take a break, come back fully charged"
            }
            // Session < 30% & reset < 1h
            sessionUtil < 30.0 && sessionUtil > 0.0 && sessionRemaining != null
                && sessionRemaining < Duration.ofHours(1) -> {
                val timeStr = formatDuration(sessionRemaining)
                "Session resets in $timeStr" to "Don't waste the remaining capacity!"
            }
            // Sonnet > 80%
            sonnetUtil > 80.0 -> {
                "Sonnet at ${sonnetUtil.toInt()}%" to "Consider switching to other models"
            }
            // Opus > 80%
            opusUtil > 80.0 -> {
                "Opus at ${opusUtil.toInt()}%" to "Consider switching to other models"
            }
            // Weekly > 70% & reset > 2 days
            weeklyUtil > 70.0 && weeklyRemaining != null
                && weeklyRemaining > Duration.ofDays(2) -> {
                "Weekly at ${weeklyUtil.toInt()}%" to "Focus on high-impact tasks only"
            }
            // Weekly < 40% & reset < 1 day
            weeklyUtil < 40.0 && weeklyUtil > 0.0 && weeklyRemaining != null
                && weeklyRemaining < Duration.ofDays(1) -> {
                "Last sprint!" to "Use your remaining weekly capacity before reset"
            }
            // Weekly < 50% & reset > 3 days
            weeklyUtil < 50.0 && weeklyRemaining != null
                && weeklyRemaining > Duration.ofDays(3) -> {
                "Plenty of capacity" to "Perfect time for deep work!"
            }
            else -> return
        }
        sendCoachPushNotification(title, message, COACH_ANALYSIS_ID)
    }

    private fun detectResets(data: UsageData) {
        // Session reset: previous utilization was significant, now dropped to near zero
        val currentSessionUtil = data.fiveHour?.utilization ?: 0.0
        val prevSession = prevSessionUtil
        if (prevSession != null && prevSession > 30.0 && currentSessionUtil < 5.0) {
            sendCoachPushNotification(
                "Fully charged!",
                "Session reset complete — start a new session now",
                COACH_SESSION_RESET_ID
            )
        }

        // Weekly reset: previous utilization was significant, now dropped to near zero
        val currentWeeklyUtil = data.sevenDay?.utilization ?: 0.0
        val prevWeekly = prevWeeklyUtil
        if (prevWeekly != null && prevWeekly > 30.0 && currentWeeklyUtil < 5.0) {
            sendCoachPushNotification(
                "Weekly reset!",
                "Fresh weekly capacity — let's make this week count",
                COACH_WEEKLY_RESET_ID
            )
            historyStore.clearHistory()
            _usageHistory.value = emptyList()
        }

        // Sonnet reset
        val currentSonnetUtil = data.sevenDaySonnet?.utilization ?: 0.0
        val prevSonnet = prevSonnetUtil
        if (prevSonnet != null && prevSonnet > 30.0 && currentSonnetUtil < 5.0) {
            sendCoachPushNotification(
                "Sonnet reset!",
                "Sonnet weekly limit refreshed",
                COACH_SONNET_RESET_ID
            )
        }

        // Opus reset
        val currentOpusUtil = data.sevenDayOpus?.utilization ?: 0.0
        val prevOpus = prevOpusUtil
        if (prevOpus != null && prevOpus > 30.0 && currentOpusUtil < 5.0) {
            sendCoachPushNotification(
                "Opus reset!",
                "Opus weekly limit refreshed",
                COACH_OPUS_RESET_ID
            )
        }
    }

    private fun recordUsageHistory(data: UsageData) {
        val weeklyUtil = data.sevenDay?.utilization ?: return
        historyStore.addEntry(System.currentTimeMillis(), weeklyUtil)
        _usageHistory.value = historyStore.getHistory()
    }

    private fun createCoachNotificationChannel() {
        val channel = NotificationChannel(
            COACH_CHANNEL_ID,
            "Productivity Coach",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Smart usage tips and reset alerts"
        }
        val manager = getApplication<Application>()
            .getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun sendCoachPushNotification(title: String, message: String, notificationId: Int) {
        val app = getApplication<Application>()
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            app, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, COACH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    private fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds
        if (totalSeconds <= 0) return "0m"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        return when {
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    companion object {
        const val UPDATE_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val COACH_EVAL_INTERVAL_MS = 2 * 60 * 60 * 1000L // 2 hours
        private const val COACH_CHANNEL_ID = "coach_channel"
        private const val COACH_SESSION_RESET_ID = 2001
        private const val COACH_WEEKLY_RESET_ID = 2002
        private const val COACH_SONNET_RESET_ID = 2003
        private const val COACH_OPUS_RESET_ID = 2004
        private const val COACH_ANALYSIS_ID = 2010
    }
}
