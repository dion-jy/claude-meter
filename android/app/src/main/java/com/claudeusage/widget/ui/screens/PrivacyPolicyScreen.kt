package com.claudeusage.widget.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudeusage.widget.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
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
                            text = "Privacy Policy",
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
                PolicyLastUpdated("Last updated: August 23, 2026")

                PolicySectionTitle("Overview")
                PolicyBody("Claude Meter (\"the App\") is an Android utility that displays your Claude.ai (and optionally ChatGPT/Codex) usage information in real-time. This policy explains how the App handles your data.")

                PolicySectionTitle("Data Collection")
                PolicyBody("The App itself does not collect, store, or transmit any personal data to servers operated by the developer. However, the App includes the Google AdMob advertising SDK, which collects certain identifiers as described in the Advertising section below.")

                PolicySubTitle("What the App stores locally on your device:")
                PolicyBullet("Claude session key — Used to authenticate with the Claude.ai API. Stored in encrypted storage (device-only).")
                PolicyBullet("ChatGPT credentials (optional) — If you connect Codex usage tracking, the access token and session cookies are stored in encrypted storage on your device only.")
                PolicyBullet("Organization ID — Used to fetch your usage data. Stored locally.")
                PolicyBullet("Display preferences — Your metric visibility and notification settings.")
                PolicyBullet("Usage history — Recent usage snapshots kept locally for the forecast feature.")

                PolicySubTitle("What the App does NOT do:")
                PolicyBullet("Does not collect analytics or telemetry")
                PolicyBullet("Does not share your Claude/ChatGPT credentials or usage data with third parties")
                PolicyBullet("Does not send your data to any server operated by the developer")

                PolicySectionTitle("Advertising (Google AdMob)")
                PolicyBody("The App displays ads (banner and interstitial) served by Google AdMob. To serve and measure ads, the Google Mobile Ads SDK may automatically collect and share with Google:")
                PolicyBullet("Device or other IDs — the Android Advertising ID")
                PolicyBullet("IP address and coarse location derived from it")
                PolicyBullet("Ad interaction data (impressions, clicks) and diagnostic information")
                PolicyBody("This data is collected by Google, not by the developer, and is used for advertising, ad measurement, and fraud prevention. See Google's Privacy Policy (policies.google.com/privacy) for details. You can limit ad personalization or delete/reset your Advertising ID at any time in your device settings under Settings > Google > Ads.")

                PolicySectionTitle("Network Requests")
                PolicyBullet("claude.ai — to fetch your usage metrics, spending limit info, and prepaid balance")
                PolicyBullet("chatgpt.com (only if you enable Codex tracking) — to authenticate and fetch your Codex usage data")
                PolicyBullet("Google ad servers — to load and display AdMob ads")
                PolicyBody("No other network requests are made.")

                PolicySectionTitle("Data Security")
                PolicyBullet("Your session key and tokens are stored only on your device, in encrypted storage")
                PolicyBullet("All network communication uses HTTPS")

                PolicySectionTitle("Third-Party Services")
                PolicyBody("Google AdMob is used to display ads, as described above. This App is not affiliated with, endorsed by, or officially connected to Anthropic or OpenAI in any way. It uses the same API endpoints that the Claude.ai and ChatGPT web interfaces use.")

                PolicySectionTitle("Children's Privacy")
                PolicyBody("This App is not intended for use by children under 13.")

                PolicySectionTitle("Changes")
                PolicyBody("This policy may be updated occasionally. Changes will be reflected in future app updates.")

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PolicyLastUpdated(text: String) {
    Text(
        text = text,
        color = ExtendedTheme.colors.textMuted,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
private fun PolicySectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun PolicySubTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun PolicyBody(text: String) {
    Text(
        text = text,
        color = ExtendedTheme.colors.textSecondary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun PolicyBullet(text: String) {
    Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Text(
            text = "\u2022  ",
            color = ExtendedTheme.colors.textSecondary,
            fontSize = 14.sp
        )
        Text(
            text = text,
            color = ExtendedTheme.colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}
