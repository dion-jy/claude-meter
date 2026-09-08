package com.claudeusage.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.claudeusage.widget.data.local.AppPreferences
import com.claudeusage.widget.service.UsageNotificationService
import com.claudeusage.widget.service.UsageUpdateScheduler
import com.claudeusage.widget.ui.screens.CodexUiState
import com.claudeusage.widget.ui.screens.ForecastScreen
import com.claudeusage.widget.ui.screens.MetricToggle
import com.claudeusage.widget.ui.screens.SettingsScreen
import com.claudeusage.widget.ui.screens.PrivacyPolicyScreen
import com.claudeusage.widget.ui.screens.UiState
import com.claudeusage.widget.ui.screens.UsageScreen
import com.claudeusage.widget.ui.screens.UsageViewModel
import com.claudeusage.widget.ui.components.InterstitialAdManager
import com.claudeusage.widget.ui.theme.ClaudeUsageTheme

private enum class Screen { Usage, Settings, Forecast, PrivacyPolicy }

class MainActivity : ComponentActivity() {

    private val viewModel: UsageViewModel by viewModels()
    private lateinit var appPreferences: AppPreferences

    private var currentScreen by mutableStateOf(Screen.Usage)
    private var notificationEnabled by mutableStateOf(false)
    private var coachEnabled by mutableStateOf(true)
    private var themeMode by mutableStateOf(AppPreferences.THEME_DARK)
    private var hiddenMetrics by mutableStateOf<Set<String>>(emptySet())
    private var hiddenGraphSeries by mutableStateOf<Set<String>>(emptySet())
    private val interstitialAdManager = InterstitialAdManager()

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val sessionKey = result.data?.getStringExtra(LoginActivity.EXTRA_SESSION_KEY)
            if (sessionKey != null) {
                viewModel.onLoginComplete(sessionKey)
            }
        }
    }

    private val codexLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val cookies = result.data?.getStringExtra(CodexLoginActivity.EXTRA_COOKIES)
            if (cookies != null) {
                viewModel.onCodexLoginComplete(cookies)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            appPreferences.notificationEnabled = true
            notificationEnabled = true
            if (viewModel.uiState.value is UiState.Success) {
                UsageNotificationService.start(applicationContext)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        appPreferences = AppPreferences(applicationContext)
        notificationEnabled = appPreferences.notificationEnabled
        coachEnabled = appPreferences.coachEnabled
        themeMode = appPreferences.themeMode

        // Load metric visibility from preferences
        hiddenMetrics = appPreferences.hiddenMetricKeys
        hiddenGraphSeries = appPreferences.hiddenGraphSeries

        // Request notification permission on first launch (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Preload interstitial ad
        interstitialAdManager.load(this)

        // Schedule background updates
        UsageUpdateScheduler.schedule(applicationContext)

        setContent {
            val isDark = when (themeMode) {
                AppPreferences.THEME_LIGHT -> false
                AppPreferences.THEME_SYSTEM -> isSystemInDarkTheme()
                else -> true
            }
            ClaudeUsageTheme(darkTheme = isDark) {
                val uiState by viewModel.uiState.collectAsState()
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val lastUpdated by viewModel.lastUpdated.collectAsState()
                val usageHistory by viewModel.usageHistory.collectAsState()
                val codexState by viewModel.codexState.collectAsState()

                when (currentScreen) {
                    Screen.Usage -> {
                        UsageScreen(
                            uiState = uiState,
                            isRefreshing = isRefreshing,
                            lastUpdated = lastUpdated,
                            hiddenMetrics = hiddenMetrics,
                            codexState = codexState,
                            onRefresh = viewModel::refresh,
                            onLogout = {
                                interstitialAdManager.showThen(this@MainActivity) {
                                    viewModel.logout()
                                    UsageUpdateScheduler.cancel(applicationContext)
                                }
                            },
                            onLoginClick = { launchLogin() },
                            onManualLogin = { sessionKey ->
                                viewModel.onManualLogin(sessionKey)
                            },
                            onSettingsClick = { currentScreen = Screen.Settings },
                            onForecastClick = { currentScreen = Screen.Forecast },
                            onCodexLoginClick = { launchCodexLogin() },
                            onCodexLogout = {
                                interstitialAdManager.showThen(this@MainActivity) {
                                    viewModel.logoutCodex()
                                }
                            }
                        )
                    }
                    Screen.Settings -> {
                        BackHandler { currentScreen = Screen.Usage }
                        // One toggle per metric the server currently reports,
                        // plus the app-level Codex toggle
                        val usageData = (uiState as? UiState.Success)?.data
                        val availableToggles = buildList {
                            usageData?.extraMetrics?.forEach { labeled ->
                                add(
                                    MetricToggle(
                                        labeled.key,
                                        labeled.label,
                                        labeled.key !in hiddenMetrics
                                    )
                                )
                            }
                            add(MetricToggle("codex_usage", "Codex Usage", "codex_usage" !in hiddenMetrics))
                        }

                        SettingsScreen(
                            notificationEnabled = notificationEnabled,
                            onNotificationToggle = { enabled ->
                                handleNotificationToggle(enabled)
                            },
                            coachEnabled = coachEnabled,
                            onCoachToggle = { enabled ->
                                coachEnabled = enabled
                                appPreferences.coachEnabled = enabled
                            },
                            metricToggles = availableToggles,
                            onMetricToggle = { key, enabled ->
                                handleMetricToggle(key, enabled)
                            },
                            themeMode = themeMode,
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                appPreferences.themeMode = mode
                            },
                            onPrivacyPolicyClick = { currentScreen = Screen.PrivacyPolicy },
                            onBack = { currentScreen = Screen.Usage }
                        )
                    }
                    Screen.PrivacyPolicy -> {
                        BackHandler { currentScreen = Screen.Settings }
                        PrivacyPolicyScreen(
                            onBack = { currentScreen = Screen.Settings }
                        )
                    }
                    Screen.Forecast -> {
                        BackHandler { currentScreen = Screen.Usage }
                        val usageData = (uiState as? UiState.Success)?.data
                        val codexData = (codexState as? CodexUiState.Connected)?.data
                            ?: (uiState as? UiState.Success)?.codexData
                        ForecastScreen(
                            usageData = usageData,
                            codexData = codexData,
                            history = usageHistory,
                            hiddenSeries = hiddenGraphSeries,
                            onToggleSeries = { key, visible ->
                                val updated = if (visible) {
                                    hiddenGraphSeries - key
                                } else {
                                    hiddenGraphSeries + key
                                }
                                hiddenGraphSeries = updated
                                appPreferences.hiddenGraphSeries = updated
                            },
                            onBack = { currentScreen = Screen.Usage }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForeground()
        val state = viewModel.uiState.value
        if (state is UiState.Success) {
            viewModel.refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppBackground()
    }

    private fun launchLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        loginLauncher.launch(intent)
    }

    private fun launchCodexLogin() {
        val intent = Intent(this, CodexLoginActivity::class.java)
        codexLoginLauncher.launch(intent)
    }

    private fun handleNotificationToggle(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    appPreferences.notificationEnabled = true
                    notificationEnabled = true
                    if (viewModel.uiState.value is UiState.Success) {
                        UsageNotificationService.start(applicationContext)
                    }
                } else {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            } else {
                appPreferences.notificationEnabled = true
                notificationEnabled = true
                if (viewModel.uiState.value is UiState.Success) {
                    UsageNotificationService.start(applicationContext)
                }
            }
        } else {
            appPreferences.notificationEnabled = false
            notificationEnabled = false
            UsageNotificationService.stop(applicationContext)
        }
    }

    private fun handleMetricToggle(key: String, enabled: Boolean) {
        hiddenMetrics = if (enabled) hiddenMetrics - key else hiddenMetrics + key
        appPreferences.hiddenMetricKeys = hiddenMetrics
    }
}
