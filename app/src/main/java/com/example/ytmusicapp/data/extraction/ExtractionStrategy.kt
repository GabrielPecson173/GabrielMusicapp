package com.example.ytmusicapp.data.extraction

import com.example.ytmusicapp.domain.model.StreamInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtractionStrategy @Inject constructor(
    private val innerTube: InnerTubeClient,
    private val newPipe: NewPipeClient
) {
    suspend fun getStreamUrl(videoId: String): StreamInfo = try {
        innerTube.getStream(videoId)
    } catch (e: Exception) {
        newPipe.getStream(videoId)
    }
}