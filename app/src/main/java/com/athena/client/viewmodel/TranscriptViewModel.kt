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
import com.athena.client.data.models.FormatTextRequest
import com.athena.client.data.models.SpeakRequest
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

private const val TAG = "TranscriptViewModel"

data class TranscriptUiState(
    val transcriptId: String? = null,
    val title: String = "Transcript",
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
    val initialLoadComplete: Boolean = false,
    val pendingMessageId: String? = null
) {
    val isVoiceEnabled: Boolean
        get() = selectedVoice != VOICE_NONE
}

class TranscriptViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val INITIAL_POLL_DELAY_MS = 1000L
        private const val MAX_POLL_DELAY_MS = 5000L
        private const val POLL_BACKOFF_MULTIPLIER = 1.5
        private const val MAX_POLL_TIME_MS = 600000L
    }

    private val repository = (application as AthenaApplication).conversationRepository

    private val _uiState = MutableStateFlow(TranscriptUiState())
    val uiState: StateFlow<TranscriptUiState> = _uiState.asStateFlow()

    val isConnected: StateFlow<Boolean> = ApiClient.isConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        ApiClient.startHealthChecks()
    }

    override fun onCleared() {
        super.onCleared()
        ApiClient.stopHealthChecks()
        _uiState.value.transcriptId?.let { transcriptId ->
            kotlinx.coroutines.runBlocking {
                repository.deleteConversationIfEmpty(transcriptId)
            }
        }
    }

    fun loadAudio(audioPath: String?): String? {
        return repository.loadAudio(audioPath)
    }

    fun loadTranscript(transcriptId: String) {
        viewModelScope.launch {
            val transcript = repository.getConversationById(transcriptId)
            if (transcript != null) {
                _uiState.update { 
                    it.copy(
                        transcriptId = transcriptId,
                        title = transcript.title
                    )
                }
                
                val initialMessages = repository.getMessagesForConversation(transcriptId).first()
                _uiState.update { 
                    it.copy(
                        messages = initialMessages,
                        initialLoadComplete = true
                    )
                }
                
                recoverPendingJobs(transcriptId)
                
                repository.getMessagesForConversation(transcriptId).collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
            }
        }
    }
    
    private fun recoverPendingJobs(transcriptId: String) {
        viewModelScope.launch {
            val pendingMessages = repository.getPendingMessagesForConversation(transcriptId)
            
            pendingMessages.forEach { message ->
                val jobId = message.pendingJobId ?: return@forEach
                val jobType = message.pendingJobType ?: return@forEach
                
                Log.d(TAG, "Recovering pending transcript job: $jobId of type $jobType")
                
                _uiState.update { it.copy(isPolling = true, currentJobId = jobId, pendingMessageId = message.id) }
                
                if (jobType == "speak") {
                    recoverSpeakJob(jobId, transcriptId, message.id, message.content)
                }
            }
        }
    }
    
    private suspend fun recoverSpeakJob(jobId: String, transcriptId: String, messageId: String, originalText: String) {
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
                
                val status = api.getSpeakJobStatus(jobId)
                
                when (status.status) {
                    "completed" -> {
                        val audio = status.audio
                        if (audio.isNullOrBlank()) {
                            repository.clearPendingJob(messageId)
                            _uiState.update { 
                                it.copy(
                                    isPolling = false,
                                    currentJobId = null,
                                    pendingMessageId = null,
                                    error = "Speech generation completed but no audio returned"
                                )
                            }
                            return
                        }
                        
                        repository.completePendingMessage(
                            messageId = messageId,
                            content = originalText,
                            audioBase64 = audio
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
                Log.e(TAG, "Error recovering speak job $jobId", e)
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

    suspend fun createTranscript(): String {
        return repository.createConversation(ConversationType.TRANSCRIPT, "Transcript")
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

    fun speakText(rawText: String) {
        if (rawText.isBlank()) return
        val transcriptId = _uiState.value.transcriptId ?: return
        
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
                
                val currentVoice = _uiState.value.selectedVoice
                val voice = if (currentVoice != null && currentVoice != VOICE_NONE) {
                    currentVoice
                } else {
                    null
                }
                
                Log.d(TAG, "Submitting speak job: voice=$voice")
                
                val submitResponse = api.submitSpeakJob(SpeakRequest(text = formattedText, speakerVoice = voice))
                
                val pendingMsgId = repository.addPendingMessage(
                    conversationId = transcriptId,
                    role = MessageRole.TRANSCRIPT,
                    pendingJobId = submitResponse.jobId,
                    pendingJobType = "speak",
                    placeholderContent = formattedText
                )
                
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        isPolling = true, 
                        currentJobId = submitResponse.jobId,
                        pendingMessageId = pendingMsgId
                    ) 
                }
                
                pollForSpeakCompletion(submitResponse.jobId, transcriptId, formattedText, voice, pendingMsgId)
                
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
                Log.e(TAG, "Failed to submit speak job", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isPolling = false,
                        error = "Failed to submit request. Please try again."
                    )
                }
            }
        }
    }

    private suspend fun pollForSpeakCompletion(
        jobId: String, 
        transcriptId: String, 
        originalText: String, 
        voice: String?,
        pendingMessageId: String
    ) {
        var currentDelay = INITIAL_POLL_DELAY_MS
        var consecutiveErrors = 0
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
                val status = api.getSpeakJobStatus(jobId)
                consecutiveErrors = 0
                
                when (status.status) {
                    "completed" -> {
                        val audio = status.audio
                        if (audio.isNullOrBlank()) {
                            repository.clearPendingJob(pendingMessageId)
                            _uiState.update { 
                                it.copy(
                                    isPolling = false,
                                    currentJobId = null,
                                    pendingMessageId = null,
                                    error = "Speech generation completed but no audio returned"
                                )
                            }
                            return
                        }
                        
                        repository.completePendingMessage(
                            messageId = pendingMessageId,
                            content = originalText,
                            audioBase64 = audio
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
                                error = status.error ?: "Speech generation failed"
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
                } else if (e.code() in 400..499) {
                    repository.clearPendingJob(pendingMessageId)
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
                            pendingMessageId = null,
                            error = "Request error (${e.code()}). Please try again."
                        )
                    }
                    return
                } else {
                    consecutiveErrors++
                    if (consecutiveErrors >= 5) {
                        repository.clearPendingJob(pendingMessageId)
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
                                pendingMessageId = null,
                                error = "Server error. Please try again later."
                            )
                        }
                        return
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveErrors++
                Log.e(TAG, "Poll error (consecutive: $consecutiveErrors)", e)
                if (consecutiveErrors >= 5) {
                    repository.clearPendingJob(pendingMessageId)
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
                            pendingMessageId = null,
                            error = "Connection error. Please try again."
                        )
                    }
                    return
                }
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
