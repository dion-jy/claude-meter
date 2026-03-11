package com.claudeusage.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.claudeusage.widget.ui.theme.*

class CodexLoginActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var cookiePollingRunnable: Runnable? = null
    private var sessionCaptured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ClaudeUsageTheme {
                var webViewError by remember { mutableStateOf<String?>(null) }

                if (webViewError != null) {
                    WebViewErrorScreen(
                        message = webViewError!!,
                        onClose = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                } else {
                    CodexLoginWebViewScreen(
                        onSessionCaptured = { cookies ->
                            if (!sessionCaptured) {
                                sessionCaptured = true
                                stopCookiePolling()
                                val resultIntent = Intent().apply {
                                    putExtra(EXTRA_COOKIES, cookies)
                                }
                                setResult(Activity.RESULT_OK, resultIntent)
                                finish()
                            }
                        },
                        onClose = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                        onError = { error ->
                            webViewError = error
                        },
                        onStartPolling = { callback ->
                            startCookiePolling(callback)
                        }
                    )
                }
            }
        }
    }

    private fun startCookiePolling(onSessionCaptured: (String) -> Unit) {
        stopCookiePolling()
        cookiePollingRunnable = object : Runnable {
            override fun run() {
                if (sessionCaptured) return
                val cookies = extractSessionCookies()
                if (cookies != null) {
                    onSessionCaptured(cookies)
                } else {
                    handler.postDelayed(this, 2000)
                }
            }
        }
        handler.postDelayed(cookiePollingRunnable!!, 2000)
    }

    private fun stopCookiePolling() {
        cookiePollingRunnable?.let { handler.removeCallbacks(it) }
        cookiePollingRunnable = null
    }

    private fun extractSessionCookies(): String? {
        return try {
            val cookies = CookieManager.getInstance().getCookie("https://chatgpt.com") ?: return null
            // Only __Secure-next-auth.session-token indicates a real login
            // __cf_bm and _puid are set before login and must NOT be used
            if (cookies.contains("__Secure-next-auth.session-token")) {
                cookies.trim()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCookiePolling()
    }

    companion object {
        const val EXTRA_COOKIES = "codex_cookies"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewErrorScreen(
    message: String,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to ChatGPT", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ExtendedTheme.colors.textSecondary)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "WebView Unavailable",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = ExtendedTheme.colors.textSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CodexLoginWebViewScreen(
    onSessionCaptured: (String) -> Unit,
    onClose: () -> Unit,
    onError: (String) -> Unit,
    onStartPolling: ((String) -> Unit) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var showHint by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sign in to ChatGPT",
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    try {
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportMultipleWindows(false)
                            settings.javaScriptCanOpenWindowsAutomatically = true

                            val webView = this
                            try {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(webView, true)
                                cookieManager.removeAllCookies(null)
                                cookieManager.flush()
                            } catch (_: Exception) {}

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    return false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false

                                    val isAuthPage = url?.contains("/auth") == true
                                    showHint = isAuthPage

                                    // Only check cookies on non-auth pages (post-login)
                                    if (!isAuthPage && url?.contains("chatgpt.com") == true) {
                                        checkForSessionCookies(onSessionCaptured)
                                        onStartPolling(onSessionCaptured)
                                    }
                                }
                            }

                            loadUrl("https://chatgpt.com/auth/login")
                        }
                    } catch (e: Exception) {
                        onError("Failed to initialize WebView: ${e.message}")
                        android.view.View(context)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Login hint banner
            if (showHint) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = CodexGreen
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Sign in with your ChatGPT account to track Codex usage.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = CodexGreen,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

private fun checkForSessionCookies(onSessionCaptured: (String) -> Unit) {
    try {
        val cookies = CookieManager.getInstance().getCookie("https://chatgpt.com") ?: return
        if (cookies.contains("__Secure-next-auth.session-token")) {
            onSessionCaptured(cookies.trim())
        }
    } catch (_: Exception) {}
}
