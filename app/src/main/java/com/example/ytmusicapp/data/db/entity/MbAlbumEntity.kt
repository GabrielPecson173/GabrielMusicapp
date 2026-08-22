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
    val resolvedAtMs: Long,
    // Decided once, at resolution time (ArtistRepository.resolveAlbum), between Cover Art
    // Archive's dedicated cover (preferred, when confirmed to exist) and a matched track's
    // incidental video thumbnail (fallback) — persisted rather than re-derived on every cached
    // read, so reading a cached album never needs a network call to make that decision, and a
    // decision made with better logic doesn't silently keep applying old logic to old rows.
    // Blank on rows written before this field existed; ArtistRepository.toAlbum() falls back to
    // the Cover Art Archive URL directly for those until the album is naturally re-resolved.
    val thumbnailUrl: String = ""
)
