package com.example.ytmusicapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached result of resolving an artist name to a MusicBrainz artist MBID. [artistMbid] being
 *  null is itself a valid cached outcome — "looked it up, MusicBrainz has no confident match" —
 *  so a miss doesn't get re-queried (and re-rate-limited) on every Artist Page visit. */
@Entity(tableName = "mb_artist_resolution")
data class MbArtistResolutionEntity(
    @PrimaryKey val artistKey: String,
    val artistMbid: String?,
    val resolvedAtMs: Long
)
