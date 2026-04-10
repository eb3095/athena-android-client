package com.athena.client.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_personalities")
data class PersonalityEntity(
    @PrimaryKey
    val key: String,
    val personality: String,
    val createdAt: Long = System.currentTimeMillis()
)
