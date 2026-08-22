package com.example.ytmusicapp.domain.usecase

import com.example.ytmusicapp.data.repository.QueueRepository
import com.example.ytmusicapp.domain.engine.MAX_SAME_ARTIST
import com.example.ytmusicapp.domain.engine.SmartQueueEngine
import com.example.ytmusicapp.domain.model.QueueMode
import com.example.ytmusicapp.domain.model.Track
import javax.inject.Inject

/**
 * Generates the next batch of queue tracks for Autoplay/Song Radio (seed = a track) and Artist Radio
 * (seed = an artist). Tries [SmartQueueEngine]'s personalized pipeline first; if the local candidate
 * pool is too thin to fill [limit] (cold start / brand-new artist), tops up with
 * [QueueRepository]'s legacy live-search generator — strictly appended after the ranked smart
 * results, never merged in a way that reorders them.
 */
class GenerateQueueUseCase @Inject constructor(
    private val queueRepository: QueueRepository,
    private val smartQueueEngine: SmartQueueEngine
) {
    suspend operator fun invoke(
        seedTrack: Track,
        mode: QueueMode = QueueMode.AUTOPLAY,
        limit: Int = 20
    ): List<Track> {
        val excludeIds = queueRepository.getQueue().map { it.id }.toSet() + seedTrack.id
        val exploratory = mode == QueueMode.RADIO

        val smart = smartQueueEngine.generateForTrack(seedTrack, excludeIds, limit, exploratory)
        if (smart.size >= limit) return smart

        val fallback = queueRepository.generateRelatedTracks(seedTrack, limit = limit * 2)
            .filter { it.id !in excludeIds }
        return smart + fillWithoutArtistRuns(smart, fallback, limit - smart.size)
    }

    suspend fun forArtist(
        artistId: String,
        artistName: String,
        mode: QueueMode = QueueMode.RADIO,
        limit: Int = 30
    ): List<Track> {
        val excludeIds = queueRepository.getQueue().map { it.id }.toSet()
        val exploratory = mode == QueueMode.RADIO

        val smart = smartQueueEngine.generateForArtist(artistId, artistName, excludeIds, limit, exploratory)
        if (smart.size >= limit) return smart

        val fallback = queueRepository.generateRelatedTracksForArtist(artistName, limit = limit * 2)
            .filter { it.id !in excludeIds }
        return smart + fillWithoutArtistRuns(smart, fallback, limit - smart.size)
    }

    /**
     * Appends up to [take] tracks from [fallback] after the already-ranked [rankedHead], never
     * reordering it and never re-running full MMR (which could disturb the smart ranking). The
     * fallback itself has no diversity control (raw shuffled live-search results), so this applies
     * only a light order-preserving guard against runs of the same repeated artist at the tail,
     * reusing MMRDiversifier's own same-artist cap rather than a new threshold.
     */
    private fun fillWithoutArtistRuns(rankedHead: List<Track>, fallback: List<Track>, take: Int): List<Track> {
        if (take <= 0) return emptyList()
        val result = mutableListOf<Track>()
        val recentArtists = ArrayDeque(rankedHead.takeLast(MAX_SAME_ARTIST).map { it.artist })
        for (track in fallback) {
            if (result.size >= take) break
            if (recentArtists.count { it == track.artist } >= MAX_SAME_ARTIST) continue
            result.add(track)
            recentArtists.addLast(track.artist)
            if (recentArtists.size > MAX_SAME_ARTIST) recentArtists.removeFirst()
        }
        return result
    }
}