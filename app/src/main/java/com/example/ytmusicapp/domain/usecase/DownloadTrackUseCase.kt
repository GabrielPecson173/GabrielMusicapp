package com.example.ytmusicapp.domain.usecase

import com.example.ytmusicapp.data.repository.DownloadRepository
import com.example.ytmusicapp.domain.model.Track
import javax.inject.Inject

class DownloadTrackUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(track: Track) =
        downloadRepository.enqueue(track)
}