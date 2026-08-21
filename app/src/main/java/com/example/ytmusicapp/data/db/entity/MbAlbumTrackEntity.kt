package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A canonical track from a resolved [MbAlbumEntity]'s chosen release, plus whatever playable
 *  YouTube video it was matched to (if any). [matchedVideoId] being null means the track is
 *  known to exist on the real album but no confident playable match was found — cached as such
 *  so that failed lookup isn't retried on every load, and the track is simply omitted from the
 *  rendered album rather than shown as a broken row. */
@Entity(tableName = "mb_album_tracks", indices = [Index("albumId")])
data class MbAlbumTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val albumId: String, // FK -> MbAlbumEntity.id
    val trackNumber: Int,
    val title: String,
    val durationMs: Long,
    val matchedVideoId: String?,
    val matchedThumbnailUrl: String?
)
