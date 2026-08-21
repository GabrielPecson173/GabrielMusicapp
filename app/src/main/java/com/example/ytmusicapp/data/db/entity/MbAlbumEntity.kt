package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A MusicBrainz release-group (the canonical "album" concept, independent of any one
 *  physical/digital edition) resolved for an artist, cached so it's fetched from MusicBrainz
 *  at most once. */
@Entity(tableName = "mb_albums", indices = [Index("artistKey")])
data class MbAlbumEntity(
    @PrimaryKey val id: String, // release-group MBID
    val artistKey: String, // normalized, used to query
    val artistName: String, // display-cased, used to render
    val title: String,
    val year: Int?,
    val resolvedAtMs: Long
)
