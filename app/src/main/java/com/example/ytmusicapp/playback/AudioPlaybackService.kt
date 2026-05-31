package com.example.ytmusicapp.playback

import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.ytmusicapp.data.extraction.StreamResolvingDataSourceFactory
import com.example.ytmusicapp.data.repository.QueueRepository
import com.example.ytmusicapp.domain.model.Track
import com.example.ytmusicapp.download.DownloadManagerProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TARGET_LUFS        = -14.0f
private const val DEFAULT_TRACK_LUFS = -18.0f
private const val MAX_BOOST_MB       = 1000
private const val MIN_BOOST_MB       = -2000

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class AudioPlaybackService : MediaSessionService() {

    @Inject lateinit var queueRepository: QueueRepository
    @Inject lateinit var downloadManagerProvider: DownloadManagerProvider
    @Inject lateinit var streamResolvingDataSourceFactory: StreamResolvingDataSourceFactory

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentTrackLufs: Float = DEFAULT_TRACK_LUFS

    override fun onCreate() {
        super.onCreate()

        // Download cache as primary — serves completed downloads WITHOUT network.
        // StreamResolvingDataSourceFactory as upstream — only called on cache miss.
        // Cache key factory strips "ytmusic://" scheme → bare videoId matches download key.
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(downloadManagerProvider.cache)
            .setUpstreamDataSourceFactory(streamResolvingDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()

        player.repeatMode = Player.REPEAT_MODE_OFF

        player.addListener(object : Player.Listener {

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                applyNormalization(audioSessionId, currentTrackLufs)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem ?: return
                currentTrackLufs = DEFAULT_TRACK_LUFS
                applyNormalization(player.audioSessionId, currentTrackLufs)
                persistQueue()
                serviceScope.launch {
                    queueRepository.setCurrentItem(mediaItem.mediaId)
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    persistQueue()
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    fun applyTrackLoudness(trackLufs: Float) {
        currentTrackLufs = trackLufs
        applyNormalization(player.audioSessionId, trackLufs)
    }

    private fun applyNormalization(audioSessionId: Int, trackLufs: Float) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return

        val gainMb = ((TARGET_LUFS - trackLufs) * 100f)
            .toInt()
            .coerceIn(MIN_BOOST_MB, MAX_BOOST_MB)

        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null

            if (gainMb > 0) {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(gainMb)
                    enabled = true
                }
            } else {
                val gainDb     = gainMb / 100f
                val linearGain = Math.pow(10.0, (gainDb / 20.0)).toFloat().coerceIn(0f, 1f)
                player.volume  = linearGain
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioNorm", "LoudnessEnhancer unavailable: ${e.message}")
        }
    }

    private fun persistQueue() {
        if (player.mediaItemCount == 0) return
        val currentIndex = player.currentMediaItemIndex
        val tracks = (0 until player.mediaItemCount).map { i ->
            val item = player.getMediaItemAt(i)
            Track(
                id           = item.mediaId,
                title        = item.mediaMetadata.title?.toString() ?: "",
                artist       = item.mediaMetadata.artist?.toString() ?: "",
                album        = "",
                thumbnailUrl = item.mediaMetadata.artworkUri?.toString() ?: "",
                durationMs   = item.mediaMetadata.durationMs ?: 0L,
                isDownloaded = false
            )
        }
        serviceScope.launch(Dispatchers.IO) {
            queueRepository.replaceQueue(tracks, currentIndex)
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}