package com.example.ytmusicapp.domain.usecase

import com.example.ytmusicapp.data.repository.DownloadRepository
import com.example.ytmusicapp.data.repository.MusicRepository
import com.example.ytmusicapp.domain.model.StreamInfo
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetStreamUrlUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(videoId: String): StreamInfo {
        val download = downloadRepository.getDownloadByTrackId(videoId).first()

        if (download?.status == DownloadRepository.STATUS_COMPLETED) {
            return StreamInfo(
                videoId   = videoId,
                streamUrl = "ytmusic://$videoId",  //noinspection TypoInStringLiteral
                mimeType  = "audio/mp4",
                bitrate   = 0,
                durationMs = 0L
            )
        }

        return musicRepository.getStreamInfo(videoId)
    }
}