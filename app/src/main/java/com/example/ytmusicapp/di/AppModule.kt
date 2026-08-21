package com.example.ytmusicapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.ytmusicapp.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Database ──────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context
    ): AppDatabase = Room.databaseBuilder(
        ctx,
        AppDatabase::class.java,
        "ytmusic.db"
    )
        .addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7
        )
        .build()

    // ── Core DAOs ─────────────────────────────────────────────────────────────

    @Provides @Singleton fun provideTrackDao(db: AppDatabase) = db.trackDao()
    @Provides @Singleton fun providePlaylistDao(db: AppDatabase) = db.playlistDao()
    @Provides @Singleton fun provideDownloadDao(db: AppDatabase) = db.downloadDao()
    @Provides @Singleton fun provideQueueDao(db: AppDatabase) = db.queueDao()

    // ── Recommendation DAOs ───────────────────────────────────────────────────

    @Provides @Singleton fun providePlayEventDao(db: AppDatabase) = db.playEventDao()
    @Provides @Singleton fun provideSkipEventDao(db: AppDatabase) = db.skipEventDao()
    @Provides @Singleton fun provideSearchEventDao(db: AppDatabase) = db.searchEventDao()
    @Provides @Singleton fun provideRecommendationCandidateDao(db: AppDatabase) = db.recommendationCandidateDao()
    @Provides @Singleton fun provideHomeFeedSectionDao(db: AppDatabase) = db.homeFeedSectionDao()
    @Provides @Singleton fun provideTrackEmbeddingDao(db: AppDatabase) = db.trackEmbeddingDao()
    @Provides @Singleton fun provideSuppressionDao(db: AppDatabase) = db.suppressionDao()
    @Provides @Singleton fun provideSessionPairDao(db: AppDatabase) = db.sessionPairDao()
    @Provides @Singleton fun provideShelfImpressionDao(db: AppDatabase) = db.shelfImpressionDao()
    @Provides @Singleton fun providePlaybackPositionDao(db: AppDatabase) = db.playbackPositionDao()
    @Provides @Singleton fun provideMusicBrainzDao(db: AppDatabase) = db.musicBrainzDao()

    // ── Other ─────────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext ctx: Context
    ): DataStore<Preferences> = ctx.dataStore

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        // OkHttp's defaults (10s connect/read/write) are tuned for a decent connection — on a
        // genuinely slow link (e.g. ~512kbps) a normal, still-progressing response can take
        // longer than that to finish downloading and would otherwise time out and look
        // indistinguishable from a real failure. Generous but still bounded.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
}