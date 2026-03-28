package com.athena.client.audio

import android.media.MediaPlayer
import android.util.Base64
import android.util.Log

class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    
    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    fun play(
        base64Audio: String,
        onCompletion: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        stop()
        
        try {
            val audioData = Base64.decode(base64Audio, Base64.DEFAULT)
            
            if (audioData.size < 44) {
                onError("Invalid audio data")
                return
            }
            
            val dataSource = ByteArrayMediaDataSource(audioData)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(dataSource)
                setOnCompletionListener { onCompletion() }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayer", "MediaPlayer error: what=$what, extra=$extra")
                    onError("Playback error")
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to play audio", e)
            onError("Failed to play audio")
        }
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping player", e)
        } finally {
            mediaPlayer = null
        }
    }

    fun release() {
        stop()
    }
}
