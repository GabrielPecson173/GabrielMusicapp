package com.example.ytmusicapp.domain.usecase

import com.example.ytmusicapp.data.repository.SearchRepository
import com.example.ytmusicapp.domain.model.Track
import javax.inject.Inject

class SearchMusicUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(query: String): List<Track> =
        searchRepository.search(query)
}