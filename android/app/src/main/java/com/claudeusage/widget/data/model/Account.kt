package com.claudeusage.widget.data.model

/**
 * One saved login. [label] is what the account switcher shows (the
 * account email when the server reports one), [credentials] is either
 * [Credentials] (Claude) or [CodexCredentials] (ChatGPT).
 */
data class Account<T>(
    val id: String,
    val label: String,
    val credentials: T
)
