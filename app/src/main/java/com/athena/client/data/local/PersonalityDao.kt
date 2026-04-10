package com.athena.client.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalityDao {
    @Query("SELECT * FROM custom_personalities ORDER BY createdAt DESC")
    fun getAllCustomPersonalities(): Flow<List<PersonalityEntity>>

    @Query("SELECT * FROM custom_personalities WHERE `key` = :key")
    suspend fun getByKey(key: String): PersonalityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(personality: PersonalityEntity)

    @Delete
    suspend fun delete(personality: PersonalityEntity)

    @Query("DELETE FROM custom_personalities WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM custom_personalities")
    suspend fun deleteAll()
}
