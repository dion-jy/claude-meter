package com.claudeusage.widget.data.model

data class CodexCredentials(
    val accessToken: String,
    val sessionCookies: String
) {
    val isValid: Boolean
        get() = accessToken.isNotBlank()
}
