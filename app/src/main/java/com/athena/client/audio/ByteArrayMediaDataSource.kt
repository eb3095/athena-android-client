package com.athena.client.audio

import android.media.MediaDataSource

class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
    
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        
        val remaining = data.size - position.toInt()
        val bytesToRead = minOf(size, remaining)
        
        System.arraycopy(data, position.toInt(), buffer, offset, bytesToRead)
        return bytesToRead
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() {}
}
