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
                PolicyLastUpdated("Last updated: February 8, 2026")

                PolicySectionTitle("Overview")
                PolicyBody("Claude Meter (\"the App\") is an Android utility that displays your Claude.ai usage information in real-time. This policy explains how the App handles your data.")

                PolicySectionTitle("Data Collection")
                PolicyBody("The App does not collect, store, or transmit any personal data to third-party servers.")

                PolicySubTitle("What the App stores locally on your device:")
                PolicyBullet("Session key — Used to authenticate with Claude.ai API. Stored in Android SharedPreferences (device-only).")
                PolicyBullet("Organization ID — Used to fetch your usage data. Stored locally.")
                PolicyBullet("Display preferences — Your metric visibility and notification settings.")

                PolicySubTitle("What the App does NOT do:")
                PolicyBullet("Does not collect analytics or telemetry")
                PolicyBullet("Does not use tracking SDKs")
                PolicyBullet("Does not share data with third parties")
                PolicyBullet("Does not store your usage data permanently — it is fetched on demand and displayed in real-time")

                PolicySectionTitle("Network Requests")
                PolicyBody("The App communicates only with claude.ai to fetch your current usage metrics, spending limit info, and prepaid balance. No other network requests are made.")

                PolicySectionTitle("Data Security")
                PolicyBullet("Your session key is stored locally on your device only")
                PolicyBullet("All network communication uses HTTPS")
                PolicyBullet("No data is sent to any server other than claude.ai")

                PolicySectionTitle("Third-Party Services")
                PolicyBody("This App is not affiliated with, endorsed by, or officially connected to Anthropic in any way. It uses the same API endpoints that the Claude.ai web interface uses.")

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
