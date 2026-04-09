package com.athena.client.data

import android.util.Log
import com.athena.client.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

data class ServerStatus(
    val url: String,
    val isHealthy: Boolean
)

object ApiClient {
    private const val TAG = "ApiClient"
    private const val HEALTH_CHECK_INTERVAL_MS = 5_000L
    
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

    private val healthCheckClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    private val apiClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val serverUrls: List<String> = BuildConfig.API_SERVERS
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.trimEnd('/') + "/" }

    private val serverApis: Map<String, AthenaApi> = serverUrls.associateWith { url ->
        createApi(url, apiClient)
    }

    private val healthCheckApis: Map<String, AthenaApi> = serverUrls.associateWith { url ->
        createApi(url, healthCheckClient)
    }

    private fun createApi(baseUrl: String, client: OkHttpClient): AthenaApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AthenaApi::class.java)
    }

    private val _serverStatuses = MutableStateFlow<List<ServerStatus>>(
        serverUrls.map { ServerStatus(it, false) }
    )
    val serverStatuses: StateFlow<List<ServerStatus>> = _serverStatuses.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    @Volatile
    private var currentHealthyUrl: String? = null
    
    @Volatile
    private var hasCompletedFirstCheck = false

    private var healthCheckJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startHealthChecks() {
        if (healthCheckJob?.isActive == true) return
        
        Log.d(TAG, "Starting health checks for ${serverUrls.size} servers")
        healthCheckJob = scope.launch {
            while (true) {
                checkAllServers()
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
        }
    }

    fun stopHealthChecks() {
        Log.d(TAG, "Stopping health checks")
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    private suspend fun checkAllServers() {
        val statuses = serverUrls.map { url ->
            val isHealthy = checkServerHealth(url)
            ServerStatus(url, isHealthy)
        }
        
        _serverStatuses.value = statuses
        
        val firstHealthy = statuses.firstOrNull { it.isHealthy }?.url
        currentHealthyUrl = firstHealthy
        
        if (hasCompletedFirstCheck || firstHealthy != null) {
            _isConnected.value = firstHealthy != null
        }
        hasCompletedFirstCheck = true
        
        if (firstHealthy != null) {
            Log.d(TAG, "Active server: $firstHealthy")
        } else {
            Log.w(TAG, "No healthy servers available")
        }
    }

    private suspend fun checkServerHealth(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                healthCheckApis[url]?.health()
                Log.d(TAG, "Health check passed: $url")
                true
            } catch (e: Exception) {
                Log.d(TAG, "Health check failed: $url - ${e.message}")
                false
            }
        }
    }

    fun getApi(): AthenaApi? {
        val url = currentHealthyUrl ?: return null
        return serverApis[url]
    }

    fun getApiOrThrow(): AthenaApi {
        return getApi() ?: throw IllegalStateException("No healthy servers available")
    }
}
