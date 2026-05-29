package com.example.ytmusicapp.domain.usecase

import com.example.ytmusicapp.data.repository.QueueRepository
import com.example.ytmusicapp.domain.model.QueueMode
import com.example.ytmusicapp.domain.model.Track
import javax.inject.Inject

class GenerateQueueUseCase @Inject constructor(
    private val queueRepository: QueueRepository
) {
    suspend operator fun invoke(
        seedTrack: Track,
        mode: QueueMode = QueueMode.AUTOPLAY,
        limit: Int = 20
    ): List<Track> {
        val candidates = queueRepository.generateRelatedTracks(seedTrack, limit = limit * 2)
        val currentQueue = queueRepository.getQueue()
        val currentIds = currentQueue.map { it.id }.toSet()

        val filtered = candidates.filter { it.id !in currentIds }

        return when (mode) {
            QueueMode.AUTOPLAY -> filtered
                .take(limit)

            QueueMode.RADIO -> filtered
                .shuffled()
                .take(limit)
        }
    }
}