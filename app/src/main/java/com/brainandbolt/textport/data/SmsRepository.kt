package com.brainandbolt.textport.data

import android.os.Build
import com.brainandbolt.textport.BuildConfig

class SmsRepository(
    private val api: ApiService,
    private val preferences: SecurePreferences
) {
    suspend fun testConnection(): Result<Boolean> = runCatching {
        api.health().ok
    }

    suspend fun requestCode(label: String? = null): Result<String> = runCatching {
        val response = api.requestCode(
            RequestCodeRequest(
                label = label,
                deviceId = Build.ID,
                deviceName = Build.DEVICE,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                deviceBrand = Build.BRAND,
                deviceManufacturer = Build.MANUFACTURER,
                androidVersion = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT.toString(),
                deviceHardware = Build.HARDWARE,
                deviceBoard = Build.BOARD,
                deviceProduct = Build.PRODUCT
            )
        )
        val code = response.code?.trim().orEmpty()
        if (!response.ok || code.isBlank()) error(response.message ?: "Failed to request code")
        code
    }

    suspend fun activate(code: String): Result<Unit> = runCatching {
        val response = api.activate(
            ActivateRequest(
                code = code.trim().uppercase(),
                deviceId = Build.ID,
                deviceName = Build.DEVICE,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                deviceBrand = Build.BRAND,
                deviceManufacturer = Build.MANUFACTURER,
                androidVersion = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT.toString(),
                deviceHardware = Build.HARDWARE,
                deviceBoard = Build.BOARD,
                deviceProduct = Build.PRODUCT,
                appVersion = BuildConfig.VERSION_NAME
            )
        )
        preferences.setToken(response.token)
    }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val response = api.login(AuthRequest(email, password))
        preferences.setToken(response.token)
    }

    suspend fun sync(items: List<SmsSyncItem>): Result<Unit> = runCatching {
        val token = preferences.token() ?: error("Not authenticated")
        api.syncMessages(
            "Bearer $token",
            SyncRequest(
                deviceId = Build.ID,
                deviceName = Build.DEVICE,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                deviceBrand = Build.BRAND,
                deviceManufacturer = Build.MANUFACTURER,
                androidVersion = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT.toString(),
                deviceHardware = Build.HARDWARE,
                deviceBoard = Build.BOARD,
                deviceProduct = Build.PRODUCT,
                appVersion = BuildConfig.VERSION_NAME,
                messages = items
            )
        )
    }

    suspend fun pause(): Result<Unit> = runCatching {
        val token = preferences.token() ?: error("Not authenticated")
        api.pause("Bearer $token")
        preferences.setSyncEnabled(false)
    }

    suspend fun resume(): Result<Unit> = runCatching {
        val token = preferences.token() ?: error("Not authenticated")
        api.resume("Bearer $token")
        preferences.setSyncEnabled(true)
    }

    suspend fun export(): Result<String> = runCatching {
        val token = preferences.token() ?: error("Not authenticated")
        api.export("Bearer $token").downloadUrl
    }

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val token = preferences.token() ?: error("Not authenticated")
        api.deleteAccount("Bearer $token")
        preferences.clearAll()
    }

    fun prefs() = preferences
}
