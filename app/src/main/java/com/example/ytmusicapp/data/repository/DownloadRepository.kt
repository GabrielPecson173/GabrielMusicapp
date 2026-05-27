package com.example.ytmusicapp.data.repository

import com.example.ytmusicapp.data.db.dao.DownloadDao
import com.example.ytmusicapp.data.db.entity.DownloadEntity
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.download.DownloadManagerProvider
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadManager
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val downloadManagerProvider: DownloadManagerProvider,
    private val musicRepository: MusicRepository
) {

    private val downloadManager: DownloadManager
        get() = downloadManagerProvider.downloadManager

    fun getAllDownloads(): Flow<List<DownloadEntity>> =
        downloadDao.getAllDownloads()

    fun getDownloadByTrackId(trackId: String): Flow<DownloadEntity?> =
        downloadDao.getDownloadByTrackId(trackId)

    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>> =
        downloadDao.getDownloadsByStatus(status)

    suspend fun enqueue(track: Track) {
        val streamInfo = musicRepository.getStreamInfo(track.id)

        val downloadRequest = DownloadRequest.Builder(
            track.id,
            Uri.parse(streamInfo.streamUrl)
        ).build()

        downloadManager.addDownload(downloadRequest)

        downloadDao.insert(
            DownloadEntity(
                id = track.id,
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                filePath = streamInfo.streamUrl,
                status = STATUS_QUEUED
            )
        )
    }

    suspend fun cancel(trackId: String) {
        downloadManager.removeDownload(trackId)
        downloadDao.updateStatus(trackId, STATUS_CANCELLED)
    }

    suspend fun remove(trackId: String) {
        downloadManager.removeDownload(trackId)
        downloadDao.getDownloadByTrackId(trackId).collect { entity ->
            entity?.let { downloadDao.delete(it) }
        }
    }

    suspend fun updateStatus(trackId: String, status: String) =
        downloadDao.updateStatus(trackId, status)

    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}