package com.athena.client.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.athena.client.AthenaApplication
import com.athena.client.data.ApiClient
import com.athena.client.data.local.ConversationEntity
import com.athena.client.data.local.ConversationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AthenaApplication
    private val repository = app.conversationRepository
    private val settingsManager = app.settingsManager

    val conversations: StateFlow<List<ConversationEntity>> = repository.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val useStreamingMode: StateFlow<Boolean> = settingsManager.useStreamingMode

    private val _showDeleteConfirmation = MutableStateFlow<ConversationEntity?>(null)
    val showDeleteConfirmation: StateFlow<ConversationEntity?> = _showDeleteConfirmation

    private var currentConversationId: String? = null

    init {
        ApiClient.startHealthChecks()
    }

    fun setCurrentConversation(conversationId: String?) {
        val previousId = currentConversationId
        currentConversationId = conversationId
        
        if (previousId != null && previousId != conversationId) {
            viewModelScope.launch {
                repository.deleteConversationIfEmpty(previousId)
            }
        }
    }

    suspend fun createConversation(): String {
        val previousId = currentConversationId
        if (previousId != null) {
            repository.deleteConversationIfEmpty(previousId)
        }
        val newId = repository.createConversation(ConversationType.CONVERSATION)
        currentConversationId = newId
        return newId
    }

    suspend fun createTranscript(): String {
        val previousId = currentConversationId
        if (previousId != null) {
            repository.deleteConversationIfEmpty(previousId)
        }
        val newId = repository.createConversation(ConversationType.TRANSCRIPT, "Transcript")
        currentConversationId = newId
        return newId
    }

    fun setStreamingMode(enabled: Boolean) {
        settingsManager.setUseStreamingMode(enabled)
    }

    fun deleteConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            repository.deleteConversation(conversation.id)
        }
    }

    fun requestDeleteConversation(conversation: ConversationEntity) {
        _showDeleteConfirmation.value = conversation
    }

    fun dismissDeleteConfirmation() {
        _showDeleteConfirmation.value = null
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            repository.deleteAllConversations()
        }
        currentConversationId = null
    }
}
