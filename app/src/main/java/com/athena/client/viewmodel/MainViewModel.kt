package com.athena.client.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.athena.client.AthenaApplication
import com.athena.client.data.ApiClient
import com.athena.client.data.models.PromptRequest
import com.athena.client.data.models.SpeakRequest
import com.athena.client.data.models.StreamJobRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

private const val TAG = "MainViewModel"

const val VOICE_NONE = "none"

enum class ResponseType {
    AI_RESPONSE,
    TRANSCRIPT
}

data class SentenceAudioItem(
    val index: Int,
    val text: String,
    val audio: String?,
    val status: String
)

data class ResponseItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val audioBase64: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val type: ResponseType = ResponseType.AI_RESPONSE,
    val voice: String? = null,
    val isStreaming: Boolean = false,
    val sentenceAudios: List<SentenceAudioItem> = emptyList(),
    val combinedAudio: String? = null,
    val streamingComplete: Boolean = false
)

data class UiState(
    val responses: List<ResponseItem> = emptyList(),
    val isLoading: Boolean = false,
    val isPolling: Boolean = false,
    val currentJobId: String? = null,
    val isListening: Boolean = false,
    val error: String? = null,
    val playingResponseId: String? = null,
    val voices: List<String> = emptyList(),
    val selectedVoice: String? = null,
    val isLoadingVoices: Boolean = false,
    val streamingResponseId: String? = null,
    val useStreamingMode: Boolean = true,
    val apiKey: String? = null,
    val serverUrls: String? = null,
    val isStreamingMuted: Boolean = false
) {
    val isVoiceEnabled: Boolean
        get() = selectedVoice != VOICE_NONE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val INITIAL_POLL_DELAY_MS = 1000L
        private const val MAX_POLL_DELAY_MS = 5000L
        private const val POLL_BACKOFF_MULTIPLIER = 1.5
        private const val MAX_POLL_TIME_MS = 600000L
    }

    private val app = application as AthenaApplication
    private val settingsManager = app.settingsManager
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isConnected: StateFlow<Boolean> = ApiClient.isConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        ApiClient.startHealthChecks()
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
        _uiState.update { it.copy(useStreamingMode = enabled) }
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

    fun sendPrompt(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val api = ApiClient.getApiOrThrow()
                val currentVoice = _uiState.value.selectedVoice
                val useVoice = currentVoice != VOICE_NONE
                val useStreaming = _uiState.value.useStreamingMode
                
                if (useVoice && useStreaming) {
                    Log.d(TAG, "Submitting streaming job: voice=$currentVoice")
                    
                    val submitResponse = api.submitStreamJob(
                        StreamJobRequest(
                            prompt = text,
                            speakerVoice = if (currentVoice != null) currentVoice else null
                        )
                    )
                    
                    Log.d(TAG, "Stream job submitted: jobId=${submitResponse.jobId}")
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            isPolling = true, 
                            currentJobId = submitResponse.jobId
                        ) 
                    }
                    
                    pollForStreamCompletion(submitResponse.jobId)
                } else {
                    Log.d(TAG, "Submitting prompt job: speaker=$useVoice, voice=$currentVoice")
                    
                    val submitResponse = api.submitPromptJob(
                        PromptRequest(
                            prompt = text,
                            speaker = useVoice,
                            speakerVoice = if (useVoice && currentVoice != null) currentVoice else null
                        )
                    )
                    
                    Log.d(TAG, "Job submitted: jobId=${submitResponse.jobId}")
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            isPolling = true, 
                            currentJobId = submitResponse.jobId
                        ) 
                    }
                    
                    pollForCompletion(submitResponse.jobId, text)
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
                Log.e(TAG, "Connection error submitting job", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isPolling = false,
                        error = "Connection error. Please check your network."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit job", e)
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

    fun speakText(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val api = ApiClient.getApiOrThrow()
                val currentVoice = _uiState.value.selectedVoice
                val voice = if (currentVoice != null && currentVoice != VOICE_NONE) {
                    currentVoice
                } else {
                    null
                }
                
                Log.d(TAG, "Submitting speak job: voice=$voice")
                
                val submitResponse = api.submitSpeakJob(SpeakRequest(text = text, speakerVoice = voice))
                
                Log.d(TAG, "Speak job submitted: jobId=${submitResponse.jobId}")
                
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        isPolling = true, 
                        currentJobId = submitResponse.jobId
                    ) 
                }
                
                pollForSpeakCompletion(submitResponse.jobId, text, voice)
                
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
                Log.e(TAG, "Connection error submitting speak job", e)
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

    private suspend fun pollForSpeakCompletion(jobId: String, originalText: String, voice: String?) {
        var currentDelay = INITIAL_POLL_DELAY_MS
        var consecutiveErrors = 0
        val startTime = System.currentTimeMillis()
        
        while (_uiState.value.isPolling && _uiState.value.currentJobId == jobId) {
            if (System.currentTimeMillis() - startTime > MAX_POLL_TIME_MS) {
                Log.e(TAG, "Speak job $jobId timed out after ${MAX_POLL_TIME_MS}ms")
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
                val status = api.getSpeakJobStatus(jobId)
                consecutiveErrors = 0
                
                Log.d(TAG, "Speak job $jobId status: ${status.status}")
                
                when (status.status) {
                    "completed" -> {
                        val audio = status.audio
                        if (audio.isNullOrBlank()) {
                            Log.e(TAG, "Speak job $jobId completed but no audio returned")
                            _uiState.update { 
                                it.copy(
                                    isPolling = false,
                                    currentJobId = null,
                                    error = "Speech generation completed but no audio returned"
                                )
                            }
                            return
                        }
                        val responseItem = ResponseItem(
                            text = originalText,
                            audioBase64 = audio,
                            type = ResponseType.TRANSCRIPT,
                            voice = status.voice
                        )
                        _uiState.update { state ->
                            val newResponses = (state.responses + responseItem).sortedBy { it.timestamp }
                            state.copy(
                                responses = newResponses,
                                isPolling = false,
                                currentJobId = null
                            )
                        }
                        return
                    }
                    "failed" -> {
                        Log.e(TAG, "Speak job $jobId failed: ${status.error}")
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
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
                    Log.e(TAG, "Speak job $jobId not found")
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
                            error = "Request lost. Please try again."
                        )
                    }
                    return
                } else if (e.code() in 400..499) {
                    Log.e(TAG, "Client error polling speak job: ${e.code()}", e)
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
                            error = "Request error (${e.code()}). Please try again."
                        )
                    }
                    return
                } else {
                    consecutiveErrors++
                    Log.e(TAG, "HTTP error polling speak job: ${e.code()} (consecutive: $consecutiveErrors)", e)
                    if (consecutiveErrors >= 5) {
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
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
                Log.e(TAG, "Poll error for speak job $jobId (consecutive: $consecutiveErrors)", e)
                if (consecutiveErrors >= 5) {
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
                            error = "Connection error. Please try again."
                        )
                    }
                    return
                }
            }
        }
    }

    private suspend fun pollForCompletion(jobId: String, originalPrompt: String) {
        var currentDelay = INITIAL_POLL_DELAY_MS
        val startTime = System.currentTimeMillis()
        
        while (_uiState.value.isPolling && _uiState.value.currentJobId == jobId) {
            if (System.currentTimeMillis() - startTime > MAX_POLL_TIME_MS) {
                Log.e(TAG, "Job $jobId timed out after ${MAX_POLL_TIME_MS}ms")
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
                val status = api.getJobStatus(jobId)
                
                Log.d(TAG, "Job $jobId status: ${status.status}")
                
                when (status.status) {
                    "completed" -> {
                        val responseItem = ResponseItem(
                            text = status.response ?: "",
                            audioBase64 = status.audio,
                            voice = status.voice
                        )
                        _uiState.update { state ->
                            val newResponses = (state.responses + responseItem).sortedBy { it.timestamp }
                            state.copy(
                                responses = newResponses,
                                isPolling = false,
                                currentJobId = null
                            )
                        }
                        return
                    }
                    "failed" -> {
                        Log.e(TAG, "Job $jobId failed: ${status.error}")
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
                    Log.e(TAG, "Job $jobId not found")
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
                            error = "Request lost. Please try again."
                        )
                    }
                    return
                } else {
                    Log.e(TAG, "HTTP error polling job: ${e.code()}", e)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Poll error for job $jobId", e)
            }
        }
    }

    private suspend fun pollForStreamCompletion(jobId: String) {
        var currentDelay = INITIAL_POLL_DELAY_MS
        val startTime = System.currentTimeMillis()
        var responseItemId: String? = null
        
        while (_uiState.value.isPolling && _uiState.value.currentJobId == jobId) {
            if (System.currentTimeMillis() - startTime > MAX_POLL_TIME_MS) {
                Log.e(TAG, "Stream job $jobId timed out after ${MAX_POLL_TIME_MS}ms")
                _uiState.update { 
                    it.copy(
                        isPolling = false,
                        currentJobId = null,
                        streamingResponseId = null,
                        isStreamingMuted = false,
                        error = "Request timed out. Please try again."
                    )
                }
                return
            }
            
            delay(currentDelay)
            
            try {
                val api = ApiClient.getApi() ?: continue
                val status = api.getStreamJobStatus(jobId)
                
                Log.d(TAG, "Stream job $jobId status: ${status.status}, sentences: ${status.sentences.size}")
                
                val sentenceAudios = status.sentences.map { s ->
                    SentenceAudioItem(
                        index = s.index,
                        text = s.text,
                        audio = s.audio,
                        status = s.status
                    )
                }
                
                val hasFirstSentenceAudio = sentenceAudios.any { it.index == 0 && it.status == "completed" && it.audio != null }
                
                if (responseItemId == null && status.response != null && hasFirstSentenceAudio) {
                    val newId = UUID.randomUUID().toString()
                    responseItemId = newId
                    
                    val responseItem = ResponseItem(
                        id = newId,
                        text = status.response,
                        audioBase64 = null,
                        voice = status.voice,
                        isStreaming = true,
                        sentenceAudios = sentenceAudios,
                        combinedAudio = null,
                        streamingComplete = false
                    )
                    
                    _uiState.update { state ->
                        val newResponses = (state.responses + responseItem).sortedBy { it.timestamp }
                        state.copy(
                            responses = newResponses,
                            streamingResponseId = newId
                        )
                    }
                } else if (responseItemId != null) {
                    _uiState.update { state ->
                        val updatedResponses = state.responses.map { item ->
                            if (item.id == responseItemId) {
                                item.copy(
                                    sentenceAudios = sentenceAudios,
                                    combinedAudio = status.combinedAudio,
                                    streamingComplete = status.status == "completed",
                                    audioBase64 = status.combinedAudio
                                )
                            } else {
                                item
                            }
                        }
                        state.copy(responses = updatedResponses)
                    }
                }
                
                when (status.status) {
                    "completed" -> {
                        Log.d(TAG, "Stream job $jobId completed")
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
                                streamingResponseId = null,
                                isStreamingMuted = false
                            )
                        }
                        return
                    }
                    "failed" -> {
                        Log.e(TAG, "Stream job $jobId failed: ${status.error}")
                        _uiState.update { 
                            it.copy(
                                isPolling = false,
                                currentJobId = null,
                                streamingResponseId = null,
                                isStreamingMuted = false,
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
                    Log.e(TAG, "Stream job $jobId not found")
                    _uiState.update { 
                        it.copy(
                            isPolling = false,
                            currentJobId = null,
                            streamingResponseId = null,
                            isStreamingMuted = false,
                            error = "Request lost. Please try again."
                        )
                    }
                    return
                } else {
                    Log.e(TAG, "HTTP error polling stream job: ${e.code()}", e)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Poll error for stream job $jobId", e)
            }
        }
    }

    fun cancelCurrentJob() {
        Log.d(TAG, "Cancelling current job: ${_uiState.value.currentJobId}")
        _uiState.update { 
            it.copy(isPolling = false, currentJobId = null) 
        }
    }

    fun setPlayingResponse(responseId: String?) {
        _uiState.update { it.copy(playingResponseId = responseId) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
