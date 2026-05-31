package com.example.ytmusicapp.data.tracker

import com.example.ytmusicapp.data.db.dao.PlayEventDao
import com.example.ytmusicapp.data.db.dao.SearchEventDao
import com.example.ytmusicapp.data.db.dao.ShelfImpressionDao
import com.example.ytmusicapp.data.db.dao.SkipEventDao
import com.example.ytmusicapp.data.db.entity.PlayEventEntity
import com.example.ytmusicapp.data.db.entity.SearchEventEntity
import com.example.ytmusicapp.data.db.entity.ShelfImpressionEntity
import com.example.ytmusicapp.data.db.entity.SkipEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventTracker @Inject constructor(
    private val playEventDao: PlayEventDao,
    private val skipEventDao: SkipEventDao,
    private val searchEventDao: SearchEventDao,
    private val shelfImpressionDao: ShelfImpressionDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun logPlay(
        videoId: String,
        playDurationMs: Long,
        totalDurationMs: Long,
        isCompleted: Boolean
    ) {
        val completionRate = if (totalDurationMs > 0) {
            (playDurationMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
        } else 0f

        android.util.Log.d("EventTracker", "logPlay: $videoId completed=$isCompleted rate=$completionRate")

        scope.launch {
            playEventDao.insert(
                PlayEventEntity(
                    videoId = videoId,
                    timestamp = System.currentTimeMillis(),
                    playbackDurationMs = playDurationMs,
                    totalDurationMs = totalDurationMs,
                    completionRate = completionRate,
                    isCompleted = isCompleted
                )
            )
        }
    }

    fun logSkip(videoId: String, skipPositionMs: Long) {
        val wasUnder30s = skipPositionMs < 30_000L
        scope.launch {
            skipEventDao.insert(
                SkipEventEntity(
                    videoId = videoId,
                    timestamp = System.currentTimeMillis(),
                    skipPositionMs = skipPositionMs,
                    wasUnder30s = wasUnder30s
                )
            )
        }
    }

    fun logSearch(query: String, clickedVideoId: String?) {
        scope.launch {
            searchEventDao.insert(
                SearchEventEntity(
                    queryString = query,
                    timestamp = System.currentTimeMillis(),
                    clickedVideoId = clickedVideoId
                )
            )
        }
    }

    fun logImpression(sectionId: String) {
        scope.launch {
            shelfImpressionDao.insert(
                ShelfImpressionEntity(
                    sectionId = sectionId,
                    timestamp = System.currentTimeMillis(),
                    impressionCount = 1,
                    clickCount = 0
                )
            )
        }
    }

    fun logSectionClick(sectionId: String) {
        scope.launch {
            shelfImpressionDao.insert(
                ShelfImpressionEntity(
                    sectionId = sectionId,
                    timestamp = System.currentTimeMillis(),
                    impressionCount = 0,
                    clickCount = 1
                )
            )
        }
    }

    fun logFavorite(videoId: String) {
        logPlay(
            videoId = videoId,
            playDurationMs = 0L,
            totalDurationMs = 0L,
            isCompleted = false
        )
    }

    fun logNotInterested(videoId: String) {
        scope.launch {
            skipEventDao.insert(
                SkipEventEntity(
                    videoId = videoId,
                    timestamp = System.currentTimeMillis(),
                    skipPositionMs = 0L,
                    wasUnder30s = true
                )
            )
        }
    }
}