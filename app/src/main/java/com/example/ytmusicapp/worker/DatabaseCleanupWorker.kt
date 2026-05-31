package com.example.ytmusicapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ytmusicapp.data.db.AppDatabase
import com.example.ytmusicapp.data.db.dao.DownloadDao
import com.example.ytmusicapp.data.db.dao.PlayEventDao
import com.example.ytmusicapp.data.db.dao.RecommendationCandidateDao
import com.example.ytmusicapp.data.db.dao.SkipEventDao
import com.example.ytmusicapp.data.db.dao.SuppressionDao
import com.example.ytmusicapp.data.db.dao.TrackDao
import com.example.ytmusicapp.data.repository.DownloadRepository
import com.example.ytmusicapp.domain.engine.TasteProfileEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val MAX_CANDIDATE_POOL    = 2_000
private const val MS_30D               = 2_592_000_000L
private const val MS_90D               = 7_776_000_000L

@HiltWorker
class DatabaseCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val trackDao: TrackDao,
    private val downloadDao: DownloadDao,
    private val candidateDao: RecommendationCandidateDao,
    private val suppressionDao: SuppressionDao,
    private val playEventDao: PlayEventDao,
    private val skipEventDao: SkipEventDao,
    private val tasteProfileEngine: TasteProfileEngine,
    private val appDatabase: AppDatabase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            removeOrphanedDownloads()
            pruneExpiredSuppressions()
            pruneStaleEvents()
            pruneStaleCandidates()
            rebuildLongTermTasteProfile()
            vacuumDatabase()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("Tier3Worker", "Cleanup failed", e)
            Result.retry()
        }
    }

    // ── Orphaned downloads ────────────────────────────────────────────────────

    private suspend fun removeOrphanedDownloads() {
        downloadDao.getAllDownloads().collect { downloads ->
            downloads
                .filter {
                    it.status == DownloadRepository.STATUS_CANCELLED ||
                            it.status == DownloadRepository.STATUS_FAILED
                }
                .forEach { downloadDao.delete(it) }
        }
    }

    // ── Suppression cleanup — remove entries older than 30 days ──────────────

    private suspend fun pruneExpiredSuppressions() {
        val cutoff = System.currentTimeMillis()
        suppressionDao.pruneExpired(cutoff)
    }

    // ── Stale telemetry — trim play/skip events beyond 90 days ───────────────

    private suspend fun pruneStaleEvents() {
        val cutoff = System.currentTimeMillis() - MS_90D
        playEventDao.deleteOlderThan(cutoff)
        skipEventDao.deleteOlderThan(cutoff)
    }

    // ── Candidate pool cap — max 2,000 records ────────────────────────────────
    // Eviction priority: no telemetry + not favorited, then lowest popularity score

    private suspend fun pruneStaleCandidates() {
        val nowMs        = System.currentTimeMillis()
        val cutoff = System.currentTimeMillis()
        suppressionDao.pruneExpired(cutoff)
        val recentIds    = playEventDao.getRecent(5000).map { it.videoId }.toSet()
        val favoriteIds  = candidateDao.getFavorites().map { it.videoId }.toSet()

        val all = candidateDao.getTopCandidates(Int.MAX_VALUE)
        if (all.size <= MAX_CANDIDATE_POOL) return

        val evictable = all
            .filter { it.videoId !in recentIds && it.videoId !in favoriteIds }
            .sortedBy { it.popularityScore }

        val evictCount = all.size - MAX_CANDIDATE_POOL
        evictable.take(evictCount).forEach { candidate ->
            candidateDao.deleteByVideoId(candidate.videoId)
        }
    }

    // ── Long-term taste profile rebuild ───────────────────────────────────────

    private suspend fun rebuildLongTermTasteProfile() {
        val nowMs       = System.currentTimeMillis()
        val recentPlays = playEventDao.getRecent(2000)
        val recentSkips = skipEventDao.getSince(nowMs - MS_30D)
        val candidates  = candidateDao.getTopCandidates(MAX_CANDIDATE_POOL)
        val favorites   = candidateDao.getFavorites()

        val artistMap   = candidates.associate { it.videoId to it.artistId }
        val genreMap    = candidates.associate { it.videoId to it.genre }
        val favoriteIds = favorites.map { it.videoId }.toSet()

        // Rebuilds decay-weighted profile — result consumed by HomeFeedRepository on next feedFlow emit
        tasteProfileEngine.buildProfile(
            playEvents       = recentPlays,
            skipEvents       = recentSkips,
            artistMap        = artistMap,
            genreMap         = genreMap,
            favoriteVideoIds = favoriteIds,
            nowMs            = nowMs
        )
    }

    // ── SQLite VACUUM ─────────────────────────────────────────────────────────

    private fun vacuumDatabase() {
        appDatabase.openHelper.writableDatabase.execSQL("VACUUM")
    }
}