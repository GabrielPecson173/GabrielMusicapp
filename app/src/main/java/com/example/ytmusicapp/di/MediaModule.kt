package com.example.ytmusicapp.di

import android.content.Context
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.ytmusicapp.playback.CacheDataSourceFactory
import com.example.ytmusicapp.playback.PlaybackCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideSimpleCache(
        @ApplicationContext ctx: Context
    ): SimpleCache = PlaybackCache.getInstance(ctx)

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext ctx: Context
    ): ExoPlayer {
        val cacheDataSourceFactory = CacheDataSourceFactory.build(ctx)
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        return ExoPlayer.Builder(ctx)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
}