package com.claudeusage.widget.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.claudeusage.widget.data.model.CodexCredentials

class CodexCredentialManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "codex_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getCredentials(): CodexCredentials? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val cookies = prefs.getString(KEY_SESSION_COOKIES, null) ?: return null
        return CodexCredentials(token, cookies)
    }

    fun saveCredentials(credentials: CodexCredentials) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, credentials.accessToken)
            .putString(KEY_SESSION_COOKIES, credentials.sessionCookies)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    fun hasCredentials(): Boolean {
        return getCredentials()?.isValid == true
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_SESSION_COOKIES = "session_cookies"
    }
}
