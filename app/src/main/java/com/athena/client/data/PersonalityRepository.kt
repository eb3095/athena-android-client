package com.athena.client.data

import com.athena.client.data.local.PersonalityDao
import com.athena.client.data.local.PersonalityEntity
import com.athena.client.data.models.Personality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PersonalityRepository(
    private val personalityDao: PersonalityDao
) {
    private val _serverPersonalities = MutableStateFlow<List<Personality>>(emptyList())
    val serverPersonalities: StateFlow<List<Personality>> = _serverPersonalities.asStateFlow()

    val customPersonalities: Flow<List<PersonalityEntity>> = personalityDao.getAllCustomPersonalities()

    suspend fun fetchServerPersonalities(): Result<List<Personality>> {
        return try {
            val api = ApiClient.getApi() ?: return Result.failure(Exception("No server available"))
            val response = api.getPersonalities()
            _serverPersonalities.value = response.personalities
            Result.success(response.personalities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addCustomPersonality(key: String, personality: String) {
        personalityDao.insert(
            PersonalityEntity(
                key = key,
                personality = personality
            )
        )
    }

    suspend fun deleteCustomPersonality(key: String) {
        personalityDao.deleteByKey(key)
    }

    suspend fun deleteAllCustomPersonalities() {
        personalityDao.deleteAll()
    }

    suspend fun getCustomPersonality(key: String): PersonalityEntity? {
        return personalityDao.getByKey(key)
    }

    fun getPersonalityPrompt(
        key: String?,
        customPrompt: String?,
        serverPersonalities: List<Personality>,
        customPersonalities: List<PersonalityEntity>
    ): String? {
        if (!customPrompt.isNullOrBlank()) {
            return customPrompt
        }
        if (key.isNullOrBlank()) {
            return null
        }
        val custom = customPersonalities.find { it.key == key }
        if (custom != null) {
            return custom.personality
        }
        return serverPersonalities.find { it.key == key }?.personality
    }
}
