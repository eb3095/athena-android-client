package com.athena.client.data.local

import android.content.Context
import android.util.Base64
import java.io.File

class AudioFileManager(private val context: Context) {
    
    private val audioDir: File
        get() = File(context.filesDir, "audio").also { it.mkdirs() }
    
    fun saveAudio(messageId: String, base64Audio: String): String {
        val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
        val file = File(audioDir, "$messageId.mp3")
        file.writeBytes(audioBytes)
        return file.absolutePath
    }
    
    fun loadAudio(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                val bytes = file.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun deleteAudio(filePath: String?) {
        filePath?.let {
            try {
                File(it).delete()
            } catch (_: Exception) {}
        }
    }
    
    fun deleteAudioForMessage(messageId: String) {
        try {
            File(audioDir, "$messageId.mp3").delete()
        } catch (_: Exception) {}
    }
    
    fun deleteAllAudio() {
        try {
            audioDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}
