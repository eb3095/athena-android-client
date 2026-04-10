package com.athena.client.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ConversationType {
    CONVERSATION,
    TRANSCRIPT
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val type: ConversationType,
    val createdAt: Long,
    val updatedAt: Long,
    val personalityKey: String? = null,
    val customPersonality: String? = null
)
