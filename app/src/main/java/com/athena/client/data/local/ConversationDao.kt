package com.athena.client.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE type = :type ORDER BY updatedAt DESC")
    fun getConversationsByType(type: ConversationType): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE type = :type ORDER BY updatedAt DESC")
    suspend fun getConversationsByTypeSync(type: ConversationType): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM conversations WHERE type = :type")
    suspend fun deleteAllConversationsByType(type: ConversationType)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversationSync(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationTimestamp(id: String, updatedAt: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET personalityKey = :personalityKey, customPersonality = :customPersonality, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationPersonality(id: String, personalityKey: String?, customPersonality: String?, updatedAt: Long)

    @Query("UPDATE conversations SET councilEnabled = :councilEnabled, selectedCouncilMembers = :selectedCouncilMembers, customCouncilMembers = :customCouncilMembers, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationCouncil(id: String, councilEnabled: Boolean, selectedCouncilMembers: String?, customCouncilMembers: String?, updatedAt: Long)

    @Transaction
    suspend fun insertConversationWithMessage(conversation: ConversationEntity, message: MessageEntity) {
        insertConversation(conversation)
        insertMessage(message)
    }

    @Transaction
    suspend fun addMessageToConversation(conversationId: String, message: MessageEntity) {
        insertMessage(message)
        updateConversationTimestamp(conversationId, System.currentTimeMillis())
    }
    
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND pendingJobId IS NOT NULL")
    suspend fun getPendingMessagesForConversation(conversationId: String): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE pendingJobId IS NOT NULL")
    suspend fun getAllPendingMessages(): List<MessageEntity>
    
    @Query("UPDATE messages SET content = :content, audioPath = :audioPath, councilDetails = :councilDetails, pendingJobId = NULL, pendingJobType = NULL WHERE id = :messageId")
    suspend fun completePendingMessage(messageId: String, content: String, audioPath: String?, councilDetails: String?)
    
    @Query("UPDATE messages SET content = :content, councilDetails = :councilDetails WHERE id = :messageId")
    suspend fun updatePendingMessageContent(messageId: String, content: String, councilDetails: String?)
    
    @Query("UPDATE messages SET pendingJobId = NULL, pendingJobType = NULL WHERE id = :messageId")
    suspend fun clearPendingJob(messageId: String)
    
    @Update
    suspend fun updateMessage(message: MessageEntity)
}
