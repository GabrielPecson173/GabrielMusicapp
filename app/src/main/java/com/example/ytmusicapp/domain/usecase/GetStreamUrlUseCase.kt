package com.example.ytmusicapp.domain.usecase

import com.example.ytmusicapp.data.repository.MusicRepository
import com.example.ytmusicapp.domain.model.StreamInfo
import javax.inject.Inject

class GetStreamUrlUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(videoId: String): StreamInfo =
        musicRepository.getStreamInfo(videoId)
}