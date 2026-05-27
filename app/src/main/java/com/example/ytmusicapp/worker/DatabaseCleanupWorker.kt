package com.example.ytmusicapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ytmusicapp.data.db.dao.DownloadDao
import com.example.ytmusicapp.data.db.dao.TrackDao
import com.example.ytmusicapp.data.repository.DownloadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DatabaseCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val trackDao: TrackDao,
    private val downloadDao: DownloadDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            removeOrphanedDownloads()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun removeOrphanedDownloads() {
        downloadDao.getAllDownloads().collect { downloads ->
            downloads
                .filter { it.status == DownloadRepository.STATUS_CANCELLED ||
                        it.status == DownloadRepository.STATUS_FAILED }
                .forEach { downloadDao.delete(it) }
        }
    }
}