package com.athena.client.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athena.client.data.ApiClient
import com.athena.client.data.models.PromptRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.UUID

private const val TAG = "MainViewModel"

const val VOICE_NONE = "__none__"

data class ResponseItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val audioBase64: String?,
    val timestamp: Long = System.currentTimeMillis()
)

data class UiState(
    val responses: List<ResponseItem> = emptyList(),
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val error: String? = null,
    val playingResponseId: String? = null,
    val voices: List<String> = emptyList(),
    val selectedVoice: String? = null,
    val isLoadingVoices: Boolean = false
) {
    val isVoiceEnabled: Boolean
        get() = selectedVoice != VOICE_NONE
}

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun setListening(listening: Boolean) {
        _uiState.update { it.copy(isListening = listening) }
    }

    fun fetchVoices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVoices = true) }
            
            try {
                val api = ApiClient.checkAndSelectApi()
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

    fun sendPrompt(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val api = ApiClient.checkAndSelectApi()
                val currentVoice = _uiState.value.selectedVoice
                val useVoice = currentVoice != VOICE_NONE
                
                Log.d(TAG, "Sending prompt: speaker=$useVoice, voice=$currentVoice")
                
                val response = api.prompt(
                    PromptRequest(
                        prompt = text,
                        speaker = useVoice,
                        speakerVoice = if (useVoice && currentVoice != null) currentVoice else null
                    )
                )
                
                Log.d(TAG, "Response received: text=${response.response.take(50)}..., hasAudio=${response.audio != null}, audioLength=${response.audio?.length ?: 0}")
                
                val responseItem = ResponseItem(
                    text = response.response,
                    audioBase64 = response.audio
                )
                
                _uiState.update { state ->
                    state.copy(
                        responses = state.responses + responseItem,
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Connection error", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Connection error. Please check your network."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Server error. Please try again."
                    )
                }
            }
        }
    }

    fun setPlayingResponse(responseId: String?) {
        _uiState.update { it.copy(playingResponseId = responseId) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
