package com.athena.client.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.athena.client.AthenaApplication
import com.athena.client.data.ApiClient
import com.athena.client.data.local.ConversationType
import com.athena.client.data.local.MessageEntity
import com.athena.client.data.local.MessageRole
import com.athena.client.data.models.ConversationJobRequest
import com.athena.client.data.models.ConversationMessage
import com.athena.client.data.models.ConversationStreamJobRequest
import com.athena.client.data.models.FormatTextRequest
import com.athena.client.data.models.SummarizeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

private const val TAG = "ConversationViewModel"

data class ConversationUiState(
    val conversationId: String? = null,
    val title: String = "New conversation",
    val messages: List<MessageEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isPolling: Boolean = false,
    val currentJobId: String? = null,
    val isListening: Boolean = false,
    val error: String? = null,
    val playingMessageId: String? = null,
    val voices: List<String> = emptyList(),
    val selectedVoice: String? = null,
    val isLoadingVoices: Boolean = false,
    val useStreamingMode: Boolean = true,
    val streamingMessageId: String? = null,
    val streamingSentences: List<SentenceAudioItem> = emptyList(),
    val streamingCombinedAudio: String? = null,
    val streamingComplete: Boolean = false,
    val initialLoadComplete: Boolean = false
) {
    val isVoiceEnabled: Boolean
        get() = selectedVoice != VOICE_NONE
}

