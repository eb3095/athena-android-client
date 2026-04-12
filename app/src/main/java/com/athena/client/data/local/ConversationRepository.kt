package com.athena.client.data.local

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConversationRepository(
    private val dao: ConversationDao,
    private val audioFileManager: AudioFileManager
) {

    fun getAllConversations(): Flow<List<ConversationEntity>> = dao.getAllConversations()

    fun getConversationsByType(type: ConversationType): Flow<List<ConversationEntity>> = 
        dao.getConversationsByType(type)

    suspend fun getConversationById(id: String): ConversationEntity? = 
        dao.getConversationById(id)

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> = 
        dao.getMessagesForConversation(conversationId)

    suspend fun getMessagesForConversationSync(conversationId: String): List<MessageEntity> = 
        dao.getMessagesForConversationSync(conversationId)

    suspend fun createConversation(
        type: ConversationType,
        title: String = when (type) {
            ConversationType.CONVERSATION -> "New conversation"
            ConversationType.TRANSCRIPT -> "Transcript"
            ConversationType.COUNCIL -> "Council"
        }
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            id = id,
            title = title,
            type = type,
            createdAt = now,
            updatedAt = now,
            councilEnabled = type == ConversationType.COUNCIL
        )
        dao.insertConversation(conversation)
        return id
    }

    suspend fun updateConversationTitle(conversationId: String, title: String) {
        dao.updateConversationTitle(conversationId, title, System.currentTimeMillis())
    }

    suspend fun addMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        audioBase64: String? = null,
        voice: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val audioPath = audioBase64?.let { audioFileManager.saveAudio(id, it) }
        val message = MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            audioPath = audioPath,
            voice = voice,
            timestamp = System.currentTimeMillis()
        )
        dao.addMessageToConversation(conversationId, message)
        return id
    }

    suspend fun updateMessageAudio(messageId: String, conversationId: String, audioBase64: String, voice: String?) {
        val messages = dao.getMessagesForConversationSync(conversationId)
        val message = messages.find { it.id == messageId }
        if (message != null) {
            message.audioPath?.let { audioFileManager.deleteAudio(it) }
            val audioPath = audioFileManager.saveAudio(messageId, audioBase64)
            dao.insertMessage(message.copy(audioPath = audioPath, voice = voice))
        }
    }

    fun loadAudio(audioPath: String?): String? {
        return audioPath?.let { audioFileManager.loadAudio(it) }
    }

    suspend fun deleteConversation(conversationId: String) {
        val messages = dao.getMessagesForConversationSync(conversationId)
        messages.forEach { audioFileManager.deleteAudio(it.audioPath) }
        dao.deleteConversationById(conversationId)
    }

    suspend fun deleteConversationIfEmpty(conversationId: String): Boolean {
        val messages = dao.getMessagesForConversationSync(conversationId)
        return if (messages.isEmpty()) {
            dao.deleteConversationById(conversationId)
            true
        } else {
            false
        }
    }

    suspend fun deleteAllConversations() {
        audioFileManager.deleteAllAudio()
        dao.deleteAllConversations()
    }

    suspend fun deleteAllConversationsByType(type: ConversationType) {
        val conversations = dao.getConversationsByTypeSync(type)
        conversations.forEach { conversation ->
            val messages = dao.getMessagesForConversationSync(conversation.id)
            messages.forEach { audioFileManager.deleteAudio(it.audioPath) }
        }
        dao.deleteAllConversationsByType(type)
    }

    suspend fun updateConversationPersonality(
        conversationId: String,
        personalityKey: String?,
        customPersonality: String?
    ) {
        dao.updateConversationPersonality(
            conversationId,
            personalityKey,
            customPersonality,
            System.currentTimeMillis()
        )
    }

    suspend fun updateConversationCouncil(
        conversationId: String,
        councilEnabled: Boolean,
        selectedCouncilMembers: String?,
        customCouncilMembers: String?
    ) {
        dao.updateConversationCouncil(
            conversationId,
            councilEnabled,
            selectedCouncilMembers,
            customCouncilMembers,
            System.currentTimeMillis()
        )
    }

    suspend fun addMessageWithCouncilDetails(
        conversationId: String,
        role: MessageRole,
        content: String,
        audioBase64: String? = null,
        voice: String? = null,
        councilDetails: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val audioPath = audioBase64?.let { audioFileManager.saveAudio(id, it) }
        val message = MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role,
            content = content,
            audioPath = audioPath,
            voice = voice,
            timestamp = System.currentTimeMillis(),
            councilDetails = councilDetails
        )
        dao.addMessageToConversation(conversationId, message)
        return id
    }
    
    suspend fun addPendingMessage(
        conversationId: String,
        role: MessageRole,
        pendingJobId: String,
        pendingJobType: String,
        placeholderContent: String = "..."
    ): String {
        val id = UUID.randomUUID().toString()
        val message = MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role,
            content = placeholderContent,
            timestamp = System.currentTimeMillis(),
            pendingJobId = pendingJobId,
            pendingJobType = pendingJobType
        )
        dao.addMessageToConversation(conversationId, message)
        return id
    }
    
    suspend fun getPendingMessagesForConversation(conversationId: String): List<MessageEntity> =
        dao.getPendingMessagesForConversation(conversationId)
    
    suspend fun getAllPendingMessages(): List<MessageEntity> =
        dao.getAllPendingMessages()
    
    suspend fun completePendingMessage(
        messageId: String,
        content: String,
        audioBase64: String? = null,
        councilDetails: String? = null
    ) {
        val audioPath = audioBase64?.let { audioFileManager.saveAudio(messageId, it) }
        dao.completePendingMessage(messageId, content, audioPath, councilDetails)
    }
    
    suspend fun updatePendingMessageContent(
        messageId: String,
        content: String,
        councilDetails: String? = null
    ) {
        dao.updatePendingMessageContent(messageId, content, councilDetails)
    }
    
    suspend fun clearPendingJob(messageId: String) {
        dao.clearPendingJob(messageId)
    }
    
    suspend fun updateMessage(message: MessageEntity) {
        dao.updateMessage(message)
    }
}
