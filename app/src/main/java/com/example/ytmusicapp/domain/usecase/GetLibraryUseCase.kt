package com.example.ytmusicapp.domain.usecase

import com.example.ytmusicapp.data.repository.MusicRepository
import com.example.ytmusicapp.domain.model.Track
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLibraryUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    operator fun invoke(): Flow<List<Track>> =
        musicRepository.getAllTracks()
}