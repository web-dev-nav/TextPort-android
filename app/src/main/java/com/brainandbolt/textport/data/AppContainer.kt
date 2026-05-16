package com.brainandbolt.textport.data

import android.content.Context
import com.brainandbolt.textport.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object AppContainer {
    private val json = Json { ignoreUnknownKeys = true }

    private fun retrofit(baseUrl: String): Retrofit {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(client)
            .build()
    }

    fun repository(context: Context): SmsRepository = SmsRepository(
        api = retrofit(resolveBaseUrl(context)).create(ApiService::class.java),
        preferences = SecurePreferences(context)
    )

    private fun resolveBaseUrl(context: Context): String {
        val prefs = SecurePreferences(context)
        val saved = prefs.apiBaseUrl()?.trim().orEmpty()
        return if (saved.isNotBlank()) saved.ensureTrailingSlash() else BuildConfig.API_BASE_URL
    }
}

private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
