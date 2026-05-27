package com.example.ytmusicapp.data.extraction

import com.example.ytmusicapp.domain.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo as NewPipeStreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeClient @Inject constructor() {

    suspend fun getStream(videoId: String): StreamInfo =
        withContext(Dispatchers.IO) {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val info = NewPipeStreamInfo.getInfo(ServiceList.YouTube, url)
            android.util.Log.d("NewPipeClient", "audioStreams=${info.audioStreams.size} videoStreams=${info.videoStreams.size} errors=${info.errors}")

            val audioStream = info.audioStreams.maxByOrNull { it.averageBitrate }

            if (audioStream != null) {
                StreamInfo(
                    videoId = videoId,
                    streamUrl = audioStream.content,
                    mimeType = audioStream.format?.mimeType ?: "audio/mp4",
                    bitrate = audioStream.averageBitrate,
                    durationMs = info.duration * 1000L
                )
            } else {
                val videoStream = info.videoStreams.maxByOrNull { it.bitrate }
                    ?: throw Exception("No streams for $videoId")
                StreamInfo(
                    videoId = videoId,
                    streamUrl = videoStream.content,
                    mimeType = videoStream.format?.mimeType ?: "video/mp4",
                    bitrate = videoStream.bitrate,
                    durationMs = info.duration * 1000L
                )
            }
        }
}