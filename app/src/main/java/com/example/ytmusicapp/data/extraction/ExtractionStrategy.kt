package com.example.ytmusicapp.data.extraction

import com.example.ytmusicapp.domain.model.StreamInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtractionStrategy @Inject constructor(
    private val innerTube: InnerTubeClient,
    private val newPipe: NewPipeClient
) {
    suspend fun getStreamUrl(videoId: String): StreamInfo {
        return try {
            innerTube.getStream(videoId)
        } catch (e: Exception) {
            android.util.Log.e("ExtractionStrategy", "InnerTube failed: ${e.message}")
            try {
                newPipe.getStream(videoId)
            } catch (e2: Exception) {
                android.util.Log.e("ExtractionStrategy", "NewPipe failed: ${e2.message}")
                throw e2
            }
        }
    }
}