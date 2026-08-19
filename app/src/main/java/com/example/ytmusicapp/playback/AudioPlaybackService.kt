package com.example.ytmusicapp.playback

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.ytmusicapp.data.repository.QueueRepository
import com.example.ytmusicapp.domain.model.Track
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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

    companion object {
        const val CMD_GET_AUDIO_SESSION_ID  = "com.example.ytmusicapp.GET_AUDIO_SESSION_ID"
        const val CMD_GET_EQUALIZER_STATE   = "com.example.ytmusicapp.GET_EQUALIZER_STATE"
        const val CMD_SET_EQUALIZER_BAND    = "com.example.ytmusicapp.SET_EQUALIZER_BAND"
        const val CMD_SET_EQUALIZER_ENABLED = "com.example.ytmusicapp.SET_EQUALIZER_ENABLED"

        const val EXTRA_AUDIO_SESSION_ID = "audioSessionId"
        const val EXTRA_EQ_ENABLED       = "eqEnabled"
        const val EXTRA_EQ_NUM_BANDS     = "eqNumBands"
        const val EXTRA_EQ_MIN_LEVEL     = "eqMinLevel"
        const val EXTRA_EQ_MAX_LEVEL     = "eqMaxLevel"
        const val EXTRA_EQ_CENTER_FREQS  = "eqCenterFreqs"
        const val EXTRA_EQ_LEVELS        = "eqLevels"
        const val EXTRA_EQ_BAND          = "eqBand"
        const val EXTRA_EQ_LEVEL         = "eqLevel"
    }

    @Inject lateinit var queueRepository: QueueRepository
    @Inject lateinit var playbackCacheProvider: PlaybackCacheProvider

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentTrackLufs: Float = DEFAULT_TRACK_LUFS

    private var equalizer: Equalizer? = null
    private var eqEnabled = false
    private val eqBandLevels = mutableMapOf<Int, Int>()

    override fun onCreate() {
        super.onCreate()

        // Download cache as primary — serves completed downloads WITHOUT network.
        // Bounded streaming cache underneath — serves/holds anything merely played (see
        // PlaybackCacheProvider), falling back to the network only on a genuine cache miss.
        val cacheDataSourceFactory = playbackCacheProvider.createPlaybackDataSourceFactory()

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
                attachEqualizer(audioSessionId)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem ?: return
                currentTrackLufs = DEFAULT_TRACK_LUFS
                applyNormalization(player.audioSessionId, currentTrackLufs)
                if (equalizer == null) attachEqualizer(player.audioSessionId)
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

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val result = super.onConnect(session, controller)
                    val sessionCommands = result.availableSessionCommands.buildUpon()
                        .add(SessionCommand(CMD_GET_AUDIO_SESSION_ID, Bundle.EMPTY))
                        .add(SessionCommand(CMD_GET_EQUALIZER_STATE, Bundle.EMPTY))
                        .add(SessionCommand(CMD_SET_EQUALIZER_BAND, Bundle.EMPTY))
                        .add(SessionCommand(CMD_SET_EQUALIZER_ENABLED, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.accept(sessionCommands, result.availablePlayerCommands)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CMD_GET_AUDIO_SESSION_ID -> {
                            val resultData = Bundle().apply { putInt(EXTRA_AUDIO_SESSION_ID, player.audioSessionId) }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultData))
                        }
                        CMD_GET_EQUALIZER_STATE -> {
                            if (equalizer == null) attachEqualizer(player.audioSessionId)
                            val eq = equalizer
                                ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                            val numBands = eq.numberOfBands.toInt()
                            val range = eq.bandLevelRange
                            val centerFreqs = IntArray(numBands) { eq.getCenterFreq(it.toShort()) / 1000 }
                            val levels = IntArray(numBands) { eq.getBandLevel(it.toShort()).toInt() }
                            val resultData = Bundle().apply {
                                putBoolean(EXTRA_EQ_ENABLED, eq.enabled)
                                putInt(EXTRA_EQ_NUM_BANDS, numBands)
                                putInt(EXTRA_EQ_MIN_LEVEL, range[0].toInt())
                                putInt(EXTRA_EQ_MAX_LEVEL, range[1].toInt())
                                putIntArray(EXTRA_EQ_CENTER_FREQS, centerFreqs)
                                putIntArray(EXTRA_EQ_LEVELS, levels)
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultData))
                        }
                        CMD_SET_EQUALIZER_BAND -> {
                            val band  = args.getInt(EXTRA_EQ_BAND, -1)
                            val level = args.getInt(EXTRA_EQ_LEVEL, 0)
                            if (band >= 0) {
                                eqBandLevels[band] = level
                                eqEnabled = true
                                try {
                                    equalizer?.setBandLevel(band.toShort(), level.toShort())
                                    equalizer?.enabled = true
                                } catch (e: Exception) {
                                    android.util.Log.w("Equalizer", "setBandLevel failed: ${e.message}")
                                }
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        CMD_SET_EQUALIZER_ENABLED -> {
                            val enabled = args.getBoolean(EXTRA_EQ_ENABLED, true)
                            eqEnabled = enabled
                            try { equalizer?.enabled = enabled } catch (e: Exception) {
                                android.util.Log.w("Equalizer", "setEnabled failed: ${e.message}")
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()
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

    private fun attachEqualizer(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                for ((band, level) in eqBandLevels) {
                    if (band < numberOfBands) setBandLevel(band.toShort(), level.toShort())
                }
                enabled = eqEnabled
            }
        } catch (e: Exception) {
            android.util.Log.w("Equalizer", "Equalizer unavailable: ${e.message}")
            equalizer = null
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
        equalizer?.release()
        equalizer = null
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}