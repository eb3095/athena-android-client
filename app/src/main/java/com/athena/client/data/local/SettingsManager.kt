package com.athena.client.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "athena_settings"
        private const val KEY_USE_STREAMING_MODE = "use_streaming_mode"
        private const val KEY_COUNCIL_USER_TRAITS = "council_user_traits"
        private const val KEY_COUNCIL_USER_GOAL = "council_user_goal"
        private const val KEY_DEFAULT_VOICE = "default_voice"
        private const val KEY_DEFAULT_PERSONALITY = "default_personality"
        private const val KEY_DEFAULT_COUNCIL_MEMBERS = "default_council_members"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SERVER_URLS = "server_urls"
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _useStreamingMode = MutableStateFlow(
        prefs.getBoolean(KEY_USE_STREAMING_MODE, true)
    )
    val useStreamingMode: StateFlow<Boolean> = _useStreamingMode.asStateFlow()
    
    private val _councilUserTraits = MutableStateFlow(
        loadTraits()
    )
    val councilUserTraits: StateFlow<List<String>> = _councilUserTraits.asStateFlow()
    
    private val _councilUserGoal = MutableStateFlow(
        prefs.getString(KEY_COUNCIL_USER_GOAL, "") ?: ""
    )
    val councilUserGoal: StateFlow<String> = _councilUserGoal.asStateFlow()
    
    private val _defaultVoice = MutableStateFlow(
        prefs.getString(KEY_DEFAULT_VOICE, null)
    )
    val defaultVoice: StateFlow<String?> = _defaultVoice.asStateFlow()
    
    private val _defaultPersonality = MutableStateFlow(
        prefs.getString(KEY_DEFAULT_PERSONALITY, null)
    )
    val defaultPersonality: StateFlow<String?> = _defaultPersonality.asStateFlow()
    
    private val _defaultCouncilMembers = MutableStateFlow(
        loadDefaultCouncilMembers()
    )
    val defaultCouncilMembers: StateFlow<List<String>> = _defaultCouncilMembers.asStateFlow()
    
    private val _apiKey = MutableStateFlow(
        prefs.getString(KEY_API_KEY, null)
    )
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()
    
    private val _serverUrls = MutableStateFlow(
        prefs.getString(KEY_SERVER_URLS, null)
    )
    val serverUrls: StateFlow<String?> = _serverUrls.asStateFlow()
    
    private fun loadDefaultCouncilMembers(): List<String> {
        val membersJson = prefs.getString(KEY_DEFAULT_COUNCIL_MEMBERS, "[]") ?: "[]"
        return try {
            json.decodeFromString<List<String>>(membersJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun loadTraits(): List<String> {
        val traitsJson = prefs.getString(KEY_COUNCIL_USER_TRAITS, "[]") ?: "[]"
        return try {
            json.decodeFromString<List<String>>(traitsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun setUseStreamingMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_STREAMING_MODE, enabled).apply()
        _useStreamingMode.value = enabled
    }
    
    fun setCouncilUserTraits(traits: List<String>) {
        val traitsJson = json.encodeToString(traits)
        prefs.edit().putString(KEY_COUNCIL_USER_TRAITS, traitsJson).apply()
        _councilUserTraits.value = traits
    }
    
    fun addCouncilUserTrait(trait: String) {
        val newTraits = _councilUserTraits.value + trait
        setCouncilUserTraits(newTraits)
    }
    
    fun removeCouncilUserTrait(trait: String) {
        val newTraits = _councilUserTraits.value.filter { it != trait }
        setCouncilUserTraits(newTraits)
    }
    
    fun setCouncilUserGoal(goal: String) {
        prefs.edit().putString(KEY_COUNCIL_USER_GOAL, goal).apply()
        _councilUserGoal.value = goal
    }
    
    fun setDefaultVoice(voice: String?) {
        if (voice == null) {
            prefs.edit().remove(KEY_DEFAULT_VOICE).apply()
        } else {
            prefs.edit().putString(KEY_DEFAULT_VOICE, voice).apply()
        }
        _defaultVoice.value = voice
    }
    
    fun setDefaultPersonality(personality: String?) {
        if (personality == null) {
            prefs.edit().remove(KEY_DEFAULT_PERSONALITY).apply()
        } else {
            prefs.edit().putString(KEY_DEFAULT_PERSONALITY, personality).apply()
        }
        _defaultPersonality.value = personality
    }
    
    fun setDefaultCouncilMembers(members: List<String>) {
        val membersJson = json.encodeToString(members)
        prefs.edit().putString(KEY_DEFAULT_COUNCIL_MEMBERS, membersJson).apply()
        _defaultCouncilMembers.value = members
    }
    
    fun setApiKey(key: String?) {
        if (key.isNullOrBlank()) {
            prefs.edit().remove(KEY_API_KEY).apply()
            _apiKey.value = null
        } else {
            prefs.edit().putString(KEY_API_KEY, key).apply()
            _apiKey.value = key
        }
    }
    
    fun setServerUrls(urls: String?) {
        if (urls.isNullOrBlank()) {
            prefs.edit().remove(KEY_SERVER_URLS).apply()
            _serverUrls.value = null
        } else {
            prefs.edit().putString(KEY_SERVER_URLS, urls).apply()
            _serverUrls.value = urls
        }
    }
}
