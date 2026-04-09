package com.athena.client

import android.app.Application
import com.athena.client.data.local.AppDatabase
import com.athena.client.data.local.AudioFileManager
import com.athena.client.data.local.ConversationRepository
import com.athena.client.data.local.SettingsManager

class AthenaApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val audioFileManager by lazy { AudioFileManager(this) }
    val settingsManager by lazy { SettingsManager(this) }
    val conversationRepository by lazy { 
        ConversationRepository(database.conversationDao(), audioFileManager) 
    }
}
