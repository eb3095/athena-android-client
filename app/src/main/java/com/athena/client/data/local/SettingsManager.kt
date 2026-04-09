package com.athena.client.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "athena_settings"
        private const val KEY_USE_STREAMING_MODE = "use_streaming_mode"
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _useStreamingMode = MutableStateFlow(
        prefs.getBoolean(KEY_USE_STREAMING_MODE, true)
    )
    val useStreamingMode: StateFlow<Boolean> = _useStreamingMode.asStateFlow()
    
    fun setUseStreamingMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_STREAMING_MODE, enabled).apply()
        _useStreamingMode.value = enabled
    }
}