class ConversationViewModel(application: Application) : AndroidViewModel(application) {

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

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    val isConnected: StateFlow<Boolean> = ApiClient.isConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        ApiClient.startHealthChecks()
        viewModelScope.launch {
            settingsManager.useStreamingMode.collect { enabled ->
                _uiState.update { it.copy(useStreamingMode = enabled) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ApiClient.stopHealthChecks()
        _uiState.value.conversationId?.let { conversationId ->
            kotlinx.coroutines.runBlocking {
                repository.deleteConversationIfEmpty(conversationId)
            }
        }
    }

    fun loadAudio(audioPath: String?): String? {
        return repository.loadAudio(audioPath)
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            val conversation = repository.getConversationById(conversationId)
            if (conversation != null) {
                _uiState.update { 
                    it.copy(
                        conversationId = conversationId,
                        title = conversation.title
                    )
                }
                
                val initialMessages = repository.getMessagesForConversation(conversationId).first()
                _uiState.update { 
                    it.copy(
                        messages = initialMessages,
                        initialLoadComplete = true
                    )
                }
                
                repository.getMessagesForConversation(conversationId).collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
            }
        }
    }

    suspend fun createConversation(): String {
        return repository.createConversation(ConversationType.CONVERSATION)
    }

    fun setListening(listening: Boolean) {
        _uiState.update { it.copy(isListening = listening) }
    }

    fun fetchVoices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVoices = true) }
            
            try {
                val api = ApiClient.getApi()
                if (api == null) {
                    Log.w(TAG, "No healthy server available for fetching voices")
                    _uiState.update { it.copy(isLoadingVoices = false) }
                    return@launch
                }
                val response = api.getVoices()
                _uiState.update { it.copy(voices = response.voices, isLoadingVoices = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch voices", e)
                _uiState.update { it.copy(isLoadingVoices = false) }
            }
        }
    }

    fun setSelectedVoice(voice: String?) {
        _uiState.update { it.copy(selectedVoice = voice) }
    }

    fun setStreamingMode(enabled: Boolean) {
        settingsManager.setUseStreamingMode(enabled)
    }

    fun sendMessage(rawText: String) {
        if (rawText.isBlank()) return
        val conversationId = _uiState.value.conversationId ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val api = ApiClient.getApiOrThrow()
                
                val formattedText = try {
                    val formatResponse = api.formatText(FormatTextRequest(text = rawText))
                    formatResponse.formattedText
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to format text, using raw", e)
                    rawText
                }
                
                repository.addMessage(
                    conversationId = conversationId,
                    role = MessageRole.USER,
                    content = formattedText
                )
                
                if (_uiState.value.title == "New conversation") {
                    try {
                        val summaryResponse = api.summarize(SummarizeRequest(text = formattedText))
                        repository.updateConversationTitle(conversationId, summaryResponse.summary)
                        _uiState.update { it.copy(title = summaryResponse.summary) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to generate title", e)
                    }
                }
                
                val allMessages = repository.getMessagesForConversationSync(conversationId)
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
                
                if (useVoice && useStreaming) {
                    Log.d(TAG, "Submitting conversation stream job")
                    
                    val submitResponse = api.submitConversationStreamJob(
                        ConversationStreamJobRequest(
                            messages = contextMessages,
                            speakerVoice = if (currentVoice != null) currentVoice else null
                        )
                    )
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            isPolling = true, 
                            currentJobId = submitResponse.jobId
                        ) 
                    }
                    
                    pollForConversationStreamCompletion(submitResponse.jobId, conversationId)
                } else {
                    Log.d(TAG, "Submitting conversation job: speaker=$useVoice")
                    
                    val submitResponse = api.submitConversationJob(
                        ConversationJobRequest(
                            messages = contextMessages,
                            speaker = useVoice,
                            speakerVoice = if (useVoice && currentVoice != null) currentVoice else null
                        )
                    )
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            isPolling = true, 
                            currentJobId = submitResponse.jobId
                        ) 
                    }
                    
                    pollForConversationCompletion(submitResponse.jobId, conversationId)
                }
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                Log.w(TAG, "No healthy server available")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isPolling = false,
                        error = "No server available. Please wait for connection."
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Connection error", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isPolling = false,
                        error = "Connection error. Please check your network."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isPolling = false,
                        error = "Failed to send message. Please try again."
                    )
                }
            }
        }
    }

    private suspend fun pollForConversationCompletion(jobId: String, conversationId: String) {
        var currentDelay = INITIAL_POLL_DELAY_MS
        val startTime = System.currentTimeMillis()
        
        while (_uiState.value.isPolling && _uiState.value.currentJobId == jobId) {
            if (System.currentTimeMillis() - startTime > MAX_POLL_TIME_MS) {
                _uiState.update { 
                    it.copy(
                        isPolling = false,
                        currentJobId = null,
                        error = "Request timed out. Please try again."
                    )
                }
                return
            }
            
            delay(currentDelay)
            
            try {
                val api = ApiClient.getApi() ?: continue
                val status = api.getConversationJobStatus(jobId)
                
                when (status.status) {
                    "completed" -> {
                        repository.addMessage(
                            conversationId = conversationId,
                            role = MessageRole.ASSISTANT,
                            content = status.response ?: "",
                            audioBase64 = status.audio,
                            voice = status.voice
                        )
                        
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null
                            )
                        }
                        return
                    }
                    "failed" -> {
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
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
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
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

    private suspend fun pollForConversationStreamCompletion(jobId: String, conversationId: String) {
        var currentDelay = INITIAL_POLL_DELAY_MS
        val startTime = System.currentTimeMillis()
        var messageCreated = false
        var assistantMessageId: String? = null
        
        while (_uiState.value.isPolling && _uiState.value.currentJobId == jobId) {
            if (System.currentTimeMillis() - startTime > MAX_POLL_TIME_MS) {
                _uiState.update { 
                    it.copy(
                        isPolling = false,
                        currentJobId = null,
                        streamingMessageId = null,
                        error = "Request timed out. Please try again."
                    )
                }
                return
            }
            
            delay(currentDelay)
            
            try {
                val api = ApiClient.getApi() ?: continue
                val status = api.getConversationStreamJobStatus(jobId)
                
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
                
                if (!messageCreated && status.response != null && hasFirstSentenceAudio) {
                    assistantMessageId = repository.addMessage(
                        conversationId = conversationId,
                        role = MessageRole.ASSISTANT,
                        content = status.response,
                        voice = status.voice
                    )
                    messageCreated = true
                    
                    _uiState.update { 
                        it.copy(
                            streamingMessageId = assistantMessageId,
                            streamingSentences = sentenceAudios,
                            streamingComplete = false
                        )
                    }
                } else if (messageCreated) {
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
                        if (assistantMessageId != null && status.combinedAudio != null) {
                            repository.updateMessageAudio(
                                assistantMessageId, 
                                conversationId, 
                                audioBase64 = status.combinedAudio, 
                                voice = status.voice
                            )
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
                                streamingMessageId = null,
                                streamingSentences = emptyList(),
                                streamingCombinedAudio = null,
                                streamingComplete = false
                            )
                        }
                        return
                    }
                    "failed" -> {
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
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
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
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

    fun setPlayingMessage(messageId: String?) {
        _uiState.update { it.copy(playingMessageId = messageId) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
