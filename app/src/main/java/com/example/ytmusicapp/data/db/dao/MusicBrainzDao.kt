package com.example.ytmusicapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ytmusicapp.data.db.entity.MbAlbumEntity
import com.example.ytmusicapp.data.db.entity.MbAlbumTrackEntity
import com.example.ytmusicapp.data.db.entity.MbArtistResolutionEntity

@Dao
interface MusicBrainzDao {

    @Query("SELECT * FROM mb_artist_resolution WHERE artistKey = :artistKey LIMIT 1")
    suspend fun getArtistResolution(artistKey: String): MbArtistResolutionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtistResolution(resolution: MbArtistResolutionEntity)

    @Query("SELECT * FROM mb_albums WHERE artistKey = :artistKey")
    suspend fun getAlbumsForArtist(artistKey: String): List<MbAlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbum(album: MbAlbumEntity)

    @Query("SELECT * FROM mb_album_tracks WHERE albumId = :albumId ORDER BY trackNumber ASC")
    suspend fun getTracksForAlbum(albumId: String): List<MbAlbumTrackEntity>

    @Query("DELETE FROM mb_album_tracks WHERE albumId = :albumId")
    suspend fun deleteTracksForAlbum(albumId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<MbAlbumTrackEntity>)
}
