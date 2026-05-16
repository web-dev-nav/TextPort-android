package com.brainandbolt.textport.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "textport_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setToken(token: String) = prefs.edit().putString("token", token).apply()
    fun token(): String? = prefs.getString("token", null)

    fun setConsent(value: Boolean) = prefs.edit().putBoolean("consent", value).apply()
    fun hasConsent(): Boolean = prefs.getBoolean("consent", false)

    fun setSyncEnabled(value: Boolean) = prefs.edit().putBoolean("sync_enabled", value).apply()
    fun syncEnabled(): Boolean = prefs.getBoolean("sync_enabled", false)

    fun setApiBaseUrl(value: String) = prefs.edit().putString("api_base_url", value).apply()
    fun apiBaseUrl(): String? = prefs.getString("api_base_url", null)

    fun setActivationCode(value: String) = prefs.edit().putString("activation_code", value).apply()
    fun activationCode(): String? = prefs.getString("activation_code", null)

    fun setSetupCompleted(value: Boolean) = prefs.edit().putBoolean("setup_completed", value).apply()
    fun setupCompleted(): Boolean = prefs.getBoolean("setup_completed", false)

    fun setPermissionPrompted(value: Boolean) = prefs.edit().putBoolean("permission_prompted", value).apply()
    fun permissionPrompted(): Boolean = prefs.getBoolean("permission_prompted", false)

    fun setConnectionVerified(value: Boolean) = prefs.edit().putBoolean("connection_verified", value).apply()
    fun connectionVerified(): Boolean = prefs.getBoolean("connection_verified", false)

    fun clearAll() = prefs.edit().clear().apply()
}
