package com.example.ytmusicapp.download

import com.example.ytmusicapp.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataTagger @Inject constructor() {

    suspend fun tag(file: File, track: Track) =
        withContext(Dispatchers.IO) {
            try {
                writeId3v2Tag(file, track)
            } catch (e: Exception) {
                // Tagging is non-critical — log and continue
                e.printStackTrace()
            }
        }

    private fun writeId3v2Tag(file: File, track: Track) {
        val titleFrame = buildTextFrame("TIT2", track.title)
        val artistFrame = buildTextFrame("TPE1", track.artist)
        val albumFrame = buildTextFrame("TALB", track.album)

        val framesBytes = titleFrame + artistFrame + albumFrame
        val tagSize = framesBytes.size

        val header = buildId3Header(tagSize)
        val tag = header + framesBytes

        val originalBytes = file.readBytes()
        file.writeBytes(tag + originalBytes)
    }

    private fun buildId3Header(size: Int): ByteArray {
        val header = ByteArray(10)
        // ID3 identifier
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        // Version ID3v2.3
        header[3] = 0x03
        header[4] = 0x00
        // Flags
        header[5] = 0x00
        // Syncsafe size
        header[6] = ((size shr 21) and 0x7F).toByte()
        header[7] = ((size shr 14) and 0x7F).toByte()
        header[8] = ((size shr 7) and 0x7F).toByte()
        header[9] = (size and 0x7F).toByte()
        return header
    }

    private fun buildTextFrame(frameId: String, value: String): ByteArray {
        val textBytes = byteArrayOf(0x03) + value.toByteArray(Charsets.UTF_8)
        val size = textBytes.size
        val frame = ByteArray(10 + size)
        // Frame ID
        frameId.forEachIndexed { i, c -> frame[i] = c.code.toByte() }
        // Size
        frame[4] = ((size shr 24) and 0xFF).toByte()
        frame[5] = ((size shr 16) and 0xFF).toByte()
        frame[6] = ((size shr 8) and 0xFF).toByte()
        frame[7] = (size and 0xFF).toByte()
        // Flags
        frame[8] = 0x00
        frame[9] = 0x00
        // Data
        textBytes.copyInto(frame, 10)
        return frame
    }
}