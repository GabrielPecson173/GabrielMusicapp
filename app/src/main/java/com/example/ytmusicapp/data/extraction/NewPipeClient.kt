package com.example.ytmusicapp.data.extraction

import com.example.ytmusicapp.domain.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo as NewPipeStreamInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeClient @Inject constructor() {

    suspend fun getStream(videoId: String): StreamInfo =
        withContext(Dispatchers.IO) {
            val url  = "https://www.youtube.com/watch?v=$videoId"
            val info = NewPipeStreamInfo.getInfo(ServiceList.YouTube, url)

            android.util.Log.d("NewPipeClient",
                "audio=${info.audioStreams.size} " +
                        "video=${info.videoStreams.size} " +
                        "videoOnly=${info.videoOnlyStreams.size} " +
                        "errors=${info.errors}"
            )

            val audio = info.audioStreams
                .filter { it.content.isNotBlank() }
                .maxByOrNull { it.averageBitrate }

            if (audio != null) {
                android.util.Log.d("NewPipeClient", "audio bitrate=${audio.averageBitrate}")
                return@withContext StreamInfo(
                    videoId    = videoId,
                    streamUrl  = audio.content,
                    mimeType   = audio.format?.mimeType ?: "audio/mp4",
                    bitrate    = audio.averageBitrate,
                    durationMs = info.duration * 1000L
                )
            }

            val videoOnly = info.videoOnlyStreams
                .filter { it.content.isNotBlank() }
                .maxByOrNull { it.bitrate }

            if (videoOnly != null) {
                android.util.Log.d("NewPipeClient", "videoOnly bitrate=${videoOnly.bitrate}")
                return@withContext StreamInfo(
                    videoId    = videoId,
                    streamUrl  = videoOnly.content,
                    mimeType   = videoOnly.format?.mimeType ?: "video/mp4",
                    bitrate    = videoOnly.bitrate,
                    durationMs = info.duration * 1000L
                )
            }

            val muxed = info.videoStreams
                .filter { it.content.isNotBlank() }
                .minByOrNull { it.bitrate }
                ?: throw Exception("No streams for $videoId errors=${info.errors}")

            android.util.Log.d("NewPipeClient", "muxed bitrate=${muxed.bitrate}")
            StreamInfo(
                videoId    = videoId,
                streamUrl  = muxed.content,
                mimeType   = muxed.format?.mimeType ?: "video/mp4",
                bitrate    = muxed.bitrate,
                durationMs = info.duration * 1000L
            )
        }
}