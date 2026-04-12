package com.athena.client.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.athena.client.AthenaApplication
import com.athena.client.data.ApiClient
import com.athena.client.data.local.MessageEntity
import com.athena.client.data.local.MessageRole
import com.athena.client.data.models.CouncilJobRequest
import com.athena.client.data.models.CouncilMemberConfig
import com.athena.client.data.models.CouncilMemberInfo
import com.athena.client.data.models.CouncilMemberResponse
import com.athena.client.data.models.CouncilStreamJobRequest
import com.athena.client.data.models.ConversationMessage
import com.athena.client.data.models.FormatTextRequest
import com.athena.client.data.models.SummarizeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException

private const val TAG = "CouncilViewModel"

data class CouncilUiState(
    val councilId: String? = null,
    val title: String = "Council",
    val messages: List<MessageEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isPolling: Boolean = false,
    val currentJobId: String? = null,
    val error: String? = null,
    val serverCouncilMembers: List<CouncilMemberInfo> = emptyList(),
    val selectedCouncilMembers: Set<String> = emptySet(),
    val customCouncilMembers: List<CouncilMemberConfig> = emptyList(),
    val isLoadingCouncilMembers: Boolean = false,
    val voices: List<String> = emptyList(),
    val selectedVoice: String? = null,
    val isLoadingVoices: Boolean = false,
    val useStreamingMode: Boolean = true,
    val streamingMessageId: String? = null,
    val streamingSentences: List<SentenceAudioItem> = emptyList(),
    val streamingCombinedAudio: String? = null,
    val streamingComplete: Boolean = false,
    val initialLoadComplete: Boolean = false,
    val councilUserTraits: List<String> = emptyList(),
    val councilUserGoal: String = "",
    val showingCouncilDetails: List<CouncilMemberResponse>? = null,
    val isListening: Boolean = false,
    val playingMessageId: String? = null,
    val defaultVoice: String? = null,
    val defaultPersonality: String? = null,
    val defaultCouncilMembers: List<String> = emptyList(),
    val apiKey: String? = null,
    val serverUrls: String? = null,
    val isStreamingMuted: Boolean = false,
    val pendingMessageId: String? = null
)

class CouncilViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val INITIAL_POLL_DELAY_MS = 1000L
        private const val MAX_POLL_DELAY_MS = 5000L
        private const val POLL_BACKOFF_MULTIPLIER = 1.5
        private const val MAX_POLL_TIME_MS = 600000L
        private const val MAX_CONTEXT_MESSAGES = 20
    }

    private val app = application as AthenaApplication
    private val repository = app.conversationRepository
    private val settingsManager = app.settingsManager
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(CouncilUiState())
    val uiState: StateFlow<CouncilUiState> = _uiState.asStateFlow()

    val isConnected: StateFlow<Boolean> = ApiClient.isConnected

    init {
        ApiClient.startHealthChecks()
        viewModelScope.launch {
            settingsManager.useStreamingMode.collect { streaming ->
                _uiState.update { it.copy(useStreamingMode = streaming) }
            }
        }
        viewModelScope.launch {
            settingsManager.councilUserTraits.collect { traits ->
                _uiState.update { it.copy(councilUserTraits = traits) }
            }
        }
        viewModelScope.launch {
            settingsManager.councilUserGoal.collect { goal ->
                _uiState.update { it.copy(councilUserGoal = goal) }
            }
        }
        viewModelScope.launch {
            settingsManager.defaultVoice.collect { voice ->
                if (_uiState.value.selectedVoice == null) {
                    _uiState.update { it.copy(selectedVoice = voice) }
                }
            }
        }
        viewModelScope.launch {
            settingsManager.defaultCouncilMembers.collect { members ->
                if (_uiState.value.selectedCouncilMembers.isEmpty() && members.isNotEmpty()) {
                    _uiState.update { it.copy(selectedCouncilMembers = members.toSet()) }
                }
                _uiState.update { it.copy(defaultCouncilMembers = members) }
            }
        }
        viewModelScope.launch {
            settingsManager.defaultVoice.collect { voice ->
                _uiState.update { it.copy(defaultVoice = voice) }
            }
        }
        viewModelScope.launch {
            settingsManager.defaultPersonality.collect { personality ->
                _uiState.update { it.copy(defaultPersonality = personality) }
            }
        }
        viewModelScope.launch {
            settingsManager.apiKey.collect { key ->
                _uiState.update { it.copy(apiKey = key) }
            }
        }
        viewModelScope.launch {
            settingsManager.serverUrls.collect { urls ->
                _uiState.update { it.copy(serverUrls = urls) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ApiClient.stopHealthChecks()
        _uiState.value.councilId?.let { councilId ->
            kotlinx.coroutines.runBlocking {
                repository.deleteConversationIfEmpty(councilId)
            }
        }
    }

    fun loadCouncil(councilId: String) {
        viewModelScope.launch {
            val council = repository.getConversationById(councilId)
            if (council != null) {
                val selectedMembers = council.selectedCouncilMembers?.let {
                    try { json.decodeFromString<List<String>>(it).toSet() } catch (e: Exception) { emptySet() }
                } ?: emptySet()
                
                val customMembers = council.customCouncilMembers?.let {
                    try { json.decodeFromString<List<CouncilMemberConfig>>(it) } catch (e: Exception) { emptyList() }
                } ?: emptyList()
                
                val defaultVoice = settingsManager.defaultVoice.value
                
                _uiState.update { 
                    it.copy(
                        councilId = councilId,
                        title = council.title,
                        selectedCouncilMembers = selectedMembers,
                        customCouncilMembers = customMembers,
                        selectedVoice = defaultVoice
                    )
                }
                
                if (selectedMembers.isEmpty()) {
                    fetchAndPreselectCouncilMembers(councilId)
                }
                
                val initialMessages = repository.getMessagesForConversation(councilId).first()
                _uiState.update { 
                    it.copy(
                        messages = initialMessages,
                        initialLoadComplete = true
                    )
                }
                
                recoverPendingJobs(councilId)
                
                repository.getMessagesForConversation(councilId).collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
            }
        }
    }
    
    private fun recoverPendingJobs(councilId: String) {
        viewModelScope.launch {
            val pendingMessages = repository.getPendingMessagesForConversation(councilId)
            
            pendingMessages.forEach { message ->
                val jobId = message.pendingJobId ?: return@forEach
                val jobType = message.pendingJobType ?: return@forEach
                
                Log.d(TAG, "Recovering pending council job: $jobId of type $jobType")
                
                _uiState.update { it.copy(isPolling = true, currentJobId = jobId, pendingMessageId = message.id) }
                
                when (jobType) {
                    "council_stream" -> recoverCouncilStreamJob(jobId, councilId, message.id)
                    "council" -> recoverCouncilJob(jobId, councilId, message.id)
                }
            }
        }
    }
    
    private suspend fun recoverCouncilJob(jobId: String, councilId: String, messageId: String) {
        val startTime = System.currentTimeMillis()
        var currentDelay = INITIAL_POLL_DELAY_MS
        
        while (System.currentTimeMillis() - startTime < MAX_POLL_TIME_MS) {
            delay(currentDelay)
            
            try {
                val api = ApiClient.getApi()
                if (api == null) {
                    currentDelay = minOf((currentDelay * POLL_BACKOFF_MULTIPLIER).toLong(), MAX_POLL_DELAY_MS)
                    continue
                }
                
                val status = api.getCouncilJobStatus(jobId)
                
                when (status.status) {
                    "completed" -> {
                        val councilDetailsJson = status.memberResponses?.let { 
                            json.encodeToString(it) 
                        }
                        repository.completePendingMessage(
                            messageId = messageId,
                            content = status.advisorResponse ?: "",
                            audioBase64 = status.audio,
                            councilDetails = councilDetailsJson
                        )
                        _uiState.update { it.copy(isPolling = false, currentJobId = null, pendingMessageId = null) }
                        return
                    }
                    "failed" -> {
                        repository.clearPendingJob(messageId)
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
                                pendingMessageId = null,
                                error = status.error ?: "Job failed"
                            )
                        }
                        return
                    }
                }
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    repository.clearPendingJob(messageId)
                    _uiState.update { 
                        it.copy(
                            isPolling = false, 
                            currentJobId = null,
                            pendingMessageId = null,
                            error = "Job not found"
                        )
                    }
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recovering council job $jobId", e)
            }
            
            currentDelay = minOf((currentDelay * POLL_BACKOFF_MULTIPLIER).toLong(), MAX_POLL_DELAY_MS)
        }
        
        repository.clearPendingJob(messageId)
        _uiState.update { 
            it.copy(
                isPolling = false,
                currentJobId = null,
                pendingMessageId = null,
                error = "Job recovery timed out"
            )
        }
    }
    
    private suspend fun recoverCouncilStreamJob(jobId: String, councilId: String, messageId: String) {
        val startTime = System.currentTimeMillis()
        var currentDelay = INITIAL_POLL_DELAY_MS
        var messageUpdated = false
        
        while (System.currentTimeMillis() - startTime < MAX_POLL_TIME_MS) {
            delay(currentDelay)
            
            try {
                val api = ApiClient.getApi()
                if (api == null) {
                    currentDelay = minOf((currentDelay * POLL_BACKOFF_MULTIPLIER).toLong(), MAX_POLL_DELAY_MS)
                    continue
                }
                
                val status = api.getCouncilStreamJobStatus(jobId)
                
                when (status.status) {
                    "completed" -> {
                        val councilDetailsJson = status.memberResponses?.let { 
                            json.encodeToString(it) 
                        }
                        
                        if (!messageUpdated) {
                            repository.completePendingMessage(
                                messageId = messageId,
                                content = status.advisorResponse ?: "",
                                audioBase64 = status.combinedAudio,
                                councilDetails = councilDetailsJson
                            )
                        } else if (status.combinedAudio != null) {
                            repository.updateMessageAudio(
                                messageId,
                                councilId,
                                audioBase64 = status.combinedAudio,
                                voice = status.voice
                            )
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isPolling = false, 
                                currentJobId = null, 
                                pendingMessageId = null,
                                streamingMessageId = null
                            ) 
                        }
                        return
                    }
                    "failed" -> {
                        if (!messageUpdated) {
                            repository.clearPendingJob(messageId)
                        }
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
                                pendingMessageId = null,
                                streamingMessageId = null,
                                error = status.error ?: "Job failed"
                            )
                        }
                        return
                    }
                    else -> {
                        if (!messageUpdated && status.advisorResponse != null) {
                            val councilDetailsJson = status.memberResponses?.let { 
                                json.encodeToString(it) 
                            }
                            repository.completePendingMessage(
                                messageId = messageId,
                                content = status.advisorResponse,
                                audioBase64 = null,
                                councilDetails = councilDetailsJson
                            )
                            messageUpdated = true
                        }
                    }
                }
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    repository.clearPendingJob(messageId)
                    _uiState.update { 
                        it.copy(
                            isPolling = false, 
                            currentJobId = null,
                            pendingMessageId = null,
                            streamingMessageId = null,
                            error = "Job not found"
                        )
                    }
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recovering council stream job $jobId", e)
            }
            
            currentDelay = minOf((currentDelay * POLL_BACKOFF_MULTIPLIER).toLong(), MAX_POLL_DELAY_MS)
        }
        
        repository.clearPendingJob(messageId)
        _uiState.update { 
            it.copy(
                isPolling = false,
                currentJobId = null,
                pendingMessageId = null,
                streamingMessageId = null,
                error = "Job recovery timed out"
            )
        }
    }

    private suspend fun fetchAndPreselectCouncilMembers(councilId: String) {
        _uiState.update { it.copy(isLoadingCouncilMembers = true) }
        
        try {
            val api = ApiClient.getApi()
            if (api == null) {
                Log.w(TAG, "No healthy server available for fetching council members")
                _uiState.update { it.copy(isLoadingCouncilMembers = false) }
                return
            }
            
            val response = api.getCouncilMembers()
            val members = response.members
            
            val defaultMembers = settingsManager.defaultCouncilMembers.value
            val selectedMembers = if (defaultMembers.isNotEmpty()) {
                defaultMembers.filter { name -> members.any { it.name == name } }.toSet()
                    .ifEmpty { members.map { it.name }.toSet() }
            } else {
                members.map { it.name }.toSet()
            }
            
            _uiState.update { 
                it.copy(
                    serverCouncilMembers = members,
                    selectedCouncilMembers = selectedMembers,
                    isLoadingCouncilMembers = false
                )
            }
            
            val selectedJson = json.encodeToString(selectedMembers.toList())
            repository.updateConversationCouncil(councilId, true, selectedJson, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch council members", e)
            _uiState.update { it.copy(isLoadingCouncilMembers = false) }
        }
    }

    fun fetchCouncilMembers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCouncilMembers = true) }
            
            try {
                val api = ApiClient.getApi()
                if (api == null) {
                    Log.w(TAG, "No healthy server available for fetching council members")
                    _uiState.update { it.copy(isLoadingCouncilMembers = false) }
                    return@launch
                }
                val response = api.getCouncilMembers()
                
                val currentSelected = _uiState.value.selectedCouncilMembers
                val newSelected = if (currentSelected.isEmpty()) {
                    response.members.map { it.name }.toSet()
                } else {
                    currentSelected
                }
                
                _uiState.update { 
                    it.copy(
                        serverCouncilMembers = response.members,
                        selectedCouncilMembers = newSelected,
                        isLoadingCouncilMembers = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch council members", e)
                _uiState.update { it.copy(isLoadingCouncilMembers = false) }
            }
        }
    }

    fun fetchVoices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVoices = true) }
            
            try {
                val api = ApiClient.getApi()
                if (api == null) {
                    _uiState.update { it.copy(isLoadingVoices = false) }
                    return@launch
                }
                
                val response = api.getVoices()
                val defaultVoice = settingsManager.defaultVoice.value
                val currentVoice = _uiState.value.selectedVoice
                
                val selected = when {
                    currentVoice != null && (currentVoice == VOICE_NONE || response.voices.contains(currentVoice)) -> currentVoice
                    defaultVoice != null && response.voices.contains(defaultVoice) -> defaultVoice
                    else -> null
                }
                
                _uiState.update { 
                    it.copy(
                        voices = response.voices,
                        selectedVoice = selected,
                        isLoadingVoices = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch voices", e)
                _uiState.update { it.copy(isLoadingVoices = false) }
            }
        }
    }

    fun toggleCouncilMember(name: String, selected: Boolean) {
        val councilId = _uiState.value.councilId ?: return
        
        val newSelected = if (selected) {
            _uiState.value.selectedCouncilMembers + name
        } else {
            _uiState.value.selectedCouncilMembers - name
        }
        
        _uiState.update { it.copy(selectedCouncilMembers = newSelected) }
        
        viewModelScope.launch {
            val selectedJson = json.encodeToString(newSelected.toList())
            val customJson = json.encodeToString(_uiState.value.customCouncilMembers)
            repository.updateConversationCouncil(councilId, true, selectedJson, customJson)
        }
    }

    fun addCustomCouncilMember(name: String, prompt: String) {
        val councilId = _uiState.value.councilId ?: return
        
        val newMember = CouncilMemberConfig(name = name, prompt = prompt)
        val newCustomMembers = _uiState.value.customCouncilMembers + newMember
        val newSelected = _uiState.value.selectedCouncilMembers + name
        
        _uiState.update { 
            it.copy(
                customCouncilMembers = newCustomMembers,
                selectedCouncilMembers = newSelected
            )
        }
        
        viewModelScope.launch {
            val selectedJson = json.encodeToString(newSelected.toList())
            val customJson = json.encodeToString(newCustomMembers)
            repository.updateConversationCouncil(councilId, true, selectedJson, customJson)
        }
    }

    fun deleteCustomCouncilMember(name: String) {
        val councilId = _uiState.value.councilId ?: return
        
        val newCustomMembers = _uiState.value.customCouncilMembers.filter { it.name != name }
        val newSelected = _uiState.value.selectedCouncilMembers - name
        
        _uiState.update { 
            it.copy(
                customCouncilMembers = newCustomMembers,
                selectedCouncilMembers = newSelected
            )
        }
        
        viewModelScope.launch {
            val selectedJson = json.encodeToString(newSelected.toList())
            val customJson = json.encodeToString(newCustomMembers)
            repository.updateConversationCouncil(councilId, true, selectedJson, customJson)
        }
    }

    fun setSelectedVoice(voice: String?) {
        _uiState.update { it.copy(selectedVoice = voice) }
    }

    fun setListening(listening: Boolean) {
        _uiState.update { it.copy(isListening = listening) }
    }

    fun setPlayingMessage(messageId: String?) {
        _uiState.update { it.copy(playingMessageId = messageId) }
    }

    fun showCouncilDetails(memberResponses: List<CouncilMemberResponse>) {
        _uiState.update { it.copy(showingCouncilDetails = memberResponses) }
    }

    fun hideCouncilDetails() {
        _uiState.update { it.copy(showingCouncilDetails = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun setStreamingMode(enabled: Boolean) {
        settingsManager.setUseStreamingMode(enabled)
    }
    
    fun addCouncilUserTrait(trait: String) {
        settingsManager.addCouncilUserTrait(trait)
    }
    
    fun removeCouncilUserTrait(trait: String) {
        settingsManager.removeCouncilUserTrait(trait)
    }
    
    fun setCouncilUserGoal(goal: String) {
        settingsManager.setCouncilUserGoal(goal)
    }
    
    fun setDefaultVoice(voice: String?) {
        settingsManager.setDefaultVoice(voice)
    }
    
    fun setDefaultPersonality(personality: String?) {
        settingsManager.setDefaultPersonality(personality)
    }
    
    fun setDefaultCouncilMembers(members: List<String>) {
        settingsManager.setDefaultCouncilMembers(members)
    }
    
    fun setApiKey(key: String?) {
        settingsManager.setApiKey(key)
    }
    
    fun setServerUrls(urls: String?) {
        settingsManager.setServerUrls(urls)
    }
    
    fun toggleStreamingMute() {
        _uiState.update { it.copy(isStreamingMuted = !it.isStreamingMuted) }
    }
    
    fun setStreamingMuted(muted: Boolean) {
        _uiState.update { it.copy(isStreamingMuted = muted) }
    }

    fun getCouncilDetailsForMessage(message: MessageEntity): List<CouncilMemberResponse>? {
        return message.councilDetails?.let {
            try {
                json.decodeFromString<List<CouncilMemberResponse>>(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun sendMessage(rawText: String, fromVoice: Boolean = false) {
        if (rawText.isBlank()) return
        val councilId = _uiState.value.councilId ?: return
        
        val selectedMembers = _uiState.value.selectedCouncilMembers.toList()
        if (selectedMembers.isEmpty()) {
            _uiState.update { it.copy(error = "Please select at least one council member") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val api = ApiClient.getApiOrThrow()
                
                val messageText = if (fromVoice) {
                    try {
                        val formatResponse = api.formatText(FormatTextRequest(text = rawText))
                        formatResponse.formattedText
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to format text, using raw", e)
                        rawText
                    }
                } else {
                    rawText
                }

                repository.addMessage(
                    conversationId = councilId,
                    role = MessageRole.USER,
                    content = messageText
                )

                if (_uiState.value.title == "Council") {
                    try {
                        val summaryResponse = api.summarize(SummarizeRequest(text = messageText))
                        repository.updateConversationTitle(councilId, summaryResponse.summary)
                        _uiState.update { it.copy(title = summaryResponse.summary) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to generate title", e)
                    }
                }

                val allMessages = repository.getMessagesForConversationSync(councilId)
                val contextMessages = allMessages
                    .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
                    .takeLast(MAX_CONTEXT_MESSAGES)
                    .map { ConversationMessage(
                        role = if (it.role == MessageRole.USER) "user" else "assistant",
                        content = it.content
                    )}

                val currentVoice = _uiState.value.selectedVoice
                val useVoice = currentVoice != VOICE_NONE
                val useStreaming = _uiState.value.useStreamingMode
                val councilUserTraits = _uiState.value.councilUserTraits
                val councilUserGoal = _uiState.value.councilUserGoal
                val customMembers = _uiState.value.customCouncilMembers
                val serverMemberNames = _uiState.value.serverCouncilMembers.map { it.name }

                val serverMembersToUse = selectedMembers.filter { it in serverMemberNames }.ifEmpty { null }
                val customMembersToUse = customMembers.filter { it.name in selectedMembers }.ifEmpty { null }

                if (useVoice && useStreaming) {
                    Log.d(TAG, "Submitting council stream job")
                    
                    val submitResponse = api.submitCouncilStreamJob(
                        CouncilStreamJobRequest(
                            messages = contextMessages,
                            speakerVoice = if (currentVoice == VOICE_NONE) null else currentVoice,
                            councilMembers = serverMembersToUse,
                            customMembers = customMembersToUse,
                            userTraits = councilUserTraits.ifEmpty { null },
                            userGoal = councilUserGoal.ifBlank { null }
                        )
                    )
                    
                    val pendingMsgId = repository.addPendingMessage(
                        conversationId = councilId,
                        role = MessageRole.ASSISTANT,
                        pendingJobId = submitResponse.jobId,
                        pendingJobType = "council_stream"
                    )
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            isPolling = true, 
                            currentJobId = submitResponse.jobId,
                            pendingMessageId = pendingMsgId
                        ) 
                    }
                    
                    pollForCouncilStreamCompletion(submitResponse.jobId, councilId, pendingMsgId)
                } else {
                    Log.d(TAG, "Submitting council job")
                    
                    val submitResponse = api.submitCouncilJob(
                        CouncilJobRequest(
                            messages = contextMessages,
                            speakerVoice = if (useVoice) currentVoice else null,
                            councilMembers = serverMembersToUse,
                            customMembers = customMembersToUse,
                            userTraits = councilUserTraits.ifEmpty { null },
                            userGoal = councilUserGoal.ifBlank { null }
                        )
                    )
                    
                    val pendingMsgId = repository.addPendingMessage(
                        conversationId = councilId,
                        role = MessageRole.ASSISTANT,
                        pendingJobId = submitResponse.jobId,
                        pendingJobType = "council"
                    )
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            isPolling = true, 
                            currentJobId = submitResponse.jobId,
                            pendingMessageId = pendingMsgId
                        ) 
                    }
                    
                    pollForCouncilCompletion(submitResponse.jobId, councilId, pendingMsgId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit council job", e)
                _uiState.update { 
                    it.copy(isLoading = false, error = "Failed to submit: ${e.message}")
                }
            }
        }
    }

    private suspend fun pollForCouncilCompletion(jobId: String, councilId: String, pendingMessageId: String) {
        var currentDelay = INITIAL_POLL_DELAY_MS
        val startTime = System.currentTimeMillis()

        while (_uiState.value.isPolling && _uiState.value.currentJobId == jobId) {
            if (System.currentTimeMillis() - startTime > MAX_POLL_TIME_MS) {
                repository.clearPendingJob(pendingMessageId)
                _uiState.update { 
                    it.copy(
                        isPolling = false,
                        currentJobId = null,
                        pendingMessageId = null,
                        error = "Request timed out. Please try again."
                    )
                }
                return
            }

            delay(currentDelay)

            try {
                val api = ApiClient.getApi() ?: continue
                val status = api.getCouncilJobStatus(jobId)

                when (status.status) {
                    "completed" -> {
                        val councilDetailsJson = status.memberResponses?.let { 
                            json.encodeToString(it) 
                        }
                        
                        repository.completePendingMessage(
                            messageId = pendingMessageId,
                            content = status.advisorResponse ?: "",
                            audioBase64 = status.audio,
                            councilDetails = councilDetailsJson
                        )
                        
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
                                pendingMessageId = null
                            )
                        }
                        return
                    }
                    "failed" -> {
                        repository.clearPendingJob(pendingMessageId)
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
                                pendingMessageId = null,
                                error = status.error ?: "Job failed"
                            )
                        }
                        return
                    }
                }
                
                currentDelay = (currentDelay * POLL_BACKOFF_MULTIPLIER).toLong()
                    .coerceAtMost(MAX_POLL_DELAY_MS)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    repository.clearPendingJob(pendingMessageId)
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
                            pendingMessageId = null,
                            error = "Request lost. Please try again."
                        )
                    }
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error polling for status", e)
            }
        }
    }

    private suspend fun pollForCouncilStreamCompletion(jobId: String, councilId: String, pendingMessageId: String) {
        var currentDelay = INITIAL_POLL_DELAY_MS
        val startTime = System.currentTimeMillis()
        var messageUpdated = false

        while (_uiState.value.isPolling && _uiState.value.currentJobId == jobId) {
            if (System.currentTimeMillis() - startTime > MAX_POLL_TIME_MS) {
                repository.clearPendingJob(pendingMessageId)
                _uiState.update { 
                    it.copy(
                        isPolling = false,
                        currentJobId = null,
                        pendingMessageId = null,
                        streamingMessageId = null,
                        error = "Request timed out. Please try again."
                    )
                }
                return
            }

            delay(currentDelay)

            try {
                val api = ApiClient.getApi() ?: continue
                val status = api.getCouncilStreamJobStatus(jobId)

                val sentenceAudios = status.sentences.map { s ->
                    SentenceAudioItem(
                        index = s.index,
                        text = s.text,
                        audio = s.audio,
                        status = s.status
                    )
                }

                val hasFirstSentenceAudio = sentenceAudios.any { 
                    it.index == 0 && it.status == "completed" && it.audio != null 
                }

                if (!messageUpdated && status.advisorResponse != null && hasFirstSentenceAudio) {
                    val councilDetailsJson = status.memberResponses?.let { 
                        json.encodeToString(it) 
                    }
                    
                    repository.updatePendingMessageContent(
                        messageId = pendingMessageId,
                        content = status.advisorResponse,
                        councilDetails = councilDetailsJson
                    )
                    messageUpdated = true
                    
                    _uiState.update { 
                        it.copy(
                            streamingMessageId = pendingMessageId,
                            streamingSentences = sentenceAudios,
                            streamingComplete = false
                        )
                    }
                } else if (messageUpdated) {
                    _uiState.update { 
                        it.copy(
                            streamingSentences = sentenceAudios,
                            streamingCombinedAudio = status.combinedAudio,
                            streamingComplete = status.status == "completed"
                        )
                    }
                }

                when (status.status) {
                    "completed" -> {
                        repository.clearPendingJob(pendingMessageId)
                        if (status.combinedAudio != null) {
                            repository.updateMessageAudio(
                                pendingMessageId, 
                                councilId, 
                                audioBase64 = status.combinedAudio, 
                                voice = status.voice
                            )
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
                                pendingMessageId = null
                            )
                        }
                        return
                    }
                    "failed" -> {
                        if (!messageUpdated) {
                            repository.clearPendingJob(pendingMessageId)
                        }
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
                                pendingMessageId = null,
                                streamingMessageId = null,
                                error = status.error ?: "Request failed"
                            )
                        }
                        return
                    }
                }
                
                currentDelay = (currentDelay * POLL_BACKOFF_MULTIPLIER).toLong()
                    .coerceAtMost(MAX_POLL_DELAY_MS)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    repository.clearPendingJob(pendingMessageId)
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
                            pendingMessageId = null,
                            streamingMessageId = null,
                            error = "Request lost. Please try again."
                        )
                    }
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Poll error", e)
            }
        }
    }

    fun clearStreamingState() {
        _uiState.update { 
            it.copy(
                streamingMessageId = null,
                streamingSentences = emptyList(),
                streamingCombinedAudio = null,
                streamingComplete = false,
                isStreamingMuted = false
            )
        }
    }

    fun loadAudio(audioPath: String?): String? {
        return repository.loadAudio(audioPath)
    }
}
