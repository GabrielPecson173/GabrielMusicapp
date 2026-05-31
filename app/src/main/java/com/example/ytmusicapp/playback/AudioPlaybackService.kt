package com.example.ytmusicapp.playback

import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.ytmusicapp.data.repository.QueueRepository
import com.example.ytmusicapp.domain.model.Track
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

// Target loudness per EBU R128 / YouTube / Spotify standard
private const val TARGET_LUFS         = -14.0f
private const val DEFAULT_TRACK_LUFS  = -18.0f  // conservative fallback if no metadata
private const val MAX_BOOST_MB        = 1000     // cap at +10 dB to avoid clipping
private const val MIN_BOOST_MB        = -2000    // floor at -20 dB

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class AudioPlaybackService : MediaSessionService() {

    @Inject lateinit var queueRepository: QueueRepository

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // LoudnessEnhancer is per audio session — recreated on session ID change
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // Loudness of the current track in LUFS (populated from stream metadata when available)
    private var currentTrackLufs: Float = DEFAULT_TRACK_LUFS

    override fun onCreate() {
        super.onCreate()

        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            CacheDataSourceFactory.build(this)
        )

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        player.repeatMode = Player.REPEAT_MODE_OFF

        player.addListener(object : Player.Listener {

            // ── Audio session changed — re-attach LoudnessEnhancer ────────────
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                applyNormalization(audioSessionId, currentTrackLufs)
            }

            // ── Track changed — reset LUFS to fallback, re-normalize ─────────
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

    /**
     * Call this when stream metadata provides the track's integrated loudness.
     * PlayerViewModel calls this after GetStreamUrlUseCase resolves.
     *
     * @param trackLufs integrated loudness of the stream in LUFS (e.g. -16.5f)
     */
    fun applyTrackLoudness(trackLufs: Float) {
        currentTrackLufs = trackLufs
        applyNormalization(player.audioSessionId, trackLufs)
    }

    /**
     * Compute gain delta and push to LoudnessEnhancer.
     *
     * gain = (TARGET_LUFS - trackLufs) * 100  → millibels
     * Positive = boost quiet track. Negative = attenuate loud track.
     * LoudnessEnhancer only boosts; attenuation is handled via player volume.
     */
    private fun applyNormalization(audioSessionId: Int, trackLufs: Float) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return

        val gainMb = ((TARGET_LUFS - trackLufs) * 100f)
            .toInt()
            .coerceIn(MIN_BOOST_MB, MAX_BOOST_MB)

        try {
            // Release old enhancer bound to previous session
            loudnessEnhancer?.release()
            loudnessEnhancer = null

            if (gainMb > 0) {
                // Boost: use LoudnessEnhancer
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(gainMb)
                    enabled = true
                }
            } else {
                // Attenuate: scale player volume down instead
                // gainMb is negative; convert back to linear: 10^(dB/20)
                val gainDb    = gainMb / 100f
                val linearGain = Math.pow(10.0, (gainDb / 20.0)).toFloat().coerceIn(0f, 1f)
                player.volume = linearGain
            }
        } catch (e: Exception) {
            // Audio effect unavailable on this device — degrade gracefully
            android.util.Log.w("AudioNorm", "LoudnessEnhancer unavailable: ${e.message}")
        }
    }

    // ── Queue persistence ─────────────────────────────────────────────────────

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