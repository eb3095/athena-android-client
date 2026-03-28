package com.athena.client.data

import android.util.Log
import com.athena.client.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${BuildConfig.AUTH_TOKEN}")
            .addHeader("Content-Type", "application/json")
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val primaryUrl = BuildConfig.API_URL.trimEnd('/') + "/"
    private val fallbackUrl = BuildConfig.API_URL_FALLBACK.takeIf { it.isNotBlank() }?.trimEnd('/')?.plus("/")

    private fun createApi(baseUrl: String): AthenaApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AthenaApi::class.java)
    }

    private val primaryApi: AthenaApi = createApi(primaryUrl)
    private val fallbackApi: AthenaApi? = fallbackUrl?.let { createApi(it) }

    @Volatile
    private var currentApi: AthenaApi = primaryApi
    
    @Volatile
    private var lastHealthCheckTime: Long = 0
    
    private const val HEALTH_CHECK_INTERVAL_MS = 30_000L

    val api: AthenaApi
        get() = currentApi

    suspend fun checkAndSelectApi(): AthenaApi {
        val now = System.currentTimeMillis()
        if (now - lastHealthCheckTime < HEALTH_CHECK_INTERVAL_MS) {
            return currentApi
        }
        
        lastHealthCheckTime = now
        
        try {
            Log.d(TAG, "Health checking primary URL: $primaryUrl")
            primaryApi.health()
            Log.d(TAG, "Primary URL healthy")
            currentApi = primaryApi
            return primaryApi
        } catch (e: Exception) {
            Log.w(TAG, "Primary URL health check failed: ${e.message}")
            if (fallbackApi != null) {
                Log.d(TAG, "Switching to fallback URL: $fallbackUrl")
                currentApi = fallbackApi
                return fallbackApi
            }
            throw e
        }
    }
}
